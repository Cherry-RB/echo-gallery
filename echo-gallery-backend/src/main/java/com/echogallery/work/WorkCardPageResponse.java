package com.echogallery.work;

import java.util.List;

public record WorkCardPageResponse(
        List<WorkCardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
