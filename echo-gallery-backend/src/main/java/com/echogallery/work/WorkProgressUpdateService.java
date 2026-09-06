package com.echogallery.work;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkProgressUpdateService {

    private static final int MAX_RECENT_LIMIT = 50;
    private static final int MAX_WORK_UPDATE_PAGE_SIZE = 20;

    private final WorkProgressUpdateRepository updateRepository;
    private final WorkRepository workRepository;

    @Transactional(readOnly = true)
    public WorkProgressUpdatePageResponse getUpdates(Long workId, int requestedPage, int requestedSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_WORK_UPDATE_PAGE_SIZE));
        Slice<WorkProgressUpdate> updateSlice = updateRepository
                .findByWorkIdOrderByCreatedAtDescIdDesc(workId, PageRequest.of(page, size));
        return new WorkProgressUpdatePageResponse(
                updateSlice.getContent().stream().map(this::toResponse).toList(),
                page,
                size,
                updateSlice.hasNext());
    }

    @Transactional(readOnly = true)
    public List<WorkProgressUpdateResponse> getRecentUpdates(int requestedLimit) {
        Long userId = SecurityUtil.getCurrentUserId();
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RECENT_LIMIT));
        return updateRepository.findRecentByUserId(userId, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WorkProgressUpdateResponse createUpdate(Long workId, WorkProgressUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Work work = getOwnedWork(workId, userId);
        NormalizedContent content = normalizeAndValidate(request);

        WorkProgressUpdate update = WorkProgressUpdate.builder()
                .work(work)
                .changeSummary(content.changeSummary())
                .assessment(content.assessment())
                .nextStep(content.nextStep())
                .build();

        return toResponse(updateRepository.save(update));
    }

    @Transactional
    public WorkProgressUpdateResponse updateUpdate(
            Long workId,
            Long updateId,
            WorkProgressUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        WorkProgressUpdate update = updateRepository.findByIdAndWorkId(updateId, workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題更新不存在"));
        NormalizedContent content = normalizeAndValidate(request);

        update.setChangeSummary(content.changeSummary());
        update.setAssessment(content.assessment());
        update.setNextStep(content.nextStep());
        return toResponse(update);
    }

    @Transactional
    public void deleteUpdate(Long workId, Long updateId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedWork(workId, userId);
        WorkProgressUpdate update = updateRepository.findByIdAndWorkId(updateId, workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題更新不存在"));
        updateRepository.delete(update);
    }

    private Work getOwnedWork(Long workId, Long userId) {
        Work work = workRepository.findById(workId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題不存在"));
        if (!work.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此議題");
        }
        return work;
    }

    private NormalizedContent normalizeAndValidate(WorkProgressUpdateRequest request) {
        String changeSummary = normalizeOptionalText(request.getChangeSummary());
        String assessment = normalizeOptionalText(request.getAssessment());
        String nextStep = normalizeOptionalText(request.getNextStep());
        if (changeSummary == null && assessment == null && nextStep == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少填寫一項議題更新內容");
        }
        return new NormalizedContent(changeSummary, assessment, nextStep);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkProgressUpdateResponse toResponse(WorkProgressUpdate update) {
        WorkProgressUpdateResponse response = new WorkProgressUpdateResponse();
        response.setId(update.getId());
        response.setWorkId(update.getWork().getId());
        response.setWorkTitle(update.getWork().getTitle());
        response.setChangeSummary(update.getChangeSummary());
        response.setAssessment(update.getAssessment());
        response.setNextStep(update.getNextStep());
        response.setCreatedAt(update.getCreatedAt());
        response.setUpdatedAt(update.getUpdatedAt());
        return response;
    }

    private record NormalizedContent(String changeSummary, String assessment, String nextStep) {}
}
