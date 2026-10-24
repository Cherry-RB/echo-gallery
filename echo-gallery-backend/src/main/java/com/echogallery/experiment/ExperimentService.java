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
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final UserRepository userRepository;
    private final ExperimentExplorationRepository experimentExplorationRepository;
    private final ExperimentExplorationRecordRepository experimentExplorationRecordRepository;
    private final ExplorationThreadMethodRepository explorationThreadMethodRepository;
    private final ExplorationRecordCardRepository explorationRecordCardRepository;
    private final com.echogallery.treasure.TreasureLifecycle treasureLifecycle;

    @Transactional(readOnly = true)
    public PageResponse<ExperimentResponse> getExperiments(Boolean archived, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        PageRequest pageable = PageRequest.of(page, size);
        Page<Experiment> experiments = archived == null
                ? experimentRepository.findByUserIdOrderByUpdatedAtDescIdDesc(SecurityUtil.getCurrentUserId(), pageable)
                : experimentRepository.findByUserIdAndIsArchivedOrderByUpdatedAtDescIdDesc(
                        SecurityUtil.getCurrentUserId(), archived, pageable);
        List<Long> experimentIds = experiments.getContent().stream().map(Experiment::getId).toList();
        Map<Long, String> currentTries = getCurrentTries(experimentIds);
        Map<Long, Long> explorationRecordCounts = getExplorationRecordCounts(experimentIds);
        return new PageResponse<>(
                experiments.getContent().stream()
                        .map(experiment -> toExperimentResponse(
                                experiment,
                                currentTries.get(experiment.getId()),
                                explorationRecordCounts.getOrDefault(experiment.getId(), 0L)))
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
                getCurrentTry(experimentId),
                getExplorationRecordCount(experimentId));
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
                .conclusion(normalizeOptionalText(request.getConclusion()))
                .themeColor(request.getThemeColor() == null ? ExperimentThemeColor.LEAF : request.getThemeColor())
                .build();
        return toExperimentResponse(experimentRepository.save(experiment), null, 0L);
    }

    @Transactional
    public ExperimentResponse updateExperiment(Long experimentId, ExperimentRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experiment.setTitle(request.getTitle().trim());
        experiment.setDescription(normalizeOptionalText(request.getDescription()));
        experiment.setHypothesis(normalizeOptionalText(request.getHypothesis()));
        experiment.setConclusion(normalizeOptionalText(request.getConclusion()));
        if (request.getThemeColor() != null) {
            experiment.setThemeColor(request.getThemeColor());
        }
        return toExperimentResponse(
                experiment,
                getCurrentTry(experimentId),
                getExplorationRecordCount(experimentId));
    }

    @Transactional
    public ExperimentResponse setArchived(Long experimentId, boolean archived) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        experiment.setArchived(archived);
        return toExperimentResponse(
                experiment,
                getCurrentTry(experimentId),
                getExplorationRecordCount(experimentId));
    }

    @Transactional
    public void deleteExperiment(Long experimentId) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        treasureLifecycle.detachExperiment(SecurityUtil.getCurrentUserId(), experimentId);
        // 只移除實驗主題的組織與脈絡資料；卡片本體仍留在收藏庫。
        experimentRepository.delete(experiment);
    }

    @Transactional
    public CardDetailResponse createExplorationCard(Long experimentId, ExperimentExplorationCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Experiment experiment = getOwnedExperiment(experimentId, userId);
        List<ExperimentExplorationRecord> records = getOwnedExplorationRecords(
                experimentId, request.getRecordIds());
        Card newCard = cardService.createCardEntity(request);
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
        exploration.setCurrentTrySourceMethod(resolveSourceMethod(experimentId, request.sourceMethodId()));
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
        ExplorationThreadMethod sourceMethod = exploration == null
                ? null
                : exploration.getCurrentTrySourceMethod();
        ExperimentExplorationRecord record = ExperimentExplorationRecord.builder()
                .experiment(experiment)
                .tryText(tryText)
                .sourceThreadMethod(sourceMethod)
                .discovery(request.discovery().trim())
                .build();
        experimentExplorationRecordRepository.save(record);
        if (tryText != null) {
            exploration.setCurrentTry(null);
            exploration.setCurrentTrySourceMethod(null);
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
    public ExperimentExplorationResponse updateExplorationRecord(
            Long experimentId,
            Long recordId,
            ExperimentExplorationRecordUpdateRequest request) {
        Experiment experiment = getOwnedExperiment(experimentId, SecurityUtil.getCurrentUserId());
        ExperimentExplorationRecord record = experimentExplorationRecordRepository
                .findByIdAndExperimentId(recordId, experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索紀錄"));
        String tryText = normalizeOptionalText(request.tryText());
        String discovery = normalizeOptionalText(request.discovery());
        if (tryText == null && discovery == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "試法與發現不可同時為空白");
        }
        record.setTryText(tryText);
        record.setDiscovery(discovery == null ? "" : discovery);
        touchExperiment(experiment);
        return toExplorationResponse(experimentId);
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

    private Map<Long, Long> getExplorationRecordCounts(Collection<Long> experimentIds) {
        if (experimentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        experimentExplorationRecordRepository.countByExperimentIds(experimentIds)
                .forEach(count -> result.put(count.getExperimentId(), count.getRecordCount()));
        return result;
    }

    private long getExplorationRecordCount(Long experimentId) {
        return experimentExplorationRecordRepository.countByExperimentId(experimentId);
    }

    private ExperimentResponse toExperimentResponse(
            Experiment experiment,
            String currentTry,
            long explorationRecordCount) {
        return new ExperimentResponse(
                experiment.getId(), experiment.getTitle(), experiment.getDescription(), experiment.getHypothesis(), experiment.getConclusion(),
                currentTry, explorationRecordCount,
                experiment.getThemeColor() == null ? ExperimentThemeColor.LEAF : experiment.getThemeColor(), experiment.isArchived(),
                experiment.getCreatedAt(), experiment.getUpdatedAt());
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
                exploration == null || exploration.getCurrentTrySourceMethod() == null
                        ? null
                        : exploration.getCurrentTrySourceMethod().getId(),
                favoriteTries,
                records);
    }

    private ExperimentExplorationResponse.RecordResponse toExplorationRecordResponse(
            ExperimentExplorationRecord record) {
        return new ExperimentExplorationResponse.RecordResponse(
                record.getId(),
                record.getTryText(),
                record.getSourceThreadMethod() == null ? null : record.getSourceThreadMethod().getId(),
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

    private ExplorationThreadMethod resolveSourceMethod(Long experimentId, Long sourceMethodId) {
        if (sourceMethodId == null) {
            return null;
        }
        return explorationThreadMethodRepository.findById(sourceMethodId)
                .filter(method -> method.getThread().getExperiment().getId().equals(experimentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到這個實驗的探索線試法"));
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
