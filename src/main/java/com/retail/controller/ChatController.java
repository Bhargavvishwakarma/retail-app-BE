package com.retail.controller;

import com.retail.dto.ChatRequest;
import com.retail.dto.ChatResponse;
import com.retail.dto.AvailableChatModel;
import com.retail.dto.ConversationPageResponse;
import com.retail.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    @GetMapping("/models")
    public List<AvailableChatModel> models() {
        return chatService.availableModels();
    }

    @GetMapping("/conversations")
    public ConversationPageResponse conversations(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return chatService.conversations(page, size);
    }

    @GetMapping("/conversations/{conversationId}")
    public JsonNode conversation(@PathVariable String conversationId) {
        return chatService.conversation(conversationId);
    }
}
