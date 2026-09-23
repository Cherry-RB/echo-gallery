package com.echogallery.experiment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards/{cardId}")
@RequiredArgsConstructor
public class CardExperimentController {

    private final ExperimentService experimentService;

    @GetMapping({ "/experiments", "/gardens" })
    public ResponseEntity<List<CardExperimentResponse>> getExperiments(@PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(experimentService.getCardExperiments(cardId));
    }

    @GetMapping("/relations")
    public ResponseEntity<List<CardRelationResponse>> getRelations(@PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(experimentService.getCardRelations(cardId));
    }

    @GetMapping("/experiment-context")
    public ResponseEntity<CardExperimentContextResponse> getExperimentContext(@PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(experimentService.getCardExperimentContext(cardId));
    }
}
