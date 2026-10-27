package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.echogallery.card.PageResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.util.SecurityUtil;
import com.echogallery.work.CreativeWork;
import com.echogallery.work.CreativeWorkRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExplorationThreadService {
    private final ExperimentRepository experiments;
    private final ExplorationActivityService activity;
    private final ExperimentExplorationRepository states;
    private final ExplorationThreadRepository threads;
    private final ExplorationThreadMethodRepository methods;
    private final ExperimentExplorationRecordRepository records;
    private final ExplorationThreadCardRepository threadCards;
    private final ExplorationThreadWorkRepository threadWorks;
    private final CardRepository cards;
    private final CreativeWorkRepository works;
    public record ThreadOption(Long id, String name, String systemKey, boolean available) {}
    @Transactional(readOnly = true)
    public PageResponse<ThreadOption> options(Long experimentId, String keyword, Long cardId, int page) {
        ownedExperiment(experimentId);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = threads.options(experimentId, search, cardId, PageRequest.of(Math.max(0, page), 20));
        return new PageResponse<>(result.getContent().stream().map(t -> new ThreadOption(t.getId(), t.getName(), t.getSystemKey(), t.getAvailable())).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<WorkExplorationThreadController.WorkThreadResponse> listByWork(Long workId) {
        works.findByIdAndUserId(workId, owner()).orElseThrow(this::missing);
        return threadWorks.findByWorkIdAndThreadExperimentUserIdOrderByCreatedAtDesc(workId, owner()).stream()
                .map(link -> new WorkExplorationThreadController.WorkThreadResponse(link.getThread().getId(),
                        link.getThread().getName(), link.getThread().getExperiment().getId(),
                        link.getThread().getExperiment().getTitle(), link.getThread().getStatus())).toList();
    }

    @Transactional
    public ExplorationThreadResponse createWithImports(Long experimentId, ExplorationThreadRequests.NewImportRequest request) {
        throw new ResponseStatusException(HttpStatus.GONE, "副本匯入已退場，請使用調整歸屬或整理");
    }

    @Transactional
    public ExplorationThreadResponse createWithMaterial(Long experimentId, ExplorationThreadRequests.NewMaterialRequest request) {
        ExplorationThreadResponse thread = create(experimentId,
                new ExplorationThreadRequests.ThreadRequest(request.name(), null, null));
        return addCard(experimentId, thread.id(), new ExplorationThreadRequests.CardRequest(
                request.cardId(), ExplorationThreadCardRole.MATERIAL, null));
    }

    @Transactional
    public ExplorationThreadResponse discover(Long experimentId, Long threadId, ExplorationThreadRequests.DiscoveryRequest request) {
        activity.createRecord(experimentId, new ExplorationActivityRequests.Discovery(threadId, request.methodId(), request.discovery(), null, null, request.expectedMethodVersion()));
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse addCards(Long experimentId, Long threadId, ExplorationThreadRequests.CardsRequest request) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        Set<Long> ids = new LinkedHashSet<>(request.cardIds());
        Map<Long, Card> available = new HashMap<>();
        cards.findAllById(ids).stream().filter(card -> card.getUser().getId().equals(owner()))
                .forEach(card -> available.put(card.getId(), card));
        if (available.size() != ids.size()) throw missing();
        List<ExplorationThreadCard> existing = threadCards.findByThreadIdOrderByRoleAscSortOrderAscIdAsc(threadId)
                .stream().filter(link -> link.getRole() == request.role()).toList();
        if (existing.stream().anyMatch(link -> ids.contains(link.getCard().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "部分卡片已在探索線中，請重新載入候選清單");
        }
        int position = existing.stream().mapToInt(ExplorationThreadCard::getSortOrder).max().orElse(-1) + 1;
        List<ExplorationThreadCard> additions = new ArrayList<>();
        for (Long cardId : ids) additions.add(ExplorationThreadCard.builder().thread(thread)
                .card(available.get(cardId)).role(request.role()).sortOrder(position++).build());
        threadCards.saveAll(additions);
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse updateCardNote(Long experimentId, Long threadId, Long linkId, ExplorationThreadRequests.NoteRequest request) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadCard link = threadCards.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing);
        link.setNote(optional(request.note()));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional(readOnly = true)
    public List<ExplorationThreadResponse> list(Long experimentId) {
        ownedExperiment(experimentId);
        return threads.listRows(experimentId, PageRequest.of(0, 50)).stream().map(this::metadata).toList();
    }

    /** 列表只取得固定數量的摘要與預覽，完整關聯在讀取單條詳情時才載入。 */
    @Transactional(readOnly = true)
    public PageResponse<ExplorationThreadSummaryResponse> listSummaries(Long experimentId, int requestedPage, int requestedSize) {
        ownedExperiment(experimentId);
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 20));
        Page<ExplorationThreadRepository.ListRow> result = threads.listRows(experimentId, PageRequest.of(page, size));
        long orderVersion = states.findByExperimentId(experimentId).map(ExperimentExploration::getOrderVersion).orElse(0L);
        List<Long> ids = result.getContent().stream().map(ExplorationThreadRepository.ListRow::getId).toList();
        if (ids.isEmpty()) return new PageResponse<>(List.of(), page, size, result.getTotalElements(), result.getTotalPages());

        Map<Long, Long> methodCounts = countMap(methods.countByThreadIds(ids));
        Map<Long, String> firstMethods = new HashMap<>();
        for (Object[] row : methods.findFirstPreviewsByThreadIds(ids)) firstMethods.put(number(row[0]), text(row[1]));
        Map<Long, Long> recordCounts = countMap(records.countByThreadIds(ids));
        Map<Long, Long> workCounts = countMap(threadWorks.countByThreadIds(ids));
        Map<Long, Long> materialCounts = new HashMap<>();
        Map<Long, Long> resultCounts = new HashMap<>();
        for (Object[] row : threadCards.countByThreadIds(ids)) {
            Map<Long, Long> target = row[1] == ExplorationThreadCardRole.MATERIAL ? materialCounts : resultCounts;
            target.put(number(row[0]), number(row[2]));
        }
        Map<Long, List<ExplorationThreadSummaryResponse.MaterialPreview>> materials = new HashMap<>();
        for (Object[] row : threadCards.findRecentMaterialPreviewsByThreadIds(ids)) {
            materials.computeIfAbsent(number(row[0]), ignored -> new ArrayList<>()).add(
                    new ExplorationThreadSummaryResponse.MaterialPreview(number(row[1]), text(row[2]), text(row[3])));
        }
        Map<Long, List<ExplorationThreadSummaryResponse.RecordPreview>> previews = new HashMap<>();
        for (Object[] row : records.findRecentPreviewsByThreadIds(ids)) {
            previews.computeIfAbsent(number(row[0]), ignored -> new ArrayList<>()).add(
                    new ExplorationThreadSummaryResponse.RecordPreview(number(row[1]), text(row[2]), text(row[3]), time(row[4]), time(row[5])));
        }
        List<ExplorationThreadSummaryResponse> content = result.getContent().stream().map(thread ->
                new ExplorationThreadSummaryResponse(thread.getId(), thread.getExperimentId(), thread.getName(),
                        thread.getStatus(), thread.getConclusion(), thread.getSortOrder(), thread.getLastActivityAt(),
                        materialCounts.getOrDefault(thread.getId(), 0L), methodCounts.getOrDefault(thread.getId(), 0L),
                        recordCounts.getOrDefault(thread.getId(), 0L), resultCounts.getOrDefault(thread.getId(), 0L),
                        workCounts.getOrDefault(thread.getId(), 0L), firstMethods.get(thread.getId()), materials.getOrDefault(thread.getId(), List.of()),
                        previews.getOrDefault(thread.getId(), List.of()), thread.getVersion(), thread.getSystemKey(), orderVersion))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExplorationThreadResponse get(Long experimentId, Long threadId) {
        ownedExperiment(experimentId);
        return response(threads.detailRow(experimentId, threadId).orElseThrow(this::missing));
    }

    @Transactional(readOnly = true)
    public List<CardExplorationThreadResponse> listByCard(Long cardId) {
        cards.findById(cardId)
                .filter(card -> card.getUser().getId().equals(owner()))
                .orElseThrow(this::missing);
        return threadCards.findByCardIdAndThreadExperimentUserIdOrderByCreatedAtDesc(cardId, owner()).stream()
                .map(link -> new CardExplorationThreadResponse(
                        link.getThread().getId(),
                        link.getThread().getName(),
                        link.getThread().getExperiment().getId(),
                        link.getThread().getExperiment().getTitle(),
                        link.getThread().getExperiment().isArchived(),
                        link.getThread().getStatus(),
                        link.getRole(),
                        link.getNote()))
                .toList();
    }

    @Transactional
    public ExplorationThreadResponse create(Long experimentId, ExplorationThreadRequests.ThreadRequest request) {
        Experiment experiment = activity.lock(experimentId);
        ZonedDateTime now = ZonedDateTime.now();
        ExplorationThread thread = threads.save(ExplorationThread.builder()
                .experiment(experiment).name(request.name().trim())
                .status(request.status() == null ? ExplorationThreadStatus.NOT_STARTED : request.status())
                .conclusion(optional(request.conclusion())).sortOrder(threads.nextPosition(experimentId))
                .lastActivityAt(now).build());
        touch(experiment, thread);
        var ordering = activity.settings(experiment);
        ordering.setOrderVersion(ordering.getOrderVersion() + 1);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse update(Long experimentId, Long threadId, ExplorationThreadRequests.ThreadRequest request) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationActivityService.version(thread.getVersion(), request.expectedVersion());
        if (thread.getSystemKey() != null && !thread.getName().equals(request.name().strip()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系統未設定探索線不可改名");
        thread.setName(request.name().strip());
        thread.setStatus(request.status() == null ? thread.getStatus() : request.status());
        thread.setConclusion(optional(request.conclusion()));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public List<ExplorationThreadResponse> reorder(Long experimentId, ExplorationThreadRequests.OrderRequest request) {
        var state = activity.settings(activity.lock(experimentId));
        ExplorationActivityService.version(state.getOrderVersion(), request.expectedVersion());
        List<Long> all = threads.orderedIds(experimentId);
        Set<Long> requested = new LinkedHashSet<>(request.threadIds());
        Set<Long> existing = new HashSet<>(all);
        if (requested.size() != request.threadIds().size() || !requested.equals(existing)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排序內容必須包含這個實驗的全部探索線，且不可重複");
        }
        saveOrder(experimentId, request.threadIds(), state);
        return list(experimentId);
    }

    @Transactional
    public void move(Long experimentId, Long threadId, ExplorationThreadRequests.PositionRequest request) {
        var state = activity.settings(activity.lock(experimentId));
        ExplorationActivityService.version(state.getOrderVersion(), request.expectedVersion());
        List<Long> ordered = new ArrayList<>(threads.orderedIds(experimentId));
        if (!ordered.contains(threadId) || !ordered.contains(request.targetThreadId())) throw missing();
        if (threadId.equals(request.targetThreadId())) return;
        ordered.remove(threadId);
        ordered.add(ordered.indexOf(request.targetThreadId()) + (request.after() ? 1 : 0), threadId);
        saveOrder(experimentId, ordered, state);
    }

    private void saveOrder(Long experimentId, List<Long> ids, ExperimentExploration state) {
        threads.saveOrder(experimentId, ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        state.setOrderVersion(state.getOrderVersion() + 1);
    }

    @Transactional
    public void delete(Long experimentId, Long threadId) {
        var experiment = activity.lock(experimentId);
        var source = ownedThread(experimentId, threadId);
        if (source.getSystemKey() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "系統未設定探索線不可刪除");
        var children = methods.findByThreadIdOrderBySortOrderAscIdAsc(threadId);
        var discoveries = records.findByThreadIdOrderByOccurredAtAscIdAsc(threadId);
        var target = activity.unassigned(experiment);
        for (var method : children) activity.checkName(target.getId(), method.getContent(), method.getId());
        int nextPosition = methods.nextPosition(target.getId());
        for (var method : children) { method.setThread(target); method.setSortOrder(nextPosition++); method.setVersion(method.getVersion() + 1); }
        for (var record : discoveries) { record.setThread(target); record.setVersion(record.getVersion() + 1); }
        String preserved = source.getUnassignedHistoryText();
        if (source.getConclusion() != null && !source.getConclusion().isBlank())
            preserved = ExplorationActivityService.append(preserved, "原小結論：\n" + source.getConclusion());
        if (!preserved.isBlank()) {
            target.setUnassignedHistoryText(ExplorationActivityService.append(target.getUnassignedHistoryText(),
                "已移除探索線：" + source.getName() + "\n\n" + preserved));
            target.setUnassignedLastOrganizedAt(ZonedDateTime.now());
        }
        var state = states.findByExperimentId(experimentId).orElse(null);
        if (state != null && state.getCurrentThread() != null && state.getCurrentThread().getId().equals(threadId)) {
            state.setCurrentThread(target); state.setVersion(state.getVersion() + 1);
        }
        activity.bump(target, null);
        methods.flush(); records.flush(); states.flush();
        threads.delete(source);
        var ordering = activity.settings(experiment);
        ordering.setOrderVersion(ordering.getOrderVersion() + 1);
    }

    @Transactional
    public ExplorationThreadResponse addMethod(Long experimentId, Long threadId, ExplorationThreadRequests.MethodRequest request) {
        activity.createMethod(experimentId, new ExplorationActivityRequests.Method(threadId, request.content(), null, null));
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse updateMethod(Long experimentId, Long threadId, Long methodId, ExplorationThreadRequests.MethodRequest request) {
        activity.updateMethod(experimentId, methodId, new ExplorationActivityRequests.Method(threadId, request.content(), null, request.expectedVersion()));
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse deleteMethod(Long experimentId, Long threadId, Long methodId, boolean deleteRecords, long expectedVersion) {
        ownedThread(experimentId, threadId);
        if (!activity.method(experimentId, methodId).getThread().getId().equals(threadId)) throw missing();
        activity.deleteMethod(experimentId, methodId, expectedVersion, deleteRecords);
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse addRecord(Long experimentId, Long threadId, ExplorationThreadRequests.RecordRequest request) {
        activity.createRecord(experimentId, new ExplorationActivityRequests.Discovery(threadId, request.methodId(), request.discovery(), request.occurredAt(), null, request.expectedMethodVersion()));
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse importRecords(Long experimentId, Long threadId, ExplorationThreadRequests.ImportRequest request) {
        throw new ResponseStatusException(HttpStatus.GONE, "副本匯入已退場，請使用調整歸屬或整理");
    }

    @Transactional
    public ExplorationThreadResponse updateRecord(Long experimentId, Long threadId, Long recordId, ExplorationThreadRequests.RecordRequest request) {
        activity.updateRecord(experimentId, recordId, new ExplorationActivityRequests.Discovery(threadId, request.methodId(), request.discovery(), null, request.expectedVersion(), request.expectedMethodVersion()));
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse deleteRecord(Long experimentId, Long threadId, Long recordId, long expectedVersion) {
        if (!activity.ownedRecord(experimentId, recordId).getThread().getId().equals(threadId)) throw missing();
        activity.deleteRecord(experimentId, recordId, expectedVersion);
        return get(experimentId, threadId);
    }

    @Transactional
    public ExplorationThreadResponse addCard(Long experimentId, Long threadId, ExplorationThreadRequests.CardRequest request) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        Card card = cards.findById(request.cardId()).filter(value -> value.getUser().getId().equals(owner())).orElseThrow(this::missing);
        if (threadCards.existsByThreadIdAndCardIdAndRole(threadId, card.getId(), request.role())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這張卡片已經在探索線的相同位置");
        }
        threadCards.save(ExplorationThreadCard.builder().thread(thread).card(card).role(request.role())
                .note(optional(request.note())).sortOrder((int) threadCards.countByThreadIdAndRole(threadId, request.role())).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse removeCard(Long experimentId, Long threadId, Long linkId) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        threadCards.delete(threadCards.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse addWork(Long experimentId, Long threadId, ExplorationThreadRequests.WorkRequest request) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        CreativeWork work = works.findByIdAndUserId(request.workId(), owner()).orElseThrow(this::missing);
        if (threadWorks.existsByThreadIdAndWorkId(threadId, work.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "這件作品已經是探索線成果");
        threadWorks.save(ExplorationThreadWork.builder().thread(thread).work(work).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse removeWork(Long experimentId, Long threadId, Long linkId) {
        activity.lock(experimentId);
        ExplorationThread thread = ownedThread(experimentId, threadId);
        threadWorks.delete(threadWorks.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    private ExplorationThreadResponse response(ExplorationThread thread) {
        threads.flush();
        return response(threads.detailRow(thread.getExperiment().getId(), thread.getId()).orElseThrow(this::missing));
    }

    private ExplorationThreadResponse metadata(ExplorationThreadRepository.ListRow t) {
        return new ExplorationThreadResponse(t.getId(), t.getExperimentId(), t.getName(), t.getStatus(), t.getConclusion(),
            t.getSortOrder(), t.getLastActivityAt(), null, null, List.of(), List.of(), List.of(), List.of(),
            t.getVersion(), t.getUnassignedVersion(), t.getSystemKey(), 0, 0, t.getHasUnassignedHistory(), 0);
    }

    private ExplorationThreadResponse response(ExplorationThreadRepository.ListRow t) {
        var methodPage = activity.methods(t.getExperimentId(), t.getId(), "", 0, 20);
        var methodResponses = methodPage.content().stream().map(m ->
            new ExplorationThreadResponse.MethodResponse(m.id(), m.content(), 0, null, null, null,
                m.version(), m.discoveryCount(), m.hasHistory(), m.lastOrganizedAt())).toList();
        var recordPage = activity.records(t.getExperimentId(), t.getId(), null, false, 0, 20);
        var recordResponses = recordPage.content().stream().map(r ->
            new ExplorationThreadResponse.RecordResponse(r.id(), r.methodId(), r.discovery(), 0, null,
                r.occurredAt(), r.createdAt(), r.updatedAt(), r.version(), r.threadId(), r.tryText())).toList();
        var cardResponses = threadCards.findByThreadIdOrderByRoleAscSortOrderAscIdAsc(t.getId()).stream()
            .map(v -> new ExplorationThreadResponse.CardResponse(v.getId(), v.getCard().getId(), v.getCard().getTitle(),
                v.getCard().getCardNote(), v.getCard().getType(), v.getRole(), v.getNote(), v.getSortOrder(),
                v.getCard().isArchived(), v.getCreatedAt())).toList();
        var workResponses = threadWorks.findByThreadIdOrderByCreatedAtAscIdAsc(t.getId()).stream()
            .map(v -> new ExplorationThreadResponse.WorkResponse(v.getId(), v.getWork().getId(), v.getWork().getTitle(),
                ExplorationThreadResponse.CreativeWorkStage.valueOf(v.getWork().getStage().name()),
                v.getWork().isArchived(), v.getCreatedAt())).toList();
        return new ExplorationThreadResponse(t.getId(), t.getExperimentId(), t.getName(), t.getStatus(), t.getConclusion(),
            t.getSortOrder(), t.getLastActivityAt(), null, null, methodResponses, recordResponses, cardResponses, workResponses,
            t.getVersion(), t.getUnassignedVersion(), t.getSystemKey(), methodPage.totalElements(), recordPage.totalElements(), t.getHasUnassignedHistory(), records.countByThreadIdAndMethodIsNull(t.getId()));
    }

    private Experiment ownedExperiment(Long id) {
        return experiments.findById(id).filter(value -> value.getUser().getId().equals(owner())).orElseThrow(this::missing);
    }
    private ExplorationThread ownedThread(Long experimentId, Long id) {
        ownedExperiment(experimentId);
        return threads.findByIdAndExperimentId(id, experimentId).orElseThrow(this::missing);
    }
    private void touch(Experiment experiment, ExplorationThread thread) { activity.touch(thread); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    private Map<Long, Long> countMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put(number(row[0]), number(row[1]));
        return counts;
    }
    private Long number(Object value) { return ((Number) value).longValue(); }
    private String text(Object value) { return value == null ? null : value.toString(); }
    private ZonedDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof ZonedDateTime date) return date;
        if (value instanceof OffsetDateTime date) return date.toZonedDateTime();
        if (value instanceof java.time.Instant date) return date.atZone(java.time.ZoneId.systemDefault());
        if (value instanceof Timestamp date) return date.toInstant().atZone(java.time.ZoneId.systemDefault());
        if (value instanceof LocalDateTime date) return date.atZone(java.time.ZoneId.systemDefault());
        if (value instanceof java.util.Date date) return date.toInstant().atZone(java.time.ZoneId.systemDefault());
        throw new IllegalArgumentException("不支援的時間欄位型別：" + value.getClass().getName());
    }
    private Long owner() { return SecurityUtil.getCurrentUserId(); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到探索線資料"); }
}
