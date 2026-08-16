package com.echogallery.work;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards/{cardId}/works")
@RequiredArgsConstructor
public class CardWorkController {

    private final WorkCardService workCardService;

    @GetMapping
    public ResponseEntity<List<CardWorkResponse>> getWorks(
            @PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(workCardService.getWorks(cardId));
    }
}
