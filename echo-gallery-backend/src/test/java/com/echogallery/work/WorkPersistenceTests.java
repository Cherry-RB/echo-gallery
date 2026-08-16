package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

class WorkPersistenceTests extends IntegrationTestBase {

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void newWorkDefaultsToIdeaAndPersistsMetadata() {
        User user = createUser("default");
        Work work = Work.builder()
                .user(user)
                .title("作品資料模型")
                .description("確認 Work 的基本欄位能正確持久化")
                .externalUrl("https://example.com/works/domain-model")
                .build();

        Long workId = workRepository.saveAndFlush(work).getId();
        entityManager.clear();

        Work persistedWork = workRepository.findById(workId).orElseThrow();
        assertThat(persistedWork.getUser().getId()).isEqualTo(user.getId());
        assertThat(persistedWork.getTitle()).isEqualTo("作品資料模型");
        assertThat(persistedWork.getDescription()).isEqualTo("確認 Work 的基本欄位能正確持久化");
        assertThat(persistedWork.getStatus()).isEqualTo(WorkStatus.IDEA);
        assertThat(persistedWork.getExternalUrl()).isEqualTo("https://example.com/works/domain-model");
        assertThat(persistedWork.getCompletedAt()).isNull();
        assertThat(persistedWork.getCreatedAt()).isNotNull();
        assertThat(persistedWork.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void persistsEveryWorkStatus() {
        User user = createUser("statuses");

        for (WorkStatus status : WorkStatus.values()) {
            Work work = Work.builder()
                    .user(user)
                    .title("作品狀態 " + status)
                    .status(status)
                    .build();

            Long workId = workRepository.saveAndFlush(work).getId();
            entityManager.clear();

            assertThat(workRepository.findById(workId))
                    .get()
                    .extracting(Work::getStatus)
                    .isEqualTo(status);
        }
    }

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("work-" + suffix);
        user.setEmail("work-" + suffix + "@example.com");
        user.setPasswordHash("password-hash");
        return userRepository.saveAndFlush(user);
    }
}
