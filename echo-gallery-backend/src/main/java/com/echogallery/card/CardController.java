package com.echogallery.card;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    /**
     * 卡片查詢
     * @param request pageNumer, paseSize
     * @return
     */
    @PostMapping("/list")
    public ResponseEntity<List<CardSummaryResponse>> cardList(@RequestBody CardListRequest request) {
        List<CardSummaryResponse> cards = cardService.getCardList(request);
        return ResponseEntity.ok(cards);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CardDetailResponse> getCardDetail(@PathVariable Long id) {
        CardDetailResponse response = cardService.getCardDetailById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CardDetailResponse> updateCard(@PathVariable("id") Long cardId, @RequestBody CardRequest request) {
        // @RequestBody 會自動把 Vue 傳來的 JSON 轉成 Java 的 CardRequest 物件
        CardDetailResponse response = cardService.updateCard(cardId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CardDetailResponse> createCard(@RequestBody CardRequest request) {
        // @RequestBody 會自動把 Vue 傳來的 JSON 轉成 Java 的 CardRequest 物件
        CardDetailResponse response = cardService.createCard(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CardDetailResponse> deleteCard(@PathVariable Long id) {
        CardDetailResponse response = cardService.deleteCardById(id);
        return ResponseEntity.ok(response);
    }

    ////////// 卡片互動 ////////////
    @PutMapping("/{id}/star")
    public ResponseEntity<CardDetailResponse> toggleCardStar(@PathVariable Long id, @RequestBody CardStatusRequest request) {
        CardDetailResponse response = cardService.toggleCardStar(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<CardDetailResponse> toggleCardArchive(@PathVariable Long id, @RequestBody CardStatusRequest request) {
        CardDetailResponse response = cardService.toggleCardArchive(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/snooze")
    public ResponseEntity<CardDetailResponse> snoozeCard(@PathVariable Long id, @RequestBody CardStatusRequest request) {
        CardDetailResponse response = cardService.snoozeCard(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<CardDetailResponse> readCard(@PathVariable Long id) {
        CardDetailResponse response = cardService.readCard(id);
        return ResponseEntity.ok(response);
    }
}
