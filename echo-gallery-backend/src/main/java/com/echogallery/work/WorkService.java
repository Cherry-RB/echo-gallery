package com.echogallery.work;

import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.card.*;
import com.echogallery.experiment.*;
import com.echogallery.treasure.*;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class WorkService {
    private final CreativeWorkRepository works;
    private final WorkSourceRepository sources;
    private final CardRepository cards;
    private final ExperimentRepository experiments;
    private final TreasureRepository treasures;
    private final TreasureService treasureService;
    private final TreasureLifecycle lifecycle;
    private final Clock clock;

    public record Summary(Long id, String title, String description, Long experimentId, String experimentTitle,
            CreativeWork.Mode editorMode, CreativeWork.Stage stage, String nextStep, boolean published,
            boolean archived, Long treasureId, ZonedDateTime editedAt, Integer characterCount) {}
    public record Detail(Summary work, String body, long bodyVersion, String externalUrl, LocalDate firstCompletedOn,
            ZonedDateTime stageChangedAt, String shareUrl, LocalDate sharedOn, String shareNote) {}
    public record SourceView(Long id, Long cardId, Long recordId, String title, String text,
            Long experimentId, String experimentTitle, boolean archived, String tryText, String discovery) {}
    public record Usage(Long cardId, Long recordId, Long workId, String title, boolean archived) {}
    public record ExperimentOption(Long id, String title, boolean archived) {}
    public record SavedBody(String body, long bodyVersion, ZonedDateTime editedAt) {}

    public PageResponse<Summary> list(String keyword, List<Long> experimentIds, boolean unassigned,
            List<CreativeWork.Stage> stages, Boolean published, Boolean archived, int page, int size) {
        Specification<CreativeWork> spec = (r, q, cb) -> {
            var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.equal(r.get("user").get("id"), owner()));
            if (archived != null) p.add(cb.equal(r.get("archived"), archived));
            if (!experimentIds.isEmpty() || unassigned) {
                var experiment = r.join("experiment", jakarta.persistence.criteria.JoinType.LEFT);
                var selected = experiment.get("id").in(experimentIds);
                p.add(unassigned ? cb.or(cb.isNull(experiment.get("id")), selected) : selected);
            }
            if (!stages.isEmpty()) p.add(r.get("stage").in(stages));
            if (published != null) p.add(cb.equal(r.get("published"), published));
            if (keyword != null && !keyword.isBlank()) p.add(cb.like(cb.lower(r.get("title")), like(keyword), '\\'));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return page(works.findAll(spec, paging(page, size).withSort(Sort.by(Sort.Direction.DESC, "editedAt", "id"))), this::summary);
    }
    public Detail detail(Long id) { return detail(owned(id)); }
    public Detail fromTreasure(Long id) { return works.findByTreasureIdAndUserId(id, owner()).map(this::detail).orElse(null); }
    public List<ExperimentOption> experiments() {
        return experiments.findAllByUserId(owner()).stream().map(e -> new ExperimentOption(e.getId(), e.getTitle(), e.isArchived())).toList();
    }
    @Transactional public Detail create(WorkRequests.Info request) {
        var user = lifecycle.lockOwner(owner());
        var w = new CreativeWork(); w.setUser(user); w.setCreatedAt(now()); w.setStageChangedAt(now());
        info(w, request); works.saveAndFlush(w);
        if (request.sources() != null && !request.sources().isEmpty()) addSources(w.getId(), new WorkRequests.Sources(request.sources()));
        return detail(w);
    }
    @Transactional public Detail update(Long id, WorkRequests.Info request) {
        lock(); var w = active(id); info(w, request); return detail(w);
    }
    @Transactional public SavedBody saveBody(Long id, WorkRequests.Body request) {
        lock(); var w = active(id);
        if (w.getEditorMode() != CreativeWork.Mode.INTERNAL) fail(HttpStatus.CONFLICT, "請先切換為內建編輯");
        // owner 鎖涵蓋版本檢查與寫入，兩個分頁不能同時通過相同版本。
        if (w.getBodyVersion() != request.expectedVersion()) fail(HttpStatus.CONFLICT, "這份作品已有較新內容，請保留目前草稿再重新載入");
        if (!w.getBody().equals(request.body())) {
            w.setBody(request.body()); w.setBodyVersion(w.getBodyVersion() + 1); w.setEditedAt(now());
        }
        return new SavedBody(w.getBody(), w.getBodyVersion(), w.getEditedAt());
    }
    @Transactional public Detail nextStep(Long id, WorkRequests.NextStep request) {
        lock(); var w = active(id);
        if (!Objects.equals(w.getNextStep(), request.nextStep())) { w.setNextStep(request.nextStep()); w.setEditedAt(now()); }
        return detail(w);
    }
    @Transactional public Detail stage(Long id, WorkRequests.Stage request) {
        lock(); var w = active(id);
        if (request.firstCompletedOn() != null && request.firstCompletedOn().isAfter(LocalDate.now(clock))) fail(HttpStatus.BAD_REQUEST, "完成日期不可晚於今天");
        if (w.getStage() != request.stage() || !Objects.equals(w.getFirstCompletedOn(), request.firstCompletedOn())) {
            if (w.getStage() != request.stage()) { w.setStage(request.stage()); w.setStageChangedAt(now()); }
            w.setFirstCompletedOn(request.firstCompletedOn()); w.setEditedAt(now());
        }
        return detail(w);
    }
    @Transactional public Detail share(Long id, WorkRequests.Share request) {
        lock(); var w = active(id); String shareUrl = url(request.shareUrl());
        if (request.sharedOn() != null && request.sharedOn().isAfter(LocalDate.now(clock))) fail(HttpStatus.BAD_REQUEST, "分享日期不可晚於今天");
        if (w.isPublished() != request.published() || !Objects.equals(w.getShareUrl(), shareUrl) || !Objects.equals(w.getSharedOn(), request.sharedOn()) || !Objects.equals(w.getShareNote(), request.shareNote())) {
            w.setShareUrl(shareUrl); w.setPublished(request.published()); w.setSharedOn(request.sharedOn()); w.setShareNote(request.shareNote()); w.setEditedAt(now());
        }
        return detail(w);
    }
    @Transactional public Detail archive(Long id, boolean archived) {
        lock(); var w = owned(id); w.setArchived(archived); return detail(w);
    }
    @Transactional public void delete(Long id) { lock(); works.delete(owned(id)); }
    public List<SourceView> sources(Long id) {
        owned(id); return sources.findByWorkIdOrderByIdDesc(id).stream().map(s -> card(s.getId(), s.getCard())).toList();
    }
    @Transactional public List<SourceView> addSources(Long id, WorkRequests.Sources request) {
        lock(); var w = active(id);
        for (var input : request.sources()) {
            if (input.cardId() == null || input.recordId() != null) fail(HttpStatus.BAD_REQUEST, "作品參考素材僅接受卡片，請先將探索內容整理成卡片");
            var s = new WorkSource(); s.setWork(w);
            if (input.cardId() != null) {
                var c = cards.findById(input.cardId()).filter(x -> x.getUser().getId().equals(owner())).orElseThrow(this::missing);
                if (sources.existsByWorkIdAndCardId(id, c.getId())) continue;
                s.setCard(c);
            }
            sources.saveAndFlush(s);
        }
        return sources(id);
    }
    @Transactional public void removeSource(Long id, Long sourceId) {
        lock(); active(id);
        var source = sources.findById(sourceId).filter(x -> x.getWork().getId().equals(id)).orElseThrow(this::missing);
        sources.delete(source);
    }
    public List<Usage> usages(List<Long> cardIds, List<Long> recordIds) {
        if (cardIds.size() + recordIds.size() > 100) fail(HttpStatus.BAD_REQUEST, "一次最多查詢 100 筆來源");
        if (cardIds.isEmpty() && recordIds.isEmpty()) return List.of();
        return sources.usages(owner(), cardIds.isEmpty() ? List.of(-1L) : cardIds).stream()
                .map(s -> new Usage(s.getCard().getId(), null, s.getWork().getId(), s.getWork().getTitle(), s.getWork().isArchived())).toList();
    }
    public PageResponse<SourceView> candidates(String kind, String keyword, Long experimentId, int page, int size) {
        if ("CARD".equals(kind)) {
            Specification<Card> spec = (r, q, cb) -> {
                var own = cb.equal(r.get("user").get("id"), owner());
                if (keyword == null || keyword.isBlank()) return own;
                var title = cb.like(cb.lower(r.get("title")), like(keyword), '\\');
                try { return cb.and(own, cb.or(title, cb.equal(r.get("id"), Long.parseLong(keyword.trim())))); }
                catch (NumberFormatException ignored) { return cb.and(own, title); }
            };
            return page(cards.findAll(spec, paging(page, size).withSort(Sort.by(Sort.Direction.DESC, "id"))), c -> card(null, c));
        }
        fail(HttpStatus.BAD_REQUEST, "作品參考素材僅接受卡片");
        return null;
    }

    @Transactional public Detail link(Long id, Long treasureId) {
        lock(); var w = linkable(id);
        var t = treasures.findByIdAndUserId(treasureId, owner()).orElseThrow(this::missing);
        if (works.existsByTreasureId(treasureId)) fail(HttpStatus.CONFLICT, "這件奇珍已連到作品，請先解除關聯");
        w.setTreasure(t); return detail(w);
    }
    @Transactional public Detail createTreasure(Long id, TreasureRequests.Content request) {
        lock(); var w = linkable(id);
        var t = treasureService.create(request);
        w.setTreasure(treasures.findById(t.id()).orElseThrow(this::missing)); return detail(w);
    }
    @Transactional public Detail unlink(Long id) { lock(); var w = active(id); w.setTreasure(null); return detail(w); }
    private CreativeWork linkable(Long id) {
        var w = active(id);
        if (w.getStage() != CreativeWork.Stage.SHAREABLE || w.getTreasure() != null) fail(HttpStatus.CONFLICT, "作品須為可分享且尚未入藏");
        return w;
    }
    private void info(CreativeWork w, WorkRequests.Info r) {
        if (r.title().isBlank()) fail(HttpStatus.BAD_REQUEST, "請輸入作品名稱");
        Experiment e = r.experimentId() == null ? null : experiments.findById(r.experimentId()).filter(x -> x.getUser().getId().equals(owner())).orElseThrow(this::missing);
        String externalUrl = url(r.externalUrl());
        if (!Objects.equals(w.getTitle(), r.title().trim()) || !Objects.equals(w.getDescription(), r.description()) || !Objects.equals(w.getExperiment() == null ? null : w.getExperiment().getId(), r.experimentId()) || w.getEditorMode() != r.editorMode() || !Objects.equals(w.getExternalUrl(), externalUrl)) w.setEditedAt(now());
        w.setTitle(r.title().trim()); w.setDescription(r.description()); w.setExperiment(e);
        if (w.getEditorMode() != r.editorMode()) w.setBodyVersion(w.getBodyVersion() + 1);
        w.setEditorMode(r.editorMode()); w.setExternalUrl(externalUrl);
    }
    private SourceView card(Long id, Card c) {
        String text = java.util.stream.Stream.of(c.getUrl(), c.getCardNote(), c.getContent()).filter(s -> s != null && !s.isBlank()).distinct().collect(java.util.stream.Collectors.joining("\n\n"));
        return new SourceView(id, c.getId(), null, c.getTitle(), text, null, null, c.isArchived(), null, null);
    }
    private Summary summary(CreativeWork w) { return new Summary(w.getId(), w.getTitle(), w.getDescription(), w.getExperiment() == null ? null : w.getExperiment().getId(), w.getExperiment() == null ? null : w.getExperiment().getTitle(), w.getEditorMode(), w.getStage(), w.getNextStep(), w.isPublished(), w.isArchived(), w.getTreasure() == null ? null : w.getTreasure().getId(), w.getEditedAt(), w.getEditorMode() == CreativeWork.Mode.INTERNAL ? w.getBody().codePointCount(0, w.getBody().length()) : null); }
    private Detail detail(CreativeWork w) { return new Detail(summary(w), w.getBody(), w.getBodyVersion(), w.getExternalUrl(), w.getFirstCompletedOn(), w.getStageChangedAt(), w.getShareUrl(), w.getSharedOn(), w.getShareNote()); }
    private CreativeWork owned(Long id) { return works.findByIdAndUserId(id, owner()).orElseThrow(this::missing); }
    private CreativeWork active(Long id) { var w = owned(id); if (w.isArchived()) fail(HttpStatus.CONFLICT, "作品已封存，請先還原"); return w; }
    private Long owner() { var id = SecurityUtil.getCurrentUserId(); if (id == null) fail(HttpStatus.UNAUTHORIZED, "尚未登入"); return id; }
    private void lock() { lifecycle.lockOwner(owner()); }
    private ZonedDateTime now() { return ZonedDateTime.now(clock); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到作品或來源"); }
    private static void fail(HttpStatus status, String message) { throw new ResponseStatusException(status, message); }
    private static String url(String input) {
        if (input == null || input.isBlank()) return null;
        try { var u = URI.create(input.trim()); if (("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) && u.getHost() != null && u.getUserInfo() == null) return u.toString(); }
        catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "網址須為有效的 HTTP(S) 網址");
    }
    private static String like(String s) { return "%" + s.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private static PageRequest paging(int page, int size) { return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50))); }
    private static <A, B> PageResponse<B> page(Page<A> p, java.util.function.Function<A, B> f) { return new PageResponse<>(p.getContent().stream().map(f).toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()); }
}
