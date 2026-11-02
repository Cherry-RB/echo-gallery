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
    public List<Metric> metrics(Long id) {
        var goal=owned(id);var definition=definition(goal);return definition.metrics().stream().map(key->metric(goal,key)).toList();
    }
    Metric metric(GoalRow goal,String key) {
        return metric(goal,key,goal.startsOn,goal.endsOn);
    }
    Metric metric(GoalRow goal,String key,LocalDate starts,LocalDate ends) {
        var definition=definition(goal);Long value=null;String mode="PERIOD",coverage="RECORDED";
        if("M01".equals(key)){value=countDefinition(definition);mode="CURRENT";coverage="CURRENT";}
        else if(Set.of("M03","M04").contains(key)) {
            var links=dayLinks(goal).stream().filter(l->!l.day.modifiedOn.isBefore(starts)&&!l.day.modifiedOn.isAfter(ends)).toList();value="M03".equals(key)?links.stream().map(l->l.day.cardId).distinct().count():links.stream().map(l->l.day.modifiedOn).distinct().count();
        } else {
            var types=metricTypes(key);if(types!=null)value=factLinks(goal).stream().filter(l->{var date=l.fact.occurredAt.withZoneSameInstant(ZoneId.of("Asia/Taipei")).toLocalDate();return !date.isBefore(starts)&&!date.isAfter(ends);}).filter(l->types.contains(l.fact.type)).map(l->factKey(l)).distinct().count();
            else coverage="NOT_AVAILABLE";
        }
        String unit=switch(key){case "M04" -> "天";case "M10","M11","M12","M17","M18" -> "次";case "M14" -> "件";case "M16" -> "件";default -> "張";};
        return new Metric(key,value,unit,mode,starts,ends,goal.trackingStartedAt,coverage,goal.version);
    }
    Set<String> metricTypes(String key) { return switch(key) {
        case "M02" -> Set.of("CARD_CREATED"); case "M05" -> Set.of("TODAY_REVIEWED"); case "M06" -> Set.of("CARD_REUSED");
        case "M10" -> Set.of("DISCOVERY_CREATED"); case "M11" -> Set.of("METHOD_CREATED"); case "M12" -> Set.of("EXPLORATION_ORGANIZED");
        case "M14" -> Set.of("WORK_STAGE_CHANGED"); case "M16" -> Set.of("TREASURE_ACQUIRED"); case "M17" -> Set.of("TRIAL_ATTEMPT"); case "M18" -> Set.of("TREASURE_YIELD"); default -> null;
    }; }
    List<GoalFactLinkRow> allFactLinks(GoalRow goal) { return em.createQuery("select l from GoalFactLinkRow l join fetch l.fact where l.goal=:goal and l.fact.valid=true and l.fact.occurredAt>=:from and l.fact.occurredAt<:to",GoalFactLinkRow.class)
            .setParameter("goal",goal).setParameter("from",goal.startsOn.atStartOfDay(ZoneId.of("Asia/Taipei"))).setParameter("to",goal.endsOn.plusDays(1).atStartOfDay(ZoneId.of("Asia/Taipei"))).getResultList(); }
    List<GoalFactLinkRow> factLinks(GoalRow goal) { return allFactLinks(goal).stream().filter(l->!override(goal,"FACT",l.id).excluded).toList(); }
    List<GoalCardDayRow> allDayLinks(GoalRow goal) { return em.createQuery("select l from GoalCardDayRow l join fetch l.day where l.goal=:goal and l.day.modifiedOn between :from and :to",GoalCardDayRow.class)
            .setParameter("goal",goal).setParameter("from",goal.startsOn).setParameter("to",goal.endsOn).getResultList(); }
    List<GoalCardDayRow> dayLinks(GoalRow goal) { return allDayLinks(goal).stream().filter(l->!override(goal,"DAY",l.id).excluded).toList(); }
    GoalEvidenceOverrideRow override(GoalRow goal,String type,Long link) { return em.createQuery("select o from GoalEvidenceOverrideRow o where o.goal=:goal and o.linkType=:type and o.linkId=:link",GoalEvidenceOverrideRow.class)
            .setParameter("goal",goal).setParameter("type",type).setParameter("link",link).getResultList().stream().findFirst().orElseGet(()->{var o=new GoalEvidenceOverrideRow();o.excluded=false;o.version=0;return o;}); }
    String factKey(GoalFactLinkRow link) { return link.fact.factKey; }
    public EvidencePage evidence(Long id,String metric,int page,int size) {
        var goal=owned(id);int boundedPage=Math.max(0,page),boundedSize=Math.max(1,Math.min(100,size));var entries=new ArrayList<Evidence>();
        var types=metric==null||metric.isBlank()?null:metricTypes(metric);
        if(types==null&&metric!=null&&!Set.of("M03","M04").contains(metric)) fail(HttpStatus.BAD_REQUEST,"此指標尚無可追查活動");
        if(metric==null||metric.isBlank()||!Set.of("M03","M04").contains(metric)) for(var link:allFactLinks(goal)) if(types==null||types.contains(link.fact.type)) entries.add(evidence(goal,link));
        if(metric==null||metric.isBlank()||Set.of("M03","M04").contains(metric)) for(var link:allDayLinks(goal)) entries.add(evidence(goal,link));
        entries.sort(Comparator.comparing(Evidence::occurredAt).reversed());long total=entries.size();int from=Math.min(entries.size(),boundedPage*boundedSize),to=Math.min(entries.size(),from+boundedSize);
        return new EvidencePage(entries.subList(from,to),total,boundedPage,boundedSize,(int)((total+boundedSize-1)/boundedSize));
    }
    Evidence evidence(GoalRow goal,GoalFactLinkRow link) {var o=override(goal,"FACT",link.id);return new Evidence("fact-"+link.id,link.fact.type,link.fact.occurredAt,link.fact.recordedAt,null,link.fact.sourceId,o.excluded,o.version,o.reason);}
    Evidence evidence(GoalRow goal,GoalCardDayRow link) {var o=override(goal,"DAY",link.id);var at=link.day.modifiedOn.atStartOfDay(ZoneId.of("Asia/Taipei"));return new Evidence("day-"+link.id,"CONTENT_UPDATED",at,at,"CARD",link.day.cardId,o.excluded,o.version,o.reason);}
    @Transactional public Evidence evidence(Long id,String linkId,EvidenceChange change) {
        lock();var goal=owned(id);var parts=linkId.split("-",2);if(parts.length!=2||!Set.of("fact","day").contains(parts[0]))fail(HttpStatus.BAD_REQUEST,"證據識別不正確");Long row;try{row=Long.valueOf(parts[1]);}catch(NumberFormatException ex){fail(HttpStatus.BAD_REQUEST,"證據識別不正確");return null;}
        String type="fact".equals(parts[0])?"FACT":"DAY";boolean exists="FACT".equals(type)?em.createQuery("select count(l) from GoalFactLinkRow l where l.goal=:goal and l.id=:id",Long.class).setParameter("goal",goal).setParameter("id",row).getSingleResult()>0:em.createQuery("select count(l) from GoalCardDayRow l where l.goal=:goal and l.id=:id",Long.class).setParameter("goal",goal).setParameter("id",row).getSingleResult()>0;if(!exists)fail(HttpStatus.NOT_FOUND,"找不到證據");
        var o=override(goal,type,row);if(o.id==null){
            if(change.version()!=0)fail(HttpStatus.CONFLICT,"證據已有更新，請重新讀取");
            o.user=goal.user;o.goal=goal;o.linkType=type;o.linkId=row;o.excluded=change.excluded();o.reason=change.reason().trim();o.updatedAt=now();o.version=1;em.persist(o);
        } else {
            if(o.version!=change.version())fail(HttpStatus.CONFLICT,"證據已有更新，請重新讀取");
            o.excluded=change.excluded();o.reason=change.reason().trim();o.updatedAt=now();o.version++;
        }
        return "FACT".equals(type)?evidence(goal,em.find(GoalFactLinkRow.class,row)):evidence(goal,em.find(GoalCardDayRow.class,row));
    }
    public List<ProgressLog> logs(Long id) {
        owned(id);return em.createQuery("select l from GoalProgressLogRow l where l.goal.id=:goal and l.user.id=:owner order by l.occurredOn desc,l.id desc",GoalProgressLogRow.class)
                .setParameter("goal",id).setParameter("owner",owner()).getResultStream().map(this::log).toList();
    }
    ProgressLog log(GoalProgressLogRow row) { return new ProgressLog(row.id,LogKind.valueOf(row.kind),row.occurredOn,row.content,row.amount,row.unit,row.version,row.createdAt,row.updatedAt); }
    void validateLog(ProgressLogSave request) {
        boolean hasContent=request.content()!=null&&!request.content().isBlank(),hasAmount=request.amount()!=null;
        if(!hasContent&&!hasAmount)fail(HttpStatus.BAD_REQUEST,"請填寫補記內容或數量");
        if(request.kind()==LogKind.MEASUREMENT&&(!hasAmount||request.unit()==null||request.unit().isBlank()))fail(HttpStatus.BAD_REQUEST,"量化補記需要數量與單位");
    }
    @Transactional public ProgressLog createLog(Long id,ProgressLogSave request) {
        lock();var goal=owned(id);validateLog(request);if(request.version()!=0)fail(HttpStatus.CONFLICT,"補記已有更新，請重新讀取");
        var row=new GoalProgressLogRow();row.user=goal.user;row.goal=goal;row.kind=request.kind().name();row.occurredOn=request.occurredOn();row.content=blankToNull(request.content());row.amount=request.amount();row.unit=blankToNull(request.unit());row.version=1;row.createdAt=now();row.updatedAt=row.createdAt;em.persist(row);return log(row);
    }
    GoalProgressLogRow ownedLog(GoalRow goal,Long logId) {
        return em.createQuery("select l from GoalProgressLogRow l where l.goal=:goal and l.id=:id and l.user.id=:owner",GoalProgressLogRow.class)
                .setParameter("goal",goal).setParameter("id",logId).setParameter("owner",owner()).getResultStream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到補記"));
    }
    @Transactional public ProgressLog saveLog(Long id,Long logId,ProgressLogSave request) {
        lock();var goal=owned(id);validateLog(request);var row=ownedLog(goal,logId);if(row.version!=request.version())fail(HttpStatus.CONFLICT,"補記已有更新，請重新讀取");
        row.kind=request.kind().name();row.occurredOn=request.occurredOn();row.content=blankToNull(request.content());row.amount=request.amount();row.unit=blankToNull(request.unit());row.version++;row.updatedAt=now();return log(row);
    }
    @Transactional public void deleteLog(Long id,Long logId,long version) {
        lock();var row=ownedLog(owned(id),logId);if(row.version!=version)fail(HttpStatus.CONFLICT,"補記已有更新，請重新讀取");em.remove(row);
    }
    public List<Review> reviews(Long id) {
        owned(id);return em.createQuery("select r from GoalReviewRow r where r.goal.id=:goal and r.user.id=:owner order by r.endsOn desc,r.id desc",GoalReviewRow.class)
                .setParameter("goal",id).setParameter("owner",owner()).getResultStream().map(this::review).toList();
    }
    GoalReviewRow ownedReview(GoalRow goal,Long reviewId) {
        return em.createQuery("select r from GoalReviewRow r where r.goal=:goal and r.id=:id and r.user.id=:owner",GoalReviewRow.class)
                .setParameter("goal",goal).setParameter("id",reviewId).setParameter("owner",owner()).getResultStream().map(r->org.hibernate.Hibernate.unproxy(r,GoalReviewRow.class)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"找不到復盤"));
    }
    Review review(GoalReviewRow row) {
        row=org.hibernate.Hibernate.unproxy(row,GoalReviewRow.class);
        var status=ReviewStatus.valueOf(row.status);
        ReviewSnapshot snapshot=status==ReviewStatus.SUBMITTED
                ? row.snapshot==null?null:mapper.readValue(row.snapshot,ReviewSnapshot.class)
                : row.goal==null?null:reviewSnapshot(row,null);
        var content=reviewContent(row);
        return new Review(row.id,row.startsOn,row.endsOn,status,content,row.version,row.updatedAt,row.submittedAt,snapshot,row.goal==null?0:row.goal.version);
    }
    ReviewSnapshot reviewSnapshot(GoalReviewRow row,Status resultingStatus) {
        var goal=row.goal;var definition=definition(goal);
        var metrics=definition.metrics().stream().map(key->metric(goal,key,row.startsOn,row.endsOn)).toList();
        var logs=logs(goal.id).stream().filter(l->!l.occurredOn().isBefore(row.startsOn)&&!l.occurredOn().isAfter(row.endsOn)).toList();
        return new ReviewSnapshot(2,definition,metrics,definition.sources(),logs,countDefinition(definition),now(),resultingStatus);
    }
    ReviewContent reviewContent(GoalReviewRow row) {
        if(row.content==null||row.content.isBlank())return new ReviewContent("","","",null,null);
        return mapper.readValue(row.content,ReviewContent.class);
    }
    LocalDate latestReviewEnd(GoalRow goal) {
        return em.createQuery("select r.endsOn from GoalReviewRow r where r.goal=:goal and r.status='SUBMITTED' order by r.endsOn desc",LocalDate.class)
                .setParameter("goal",goal).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }
    public Review previewReview(Long id) {
        var goal=owned(id);var latest=latestReviewEnd(goal);
        var starts=latest==null?goal.startsOn:latest.plusDays(1);
        var today=now().toLocalDate();var ends=goal.endsOn.isBefore(today)?goal.endsOn:today;
        if(ends.isBefore(starts))fail(HttpStatus.BAD_REQUEST,"目前沒有待復盤的期間");
        var row=new GoalReviewRow();row.user=goal.user;row.goal=goal;row.startsOn=starts;row.endsOn=ends;
        row.status=ReviewStatus.DRAFT.name();row.content=json(new ReviewContent("","",null,null,null));
        row.createdAt=now();row.updatedAt=row.createdAt;
        return review(row);
    }
    @Transactional public Review initializeReview(Long id,ReviewContent content) {
        if((content.observation()==null||content.observation().isBlank())
                &&(content.decision()==null||content.decision().isBlank())
                &&(content.direction()==null||content.direction().isBlank())
                &&content.informationHelpfulness()==null&&content.actionEffectiveness()==null)
            fail(HttpStatus.BAD_REQUEST,"請先填寫復盤內容");
        lock();var preview=previewReview(id);var goal=owned(id);
        var existing=em.createQuery("select count(r) from GoalReviewRow r where r.goal=:goal and r.status='DRAFT' and r.startsOn=:starts and r.endsOn<=:ends",Long.class)
                .setParameter("goal",goal).setParameter("starts",preview.startsOn()).setParameter("ends",preview.endsOn()).getSingleResult();
        if(existing>0)fail(HttpStatus.CONFLICT,"已有未完成的復盤，請返回目標繼續填寫");
        var row=new GoalReviewRow();row.user=goal.user;row.goal=goal;row.startsOn=preview.startsOn();row.endsOn=preview.endsOn();
        row.status=ReviewStatus.DRAFT.name();row.content=json(content);row.version=1;row.createdAt=now();row.updatedAt=row.createdAt;
        em.persist(row);return review(row);
    }
    public Review review(Long id,Long reviewId) { return review(ownedReview(owned(id),reviewId)); }
    @Transactional public Review saveReview(Long id,Long reviewId,ReviewSave request) {
        lock();var goal=owned(id);var row=ownedReview(goal,reviewId);if(row.version!=request.version())fail(HttpStatus.CONFLICT,"復盤已有更新，請重新讀取");
        if(ReviewStatus.SUBMITTED.name().equals(row.status)) {
            if(request.content().direction()==null||request.content().direction().isBlank())fail(HttpStatus.BAD_REQUEST,"請選擇本次決定");
        }
        row.content=json(request.content());row.version++;row.updatedAt=now();return review(row);
    }
    @Transactional public Review submitReview(Long id,Long reviewId,ReviewSubmit request) {
        lock();var goal=owned(id);var row=ownedReview(goal,reviewId);if(ReviewStatus.SUBMITTED.name().equals(row.status)&&request.requestId().toString().equals(row.submitKey))return review(row);if(!ReviewStatus.DRAFT.name().equals(row.status)||row.version!=request.version())fail(HttpStatus.CONFLICT,"復盤已有更新，請重新讀取");
        if(goal.version!=request.goalVersion())fail(HttpStatus.CONFLICT,"目標設定已有更新；請保留內容並重新確認摘要後再完成復盤");
        var content=reviewContent(row);if(content.direction()==null||content.direction().isBlank())fail(HttpStatus.BAD_REQUEST,"請選擇本次決定");
        var latest=latestReviewEnd(goal);var expected=latest==null?goal.startsOn:latest.plusDays(1);
        if(!row.startsOn.equals(expected)||row.endsOn.isAfter(goal.endsOn)||row.endsOn.isAfter(now().toLocalDate()))fail(HttpStatus.CONFLICT,"復盤期間已變更或已完成，請重新確認");
        Status target=request.targetStatus();
        Status directionStatus=switch(content.direction()){case "PAUSE" -> Status.PAUSED;case "COMPLETE" -> Status.COMPLETED;case "STOP" -> Status.ENDED;default -> null;};
        if(!Objects.equals(target,directionStatus))fail(HttpStatus.BAD_REQUEST,"請確認本次決定對應的目標狀態");
        var snapshot=reviewSnapshot(row,target==null?status(goal):target);
        row.snapshot=json(snapshot);
        if(target!=null&&target!=status(goal)) {
            var d=definition(goal);
            var changed=new Definition(d.name(),d.startsOn(),d.endsOn(),d.expectation(),d.motivation(),d.imageUrl(),target,d.archived(),d.pinned(),d.pinOrder(),d.reviewDays(),d.remindersEnabled(),d.allTags(),d.sources(),d.metrics());
            applyReviewStatus(goal,changed,"復盤決定："+content.direction()+(content.decision()==null?"":"；"+content.decision()));
        }
        row.status=ReviewStatus.SUBMITTED.name();row.submitKey=request.requestId().toString();row.submittedAt=now();row.updatedAt=row.submittedAt;row.version++;return review(row);
    }
    void applyReviewStatus(GoalRow goal,Definition definition,String reason) {
        // 狀態變更沿用原來源外鍵（包含已刪除來源的空引用），不重新建立失效關聯。
        var sources=em.createQuery("select s from GoalSourceRow s where s.rule.goal=:goal and s.rule.revision=:revision",GoalSourceRow.class)
                .setParameter("goal",goal).setParameter("revision",goal.version).getResultList();
        goal.definition=json(definition);goal.status=definition.status().name();goal.version++;
        goal.closedAt=definition.status()==Status.COMPLETED||definition.status()==Status.ENDED?Optional.ofNullable(goal.closedAt).orElse(now()):null;
        var revision=new GoalRuleRow();revision.user=goal.user;revision.goal=goal;revision.revision=goal.version;revision.definition=goal.definition;revision.reason=reason;revision.effectiveAt=now();em.persist(revision);
        for(var source:sources) {
            var copy=new GoalSourceRow();copy.user=goal.user;copy.rule=revision;copy.kind=source.kind;copy.sourceId=source.sourceId;copy.excluded=source.excluded;
            copy.tag=source.tag;copy.experiment=source.experiment;copy.thread=source.thread;copy.work=source.work;copy.treasure=source.treasure;copy.card=source.card;em.persist(copy);
        }
    }
    public List<Reminder> reminders() {
        var now=now();return rows().stream().filter(g->!g.archived&&status(g)==Status.ACTIVE&&definition(g).remindersEnabled()).map(g->reminder(g,now)).flatMap(Optional::stream).toList();
    }
    Optional<Reminder> reminder(GoalRow goal,ZonedDateTime current) {
        var definition=definition(goal);var latest=latestReviewEnd(goal);
        if(latest!=null&&!latest.isBefore(goal.endsOn))return Optional.empty();
        var due=Optional.ofNullable(latest).map(d->d.plusDays(definition.reviewDays())).orElse(goal.startsOn.plusDays(definition.reviewDays()));
        if(due.isAfter(goal.endsOn))due=goal.endsOn;
        if(due.isAfter(current.toLocalDate())||current.getHour()<9&&due.equals(current.toLocalDate()))return Optional.empty();var row=em.createQuery("select r from GoalReminderRow r where r.goal=:goal",GoalReminderRow.class).setParameter("goal",goal).getResultStream().findFirst().orElse(null);
        if(row!=null&&row.snoozedUntil!=null&&row.snoozedUntil.isAfter(current))return Optional.empty();return Optional.of(new Reminder(goal.id,definition.name(),due,row==null?null:row.snoozedUntil,row==null?0:row.version));
    }
    @Transactional public Reminder snooze(ReminderSnooze request) {
        lock();if(!request.until().isAfter(now()))fail(HttpStatus.BAD_REQUEST,"稍後時間需晚於現在");var goal=owned(request.goalId());var rows=em.createQuery("select r from GoalReminderRow r where r.goal=:goal",GoalReminderRow.class).setParameter("goal",goal).getResultList();var row=rows.isEmpty()?new GoalReminderRow():rows.getFirst();
        row.snoozedUntil=request.until().withZoneSameInstant(ZoneId.of("Asia/Taipei"));row.updatedAt=now();if(row.id==null){if(request.version()!=0)fail(HttpStatus.CONFLICT,"提醒已有更新，請重新讀取");row.user=goal.user;row.goal=goal;row.version=1;em.persist(row);}else {if(row.version!=request.version())fail(HttpStatus.CONFLICT,"提醒已有更新，請重新讀取");row.version++;}return new Reminder(goal.id,definition(goal).name(),Optional.ofNullable(latestReviewEnd(goal)).map(d->d.plusDays(definition(goal).reviewDays())).orElse(goal.startsOn.plusDays(definition(goal).reviewDays())),row.snoozedUntil,row.version);
    }
    String blankToNull(String value) { return value==null||value.isBlank()?null:value.trim(); }
    @Transactional public void delete(Long id,long version) {
        lock();var g=owned(id);if(g.version!=version)fail(HttpStatus.CONFLICT,"目標已有更新，請重新載入");
        for(String entity:List.of("GoalReminderRow","GoalProgressLogRow","GoalReviewRow","GoalEvidenceOverrideRow","GoalCardDayRow","GoalFactLinkRow"))em.createQuery("delete from "+entity+" l where l.goal.id=:id").setParameter("id",id).executeUpdate();
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
        // 旗幟是目前可行動的追蹤提示；非進行中目標仍保留於目標詳情與歷程，但不佔用來源畫面的提示。
        var goals=rows().stream().filter(g->!g.archived&&status(g)==Status.ACTIVE).toList();
        if(goals.isEmpty()||objects.isEmpty())return result;
        Map<Long,GoalRow> byId=goals.stream().collect(Collectors.toMap(g->g.id,g->g));
        Map<Long,Definition> definitions=goals.stream().collect(Collectors.toMap(g->g.id,this::definition));
        var requestedCards=objects.stream().filter(s->s.kind()==Kind.CARD).map(Source::id).distinct().toList();
        if(!requestedCards.isEmpty()&&!goals.isEmpty()) {
            Map<String,Object> params=new HashMap<>();List<String> parts=new ArrayList<>();
            for(var g:goals)parts.add("select c.id,"+g.id+" as goal_id from cards c where c.user_id=:owner and c.id in (:cards) and "+predicate(definition(g),"g"+g.id+"p",params));
            var q=em.createNativeQuery("select distinct id,goal_id from ("+String.join(" union all ",parts)+") matches").setParameter("owner",owner()).setParameter("cards",requestedCards);params.forEach(q::setParameter);
            @SuppressWarnings("unchecked") List<Object[]> values=q.getResultList();for(var v:values){var g=byId.get(((Number)v[1]).longValue());var definition=definitions.get(g.id);result.get("CARD:"+v[0]).add(new Association(g.id,definition.name(),status(g),cardReasons(definition,((Number)v[0]).longValue())));}
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
                    var d=definitions.get(g.id);List<AssociationReason> reasons=new ArrayList<>();
                    if(d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==kind&&s.id().equals(id)))reasons.add(new AssociationReason(kind,id,null,true));
                    if(kind==Kind.THREAD&&d.sources().stream().noneMatch(s->s.excluded()&&s.kind()==Kind.THREAD&&s.id().equals(id))
                            &&d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.EXPERIMENT&&s.id().equals(threadExperiments.get(id)))) {
                        Long experiment=threadExperiments.get(id);reasons.add(new AssociationReason(Kind.EXPERIMENT,experiment,"因「"+sourceTitle(Kind.EXPERIMENT,experiment)+"」實驗",false));
                    }
                    if(kind==Kind.EXPERIMENT&&d.sources().stream().noneMatch(s->!s.excluded()&&s.kind()==Kind.EXPERIMENT&&id.equals(s.id()))) {
                        d.sources().stream().filter(s->!s.excluded()&&s.kind()==Kind.THREAD&&id.equals(threadExperiments.get(s.id())))
                                .forEach(s->reasons.add(new AssociationReason(Kind.THREAD,s.id(),"因「"+sourceTitle(Kind.THREAD,s.id())+"」探索線",false)));
                    }
                    if(kind==Kind.TREASURE&&d.sources().stream().anyMatch(s->!s.excluded()&&s.kind()==Kind.WORK&&id.equals(workTreasures.get(s.id())))) {
                        d.sources().stream().filter(s->!s.excluded()&&s.kind()==Kind.WORK&&id.equals(workTreasures.get(s.id())))
                                .forEach(s->reasons.add(new AssociationReason(Kind.WORK,s.id(),"因「"+sourceTitle(Kind.WORK,s.id())+"」作品關聯",false)));
                    }
                    if(!reasons.isEmpty())result.get(kind+":"+id).add(new Association(g.id,d.name(),status(g),reasons));
                }
            }
        }
        return result;
    }
    List<AssociationReason> cardReasons(Definition definition,Long cardId) {
        var included=definition.sources().stream().filter(s->!s.excluded()).toList();var reasons=new ArrayList<AssociationReason>();
        if(included.stream().anyMatch(s->s.kind()==Kind.CARD&&s.id().equals(cardId)))reasons.add(new AssociationReason(Kind.CARD,cardId,"直接選入此卡片",false));
        var tags=ids(included,Kind.TAG);var matchedTags=cardRelatedIds("select tag_id from card_tags where card_id=:card and tag_id in (:ids)",cardId,tags,List.of());
        if(!tags.isEmpty()&&(!definition.allTags()||matchedTags.containsAll(tags)))for(Long id:matchedTags)reasons.add(new AssociationReason(Kind.TAG,id,"因 #"+sourceTitle(Kind.TAG,id)+" 標籤",false));
        var threads=ids(included,Kind.THREAD);for(Long id:cardRelatedIds("select thread_id from exploration_thread_cards where card_id=:card and thread_id in (:ids)",cardId,threads,List.of()))reasons.add(new AssociationReason(Kind.THREAD,id,"因「"+sourceTitle(Kind.THREAD,id)+"」探索線收錄",false));
        var experiments=ids(included,Kind.EXPERIMENT);var excludedThreads=definition.sources().stream().filter(s->s.excluded()&&s.kind()==Kind.THREAD).map(Source::id).toList();
        String experimentSql="select t.experiment_id from exploration_thread_cards tc join exploration_threads t on t.id=tc.thread_id where tc.card_id=:card and t.experiment_id in (:ids)"+(excludedThreads.isEmpty()?"":" and tc.thread_id not in (:excluded)");
        for(Long id:cardRelatedIds(experimentSql,cardId,experiments,excludedThreads))reasons.add(new AssociationReason(Kind.EXPERIMENT,id,"因「"+sourceTitle(Kind.EXPERIMENT,id)+"」實驗收錄",false));
        var works=ids(included,Kind.WORK);for(Long id:cardRelatedIds("select work_id from creative_work_sources where card_id=:card and work_id in (:ids)",cardId,works,List.of()))reasons.add(new AssociationReason(Kind.WORK,id,"因「"+sourceTitle(Kind.WORK,id)+"」作品引用",false));
        return reasons;
    }
    List<Long> ids(List<Source> sources,Kind kind) { return sources.stream().filter(s->s.kind()==kind).map(Source::id).toList(); }
    Set<Long> cardRelatedIds(String sql,Long cardId,List<Long> ids,List<Long> excluded) {
        if(ids.isEmpty())return Set.of();var query=em.createNativeQuery(sql).setParameter("card",cardId).setParameter("ids",ids);if(!excluded.isEmpty())query.setParameter("excluded",excluded);
        @SuppressWarnings("unchecked") List<Number> values=query.getResultList();return values.stream().map(Number::longValue).collect(Collectors.toCollection(LinkedHashSet::new));
    }
    String sourceTitle(Kind kind,Long id) {
        String title=kind==Kind.TAG||kind==Kind.THREAD?"s.name":"s.title";String from=kind==Kind.THREAD?"exploration_threads s join experiments e on e.id=s.experiment_id":sourceTable(kind)+" s";String user=kind==Kind.THREAD?"e.user_id":"s.user_id";
        @SuppressWarnings("unchecked") List<Object> values=em.createNativeQuery("select "+title+" from "+from+" where s.id=:id and "+user+"=:owner").setParameter("id",id).setParameter("owner",owner()).getResultList();
        return values.isEmpty()?"已刪除來源":String.valueOf(values.getFirst());
    }
    @Transactional public void clearOwner(Long owner) {
        if(users.findByIdForUpdate(owner).isEmpty())return;
        for(String entity:List.of("GoalReminderRow","GoalProgressLogRow","GoalReviewRow","GoalEvidenceOverrideRow","GoalCardDayRow","GoalFactLinkRow","GoalSourceRow","GoalRuleRow","GoalPreferenceRow","GoalFactRow","CardContentDayRow","GoalRow"))
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
