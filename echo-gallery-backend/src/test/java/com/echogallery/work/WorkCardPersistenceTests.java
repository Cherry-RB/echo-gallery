package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.card.Card;
import com.echogallery.card.CardGrowthStatus;
import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Transactional
class WorkCardPersistenceTests extends IntegrationTestBase {

    @Autowired
    private WorkCardRepository workCardRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void newRelationDefaultsToCandidateAndPersistsMetadata() {
        User user = createUser("default");
        Work work = createWork(user, "測試作品");
        Card card = createCard(user, "測試卡片");

        WorkCard relation = WorkCard.builder()
                .work(work)
                .card(card)
                .note("作為作品的核心論點")
                .build();

        Long relationId = workCardRepository.saveAndFlush(relation).getId();
        entityManager.clear();

        WorkCard persistedRelation = workCardRepository.findById(relationId).orElseThrow();
        assertThat(persistedRelation.getWork().getId()).isEqualTo(work.getId());
        assertThat(persistedRelation.getCard().getId()).isEqualTo(card.getId());
        assertThat(persistedRelation.getStatus()).isEqualTo(WorkCardStatus.CANDIDATE);
        assertThat(persistedRelation.getNote()).isEqualTo("作為作品的核心論點");
        assertThat(persistedRelation.getLinkedAt()).isNotNull();
        assertThat(persistedRelation.getUsedAt()).isNull();
    }

    @Test
    void sameCardCanHaveIndependentStatusesInDifferentWorks() {
        User user = createUser("multiple-works");
        Work firstWork = createWork(user, "第一件作品");
        Work secondWork = createWork(user, "第二件作品");
        Card card = createCard(user, "共用素材");
        ZonedDateTime usedAt = ZonedDateTime.now();

        workCardRepository.save(WorkCard.builder()
                .work(firstWork)
                .card(card)
                .build());
        workCardRepository.save(WorkCard.builder()
                .work(secondWork)
                .card(card)
                .status(WorkCardStatus.USED)
                .usedAt(usedAt)
                .build());
        workCardRepository.flush();
        entityManager.clear();

        assertThat(workCardRepository.findByWorkIdAndCardId(firstWork.getId(), card.getId()))
                .get()
                .extracting(WorkCard::getStatus)
                .isEqualTo(WorkCardStatus.CANDIDATE);
        assertThat(workCardRepository.findByWorkIdAndCardId(secondWork.getId(), card.getId()))
                .get()
                .extracting(WorkCard::getStatus)
                .isEqualTo(WorkCardStatus.USED);
        assertThat(cardRepository.findById(card.getId()))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.UNMARKED);
    }

    @Test
    void duplicateWorkAndCardPairIsRejectedByDatabase() {
        User user = createUser("duplicate");
        Work work = createWork(user, "不可重複的作品");
        Card card = createCard(user, "不可重複的素材");

        workCardRepository.saveAndFlush(WorkCard.builder()
                .work(work)
                .card(card)
                .build());

        assertThatThrownBy(() -> workCardRepository.saveAndFlush(WorkCard.builder()
                .work(work)
                .card(card)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unlinkKeepsBothWorkAndCard() {
        User user = createUser("unlink");
        Work work = createWork(user, "保留作品");
        Card card = createCard(user, "保留卡片");
        WorkCard relation = workCardRepository.saveAndFlush(WorkCard.builder()
                .work(work)
                .card(card)
                .build());

        workCardRepository.delete(relation);
        workCardRepository.flush();
        entityManager.clear();

        assertThat(workCardRepository.findById(relation.getId())).isEmpty();
        assertThat(workRepository.findById(work.getId())).isPresent();
        assertThat(cardRepository.findById(card.getId())).isPresent();
    }

    @Test
    void deletingCardRemovesOnlyItsRelations() {
        User user = createUser("delete-card");
        Work work = createWork(user, "仍然存在的作品");
        Card card = createCard(user, "即將刪除的卡片");
        Long relationId = workCardRepository.saveAndFlush(WorkCard.builder()
                .work(work)
                .card(card)
                .build()).getId();

        cardRepository.delete(card);
        cardRepository.flush();
        entityManager.clear();

        assertThat(workCardRepository.findById(relationId)).isEmpty();
        assertThat(cardRepository.findById(card.getId())).isEmpty();
        assertThat(workRepository.findById(work.getId())).isPresent();
    }

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("work-card-" + suffix);
        user.setEmail("work-card-" + suffix + "@example.com");
        user.setPasswordHash("password-hash");
        return userRepository.saveAndFlush(user);
    }

    private Work createWork(User user, String title) {
        return workRepository.saveAndFlush(Work.builder()
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
