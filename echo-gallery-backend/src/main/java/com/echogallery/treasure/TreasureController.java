package com.echogallery.treasure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.echogallery.card.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/treasures") @RequiredArgsConstructor
public class TreasureController {
    private final TreasureService service;

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<com.echogallery.exception.ErrorResponse> handleDomainError(
            org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(
                new com.echogallery.exception.ErrorResponse(error.getReason(), error.getStatusCode().value()));
    }

    @GetMapping
    public PageResponse<TreasureResponses.Summary> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "rank", required = false) TreasureRank rank,
            @RequestParam(name = "archived", defaultValue = "false") boolean archived,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(keyword, rank, archived, page, size);
    }
    @GetMapping("/stats") public TreasureResponses.Stats stats() { return service.stats(); }
    @PostMapping public TreasureResponses.Detail create(@Valid @RequestBody TreasureRequests.Content request) { return service.create(request); }
    @GetMapping("/{id}") public TreasureResponses.Detail detail(@PathVariable("id") Long id) { return service.detail(id); }
    @PutMapping("/{id}") public TreasureResponses.Detail update(@PathVariable("id") Long id, @Valid @RequestBody TreasureRequests.Content request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable("id") Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @PutMapping("/{id}/archive") public TreasureResponses.Detail archive(@PathVariable("id") Long id) { return service.archive(id, true); }
    @PutMapping("/{id}/restore") public TreasureResponses.Detail restore(@PathVariable("id") Long id) { return service.archive(id, false); }
    @GetMapping("/{id}/relations") public PageResponse<TreasureResponses.Reference> relations(
            @PathVariable("id") Long id, @RequestParam(name = "sources", defaultValue = "false") boolean sources,
            @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) { return service.relations(id, sources, page, size); }
    @GetMapping("/{id}/trials") public PageResponse<TreasureResponses.Trial> trials(
            @PathVariable("id") Long id, @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) { return service.trials(id, page, size); }
    @PostMapping("/{id}/trials") public TreasureResponses.Trial createTrial(@PathVariable("id") Long id, @Valid @RequestBody TreasureRequests.Trial request) { return service.createTrial(id, request); }
    @GetMapping("/{id}/challenge-route") public java.util.List<TreasureResponses.Trial> route(@PathVariable("id") Long id) { return service.route(id); }
    @GetMapping("/{id}/trials/{trialId}/attempts") public PageResponse<TreasureResponses.Attempt> attempts(@PathVariable("id") Long id, @PathVariable("trialId") Long trialId, @RequestParam(name = "page", defaultValue = "0") int page) { return service.attempts(id, trialId, page); }
    @PostMapping("/{id}/trials/{trialId}/attempts") public TreasureResponses.Attempt attempt(@PathVariable("id") Long id, @PathVariable("trialId") Long trialId, @Valid @RequestBody TreasureRequests.Attempt request) { return service.recordAttempt(id, trialId, request); }
    @PutMapping("/{id}/trials/{trialId}") public TreasureResponses.Trial updateTrial(@PathVariable("id") Long id, @PathVariable("trialId") Long trialId, @Valid @RequestBody TreasureRequests.Trial request) { return service.updateTrial(id, trialId, request); }
    @PostMapping("/{id}/trials/{trialId}/pass") public TreasureResponses.Trial pass(@PathVariable("id") Long id, @PathVariable("trialId") Long trialId, @Valid @RequestBody TreasureRequests.Result request) { return service.pass(id, trialId, request); }
    @GetMapping("/{id}/yields") public PageResponse<TreasureResponses.Yield> yields(
            @PathVariable("id") Long id, @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) { return service.yields(id, page, size); }
    @PostMapping("/{id}/yields") public TreasureResponses.Yield createYield(@PathVariable("id") Long id, @Valid @RequestBody TreasureRequests.Yield request) { return service.saveYield(id, null, request); }
    @PutMapping("/{id}/yields/{yieldId}") public TreasureResponses.Yield updateYield(@PathVariable("id") Long id, @PathVariable("yieldId") Long yieldId, @Valid @RequestBody TreasureRequests.Yield request) { return service.saveYield(id, yieldId, request); }
    @DeleteMapping("/{id}/yields/{yieldId}") public ResponseEntity<Void> deleteYield(@PathVariable("id") Long id, @PathVariable("yieldId") Long yieldId) { service.deleteYield(id, yieldId); return ResponseEntity.noContent().build(); }
}
