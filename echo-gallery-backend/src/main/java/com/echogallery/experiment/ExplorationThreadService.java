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
    private final ExperimentExplorationRecordRepository sourceRecords;
    private final ExplorationThreadRepository threads;
    private final ExplorationThreadMethodRepository methods;
    private final ExplorationThreadRecordRepository records;
    private final ExplorationThreadCardRepository threadCards;
    private final ExplorationThreadWorkRepository threadWorks;
    private final CardRepository cards;
    private final CreativeWorkRepository works;

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
        ExplorationThreadResponse thread = create(experimentId,
                new ExplorationThreadRequests.ThreadRequest(request.name(), null, null));
        return importRecords(experimentId, thread.id(), new ExplorationThreadRequests.ImportRequest(request.items()));
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
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadMethod method = request.methodId() == null ? null : ownedMethod(threadId, request.methodId());
        ExperimentExplorationRecord source = sourceRecords.save(ExperimentExplorationRecord.builder()
                .experiment(thread.getExperiment()).sourceThreadMethod(method)
                .tryText(optional(request.tryText())).discovery(request.discovery().trim()).build());
        // 原始紀錄與探索線發現同一交易保存，避免部分成功造成重複提交。
        records.save(ExplorationThreadRecord.builder().thread(thread).method(method)
                .sourceRecord(source).discovery(source.getDiscovery()).occurredAt(source.getCreatedAt())
                .sortOrder((int) records.countByThreadId(threadId)).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse addCards(Long experimentId, Long threadId, ExplorationThreadRequests.CardsRequest request) {
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
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadCard link = threadCards.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing);
        link.setNote(optional(request.note()));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional(readOnly = true)
    public List<ExplorationThreadResponse> list(Long experimentId) {
        ownedExperiment(experimentId);
        return threads.findByExperimentIdOrderBySortOrderAscIdAsc(experimentId).stream().map(this::response).toList();
    }

    /** 列表只取得固定數量的摘要與預覽，完整關聯在讀取單條詳情時才載入。 */
    @Transactional(readOnly = true)
    public PageResponse<ExplorationThreadSummaryResponse> listSummaries(Long experimentId, int requestedPage, int requestedSize) {
        ownedExperiment(experimentId);
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 20));
        Page<ExplorationThread> result = threads.findByExperimentIdOrderBySortOrderAscIdAsc(experimentId, PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(ExplorationThread::getId).toList();
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
                new ExplorationThreadSummaryResponse(thread.getId(), thread.getExperiment().getId(), thread.getName(),
                        thread.getStatus(), thread.getConclusion(), thread.getSortOrder(), thread.getLastActivityAt(),
                        materialCounts.getOrDefault(thread.getId(), 0L), methodCounts.getOrDefault(thread.getId(), 0L),
                        recordCounts.getOrDefault(thread.getId(), 0L), resultCounts.getOrDefault(thread.getId(), 0L),
                        workCounts.getOrDefault(thread.getId(), 0L), firstMethods.get(thread.getId()), materials.getOrDefault(thread.getId(), List.of()),
                        previews.getOrDefault(thread.getId(), List.of())))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExplorationThreadResponse get(Long experimentId, Long threadId) {
        return response(ownedThread(experimentId, threadId));
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
        Experiment experiment = ownedExperiment(experimentId);
        ZonedDateTime now = ZonedDateTime.now();
        ExplorationThread thread = threads.save(ExplorationThread.builder()
                .experiment(experiment).name(request.name().trim())
                .status(request.status() == null ? ExplorationThreadStatus.NOT_STARTED : request.status())
                .conclusion(optional(request.conclusion())).sortOrder((int) threads.countByExperimentId(experimentId))
                .lastActivityAt(now).build());
        touch(experiment, thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse update(Long experimentId, Long threadId, ExplorationThreadRequests.ThreadRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        thread.setName(request.name().trim());
        thread.setStatus(request.status() == null ? thread.getStatus() : request.status());
        thread.setConclusion(optional(request.conclusion()));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public List<ExplorationThreadResponse> reorder(Long experimentId, ExplorationThreadRequests.OrderRequest request) {
        ownedExperiment(experimentId);
        List<ExplorationThread> all = threads.findByExperimentIdOrderBySortOrderAscIdAsc(experimentId);
        Set<Long> requested = new LinkedHashSet<>(request.threadIds());
        Set<Long> existing = new HashSet<>(all.stream().map(ExplorationThread::getId).toList());
        if (requested.size() != request.threadIds().size() || !requested.equals(existing)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "排序內容必須包含這個實驗的全部探索線，且不可重複");
        }
        Map<Long, ExplorationThread> byId = new HashMap<>();
        all.forEach(thread -> byId.put(thread.getId(), thread));
        for (int index = 0; index < request.threadIds().size(); index++) byId.get(request.threadIds().get(index)).setSortOrder(index);
        return request.threadIds().stream().map(byId::get).map(this::response).toList();
    }

    @Transactional
    public void move(Long experimentId, Long threadId, ExplorationThreadRequests.PositionRequest request) {
        ownedExperiment(experimentId);
        ExplorationThread source = ownedThread(experimentId, threadId);
        ExplorationThread target = ownedThread(experimentId, request.targetThreadId());
        if (source.getId().equals(target.getId())) return;
        List<ExplorationThread> ordered = new ArrayList<>(threads.findByExperimentIdOrderBySortOrderAscIdAsc(experimentId));
        ordered.removeIf(item -> item.getId().equals(source.getId()));
        int targetIndex = java.util.stream.IntStream.range(0, ordered.size())
                .filter(index -> ordered.get(index).getId().equals(target.getId())).findFirst().orElseThrow(this::missing);
        ordered.add(targetIndex + (request.after() ? 1 : 0), source);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setSortOrder(index);
    }

    @Transactional
    public void delete(Long experimentId, Long threadId) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        threads.delete(thread);
        thread.getExperiment().setUpdatedAt(ZonedDateTime.now());
    }

    @Transactional
    public ExplorationThreadResponse addMethod(Long experimentId, Long threadId, ExplorationThreadRequests.MethodRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExperimentExplorationRecord source = ownedSource(experimentId, request.sourceRecordId());
        ensureMethodSourceAvailable(threadId, source, null);
        methods.save(ExplorationThreadMethod.builder().thread(thread).content(request.content().trim())
                .sortOrder((int) methods.countByThreadId(threadId)).sourceRecord(source).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse updateMethod(Long experimentId, Long threadId, Long methodId, ExplorationThreadRequests.MethodRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadMethod method = ownedMethod(threadId, methodId);
        ExperimentExplorationRecord source = ownedSource(experimentId, request.sourceRecordId());
        ensureMethodSourceAvailable(threadId, source, methodId);
        method.setContent(request.content().trim());
        method.setSourceRecord(source);
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse deleteMethod(Long experimentId, Long threadId, Long methodId, boolean deleteRecords) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadMethod method = ownedMethod(threadId, methodId);
        if (deleteRecords) records.deleteByMethodId(methodId); else records.detachMethod(methodId);
        methods.delete(method);
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse addRecord(Long experimentId, Long threadId, ExplorationThreadRequests.RecordRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadMethod method = request.methodId() == null ? null : ownedMethod(threadId, request.methodId());
        ExperimentExplorationRecord source = ownedSource(experimentId, request.sourceRecordId());
        ensureRecordSourceAvailable(threadId, source, null);
        records.save(ExplorationThreadRecord.builder().thread(thread).method(method).discovery(request.discovery().trim())
                .sourceRecord(source).occurredAt(request.occurredAt())
                .sortOrder((int) records.countByThreadId(threadId)).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse importRecords(Long experimentId, Long threadId, ExplorationThreadRequests.ImportRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        Set<Long> requestedIds = new HashSet<>();
        List<ImportPlan> plans = new ArrayList<>();

        for (ExplorationThreadRequests.ImportItem item : request.items()) {
            if (!requestedIds.add(item.recordId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一筆探索紀錄不可重複選取");
            }
            if (!item.includeMethod() && !item.includeDiscovery()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每筆探索紀錄至少要選擇試法或發現");
            }
            if (item.includeMethod() && item.targetMethodId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "建立新試法時不可同時指定既有試法");
            }

            ExperimentExplorationRecord source = ownedSource(experimentId, item.recordId());
            String methodContent = item.includeMethod() ? requiredText(item.methodContent(), "請填寫要加入的試法") : null;
            String discovery = item.includeDiscovery() ? requiredText(item.discovery(), "請填寫要加入的發現") : null;
            ExplorationThreadMethod targetMethod = item.targetMethodId() == null ? null : ownedMethod(threadId, item.targetMethodId());

            if (item.includeMethod() && methods.existsByThreadIdAndSourceRecordId(threadId, source.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "所選紀錄的試法已加入這條探索線");
            }
            if (item.includeDiscovery() && records.existsByThreadIdAndSourceRecordId(threadId, source.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "所選紀錄的發現已加入這條探索線");
            }
            plans.add(new ImportPlan(source, item.includeMethod(), methodContent, discovery, targetMethod));
        }

        int methodOrder = (int) methods.countByThreadId(threadId);
        int recordOrder = (int) records.countByThreadId(threadId);
        for (ImportPlan plan : plans) {
            ExplorationThreadMethod targetMethod = plan.targetMethod();
            if (plan.includeMethod()) {
                targetMethod = methods.save(ExplorationThreadMethod.builder()
                        .thread(thread).content(plan.methodContent()).sortOrder(methodOrder++)
                        .sourceRecord(plan.source()).build());
            }
            if (plan.discovery() != null) {
                records.save(ExplorationThreadRecord.builder()
                        .thread(thread).method(targetMethod).discovery(plan.discovery())
                        .sourceRecord(plan.source()).occurredAt(plan.source().getCreatedAt())
                        .sortOrder(recordOrder++).build());
            }
        }
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse updateRecord(Long experimentId, Long threadId, Long recordId, ExplorationThreadRequests.RecordRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        ExplorationThreadRecord record = records.findByIdAndThreadId(recordId, threadId).orElseThrow(this::missing);
        ExperimentExplorationRecord source = ownedSource(experimentId, request.sourceRecordId());
        ensureRecordSourceAvailable(threadId, source, recordId);
        record.setMethod(request.methodId() == null ? null : ownedMethod(threadId, request.methodId()));
        record.setDiscovery(request.discovery().trim());
        record.setSourceRecord(source);
        record.setOccurredAt(request.occurredAt());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse deleteRecord(Long experimentId, Long threadId, Long recordId) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        records.delete(records.findByIdAndThreadId(recordId, threadId).orElseThrow(this::missing));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse addCard(Long experimentId, Long threadId, ExplorationThreadRequests.CardRequest request) {
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
        ExplorationThread thread = ownedThread(experimentId, threadId);
        threadCards.delete(threadCards.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse addWork(Long experimentId, Long threadId, ExplorationThreadRequests.WorkRequest request) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        CreativeWork work = works.findByIdAndUserId(request.workId(), owner()).orElseThrow(this::missing);
        if (threadWorks.existsByThreadIdAndWorkId(threadId, work.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "這件作品已經是探索線成果");
        threadWorks.save(ExplorationThreadWork.builder().thread(thread).work(work).build());
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    @Transactional
    public ExplorationThreadResponse removeWork(Long experimentId, Long threadId, Long linkId) {
        ExplorationThread thread = ownedThread(experimentId, threadId);
        threadWorks.delete(threadWorks.findByIdAndThreadId(linkId, threadId).orElseThrow(this::missing));
        touch(thread.getExperiment(), thread);
        return response(thread);
    }

    private ExplorationThreadResponse response(ExplorationThread thread) {
        List<ExplorationThreadResponse.MethodResponse> methodResponses = methods.findByThreadIdOrderBySortOrderAscIdAsc(thread.getId()).stream()
                .map(value -> new ExplorationThreadResponse.MethodResponse(value.getId(), value.getContent(), value.getSortOrder(), id(value.getSourceRecord()), value.getCreatedAt(), value.getUpdatedAt())).toList();
        List<ExplorationThreadResponse.RecordResponse> recordResponses = records.findByThreadIdOrderBySortOrderAscIdAsc(thread.getId()).stream()
                .map(value -> new ExplorationThreadResponse.RecordResponse(value.getId(), id(value.getMethod()), value.getDiscovery(), value.getSortOrder(), id(value.getSourceRecord()), value.getOccurredAt(), value.getCreatedAt(), value.getUpdatedAt())).toList();
        List<ExplorationThreadResponse.CardResponse> cardResponses = threadCards.findByThreadIdOrderByRoleAscSortOrderAscIdAsc(thread.getId()).stream()
                .map(value -> new ExplorationThreadResponse.CardResponse(value.getId(), value.getCard().getId(), value.getCard().getTitle(), value.getCard().getCardNote(), value.getCard().getType(), value.getRole(), value.getNote(), value.getSortOrder(), value.getCard().isArchived(), value.getCreatedAt())).toList();
        List<ExplorationThreadResponse.WorkResponse> workResponses = threadWorks.findByThreadIdOrderByCreatedAtAscIdAsc(thread.getId()).stream()
                .map(value -> new ExplorationThreadResponse.WorkResponse(value.getId(), value.getWork().getId(), value.getWork().getTitle(), ExplorationThreadResponse.CreativeWorkStage.valueOf(value.getWork().getStage().name()), value.getWork().isArchived(), value.getCreatedAt())).toList();
        return new ExplorationThreadResponse(thread.getId(), thread.getExperiment().getId(), thread.getName(), thread.getStatus(), thread.getConclusion(), thread.getSortOrder(), thread.getLastActivityAt(), thread.getCreatedAt(), thread.getUpdatedAt(), methodResponses, recordResponses, cardResponses, workResponses);
    }

    private Experiment ownedExperiment(Long id) {
        return experiments.findById(id).filter(value -> value.getUser().getId().equals(owner())).orElseThrow(this::missing);
    }
    private ExplorationThread ownedThread(Long experimentId, Long id) {
        ownedExperiment(experimentId);
        return threads.findByIdAndExperimentId(id, experimentId).orElseThrow(this::missing);
    }
    private ExplorationThreadMethod ownedMethod(Long threadId, Long id) { return methods.findByIdAndThreadId(id, threadId).orElseThrow(this::missing); }
    private ExperimentExplorationRecord ownedSource(Long experimentId, Long id) {
        if (id == null) return null;
        return sourceRecords.findByIdAndExperimentId(id, experimentId).orElseThrow(this::missing);
    }
    private void ensureMethodSourceAvailable(Long threadId, ExperimentExplorationRecord source, Long currentId) {
        if (source == null) return;
        boolean exists = currentId == null
                ? methods.existsByThreadIdAndSourceRecordId(threadId, source.getId())
                : methods.existsByThreadIdAndSourceRecordIdAndIdNot(threadId, source.getId(), currentId);
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "所選紀錄的試法已加入這條探索線");
    }
    private void ensureRecordSourceAvailable(Long threadId, ExperimentExplorationRecord source, Long currentId) {
        if (source == null) return;
        boolean exists = currentId == null
                ? records.existsByThreadIdAndSourceRecordId(threadId, source.getId())
                : records.existsByThreadIdAndSourceRecordIdAndIdNot(threadId, source.getId(), currentId);
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "所選紀錄的發現已加入這條探索線");
    }
    private void touch(Experiment experiment, ExplorationThread thread) { ZonedDateTime now = ZonedDateTime.now(); thread.setLastActivityAt(now); experiment.setUpdatedAt(now); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String requiredText(String submitted, String message) {
        String value = optional(submitted);
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value;
    }
    private record ImportPlan(ExperimentExplorationRecord source, boolean includeMethod,
            String methodContent, String discovery, ExplorationThreadMethod targetMethod) {}
    private Long id(Object value) {
        if (value == null) return null;
        if (value instanceof ExperimentExplorationRecord source) return source.getId();
        if (value instanceof ExplorationThreadMethod method) return method.getId();
        throw new IllegalArgumentException("不支援的來源型別");
    }
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
