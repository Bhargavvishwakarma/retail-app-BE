package com.retail.dto;

import com.retail.model.UserRole;

import java.util.UUID;

public record InternalChatRequest(String question, String modelId, String conversationId, User user) {
    public record User(UUID userId, UserRole role, String actorToken) {
    }
}
