package com.echogallery.work;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkProgressUpdateController {

    private final WorkProgressUpdateService updateService;

    @GetMapping("/work-updates/recent")
    public ResponseEntity<List<WorkProgressUpdateResponse>> getRecentUpdates(
            @RequestParam(name = "limit", defaultValue = "12") int limit) {
        return ResponseEntity.ok(updateService.getRecentUpdates(limit));
    }

    @GetMapping("/works/{workId}/updates")
    public ResponseEntity<WorkProgressUpdatePageResponse> getUpdates(
            @PathVariable("workId") Long workId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        return ResponseEntity.ok(updateService.getUpdates(workId, page, size));
    }

    @PostMapping("/works/{workId}/updates")
    public ResponseEntity<WorkProgressUpdateResponse> createUpdate(
            @PathVariable("workId") Long workId,
            @Valid @RequestBody WorkProgressUpdateRequest request) {
        return ResponseEntity.ok(updateService.createUpdate(workId, request));
    }

    @PutMapping("/works/{workId}/updates/{updateId}")
    public ResponseEntity<WorkProgressUpdateResponse> updateUpdate(
            @PathVariable("workId") Long workId,
            @PathVariable("updateId") Long updateId,
            @Valid @RequestBody WorkProgressUpdateRequest request) {
        return ResponseEntity.ok(updateService.updateUpdate(workId, updateId, request));
    }

    @DeleteMapping("/works/{workId}/updates/{updateId}")
    public ResponseEntity<Void> deleteUpdate(
            @PathVariable("workId") Long workId,
            @PathVariable("updateId") Long updateId) {
        updateService.deleteUpdate(workId, updateId);
        return ResponseEntity.noContent().build();
    }
}
