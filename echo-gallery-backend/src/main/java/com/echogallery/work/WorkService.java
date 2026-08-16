package com.echogallery.work;

import java.time.ZonedDateTime;
import java.util.List;

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
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkSummaryResponse> getWorks() {
        Long userId = SecurityUtil.getCurrentUserId();
        return workRepository.findSummariesByUserId(
                userId,
                WorkCardStatus.CANDIDATE,
                WorkCardStatus.USED);
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
                .description(request.getDescription())
                .externalUrl(normalizeOptionalText(request.getExternalUrl()))
                .build();

        return toDetailResponse(workRepository.save(work));
    }

    @Transactional
    public WorkDetailResponse updateWork(Long workId, UpdateWorkRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Work work = getOwnedWork(workId, userId);

        work.setTitle(request.getTitle().trim());
        work.setDescription(request.getDescription());
        work.setExternalUrl(normalizeOptionalText(request.getExternalUrl()));
        updateStatus(work, request.getStatus());

        return toDetailResponse(work);
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

    private WorkDetailResponse toDetailResponse(Work work) {
        WorkDetailResponse response = new WorkDetailResponse();
        response.setId(work.getId());
        response.setTitle(work.getTitle());
        response.setDescription(work.getDescription());
        response.setStatus(work.getStatus());
        response.setExternalUrl(work.getExternalUrl());
        response.setCompletedAt(work.getCompletedAt());
        response.setCreatedAt(work.getCreatedAt());
        response.setUpdatedAt(work.getUpdatedAt());
        return response;
    }
}
