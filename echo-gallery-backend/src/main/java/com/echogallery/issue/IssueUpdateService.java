package com.echogallery.issue;

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
public class IssueUpdateService {

    private static final int MAX_RECENT_LIMIT = 50;
    private static final int MAX_ISSUE_UPDATE_PAGE_SIZE = 20;

    private final IssueUpdateRepository updateRepository;
    private final IssueRepository issueRepository;

    @Transactional(readOnly = true)
    public IssueUpdatePageResponse getUpdates(Long issueId, int requestedPage, int requestedSize) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_ISSUE_UPDATE_PAGE_SIZE));
        Slice<IssueUpdate> updateSlice = updateRepository
                .findByIssueIdOrderByCreatedAtDescIdDesc(issueId, PageRequest.of(page, size));
        return new IssueUpdatePageResponse(
                updateSlice.getContent().stream().map(this::toResponse).toList(),
                page,
                size,
                updateSlice.hasNext(),
                updateRepository.countByIssueId(issueId));
    }

    @Transactional(readOnly = true)
    public List<IssueUpdateResponse> getRecentUpdates(int requestedLimit) {
        Long userId = SecurityUtil.getCurrentUserId();
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RECENT_LIMIT));
        return updateRepository.findRecentByUserId(userId, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public IssueUpdateResponse createUpdate(Long issueId, IssueUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Issue issue = getOwnedIssue(issueId, userId);
        NormalizedContent content = normalizeAndValidate(request);

        IssueUpdate update = IssueUpdate.builder()
                .issue(issue)
                .changeSummary(content.changeSummary())
                .assessment(content.assessment())
                .nextStep(content.nextStep())
                .build();

        return toResponse(updateRepository.save(update));
    }

    @Transactional
    public IssueUpdateResponse updateUpdate(
            Long issueId,
            Long updateId,
            IssueUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        IssueUpdate update = updateRepository.findByIdAndIssueId(updateId, issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題更新不存在"));
        NormalizedContent content = normalizeAndValidate(request);

        update.setChangeSummary(content.changeSummary());
        update.setAssessment(content.assessment());
        update.setNextStep(content.nextStep());
        return toResponse(update);
    }

    @Transactional
    public void deleteUpdate(Long issueId, Long updateId) {
        Long userId = SecurityUtil.getCurrentUserId();
        getOwnedIssue(issueId, userId);
        IssueUpdate update = updateRepository.findByIdAndIssueId(updateId, issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題更新不存在"));
        updateRepository.delete(update);
    }

    private Issue getOwnedIssue(Long issueId, Long userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "議題不存在"));
        if (!issue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此議題");
        }
        return issue;
    }

    private NormalizedContent normalizeAndValidate(IssueUpdateRequest request) {
        String changeSummary = normalizeOptionalText(request.getChangeSummary());
        String assessment = normalizeOptionalText(request.getAssessment());
        String nextStep = normalizeOptionalText(request.getNextStep());
        if (changeSummary == null && assessment == null && nextStep == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請至少填寫一項系統訊號內容");
        }
        return new NormalizedContent(changeSummary, assessment, nextStep);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private IssueUpdateResponse toResponse(IssueUpdate update) {
        IssueUpdateResponse response = new IssueUpdateResponse();
        response.setId(update.getId());
        response.setIssueId(update.getIssue().getId());
        response.setIssueTitle(update.getIssue().getTitle());
        response.setChangeSummary(update.getChangeSummary());
        response.setAssessment(update.getAssessment());
        response.setNextStep(update.getNextStep());
        response.setCreatedAt(update.getCreatedAt());
        response.setUpdatedAt(update.getUpdatedAt());
        return response;
    }

    private record NormalizedContent(String changeSummary, String assessment, String nextStep) {}
}
