package com.retail.dto;

import java.util.List;

public record ChatResponse(String answer, String conversationId, String modelId, List<String> dataSources) {
}
