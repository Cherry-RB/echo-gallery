package com.echogallery.experiment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.Card;
import com.echogallery.card.CardDetailResponse;
import com.echogallery.card.CardRepository;
import com.echogallery.card.CardService;
import com.echogallery.card.PageResponse;
import com.echogallery.util.SecurityUtil;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExperimentService {

    private static final int MAX_PAGE_SIZE = 20;

    private final ExperimentRepository experimentRepository;
    private final ExperimentCardRepository experimentCardRepository;
    private final CardRelationRepository cardRelationRepository;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<ExperimentResponse> getExperiments(boolean archived, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        Page<Experiment> experiments = experimentRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDescIdDesc(
                SecurityUtil.getCurrentUserId(), archived, PageRequest.of(page, size));
        Map<Long, ExperimentStageCount> counts = getCounts(experiments.getContent().stream().map(Experiment::getId).toList());
        return new PageResponse<>(
                experiments.getContent().stream().map(experiment -> toExperimentResponse(experiment, counts.get(experiment.getId()))).toList(),
                page,
                size,
                experiments.getTotalElements(),
                experiments.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExperimentResponse getExperiment(Long experimentId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        return toExperimentResponse(experiment, getCounts(List.of(experimentId)).get(experimentId));
    }

    @Transactional
    public ExperimentResponse createExperiment(ExperimentRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userRepository.getReferenceById(userId);
        Experiment experiment = Experiment.builder()
                .user(user)
                .title(request.getTitle().trim())
                .description(normalizeOptionalText(request.getDescription()))
                .hypothesis(normalizeOptionalText(request.getHypothesis()))
                .themeColor(request.getThemeColor() == null ? ExperimentThemeColor.LEAF : request.getThemeColor())
                .build();
        return toExperimentResponse(experimentRepository.save(experiment), null);
    }

    @Transactional
    public ExperimentResponse updateExperiment(Long experimentId, ExperimentRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experiment.setTitle(request.getTitle().trim());
        experiment.setDescription(normalizeOptionalText(request.getDescription()));
        experiment.setHypothesis(normalizeOptionalText(request.getHypothesis()));
        if (request.getThemeColor() != null) {
            experiment.setThemeColor(request.getThemeColor());
        }
        return toExperimentResponse(experiment, getCounts(List.of(experimentId)).get(experimentId));
    }

    @Transactional
    public ExperimentResponse setArchived(Long experimentId, boolean archived) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experiment.setArchived(archived);
        return toExperimentResponse(experiment, getCounts(List.of(experimentId)).get(experimentId));
    }

    @Transactional
    public void deleteExperiment(Long experimentId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        // 只移除實驗主題的組織與脈絡資料；卡片本體仍留在收藏庫。
        cardRelationRepository.deleteByExperimentId(experimentId);
        experimentCardRepository.deleteByExperimentId(experimentId);
        experimentRepository.delete(experiment);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExperimentCardResponse> getExperimentCards(
            Long experimentId, ExperimentStage stage, int requestedPage, int requestedSize) {
        getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        Page<ExperimentCard> cards = experimentCardRepository.findByExperimentIdAndStageOrderByAddedAtDescIdDesc(
                experimentId, stage, PageRequest.of(page, size));
        return new PageResponse<>(cards.getContent().stream().map(this::toExperimentCardResponse).toList(), page, size,
                cards.getTotalElements(), cards.getTotalPages());
    }

    @Transactional
    public ExperimentCardResponse addExperimentCard(Long experimentId, ExperimentCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Experiment experiment = getOwnedExperiment(experimentId, userId);
        Card card = getOwnedCard(request.getCardId(), userId);
        if (experimentCardRepository.existsByExperimentIdAndCardId(experimentId, card.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這張卡片已經在這個實驗主題裡");
        }
        ExperimentCard relation = ExperimentCard.builder()
                .experiment(experiment)
                .card(card)
                .stage(request.getStage())
                .note(normalizeOptionalText(request.getNote()))
                .build();
        return toExperimentCardResponse(experimentCardRepository.save(relation));
    }

    @Transactional
    public ExperimentCardResponse updateExperimentCardStage(Long experimentId, Long cardId, ExperimentCardStageRequest request) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        relation.setStage(request.getStage());
        return toExperimentCardResponse(relation);
    }

    @Transactional
    public ExperimentCardResponse updateExperimentCardNote(Long experimentId, Long cardId, ExperimentCardNoteRequest request) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        relation.setNote(normalizeOptionalText(request.getNote()));
        return toExperimentCardResponse(relation);
    }

    @Transactional
    public void removeExperimentCard(Long experimentId, Long cardId) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        // 關係的來源／產物都必須仍屬於同一個實驗主題；移出時一併移除其局部譜系。
        cardRelationRepository.deleteByExperimentIdAndCardId(experimentId, cardId);
        experimentCardRepository.delete(relation);
    }

    /** 在同一交易中建立卡片、種入目標土壤，並保留所有來源譜系。 */
    @Transactional
    public CardDetailResponse growCard(Long experimentId, ExperimentGrowRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Experiment experiment = getOwnedExperiment(experimentId, userId);
        List<Long> sourceIds = request.getSourceCardIds().stream().filter(Objects::nonNull).distinct().toList();
        List<ExperimentCard> sources = experimentCardRepository.findByExperimentIdAndCardIdIn(experimentId, sourceIds);
        if (sources.size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所有來源卡片都必須先放入這個實驗主題");
        }
        Card newCard = cardService.createCardEntity(request);
        ExperimentCard newExperimentCard = ExperimentCard.builder()
                .experiment(experiment)
                .card(newCard)
                .stage(request.getStage())
                .note(normalizeOptionalText(request.getNote()))
                .build();
        experimentCardRepository.save(newExperimentCard);
        List<CardRelation> relations = sources.stream()
                .map(source -> CardRelation.builder()
                        .experiment(experiment)
                        .sourceCard(source.getCard())
                        .derivedCard(newCard)
                        .relationType(CardRelationType.DERIVED_FROM)
                        .build())
                .toList();
        cardRelationRepository.saveAll(relations);
        return cardService.convertToDetailResponse(newCard);
    }

    @Transactional(readOnly = true)
    public List<CardExperimentResponse> getCardExperiments(Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedCard(cardId, userId);
        return loadCardExperiments(cardId, userId);
    }

    private List<CardExperimentResponse> loadCardExperiments(Long cardId, Long userId) {
        return experimentCardRepository.findByCardIdAndExperimentUserIdOrderByAddedAtDesc(cardId, userId).stream()
                .map(relation -> new CardExperimentResponse(
                        relation.getExperiment().getId(),
                        relation.getExperiment().getTitle(),
                        relation.getExperiment().getHypothesis(),
                        relation.getExperiment().isArchived(),
                        relation.getStage(),
                        relation.getNote(),
                        relation.getAddedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CardRelationResponse> getCardRelations(Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedCard(cardId, userId);
        return loadCardRelations(cardId, userId);
    }

    private List<CardRelationResponse> loadCardRelations(Long cardId, Long userId) {
        List<CardRelation> asSource = cardRelationRepository.findBySourceCardIdAndExperimentUserIdOrderByCreatedAtDesc(cardId, userId);
        List<CardRelation> asDerived = cardRelationRepository.findByDerivedCardIdAndExperimentUserIdOrderByCreatedAtDesc(cardId, userId);
        return java.util.stream.Stream.concat(asSource.stream(), asDerived.stream())
                .map(this::toCardRelationResponse)
                .sorted(java.util.Comparator.comparing(CardRelationResponse::createdAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public CardExperimentContextResponse getCardExperimentContext(Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedCard(cardId, userId);
        return new CardExperimentContextResponse(
                loadCardExperiments(cardId, userId),
                loadCardRelations(cardId, userId));
    }

    private Experiment getOwnedExperiment(Long experimentId, Long userId) {
        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到實驗主題"));
        if (!experiment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "沒有權限存取這個實驗主題");
        }
        return experiment;
    }

    private Card getOwnedCard(Long cardId, Long userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到卡片"));
        if (!card.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "沒有權限存取這張卡片");
        }
        return card;
    }

    private ExperimentCard getOwnedExperimentCard(Long experimentId, Long cardId, Long userId) {
        getOwnedExperiment(experimentId, userId);
        return experimentCardRepository.findByExperimentIdAndCardId(experimentId, cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆種植關係"));
    }

    private Map<Long, ExperimentStageCount> getCounts(Collection<Long> experimentIds) {
        if (experimentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ExperimentStageCount> result = new HashMap<>();
        experimentCardRepository.countByExperimentIds(experimentIds).forEach(count -> result.put(count.getExperimentId(), count));
        return result;
    }

    private ExperimentResponse toExperimentResponse(Experiment experiment, ExperimentStageCount count) {
        return new ExperimentResponse(
                experiment.getId(), experiment.getTitle(), experiment.getDescription(), experiment.getHypothesis(),
                experiment.getThemeColor() == null ? ExperimentThemeColor.LEAF : experiment.getThemeColor(), experiment.isArchived(),
                count == null ? 0 : count.getSeedCount(),
                count == null ? 0 : count.getGrowingCount(),
                count == null ? 0 : count.getMatureCount(),
                experiment.getCreatedAt(), experiment.getUpdatedAt());
    }

    private ExperimentCardResponse toExperimentCardResponse(ExperimentCard relation) {
        Card card = relation.getCard();
        return new ExperimentCardResponse(
                card.getId(), card.getType(), card.getTitle(), card.getReason(), card.getSummary(),
                card.getTags().stream().map(tag -> tag.getName()).sorted().toList(),
                card.isArchived(), card.getIntervalDays(), card.getNextShowAt(), card.isNeedsProcessing(),
                relation.getStage(), relation.getNote(), relation.getAddedAt());
    }

    private CardRelationResponse toCardRelationResponse(CardRelation relation) {
        return new CardRelationResponse(
                relation.getId(), relation.getExperiment().getId(), relation.getExperiment().getTitle(),
                relation.getExperiment().getHypothesis(), relation.getRelationType(),
                toLineageCard(relation.getSourceCard()), toLineageCard(relation.getDerivedCard()), relation.getCreatedAt());
    }

    private CardLineageCardResponse toLineageCard(Card card) {
        return new CardLineageCardResponse(card.getId(), card.getTitle(), card.getType(), card.isArchived());
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
