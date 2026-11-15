package com.retail.notification.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.retail.dto.SaleRequest;
import com.retail.model.AppUser;
import com.retail.model.PaymentMethod;
import com.retail.model.Product;
import com.retail.model.Sale;
import com.retail.model.UserRole;
import com.retail.notification.dto.NotificationDelivery;
import com.retail.notification.dto.NotificationResponse;
import com.retail.notification.entity.Notification;
import com.retail.notification.entity.NotificationOutbox;
import com.retail.notification.enums.NotificationCategory;
import com.retail.notification.enums.NotificationOutboxStatus;
import com.retail.notification.enums.NotificationSeverity;
import com.retail.notification.enums.NotificationType;
import com.retail.notification.event.SaleCompletedEvent;
import com.retail.notification.outbox.NotificationOutboxWorker;
import com.retail.notification.outbox.NotificationOutboxService;
import com.retail.notification.realtime.NotificationStreamService;
import com.retail.notification.repository.NotificationOutboxRepository;
import com.retail.notification.repository.NotificationRepository;
import com.retail.notification.service.NotificationCommand;
import com.retail.notification.service.NotificationService;
import com.retail.repository.AppUserRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import com.retail.service.JwtService;
import com.retail.service.ProductService;
import com.retail.service.SaleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL coverage for Release 1 notification persistence and delivery boundaries. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.bootstrap-owner.email=",
        "app.jwt.secret=dGVzdC1qd3Qtc2lnbmluZy1rZXktZm9yLWludGVncmF0aW9uLXRlc3RzLTAxMjM0NTY=",
        "app.notifications.outbox.poll-interval-ms=600000",
        "app.notifications.sse-heartbeat-ms=600000"
})
@AutoConfigureMockMvc
class NotificationPostgresIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationOutboxWorker outboxWorker;
    @Autowired private NotificationOutboxService notificationOutboxService;
    @Autowired private NotificationStreamService streamService;
    @Autowired private SaleService saleService;
    @Autowired private ProductService productService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        streamService.closeStreams();
        jdbcTemplate.execute("""
                truncate table notifications, notification_preferences, notification_outbox,
                sale_items, sales, products, customers, auth_refresh_tokens, app_users cascade
                """);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void closeStreams() {
        streamService.closeStreams();
    }

    @Test
    void migrationV8CreatesExpectedPostgresTablesColumnsConstraintsAndIndexes() {
        assertEquals(1, count("select count(*) from flyway_schema_history where version = '8' and success"));
        for (String table : List.of("notifications", "notification_preferences", "notification_outbox")) {
            assertEquals(1, count("select count(*) from information_schema.tables "
                    + "where table_schema = 'public' and table_name = ?", table));
        }
        assertEquals("jsonb", jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'notifications' and column_name = 'metadata'
                """, String.class));
        assertEquals("jsonb", jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'notification_outbox' and column_name = 'payload'
                """, String.class));
        assertEquals(1, count("select count(*) from pg_constraint where conname = 'uq_notification_preferences_user_type'"));
        String deduplicationIndex = jdbcTemplate.queryForObject("""
                select indexdef from pg_indexes
                where schemaname = 'public' and indexname = 'uq_notifications_recipient_deduplication_key'
                """, String.class);
        assertNotNull(deduplicationIndex);
        assertTrue(deduplicationIndex.toLowerCase().contains("create unique index"));
        assertTrue(deduplicationIndex.contains("recipient_user_id"));
        assertTrue(deduplicationIndex.contains("deduplication_key"));
        assertTrue(deduplicationIndex.toLowerCase().contains("where"));
        for (String index : List.of("uq_notifications_recipient_deduplication_key",
                "ix_notifications_recipient_history", "ix_notifications_recipient_unread",
                "ix_notification_outbox_ready", "ix_notification_outbox_stale_processing")) {
            assertEquals(1, count("select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", index));
        }
    }

    @Test
    void jsonbPayloadAndPartialUniqueDeduplicationWorkAgainstPostgres() {
        AppUser recipient = user(UserRole.OWNER, "owner-notification-test@example.com");
        UUID eventId = UUID.randomUUID();
        NotificationCommand command = notificationCommand(recipient, eventId, "dedupe-key", "Low stock");

        List<NotificationDelivery> inserted = notificationService.persist(List.of(command, command));

        assertEquals(1, inserted.size());
        List<Notification> saved = notificationRepository.findAll();
        assertEquals(1, saved.size());
        assertEquals(5, saved.get(0).getMetadata().get("quantity").asInt());
        assertNotNull(saved.get(0).getCreatedAt());
    }

    @Test
    void committedSaleUpdatesInventoryCapturesOutboxAndCompletesNotificationLifecycle() {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        AppUser receptionist = user(UserRole.RECEPTIONIST, "desk@example.com");
        Product product = product("SKU-SALE", 10, new BigDecimal("100"));

        Sale sale = saleService.createSale(saleRequest(product.getId(), 1));

        assertNotNull(sale.getId());
        assertEquals(9, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        List<NotificationOutbox> captured = outboxRepository.findAll();
        assertEquals(2, captured.size());
        assertTrue(captured.stream().allMatch(event -> event.getStatus() == NotificationOutboxStatus.PENDING));

        List<UUID> claimed = outboxWorker.claimReadyEvents();
        assertEquals(2, claimed.size());
        claimed.forEach(outboxWorker::processClaimedEvent);

        assertTrue(outboxRepository.findAll().stream()
                .allMatch(event -> event.getStatus() == NotificationOutboxStatus.PROCESSED && event.getProcessedAt() != null));
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(2, notifications.size());
        assertTrue(notifications.stream().allMatch(notification -> notification.getType() == NotificationType.SALE_COMPLETED));
        assertTrue(notifications.stream().anyMatch(notification -> notification.getRecipientUserId().equals(owner.getId())));
        assertTrue(notifications.stream().anyMatch(notification -> notification.getRecipientUserId().equals(receptionist.getId())));
    }

    @Test
    void commitFailureAfterOutboxCaptureRollsBackSaleInventoryOutboxAndNotifications() {
        user(UserRole.OWNER, "owner@example.com");
        Product product = product("SKU-ROLLBACK-SALE", 7, new BigDecimal("100"));

        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            Sale sale = saleService.createSale(saleRequest(product.getId(), 1));
            notificationOutboxService.capture(new SaleCompletedEvent(
                    UUID.randomUUID(), sale.getId(), sale.getSaleNumber(), sale.getTotalAmount()));
            outboxRepository.flush();
            assertEquals(1, outboxRepository.count(), "outbox capture must be durable inside the open transaction");
            Sale duplicateSaleNumber = new Sale();
            duplicateSaleNumber.setSaleNumber(sale.getSaleNumber());
            saleRepository.saveAndFlush(duplicateSaleNumber);
        }));

        assertEquals(0, saleRepository.count());
        assertEquals(7, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, outboxRepository.count());
        assertEquals(0, notificationRepository.count());
    }

    @Test
    void commitFailureAfterInventoryEventCaptureRollsBackInventoryAndOutbox() {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        Product product = product("SKU-ROLLBACK-INVENTORY", 10, new BigDecimal("100"));

        assertThrows(RuntimeException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            productService.update(product.getId(), productUpdate(product, 5));
            notificationOutboxService.capture(new com.retail.notification.event.InventoryStockChangedEvent(
                    UUID.randomUUID(), product.getId(), product.getName(), 10, 5,
                    com.retail.notification.event.InventoryChangeSource.MANUAL_ADJUSTMENT));
            outboxRepository.flush();
            assertEquals(1, outboxRepository.count(), "stock outbox capture must be durable inside the open transaction");
            appUserRepository.saveAndFlush(unsavedUser(UserRole.RECEPTIONIST, owner.getEmail()));
        }));

        assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, outboxRepository.count());
        assertEquals(0, notificationRepository.count());
    }

    @Test
    void processingFailureLeavesBusinessDataCommittedThenRetriesWithoutDuplicates() {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        Product product = product("SKU-RETRY", 10, new BigDecimal("100"));
        Sale sale = saleService.createSale(saleRequest(product.getId(), 1));
        NotificationOutbox saleEvent = saleEventFor(sale.getId());
        List<NotificationOutbox> siblings = outboxRepository.findAll().stream()
                .filter(event -> !event.getId().equals(saleEvent.getId())).toList();
        siblings.forEach(event -> {
            event.setStatus(NotificationOutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setNextAttemptAt(Instant.now());
        });
        outboxRepository.saveAllAndFlush(siblings);
        jdbcTemplate.update("update notification_outbox set event_type = 'UNSUPPORTED_EVENT' where id = ?",
                saleEvent.getId());

        UUID claimedEvent = outboxWorker.claimReadyEvents().get(0);
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> outboxWorker.processClaimedEvent(claimedEvent));
        outboxWorker.recordFailure(claimedEvent, failure);

        NotificationOutbox retry = outboxRepository.findById(claimedEvent).orElseThrow();
        assertEquals(NotificationOutboxStatus.PENDING, retry.getStatus());
        assertEquals(1, retry.getAttempts());
        assertEquals(1, saleRepository.count());
        assertEquals(9, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        assertEquals(0, notificationRepository.count());

        jdbcTemplate.update("update notification_outbox set event_type = 'SALE_COMPLETED' where id = ?",
                retry.getId());
        retry.setNextAttemptAt(Instant.now());
        outboxRepository.saveAndFlush(retry);
        outboxWorker.claimReadyEvents().forEach(outboxWorker::processClaimedEvent);
        assertEquals(NotificationOutboxStatus.PROCESSED,
                outboxRepository.findById(claimedEvent).orElseThrow().getStatus());
        assertEquals(1, notificationRepository.count());

        NotificationOutbox replay = outboxRepository.findById(claimedEvent).orElseThrow();
        replay.setStatus(NotificationOutboxStatus.PENDING);
        replay.setNextAttemptAt(Instant.now());
        outboxRepository.saveAndFlush(replay);
        outboxWorker.claimReadyEvents().forEach(outboxWorker::processClaimedEvent);
        assertEquals(1, notificationRepository.count());
        assertEquals(owner.getId(), notificationRepository.findAll().get(0).getRecipientUserId());
    }

    @Test
    void retryBackoffAndMaximumAttemptsArePersistedAgainstPostgres() {
        NotificationOutbox retry = outbox(saleEvent(UUID.randomUUID()), NotificationOutboxStatus.PROCESSING, 1,
                Instant.now(), Instant.now());
        Instant firstBefore = Instant.now();
        outboxWorker.recordFailure(retry.getId(), new IllegalStateException("simulated failure"));
        Instant firstAfter = Instant.now();
        retry = outboxRepository.findById(retry.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PENDING, retry.getStatus());
        assertBetween(retry.getNextAttemptAt(), firstBefore.plusSeconds(1), firstAfter.plusSeconds(1));

        retry.setStatus(NotificationOutboxStatus.PROCESSING);
        retry.setAttempts(2);
        retry.setProcessingStartedAt(Instant.now());
        outboxRepository.saveAndFlush(retry);
        Instant secondBefore = Instant.now();
        outboxWorker.recordFailure(retry.getId(), new IllegalStateException("simulated failure"));
        Instant secondAfter = Instant.now();
        retry = outboxRepository.findById(retry.getId()).orElseThrow();
        assertBetween(retry.getNextAttemptAt(), secondBefore.plusSeconds(2), secondAfter.plusSeconds(2));

        retry.setStatus(NotificationOutboxStatus.PROCESSING);
        retry.setAttempts(5);
        retry.setProcessingStartedAt(Instant.now());
        outboxRepository.saveAndFlush(retry);
        outboxWorker.recordFailure(retry.getId(), new IllegalStateException("simulated failure"));
        retry = outboxRepository.findById(retry.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.FAILED, retry.getStatus());
        assertEquals(5, retry.getAttempts());
    }

    @Test
    void expiredProcessingLeaseIsRecoveredThenProcessedSuccessfully() {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        NotificationOutbox stale = outbox(saleEvent(UUID.randomUUID()), NotificationOutboxStatus.PROCESSING, 0,
                Instant.now().minusSeconds(600), Instant.now());

        assertEquals(List.of(stale.getId()), outboxWorker.claimReadyEvents());
        NotificationOutbox reclaimed = outboxRepository.findById(stale.getId()).orElseThrow();
        assertEquals(NotificationOutboxStatus.PROCESSING, reclaimed.getStatus());
        assertEquals(1, reclaimed.getAttempts());
        outboxWorker.processClaimedEvent(stale.getId());
        assertEquals(NotificationOutboxStatus.PROCESSED, outboxRepository.findById(stale.getId()).orElseThrow().getStatus());
        assertEquals(1, notificationRepository.count());
        assertEquals(owner.getId(), notificationRepository.findAll().get(0).getRecipientUserId());
    }

    @Test
    void skipLockedPreventsConcurrentClaimsAndReleasedWorkIsEventuallyProcessed() throws Exception {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        NotificationOutbox event = outbox(saleEvent(UUID.randomUUID()), NotificationOutboxStatus.PENDING, 0,
                null, Instant.now());
        CountDownLatch firstClaimedLock = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<UUID>> firstClaim = executor.submit(() -> transactionTemplate.execute(status -> {
                List<NotificationOutbox> locked = outboxRepository.lockReadyBatch(10);
                firstClaimedLock.countDown();
                try {
                    assertTrue(releaseFirstTransaction.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while holding PostgreSQL row lock", exception);
                }
                return locked.stream().map(NotificationOutbox::getId).toList();
            }));
            assertTrue(firstClaimedLock.await(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            List<UUID> secondClaim = transactionTemplate.execute(status -> outboxRepository.lockReadyBatch(10).stream()
                    .map(NotificationOutbox::getId).toList());
            assertTrue(secondClaim.isEmpty());
            releaseFirstTransaction.countDown();
            assertEquals(List.of(event.getId()), firstClaim.get(AWAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }

        assertEquals(List.of(event.getId()), outboxWorker.claimReadyEvents());
        outboxWorker.processClaimedEvent(event.getId());
        assertEquals(NotificationOutboxStatus.PROCESSED, outboxRepository.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(1, notificationRepository.count());
        assertEquals(owner.getId(), notificationRepository.findAll().get(0).getRecipientUserId());
    }

    @Test
    void authenticatedRestApisUseRecipientScopedPostgresRecordsAndMutations() throws Exception {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        AppUser receptionist = user(UserRole.RECEPTIONIST, "desk@example.com");
        AppUser independent = user(UserRole.OWNER, "other@example.com");
        NotificationDelivery first = persist(owner, "owner-1", "Owner one");
        persist(owner, "owner-2", "Owner two");
        persist(owner, "owner-3", "Owner three");
        NotificationDelivery receptionistNotification = persist(receptionist, "desk-1", "Desk only");
        NotificationDelivery independentNotification = persist(independent, "other-1", "Other only");

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(owner)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(owner)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(patch("/api/notifications/{id}/read", first.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isRead").value(true));
        mockMvc.perform(patch("/api/notifications/read-all").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(delete("/api/notifications/{id}", first.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(patch("/api/notifications/{id}/read", receptionistNotification.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/notifications/{id}", independentNotification.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        assertFalse(notificationRepository.findById(independentNotification.id()).orElseThrow().isRead());
    }

    @Test
    void authenticatedSseEndpointTracksUsersTabsAndReconnectContext() throws Exception {
        AppUser owner = user(UserRole.OWNER, "owner@example.com");
        AppUser other = user(UserRole.OWNER, "other@example.com");
        MvcResult ownerStream = mockMvc.perform(get("/api/notifications/stream")
                        .header("Authorization", bearer(owner)).header("Last-Event-ID", "prior-notification"))
                .andExpect(status().isOk()).andReturn();
        MvcResult secondOwnerStream = mockMvc.perform(get("/api/notifications/stream").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        MvcResult otherStream = mockMvc.perform(get("/api/notifications/stream").header("Authorization", bearer(other)))
                .andExpect(status().isOk()).andReturn();

        assertEquals(2, streamService.activeConnectionCount(owner.getId()));
        assertEquals(1, streamService.activeConnectionCount(other.getId()));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
            assertTrue(ownerStream.getResponse().getContentAsString().contains("connected"));
            assertTrue(ownerStream.getResponse().getContentAsString().contains("prior-notification"));
        });

        NotificationDelivery delivery = persist(owner, "sse-owner", "SSE owner event");
        assertTrue(notificationRepository.existsById(delivery.id()));
        streamService.publish(owner.getId(), NotificationResponse.from(delivery), delivery.id().toString());
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertTrue(ownerStream.getResponse().getContentAsString().contains(delivery.id().toString())));
        assertFalse(otherStream.getResponse().getContentAsString().contains(delivery.id().toString()));

        NotificationOutbox persistedEvent = outbox(saleEvent(UUID.randomUUID()), NotificationOutboxStatus.PENDING, 0,
                null, Instant.now());
        outboxWorker.claimReadyEvents().forEach(outboxWorker::processClaimedEvent);
        Notification postCommitDelivery = notificationRepository.findAll().stream()
                .filter(notification -> notification.getEventId().equals(persistedEvent.getId()))
                .filter(notification -> notification.getRecipientUserId().equals(owner.getId()))
                .findFirst().orElseThrow();
        assertTrue(notificationRepository.existsById(postCommitDelivery.getId()));
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() ->
                assertTrue(ownerStream.getResponse().getContentAsString().contains(postCommitDelivery.getId().toString())));

        ownerStream.getRequest().getAsyncContext().complete();
        secondOwnerStream.getRequest().getAsyncContext().complete();
        otherStream.getRequest().getAsyncContext().complete();
        await().atMost(AWAIT_TIMEOUT).until(() -> streamService.activeConnectionCount(owner.getId()) == 0);
        await().atMost(AWAIT_TIMEOUT).until(() -> streamService.activeConnectionCount(other.getId()) == 0);
    }

    private AppUser user(UserRole role, String email) {
        return appUserRepository.saveAndFlush(unsavedUser(role, email));
    }

    private AppUser unsavedUser(UserRole role, String email) {
        AppUser user = new AppUser();
        user.setName(email.substring(0, email.indexOf('@')));
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private Product product(String sku, int stock, BigDecimal price) {
        Product product = new Product();
        product.setName(sku);
        product.setSku(sku);
        product.setPrice(price);
        product.setCost(BigDecimal.ZERO);
        product.setStockQuantity(stock);
        product.setActive(true);
        return productRepository.saveAndFlush(product);
    }

    private Product productUpdate(Product existing, int stock) {
        Product request = new Product();
        request.setName(existing.getName());
        request.setSku(existing.getSku());
        request.setDescription(existing.getDescription());
        request.setSize(existing.getSize());
        request.setColor(existing.getColor());
        request.setPrice(existing.getPrice());
        request.setCost(existing.getCost());
        request.setStockQuantity(stock);
        request.setActive(existing.getActive());
        request.setCategory(existing.getCategory());
        return request;
    }

    private SaleRequest saleRequest(UUID productId, int quantity) {
        SaleRequest.Item item = new SaleRequest.Item();
        item.setProductId(productId);
        item.setQuantity(quantity);
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setItems(List.of(item));
        return request;
    }

    private NotificationOutbox saleEventFor(UUID saleId) {
        return outboxRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(saleId))
                .filter(event -> "SALE_COMPLETED".equals(event.getEventType()))
                .findFirst().orElseThrow();
    }

    private SaleCompletedEvent saleEvent(UUID eventId) {
        return new SaleCompletedEvent(eventId, UUID.randomUUID(), "S-integration", new BigDecimal("100"));
    }

    private NotificationOutbox outbox(SaleCompletedEvent event, NotificationOutboxStatus status,
                                      int attempts, Instant processingStartedAt, Instant nextAttemptAt) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(event.eventId());
        outbox.setEventType(event.eventType());
        outbox.setAggregateType(event.aggregateType());
        outbox.setAggregateId(event.aggregateId());
        outbox.setPayload(objectMapper.valueToTree(event));
        outbox.setStatus(status);
        outbox.setAttempts(attempts);
        outbox.setProcessingStartedAt(processingStartedAt);
        outbox.setNextAttemptAt(nextAttemptAt);
        outbox.setCreatedAt(Instant.now());
        return outboxRepository.saveAndFlush(outbox);
    }

    private NotificationCommand notificationCommand(AppUser recipient, UUID eventId, String key, String title) {
        return new NotificationCommand(recipient.getId(), eventId, NotificationType.LOW_STOCK,
                NotificationCategory.INVENTORY, title, "Notebook has 5 units remaining.",
                NotificationSeverity.WARNING, "PRODUCT", UUID.randomUUID(),
                JsonNodeFactory.instance.objectNode().put("quantity", 5), key);
    }

    private NotificationDelivery persist(AppUser recipient, String key, String title) {
        return notificationService.persist(List.of(notificationCommand(recipient, UUID.randomUUID(), key, title))).get(0);
    }

    private String bearer(AppUser user) {
        return "Bearer " + jwtService.createToken(user);
    }

    private int count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private void assertBetween(Instant actual, Instant lowerInclusive, Instant upperInclusive) {
        assertFalse(actual.isBefore(lowerInclusive));
        assertFalse(actual.isAfter(upperInclusive));
    }
}
