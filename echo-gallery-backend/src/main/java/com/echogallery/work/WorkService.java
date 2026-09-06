package com.echogallery.work;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkService {

    private final WorkRepository workRepository;
    private final WorkCardRepository workCardRepository;
    private final WorkProgressUpdateRepository progressUpdateRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkSummaryResponse> getWorks() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<WorkSummaryResponse> summaries = workRepository.findSummariesByUserId(
                userId,
                WorkCardStatus.CANDIDATE,
                WorkCardStatus.USED);
        Map<Long, WorkProgressUpdate> latestUpdates = progressUpdateRepository
                .findLatestByUserId(userId)
                .stream()
                .collect(Collectors.toMap(update -> update.getWork().getId(), Function.identity()));

        summaries.forEach(summary -> {
            WorkProgressUpdate latest = latestUpdates.get(summary.getId());
            if (latest == null) return;

            summary.setLatestProgressAt(latest.getCreatedAt());
            summary.setLatestProgressChangeSummary(latest.getChangeSummary());
            summary.setLatestProgressAssessment(latest.getAssessment());
            summary.setLatestProgressNextStep(latest.getNextStep());
        });
        return summaries.stream()
                .sorted(Comparator.comparing(this::latestActivityAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkDetailResponse getWork(Long workId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return toDetailResponse(getOwnedWork(workId, userId));
    }

    @Transactional
    public WorkDetailResponse createWork(CreateWorkRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userRepository.getReferenceById(userId);

        Work work = Work.builder()
                .user(user)
                .title(request.getTitle().trim())
                .objective(normalizeOptionalText(request.getObjective()))
                .description(request.getDescription())
                .currentAssessment(normalizeOptionalText(request.getCurrentAssessment()))
                .outcomeCriteria(normalizeOptionalText(request.getOutcomeCriteria()))
                .externalUrl(normalizeOptionalText(request.getExternalUrl()))
                .build();

        return toDetailResponse(workRepository.save(work));
    }

    @Transactional
    public WorkDetailResponse updateWork(Long workId, UpdateWorkRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Work work = getOwnedWork(workId, userId);

        work.setTitle(request.getTitle().trim());
        work.setObjective(normalizeOptionalText(request.getObjective()));
        work.setDescription(request.getDescription());
        work.setCurrentAssessment(normalizeOptionalText(request.getCurrentAssessment()));
        work.setOutcomeCriteria(normalizeOptionalText(request.getOutcomeCriteria()));
        work.setExternalUrl(normalizeOptionalText(request.getExternalUrl()));
        updateStatus(work, request.getStatus());

        return toDetailResponse(work);
    }

    @Transactional
    public void deleteWork(Long workId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Work work = getOwnedWork(workId, userId);

        // 議題刪除只清除其更新與素材關聯，原始卡片仍保留在收藏庫。
        progressUpdateRepository.deleteByWorkId(workId);
        workCardRepository.deleteByWorkId(workId);
        workRepository.delete(work);
    }

    private Work getOwnedWork(Long workId, Long userId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));

        if (!work.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此作品");
        }
        return work;
    }

    private void updateStatus(Work work, WorkStatus nextStatus) {
        if (nextStatus == WorkStatus.DONE && work.getCompletedAt() == null) {
            work.setCompletedAt(ZonedDateTime.now());
        } else if (nextStatus == WorkStatus.IDEA
                || nextStatus == WorkStatus.DRAFT
                || nextStatus == WorkStatus.ACTIVE) {
            work.setCompletedAt(null);
        }
        work.setStatus(nextStatus);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ZonedDateTime latestActivityAt(WorkSummaryResponse summary) {
        ZonedDateTime latestProgressAt = summary.getLatestProgressAt();
        if (latestProgressAt != null && latestProgressAt.isAfter(summary.getUpdatedAt())) {
            return latestProgressAt;
        }
        return summary.getUpdatedAt();
    }

    private WorkDetailResponse toDetailResponse(Work work) {
        WorkDetailResponse response = new WorkDetailResponse();
        response.setId(work.getId());
        response.setTitle(work.getTitle());
        response.setObjective(work.getObjective());
        response.setDescription(work.getDescription());
        response.setCurrentAssessment(work.getCurrentAssessment());
        response.setOutcomeCriteria(work.getOutcomeCriteria());
        response.setStatus(work.getStatus());
        response.setExternalUrl(work.getExternalUrl());
        response.setCompletedAt(work.getCompletedAt());
        response.setCreatedAt(work.getCreatedAt());
        response.setUpdatedAt(work.getUpdatedAt());
        return response;
    }
}
