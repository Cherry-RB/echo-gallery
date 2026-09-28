package com.echogallery.issue;

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
public class IssueUpdateController {

    private final IssueUpdateService updateService;

    @GetMapping("/issue-updates/recent")
    public ResponseEntity<List<IssueUpdateResponse>> getRecentUpdates(
            @RequestParam(name = "limit", defaultValue = "12") int limit) {
        return ResponseEntity.ok(updateService.getRecentUpdates(limit));
    }

    @GetMapping("/issues/{issueId}/updates")
    public ResponseEntity<IssueUpdatePageResponse> getUpdates(
            @PathVariable("issueId") Long issueId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        return ResponseEntity.ok(updateService.getUpdates(issueId, page, size));
    }

    @PostMapping("/issues/{issueId}/updates")
    public ResponseEntity<IssueUpdateResponse> createUpdate(
            @PathVariable("issueId") Long issueId,
            @Valid @RequestBody IssueUpdateRequest request) {
        return ResponseEntity.ok(updateService.createUpdate(issueId, request));
    }

    @PutMapping("/issues/{issueId}/updates/{updateId}")
    public ResponseEntity<IssueUpdateResponse> updateUpdate(
            @PathVariable("issueId") Long issueId,
            @PathVariable("updateId") Long updateId,
            @Valid @RequestBody IssueUpdateRequest request) {
        return ResponseEntity.ok(updateService.updateUpdate(issueId, updateId, request));
    }

    @DeleteMapping("/issues/{issueId}/updates/{updateId}")
    public ResponseEntity<Void> deleteUpdate(
            @PathVariable("issueId") Long issueId,
            @PathVariable("updateId") Long updateId) {
        updateService.deleteUpdate(issueId, updateId);
        return ResponseEntity.noContent().build();
    }
}
