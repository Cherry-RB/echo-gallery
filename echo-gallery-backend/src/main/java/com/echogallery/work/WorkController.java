package com.echogallery.work;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/works")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;

    @GetMapping
    public ResponseEntity<List<WorkSummaryResponse>> getWorks() {
        return ResponseEntity.ok(workService.getWorks());
    }

    @PostMapping
    public ResponseEntity<WorkDetailResponse> createWork(@Valid @RequestBody CreateWorkRequest request) {
        return ResponseEntity.ok(workService.createWork(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkDetailResponse> getWork(@PathVariable("id") Long workId) {
        return ResponseEntity.ok(workService.getWork(workId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkDetailResponse> updateWork(
            @PathVariable("id") Long workId,
            @Valid @RequestBody UpdateWorkRequest request) {
        return ResponseEntity.ok(workService.updateWork(workId, request));
    }
}
