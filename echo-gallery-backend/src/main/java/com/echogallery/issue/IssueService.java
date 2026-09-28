package com.echogallery.issue;

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
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueCardRepository issueCardRepository;
    private final IssueUpdateRepository progressUpdateRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<IssueSummaryResponse> getIssues() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<IssueSummaryResponse> summaries = issueRepository.findSummariesByUserId(
                userId,
                IssueCardStatus.CANDIDATE,
                IssueCardStatus.USED);
        Map<Long, IssueUpdate> latestUpdates = progressUpdateRepository
                .findLatestByUserId(userId)
                .stream()
                .collect(Collectors.toMap(update -> update.getIssue().getId(), Function.identity()));

        summaries.forEach(summary -> {
            IssueUpdate latest = latestUpdates.get(summary.getId());
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
    public IssueDetailResponse getIssue(Long issueId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return toDetailResponse(getOwnedIssue(issueId, userId));
    }

    @Transactional
    public IssueDetailResponse createIssue(CreateIssueRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userRepository.getReferenceById(userId);

        Issue issue = Issue.builder()
                .user(user)
                .title(request.getTitle().trim())
                .objective(normalizeOptionalText(request.getObjective()))
                .description(request.getDescription())
                .currentAssessment(normalizeOptionalText(request.getCurrentAssessment()))
                .outcomeCriteria(normalizeOptionalText(request.getOutcomeCriteria()))
                .externalUrl(normalizeOptionalText(request.getExternalUrl()))
                .build();

        return toDetailResponse(issueRepository.save(issue));
    }

    @Transactional
    public IssueDetailResponse updateIssue(Long issueId, UpdateIssueRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Issue issue = getOwnedIssue(issueId, userId);

        issue.setTitle(request.getTitle().trim());
        issue.setObjective(normalizeOptionalText(request.getObjective()));
        issue.setDescription(request.getDescription());
        issue.setCurrentAssessment(normalizeOptionalText(request.getCurrentAssessment()));
        issue.setOutcomeCriteria(normalizeOptionalText(request.getOutcomeCriteria()));
        issue.setExternalUrl(normalizeOptionalText(request.getExternalUrl()));
        updateStatus(issue, request.getStatus());

        return toDetailResponse(issue);
    }

    @Transactional
    public void deleteIssue(Long issueId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Issue issue = getOwnedIssue(issueId, userId);

        // 議題刪除只清除其更新與素材關聯，原始卡片仍保留在收藏庫。
        progressUpdateRepository.deleteByIssueId(issueId);
        issueCardRepository.deleteByIssueId(issueId);
        issueRepository.delete(issue);
    }

    private Issue getOwnedIssue(Long issueId, Long userId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在"));

        if (!issue.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "無權存取此作品");
        }
        return issue;
    }

    private void updateStatus(Issue issue, IssueStatus nextStatus) {
        if (nextStatus == IssueStatus.DONE && issue.getCompletedAt() == null) {
            issue.setCompletedAt(ZonedDateTime.now());
        } else if (nextStatus == IssueStatus.IDEA
                || nextStatus == IssueStatus.DRAFT
                || nextStatus == IssueStatus.ACTIVE) {
            issue.setCompletedAt(null);
        }
        issue.setStatus(nextStatus);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ZonedDateTime latestActivityAt(IssueSummaryResponse summary) {
        ZonedDateTime latestProgressAt = summary.getLatestProgressAt();
        if (latestProgressAt != null && latestProgressAt.isAfter(summary.getUpdatedAt())) {
            return latestProgressAt;
        }
        return summary.getUpdatedAt();
    }

    private IssueDetailResponse toDetailResponse(Issue issue) {
        IssueDetailResponse response = new IssueDetailResponse();
        response.setId(issue.getId());
        response.setTitle(issue.getTitle());
        response.setObjective(issue.getObjective());
        response.setDescription(issue.getDescription());
        response.setCurrentAssessment(issue.getCurrentAssessment());
        response.setOutcomeCriteria(issue.getOutcomeCriteria());
        response.setStatus(issue.getStatus());
        response.setExternalUrl(issue.getExternalUrl());
        response.setCompletedAt(issue.getCompletedAt());
        response.setCreatedAt(issue.getCreatedAt());
        response.setUpdatedAt(issue.getUpdatedAt());
        return response;
    }
}
