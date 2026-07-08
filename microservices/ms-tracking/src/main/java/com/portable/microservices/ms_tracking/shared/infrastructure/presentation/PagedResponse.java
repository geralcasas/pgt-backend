package com.portable.microservices.ms_tracking.shared.infrastructure.presentation;

import java.util.List;

public record PagedResponse<T>(
    List<T> items,
    long total,
    int page,
    int pageSize
) {
    public int totalPages() {
        return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }
}
