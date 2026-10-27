package com.echogallery.work;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class WorkLifecycle {
    private final CreativeWorkRepository works;
    private final WorkSourceRepository sources;
    private final com.echogallery.experiment.ExplorationOperationReceiptRepository explorationReceipts;

    @Transactional
    public void clearOwner(Long owner) {
        explorationReceipts.deleteByUserId(owner);
        sources.deleteOwner(owner);
        works.deleteOwner(owner);
    }
}
