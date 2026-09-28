package com.echogallery.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Transactional
class IssueCardPersistenceTests extends IntegrationTestBase {

    @Autowired
    private IssueCardRepository issueCardRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void newRelationDefaultsToCandidateAndPersistsMetadata() {
        User user = createUser("default");
        Issue issue = createIssue(user, "測試作品");
        Card card = createCard(user, "測試卡片");

        IssueCard relation = IssueCard.builder()
                .issue(issue)
                .card(card)
                .note("作為作品的核心論點")
                .build();

        Long relationId = issueCardRepository.saveAndFlush(relation).getId();
        entityManager.clear();

        IssueCard persistedRelation = issueCardRepository.findById(relationId).orElseThrow();
        assertThat(persistedRelation.getIssue().getId()).isEqualTo(issue.getId());
        assertThat(persistedRelation.getCard().getId()).isEqualTo(card.getId());
        assertThat(persistedRelation.getStatus()).isEqualTo(IssueCardStatus.CANDIDATE);
        assertThat(persistedRelation.getNote()).isEqualTo("作為作品的核心論點");
        assertThat(persistedRelation.getLinkedAt()).isNotNull();
        assertThat(persistedRelation.getUsedAt()).isNull();
    }

    @Test
    void sameCardCanHaveIndependentStatusesInDifferentIssues() {
        User user = createUser("multiple-issues");
        Issue firstIssue = createIssue(user, "第一件作品");
        Issue secondIssue = createIssue(user, "第二件作品");
        Card card = createCard(user, "共用素材");
        ZonedDateTime usedAt = ZonedDateTime.now();

        issueCardRepository.save(IssueCard.builder()
                .issue(firstIssue)
                .card(card)
                .build());
        issueCardRepository.save(IssueCard.builder()
                .issue(secondIssue)
                .card(card)
                .status(IssueCardStatus.USED)
                .usedAt(usedAt)
                .build());
        issueCardRepository.flush();
        entityManager.clear();

        assertThat(issueCardRepository.findByIssueIdAndCardId(firstIssue.getId(), card.getId()))
                .get()
                .extracting(IssueCard::getStatus)
                .isEqualTo(IssueCardStatus.CANDIDATE);
        assertThat(issueCardRepository.findByIssueIdAndCardId(secondIssue.getId(), card.getId()))
                .get()
                .extracting(IssueCard::getStatus)
                .isEqualTo(IssueCardStatus.USED);
    }

    @Test
    void duplicateIssueAndCardPairIsRejectedByDatabase() {
        User user = createUser("duplicate");
        Issue issue = createIssue(user, "不可重複的作品");
        Card card = createCard(user, "不可重複的素材");

        issueCardRepository.saveAndFlush(IssueCard.builder()
                .issue(issue)
                .card(card)
                .build());

        assertThatThrownBy(() -> issueCardRepository.saveAndFlush(IssueCard.builder()
                .issue(issue)
                .card(card)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unlinkKeepsBothIssueAndCard() {
        User user = createUser("unlink");
        Issue issue = createIssue(user, "保留作品");
        Card card = createCard(user, "保留卡片");
        IssueCard relation = issueCardRepository.saveAndFlush(IssueCard.builder()
                .issue(issue)
                .card(card)
                .build());

        issueCardRepository.delete(relation);
        issueCardRepository.flush();
        entityManager.clear();

        assertThat(issueCardRepository.findById(relation.getId())).isEmpty();
        assertThat(issueRepository.findById(issue.getId())).isPresent();
        assertThat(cardRepository.findById(card.getId())).isPresent();
    }

    @Test
    void deletingCardRemovesOnlyItsRelations() {
        User user = createUser("delete-card");
        Issue issue = createIssue(user, "仍然存在的作品");
        Card card = createCard(user, "即將刪除的卡片");
        Long relationId = issueCardRepository.saveAndFlush(IssueCard.builder()
                .issue(issue)
                .card(card)
                .build()).getId();

        cardRepository.delete(card);
        cardRepository.flush();
        entityManager.clear();

        assertThat(issueCardRepository.findById(relationId)).isEmpty();
        assertThat(cardRepository.findById(card.getId())).isEmpty();
        assertThat(issueRepository.findById(issue.getId())).isPresent();
    }

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("issue-card-" + suffix);
        user.setEmail("issue-card-" + suffix + "@example.com");
        user.setPasswordHash("password-hash");
        return userRepository.saveAndFlush(user);
    }

    private Issue createIssue(User user, String title) {
        return issueRepository.saveAndFlush(Issue.builder()
                .user(user)
                .title(title)
                .build());
    }

    private Card createCard(User user, String title) {
        return cardRepository.saveAndFlush(Card.builder()
                .user(user)
                .type("note")
                .title(title)
                .build());
    }
}
