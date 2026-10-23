package com.echogallery.experiment;

import java.util.List;
import com.echogallery.card.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/experiments/{experimentId}/threads")
@RequiredArgsConstructor
public class ExplorationThreadController {
    private final ExplorationThreadService service;
    @PostMapping("/imports") public ResponseEntity<ExplorationThreadResponse> createWithImports(@PathVariable Long experimentId, @Valid @RequestBody ExplorationThreadRequests.NewImportRequest request) { return ResponseEntity.ok(service.createWithImports(experimentId, request)); }
    @PostMapping("/materials") public ResponseEntity<ExplorationThreadResponse> createWithMaterial(@PathVariable Long experimentId, @Valid @RequestBody ExplorationThreadRequests.NewMaterialRequest request) { return ResponseEntity.ok(service.createWithMaterial(experimentId, request)); }
    @PostMapping("/{threadId}/discoveries") public ResponseEntity<ExplorationThreadResponse> discover(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.DiscoveryRequest request) { return ResponseEntity.ok(service.discover(experimentId, threadId, request)); }
    @PostMapping("/{threadId}/cards/batch") public ResponseEntity<ExplorationThreadResponse> addCards(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.CardsRequest request) { return ResponseEntity.ok(service.addCards(experimentId, threadId, request)); }
    @PutMapping("/{threadId}/cards/{linkId}/note") public ResponseEntity<ExplorationThreadResponse> updateCardNote(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long linkId, @Valid @RequestBody ExplorationThreadRequests.NoteRequest request) { return ResponseEntity.ok(service.updateCardNote(experimentId, threadId, linkId, request)); }
    @GetMapping public ResponseEntity<List<ExplorationThreadResponse>> list(@PathVariable Long experimentId) { return ResponseEntity.ok(service.list(experimentId)); }
    @GetMapping("/summaries") public ResponseEntity<PageResponse<ExplorationThreadSummaryResponse>> summaries(
            @PathVariable Long experimentId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(service.listSummaries(experimentId, page, size)); }
    @GetMapping("/{threadId}") public ResponseEntity<ExplorationThreadResponse> get(@PathVariable Long experimentId, @PathVariable Long threadId) { return ResponseEntity.ok(service.get(experimentId, threadId)); }
    @PostMapping public ResponseEntity<ExplorationThreadResponse> create(@PathVariable Long experimentId, @Valid @RequestBody ExplorationThreadRequests.ThreadRequest request) { return ResponseEntity.ok(service.create(experimentId, request)); }
    @PutMapping("/{threadId}") public ResponseEntity<ExplorationThreadResponse> update(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.ThreadRequest request) { return ResponseEntity.ok(service.update(experimentId, threadId, request)); }
    @PutMapping("/order") public ResponseEntity<List<ExplorationThreadResponse>> reorder(@PathVariable Long experimentId, @Valid @RequestBody ExplorationThreadRequests.OrderRequest request) { return ResponseEntity.ok(service.reorder(experimentId, request)); }
    @PutMapping("/{threadId}/position") public ResponseEntity<Void> move(@PathVariable Long experimentId,
            @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.PositionRequest request) {
        service.move(experimentId, threadId, request); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/{threadId}") public ResponseEntity<Void> delete(@PathVariable Long experimentId, @PathVariable Long threadId) { service.delete(experimentId, threadId); return ResponseEntity.noContent().build(); }
    @PostMapping("/{threadId}/methods") public ResponseEntity<ExplorationThreadResponse> addMethod(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.MethodRequest request) { return ResponseEntity.ok(service.addMethod(experimentId, threadId, request)); }
    @PutMapping("/{threadId}/methods/{methodId}") public ResponseEntity<ExplorationThreadResponse> updateMethod(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long methodId, @Valid @RequestBody ExplorationThreadRequests.MethodRequest request) { return ResponseEntity.ok(service.updateMethod(experimentId, threadId, methodId, request)); }
    @DeleteMapping("/{threadId}/methods/{methodId}") public ResponseEntity<ExplorationThreadResponse> deleteMethod(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long methodId, @RequestParam(defaultValue = "false") boolean deleteRecords) { return ResponseEntity.ok(service.deleteMethod(experimentId, threadId, methodId, deleteRecords)); }
    @PostMapping("/{threadId}/records") public ResponseEntity<ExplorationThreadResponse> addRecord(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.RecordRequest request) { return ResponseEntity.ok(service.addRecord(experimentId, threadId, request)); }
    @PostMapping("/{threadId}/imports") public ResponseEntity<ExplorationThreadResponse> importRecords(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.ImportRequest request) { return ResponseEntity.ok(service.importRecords(experimentId, threadId, request)); }
    @PutMapping("/{threadId}/records/{recordId}") public ResponseEntity<ExplorationThreadResponse> updateRecord(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long recordId, @Valid @RequestBody ExplorationThreadRequests.RecordRequest request) { return ResponseEntity.ok(service.updateRecord(experimentId, threadId, recordId, request)); }
    @DeleteMapping("/{threadId}/records/{recordId}") public ResponseEntity<ExplorationThreadResponse> deleteRecord(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long recordId) { return ResponseEntity.ok(service.deleteRecord(experimentId, threadId, recordId)); }
    @PostMapping("/{threadId}/cards") public ResponseEntity<ExplorationThreadResponse> addCard(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.CardRequest request) { return ResponseEntity.ok(service.addCard(experimentId, threadId, request)); }
    @DeleteMapping("/{threadId}/cards/{linkId}") public ResponseEntity<ExplorationThreadResponse> removeCard(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long linkId) { return ResponseEntity.ok(service.removeCard(experimentId, threadId, linkId)); }
    @PostMapping("/{threadId}/works") public ResponseEntity<ExplorationThreadResponse> addWork(@PathVariable Long experimentId, @PathVariable Long threadId, @Valid @RequestBody ExplorationThreadRequests.WorkRequest request) { return ResponseEntity.ok(service.addWork(experimentId, threadId, request)); }
    @DeleteMapping("/{threadId}/works/{linkId}") public ResponseEntity<ExplorationThreadResponse> removeWork(@PathVariable Long experimentId, @PathVariable Long threadId, @PathVariable Long linkId) { return ResponseEntity.ok(service.removeWork(experimentId, threadId, linkId)); }
}
