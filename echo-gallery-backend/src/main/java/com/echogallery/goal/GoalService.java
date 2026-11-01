package com.echogallery.goal;

import static com.echogallery.goal.GoalData.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.card.*;
import com.echogallery.user.*;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class GoalService {
    private final EntityManager em;
    private final UserRepository users;
    private final CardRepository cards;
    private final CardService cardService;
    private final ObjectMapper mapper;
    private final Clock clock;
    private static final Set<String> METRICS = java.util.stream.IntStream.rangeClosed(1,20)
            .mapToObj(i -> "M%02d".formatted(i)).collect(Collectors.toSet());

    Long owner() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) fail(HttpStatus.UNAUTHORIZED,"尚未登入");
        return id;
    }
    User lock() { return users.findByIdForUpdate(owner()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"使用者不存在")); }
    ZonedDateTime now() { return ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of("Asia/Taipei")); }
    String json(Object value) { return mapper.writeValueAsString(value); }
    Definition definition(GoalRow row) { return mapper.readValue(row.definition,Definition.class); }
    Status status(GoalRow row) {
        Status value = Status.valueOf(row.status);
        if(value==Status.ACTIVE&&row.startsOn.isAfter(now().toLocalDate()))return Status.PLANNED;
        return value == Status.PLANNED && !row.startsOn.isAfter(now().toLocalDate()) ? Status.ACTIVE : value;
    }
    GoalRow owned(Long id) {
        GoalRow row = em.find(GoalRow.class,id);
        if (row == null || !row.user.getId().equals(owner())) fail(HttpStatus.NOT_FOUND,"找不到目標");
        return row;
    }
    List<GoalRow> rows() {
        return em.createQuery("select g from GoalRow g where g.user.id=:owner order by g.id desc",GoalRow.class)
                .setParameter("owner",owner()).getResultList();
    }
    public PageResponse<View> list(String keyword, String state, boolean archived, int page, int size) {
        return list(keyword,state,archived,page,size,null,null,null,"created");
    }
    public PageResponse<View> list(String keyword, String state, boolean archived, int page, int size,
            List<Status> states, ArchiveScope scope, Boolean pinned, String sort) {
        if(!Set.of("created","pinOrder").contains(sort))fail(HttpStatus.BAD_REQUEST,"排序方式不正確");
        int boundedSize = Math.max(1,Math.min(100,size)), boundedPage = Math.max(0,page);
        String where=" where g.user_id=:owner";
        Map<String,Object> params=new HashMap<>();params.put("owner",owner());
        if(scope!=ArchiveScope.ALL){where+=" and g.archived=:archived";params.put("archived",scope==null?archived:scope==ArchiveScope.ARCHIVED);}
        if(pinned!=null){where+=" and cast(g.definition as jsonb)->>'pinned'=:pinned";params.put("pinned",pinned.toString());}
        List<String> selectedStates=new ArrayList<>();
        if(states!=null)selectedStates=states.stream().filter(Objects::nonNull).map(Enum::name).distinct().toList();
        else if(state!=null&&!state.isBlank()) {
            try{selectedStates=List.of(Status.valueOf(state).name());}catch(IllegalArgumentException ex){fail(HttpStatus.BAD_REQUEST,"目標狀態不正確");}
        }
        if(!selectedStates.isEmpty()) {
            where+=" and (case when g.status='PLANNED' and g.starts_on<=:today then 'ACTIVE' when g.status='ACTIVE' and g.starts_on>:today then 'PLANNED' else g.status end) in (:states)";
            params.put("today",now().toLocalDate());params.put("states",selectedStates);
        }
        if(keyword!=null&&!keyword.isBlank()) {
            where+=" and lower(cast(g.definition as jsonb)->>'name') like :keyword escape '!'";
            params.put("keyword","%"+keyword.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        String order="pinOrder".equals(sort)?"cast(cast(g.definition as jsonb)->>'pinOrder' as integer),g.id":"g.id desc";
        var query=em.createNativeQuery("select g.* from goals g"+where+" order by "+order,GoalRow.class);
        var countQuery=em.createNativeQuery("select count(*) from goals g"+where);params.forEach((key,value)->{query.setParameter(key,value);countQuery.setParameter(key,value);});
        long total=((Number)countQuery.getSingleResult()).longValue();
        @SuppressWarnings("unchecked") List<GoalRow> selected=query.setFirstResult((int)Math.min((long)boundedPage*boundedSize,Integer.MAX_VALUE)).setMaxResults(boundedSize).getResultList();
        var counts = counts(selected);
        return new PageResponse<>(selected.stream().map(g -> view(g,counts.getOrDefault(g.id,0L))).toList(),boundedPage,boundedSize,
                total,(int)Math.min(Integer.MAX_VALUE,(total+boundedSize-1)/boundedSize));
    }
    public View detail(Long id) { var g=owned(id); return view(g,counts(List.of(g)).getOrDefault(g.id,0L)); }
    public List<View> overview() {
        @SuppressWarnings("unchecked") List<GoalRow> list=em.createNativeQuery("select g.* from goals g where g.user_id=:owner and not g.archived and cast(g.definition as jsonb)->>'pinned'='true' order by cast(cast(g.definition as jsonb)->>'pinOrder' as integer),g.id limit 3",GoalRow.class).setParameter("owner",owner()).getResultList();
        var counts=counts(list); return list.stream().map(g -> view(g,counts.getOrDefault(g.id,0L))).toList();
    }
    View view(GoalRow g,long count) { return new View(g.id,definition(g),status(g),g.version,count,g.trackingStartedAt,g.closedAt); }

    @Transactional
    public View save(Long id, Save request) {
        User user=lock(); Definition d=request.definition(); validate(d);
        GoalRow g;
        if (id==null) {
            var existing=em.createQuery("select g from GoalRow g where g.user.id=:owner and g.requestKey=:key",GoalRow.class)
                    .setParameter("owner",owner()).setParameter("key",request.requestId().toString()).getResultList();
            if (!existing.isEmpty()) {
                if (!existing.getFirst().definition.equals(json(d))) fail(HttpStatus.CONFLICT,"同一請求不可用於不同目標內容");
                return detail(existing.getFirst().id);
            }
            if (request.version()!=0) fail(HttpStatus.CONFLICT,"建立目標版本不正確");
            g=new GoalRow(); g.user=user;g.requestKey=request.requestId().toString();g.trackingStartedAt=now();
        } else {
            g=owned(id);
            if (g.version!=request.version()) fail(HttpStatus.CONFLICT,"目標已有更新，請保留輸入並重新載入");
            if (g.closedAt!=null && (d.status()==Status.ACTIVE || d.status()==Status.PLANNED)
                    && (request.reason()==null || request.reason().isBlank())) fail(HttpStatus.BAD_REQUEST,"撤回完成或結束請填原因");
            if (g.definition.equals(json(d))) return detail(g.id);
        }
        boolean closing=d.status()==Status.COMPLETED || d.status()==Status.ENDED;
        g.closedAt=closing ? g.closedAt==null?now():g.closedAt : null;
        g.definition=json(d);g.startsOn=d.startsOn();g.endsOn=d.endsOn();g.status=d.status().name();g.archived=d.archived();g.version++;
        if(g.id==null)em.persist(g);
        var revision=new GoalRuleRow();revision.user=user;revision.goal=g;revision.revision=g.version;
        revision.definition=g.definition;revision.reason=request.reason();revision.effectiveAt=now();em.persist(revision);
        for(Source s:d.sources()) {
            var source=new GoalSourceRow();source.user=user;source.rule=revision;source.kind=s.kind().name();source.sourceId=s.id();source.excluded=s.excluded();
            switch(s.kind()) {
                case TAG -> source.tag=em.getReference(com.echogallery.tag.Tag.class,s.id());
                case EXPERIMENT -> source.experiment=em.getReference(com.echogallery.experiment.Experiment.class,s.id());
                case THREAD -> source.thread=em.getReference(com.echogallery.experiment.ExplorationThread.class,s.id());
                case WORK -> source.work=em.getReference(com.echogallery.work.CreativeWork.class,s.id());
                case TREASURE -> source.treasure=em.getReference(com.echogallery.treasure.Treasure.class,s.id());
                case CARD -> source.card=em.getReference(Card.class,s.id());
            }
            em.persist(source);
        }
        em.flush();return detail(g.id);
    }
    void validate(Definition d) {
        if(d.endsOn().isBefore(d.startsOn()))fail(HttpStatus.BAD_REQUEST,"結束日期不可早於開始日期");
        if(!METRICS.containsAll(d.metrics()))fail(HttpStatus.BAD_REQUEST,"指標代碼不正確");
        if(d.imageUrl()!=null&&!d.imageUrl().isBlank()) {
            try { var u=java.net.URI.create(d.imageUrl());if((!"http".equalsIgnoreCase(u.getScheme())&&!"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException(); }
            catch(IllegalArgumentException ex){fail(HttpStatus.BAD_REQUEST,"圖片須為有效 HTTP(S) 網址");}
        }
        validateSources(d.sources());
    }
    void validateSources(List<Source> sources) {
        if(sources.stream().map(s->s.kind()+":"+s.id()).distinct().count()!=sources.size())fail(HttpStatus.BAD_REQUEST,"來源不可重複或同時納入與排除");
        if(sources.stream().anyMatch(s->s.excluded()&&s.kind()!=Kind.CARD&&s.kind()!=Kind.THREAD))fail(HttpStatus.BAD_REQUEST,"個別排除目前支援卡片與探索線");
        for(Kind kind:Kind.values()) {
            var ids=sources.stream().filter(s->s.kind()==kind).map(Source::id).distinct().toList();if(ids.isEmpty())continue;
            String sql=kind==Kind.THREAD?"select count(*) from exploration_threads t join experiments e on e.id=t.experiment_id where t.id in (:ids) and e.user_id=:owner"
                    :"select count(*) from "+sourceTable(kind)+" where id in (:ids) and user_id=:owner";
            long found=((Number)em.createNativeQuery(sql).setParameter("ids",ids).setParameter("owner",owner()).getSingleResult()).longValue();
            if(found!=ids.size())fail(HttpStatus.BAD_REQUEST,"來源不存在或不可使用，請重新選擇");
        }
    }
    String sourceTable(Kind kind) {return switch(kind){case TAG->"tags";case EXPERIMENT->"experiments";case THREAD->"exploration_threads";case WORK->"creative_works";case TREASURE->"treasures";case CARD->"cards";};}
    public List<Option> options(Kind kind,String keyword,int page) {
        return options(kind,keyword,page,null);
    }
    public List<Option> options(Kind kind,String keyword,int page,Long experimentId) {
        if(experimentId!=null){if(kind!=Kind.THREAD)fail(HttpStatus.BAD_REQUEST,"只有探索線可限定實驗");validateSources(List.of(new Source(Kind.EXPERIMENT,experimentId,false)));}
        String from=kind==Kind.THREAD?"exploration_threads s join experiments e on e.id=s.experiment_id":"%s s".formatted(sourceTable(kind));
        String name=kind==Kind.THREAD||kind==Kind.TAG?"s.name":"s.title";
        String user=kind==Kind.THREAD?"e.user_id":"s.user_id";
        String metadata=kind==Kind.THREAD?",e.id,e.title":",cast(null as bigint),cast(null as text)";
        String search=kind==Kind.THREAD?"(lower(s.name) like :keyword escape '!' or lower(e.title) like :keyword escape '!')":"lower("+name+") like :keyword escape '!'";
        var query=em.createNativeQuery("select s.id,"+name+metadata+" from "+from+" where "+user+"=:owner and "+search+(experimentId==null?"":" and e.id=:experiment")+" order by s.id desc")
                .setParameter("owner",owner()).setParameter("keyword","%"+(keyword==null?"":keyword.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_"))+"%");
        if(experimentId!=null)query.setParameter("experiment",experimentId);
        @SuppressWarnings("unchecked") List<Object[]> values=query.setFirstResult((int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,page)*20)).setMaxResults(20).getResultList();
        return values.stream().map(v->new Option(kind,((Number)v[0]).longValue(),(String)v[1],v[2]==null?null:((Number)v[2]).longValue(),(String)v[3])).toList();
    }
    public Map<String,Object> preview(Definition d) {validate(d);return Map.of("cardCount",countDefinition(d),"sources",d.sources());}
    public Map<String,Object> preview(TrackingPreview request) {
        validateSources(request.sources());
        var d=new Definition("預覽",now().toLocalDate(),now().toLocalDate(),"","","",Status.ACTIVE,false,false,0,7,false,request.allTags(),request.sources(),List.of());
        return Map.of("cardCount",countDefinition(d),"sources",d.sources());
    }
    @Transactional public List<View> pinOrder(Long id,PinOrder request) {
        lock();var moving=owned(id);var adjacent=owned(request.adjacentId());
        if(moving.version!=request.version()||adjacent.version!=request.adjacentVersion())fail(HttpStatus.CONFLICT,"固定順序已有更新，請重新讀取");
        var ordered=new ArrayList<>(rows().stream().filter(g->!g.archived&&definition(g).pinned())
                .sorted(Comparator.comparingInt((GoalRow g)->definition(g).pinOrder()).thenComparing(g->g.id)).toList());
        int index=ordered.indexOf(moving),target=index+(request.direction()==Direction.UP?-1:1);
        if(index<0||target<0||target>=ordered.size()||!ordered.get(target).id.equals(adjacent.id))fail(HttpStatus.CONFLICT,"相鄰目標已變更，請重新讀取");
        Collections.swap(ordered,index,target);
        for(int position=0;position<ordered.size();position++) {
            var row=ordered.get(position);var d=definition(row);if(d.pinOrder()==position)continue;
            var next=new Definition(d.name(),d.startsOn(),d.endsOn(),d.expectation(),d.motivation(),d.imageUrl(),d.status(),d.archived(),d.pinned(),position,d.reviewDays(),d.remindersEnabled(),d.allTags(),d.sources(),d.metrics());
            save(row.id,new Save(next,row.version,"調整總覽排序",UUID.randomUUID()));
        }
        return overview();
    }
    public List<Map<String,Object>> sources(Long id) {
        var d=definition(owned(id));if(d.sources().isEmpty())return List.of();
        Map<String,Object> params=new HashMap<>();List<String> parts=new ArrayList<>();int index=0;
        for(var source:d.sources()) {
            String p="source"+index++;params.put(p,source.id());String title=source.kind()==Kind.THREAD||source.kind()==Kind.TAG?"s.name":"s.title";
            String from=source.kind()==Kind.THREAD?"exploration_threads s join experiments e on e.id=s.experiment_id":sourceTable(source.kind())+" s";
            String ownerColumn=source.kind()==Kind.THREAD?"e.user_id":"s.user_id";
            parts.add("select '"+source.kind().name()+"' as kind,s.id,"+title+" as title,"+(source.kind()==Kind.THREAD?"s.experiment_id,e.title":"cast(null as bigint),cast(null as text)")+" from "+from+" where s.id=:"+p+" and "+ownerColumn+"=:owner");
        }
        var q=em.createNativeQuery(String.join(" union all ",parts)).setParameter("owner",owner());params.forEach(q::setParameter);
        @SuppressWarnings("unchecked") List<Object[]> values=q.getResultList();Map<String,Object[]> found=new HashMap<>();values.forEach(v->found.put(v[0]+":"+v[1],v));
        return d.sources().stream().map(s->{var value=found.get(s.kind()+":"+s.id());Map<String,Object> result=new LinkedHashMap<>();result.put("kind",s.kind());result.put("id",s.id());result.put("title",value==null?"來源已刪除":value[2]);result.put("available",value!=null);result.put("excluded",s.excluded());
            result.put("parentId",value==null?null:value[3]);result.put("parentTitle",value==null?null:value[4]);
            result.put("path",value==null?"":switch(s.kind()){case TAG->"";case CARD->"/card/"+s.id();case EXPERIMENT->"/experiments/"+s.id();case THREAD->"/experiments/"+value[3]+"?thread="+s.id();case WORK->"/works/"+s.id();case TREASURE->"/treasures/"+s.id();});return result;}).toList();
    }
    public List<Map<String,Object>> revisions(Long id) {
        owned(id);return em.createQuery("select r from GoalRuleRow r where r.goal.id=:goal and r.user.id=:owner order by r.revision desc",GoalRuleRow.class)
                .setParameter("goal",id).setParameter("owner",owner()).setMaxResults(100).getResultStream()
                .map(r -> {Map<String,Object> m=new LinkedHashMap<>();m.put("revision",r.revision);m.put("definition",mapper.readValue(r.definition,Definition.class));m.put("reason",r.reason);m.put("effectiveAt",r.effectiveAt);return m;}).toList();
    }
    @Transactional public void delete(Long id,long version) {
        lock();var g=owned(id);if(g.version!=version)fail(HttpStatus.CONFLICT,"目標已有更新，請重新載入");
        for(String entity:List.of("GoalCardDayRow","GoalFactLinkRow"))em.createQuery("delete from "+entity+" l where l.goal.id=:id").setParameter("id",id).executeUpdate();
        em.createQuery("delete from GoalSourceRow s where s.rule.goal.id=:id").setParameter("id",id).executeUpdate();
        em.createQuery("delete from GoalRuleRow r where r.goal.id=:id").setParameter("id",id).executeUpdate();
        em.remove(g);em.flush();
        em.createQuery("delete from GoalFactRow f where f.user.id=:owner and not exists(select l.id from GoalFactLinkRow l where l.fact=f)").setParameter("owner",owner()).executeUpdate();
        em.createQuery("delete from CardContentDayRow d where d.user.id=:owner and not exists(select l.id from GoalCardDayRow l where l.day=d)").setParameter("owner",owner()).executeUpdate();
    }

    // 所有來源規則都留在資料庫篩選，不將整個卡片池載入前端或 Java 記憶體。
    String predicate(Definition d,String prefix,Map<String,Object> params) {
        List<String> includes=new ArrayList<>(),excludes=new ArrayList<>(),tagRules=new ArrayList<>();int i=0;
        List<Long> excludedThreads=d.sources().stream().filter(s->s.excluded()&&s.kind()==Kind.THREAD).map(Source::id).toList();
        String threadFilter=excludedThreads.isEmpty()?"":" and tc.thread_id not in ("+excludedThreads.stream().map(String::valueOf).collect(Collectors.joining(","))+")";
        for(Source s:d.sources()) {
            if(s.excluded()&&s.kind()!=Kind.CARD)continue;
            String p=prefix+(i++);params.put(p,s.id());String condition=switch(s.kind()) {
                case CARD->"c.id=:"+p;
                case TAG->"exists(select 1 from card_tags ct where ct.card_id=c.id and ct.tag_id=:"+p+")";
                case THREAD->"exists(select 1 from exploration_thread_cards tc where tc.card_id=c.id and tc.thread_id=:"+p+")";
                case EXPERIMENT->"exists(select 1 from exploration_thread_cards tc join exploration_threads t on t.id=tc.thread_id where tc.card_id=c.id and t.experiment_id=:"+p+threadFilter+")";
                case WORK->"exists(select 1 from creative_work_sources ws where ws.card_id=c.id and ws.work_id=:"+p+")";
                case TREASURE->"c.id=:"+p+" and false";
            };
            if(s.excluded()) {if(s.kind()==Kind.CARD)excludes.add(condition);}
            else if(s.kind()==Kind.TAG)tagRules.add(condition);else includes.add(condition);
        }
        if(!tagRules.isEmpty())includes.add("("+String.join(d.allTags()?" and ":" or ",tagRules)+")");
        return "("+(includes.isEmpty()?"false":String.join(" or ",includes))+")"+(excludes.isEmpty()?"":" and not ("+String.join(" or ",excludes)+")");
    }
    long countDefinition(Definition d) {
        var params=new LinkedHashMap<String,Object>();String where=predicate(d,"p",params);
        var q=em.createNativeQuery("select count(*) from cards c where c.user_id=:owner and "+where).setParameter("owner",owner());params.forEach(q::setParameter);
        return ((Number)q.getSingleResult()).longValue();
    }
    Map<Long,Long> counts(List<GoalRow> goals) {
        Map<Long,Long> result=new HashMap<>();if(goals.isEmpty())return result;
        Map<String,Object> params=new HashMap<>();List<String> selects=new ArrayList<>();
        for(var g:goals)selects.add("select "+g.id+" as goal_id,count(*) as total from cards c where c.user_id=:owner and "+predicate(definition(g),"g"+g.id+"p",params));
        var q=em.createNativeQuery(String.join(" union all ",selects)).setParameter("owner",owner());params.forEach(q::setParameter);
        @SuppressWarnings("unchecked") List<Object[]> values=q.getResultList();for(Object[] v:values)result.put(((Number)v[0]).longValue(),((Number)v[1]).longValue());return result;
    }
    public Preference preference() {
        var rows=em.createQuery("select p from GoalPreferenceRow p where p.user.id=:owner",GoalPreferenceRow.class).setParameter("owner",owner()).getResultList();
        if(rows.isEmpty())return new Preference(true,List.of(),true,false,0L);
        var p=rows.getFirst();var value=mapper.readValue(p.selection,Preference.class);
        return new Preference(value.allActive(),value.goalIds(),value.includePaused(),value.includeArchived(),p.version);
    }
    @Transactional public Preference preference(Preference request) {
        User user=lock();for(Long id:request.goalIds())owned(id);
        var rows=em.createQuery("select p from GoalPreferenceRow p where p.user.id=:owner",GoalPreferenceRow.class).setParameter("owner",owner()).getResultList();
        var p=rows.isEmpty()?new GoalPreferenceRow():rows.getFirst();
        if(p.version!=request.version())fail(HttpStatus.CONFLICT,"另一個裝置已修改偏好，請重新載入或確認覆蓋");
        p.user=user;p.selection=json(request);p.version++;if(p.id==null)em.persist(p);return preference();
    }
    record Cursor(String fingerprint,long position,long bucket) {}
    public Map<String,Object> board(Board request) {
        var selection=request.selection();Set<Long> ids=new HashSet<>(selection.goalIds());
        var selected=rows().stream().filter(g->!g.archived&&(selection.allActive()?status(g)==Status.ACTIVE:ids.contains(g.id))).sorted(Comparator.comparing(g->g.id)).toList();
        if(!selection.allActive())for(Long id:ids)owned(id);
        String range=selected.stream().map(g->{var d=definition(g);return g.id+"|"+d.allTags()+"|"+d.sources().stream()
                .sorted(Comparator.comparing((Source s)->s.kind().name()).thenComparing(Source::id).thenComparing(Source::excluded))
                .map(s->s.kind()+":"+s.id()+":"+s.excluded()).collect(Collectors.joining(","));}).collect(Collectors.joining(";"));
        String fingerprint=owner()+":"+request.seed()+":"+digest(range)+":"+selection.includePaused()+":"+selection.includeArchived();
        long position=0,bucket=0;
        if(request.cursor()!=null&&!request.cursor().isBlank()) {
            try{var cursor=mapper.readValue(new String(Base64.getUrlDecoder().decode(request.cursor()),java.nio.charset.StandardCharsets.UTF_8),Cursor.class);
                if(!fingerprint.equals(cursor.fingerprint())||cursor.position()<0||cursor.bucket()<0)throw new IllegalArgumentException();position=cursor.position();bucket=cursor.bucket();}
            catch(Exception ex){fail(HttpStatus.CONFLICT,"目標範圍已變更，請開始新一輪");}
        }
        if(selected.isEmpty())return Map.of("content",List.of(),"total",0,"hasMore",false,"fingerprint",fingerprint,"nextCursor","",
            "emptyReason",selection.allActive()?"NO_ACTIVE_GOALS":ids.isEmpty()?"NO_SELECTION":"NO_AVAILABLE_GOALS","selectedGoalCount",0);
        Map<String,Object> params=new HashMap<>();List<String> parts=new ArrayList<>();
        for(var g:selected)parts.add("select c.id,"+g.id+" as goal_id from cards c where c.user_id=:owner and "+(selection.includeArchived()?"true":"not c.is_archived")
                +" and "+(selection.includePaused()?"true":"c.interval_days is not null")+" and "+predicate(definition(g),"g"+g.id+"p",params));
        // 先將重複候選歸入唯一目標，再按組內順序輪替；頁間不重新抽亂數。
        String base="with candidates as ("+String.join(" union all ",parts)+"), chosen as (select id,goal_id,row_number() over(partition by id order by md5(cast(goal_id as text)||:seed),goal_id) as choice from candidates), buckets as (select id,goal_id as bucket from chosen where choice=1), ranked as (select id,bucket,row_number() over(partition by bucket order by md5(cast(id as text)||:seed),id) as position from buckets) ";
        boolean lookup=request.pageIds()!=null&&!request.pageIds().isEmpty();
        var q=em.createNativeQuery(base+"select id,position,bucket from ranked where "+(lookup?"id in (:pageIds)":"position>:position or (position=:position and bucket>:bucket)")+" order by position,bucket limit 6").setParameter("owner",owner()).setParameter("seed",request.seed().toString());
        if(lookup)q.setParameter("pageIds",request.pageIds());else q.setParameter("position",position).setParameter("bucket",bucket);
        params.forEach(q::setParameter);@SuppressWarnings("unchecked") List<Object[]> numbers=q.getResultList();
        var countQuery=em.createNativeQuery("select count(distinct id) from ("+String.join(" union all ",parts)+") pool").setParameter("owner",owner());params.forEach(countQuery::setParameter);
        long total=((Number)countQuery.getSingleResult()).longValue();boolean more=numbers.size()>5;
        var cardIds=numbers.stream().limit(5).map(row->((Number)row[0]).longValue()).toList();
        Map<Long,Card> found=cards.findAllById(cardIds).stream().collect(Collectors.toMap(Card::getId,c->c));
        var content=cardIds.stream().filter(found::containsKey).map(id->cardService.convertToSummaryResponse(found.get(id))).toList();
        Object[] last=numbers.isEmpty()?null:numbers.get(Math.min(4,numbers.size()-1));
        String next=Base64.getUrlEncoder().withoutPadding().encodeToString(json(new Cursor(fingerprint,last==null?position:((Number)last[1]).longValue(),last==null?bucket:((Number)last[2]).longValue())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean hasSources=selected.stream().anyMatch(g->definition(g).sources().stream().anyMatch(s->!s.excluded()&&s.kind()!=Kind.TREASURE));
        return Map.of("content",content,"total",total,"hasMore",more,"fingerprint",fingerprint,"nextCursor",next,
            "emptyReason",total>0?"":hasSources?"NO_MATCHING_CARDS":"NO_CARD_SOURCES","selectedGoalCount",selected.size());
    }
    public List<Association> associations(Source source) {
        return associationResults(List.of(source)).getOrDefault(source.kind()+":"+source.id(),List.of());
    }
    public Map<String,Long> associations(Associations request) {
        Map<String,Long> result=new LinkedHashMap<>();associationResults(request.objects()).forEach((key,value)->result.put(key,(long)value.size()));return result;
    }
    Map<String,List<Association>> associationResults(List<Source> objects) {
        Map<String,List<Association>> result=new LinkedHashMap<>();objects.forEach(s->result.put(s.kind()+":"+s.id(),new ArrayList<>()));
        var goals=rows().stream().filter(g->!g.archived).toList();
        if(goals.isEmpty()||objects.isEmpty())return result;
        Map<Long,GoalRow> byId=goals.stream().collect(Collectors.toMap(g->g.id,g->g));
        Map<Long,Definition> definitions=goals.stream().collect(Collectors.toMap(g->g.id,this::definition));
        var requestedCards=objects.stream().filter(s->s.kind()==Kind.CARD).map(Source::id).distinct().toList();
        if(!requestedCards.isEmpty()&&!goals.isEmpty()) {
            Map<String,Object> params=new HashMap<>();List<String> parts=new ArrayList<>();
            for(var g:goals)parts.add("select c.id,"+g.id+" as goal_id from cards c where c.user_id=:owner and c.id in (:cards) and "+predicate(definition(g),"g"+g.id+"p",params));
            var q=em.createNativeQuery("select distinct id,goal_id from ("+String.join(" union all ",parts)+") matches").setParameter("owner",owner()).setParameter("cards",requestedCards);params.forEach(q::setParameter);
            @SuppressWarnings("unchecked") List<Object[]> values=q.getResultList();for(var v:values){var g=byId.get(((Number)v[1]).longValue());result.get("CARD:"+v[0]).add(new Association(g.id,definitions.get(g.id).name(),status(g),List.of("符合目標的標籤、探索材料或作品來源規則")));}
        }
        Set<Long> threadIds=new HashSet<>(),workIds=new HashSet<>();
        definitions.values().forEach(d->d.sources().forEach(s->{if(s.kind()==Kind.THREAD)threadIds.add(s.id());if(s.kind()==Kind.WORK)workIds.add(s.id());}));
        objects.stream().filter(s->s.kind()==Kind.THREAD).forEach(s->threadIds.add(s.id()));
        Map<Long,Long> threadExperiments=new HashMap<>(),workTreasures=new HashMap<>();
        if(!threadIds.isEmpty()) {
            @SuppressWarnings("unchecked") List<Object[]> values=em.createNativeQuery("select t.id,t.experiment_id from exploration_threads t join experiments e on e.id=t.experiment_id where e.user_id=:owner and t.id in (:ids)").setParameter("owner",owner()).setParameter("ids",threadIds).getResultList();
            values.forEach(v->threadExperiments.put(((Number)v[0]).longValue(),((Number)v[1]).longValue()));
        }
        if(!workIds.isEmpty()) {
            @SuppressWarnings("unchecked") List<Object[]> values=em.createNativeQuery("select id,treasure_id from creative_works where user_id=:owner and id in (:ids) and treasure_id is not null").setParameter("owner",owner()).setParameter("ids",workIds).getResultList();
            values.forEach(v->workTreasures.put(((Number)v[0]).longValue(),((Number)v[1]).longValue()));
        }
        for(Kind kind:Kind.values()) {
            if(kind==Kind.CARD)continue;var requested=objects.stream().filter(s->s.kind()==kind).map(Source::id).distinct().toList();if(requested.isEmpty())continue;
            Set<Long> valid;
            if(kind==Kind.THREAD)valid=threadExperiments.keySet();
            else {@SuppressWarnings("unchecked") List<Number> ids=em.createNativeQuery("select id from "+sourceTable(kind)+" where user_id=:owner and id in (:ids)").setParameter("owner",owner()).setParameter("ids",requested).getResultList();valid=ids.stream().map(Number::longValue).collect(Collectors.toSet());}
            for(Long id:requested) {
                if(!valid.contains(id))continue;
                for(var g:goals) {
                    var d=definitions.get(g.id);List<String> reasons=new ArrayList<>();
                    if(d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==kind&&s.id().equals(id)))reasons.add("直接追蹤此來源");
                    if(kind==Kind.THREAD&&d.sources().stream().noneMatch(s->s.excluded()&&s.kind()==Kind.THREAD&&s.id().equals(id))
                            &&d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.EXPERIMENT&&s.id().equals(threadExperiments.get(id))))reasons.add("追蹤所屬實驗");
                    if(kind==Kind.EXPERIMENT&&d.sources().stream().noneMatch(s->!s.excluded()&&s.kind()==Kind.EXPERIMENT&&id.equals(s.id()))&&d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.THREAD&&id.equals(threadExperiments.get(s.id()))))reasons.add("僅追蹤部分探索線");
                    if(kind==Kind.TREASURE&&d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.WORK&&id.equals(workTreasures.get(s.id()))))reasons.add("關聯已追蹤作品的展品");
                    if(!reasons.isEmpty())result.get(kind+":"+id).add(new Association(g.id,d.name(),status(g),reasons));
                }
            }
        }
        return result;
    }
    @Transactional public void clearOwner(Long owner) {
        if(users.findByIdForUpdate(owner).isEmpty())return;
        for(String entity:List.of("GoalCardDayRow","GoalFactLinkRow","GoalSourceRow","GoalRuleRow","GoalPreferenceRow","GoalFactRow","CardContentDayRow","GoalRow"))
            em.createQuery("delete from "+entity+" r where r.user.id=:owner").setParameter("owner",owner).executeUpdate();
        em.flush();
        em.clear();
    }
    static void fail(HttpStatus status,String message){throw new ResponseStatusException(status,message);}
    private static String digest(String value) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException("無法建立範圍識別",ex);}
    }
}
