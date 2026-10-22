package com.echogallery.work;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.card.PageResponse;
import com.echogallery.exception.ErrorResponse;
import com.echogallery.treasure.TreasureRequests;

@RestController @RequestMapping("/api/creative-works") @RequiredArgsConstructor
public class WorkController {
    private final WorkService service;
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> error(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponse(e.getReason(), e.getStatusCode().value())); }
    @GetMapping public PageResponse<WorkService.Summary> list(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long experimentId,
            @RequestParam(defaultValue = "false") boolean unassigned, @RequestParam(required = false) CreativeWork.Stage stage,
            @RequestParam(required = false) Boolean published, @RequestParam(defaultValue = "false") String archived,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<Long> experimentIds,
            @RequestParam(required = false) List<CreativeWork.Stage> stages) {
        return service.list(keyword, experimentIds != null ? experimentIds : experimentId == null ? List.of() : List.of(experimentId), unassigned, stages != null ? stages : stage == null ? List.of() : List.of(stage), published, "all".equals(archived) ? null : Boolean.valueOf(archived), page, size);
    }
    @GetMapping("/experiments") public List<WorkService.ExperimentOption> experiments() { return service.experiments(); }
    @GetMapping("/treasure/{id}") public WorkService.Detail treasure(@PathVariable Long id) { return service.fromTreasure(id); }
    @GetMapping("/candidates") public PageResponse<WorkService.SourceView> candidates(@RequestParam String kind, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long experimentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.candidates(kind, keyword, experimentId, page, size); }
    @GetMapping("/usages") public List<WorkService.Usage> usages(@RequestParam(defaultValue = "") List<Long> cardIds, @RequestParam(defaultValue = "") List<Long> recordIds) { return service.usages(cardIds, recordIds); }
    @PostMapping public WorkService.Detail create(@Valid @RequestBody WorkRequests.Info r) { return service.create(r); }
    @GetMapping("/{id}") public WorkService.Detail detail(@PathVariable Long id) { return service.detail(id); }
    @PutMapping("/{id}") public WorkService.Detail update(@PathVariable Long id, @Valid @RequestBody WorkRequests.Info r) { return service.update(id, r); }
    @PutMapping("/{id}/body") public WorkService.SavedBody body(@PathVariable Long id, @Valid @RequestBody WorkRequests.Body r) { return service.saveBody(id, r); }
    @PutMapping("/{id}/next-step") public WorkService.Detail next(@PathVariable Long id, @Valid @RequestBody WorkRequests.NextStep r) { return service.nextStep(id, r); }
    @PutMapping("/{id}/stage") public WorkService.Detail stage(@PathVariable Long id, @Valid @RequestBody WorkRequests.Stage r) { return service.stage(id, r); }
    @PutMapping("/{id}/share") public WorkService.Detail share(@PathVariable Long id, @Valid @RequestBody WorkRequests.Share r) { return service.share(id, r); }
    @PutMapping("/{id}/archive") public WorkService.Detail archive(@PathVariable Long id) { return service.archive(id, true); }
    @PutMapping("/{id}/restore") public WorkService.Detail restore(@PathVariable Long id) { return service.archive(id, false); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/{id}/sources") public List<WorkService.SourceView> sources(@PathVariable Long id) { return service.sources(id); }
    @PostMapping("/{id}/sources") public List<WorkService.SourceView> add(@PathVariable Long id, @Valid @RequestBody WorkRequests.Sources r) { return service.addSources(id, r); }
    @DeleteMapping("/{id}/sources/{sourceId}") public ResponseEntity<Void> remove(@PathVariable Long id, @PathVariable Long sourceId) { service.removeSource(id, sourceId); return ResponseEntity.noContent().build(); }
    @PutMapping("/{id}/treasure") public WorkService.Detail link(@PathVariable Long id, @Valid @RequestBody WorkRequests.Link r) { return service.link(id, r.treasureId()); }
    @PostMapping("/{id}/treasure") public WorkService.Detail createTreasure(@PathVariable Long id, @Valid @RequestBody TreasureRequests.Content r) { return service.createTreasure(id, r); }
    @DeleteMapping("/{id}/treasure") public WorkService.Detail unlink(@PathVariable Long id) { return service.unlink(id); }
}
