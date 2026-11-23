package com.echogallery.experiment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.echogallery.card.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({ "/api/experiments", "/api/gardens" })
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;
    private final ExperimentRecordService experimentRecordService;
    private final ExperimentRecordCardService experimentRecordCardService;
    private final ExperimentRecordReferenceService experimentRecordReferenceService;
    private final ExperimentOrganizationService experimentOrganizationService;

    @GetMapping
    public ResponseEntity<PageResponse<ExperimentResponse>> getExperiments(
            @RequestParam(name = "archived", defaultValue = "false") String archived,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(experimentService.getExperiments(
                "all".equals(archived) ? null : Boolean.valueOf(archived), page, size));
    }

    @PostMapping
    public ResponseEntity<ExperimentResponse> createExperiment(@Valid @RequestBody ExperimentRequest request) {
        return ResponseEntity.ok(experimentService.createExperiment(request));
    }

    @GetMapping("/{experimentId}")
    public ResponseEntity<ExperimentResponse> getExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.getExperiment(experimentId));
    }

    @PutMapping("/{experimentId}")
    public ResponseEntity<ExperimentResponse> updateExperiment(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentRequest request) {
        return ResponseEntity.ok(experimentService.updateExperiment(experimentId, request));
    }

    @PutMapping("/{experimentId}/archive")
    public ResponseEntity<ExperimentResponse> archiveExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.setArchived(experimentId, true));
    }

    @PutMapping("/{experimentId}/restore")
    public ResponseEntity<ExperimentResponse> restoreExperiment(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentService.setArchived(experimentId, false));
    }

    @DeleteMapping("/{experimentId}")
    public ResponseEntity<Void> deleteExperiment(@PathVariable("experimentId") Long experimentId) {
        experimentService.deleteExperiment(experimentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{experimentId}/records")
    public ResponseEntity<PageResponse<ExperimentRecordResponse>> getRecords(
            @PathVariable("experimentId") Long experimentId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "understandingNode", required = false) Boolean understandingNode,
            @RequestParam(name = "methodCandidate", required = false) Boolean methodCandidate,
            @RequestParam(name = "highlight", required = false) ExperimentRecordDraft.Highlight highlight,
            @RequestParam(name = "dateFrom", required = false) java.time.LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) java.time.LocalDate dateTo,
            @RequestParam(name = "topicId", required = false) Long topicId,
            @RequestParam(name = "topicIds", defaultValue = "") String topicIds,
            @RequestParam(name = "topicMatch", defaultValue = "ANY") String topicMatch,
            @RequestParam(name = "unclassified", required = false) Boolean unclassified,
            @RequestParam(name = "sort", defaultValue = "recordedOn,DESC") String sort) {
        var parsedTopicIds = parseTopicIds(topicId, topicIds);
        return ResponseEntity.ok(experimentRecordService.list(experimentId, keyword, understandingNode, methodCandidate, highlight, dateFrom, dateTo, parsedTopicIds, topicMatch, unclassified, page, size, sort));
    }

    private static java.util.List<Long> parseTopicIds(Long topicId, String topicIds) {
        try {
            return java.util.stream.Stream.concat(
                    topicId == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(topicId),
                    java.util.Arrays.stream(topicIds.split(",")).filter(value -> !value.isBlank()).map(Long::valueOf))
                    .distinct().toList();
        } catch (NumberFormatException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "topicIds 必須是以逗號分隔的數字");
        }
    }

    @GetMapping("/{experimentId}/records/{recordId}")
    public ResponseEntity<ExperimentRecordResponse> getRecord(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("recordId") Long recordId) {
        return ResponseEntity.ok(experimentRecordService.get(experimentId, recordId));
    }

    @GetMapping("/{experimentId}/recent-practices")
    public ResponseEntity<java.util.List<String>> getRecentPractices(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentRecordService.recentPractices(experimentId));
    }

    @PostMapping("/{experimentId}/records")
    public ResponseEntity<ExperimentRecordResponse> createRecord(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentRecordRequest request) {
        return ResponseEntity.ok(experimentRecordService.create(experimentId, request));
    }

    @PostMapping("/{experimentId}/records/from-card")
    public ResponseEntity<ExperimentRecordResponse> createRecordFromCard(
            @PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentRecordFromCardRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(experimentRecordCardService.create(experimentId, request));
    }

    @PutMapping("/{experimentId}/records/{recordId}")
    public ResponseEntity<ExperimentRecordResponse> updateRecord(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("recordId") Long recordId,
            @Valid @RequestBody ExperimentRecordRequest request) {
        return ResponseEntity.ok(experimentRecordService.update(experimentId, recordId, request));
    }

    @DeleteMapping("/{experimentId}/records/{recordId}")
    public ResponseEntity<Void> deleteRecord(
            @PathVariable("experimentId") Long experimentId,
            @PathVariable("recordId") Long recordId,
            @RequestParam(name = "expectedVersion") long expectedVersion) {
        experimentRecordService.delete(experimentId, recordId, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{experimentId}/records/{recordId}/references")
    public ResponseEntity<java.util.List<ExperimentRecordReferenceResponse>> getRecordReferences(
            @PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId) {
        return ResponseEntity.ok(experimentRecordReferenceService.list(experimentId, recordId));
    }

    @PostMapping("/{experimentId}/records/{recordId}/references")
    public ResponseEntity<ExperimentRecordReferenceResponse> createRecordReference(
            @PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId,
            @Valid @RequestBody ExperimentRecordReferenceRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(experimentRecordReferenceService.create(experimentId, recordId, request));
    }

    @DeleteMapping("/{experimentId}/records/{recordId}/references/{referenceId}")
    public ResponseEntity<Void> deleteRecordReference(@PathVariable("experimentId") Long experimentId,
            @PathVariable("recordId") Long recordId, @PathVariable("referenceId") Long referenceId) {
        experimentRecordReferenceService.delete(experimentId, recordId, referenceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{experimentId}/topics")
    public ResponseEntity<java.util.List<ExperimentOrganizationResponses.Topic>> getTopics(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentOrganizationService.topics(experimentId));
    }

    @PostMapping("/{experimentId}/topics")
    public ResponseEntity<ExperimentOrganizationResponses.Topic> createTopic(@PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentOrganizationRequests.Topic request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(experimentOrganizationService.createTopic(experimentId, request));
    }

    @PutMapping("/{experimentId}/topics/{topicId}/pin")
    public ResponseEntity<ExperimentOrganizationResponses.Topic> pinTopic(@PathVariable("experimentId") Long experimentId,
            @PathVariable("topicId") Long topicId, @Valid @RequestBody ExperimentOrganizationRequests.Pin request) {
        return ResponseEntity.ok(experimentOrganizationService.pinTopic(experimentId, topicId, request.slot()));
    }

    @PutMapping("/{experimentId}/topics/order")
    public ResponseEntity<java.util.List<ExperimentOrganizationResponses.Topic>> reorderTopics(
            @PathVariable("experimentId") Long experimentId, @Valid @RequestBody ExperimentOrganizationRequests.TopicOrder request) {
        return ResponseEntity.ok(experimentOrganizationService.reorderTopics(experimentId, request));
    }

    @DeleteMapping("/{experimentId}/topics/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable("experimentId") Long experimentId, @PathVariable("topicId") Long topicId) {
        experimentOrganizationService.deleteTopic(experimentId, topicId); return ResponseEntity.noContent().build();
    }

    @GetMapping("/{experimentId}/records/{recordId}/topics")
    public ResponseEntity<java.util.List<ExperimentOrganizationResponses.Topic>> getRecordTopics(@PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId) {
        return ResponseEntity.ok(experimentOrganizationService.recordTopics(experimentId, recordId));
    }

    @PutMapping("/{experimentId}/records/{recordId}/topics")
    public ResponseEntity<java.util.List<ExperimentOrganizationResponses.Topic>> setRecordTopics(@PathVariable("experimentId") Long experimentId, @PathVariable("recordId") Long recordId,
            @Valid @RequestBody ExperimentOrganizationRequests.RecordTopics request) {
        return ResponseEntity.ok(experimentOrganizationService.setRecordTopics(experimentId, recordId, request));
    }

    @GetMapping("/{experimentId}/pinned-practices")
    public ResponseEntity<java.util.List<ExperimentOrganizationResponses.Practice>> getPinnedPractices(@PathVariable("experimentId") Long experimentId) {
        return ResponseEntity.ok(experimentOrganizationService.practices(experimentId));
    }

    @PostMapping("/{experimentId}/pinned-practices")
    public ResponseEntity<ExperimentOrganizationResponses.Practice> pinPractice(@PathVariable("experimentId") Long experimentId,
            @Valid @RequestBody ExperimentOrganizationRequests.Practice request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(experimentOrganizationService.pinPractice(experimentId, request));
    }

    @DeleteMapping("/{experimentId}/pinned-practices/{practiceId}")
    public ResponseEntity<Void> unpinPractice(@PathVariable("experimentId") Long experimentId, @PathVariable("practiceId") Long practiceId) {
        experimentOrganizationService.unpinPractice(experimentId, practiceId); return ResponseEntity.noContent().build();
    }

}
