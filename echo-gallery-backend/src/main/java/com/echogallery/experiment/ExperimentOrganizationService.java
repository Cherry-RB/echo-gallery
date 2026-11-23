package com.echogallery.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExperimentOrganizationService {
    private final ExperimentRepository experiments;
    private final ExperimentRecordRepository records;
    private final ExperimentTopicRepository topics;
    private final ExperimentRecordTopicRepository recordTopics;
    private final ExperimentPinnedPracticeRepository practices;

    @Transactional(readOnly = true)
    public List<ExperimentOrganizationResponses.Topic> topics(Long experimentId) {
        owned(experimentId);
        return topics.findByExperimentIdOrdered(experimentId).stream().map(ExperimentOrganizationResponses.Topic::from).toList();
    }

    @Transactional
    public ExperimentOrganizationResponses.Topic createTopic(Long experimentId, ExperimentOrganizationRequests.Topic request) {
        var experiment = owned(experimentId);
        String name = request.name().trim();
        try { return ExperimentOrganizationResponses.Topic.from(topics.save(new ExperimentTopic(experiment, name, topics.maxDisplayOrderByExperimentId(experimentId) + 1))); }
        catch (org.springframework.dao.DataIntegrityViolationException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "相同主題已存在"); }
    }

    @Transactional
    public ExperimentOrganizationResponses.Topic pinTopic(Long experimentId, Long topicId, Integer slot) {
        if (slot != null && slot == 0) slot = null;
        if (slot != null && (slot < 1 || slot > 3)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "置頂位置必須介於 1 到 3");
        var topic = ownedTopic(experimentId, topicId);
        if (slot != null) {
            final Integer targetSlot = slot;
            topics.findByExperimentIdOrdered(experimentId).stream()
                    .filter(item -> targetSlot.equals(item.getPinnedSlot()) && !item.getId().equals(topicId)).forEach(item -> item.setPinnedSlot(null));
        }
        topic.setPinnedSlot(slot);
        return ExperimentOrganizationResponses.Topic.from(topic);
    }

    @Transactional
    public List<ExperimentOrganizationResponses.Topic> reorderTopics(Long experimentId, ExperimentOrganizationRequests.TopicOrder request) {
        owned(experimentId);
        var current = topics.findByExperimentIdOrdered(experimentId);
        var requested = request.topicIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        var currentIds = current.stream().map(ExperimentTopic::getId).collect(java.util.stream.Collectors.toSet());
        if (requested.size() != current.size() || !currentIds.equals(new java.util.HashSet<>(requested))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主題排序必須包含此實驗的全部主題且不可重複");
        }
        var byId = current.stream().collect(java.util.stream.Collectors.toMap(ExperimentTopic::getId, item -> item));
        for (int index = 0; index < requested.size(); index++) byId.get(requested.get(index)).setDisplayOrder(index + 1);
        return topics.findByExperimentIdOrdered(experimentId).stream().map(ExperimentOrganizationResponses.Topic::from).toList();
    }

    @Transactional
    public void deleteTopic(Long experimentId, Long topicId) {
        var topic = ownedTopic(experimentId, topicId);
        recordTopics.deleteByTopicId(topic.getId());
        topics.delete(topic);
    }

    @Transactional
    public List<ExperimentOrganizationResponses.Topic> setRecordTopics(Long experimentId, Long recordId, ExperimentOrganizationRequests.RecordTopics request) {
        ownedRecord(experimentId, recordId);
        Set<Long> ids = Set.copyOf(request.topicIds());
        var selected = ids.stream().map(id -> ownedTopic(experimentId, id)).toList();
        recordTopics.deleteByRecordId(recordId);
        selected.forEach(topic -> recordTopics.save(new ExperimentRecordTopic(owned(experimentId), ownedRecord(experimentId, recordId), topic)));
        return selected.stream().map(ExperimentOrganizationResponses.Topic::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExperimentOrganizationResponses.Topic> recordTopics(Long experimentId, Long recordId) {
        ownedRecord(experimentId, recordId);
        return recordTopics.findByRecordId(recordId).stream().map(link -> ExperimentOrganizationResponses.Topic.from(link.getTopic())).toList();
    }

    @Transactional(readOnly = true)
    public List<ExperimentOrganizationResponses.Practice> practices(Long experimentId) {
        owned(experimentId);
        return practices.findByExperimentIdOrderBySlotAsc(experimentId).stream().map(ExperimentOrganizationResponses.Practice::from).toList();
    }

    @Transactional
    public ExperimentOrganizationResponses.Practice pinPractice(Long experimentId, ExperimentOrganizationRequests.Practice request) {
        var experiment = owned(experimentId);
        String content = request.content().trim();
        String key = hash(content);
        var current = practices.findByExperimentIdOrderBySlotAsc(experimentId);
        if (current.stream().anyMatch(item -> item.getContentKey().equals(key))) throw new ResponseStatusException(HttpStatus.CONFLICT, "相同做法已置頂");
        if (current.size() >= 3) throw new ResponseStatusException(HttpStatus.CONFLICT, "最多可置頂三個做法");
        int slot = request.slot() == null ? current.size() + 1 : request.slot();
        if (slot < 1 || slot > 3 || current.stream().anyMatch(item -> item.getSlot() == slot)) throw new ResponseStatusException(HttpStatus.CONFLICT, "置頂位置不可用");
        return ExperimentOrganizationResponses.Practice.from(practices.save(new ExperimentPinnedPractice(experiment, content, key, slot)));
    }

    @Transactional
    public void unpinPractice(Long experimentId, Long practiceId) {
        var practice = practices.findByIdAndExperimentId(practiceId, experimentId).orElseThrow(this::missing);
        practices.delete(practice);
    }

    private Experiment owned(Long id) { return experiments.findByIdAndUserId(id, owner()).orElseThrow(this::missing); }
    private ExperimentTopic ownedTopic(Long experimentId, Long id) { owned(experimentId); return topics.findByIdAndExperimentId(id, experimentId).orElseThrow(this::missing); }
    private ExperimentRecord ownedRecord(Long experimentId, Long id) { return records.findByIdAndExperimentIdAndExperimentUserId(id, experimentId, owner()).orElseThrow(this::missing); }
    private Long owner() { Long id = SecurityUtil.getCurrentUserId(); if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要登入"); return id; }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到實驗資料"); }
    private static String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
}
