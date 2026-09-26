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

    @GetMapping("/{experimentId}/exploration")
    public ResponseEntity<ExperimentExplorationResponse> getExploration(
            @PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.getExploration(experimentId));
    }

    @PutMapping("/{experimentId}/exploration/current-try")
    public ResponseEntity<ExperimentExplorationResponse> updateCurrentTry(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentCurrentTryRequest request) {
        return ResponseEntity.ok(experimentService.updateCurrentTry(experimentId, request));
    }

    @PutMapping("/{experimentId}/exploration/favorite-tries")
    public ResponseEntity<ExperimentExplorationResponse> updateFavoriteTries(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentFavoriteTriesRequest request) {
        return ResponseEntity.ok(experimentService.updateFavoriteTries(experimentId, request));
    }

    @PostMapping("/{experimentId}/exploration/records")
    public ResponseEntity<ExperimentExplorationResponse> createExplorationRecord(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentExplorationRecordRequest request) {
        return ResponseEntity.ok(experimentService.createExplorationRecord(experimentId, request));
    }

    @DeleteMapping("/{experimentId}/exploration/records/{recordId}")
    public ResponseEntity<Void> deleteExplorationRecord(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("recordId") Long recordId) {
        experimentService.deleteExplorationRecord(experimentId, recordId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{experimentId}/exploration")
    public ResponseEntity<Void> clearExploration(@PathVariable("experimentId") Long experimentId) {
        experimentService.clearExploration(experimentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{experimentId}/exploration/cards")
    public ResponseEntity<CardDetailResponse> createExplorationCard(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentExplorationCardRequest request) {
        return ResponseEntity.ok(experimentService.createExplorationCard(experimentId, request));
    }

    @PostMapping("/{experimentId}/exploration/cards/{cardId}")
    public ResponseEntity<CardDetailResponse> appendExplorationToCard(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody ExperimentExplorationAppendRequest request) {
        return ResponseEntity.ok(experimentService.appendExplorationToCard(experimentId, cardId, request));
    }
}
