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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public ResponseEntity<List<IssueSummaryResponse>> getIssues() {
        return ResponseEntity.ok(issueService.getIssues());
    }

    @PostMapping
    public ResponseEntity<IssueDetailResponse> createIssue(@Valid @RequestBody CreateIssueRequest request) {
        return ResponseEntity.ok(issueService.createIssue(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueDetailResponse> getIssue(@PathVariable("id") Long issueId) {
        return ResponseEntity.ok(issueService.getIssue(issueId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IssueDetailResponse> updateIssue(
            @PathVariable("id") Long issueId,
            @Valid @RequestBody UpdateIssueRequest request) {
        return ResponseEntity.ok(issueService.updateIssue(issueId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIssue(@PathVariable("id") Long issueId) {
        issueService.deleteIssue(issueId);
        return ResponseEntity.noContent().build();
    }
}
