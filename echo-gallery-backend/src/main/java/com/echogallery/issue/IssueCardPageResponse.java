package com.echogallery.issue;

import java.util.List;

public record IssueCardPageResponse(
        List<IssueCardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
