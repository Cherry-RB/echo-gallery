package com.echogallery.experiment;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.CardRepository;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

/** 參考資料的 owner scope 永遠由紀錄所屬實驗取得，不相信呼叫端帶入的 ID。 */
@Service
@RequiredArgsConstructor
public class ExperimentRecordReferenceService {
    public record CardUsage(Long referenceId, Long recordId, Long experimentId, String experimentTitle, String recordTitle, boolean experimentArchived) {}
    private final ExperimentRecordRepository records;
    private final ExperimentRecordReferenceRepository references;
    private final CardRepository cards;

    @Transactional(readOnly = true)
    public List<ExperimentRecordReferenceResponse> list(Long experimentId, Long recordId) {
        owned(experimentId, recordId);
        return references.findByRecordIdOrderByIdAsc(recordId).stream().map(ExperimentRecordReferenceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CardUsage> cardUsages(Long cardId) {
        return references.findCardUsages(cardId, owner()).stream().map(reference -> {
            var record = reference.getRecord();
            var experiment = record.getExperiment();
            String title = record.getTitle() == null || record.getTitle().isBlank() ? "探索紀錄" : record.getTitle();
            return new CardUsage(reference.getId(), record.getId(), experiment.getId(), experiment.getTitle(), title, experiment.isArchived());
        }).toList();
    }

    @Transactional
    public ExperimentRecordReferenceResponse create(Long experimentId, Long recordId, ExperimentRecordReferenceRequest request) {
        var record = owned(experimentId, recordId);
        var reference = new ExperimentRecordReference();
        reference.setRecord(record);
        apply(reference, request);
        reference.setCreatedAt(ZonedDateTime.now());
        return ExperimentRecordReferenceResponse.from(references.save(reference));
    }

    @Transactional
    public void delete(Long experimentId, Long recordId, Long referenceId) {
        owned(experimentId, recordId);
        var reference = references.findByIdAndRecordId(referenceId, recordId).orElseThrow(this::missing);
        references.delete(reference);
    }

    private void apply(ExperimentRecordReference reference, ExperimentRecordReferenceRequest request) {
        if (request.kind() == null) throw badRequest("參考資料類型為必填");
        reference.setKind(request.kind());
        reference.setLabel(blankToNull(request.label()));
        if (request.kind() == ExperimentRecordReference.Kind.CARD) {
            if (request.cardId() == null || blankToNull(request.url()) != null) throw badRequest("卡片參考僅能指定一張收藏卡片");
            var card = cards.findById(request.cardId()).filter(item -> item.getUser().getId().equals(owner())).orElseThrow(this::missing);
            reference.setCard(card);
            reference.setUrl(null);
            return;
        }
        if (request.cardId() != null || !validUrl(request.url())) throw badRequest("外部連結必須是有效的 http 或 https URL");
        reference.setCard(null);
        reference.setUrl(request.url().trim());
    }

    private ExperimentRecord owned(Long experimentId, Long recordId) {
        return records.findByIdAndExperimentIdAndExperimentUserId(recordId, experimentId, owner()).orElseThrow(this::missing);
    }

    private Long owner() {
        var owner = SecurityUtil.getCurrentUserId();
        if (owner == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "尚未登入");
        return owner;
    }

    private boolean validUrl(String value) {
        try {
            var uri = URI.create(value == null ? "" : value.trim());
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) { return false; }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定的探索紀錄或參考資料"); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
