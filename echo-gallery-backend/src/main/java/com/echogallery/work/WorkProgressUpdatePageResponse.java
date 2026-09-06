package com.echogallery.work;

import java.util.List;

public record WorkProgressUpdatePageResponse(
        List<WorkProgressUpdateResponse> items,
        int page,
        int size,
        boolean hasNext) {
}
