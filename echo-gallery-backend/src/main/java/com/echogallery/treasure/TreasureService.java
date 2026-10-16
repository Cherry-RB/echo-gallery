package com.echogallery.treasure;

import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.card.PageResponse;
import com.echogallery.experiment.Experiment;
import com.echogallery.experiment.ExperimentRepository;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreasureService {
    private final TreasureRepository treasures;
    private final TreasureTrialRepository trials;
    private final TreasureTrialAttemptRepository attempts;
    private final TreasureYieldRepository yields;
    private final TreasureRelationRepository relations;
    private final CardRepository cards;
    private final ExperimentRepository experiments;
    private final TreasureLifecycle lifecycle;
    private final Clock clock;

    public PageResponse<TreasureResponses.Summary> list(String keyword, TreasureRank rank, boolean archived, int page, int size) {
        Long owner = owner();
        String term = clean(keyword);
        Specification<Treasure> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("user").get("id"), owner));
            predicates.add(cb.equal(root.get("archived"), archived));
            if (rank != null) predicates.add(cb.equal(root.get("rank"), rank));
            if (term != null) {
                String escaped = term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var results = treasures.findAll(spec, paging(page, size).withSort(Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        var ids = results.getContent().stream().map(Treasure::getId).toList();
        Map<Long, TreasureTrial> current = new HashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            var definitions = trials.findByTreasureIdInOrderByTargetRank(ids);
            for (var treasure : results.getContent()) {
                var route = definitions.stream().filter(d -> d.getTreasure().getId().equals(treasure.getId())).toList();
                var selected = selectChallenge(treasure, route);
                if (selected != null) current.put(treasure.getId(), selected);
            }
            yields.countForTreasures(ids).forEach(count -> counts.put(count.getTreasureId(), count.getTotal()));
        }
        return page(results, t -> summary(t, current.get(t.getId()), counts.getOrDefault(t.getId(), 0L)));
    }

    public TreasureResponses.Detail detail(Long id) { return detail(owned(id)); }

    @Transactional
    public TreasureResponses.Detail create(TreasureRequests.Content request) {
        var user = lifecycle.lockOwner(owner());
        Treasure treasure = new Treasure();
        treasure.setUser(user);
        treasure.setCreatedAt(now());
        content(treasure, request);
        treasures.saveAndFlush(treasure);
        replaceSources(treasure, request.sourceTreasureIds());
        return detail(treasure);
    }

    @Transactional
    public TreasureResponses.Detail update(Long id, TreasureRequests.Content request) {
        lifecycle.lockOwner(owner());
        Treasure treasure = owned(id);
        content(treasure, request);
        replaceSources(treasure, request.sourceTreasureIds());
        return detail(treasure);
    }

    @Transactional
    public TreasureResponses.Detail archive(Long id, boolean archived) {
        lifecycle.lockOwner(owner());
        Treasure t = owned(id);
        t.setArchived(archived);
        t.setUpdatedAt(now());
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        lifecycle.lockOwner(owner());
        Treasure t = owned(id);
        relations.deleteTreasure(id);
        attempts.deleteTreasure(id);
        trials.deleteTreasure(id);
        yields.deleteTreasure(id);
        treasures.delete(t);
    }

    public TreasureResponses.Stats stats() {
        Long owner = owner();
        return new TreasureResponses.Stats(treasures.countByUserIdAndArchivedFalse(owner),
                treasures.countByUserIdAndArchivedFalseAndRank(owner, TreasureRank.STONE),
                treasures.countByUserIdAndArchivedFalseAndRank(owner, TreasureRank.BRONZE),
                treasures.countByUserIdAndArchivedFalseAndRank(owner, TreasureRank.SILVER),
                treasures.countByUserIdAndArchivedFalseAndRank(owner, TreasureRank.GOLD));
    }

    public long activeCount() { return treasures.countByUserIdAndArchivedFalse(owner()); }

    public PageResponse<TreasureResponses.Reference> relations(Long id, boolean sources, int page, int size) {
        owned(id);
        Page<TreasureRelation> result = sources ? relations.findByTargetIdOrderByIdDesc(id, paging(page, size))
                : relations.findBySourceIdOrderByIdDesc(id, paging(page, size));
        return page(result, r -> reference(sources ? r.getSource() : r.getTarget()));
    }

    public PageResponse<TreasureResponses.Trial> trials(Long id, int page, int size) {
        owned(id);
        return page(trials.findByTreasureIdOrderByCreatedAtDescIdDesc(id, paging(page, size)), TreasureService::trial);
    }

    public List<TreasureResponses.Trial> route(Long id) {
        owned(id);
        return trials.findByTreasureIdOrderByTargetRank(id).stream().map(TreasureService::trial).toList();
    }

    public PageResponse<TreasureResponses.Attempt> attempts(Long id, Long trialId, int page) {
        owned(id); trialOwned(id, trialId);
        return page(attempts.findByTrialIdOrderByAttemptedAtDescIdDesc(trialId, PageRequest.of(Math.max(0, page), 10)), TreasureService::attemptResponse);
    }

    @Transactional
    public TreasureResponses.Trial createTrial(Long id, TreasureRequests.Trial request) {
        lifecycle.lockOwner(owner());
        Treasure t = active(id);
        TreasureRank rank = request.targetRank() == null ? t.getRank().next() : request.targetRank();
        if (rank == null || rank == TreasureRank.STONE) bad("石級由入藏取得，只能設定銅、銀、金級檢定");
        if (rank.ordinal() <= t.getRank().ordinal()) conflict("不能替已取得的等級新增檢定");
        if (trials.existsByTreasureIdAndTargetRank(id, rank)) conflict("這一階已有檢定，請編輯原檢定或新增嘗試");
        TreasureTrial definition = new TreasureTrial();
        definition.setTreasure(t); definition.setTargetRank(rank); definition.setCreatedAt(now());
        definition.setTitle(required(request.title())); definition.setCriteria(required(request.criteria()));
        definition.setStatus(TrialStatus.PLANNED); definition.setUpdatedAt(now());
        trials.saveAndFlush(definition);
        t.setUpdatedAt(now());
        return trial(definition);
    }

    @Transactional
    public TreasureResponses.Trial updateTrial(Long id, Long trialId, TreasureRequests.Trial request) {
        lifecycle.lockOwner(owner());
        Treasure t = active(id);
        TreasureTrial definition = trialOwned(id, trialId);
        if (definition.getStatus() == TrialStatus.PASSED) conflict("通過的檢定不可改寫");
        if (request.targetRank() != null && request.targetRank() != definition.getTargetRank()) conflict("不可改變既有檢定的目標等級");
        definition.setTitle(required(request.title())); definition.setCriteria(required(request.criteria()));
        // 編輯定義只修改名稱與條件；新增嘗試只能使用獨立操作。
        definition.setUpdatedAt(now()); t.setUpdatedAt(now());
        return trial(definition);
    }

    @Transactional
    public TreasureResponses.Attempt recordAttempt(Long id, Long trialId, TreasureRequests.Attempt request) {
        lifecycle.lockOwner(owner());
        return attemptResponse(appendAttempt(active(id), trialOwned(id, trialId), request));
    }

    @Transactional
    public TreasureResponses.Trial pass(Long id, Long trialId, TreasureRequests.Result request) {
        lifecycle.lockOwner(owner());
        Treasure t = active(id);
        TreasureTrial definition = trialOwned(id, trialId);
        if (definition.getStatus() == TrialStatus.PASSED) return trial(definition);
        appendAttempt(t, definition, new TreasureRequests.Attempt(TrialStatus.PASSED, request.resultNote(), request.evidenceUrl(), request.attemptedAt()));
        return trial(definition);
    }

    private TreasureTrialAttempt appendAttempt(Treasure treasure, TreasureTrial definition, TreasureRequests.Attempt request) {
        if (definition.getStatus() == TrialStatus.PASSED) conflict("已通過的檢定不可再新增嘗試");
        if (definition.getTargetRank() != treasure.getRank().next()) conflict("請先完成前一階檢定，再記錄這一階的嘗試");
        if (!List.of(TrialStatus.ATTEMPTED, TrialStatus.NOT_PASSED, TrialStatus.PASSED).contains(request.status())) bad("請選擇實際嘗試的結果");
        attempted(request.attemptedAt());
        TreasureTrialAttempt attempt = new TreasureTrialAttempt();
        attempt.setTrial(definition); attempt.setTitleSnapshot(definition.getTitle()); attempt.setCriteriaSnapshot(definition.getCriteria());
        attempt.setStatus(request.status()); attempt.setResultNote(required(request.resultNote()));
        attempt.setEvidenceUrl(url(request.evidenceUrl())); attempt.setAttemptedAt(request.attemptedAt().withZoneSameInstant(clock.getZone())); attempt.setCreatedAt(now());
        attempts.saveAndFlush(attempt);
        // 以下只作為目前狀態摘要，不可取代獨立且不可變的嘗試歷程。
        definition.setStatus(request.status()); definition.setResultNote(attempt.getResultNote()); definition.setEvidenceUrl(attempt.getEvidenceUrl());
        definition.setAttemptedAt(attempt.getAttemptedAt()); definition.setUpdatedAt(now()); treasure.setUpdatedAt(now());
        if (request.status() == TrialStatus.PASSED) { definition.setPassedAt(now()); treasure.setRank(definition.getTargetRank()); }
        return attempt;
    }

    private static TreasureResponses.Attempt attemptResponse(TreasureTrialAttempt attempt) {
        return new TreasureResponses.Attempt(attempt.getId(), attempt.getTitleSnapshot(), attempt.getCriteriaSnapshot(), attempt.getStatus(), attempt.getResultNote(), attempt.getEvidenceUrl(), attempt.getAttemptedAt(), attempt.getCreatedAt());
    }

    public PageResponse<TreasureResponses.Yield> yields(Long id, int page, int size) {
        owned(id);
        return page(yields.findByTreasureIdOrderByOccurredOnDescIdDesc(id, paging(page, size)), TreasureService::yield);
    }

    @Transactional
    public TreasureResponses.Yield saveYield(Long id, Long yieldId, TreasureRequests.Yield request) {
        lifecycle.lockOwner(owner());
        // 封存只退出目前館藏；仍可記下後續產出，但既有紀錄維持封存時的編輯限制。
        Treasure t = yieldId == null ? owned(id) : active(id);
        TreasureYield y = yieldId == null ? new TreasureYield() : yields.findByIdAndTreasureId(yieldId, id).orElseThrow(TreasureService::notFound);
        if (request.occurredOn().isAfter(LocalDate.now(clock))) bad("收益必須是已發生事件");
        y.setTreasure(t);
        y.setTitle(required(request.title()));
        y.setDescription(clean(request.description()));
        y.setOccurredOn(request.occurredOn());
        y.setRelatedCard(card(request.relatedCardId()));
        if (yieldId == null) y.setCreatedAt(now());
        y.setUpdatedAt(now());
        yields.save(y);
        t.setUpdatedAt(now());
        return TreasureService.yield(y);
    }

    @Transactional
    public void deleteYield(Long id, Long yieldId) {
        lifecycle.lockOwner(owner());
        Treasure t = active(id);
        yields.delete(yields.findByIdAndTreasureId(yieldId, id).orElseThrow(TreasureService::notFound));
        t.setUpdatedAt(now());
    }

    public List<TreasureResponses.Summary> actions() {
        var candidates = treasures.findActionCandidates(owner(), PageRequest.of(0, 3));
        if (candidates.isEmpty()) return List.of();
        var ids = candidates.stream().map(Treasure::getId).toList();
        var definitions = trials.findByTreasureIdInOrderByTargetRank(ids);
        Map<Long, Long> counts = new HashMap<>();
        yields.countForTreasures(ids).forEach(count -> counts.put(count.getTreasureId(), count.getTotal()));
        // 行動摘要只呈現目前下一階，未來規劃與已發生事件不補作進行中挑戰。
        return candidates.stream().flatMap(t -> definitions.stream()
                .filter(d -> d.getTreasure().getId().equals(t.getId()) && d.getTargetRank() == t.getRank().next())
                .filter(d -> List.of(TrialStatus.PLANNED, TrialStatus.ATTEMPTED, TrialStatus.NOT_PASSED).contains(d.getStatus()))
                .findFirst().map(d -> summary(t, d, counts.getOrDefault(t.getId(), 0L))).stream()).toList();
    }

    public TreasureResponses.Growth growth(ZonedDateTime start, ZonedDateTime end) {
        return new TreasureResponses.Growth(treasures.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(owner(), start, end),
                trials.countPromoted(owner(), start, end),
                yields.countYieldTreasures(owner(), dateBoundary(start), dateBoundary(end)),
                yields.countByTreasureUserIdAndOccurredOnGreaterThanEqualAndOccurredOnLessThan(owner(), dateBoundary(start), dateBoundary(end)));
    }

    public TreasureResponses.RecentActivity recentActivity(ZonedDateTime start, ZonedDateTime end) {
        Long owner = owner();
        Pageable limit = PageRequest.of(0, 3);
        record Candidate(Long id, TreasureResponses.Event event) {}
        List<Candidate> events = new ArrayList<>();
        treasures.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(owner, start, end, limit)
                .forEach(t -> events.add(new Candidate(t.getId(), event(t, "COLLECTED", "收入館藏", t.getCreatedAt(), null))));
        trials.findByTreasureUserIdAndPassedAtGreaterThanEqualAndPassedAtLessThanOrderByPassedAtDescIdDesc(owner, start, end, limit)
                .stream().filter(t -> t.getTargetRank() != null)
                .forEach(t -> events.add(new Candidate(t.getId(), event(t.getTreasure(), "PROMOTED", t.getTitle(), t.getPassedAt(), t.getTargetRank()))));
        attempts.findByTrialTreasureUserIdAndAttemptedAtGreaterThanEqualAndAttemptedAtLessThanOrderByAttemptedAtDescIdDesc(owner, start, end, limit)
                .forEach(a -> events.add(new Candidate(a.getId(), event(a.getTrial().getTreasure(), "TRIAL_ATTEMPTED", a.getTitleSnapshot(), a.getAttemptedAt(), null))));
        var recentYields = yields.findByTreasureUserIdAndOccurredOnGreaterThanEqualAndOccurredOnLessThanOrderByOccurredOnDescIdDesc(owner, dateBoundary(start), dateBoundary(end), limit)
                .stream().map(y -> event(y.getTreasure(), "YIELD", y.getTitle(), date(y.getOccurredOn()), null)).toList();
        // 兩種用途各自限制預覽筆數，不能先截斷混合事件再分組。
        var progress = events.stream().sorted(Comparator.comparing((Candidate c) -> c.event().eventAt()).reversed()
                .thenComparing(c -> c.event().kind()).thenComparing(Candidate::id, Comparator.reverseOrder()))
                .limit(3).map(Candidate::event).toList();
        return new TreasureResponses.RecentActivity(recentYields, progress);
    }

    private void content(Treasure t, TreasureRequests.Content r) {
        if (r.acquiredOn().isAfter(LocalDate.now(clock))) bad("只能收入已完成的成果");
        t.setTitle(required(r.title()));
        t.setDescription(clean(r.description()));
        t.setArtifactUrl(url(r.artifactUrl()));
        t.setCoverImageUrl(url(r.coverImageUrl()));
        t.setAcquiredOn(r.acquiredOn());
        t.setSourceCard(card(r.sourceCardId()));
        t.setSourceExperiment(experiment(r.sourceExperimentId()));
        t.setUpdatedAt(now());
    }

    private void replaceSources(Treasure t, List<Long> ids) {
        List<Long> sources = ids == null ? List.of() : ids;
        if (new HashSet<>(sources).size() != sources.size()) bad("來源奇珍不可重複");
        List<Treasure> verified = sources.stream().map(id -> {
            if (id.equals(t.getId())) bad("不可引用自己");
            Treasure source = owned(id);
            if (relations.wouldCreateCycle(t.getId(), id)) conflict("此來源會造成循環關聯");
            return source;
        }).toList();
        // 驗證完成後再替換，任一步失敗由交易完整回滾。
        relations.deleteSources(t.getId());
        for (Treasure source : verified) {
            TreasureRelation r = new TreasureRelation();
            r.setSource(source); r.setTarget(t); r.setCreatedAt(now());
            relations.save(r);
        }
    }

    private Treasure owned(Long id) { return treasures.findByIdAndUserId(id, owner()).orElseThrow(TreasureService::notFound); }
    private Treasure active(Long id) {
        Treasure t = owned(id);
        if (t.isArchived()) conflict("請先還原奇珍再操作");
        return t;
    }
    private TreasureTrial trialOwned(Long id, Long trialId) { return trials.findByIdAndTreasureId(trialId, id).orElseThrow(TreasureService::notFound); }
    private Card card(Long id) {
        if (id == null) return null;
        Card c = cards.findById(id).orElseThrow(TreasureService::notFound);
        if (!c.getUser().getId().equals(owner())) throw notFound();
        return c;
    }
    private Experiment experiment(Long id) {
        if (id == null) return null;
        Experiment e = experiments.findById(id).orElseThrow(TreasureService::notFound);
        if (!e.getUser().getId().equals(owner())) throw notFound();
        return e;
    }
    private TreasureResponses.Detail detail(Treasure t) {
        var selected = selectChallenge(t, trials.findByTreasureIdOrderByTargetRank(t.getId()));
        return new TreasureResponses.Detail(t.getId(), t.getTitle(), t.getDescription(), t.getArtifactUrl(), t.getCoverImageUrl(),
                t.getRank(), t.getRank().next(), t.getAcquiredOn(), t.isArchived(),
                t.getSourceCard() == null ? null : new TreasureResponses.Reference(t.getSourceCard().getId(), t.getSourceCard().getTitle()),
                t.getSourceExperiment() == null ? null : new TreasureResponses.Reference(t.getSourceExperiment().getId(), t.getSourceExperiment().getTitle()),
                relations.findByTargetIdOrderById(t.getId()).stream().map(r -> reference(r.getSource())).toList(),
                selected == null ? null : trial(selected), trials.countByTreasureId(t.getId()), yields.countByTreasureId(t.getId()),
                t.getCreatedAt(), t.getUpdatedAt());
    }
    // 下一階優先，其次最近的未來規劃，最後保留最近完成階段，不依更新時間換人。
    private static TreasureTrial selectChallenge(Treasure treasure, List<TreasureTrial> route) {
        var sorted = route.stream().sorted(Comparator.comparing(t -> t.getTargetRank().ordinal())).toList();
        return sorted.stream().filter(t -> t.getTargetRank().ordinal() > treasure.getRank().ordinal()).findFirst()
                .orElseGet(() -> sorted.isEmpty() ? null : sorted.getLast());
    }
    private static TreasureResponses.Summary summary(Treasure t, TreasureTrial activeTrial, long yieldCount) {
        String description = t.getDescription();
        if (description != null && description.codePointCount(0, description.length()) > 160) {
            description = description.substring(0, description.offsetByCodePoints(0, 160)) + "…";
        }
        return new TreasureResponses.Summary(t.getId(), t.getTitle(), description, t.getCoverImageUrl(), t.getRank(), t.getAcquiredOn(), t.isArchived(), t.getCreatedAt(), t.getUpdatedAt(), activeTrial == null ? null : trial(activeTrial), yieldCount);
    }
    private static TreasureResponses.Reference reference(Treasure t) { return new TreasureResponses.Reference(t.getId(), t.getTitle()); }
    private static TreasureResponses.Trial trial(TreasureTrial t) { return new TreasureResponses.Trial(t.getId(), t.getTargetRank(), t.getTitle(), t.getCriteria(), t.getStatus(), t.getResultNote(), t.getEvidenceUrl(), t.getAttemptedAt(), t.getPassedAt(), t.getCreatedAt(), t.getUpdatedAt()); }
    private static TreasureResponses.Yield yield(TreasureYield y) { return new TreasureResponses.Yield(y.getId(), y.getTitle(), y.getDescription(), y.getOccurredOn(), y.getRelatedCard() == null ? null : new TreasureResponses.Reference(y.getRelatedCard().getId(), y.getRelatedCard().getTitle()), y.getCreatedAt(), y.getUpdatedAt()); }
    private static TreasureResponses.Event event(Treasure t, String kind, String title, ZonedDateTime at, TreasureRank rank) { return new TreasureResponses.Event(t.getId(), t.getTitle(), kind, title, at, rank); }
    private static <T, R> PageResponse<R> page(Page<T> p, java.util.function.Function<T, R> mapper) { return new PageResponse<>(p.getContent().stream().map(mapper).toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()); }
    private static PageRequest paging(int page, int size) { return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 20)), Sort.by(Sort.Direction.DESC, "createdAt", "id")); }
    private Long owner() { return SecurityUtil.getCurrentUserId(); }
    private ZonedDateTime now() { return ZonedDateTime.now(clock); }
    private ZonedDateTime date(LocalDate d) { return d.atStartOfDay(clock.getZone()); }
    // DATE 事件以系統時區零時表示，轉為能保留原 timestamp 半開區間的日期邊界。
    private LocalDate dateBoundary(ZonedDateTime time) { var local = time.withZoneSameInstant(clock.getZone()); return local.toLocalTime().equals(LocalTime.MIDNIGHT) ? local.toLocalDate() : local.toLocalDate().plusDays(1); }
    private void attempted(ZonedDateTime at) { if (at == null || at.toInstant().isAfter(clock.instant())) bad("請填寫已發生的實際嘗試時間"); }
    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static String required(String text) { String value = clean(text); if (value == null) bad("請填寫必要內容"); return value; }
    private static String url(String value) {
        String text = clean(value);
        if (text == null) return null;
        try { URI uri = URI.create(text); if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return text; }
        catch (IllegalArgumentException ignored) { /* 由下方統一回傳驗證錯誤。 */ }
        bad("連結只支援有效的 HTTP 或 HTTPS 網址"); return null;
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "奇珍或來源不存在"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
