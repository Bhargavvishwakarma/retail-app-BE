package com.retail.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content, int number, int size, long totalElements,
                              int totalPages, boolean first, boolean last,
                              int numberOfElements, boolean empty) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
                page.getNumberOfElements(), page.isEmpty());
    }
}
