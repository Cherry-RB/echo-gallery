package com.echogallery.experiment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 收藏卡片入口的原子建立流程；任一環節失敗時不留下半成品紀錄。 */
@Service
@RequiredArgsConstructor
public class ExperimentRecordCardService {
    private final ExperimentRecordService records;
    private final ExperimentRecordReferenceService references;

    @Transactional
    public ExperimentRecordResponse create(Long experimentId, ExperimentRecordFromCardRequest request) {
        var record = records.create(experimentId, request.record());
        references.create(experimentId, record.id(), new ExperimentRecordReferenceRequest(
                ExperimentRecordReference.Kind.CARD, request.cardId(), null, null));
        return record;
    }
}
