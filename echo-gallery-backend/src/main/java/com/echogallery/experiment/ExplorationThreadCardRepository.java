package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplorationThreadCardRepository extends JpaRepository<ExplorationThreadCard, Long> {
    interface ExplorationMaterialKey { Long getExperimentId(); Long getCardId(); }
    @Query("select distinct l.thread.experiment.id as experimentId, l.card.id as cardId from ExplorationThreadCard l where l.thread.experiment.user.id = :userId and l.card.id in :cardIds and l.role = com.echogallery.experiment.ExplorationThreadCardRole.RESULT")
    List<ExplorationMaterialKey> findExplorationMaterialKeys(@Param("userId") Long userId, @Param("cardIds") java.util.Collection<Long> cardIds);
    List<ExplorationThreadCard> findByThreadIdOrderByRoleAscSortOrderAscIdAsc(Long threadId);
    List<ExplorationThreadCard> findByCardIdAndThreadExperimentUserIdOrderByCreatedAtDesc(Long cardId, Long userId);
    Optional<ExplorationThreadCard> findByIdAndThreadId(Long id, Long threadId);
    boolean existsByThreadIdAndCardIdAndRole(Long threadId, Long cardId, ExplorationThreadCardRole role);
    long countByThreadIdAndRole(Long threadId, ExplorationThreadCardRole role);
    @Query("select link.thread.id, link.role, count(link) from ExplorationThreadCard link where link.thread.id in :threadIds group by link.thread.id, link.role")
    List<Object[]> countByThreadIds(@Param("threadIds") List<Long> threadIds);
    @Query(value = """
            select ranked.thread_id, ranked.card_id, ranked.card_title, ranked.card_note
            from (
                select thread_card.thread_id, source_card.id as card_id, source_card.title as card_title, thread_card.note as card_note,
                       row_number() over (partition by thread_card.thread_id order by thread_card.created_at desc, thread_card.id desc) as row_number
                from exploration_thread_cards thread_card
                join cards source_card on source_card.id = thread_card.card_id
                where thread_card.thread_id in (:threadIds) and thread_card.role = 'MATERIAL'
            ) ranked
            where ranked.row_number <= 5
            order by ranked.thread_id, ranked.row_number
            """, nativeQuery = true)
    List<Object[]> findRecentMaterialPreviewsByThreadIds(@Param("threadIds") List<Long> threadIds);
    @Query("""
            select link from ExplorationThreadCard link
            where link.thread.experiment.user.id = :userId
              and link.role = com.echogallery.experiment.ExplorationThreadCardRole.MATERIAL
              and link.createdAt >= :startAt and link.createdAt < :endAt
            order by link.createdAt desc, link.id desc
            """)
    List<ExplorationThreadCard> findRecentMaterials(@Param("userId") Long userId,
            @Param("startAt") ZonedDateTime startAt, @Param("endAt") ZonedDateTime endAt);
}
