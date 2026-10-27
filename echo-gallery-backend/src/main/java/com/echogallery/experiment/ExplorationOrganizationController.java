package com.echogallery.experiment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/experiments/{experimentId}/activity/organizations")
@RequiredArgsConstructor
public class ExplorationOrganizationController {
    private final ExplorationOrganizationService service;
    @PostMapping("/preview") public ExplorationOrganizationService.PreviewResponse preview(@PathVariable("experimentId") Long experimentId,
        @Valid @RequestBody ExplorationOrganizationRequests.Preview request) { return service.preview(experimentId, request); }
    @PostMapping public ExplorationOrganizationService.Result commit(@PathVariable("experimentId") Long experimentId,
        @Valid @RequestBody ExplorationOrganizationRequests.Commit request) { return service.commit(experimentId, request); }
}
