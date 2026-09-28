package com.echogallery.issue;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
public class IssueCardService {

    private static final int MAX_ISSUE_CARD_PAGE_SIZE = 20;

    private final IssueCardRepository issueCardRepository;
    private final IssueRepository issueRepository;
    private final CardRepository cardRepository;

    @Transactional(readOnly = true)
    public IssueCardPageResponse getCards(
            Long issueId,
            IssueCardStatus status,
            int requestedPage,
            int requestedSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_ISSUE_CARD_PAGE_SIZE));
        Page<IssueCard> cardPage = issueCardRepository.findByIssueIdAndStatusOrderByLinkedAtDescIdDesc(
                issueId,
                status,
                PageRequest.of(page, size));
        return new IssueCardPageResponse(
                cardPage.getContent().stream().map(this::toResponse).toList(),
                page,
                size,
                cardPage.getTotalElements(),
                cardPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<CardIssueResponse> getIssues(Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedCard(cardId, userId);
        return issueCardRepository.findByCardIdAndIssueUserIdOrderByLinkedAtDesc(cardId, userId).stream()
                .map(this::toCardIssueResponse)
                .toList();
    }

    @Transactional
    public IssueCardResponse addCard(Long issueId, AddIssueCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Issue issue = getOwnedIssue(issueId, userId);
        Card card = getOwnedCard(request.getCardId(), userId);

        if (issueCardRepository.existsByIssueIdAndCardId(issueId, card.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此卡片已加入作品");
        }

        IssueCard relation = IssueCard.builder()
                .issue(issue)
                .card(card)
                .note(normalizeOptionalText(request.getNote()))
                .build();

        return toResponse(issueCardRepository.save(relation));
    }

    @Transactional
    public void removeCard(Long issueId, Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        getOwnedCard(cardId, userId);

        IssueCard relation = issueCardRepository.findByIssueIdAndCardId(issueId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品素材關聯不存在"));
        issueCardRepository.delete(relation);
    }

    @Transactional
    public IssueCardResponse updateStatus(
            Long issueId,
            Long cardId,
            UpdateIssueCardStatusRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        getOwnedCard(cardId, userId);

        IssueCard relation = issueCardRepository.findByIssueIdAndCardId(issueId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品素材關聯不存在"));

        IssueCardStatus nextStatus = request.getStatus();
        if (nextStatus == IssueCardStatus.USED && relation.getUsedAt() == null) {
            relation.setUsedAt(ZonedDateTime.now());
        } else if (nextStatus == IssueCardStatus.CANDIDATE) {
            relation.setUsedAt(null);
        }
        relation.setStatus(nextStatus);

        return toResponse(relation);
    }

    @Transactional
    public IssueCardResponse updateNote(
            Long issueId,
            Long cardId,
            UpdateIssueCardNoteRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        getOwnedCard(cardId, userId);

        IssueCard relation = issueCardRepository.findByIssueIdAndCardId(issueId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品素材關聯不存在"));
        relation.setNote(normalizeOptionalText(request.getNote()));

        return toResponse(relation);
    }

    private Issue getOwnedIssue(Long issueId, Long userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));
        if (!issue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此作品");
        }
        return issue;
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

    private IssueCardResponse toResponse(IssueCard relation) {
        IssueCardResponse response = new IssueCardResponse();
        response.setId(relation.getId());
        response.setIssueId(relation.getIssue().getId());
        response.setCardId(relation.getCard().getId());
        response.setCardTitle(relation.getCard().getTitle());
        response.setCardType(relation.getCard().getType());
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

    private CardIssueResponse toCardIssueResponse(IssueCard relation) {
        CardIssueResponse response = new CardIssueResponse();
        response.setIssueId(relation.getIssue().getId());
        response.setIssueTitle(relation.getIssue().getTitle());
        response.setIssueStatus(relation.getIssue().getStatus());
        response.setStatus(relation.getStatus());
        response.setNote(relation.getNote());
        response.setLinkedAt(relation.getLinkedAt());
        response.setUsedAt(relation.getUsedAt());
        return response;
    }
}
