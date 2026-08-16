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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/works/{workId}/cards")
@RequiredArgsConstructor
public class WorkCardController {

    private final WorkCardService workCardService;

    @GetMapping
    public ResponseEntity<List<WorkCardResponse>> getCards(
            @PathVariable("workId") Long workId) {
        return ResponseEntity.ok(workCardService.getCards(workId));
    }

    @PostMapping
    public ResponseEntity<WorkCardResponse> addCard(
            @PathVariable("workId") Long workId,
            @Valid @RequestBody AddWorkCardRequest request) {
        return ResponseEntity.ok(workCardService.addCard(workId, request));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> removeCard(
            @PathVariable("workId") Long workId,
            @PathVariable("cardId") Long cardId) {
        workCardService.removeCard(workId, cardId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{cardId}/status")
    public ResponseEntity<WorkCardResponse> updateStatus(
            @PathVariable("workId") Long workId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody UpdateWorkCardStatusRequest request) {
        return ResponseEntity.ok(workCardService.updateStatus(workId, cardId, request));
    }
}
