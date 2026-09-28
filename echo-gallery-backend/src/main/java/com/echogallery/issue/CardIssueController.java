package com.echogallery.issue;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards/{cardId}/issues")
@RequiredArgsConstructor
public class CardIssueController {

    private final IssueCardService issueCardService;

    @GetMapping
    public ResponseEntity<List<CardIssueResponse>> getIssues(
            @PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(issueCardService.getIssues(cardId));
    }
}
