package com.echogallery.experiment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 收藏卡片反向顯示已引用它的探索紀錄。 */
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class ExperimentRecordReferenceController {
    private final ExperimentRecordReferenceService service;

    @GetMapping("/{cardId}/experiment-record-references")
    public ResponseEntity<List<ExperimentRecordReferenceService.CardUsage>> cardUsages(@PathVariable("cardId") Long cardId) {
        return ResponseEntity.ok(service.cardUsages(cardId));
    }
}
