package com.retail.dto;

import java.util.List;

public record ConversationPageResponse(List<ConversationSummary> content, int page, int size, boolean hasMore) {
}
