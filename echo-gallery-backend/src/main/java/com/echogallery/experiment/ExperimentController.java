package com.echogallery.experiment;

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

import com.echogallery.card.CardDetailResponse;
import com.echogallery.card.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({ "/api/experiments", "/api/gardens" })
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping
    public ResponseEntity<PageResponse<ExperimentResponse>> getExperiments(
            @RequestParam(name = "archived", defaultValue = "false") boolean archived,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(experimentService.getExperiments(archived, page, size));
    }

    @PostMapping
    public ResponseEntity<ExperimentResponse> createExperiment(@Valid @RequestBody ExperimentRequest request) {
        return ResponseEntity.ok(experimentService.createExperiment(request));
    }

    @GetMapping("/{experimentId}")
    public ResponseEntity<ExperimentResponse> getExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.getExperiment(experimentId));
    }

    @PutMapping("/{experimentId}")
    public ResponseEntity<ExperimentResponse> updateExperiment(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentRequest request) {
        return ResponseEntity.ok(experimentService.updateExperiment(experimentId, request));
    }

    @PutMapping("/{experimentId}/archive")
    public ResponseEntity<ExperimentResponse> archiveExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.setArchived(experimentId, true));
    }

    @PutMapping("/{experimentId}/restore")
    public ResponseEntity<ExperimentResponse> restoreExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.setArchived(experimentId, false));
    }

    @DeleteMapping("/{experimentId}")
    public ResponseEntity<Void> deleteExperiment(@PathVariable("experimentId") Long experimentId) {
        experimentService.deleteExperiment(experimentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{experimentId}/cards")
    public ResponseEntity<PageResponse<ExperimentCardResponse>> getCards(
            @PathVariable("experimentId") Long experimentId,
            @RequestParam(name = "stage") ExperimentStage stage,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(experimentService.getExperimentCards(experimentId, stage, page, size));
    }

    @PostMapping("/{experimentId}/cards")
    public ResponseEntity<ExperimentCardResponse> addCard(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentCardRequest request) {
        return ResponseEntity.ok(experimentService.addExperimentCard(experimentId, request));
    }

    @PutMapping("/{experimentId}/cards/{cardId}/stage")
    public ResponseEntity<ExperimentCardResponse> updateStage(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody ExperimentCardStageRequest request) {
        return ResponseEntity.ok(experimentService.updateExperimentCardStage(experimentId, cardId, request));
    }

    @PutMapping("/{experimentId}/cards/{cardId}/note")
    public ResponseEntity<ExperimentCardResponse> updateNote(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody ExperimentCardNoteRequest request) {
        return ResponseEntity.ok(experimentService.updateExperimentCardNote(experimentId, cardId, request));
    }

    @DeleteMapping("/{experimentId}/cards/{cardId}")
    public ResponseEntity<Void> removeCard(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("cardId") Long cardId) {
        experimentService.removeExperimentCard(experimentId, cardId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{experimentId}/grow")
    public ResponseEntity<CardDetailResponse> growCard(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentGrowRequest request) {
        return ResponseEntity.ok(experimentService.growCard(experimentId, request));
    }
}
