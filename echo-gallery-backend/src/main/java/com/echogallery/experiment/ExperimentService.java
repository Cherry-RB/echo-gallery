package com.echogallery.experiment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.ZonedDateTime;

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
    private final ExperimentExplorationRepository experimentExplorationRepository;
    private final ExperimentExplorationRecordRepository experimentExplorationRecordRepository;
    private final ExplorationRecordCardRepository explorationRecordCardRepository;

    @Transactional(readOnly = true)
    public PageResponse<ExperimentResponse> getExperiments(boolean archived, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        Page<Experiment> experiments = experimentRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDescIdDesc(
                SecurityUtil.getCurrentUserId(), archived, PageRequest.of(page, size));
        List<Long> experimentIds = experiments.getContent().stream().map(Experiment::getId).toList();
        Map<Long, ExperimentStageCount> counts = getCounts(experimentIds);
        Map<Long, String> currentTries = getCurrentTries(experimentIds);
        return new PageResponse<>(
                experiments.getContent().stream()
                        .map(experiment -> toExperimentResponse(
                                experiment,
                                counts.get(experiment.getId()),
                                currentTries.get(experiment.getId())))
                        .toList(),
                page,
                size,
                experiments.getTotalElements(),
                experiments.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExperimentResponse getExperiment(Long experimentId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        return toExperimentResponse(
                experiment,
                getCounts(List.of(experimentId)).get(experimentId),
                getCurrentTry(experimentId));
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
        return toExperimentResponse(experimentRepository.save(experiment), null, null);
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
        return toExperimentResponse(
                experiment,
                getCounts(List.of(experimentId)).get(experimentId),
                getCurrentTry(experimentId));
    }

    @Transactional
    public ExperimentResponse setArchived(Long experimentId, boolean archived) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experiment.setArchived(archived);
        return toExperimentResponse(
                experiment,
                getCounts(List.of(experimentId)).get(experimentId),
                getCurrentTry(experimentId));
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
        touchExperiment(experiment);
        return toExperimentCardResponse(experimentCardRepository.save(relation));
    }

    @Transactional
    public ExperimentCardResponse updateExperimentCardStage(Long experimentId, Long cardId, ExperimentCardStageRequest request) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        relation.setStage(request.getStage());
        touchExperiment(relation.getExperiment());
        return toExperimentCardResponse(relation);
    }

    @Transactional
    public ExperimentCardResponse updateExperimentCardNote(Long experimentId, Long cardId, ExperimentCardNoteRequest request) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        relation.setNote(normalizeOptionalText(request.getNote()));
        touchExperiment(relation.getExperiment());
        return toExperimentCardResponse(relation);
    }

    @Transactional
    public void removeExperimentCard(Long experimentId, Long cardId) {
        ExperimentCard relation = getOwnedExperimentCard(experimentId, cardId, SecurityUtil.getCurrentUserId());
        // 關係的來源／產物都必須仍屬於同一個實驗主題；移出時一併移除其局部譜系。
        cardRelationRepository.deleteByExperimentIdAndCardId(experimentId, cardId);
        experimentCardRepository.delete(relation);
        touchExperiment(relation.getExperiment());
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
        touchExperiment(experiment);
        return cardService.convertToDetailResponse(newCard);
    }

    /**
     * 將探索紀錄整理成新卡，並在同一筆交易中放入目前實驗場的茁壯土壤。
     */
    @Transactional
    public CardDetailResponse createExplorationCard(Long experimentId, ExperimentExplorationCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Experiment experiment = getOwnedExperiment(experimentId, userId);
        List<ExperimentExplorationRecord> records = getOwnedExplorationRecords(
                experimentId, request.getRecordIds());
        Card newCard = cardService.createCardEntity(request);
        ExperimentCard experimentCard = ExperimentCard.builder()
                .experiment(experiment)
                .card(newCard)
                .stage(ExperimentStage.GROWING)
                .build();
        experimentCardRepository.save(experimentCard);
        createExplorationCardLinks(records, newCard);
        touchExperiment(experiment);
        return cardService.convertToDetailResponse(newCard);
    }

    @Transactional(readOnly = true)
    public ExperimentExplorationResponse getExploration(Long experimentId) {
        getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        return toExplorationResponse(experimentId);
    }

    @Transactional
    public ExperimentExplorationResponse updateCurrentTry(Long experimentId, ExperimentCurrentTryRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        ExperimentExploration exploration = getOrCreateExploration(experiment);
        exploration.setCurrentTry(normalizeOptionalText(request.currentTry()));
        touchExperiment(experiment);
        return toExplorationResponse(experimentId);
    }

    @Transactional
    public ExperimentExplorationResponse updateFavoriteTries(
            Long experimentId,
            ExperimentFavoriteTriesRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        List<String> favoriteTries = request.favoriteTries() == null
                ? List.of()
                : request.favoriteTries().stream()
                        .map(this::normalizeOptionalText)
                        .filter(Objects::nonNull)
                        .distinct()
                        .limit(3)
                        .toList();
        ExperimentExploration exploration = getOrCreateExploration(experiment);
        exploration.setFavoriteTry1(favoriteTries.size() > 0 ? favoriteTries.get(0) : null);
        exploration.setFavoriteTry2(favoriteTries.size() > 1 ? favoriteTries.get(1) : null);
        exploration.setFavoriteTry3(favoriteTries.size() > 2 ? favoriteTries.get(2) : null);
        touchExperiment(experiment);
        return toExplorationResponse(experimentId);
    }

    @Transactional
    public ExperimentExplorationResponse createExplorationRecord(
            Long experimentId,
            ExperimentExplorationRecordRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        ExperimentExploration exploration = experimentExplorationRepository.findByExperimentId(experimentId).orElse(null);
        String tryText = request.includeCurrentTry() && exploration != null
                ? normalizeOptionalText(exploration.getCurrentTry())
                : null;
        ExperimentExplorationRecord record = ExperimentExplorationRecord.builder()
                .experiment(experiment)
                .tryText(tryText)
                .discovery(request.discovery().trim())
                .build();
        experimentExplorationRecordRepository.save(record);
        if (tryText != null) {
            exploration.setCurrentTry(null);
        }
        touchExperiment(experiment);
        return toExplorationResponse(experimentId);
    }

    @Transactional
    public void deleteExplorationRecord(Long experimentId, Long recordId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        ExperimentExplorationRecord record = experimentExplorationRecordRepository
                .findByIdAndExperimentId(recordId, experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索紀錄"));
        experimentExplorationRecordRepository.delete(record);
        touchExperiment(experiment);
    }

    @Transactional
    public void clearExploration(Long experimentId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experimentExplorationRecordRepository.deleteByExperimentId(experimentId);
        experimentExplorationRepository.deleteByExperimentId(experimentId);
        touchExperiment(experiment);
    }

    @Transactional
    public CardDetailResponse appendExplorationToCard(
            Long experimentId,
            Long cardId,
            ExperimentExplorationAppendRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Experiment experiment = getOwnedExperiment(experimentId, userId);
        if (!experimentCardRepository.existsByExperimentIdAndCardId(experimentId, cardId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能整理到目前實驗場中的卡片");
        }
        Card card = cardRepository.findByIdForUpdate(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到卡片"));
        if (!card.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "沒有權限存取這張卡片");
        }
        List<ExperimentExplorationRecord> records = getOwnedExplorationRecords(experimentId, request.recordIds());
        boolean containsExistingLink = records.stream()
                .anyMatch(record -> explorationRecordCardRepository
                        .existsByExplorationRecordIdAndCardId(record.getId(), card.getId()));
        if (containsExistingLink) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "選取的探索紀錄已包含整理到這張卡片的內容");
        }
        String existingContent = normalizeOptionalText(card.getContent());
        card.setContent(existingContent == null
                ? request.content().trim()
                : existingContent + "\n\n---\n\n" + request.content().trim());
        createExplorationCardLinks(records, card);
        touchExperiment(experiment);
        return cardService.convertToDetailResponse(card);
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

    private Map<Long, String> getCurrentTries(Collection<Long> experimentIds) {
        if (experimentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        experimentExplorationRepository.findByExperimentIdIn(experimentIds)
                .forEach(exploration -> result.put(
                        exploration.getExperiment().getId(),
                        normalizeOptionalText(exploration.getCurrentTry())));
        return result;
    }

    private String getCurrentTry(Long experimentId) {
        return experimentExplorationRepository.findByExperimentId(experimentId)
                .map(ExperimentExploration::getCurrentTry)
                .map(this::normalizeOptionalText)
                .orElse(null);
    }

    private ExperimentResponse toExperimentResponse(
            Experiment experiment,
            ExperimentStageCount count,
            String currentTry) {
        return new ExperimentResponse(
                experiment.getId(), experiment.getTitle(), experiment.getDescription(), experiment.getHypothesis(),
                currentTry,
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

    private ExperimentExploration getOrCreateExploration(Experiment experiment) {
        return experimentExplorationRepository.findByExperimentId(experiment.getId())
                .orElseGet(() -> experimentExplorationRepository.save(
                        ExperimentExploration.builder().experiment(experiment).build()));
    }

    private List<ExperimentExplorationRecord> getOwnedExplorationRecords(
            Long experimentId,
            List<Long> requestedRecordIds) {
        List<Long> recordIds = requestedRecordIds.stream().filter(Objects::nonNull).distinct().toList();
        List<ExperimentExplorationRecord> records = experimentExplorationRecordRepository
                .findByExperimentIdAndIdIn(experimentId, recordIds);
        if (records.size() != recordIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所有探索紀錄都必須屬於目前實驗場");
        }
        return records;
    }

    private void createExplorationCardLinks(List<ExperimentExplorationRecord> records, Card card) {
        List<ExplorationRecordCard> links = records.stream()
                .filter(record -> !explorationRecordCardRepository
                        .existsByExplorationRecordIdAndCardId(record.getId(), card.getId()))
                .map(record -> ExplorationRecordCard.builder()
                        .explorationRecord(record)
                        .card(card)
                        .build())
                .toList();
        explorationRecordCardRepository.saveAll(links);
    }

    private ExperimentExplorationResponse toExplorationResponse(Long experimentId) {
        ExperimentExploration exploration = experimentExplorationRepository.findByExperimentId(experimentId).orElse(null);
        List<String> favoriteTries = exploration == null
                ? List.of()
                : java.util.stream.Stream.of(
                                exploration.getFavoriteTry1(),
                                exploration.getFavoriteTry2(),
                                exploration.getFavoriteTry3())
                        .filter(Objects::nonNull)
                        .toList();
        List<ExperimentExplorationResponse.RecordResponse> records = experimentExplorationRecordRepository
                .findByExperimentIdOrderByCreatedAtDescIdDesc(experimentId)
                .stream()
                .map(this::toExplorationRecordResponse)
                .toList();
        return new ExperimentExplorationResponse(
                exploration == null || exploration.getCurrentTry() == null ? "" : exploration.getCurrentTry(),
                favoriteTries,
                records);
    }

    private ExperimentExplorationResponse.RecordResponse toExplorationRecordResponse(
            ExperimentExplorationRecord record) {
        return new ExperimentExplorationResponse.RecordResponse(
                record.getId(),
                record.getTryText(),
                record.getDiscovery(),
                record.getCreatedAt(),
                record.getExports().stream()
                        .sorted(java.util.Comparator.comparing(ExplorationRecordCard::getCreatedAt).reversed())
                        .map(link -> new ExperimentExplorationResponse.CardExportResponse(
                                link.getCard().getId(),
                                link.getCard().getTitle(),
                                link.getCreatedAt()))
                        .toList());
    }

    private void touchExperiment(Experiment experiment) {
        experiment.setUpdatedAt(ZonedDateTime.now());
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
