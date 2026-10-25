package com.retail.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.retail.config.RequestLoggingFilter;
import com.retail.dto.AvailableChatModel;
import com.retail.dto.ChatRequest;
import com.retail.dto.ChatResponse;
import com.retail.dto.ConversationPageResponse;
import com.retail.dto.ConversationSummary;
import com.retail.dto.InternalChatRequest;
import com.retail.exception.UpstreamServiceException;
import com.retail.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String CHAT_PATH = "/internal/v1/chat";
    private static final String MODELS_PATH = "/internal/v1/models";
    private final AppUserRepository users;
    private final ActorAssertionService actorAssertions;
    private final InternalRequestSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String processorUrl;

    public ChatService(AppUserRepository users, ActorAssertionService actorAssertions,
                       InternalRequestSigner signer, ObjectMapper objectMapper,
                       @Value("${app.ai.processor-url:http://localhost:8090}") String processorUrl) {
        this.users = users;
        this.actorAssertions = actorAssertions;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.processorUrl = processorUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public ChatResponse chat(ChatRequest request) {
        var user = authenticatedUser();
        var internal = new InternalChatRequest(request.question(), request.modelId(), request.conversationId(),
                new InternalChatRequest.User(user.getId(), user.getRole(), actorAssertions.mint(user)));
        try {
            byte[] body = objectMapper.writeValueAsBytes(internal);
            long startedAt = System.nanoTime();
            log.info("ai_processor_request method=POST path={} userId={} role={} modelId={} conversationId={} questionChars={}",
                    internal.user().userId(), internal.user().role(), internal.modelId(), internal.conversationId(),
                    internal.question().length());
            if (log.isDebugEnabled()) {
                log.debug("ai_processor_request_payload method=POST path={} payload={}", CHAT_PATH,
                        redactedRequest(internal));
            }
            HttpResponse<byte[]> response = sendSigned("POST", CHAT_PATH, body,
                    HttpRequest.newBuilder(URI.create(processorUrl + CHAT_PATH))
                            .timeout(Duration.ofSeconds(70)).header("Content-Type", "application/json")
                            .header(RequestLoggingFilter.REQUEST_ID_HEADER, requestId())
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
            log.info("ai_processor_response method=POST path={} status={} durationMs={}", CHAT_PATH,
                    response.statusCode(), elapsedMs(startedAt));
            requireSuccess(response, "AI Processor chat");
            ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
            log.info("ai_chat_completed modelId={} conversationId={} dataSources={} answerChars={}",
                    chatResponse.modelId(), chatResponse.conversationId(), chatResponse.dataSources(),
                    chatResponse.answer() == null ? 0 : chatResponse.answer().length());
            return chatResponse;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (UpstreamServiceException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI Processor request timed out", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Processor is unavailable", exception);
        }
    }

    public ConversationPageResponse conversations(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation page");
        }
        JsonNode result = conversationRequest("list", null, page, size);
        JsonNode upstreamContent = result.path("content");
        List<ConversationSummary> summaries = new ArrayList<>();
        if (upstreamContent.isArray()) {
            for (JsonNode item : upstreamContent) {
                summaries.add(new ConversationSummary(textOrNull(item, "conversationId"),
                        textOrNull(item, "title"), textOrNull(item, "updatedAt")));
            }
        }

        summaries.sort(Comparator
                .comparing((ConversationSummary item) -> instantOrNull(item.updatedAt()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ConversationSummary::conversationId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        boolean upstreamHasMore = result.path("hasMore").asBoolean(result.path("hasNext").asBoolean(false));
        boolean hasMore = !summaries.isEmpty() && (upstreamHasMore
                || summaries.size() > size);
        if (summaries.size() > size) {
            summaries = new ArrayList<>(summaries.subList(0, size));
        }
        return new ConversationPageResponse(List.copyOf(summaries), page, size, hasMore);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private Instant instantOrNull(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (java.time.format.DateTimeParseException ignored) { return null; }
    }

    public JsonNode conversation(String conversationId) { return conversationRequest("get", conversationId, 0, 10); }

    private JsonNode conversationRequest(String operation, String conversationId, int page, int size) {
        var user = authenticatedUser();
        try {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("userId", user.getId().toString());
            if (conversationId != null) payload.put("conversationId", conversationId);
            if ("list".equals(operation)) {
                payload.put("page", page);
                payload.put("size", size);
            }
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String path = "/internal/v1/conversations/" + operation;
            HttpRequest request = HttpRequest.newBuilder(URI.create(processorUrl + path))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .header(RequestLoggingFilter.REQUEST_ID_HEADER, requestId())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<byte[]> response = sendSigned("POST", path, body, request);
            requireSuccess(response, "AI Processor conversation history");
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (UpstreamServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Processor is unavailable", exception);
        }
    }

    private com.retail.model.AppUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    public List<AvailableChatModel> availableModels() {
        try {
            byte[] emptyBody = new byte[0];
            HttpRequest request = HttpRequest.newBuilder(URI.create(processorUrl + MODELS_PATH))
                    .timeout(Duration.ofSeconds(10))
                    .header(RequestLoggingFilter.REQUEST_ID_HEADER, requestId()).GET().build();
            long startedAt = System.nanoTime();
            log.info("ai_processor_request method=GET path={} bodyBytes=0", MODELS_PATH);
            HttpResponse<byte[]> response = sendSigned("GET", MODELS_PATH, emptyBody, request);
            log.info("ai_processor_response method=GET path={} status={} durationMs={}", MODELS_PATH,
                    response.statusCode(), elapsedMs(startedAt));
            requireSuccess(response, "AI Processor model discovery");
            List<AvailableChatModel> models = objectMapper.readValue(response.body(), new TypeReference<>() { });
            log.info("ai_models_loaded count={} modelIds={}", models.size(),
                    models.stream().map(AvailableChatModel::id).toList());
            return models;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (UpstreamServiceException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI Processor model discovery timed out", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Processor is unavailable", exception);
        }
    }

    private HttpResponse<byte[]> sendSigned(String method, String path, byte[] body, HttpRequest request)
            throws Exception {
        var headers = signer.sign(method, path, body);
        HttpRequest signedRequest = HttpRequest.newBuilder(request, (name, value) -> true)
                .header("X-Internal-Timestamp", headers.timestamp())
                .header("X-Internal-Nonce", headers.nonce())
                .header("X-Internal-Signature", headers.signature())
                .build();
        return httpClient.send(signedRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String redactedRequest(InternalChatRequest request) {
        ObjectNode payload = objectMapper.valueToTree(request);
        if (payload.path("user") instanceof ObjectNode user) user.put("actorToken", "[REDACTED]");
        try { return objectMapper.writeValueAsString(payload); }
        catch (Exception ignored) { return "[unavailable]"; }
    }

    private String requestId() {
        String value = MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
        return value == null ? "" : value;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void requireSuccess(HttpResponse<byte[]> response, String operation) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;
        JsonNode error = null;
        try { error = objectMapper.readTree(response.body()); }
        catch (Exception ignored) { }
        String detail = firstText(error, "detail", "message", "error");
        if (detail == null || detail.isBlank()) detail = operation + " failed with HTTP " + status;
        String upstreamCode = firstText(error, "code");
        if (upstreamCode == null || upstreamCode.isBlank()) upstreamCode = "HTTP_" + status;
        String upstreamRequestId = firstText(error, "requestId");
        if (upstreamRequestId == null) upstreamRequestId = response.headers()
                .firstValue(RequestLoggingFilter.REQUEST_ID_HEADER).orElse(null);

        HttpStatus mappedStatus = switch (status) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        throw new UpstreamServiceException(mappedStatus, upstreamCode, detail, upstreamRequestId);
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }
}
