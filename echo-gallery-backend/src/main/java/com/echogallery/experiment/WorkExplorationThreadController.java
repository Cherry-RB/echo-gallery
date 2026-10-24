package com.echogallery.experiment;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/creative-works/{workId}/exploration-threads")
@RequiredArgsConstructor
public class WorkExplorationThreadController {
    private final ExplorationThreadService service;

    @GetMapping
    public List<WorkThreadResponse> list(@PathVariable("workId") Long workId) {
        return service.listByWork(workId);
    }

    public record WorkThreadResponse(Long threadId, String threadName, Long experimentId,
            String experimentTitle, ExplorationThreadStatus status) {}
}
