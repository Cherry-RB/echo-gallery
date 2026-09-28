package com.echogallery.issue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

class IssuePersistenceTests extends IntegrationTestBase {

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void newIssueDefaultsToIdeaAndPersistsMetadata() {
        User user = createUser("default");
        Issue issue = Issue.builder()
                .user(user)
                .objective("驗證議題盤面欄位")
                .currentAssessment("目前需要最小結構化")
                .outcomeCriteria("所有欄位可正確保存")
                .title("作品資料模型")
                .description("確認 Issue 的基本欄位能正確持久化")
                .externalUrl("https://example.com/issues/domain-model")
                .build();

        Long issueId = issueRepository.saveAndFlush(issue).getId();
        entityManager.clear();

        Issue persistedIssue = issueRepository.findById(issueId).orElseThrow();
        assertThat(persistedIssue.getUser().getId()).isEqualTo(user.getId());
        assertThat(persistedIssue.getObjective()).isEqualTo("驗證議題盤面欄位");
        assertThat(persistedIssue.getCurrentAssessment()).isEqualTo("目前需要最小結構化");
        assertThat(persistedIssue.getOutcomeCriteria()).isEqualTo("所有欄位可正確保存");
        assertThat(persistedIssue.getTitle()).isEqualTo("作品資料模型");
        assertThat(persistedIssue.getDescription()).isEqualTo("確認 Issue 的基本欄位能正確持久化");
        assertThat(persistedIssue.getStatus()).isEqualTo(IssueStatus.IDEA);
        assertThat(persistedIssue.getExternalUrl()).isEqualTo("https://example.com/issues/domain-model");
        assertThat(persistedIssue.getCompletedAt()).isNull();
        assertThat(persistedIssue.getCreatedAt()).isNotNull();
        assertThat(persistedIssue.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void persistsEveryIssueStatus() {
        User user = createUser("statuses");

        for (IssueStatus status : IssueStatus.values()) {
            Issue issue = Issue.builder()
                    .user(user)
                    .title("作品狀態 " + status)
                    .status(status)
                    .build();

            Long issueId = issueRepository.saveAndFlush(issue).getId();
            entityManager.clear();

            assertThat(issueRepository.findById(issueId))
                    .get()
                    .extracting(Issue::getStatus)
                    .isEqualTo(status);
        }
    }

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("issue-" + suffix);
        user.setEmail("issue-" + suffix + "@example.com");
        user.setPasswordHash("password-hash");
        return userRepository.saveAndFlush(user);
    }
}
