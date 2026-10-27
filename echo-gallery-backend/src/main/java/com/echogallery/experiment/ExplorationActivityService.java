package com.echogallery.experiment;

import java.time.*;
import java.util.*;
import com.echogallery.card.PageResponse;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplorationActivityService {
    private final ExperimentRepository experiments;
    private final ExperimentExplorationRepository states;
    private final ExplorationThreadRepository threads;
    private final ExplorationThreadMethodRepository methods;
    private final ExperimentExplorationRecordRepository records;
    private final ExperimentMethodPreferenceRepository preferences;
    private final Clock clock;

    public ExperimentExplorationResponse state(Long experimentId) {
        owned(experimentId);
        var state = states.findByExperimentId(experimentId).orElse(null);
        var favorite = preferences.findByExperimentIdAndFavoriteSlotIsNotNullOrderByFavoriteSlotAsc(experimentId)
                .stream().map(p -> option(p.getMethod().getId())).toList();
        var recent = preferences.findTop3ByExperimentIdAndLastUsedAtIsNotNullOrderByLastUsedAtDescIdDesc(experimentId)
                .stream().map(p -> option(p.getMethod().getId())).toList();
        var current = state == null || state.getCurrentMethod() == null ? null : option(state.getCurrentMethod().getId());
        var page = records(experimentId, null, null, false, 0, 20);
        return new ExperimentExplorationResponse(current == null ? "" : current.content(), current == null ? null : current.id(),
                favorite.stream().map(ExperimentExplorationResponse.MethodOption::content).toList(), page.content(),
                state == null || state.getCurrentThread() == null ? null : state.getCurrentThread().getId(),
                current, recent, favorite, state == null ? 0 : state.getVersion(), page.totalElements());
    }

    public PageResponse<ExperimentExplorationResponse.MethodOption> methods(Long experimentId, Long threadId, String keyword, int page, int size) {
        owned(experimentId);
        if (threadId != null && !threads.existsByIdAndExperimentId(threadId, experimentId)) throw missing();
        String escaped = keyword == null || keyword.isBlank() ? "" : "%" + keyword.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = methods.options(experimentId, threadId, escaped, paging(page, size));
        return new PageResponse<>(result.getContent().stream().map(this::option).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public PageResponse<ExperimentExplorationResponse.RecordResponse> records(Long experimentId, Long threadId, Long methodId, boolean unassigned, int page, int size) {
        owned(experimentId);
        if (threadId != null && !threads.existsByIdAndExperimentId(threadId, experimentId)) throw missing();
        if (methodId != null) {
            var method = ownedOption(experimentId, methodId);
            if (threadId != null && !method.threadId().equals(threadId)) bad("行動不屬於所選探索線");
            if (unassigned) bad("不可同時指定行動與未指定分組");
        }
        var result = records.page(experimentId, threadId, methodId, unassigned, paging(page, size));
        return new PageResponse<>(result.getContent().stream().map(this::record).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ExperimentExplorationResponse focus(Long experimentId, ExplorationActivityRequests.Focus request) {
        var experiment = lock(experimentId);
        var state = settings(experiment);
        version(state.getVersion(), request.expectedVersion());
        if (request.action() == null && request.methodId() != null && request.newContent() != null) bad("採用既有行動與新增行動不可同時指定");
        ExplorationThreadMethod method = request.methodId() == null ? null : method(experimentId, request.methodId());
        ExplorationThread target = request.threadId() == null ? null : thread(experimentId, request.threadId());
        if (method != null && request.action() == null && request.expectedMethodVersion() != null) version(method.getVersion(), request.expectedMethodVersion());
        if (request.action() != null) {
            if (method == null) bad("請先選擇要更新或複製的行動");
            version(method.getVersion(), request.expectedMethodVersion());
            String content = required(request.newContent(), 2000);
            if (request.action() == ExplorationActivityRequests.MethodAction.COPY) {
                if (method.getContent().equals(content)) bad("複製後的行動文字必須與來源不同");
                target = target == null ? unassigned(experiment) : target;
                method = create(experiment, target, content);
            } else {
                content = changedMethodContent(method.getContent(), content);
                if (target != null && !method.getThread().getId().equals(target.getId())) bad("更新文字不可同時搬移行動");
                if (records.countByMethodId(method.getId()) > 0) conflict("這個行動已有發現，請先完成整理再更新");
                checkName(method.getThread().getId(), content, method.getId());
                method.setContent(content);
                bump(method.getThread(), method);
                methods.flush();
            }
        } else if (request.newContent() != null) {
            target = target == null ? unassigned(experiment) : target;
            method = create(experiment, target, request.newContent());
        }
        if (method != null) {
            if (target != null && !method.getThread().getId().equals(target.getId())) bad("行動不屬於所選探索線");
            target = method.getThread();
            remember(experiment, method);
        }
        state.setCurrentThread(target);
        state.setCurrentMethod(method);
        state.setVersion(state.getVersion() + 1);
        experiment.setUpdatedAt(now());
        if (request.favoriteMethodIds() != null) return favorites(experimentId,
            new ExplorationActivityRequests.Favorites(request.favoriteMethodIds(), state.getVersion()));
        return state(experimentId);
    }

    @Transactional
    public ExperimentExplorationResponse favorites(Long experimentId, ExplorationActivityRequests.Favorites request) {
        var state = settings(lock(experimentId));
        version(state.getVersion(), request.expectedVersion());
        if (new HashSet<>(request.methodIds()).size() != request.methodIds().size()) bad("常用行動不可重複");
        var selected = request.methodIds().stream().map(id -> method(experimentId, id)).toList();
        // 先清空位置並 flush，避免交換常用順序時暫時違反唯一約束。
        var old = preferences.findByExperimentIdAndFavoriteSlotIsNotNullOrderByFavoriteSlotAsc(experimentId);
        old.forEach(p -> p.setFavoriteSlot(null));
        preferences.flush();
        for (int i = 0; i < selected.size(); i++) {
            var method = selected.get(i);
            var preference = preferences.findByExperimentIdAndMethodId(experimentId, method.getId())
                    .orElseGet(() -> ExperimentMethodPreference.builder().experiment(state.getExperiment()).method(method).build());
            preference.setFavoriteSlot(i + 1);
            preferences.save(preference);
        }
        state.setVersion(state.getVersion() + 1);
        return state(experimentId);
    }

    @Transactional
    public ExperimentExplorationResponse.MethodOption createMethod(Long experimentId, ExplorationActivityRequests.Method request) {
        var experiment = lock(experimentId);
        var target = request.threadId() == null ? unassigned(experiment) : thread(experimentId, request.threadId());
        if (request.sourceMethodId() != null) {
            var source = method(experimentId, request.sourceMethodId());
            version(source.getVersion(), request.expectedVersion());
            if (source.getContent().equals(request.content().strip())) bad("複製後的行動文字必須與來源不同");
        }
        return option(create(experiment, target, request.content()).getId());
    }

    @Transactional
    public ExperimentExplorationResponse.MethodOption updateMethod(Long experimentId, Long methodId, ExplorationActivityRequests.Method request) {
        lock(experimentId);
        var method = method(experimentId, methodId);
        version(method.getVersion(), request.expectedVersion());
        if (request.sourceMethodId() != null) bad("更新行動不可指定複製來源");
        if (request.threadId() != null && !request.threadId().equals(method.getThread().getId())) bad("更新文字不可同時搬移行動");
        String content = changedMethodContent(method.getContent(), request.content());
        if (records.countByMethodId(methodId) > 0) conflict("這個行動已有發現，請先完成整理再更新");
        checkName(method.getThread().getId(), content, methodId);
        method.setContent(content);
        method.setVersion(method.getVersion() + 1);
        methods.flush();
        touch(method.getThread());
        return option(methodId);
    }

    @Transactional
    public ExperimentExplorationResponse.RecordResponse createRecord(Long experimentId, ExplorationActivityRequests.Discovery request) {
        var experiment = lock(experimentId);
        var method = request.methodId() == null ? null : method(experimentId, request.methodId());
        var target = request.threadId() == null
                ? method != null ? method.getThread() : unassigned(experiment)
                : thread(experimentId, request.threadId());
        sameThread(target, method);
        if (method != null) version(method.getVersion(), request.expectedMethodVersion());
        var record = records.save(ExperimentExplorationRecord.builder().experiment(experiment).thread(target).method(method)
                .discovery(required(request.discovery(), 10000)).occurredAt(request.occurredAt() == null ? now() : request.occurredAt()).build());
        bump(target, method);
        if (method != null) remember(experiment, method);
        return record(record);
    }

    @Transactional
    public ExperimentExplorationResponse.RecordResponse updateRecord(Long experimentId, Long recordId, ExplorationActivityRequests.Discovery request) {
        lock(experimentId);
        var record = ownedRecord(experimentId, recordId);
        version(record.getVersion(), request.expectedVersion());
        var method = request.methodId() == null ? null : method(experimentId, request.methodId());
        var target = request.threadId() == null ? record.getThread() : thread(experimentId, request.threadId());
        sameThread(target, method);
        if (method != null) version(method.getVersion(), request.expectedMethodVersion());
        bump(record.getThread(), record.getMethod());
        if (!record.getThread().getId().equals(target.getId()) || !Objects.equals(id(record.getMethod()), id(method))) bump(target, method);
        record.setThread(target);
        record.setMethod(method);
        record.setDiscovery(required(request.discovery(), 10000));
        record.setVersion(record.getVersion() + 1);
        return record(record);
    }

    @Transactional
    public ExperimentExplorationResponse.RecordResponse assign(Long experimentId, Long recordId, ExplorationActivityRequests.Assignment request) {
        lock(experimentId);
        var record = ownedRecord(experimentId, recordId);
        version(record.getVersion(), request.expectedVersion());
        var target = thread(experimentId, request.threadId());
        var method = request.methodId() == null ? null : method(experimentId, request.methodId());
        sameThread(target, method);
        if (method != null) version(method.getVersion(), request.expectedMethodVersion());
        bump(record.getThread(), record.getMethod());
        record.setThread(target);
        record.setMethod(method);
        record.setVersion(record.getVersion() + 1);
        bump(target, method);
        return record(record);
    }

    @Transactional
    public void deleteRecord(Long experimentId, Long recordId, long expectedVersion) {
        lock(experimentId);
        var record = ownedRecord(experimentId, recordId);
        version(record.getVersion(), expectedVersion);
        bump(record.getThread(), record.getMethod());
        records.delete(record);
    }

    @Transactional
    public void deleteMethod(Long experimentId, Long methodId, long expectedVersion, boolean deleteRecords) {
        lock(experimentId);
        var method = method(experimentId, methodId);
        version(method.getVersion(), expectedVersion);
        var target = method.getThread();
        var children = records.findByMethodIdOrderByOccurredAtAscIdAsc(methodId);
        if (deleteRecords) records.deleteAll(children);
        else children.forEach(r -> { r.setMethod(null); r.setVersion(r.getVersion() + 1); });
        if (!method.getHistoryText().isBlank()) {
            target.setUnassignedHistoryText(append(target.getUnassignedHistoryText(), "已移除行動：" + method.getContent() + "\n\n" + method.getHistoryText()));
            target.setUnassignedLastOrganizedAt(now());
        }
        var state = states.findByExperimentId(experimentId).orElse(null);
        if (state != null && state.getCurrentMethod() != null && state.getCurrentMethod().getId().equals(methodId)) {
            state.setCurrentMethod(null); state.setVersion(state.getVersion() + 1);
        }
        preferences.deleteByMethodId(methodId);
        records.flush();
        methods.delete(method);
        bump(target, null);
    }

    public ExperimentExplorationResponse.History history(Long experimentId, Long threadId, Long methodId) {
        var target = thread(experimentId, threadId);
        if (methodId == null) return new ExperimentExplorationResponse.History(target.getUnassignedHistoryText(), target.getUnassignedVersion(), target.getUnassignedLastOrganizedAt());
        var method = method(experimentId, methodId);
        sameThread(target, method);
        return new ExperimentExplorationResponse.History(method.getHistoryText(), method.getVersion(), method.getLastOrganizedAt());
    }

    public Experiment lock(Long experimentId) {
        return experiments.lockOwned(experimentId, SecurityUtil.getCurrentUserId()).orElseThrow(this::missing);
    }
    public Experiment owned(Long experimentId) {
        return experiments.findById(experimentId).filter(e -> e.getUser().getId().equals(SecurityUtil.getCurrentUserId())).orElseThrow(this::missing);
    }
    public ExplorationThread thread(Long experimentId, Long threadId) {
        owned(experimentId);
        return threads.findByIdAndExperimentId(threadId, experimentId).orElseThrow(this::missing);
    }
    public ExplorationThreadMethod method(Long experimentId, Long methodId) {
        owned(experimentId);
        return methods.findByIdAndExperimentId(methodId, experimentId).orElseThrow(this::missing);
    }
    public ExperimentExplorationRecord ownedRecord(Long experimentId, Long recordId) {
        return records.findByIdAndExperimentId(recordId, experimentId).orElseThrow(this::missing);
    }
    public ExperimentExploration settings(Experiment experiment) {
        return states.findByExperimentId(experiment.getId()).orElseGet(() -> states.save(ExperimentExploration.builder().experiment(experiment).build()));
    }
    public ExplorationThread unassigned(Experiment experiment) {
        // 呼叫端必須先持有實驗鎖；讀取畫面不會因查詢而建立預設探索線。
        return threads.findByExperimentIdAndSystemKey(experiment.getId(), "UNASSIGNED").orElseGet(() -> {
            var state = settings(experiment);
            state.setOrderVersion(state.getOrderVersion() + 1);
            return threads.saveAndFlush(ExplorationThread.builder().experiment(experiment).name("未設定").systemKey("UNASSIGNED")
                .sortOrder(threads.nextPosition(experiment.getId())).lastActivityAt(now()).build());
        });
    }
    public void checkName(Long threadId, String content, Long exceptId) {
        String value = required(content, 2000);
        methods.findByThreadIdAndContentKey(threadId, ExplorationThreadMethod.key(value)).ifPresent(existing -> {
            if (!existing.getId().equals(exceptId)) conflict(existing.getContent().equals(value)
                ? "這條探索線已有相同行動，請使用既有行動或修改文字" : "行動識別發生衝突，請稍後再試");
        });
    }
    public ExplorationThreadMethod create(Experiment experiment, ExplorationThread target, String content) {
        checkName(target.getId(), content, null);
        var method = methods.saveAndFlush(ExplorationThreadMethod.builder().experiment(experiment).thread(target)
                .content(required(content, 2000)).sortOrder(methods.nextPosition(target.getId())).build());
        touch(target);
        return method;
    }
    public void bump(ExplorationThread target, ExplorationThreadMethod method) {
        if (method == null) target.setUnassignedVersion(target.getUnassignedVersion() + 1);
        else method.setVersion(method.getVersion() + 1);
        touch(target);
    }
    private void remember(Experiment experiment, ExplorationThreadMethod method) {
        var preference = preferences.findByExperimentIdAndMethodId(experiment.getId(), method.getId())
            .orElseGet(() -> ExperimentMethodPreference.builder().experiment(experiment).method(method).build());
        preference.setLastUsedAt(now());
        preferences.save(preference);
    }
    public void touch(ExplorationThread target) {
        target.setVersion(target.getVersion() + 1);
        target.setLastActivityAt(now());
        target.getExperiment().setUpdatedAt(now());
    }
    public ExperimentExplorationResponse.MethodOption option(Long id) { return option(methods.option(id)); }
    public ExperimentExplorationResponse.MethodOption ownedOption(Long experimentId, Long methodId) {
        owned(experimentId);
        if (!methods.existsByIdAndExperimentId(methodId, experimentId)) throw missing();
        return option(methodId);
    }
    private ExperimentExplorationResponse.MethodOption option(ExplorationThreadMethodRepository.Option o) {
        return new ExperimentExplorationResponse.MethodOption(o.getId(), o.getThreadId(), o.getThreadName(), o.getContent(),
                o.getVersion(), o.getDiscoveryCount(), o.getHasHistory(), o.getLastOrganizedAt());
    }
    public ExperimentExplorationResponse.RecordResponse record(ExperimentExplorationRecord r) {
        return new ExperimentExplorationResponse.RecordResponse(r.getId(), r.getTryText(), id(r.getMethod()), r.getDiscovery(),
                r.getCreatedAt(), List.of(), r.getThread().getId(), r.getThread().getName(), id(r.getMethod()),
                r.getVersion(), r.getOccurredAt(), r.getUpdatedAt());
    }
    private ExperimentExplorationResponse.RecordResponse record(ExperimentExplorationRecordRepository.RecordRow r) {
        return new ExperimentExplorationResponse.RecordResponse(r.getId(), r.getTryText(), r.getMethodId(), r.getDiscovery(),
            r.getCreatedAt(), List.of(), r.getThreadId(), r.getThreadName(), r.getMethodId(), r.getVersion(), r.getOccurredAt(), r.getUpdatedAt());
    }
    public static String append(String original, String addition) { return original == null || original.isBlank() ? addition : original + "\n\n---\n\n" + addition; }
    public static void version(long actual, Long expected) { if (expected == null || actual != expected) conflict("資料已更新，請保留草稿並重新載入確認"); }
    public static void sameThread(ExplorationThread target, ExplorationThreadMethod method) {
        if (method != null && !method.getThread().getId().equals(target.getId())) bad("行動不屬於所選探索線");
    }
    public static String required(String value, int limit) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > limit) bad("內容不可空白或超過字數限制");
        return value.strip();
    }
    public static String changedMethodContent(String original, String value) {
        String content = required(value, 2000);
        if (original.strip().equals(content)) bad("更新後的行動內容必須與原內容不同；只整理紀錄請使用整理功能");
        return content;
    }
    private Long id(ExplorationThreadMethod method) { return method == null ? null : method.getId(); }
    private PageRequest paging(int page, int size) { return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size))); }
    public ZonedDateTime now() { return ZonedDateTime.now(clock); }
    public ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索資料"); }
    public static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
