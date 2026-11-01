package com.echogallery.goal;

import java.time.*;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.echogallery.user.User;

// 八類持久資料共享 owner；不在原物件維護重複的追蹤布林值。
@MappedSuperclass
abstract class GoalOwnedRow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) User user;
}

@Entity(name="GoalRow") @Table(name="goals", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","request_key"}))
class GoalRow extends GoalOwnedRow {
    @Column(nullable=false, columnDefinition="TEXT") String definition;
    @Column(name="starts_on", nullable=false) LocalDate startsOn;
    @Column(name="ends_on", nullable=false) LocalDate endsOn;
    @Column(nullable=false, length=20) String status;
    @Column(nullable=false) boolean archived;
    @Column(name="request_key", nullable=false, length=36) String requestKey;
    @Column(name="tracking_started_at", nullable=false) ZonedDateTime trackingStartedAt;
    @Column(name="closed_at") ZonedDateTime closedAt;
    @Column(nullable=false) long version;
}

@Entity(name="GoalRuleRow") @Table(name="goal_rule_revisions", uniqueConstraints=@UniqueConstraint(columnNames={"goal_id","revision"}))
class GoalRuleRow extends GoalOwnedRow {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="goal_id",nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) GoalRow goal;
    @Column(nullable=false) long revision;
    @Column(nullable=false,columnDefinition="TEXT") String definition;
    @Column(columnDefinition="TEXT") String reason;
    @Column(name="effective_at",nullable=false) ZonedDateTime effectiveAt;
}

@Entity(name="GoalSourceRow") @Table(name="goal_tracking_sources", indexes=@Index(columnList="kind,source_id"))
@org.hibernate.annotations.Check(constraints="kind in ('TAG','EXPERIMENT','THREAD','WORK','TREASURE','CARD') and (num_nonnulls(tag_id,experiment_id,thread_id,work_id,treasure_id,card_id)=0 or (num_nonnulls(tag_id,experiment_id,thread_id,work_id,treasure_id,card_id)=1 and coalesce((kind='TAG' and tag_id=source_id) or (kind='EXPERIMENT' and experiment_id=source_id) or (kind='THREAD' and thread_id=source_id) or (kind='WORK' and work_id=source_id) or (kind='TREASURE' and treasure_id=source_id) or (kind='CARD' and card_id=source_id),false)))")
class GoalSourceRow extends GoalOwnedRow {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="rule_id",nullable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) GoalRuleRow rule;
    @Column(nullable=false,length=20) String kind;
    @Column(name="source_id",nullable=false) Long sourceId;
    @Column(nullable=false) boolean excluded;
    // 即時來源各有真實外鍵；刪除時設空，歷史識別不當作活連結使用。
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tag_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.tag.Tag tag;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="experiment_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.experiment.Experiment experiment;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="thread_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.experiment.ExplorationThread thread;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="work_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.work.CreativeWork work;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="treasure_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.treasure.Treasure treasure;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="card_id") @OnDelete(action=OnDeleteAction.SET_NULL) com.echogallery.card.Card card;
}

@Entity(name="GoalFactRow") @Table(name="goal_activity_facts", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","fact_key"}))
class GoalFactRow extends GoalOwnedRow {
    @Column(nullable=false) boolean valid=true;
    @Column(name="fact_key",nullable=false,length=255) String factKey;
    @Column(nullable=false,length=40) String type;
    @Column(name="source_id",nullable=false) Long sourceId;
    @Column(name="occurred_at",nullable=false) ZonedDateTime occurredAt;
    @Column(name="recorded_at",nullable=false) ZonedDateTime recordedAt;
    @Column(nullable=false,columnDefinition="TEXT") String details;
}

@Entity(name="GoalFactLinkRow") @Table(name="goal_fact_links",uniqueConstraints=@UniqueConstraint(columnNames={"goal_id","fact_id"}))
class GoalFactLinkRow extends GoalOwnedRow {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="goal_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) GoalRow goal;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="rule_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) GoalRuleRow rule;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fact_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) GoalFactRow fact;
    @Column(nullable=false,columnDefinition="TEXT") String details;
}

@Entity(name="CardContentDayRow") @Table(name="card_content_days",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","card_id","modified_on"}))
class CardContentDayRow extends GoalOwnedRow {
    @Column(name="card_id",nullable=false) Long cardId;
    @Column(name="modified_on",nullable=false) LocalDate modifiedOn;
}

@Entity(name="GoalCardDayRow") @Table(name="goal_card_day_links",uniqueConstraints=@UniqueConstraint(columnNames={"goal_id","day_id","rule_id"}))
class GoalCardDayRow extends GoalOwnedRow {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="goal_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) GoalRow goal;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="rule_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) GoalRuleRow rule;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="day_id",nullable=false) @OnDelete(action=OnDeleteAction.CASCADE) CardContentDayRow day;
}

@Entity(name="GoalPreferenceRow") @Table(name="goal_board_preferences",uniqueConstraints=@UniqueConstraint(columnNames="user_id"))
class GoalPreferenceRow extends GoalOwnedRow {
    @Column(nullable=false,columnDefinition="TEXT") String selection;
    @Column(nullable=false) long version;
}
