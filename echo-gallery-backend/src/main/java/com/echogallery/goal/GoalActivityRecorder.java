package com.echogallery.goal;

import static com.echogallery.goal.GoalData.*;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.echogallery.card.Card;

@Service @RequiredArgsConstructor
public class GoalActivityRecorder {
    private final ObjectProvider<GoalService> service;
    private final EntityManager em;

    @Transactional
    public void contentChanged(Card card) {
        GoalService goals=service.getObject();goals.lock();card.setContentUpdatedAt(goals.now());em.flush();
        var relevant=relevant(goals,Kind.CARD,card.getId());if(relevant.isEmpty())return;
        LocalDate day=goals.now().toLocalDate();
        var rows=em.createQuery("select d from CardContentDayRow d where d.user.id=:owner and d.cardId=:card and d.modifiedOn=:day",CardContentDayRow.class)
                .setParameter("owner",goals.owner()).setParameter("card",card.getId()).setParameter("day",day).getResultList();
        var row=rows.isEmpty()?new CardContentDayRow():rows.getFirst();
        if(row.id==null){row.user=card.getUser();row.cardId=card.getId();row.modifiedOn=day;em.persist(row);}
        for(var g:relevant) {
            var revision=revision(g);
            var count=em.createQuery("select count(l) from GoalCardDayRow l where l.goal=:goal and l.day=:day and l.rule=:rule",Long.class)
                    .setParameter("goal",g).setParameter("day",row).setParameter("rule",revision).getSingleResult();
            if(count==0){var link=new GoalCardDayRow();link.user=g.user;link.goal=g;link.rule=revision;link.day=row;em.persist(link);}
        }
    }
    List<GoalRow> relevant(GoalService goals,Kind kind,Long id) {
        LocalDate today=goals.now().toLocalDate();
        Set<Long> matched=new HashSet<>();goals.associations(new Source(kind,id,false)).forEach(a->matched.add(a.goalId()));
        return goals.rows().stream().filter(g->matched.contains(g.id)&&!g.archived&&g.closedAt==null
                &&!g.startsOn.isAfter(today)&&!g.endsOn.isBefore(today))
                .filter(g->kind!=Kind.EXPERIMENT || goals.definition(g).sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.EXPERIMENT&&s.id().equals(id))).toList();
    }
    GoalRuleRow revision(GoalRow goal) {
        return em.createQuery("select r from GoalRuleRow r where r.goal=:goal and r.revision=:revision",GoalRuleRow.class)
                .setParameter("goal",goal).setParameter("revision",goal.version).getSingleResult();
    }
    @Transactional public void invalidate(String prefix) {
        var goals=service.getObject();goals.lock();
        em.createQuery("update GoalFactRow f set f.valid=false where f.user.id=:owner and f.factKey like :prefix")
                .setParameter("owner",goals.owner()).setParameter("prefix",prefix+"%").executeUpdate();
    }
    @Transactional
    public void record(String type,String key,Kind kind,Long id,Map<String,Object> details) {
        record(type,key,kind,id,details,null);
    }
    @Transactional
    public void record(String type,String key,Kind kind,Long id,Map<String,Object> details,ZonedDateTime occurredAt) {
        GoalService goals=service.getObject();goals.lock();em.flush();
        var relevant=relevant(goals,kind,id).stream().filter(g -> occurredAt==null ||
                (!occurredAt.withZoneSameInstant(ZoneId.of("Asia/Taipei")).toLocalDate().isBefore(g.startsOn)
                && !occurredAt.withZoneSameInstant(ZoneId.of("Asia/Taipei")).toLocalDate().isAfter(g.endsOn))).toList();if(relevant.isEmpty())return;
        var old=em.createQuery("select f from GoalFactRow f where f.user.id=:owner and f.factKey=:key",GoalFactRow.class)
                .setParameter("owner",goals.owner()).setParameter("key",key).getResultList();
        var fact=old.isEmpty()?new GoalFactRow():old.getFirst();
        Map<String,Object> attributed=new LinkedHashMap<>(details);attributed.put("sourceKind",kind.name());
        if(fact.id==null){fact.user=relevant.getFirst().user;fact.type=type;fact.factKey=key;fact.sourceId=id;
            fact.recordedAt=goals.now();fact.occurredAt=occurredAt==null?fact.recordedAt:occurredAt;fact.details=goals.json(attributed);em.persist(fact);}
        for(var g:relevant){
            if(em.createQuery("select count(l) from GoalFactLinkRow l where l.goal=:goal and l.fact=:fact",Long.class).setParameter("goal",g).setParameter("fact",fact).getSingleResult()>0)continue;
            var link=new GoalFactLinkRow();link.user=g.user;link.goal=g;link.rule=revision(g);link.fact=fact;link.details=goals.json(attributed);em.persist(link);
        }
    }

    @Transactional
    public void organized(String operationId,Map<Long,Integer> clearedByThread,Long cardId) {
        GoalService goals=service.getObject();goals.lock();em.flush();
        Map<Long,GoalRow> matched=new LinkedHashMap<>();Map<Long,Map<Long,Integer>> attributed=new HashMap<>();
        for(var entry:clearedByThread.entrySet())for(var g:relevant(goals,Kind.THREAD,entry.getKey())) {
            matched.put(g.id,g);attributed.computeIfAbsent(g.id,key->new LinkedHashMap<>()).put(entry.getKey(),entry.getValue());
        }
        if(matched.isEmpty())return;
        String key="organize:"+operationId;
        if(em.createQuery("select count(f) from GoalFactRow f where f.user.id=:owner and f.factKey=:key",Long.class).setParameter("owner",goals.owner()).setParameter("key",key).getSingleResult()>0)return;
        var fact=new GoalFactRow();fact.user=matched.values().iterator().next().user;fact.type="EXPLORATION_ORGANIZED";fact.factKey=key;
        fact.sourceId=clearedByThread.keySet().iterator().next();fact.occurredAt=goals.now();fact.recordedAt=fact.occurredAt;
        fact.details=goals.json(Map.of("operationId",operationId.toString(),"clearedByThread",clearedByThread,"cardId",cardId==null?0L:cardId));em.persist(fact);
        for(var g:matched.values()){var link=new GoalFactLinkRow();link.user=g.user;link.goal=g;link.rule=revision(g);link.fact=fact;
            link.details=goals.json(Map.of("clearedByThread",attributed.get(g.id),"clearedCount",attributed.get(g.id).values().stream().mapToInt(Integer::intValue).sum(),"cardId",cardId==null?0L:cardId));em.persist(link);}
    }
}
