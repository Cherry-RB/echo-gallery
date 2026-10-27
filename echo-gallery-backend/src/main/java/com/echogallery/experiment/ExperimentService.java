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

import com.echogallery.card.CardDetailResponse;
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
    private final UserRepository userRepository;
    private final ExperimentExplorationRepository experimentExplorationRepository;
    private final ExperimentExplorationRecordRepository experimentExplorationRecordRepository;
    private final ExplorationActivityService activity;
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

    @Transactional(readOnly = true)
    public ExperimentExplorationResponse getExploration(Long experimentId) { return activity.state(experimentId); }

    // 舊寫入契約不再猜測試法身份，避免舊分頁繼續產生副本或無聲改寫關聯。
    public CardDetailResponse createExplorationCard(Long id, ExperimentExplorationCardRequest request) { throw retired(); }
    public CardDetailResponse appendExplorationToCard(Long id, Long cardId, ExperimentExplorationAppendRequest request) { throw retired(); }
    public ExperimentExplorationResponse updateCurrentTry(Long id, ExperimentCurrentTryRequest request) { throw retired(); }
    public ExperimentExplorationResponse updateFavoriteTries(Long id, ExperimentFavoriteTriesRequest request) { throw retired(); }
    public ExperimentExplorationResponse createExplorationRecord(Long id, ExperimentExplorationRecordRequest request) { throw retired(); }
    public ExperimentExplorationResponse updateExplorationRecord(Long id, Long recordId, ExperimentExplorationRecordUpdateRequest request) { throw retired(); }
    public void deleteExplorationRecord(Long id, Long recordId) { throw retired(); }
    public void clearExploration(Long id) { throw retired(); }
    private ResponseStatusException retired() { return new ResponseStatusException(HttpStatus.GONE, "探索操作已更新，請重新載入並使用共用活動紀錄流程"); }

    private Experiment getOwnedExperiment(Long experimentId, Long userId) {
        Experiment experiment = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到實驗主題"));
        if (!experiment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "沒有權限存取這個實驗主題");
        }
        return experiment;
    }

    private Map<Long, String> getCurrentTries(Collection<Long> experimentIds) {
        if (experimentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        experimentExplorationRepository.currentTries(experimentIds)
                .forEach(exploration -> result.put(
                        exploration.getExperimentId(),
                        normalizeOptionalText(exploration.getCurrentTry())));
        return result;
    }

    private String getCurrentTry(Long experimentId) {
        return experimentExplorationRepository.currentTries(List.of(experimentId)).stream().findFirst()
                .map(ExperimentExplorationRepository.CurrentTry::getCurrentTry)
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

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
