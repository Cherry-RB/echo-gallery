package com.echogallery.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.PageResponse;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

/** 探索紀錄的 owner、版本與重送保護集中於此，避免 UI 依賴舊探索線。 */
@Service
@RequiredArgsConstructor
public class ExperimentRecordService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ExperimentRepository experiments;
    private final ExperimentRecordRepository records;
    private final com.echogallery.work.WorkSourceRepository workSources;
    private final com.echogallery.goal.GoalActivityRecorder goalActivity;

    @Transactional(readOnly = true)
    public PageResponse<ExperimentRecordResponse> list(Long experimentId, int requestedPage, int requestedSize) {
        return list(experimentId, "", null, null, null, null, null, java.util.List.of(), "ANY", null, requestedPage, requestedSize, "recordedOn,DESC");
    }

    @Transactional(readOnly = true)
    public PageResponse<ExperimentRecordResponse> list(Long experimentId, String keyword, Boolean understandingNode,
            Boolean methodCandidate, ExperimentRecordDraft.Highlight highlight, java.time.LocalDate dateFrom,
            java.time.LocalDate dateTo, java.util.List<Long> requestedTopicIds, String requestedTopicMatch,
            Boolean unclassified, int requestedPage, int requestedSize, String sort) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "起始日期不可晚於結束日期");
        }
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, MAX_PAGE_SIZE));
        var field = "updatedAt".equalsIgnoreCase(sort == null ? "" : sort.split(",")[0]) ? "updatedAt" : "recordedOn";
        var direction = sort != null && sort.toUpperCase(java.util.Locale.ROOT).endsWith("ASC") ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        var topicIds = requestedTopicIds == null ? java.util.List.<Long>of() : requestedTopicIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!topicIds.isEmpty() && Boolean.TRUE.equals(unclassified)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主題與未分類條件不可同時使用");
        }
        String topicMatch = "ALL".equalsIgnoreCase(requestedTopicMatch) ? "ALL" : "ANY";
        var result = records.search(experimentId, owner(), keyword == null ? "" : keyword.trim(), understandingNode, methodCandidate, highlight, dateFrom, dateTo,
                topicIds.isEmpty() ? java.util.List.of(-1L) : topicIds, topicIds.isEmpty(), topicMatch, topicIds.size(), unclassified,
                PageRequest.of(page, size, org.springframework.data.domain.Sort.by(new org.springframework.data.domain.Sort.Order(direction, field), org.springframework.data.domain.Sort.Order.desc("id"))));
        return new PageResponse<>(result.getContent().stream().map(ExperimentRecordResponse::from).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExperimentRecordResponse get(Long experimentId, Long recordId) {
        return ExperimentRecordResponse.from(ownedRecord(experimentId, recordId));
    }

    @Transactional(readOnly = true)
    public java.util.List<String> recentPractices(Long experimentId) {
        getOwnedExperiment(experimentId);
        return records.findByExperimentIdAndExperimentUserIdAndPracticeTextIsNotNullOrderByCreatedAtDescIdDesc(
                        experimentId, owner(), PageRequest.of(0, 100))
                .stream().map(ExperimentRecord::getPracticeText)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        value -> value.strip().replaceAll("\\s+", " "), value -> value.strip(),
                        (first, ignored) -> first, java.util.LinkedHashMap::new))
                .values().stream().limit(3).toList();
    }

    @Transactional
    public ExperimentRecordResponse create(Long experimentId, ExperimentRecordRequest request) {
        var draft = request.draft();
        if (request.requestId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新增探索紀錄需要 requestId");
        }
        var experiment = getOwnedExperiment(experimentId);
        String hash = hash(request);
        var existing = records.findByExperimentIdAndCreateRequestId(experimentId, request.requestId());
        if (existing.isPresent()) {
            if (!existing.get().getCreatePayloadHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一 requestId 的內容不同，請重新載入後再試");
            }
            return ExperimentRecordResponse.from(existing.get());
        }
        var created = records.saveAndFlush(new ExperimentRecord(experiment, request.requestId(), hash, draft));
        goalActivity.record("EXPERIMENT_RECORD_CREATED", "experiment-record:" + created.getId(),
                com.echogallery.goal.GoalData.Kind.EXPERIMENT, experimentId,
                java.util.Map.of("recordId", created.getId(), "recordedOn", created.getRecordedOn().toString()));
        return ExperimentRecordResponse.from(created);
    }

    @Transactional
    public ExperimentRecordResponse update(Long experimentId, Long recordId, ExperimentRecordRequest request) {
        var record = records.lockOwned(recordId, experimentId, owner()).orElseThrow(this::missing);
        if (request.expectedVersion() == null || request.expectedVersion() != record.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這筆紀錄已有較新內容，請保留草稿後重新載入");
        }
        record.apply(request.draft());
        records.flush();
        return ExperimentRecordResponse.from(record);
    }

    @Transactional
    public void delete(Long experimentId, Long recordId, long expectedVersion) {
        var record = records.lockOwned(recordId, experimentId, owner()).orElseThrow(this::missing);
        if (expectedVersion != record.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這筆紀錄已有較新內容，請重新載入");
        }
        if (workSources.existsByRecordId(recordId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此探索紀錄已被作品引用，請先移除作品來源後再刪除");
        }
        records.delete(record);
    }

    private Experiment getOwnedExperiment(Long experimentId) {
        return experiments.findByIdAndUserId(experimentId, owner()).orElseThrow(this::missing);
    }

    private ExperimentRecord ownedRecord(Long experimentId, Long recordId) {
        return records.findByIdAndExperimentIdAndExperimentUserId(recordId, experimentId, owner()).orElseThrow(this::missing);
    }

    private Long owner() {
        Long owner = SecurityUtil.getCurrentUserId();
        if (owner == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "尚未登入");
        return owner;
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索紀錄或實驗");
    }

    private static String hash(ExperimentRecordRequest request) {
        try {
            String value = String.join("\u0000", String.valueOf(request.title()), String.valueOf(request.content()),
                    String.valueOf(request.practiceText()), String.valueOf(request.recordedOn()),
                    String.valueOf(request.understandingNode()), String.valueOf(request.methodCandidate()),
                    String.valueOf(request.highlight()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
