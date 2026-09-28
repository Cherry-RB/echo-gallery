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
@RequestMapping("/api/issues/{issueId}/cards")
@RequiredArgsConstructor
public class IssueCardController {

    private final IssueCardService issueCardService;

    @GetMapping
    public ResponseEntity<IssueCardPageResponse> getCards(
            @PathVariable("issueId") Long issueId,
            @RequestParam(name = "status", defaultValue = "CANDIDATE") IssueCardStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(issueCardService.getCards(issueId, status, page, size));
    }

    @PostMapping
    public ResponseEntity<IssueCardResponse> addCard(
            @PathVariable("issueId") Long issueId,
            @Valid @RequestBody AddIssueCardRequest request) {
        return ResponseEntity.ok(issueCardService.addCard(issueId, request));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> removeCard(
            @PathVariable("issueId") Long issueId,
            @PathVariable("cardId") Long cardId) {
        issueCardService.removeCard(issueId, cardId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{cardId}/status")
    public ResponseEntity<IssueCardResponse> updateStatus(
            @PathVariable("issueId") Long issueId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody UpdateIssueCardStatusRequest request) {
        return ResponseEntity.ok(issueCardService.updateStatus(issueId, cardId, request));
    }

    @PutMapping("/{cardId}/note")
    public ResponseEntity<IssueCardResponse> updateNote(
            @PathVariable("issueId") Long issueId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody UpdateIssueCardNoteRequest request) {
        return ResponseEntity.ok(issueCardService.updateNote(issueId, cardId, request));
    }
}
