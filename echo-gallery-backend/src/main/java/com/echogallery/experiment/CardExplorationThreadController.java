package com.echogallery.experiment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards/{cardId}/exploration-threads")
@RequiredArgsConstructor
public class CardExplorationThreadController {

    private final ExplorationThreadService service;

    @GetMapping
    public ResponseEntity<List<CardExplorationThreadResponse>> list(@PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(service.listByCard(cardId));
    }
}
