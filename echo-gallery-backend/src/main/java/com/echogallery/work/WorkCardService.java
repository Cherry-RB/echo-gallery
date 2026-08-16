package com.echogallery.work;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkCardService {

    private final WorkCardRepository workCardRepository;
    private final WorkRepository workRepository;
    private final CardRepository cardRepository;

    @Transactional(readOnly = true)
    public List<WorkCardResponse> getCards(Long workId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        return workCardRepository.findByWorkIdOrderByLinkedAtDesc(workId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkCardResponse addCard(Long workId, AddWorkCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Work work = getOwnedWork(workId, userId);
        Card card = getOwnedCard(request.getCardId(), userId);

        if (workCardRepository.existsByWorkIdAndCardId(workId, card.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此卡片已加入作品");
        }

        WorkCard relation = WorkCard.builder()
                .work(work)
                .card(card)
                .note(normalizeOptionalText(request.getNote()))
                .build();

        return toResponse(workCardRepository.save(relation));
    }

    @Transactional
    public void removeCard(Long workId, Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        getOwnedCard(cardId, userId);

        WorkCard relation = workCardRepository.findByWorkIdAndCardId(workId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品素材關聯不存在"));
        workCardRepository.delete(relation);
    }

    @Transactional
    public WorkCardResponse updateStatus(
            Long workId,
            Long cardId,
            UpdateWorkCardStatusRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        getOwnedCard(cardId, userId);

        WorkCard relation = workCardRepository.findByWorkIdAndCardId(workId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品素材關聯不存在"));

        WorkCardStatus nextStatus = request.getStatus();
        if (nextStatus == WorkCardStatus.USED && relation.getUsedAt() == null) {
            relation.setUsedAt(ZonedDateTime.now());
        } else if (nextStatus == WorkCardStatus.CANDIDATE) {
            relation.setUsedAt(null);
        }
        relation.setStatus(nextStatus);

        return toResponse(relation);
    }

    private Work getOwnedWork(Long workId, Long userId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        if (!work.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此作品");
        }
        return work;
    }

    private Card getOwnedCard(Long cardId, Long userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));
        if (!card.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此卡片");
        }
        return card;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkCardResponse toResponse(WorkCard relation) {
        WorkCardResponse response = new WorkCardResponse();
        response.setId(relation.getId());
        response.setWorkId(relation.getWork().getId());
        response.setCardId(relation.getCard().getId());
        response.setCardTitle(relation.getCard().getTitle());
        response.setCardType(relation.getCard().getType());
        response.setCardGrowthStatus(relation.getCard().getGrowthStatus());
        response.setTags(relation.getCard().getTags().stream()
                .map(tag -> tag.getName())
                .sorted()
                .toList());
        response.setStatus(relation.getStatus());
        response.setNote(relation.getNote());
        response.setLinkedAt(relation.getLinkedAt());
        response.setUsedAt(relation.getUsedAt());
        return response;
    }
}
