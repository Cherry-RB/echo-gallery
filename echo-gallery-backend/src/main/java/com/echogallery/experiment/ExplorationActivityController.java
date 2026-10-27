package com.echogallery.experiment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/experiments/{experimentId}/activity")
@RequiredArgsConstructor
public class ExplorationActivityController {
    private final ExplorationActivityService service;
    @GetMapping("/methods/{methodId}") public ExperimentExplorationResponse.MethodOption method(@PathVariable("experimentId") Long experimentId, @PathVariable("methodId") Long methodId) { return service.ownedOption(experimentId, methodId); }
    @GetMapping public ExperimentExplorationResponse state(@PathVariable("experimentId") Long experimentId) { return service.state(experimentId); }
    @PutMapping("/focus") public ExperimentExplorationResponse focus(@PathVariable("experimentId") Long experimentId, @Valid @RequestBody ExplorationActivityRequests.Focus request) { return service.focus(experimentId, request); }
    @PutMapping("/favorites") public ExperimentExplorationResponse favorites(@PathVariable("experimentId") Long experimentId, @Valid @RequestBody ExplorationActivityRequests.Favorites request) { return service.favorites(experimentId, request); }
    @GetMapping("/methods") public com.echogallery.card.PageResponse<ExperimentExplorationResponse.MethodOption> methods(@PathVariable("experimentId") Long experimentId,
        @RequestParam(name = "threadId", required = false) Long threadId, @RequestParam(name = "keyword", defaultValue = "") String keyword,
        @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) { return service.methods(experimentId, threadId, keyword, page, size); }
    @PostMapping("/methods") public ExperimentExplorationResponse.MethodOption createMethod(@PathVariable("experimentId") Long experimentId, @Valid @RequestBody ExplorationActivityRequests.Method request) { return service.createMethod(experimentId, request); }
    @PutMapping("/methods/{methodId}") public ExperimentExplorationResponse.MethodOption updateMethod(@PathVariable("experimentId") Long experimentId, @PathVariable("methodId") Long methodId, @Valid @RequestBody ExplorationActivityRequests.Method request) { return service.updateMethod(experimentId, methodId, request); }
    @DeleteMapping("/methods/{methodId}") public void deleteMethod(@PathVariable("experimentId") Long experimentId, @PathVariable("methodId") Long methodId,
        @RequestParam(name = "expectedVersion") long expectedVersion, @RequestParam(name = "deleteRecords", defaultValue = "false") boolean deleteRecords) { service.deleteMethod(experimentId, methodId, expectedVersion, deleteRecords); }
    @GetMapping("/records") public com.echogallery.card.PageResponse<ExperimentExplorationResponse.RecordResponse> records(@PathVariable("experimentId") Long experimentId,
        @RequestParam(name = "threadId", required = false) Long threadId, @RequestParam(name = "methodId", required = false) Long methodId,
        @RequestParam(name = "unassigned", defaultValue = "false") boolean unassigned, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.records(experimentId, threadId, methodId, unassigned, page, size);
    }
    @PostMapping("/records") public ExperimentExplorationResponse.RecordResponse createRecord(@PathVariable("experimentId") Long experimentId, @Valid @RequestBody ExplorationActivityRequests.Discovery request) { return service.createRecord(experimentId, request); }
    @PutMapping("/records/{recordId}") public ExperimentExplorationResponse.RecordResponse updateRecord(@PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId, @Valid @RequestBody ExplorationActivityRequests.Discovery request) { return service.updateRecord(experimentId, recordId, request); }
    @PutMapping("/records/{recordId}/assignment") public ExperimentExplorationResponse.RecordResponse assignment(@PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId, @Valid @RequestBody ExplorationActivityRequests.Assignment request) { return service.assign(experimentId, recordId, request); }
    @DeleteMapping("/records/{recordId}") public void deleteRecord(@PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId, @RequestParam(name = "expectedVersion") long expectedVersion) { service.deleteRecord(experimentId, recordId, expectedVersion); }
    @GetMapping("/history") public ExperimentExplorationResponse.History history(@PathVariable("experimentId") Long experimentId, @RequestParam(name = "threadId") Long threadId, @RequestParam(name = "methodId", required = false) Long methodId) { return service.history(experimentId, threadId, methodId); }
}
