package com.echogallery.experiment;

import static com.echogallery.experiment.ExplorationActivityService.*;
import static com.echogallery.experiment.ExplorationOrganizationRequests.Mode.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.echogallery.card.*;
import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplorationOrganizationService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ExplorationActivityService activity;
    private final ExperimentExplorationRecordRepository records;
    private final ExplorationThreadMethodRepository methods;
    private final ExplorationThreadCardRepository links;
    private final CardRepository cards;
    private final CardService cardService;
    private final UserRepository users;
    private final ExplorationOperationReceiptRepository receipts;
    private final ExplorationPreviewTokens tokens;
    private final ObjectMapper mapper;

    public record RecordVersion(Long id, long version) {}
    public record GroupSnapshot(String key, Long threadId, Long methodId, long version, String methodContent,
        boolean includeMethod, boolean includeHistory, boolean allRecords, List<RecordVersion> records) {}
    public record Snapshot(Long ownerId, Long experimentId, ExplorationOrganizationRequests.Mode mode, long expiresAt,
        List<GroupSnapshot> groups, Long targetCardId, Long cardVersion) {}
    public record GroupPreview(String key, Long threadId, String threadName, Long methodId, String methodContent,
        int recordCount, String text, String historyText, ZonedDateTime lastOrganizedAt) {}
    public record PreviewResponse(String token, List<GroupPreview> groups, String cardContent, long expiresAt) {}
    public record Result(Long cardId, int clearedCount, List<Long> threadIds) {}

    public PreviewResponse preview(Long experimentId, ExplorationOrganizationRequests.Preview request) {
        activity.owned(experimentId);
        if (request.mode() == UPDATE_METHOD && (request.groups().size() != 1 || request.groups().getFirst().methodId() == null
                || !request.groups().getFirst().allRecords())) bad("更新行動必須整理該行動目前全部發現");
        if (request.mode() != CARD && request.targetCardId() != null) bad("歷史整理不可指定卡片");
        Long cardVersion = null;
        if (request.targetCardId() != null) cardVersion = ownedCard(request.targetCardId(), false).getContentVersion();
        Set<String> keys = new HashSet<>();
        List<GroupSnapshot> snapshots = new ArrayList<>();
        List<GroupPreview> previews = new ArrayList<>();
        for (var selection : request.groups()) {
            var thread = activity.thread(experimentId, selection.threadId());
            var method = selection.methodId() == null ? null : activity.method(experimentId, selection.methodId());
            sameThread(thread, method);
            String key = method == null ? "thread:" + thread.getId() : "method:" + method.getId();
            if (!keys.add(key)) bad("同一分組不可重複選取");
            if (selection.includeHistory() && request.mode() != CARD) bad("既有歷史只能整理成卡片，不可再次追加回自身");
            List<ExperimentExplorationRecord> selected;
            if (selection.allRecords()) {
                selected = method != null ? records.findByMethodIdOrderByOccurredAtAscIdAsc(method.getId())
                    : records.findByThreadIdOrderByOccurredAtAscIdAsc(thread.getId()).stream().filter(r -> r.getMethod() == null).toList();
            } else {
                var ids = selection.recordIds() == null ? List.<Long>of() : selection.recordIds();
                if (new HashSet<>(ids).size() != ids.size()) bad("探索紀錄不可重複");
                selected = records.findByExperimentIdAndIdIn(experimentId, ids);
                if (selected.size() != ids.size()) bad("部分紀錄不存在或不屬於目前實驗");
                selected = selected.stream().sorted(Comparator.comparing(ExperimentExplorationRecord::getOccurredAt).thenComparing(ExperimentExplorationRecord::getId)).toList();
            }
            for (var r : selected) {
                if (!r.getThread().getId().equals(thread.getId()) || !Objects.equals(methodId(r), method == null ? null : method.getId()))
                    bad("選取紀錄的探索線或行動已改變，請重新確認");
            }
            String history = selection.includeHistory() ? method == null ? thread.getUnassignedHistoryText() : method.getHistoryText() : "";
            if (selected.isEmpty() && !selection.includeMethod() && history.isBlank()) {
                if (selection.allRecords()) continue;
                bad("請至少選取一項可整理內容");
            }
            if (selection.includeMethod() && method == null) bad("未指定分組沒有可選取的行動");
            String text = selected.isEmpty() ? "發現／觀察：\n本次未選取活動發現。\n\n整理心得：\n"
                : "發現／觀察：\n" + selected.stream().map(r -> displayTime(r.getOccurredAt()) + "：\n" + r.getDiscovery())
                    .collect(Collectors.joining("\n\n")) + "\n\n整理心得：\n";
            long version = method == null ? thread.getUnassignedVersion() : method.getVersion();
            var snapshot = new GroupSnapshot(key, thread.getId(), method == null ? null : method.getId(), version,
                method == null ? "未指定行動" : method.getContent(), selection.includeMethod(), selection.includeHistory(),
                selection.allRecords(), selected.stream().map(r -> new RecordVersion(r.getId(), r.getVersion())).toList());
            snapshots.add(snapshot);
            previews.add(new GroupPreview(key, thread.getId(), thread.getName(), snapshot.methodId(), snapshot.methodContent(),
                selected.size(), text, history, method == null ? thread.getUnassignedLastOrganizedAt() : method.getLastOrganizedAt()));
        }
        if (snapshots.isEmpty()) bad("探索線目前沒有可整理內容");
        long expiresAt = activity.now().plusMinutes(30).toEpochSecond();
        var snapshot = new Snapshot(SecurityUtil.getCurrentUserId(), experimentId, request.mode(), expiresAt, snapshots, request.targetCardId(), cardVersion);
        String cardContent = previews.stream().map(p -> {
            var source = snapshots.stream().filter(g -> g.key().equals(p.key())).findFirst().orElseThrow();
            String active = source.records().isEmpty() && !source.includeMethod() ? "" :
                (source.includeMethod() ? "行動：" + p.methodContent() + "\n\n" : "") + p.text();
            return append(active, p.historyText().isBlank() ? "" : "歷次整理：\n" + p.historyText());
        }).collect(Collectors.joining("\n\n---\n\n"));
        return new PreviewResponse(tokens.encode(snapshot), previews, cardContent, expiresAt);
    }

    @Transactional
    public Result commit(Long experimentId, ExplorationOrganizationRequests.Commit request) {
        var snapshot = tokens.decode(request.token(), Snapshot.class);
        Long ownerId = SecurityUtil.getCurrentUserId();
        if (!snapshot.ownerId().equals(ownerId) || !snapshot.experimentId().equals(experimentId)) bad("整理預覽不屬於目前使用者或實驗");
        // 與 Demo 清理、作品生命週期採一致的 owner → experiment → card 鎖順序。
        var owner = users.findByIdForUpdate(ownerId).orElseThrow(activity::missing);
        String hash = ExplorationThreadMethod.key(mapper.writeValueAsString(request));
        var receipt = receipts.findByUserIdAndRequestId(ownerId, request.requestId()).orElse(null);
        if (receipt != null) {
            if (!receipt.getPayloadHash().equals(hash)) conflict("同一請求識別碼不可用於不同內容");
            return mapper.readValue(receipt.getResultJson(), Result.class);
        }
        if (snapshot.expiresAt() < activity.now().toEpochSecond()) conflict("預覽已到期，請保留草稿並重新預覽");
        var experiment = activity.lock(experimentId);
        if (request.expectedFocusVersion() != null) {
            if (snapshot.mode() != UPDATE_METHOD) bad("只有更新行動可同時設定下一步");
            version(activity.settings(experiment).getVersion(), request.expectedFocusVersion());
        }
        if (request.focusFavoriteMethodIds() != null && request.expectedFocusVersion() == null) bad("常用行動需與下一步設定一併提交");
        Map<String, String> drafts = new HashMap<>();
        for (var draft : request.drafts()) if (drafts.put(draft.key(), required(draft.text(), 500000)) != null) bad("整理草稿分組重複");
        if (!drafts.keySet().equals(snapshot.groups().stream().map(GroupSnapshot::key).collect(Collectors.toSet()))) bad("整理草稿與預覽範圍不一致");
        if (snapshot.mode() != UPDATE_METHOD && request.newMethodContent() != null) bad("此整理模式不可更新行動");
        if (snapshot.mode() != CARD && (request.newCard() != null || request.cardContent() != null)) bad("此整理模式不可建立或追加卡片");
        var selectedByGroup = new LinkedHashMap<GroupSnapshot, List<ExperimentExplorationRecord>>();
        for (var group : snapshot.groups()) {
            var thread = activity.thread(experimentId, group.threadId());
            var method = group.methodId() == null ? null : activity.method(experimentId, group.methodId());
            if (method != null && !method.getThread().getId().equals(thread.getId())) conflict("行動歸屬已變更，請重新預覽");
            version(method == null ? thread.getUnassignedVersion() : method.getVersion(), group.version());
            var ids = group.records().stream().map(RecordVersion::id).toList();
            var selected = records.findByExperimentIdAndIdIn(experimentId, ids);
            if (selected.size() != ids.size()) conflict("部分紀錄已移除，請重新預覽");
            Map<Long, Long> versions = group.records().stream().collect(Collectors.toMap(RecordVersion::id, RecordVersion::version));
            for (var record : selected) {
                version(record.getVersion(), versions.get(record.getId()));
                if (!record.getThread().getId().equals(group.threadId()) || !Objects.equals(methodId(record), group.methodId())) conflict("紀錄歸屬已變更，請重新預覽");
            }
            if (group.allRecords()) {
                var all = method != null ? records.findByMethodIdOrderByOccurredAtAscIdAsc(method.getId())
                    : records.findByThreadIdOrderByOccurredAtAscIdAsc(thread.getId()).stream().filter(r -> r.getMethod() == null).toList();
                if (!new HashSet<>(all.stream().map(ExperimentExplorationRecord::getId).toList()).equals(new HashSet<>(ids)))
                    conflict("發現集合已改變，請重新預覽");
            }
            selectedByGroup.put(group, selected);
        }
        if (snapshot.mode() == UPDATE_METHOD) {
            var group = snapshot.groups().getFirst();
            if (!group.allRecords() || group.methodId() == null) bad("更新行動必須整理全部發現");
            changedMethodContent(group.methodContent(), request.newMethodContent());
            activity.checkName(group.threadId(), request.newMethodContent(), group.methodId());
        }

        Card card = null;
        if (snapshot.mode() == CARD) {
            if (snapshot.targetCardId() == null) {
                if (request.newCard() == null) bad("請填寫新卡片");
                if (request.newCard().getContent() == null || request.newCard().getContent().isBlank()) bad("整理卡片內容不可空白");
                card = cardService.createCardEntity(request.newCard());
            } else {
                if (request.newCard() != null) bad("追加既有卡片不可同時建立新卡片");
                card = ownedCard(snapshot.targetCardId(), true);
                version(card.getContentVersion(), snapshot.cardVersion());
                card.setContent(append(card.getContent(), required(request.cardContent(), 500000)));
                card.setContentVersion(card.getContentVersion() + 1);
            }
            for (Long threadId : snapshot.groups().stream().map(GroupSnapshot::threadId).distinct().toList()) {
                if (!links.existsByThreadIdAndCardIdAndRole(threadId, card.getId(), ExplorationThreadCardRole.RESULT))
                    links.save(ExplorationThreadCard.builder().thread(activity.thread(experimentId, threadId)).card(card)
                        .role(ExplorationThreadCardRole.RESULT).sortOrder((int) links.countByThreadIdAndRole(threadId, ExplorationThreadCardRole.RESULT)).build());
            }
        }

        int cleared = 0;
        boolean clear = request.clearRecords() || snapshot.mode() == UPDATE_METHOD;
        for (var entry : selectedByGroup.entrySet()) {
            var group = entry.getKey();
            var selected = entry.getValue();
            var thread = activity.thread(experimentId, group.threadId());
            var method = group.methodId() == null ? null : activity.method(experimentId, group.methodId());
            boolean saveHistory = snapshot.mode() != CARD || clear && !selected.isEmpty();
            if (saveHistory) {
                String period = selected.isEmpty() ? "無活動發現" : displayTime(selected.stream().map(ExperimentExplorationRecord::getOccurredAt).min(Comparator.naturalOrder()).orElseThrow())
                    + " ～ " + displayTime(selected.stream().map(ExperimentExplorationRecord::getOccurredAt).max(Comparator.naturalOrder()).orElseThrow());
                String block = "當時行動：" + group.methodContent() + "\n紀錄期間：" + period
                    + "\n整理時間：" + displayTime(activity.now()) + "\n\n" + drafts.get(group.key());
                if (method == null) {
                    thread.setUnassignedHistoryText(append(thread.getUnassignedHistoryText(), block));
                    thread.setUnassignedLastOrganizedAt(activity.now());
                } else {
                    method.setHistoryText(append(method.getHistoryText(), block));
                    method.setLastOrganizedAt(activity.now());
                }
                activity.bump(thread, method);
            }
            if (clear) { records.deleteAll(selected); cleared += selected.size(); }
            if (snapshot.mode() == UPDATE_METHOD) method.setContent(required(request.newMethodContent(), 2000));
            activity.touch(thread);
        }
        records.flush(); methods.flush();
        if (request.expectedFocusVersion() != null) {
            var group = snapshot.groups().getFirst();
            activity.focus(experimentId, new ExplorationActivityRequests.Focus(group.threadId(), group.methodId(), null,
                request.expectedFocusVersion(), null, null, request.focusFavoriteMethodIds()));
        }
        var result = new Result(card == null ? null : card.getId(), cleared,
            snapshot.groups().stream().map(GroupSnapshot::threadId).distinct().toList());
        receipts.saveAndFlush(ExplorationOperationReceipt.builder().user(owner).requestId(request.requestId())
            .payloadHash(hash).resultJson(mapper.writeValueAsString(result)).expiresAt(activity.now().plusDays(1)).build());
        return result;
    }

    private String displayTime(ZonedDateTime value) {
        return value.withZoneSameInstant(activity.now().getZone()).format(DISPLAY_TIME);
    }
    private Long methodId(ExperimentExplorationRecord r) { return r.getMethod() == null ? null : r.getMethod().getId(); }
    private Card ownedCard(Long id, boolean lock) {
        return (lock ? cards.findByIdForUpdate(id) : cards.findById(id))
            .filter(c -> c.getUser().getId().equals(SecurityUtil.getCurrentUserId())).orElseThrow(activity::missing);
    }
}
