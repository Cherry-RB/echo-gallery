package com.echogallery.issue;

import java.util.List;

public record IssueUpdatePageResponse(
        List<IssueUpdateResponse> items,
        int page,
        int size,
        boolean hasNext) {
}
