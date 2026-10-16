package com.echogallery.treasure;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class TreasureLifecycle {
    private final UserRepository users;
    private final TreasureRepository treasures;
    private final TreasureTrialRepository trials;
    private final TreasureTrialAttemptRepository attempts;
    private final TreasureYieldRepository yields;
    private final TreasureRelationRepository relations;

    // 與 Demo 重設共用 owner 鎖，保護來源引用及跨成果關聯的一致性。
    @Transactional
    public User lockOwner(Long userId) {
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "使用者不存在"));
    }

    @Transactional
    public void detachCard(Long userId, Long cardId) {
        lockOwner(userId);
        treasures.detachCard(cardId);
        yields.detachCard(cardId);
    }

    @Transactional
    public void detachExperiment(Long userId, Long experimentId) {
        lockOwner(userId);
        treasures.detachExperiment(experimentId);
    }

    @Transactional
    public void clearOwner(Long userId) {
        lockOwner(userId);
        relations.deleteOwner(userId);
        attempts.deleteOwner(userId);
        trials.deleteOwner(userId);
        yields.deleteOwner(userId);
        treasures.deleteOwner(userId);
    }
}
