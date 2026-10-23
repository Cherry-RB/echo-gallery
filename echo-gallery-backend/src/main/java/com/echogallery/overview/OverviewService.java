package com.echogallery.overview;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.experiment.CardRelation;
import com.echogallery.experiment.CardRelationRepository;
import com.echogallery.experiment.CardRelationType;
import com.echogallery.experiment.Experiment;
import com.echogallery.experiment.ExperimentCard;
import com.echogallery.experiment.ExperimentCardRepository;
import com.echogallery.experiment.ExperimentExploration;
import com.echogallery.experiment.ExperimentExplorationRecordCount;
import com.echogallery.experiment.ExperimentExplorationRepository;
import com.echogallery.experiment.ExperimentExplorationRecordRepository;
import com.echogallery.experiment.ExperimentRepository;
import com.echogallery.experiment.ExplorationRecordCardRepository;
import com.echogallery.experiment.ExplorationThreadCardRepository;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OverviewService {

    private static final List<Integer> ALLOWED_PERIOD_DAYS = List.of(7, 30, 90);
    private static final int EXPERIMENT_TRY_LIMIT = 9;
    private static final int DERIVED_CARD_LIMIT = 12;

    private final CardRepository cardRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCardRepository experimentCardRepository;
    private final ExperimentExplorationRepository experimentExplorationRepository;
    private final ExperimentExplorationRecordRepository experimentExplorationRecordRepository;
    private final ExplorationRecordCardRepository explorationRecordCardRepository;
    private final CardRelationRepository cardRelationRepository;
    private final ExplorationThreadCardRepository explorationThreadCardRepository;
    private final com.echogallery.treasure.TreasureService treasureService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OverviewResponse.OverviewCurrentResponse getCurrentOverview() {
        return buildCurrentOverview(SecurityUtil.getCurrentUserId(), ZonedDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public OverviewRecentResponse getRecentOverview(int periodDays) {
        validatePeriodDays(periodDays);
        Long userId = SecurityUtil.getCurrentUserId();
        ZonedDateTime periodEndAt = ZonedDateTime.now(clock);
        ZonedDateTime periodStartAt = periodEndAt.minusDays(periodDays);
        return new OverviewRecentResponse(
                periodDays,
                periodStartAt,
                periodEndAt,
                buildPeriodOverview(userId, periodStartAt, periodEndAt));
    }

    private OverviewResponse.OverviewCurrentResponse buildCurrentOverview(Long userId, ZonedDateTime now) {

        int recurringCardCount = safeInt(cardRepository.countRecurringByUserId(userId));
        int pausedCardCount = safeInt(cardRepository.countPausedByUserId(userId));
        int needsProcessingCardCount = safeInt(cardRepository.countNeedsProcessingByUserId(userId));
        int highSnoozeCardCount = safeInt(cardRepository.countActiveByUserIdAndSnoozeCountGreaterThanEqual(userId, 3));
        List<Experiment> activeExperiments = experimentRepository
                .findByUserIdAndIsArchivedFalseOrderByUpdatedAtDescIdDesc(userId, PageRequest.of(0, EXPERIMENT_TRY_LIMIT));
        Map<Long, ExperimentExploration> explorationsByExperimentId = activeExperiments.isEmpty()
                ? Map.of()
                : experimentExplorationRepository.findByExperimentIdIn(
                        activeExperiments.stream().map(Experiment::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(exploration -> exploration.getExperiment().getId(), exploration -> exploration));
        Map<Long, Long> explorationRecordCounts = activeExperiments.isEmpty()
                ? Map.of()
                : experimentExplorationRecordRepository.countByExperimentIds(
                        activeExperiments.stream().map(Experiment::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                ExperimentExplorationRecordCount::getExperimentId,
                                ExperimentExplorationRecordCount::getRecordCount));
        List<OverviewResponse.ExperimentTryResponse> experimentTries = activeExperiments.stream()
                .map(experiment -> toExperimentTry(experiment,
                        explorationsByExperimentId.get(experiment.getId()),
                        explorationRecordCounts.getOrDefault(experiment.getId(), 0L)))
                .toList();

        OverviewResponse.OverviewCurrentResponse current = new OverviewResponse.OverviewCurrentResponse(
                safeInt(cardRepository.countTodayReturnPoolByUserId(
                        userId,
                        now.toLocalDate().atStartOfDay(clock.getZone()),
                        now.toLocalDate().plusDays(1).atStartOfDay(clock.getZone()))),
                recurringCardCount,
                pausedCardCount,
                needsProcessingCardCount,
                safeInt(experimentRepository.countByUserIdAndIsArchivedFalse(userId)),
                treasureService.activeCount(),
                experimentTries,
                treasureService.actions(),
                attentionSignals(needsProcessingCardCount, highSnoozeCardCount));

        return current;
    }

    private OverviewResponse.OverviewPeriodResponse buildPeriodOverview(
            Long userId,
            ZonedDateTime periodStartAt,
            ZonedDateTime periodEndAt) {
        int reviewedCardCount = safeInt(cardRepository.countByUserIdAndLastOpenAtGreaterThanEqualAndLastOpenAtLessThan(
                userId, periodStartAt, periodEndAt));
        OverviewResponse.FlowMetricResponse flow = new OverviewResponse.FlowMetricResponse(
                safeInt(cardRepository.countReengagedByUserId(userId, periodStartAt, periodEndAt)),
                reviewedCardCount);
        OverviewResponse.GenerativityMetricResponse generativity = new OverviewResponse.GenerativityMetricResponse(
                safeInt(cardRelationRepository.countDistinctDerivedCardsByUserIdAndCreatedAtBetween(
                        userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt)),
                safeInt(cardRelationRepository.countDistinctSourceCardsByUserIdAndCreatedAtBetween(
                        userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt)));
        var growth = treasureService.growth(periodStartAt, periodEndAt);
        var treasureActivity = treasureService.recentActivity(periodStartAt, periodEndAt);
        return new OverviewResponse.OverviewPeriodResponse(
                flow,
                generativity,
                growth,
                treasureActivity.yields(),
                treasureActivity.progress(),
                buildActivities(userId, periodStartAt, periodEndAt, growth),
                findDerivedCards(userId, periodStartAt, periodEndAt),
                findRecentExperimentMaterials(userId, periodStartAt, periodEndAt));
    }

    @Transactional(readOnly = true)
    public CardReturnOverviewResponse getCardReturnOverview() {
        Long userId = SecurityUtil.getCurrentUserId();
        ZonedDateTime startOfToday = ZonedDateTime.now(clock).toLocalDate().atStartOfDay(clock.getZone());
        ZonedDateTime startOfTomorrow = startOfToday.plusDays(1);
        CardReturnOverviewResponse.CardStateResponse state = new CardReturnOverviewResponse.CardStateResponse(
                safeInt(cardRepository.countTodayReturnPoolByUserId(userId, startOfToday, startOfTomorrow)),
                safeInt(cardRepository.countRecurringByUserId(userId)),
                safeInt(cardRepository.countPausedByUserId(userId)),
                safeInt(cardRepository.countByUserIdAndIsArchivedTrue(userId)),
                safeInt(cardRepository.countNeedsProcessingByUserId(userId)),
                safeInt(cardRepository.countNeverReviewedActiveByUserId(userId)),
                safeInt(cardRepository.countScheduleIssueByUserId(userId)));

        List<CardReturnOverviewResponse.CadenceBandResponse> cadenceBands = List.of(
                cadenceBand("1-7", userId, 1, 7),
                cadenceBand("8-14", userId, 8, 14),
                cadenceBand("15-30", userId, 15, 30),
                cadenceBand("31-60", userId, 31, 60),
                cadenceBand("61-90", userId, 61, 90),
                cadenceBand("91-180", userId, 91, 180),
                cadenceBand("181+", userId, 181, 365));

        ZonedDateTime forecastEndAt = startOfToday.plusDays(7);
        Map<LocalDate, Integer> forecastCounts = cardRepository.findForecastCardsByUserId(
                        userId, startOfToday, forecastEndAt)
                .stream()
                .collect(Collectors.groupingBy(card -> card.getNextShowAt().toLocalDate(),
                        LinkedHashMap::new, Collectors.summingInt(card -> 1)));
        List<CardReturnOverviewResponse.ForecastDayResponse> forecastDays = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = startOfToday.toLocalDate().plusDays(offset);
            forecastDays.add(new CardReturnOverviewResponse.ForecastDayResponse(
                    date, forecastCounts.getOrDefault(date, 0)));
        }

        return new CardReturnOverviewResponse(
                state,
                cadenceBands,
                List.copyOf(forecastDays),
                List.of(
                        snoozeBand("3-5", userId, 3, 5),
                        snoozeBand("6-10", userId, 6, 10),
                        snoozeBand("11+", userId, 11, Integer.MAX_VALUE)));
    }

    private List<OverviewResponse.ActivityResponse> buildActivities(
            Long userId,
            ZonedDateTime periodStartAt,
            ZonedDateTime periodEndAt,
            com.echogallery.treasure.TreasureResponses.Growth growth) {
        return List.of(
                activity("created", cardRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("offered", cardRepository.countByUserIdAndLastOfferedAtGreaterThanEqualAndLastOfferedAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("reviewed", cardRepository.countByUserIdAndLastOpenAtGreaterThanEqualAndLastOpenAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("experiment-material", recentExperimentMaterials(userId, periodStartAt, periodEndAt).size()),
                activity("derived", cardRelationRepository.countDistinctDerivedCardsByUserIdAndCreatedAtBetween(
                        userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt)),
                activity("exploration-record", experimentExplorationRecordRepository
                        .countByExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                userId, periodStartAt, periodEndAt)),
                activity("treasure-collected", growth.collectedCount()),
                activity("treasure-promoted", growth.promotedTreasureCount()),
                activity("treasure-yield", growth.yieldRecordCount()));
    }

    private List<OverviewResponse.DerivedCardResponse> findDerivedCards(
            Long userId,
            ZonedDateTime periodStartAt,
            ZonedDateTime periodEndAt) {
        List<CardRelation> relations = cardRelationRepository.findRecentDerivedRelationsByUserId(
                userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt);
        Map<Long, List<CardRelation>> relationsByDerivedCard = relations.stream()
                .collect(Collectors.groupingBy(relation -> relation.getDerivedCard().getId(), LinkedHashMap::new, Collectors.toList()));

        return relationsByDerivedCard.values().stream()
                .map(this::toDerivedCard)
                .sorted(Comparator.comparing(OverviewResponse.DerivedCardResponse::createdAt).reversed())
                .limit(DERIVED_CARD_LIMIT)
                .toList();
    }

    private OverviewResponse.DerivedCardResponse toDerivedCard(List<CardRelation> relations) {
        CardRelation first = relations.getFirst();
        Card derivedCard = first.getDerivedCard();
        List<OverviewResponse.LineageCardResponse> sourceCards = relations.stream()
                .map(CardRelation::getSourceCard)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Card::getId, card -> card, (left, right) -> left, LinkedHashMap::new))
                .values().stream()
                .map(card -> new OverviewResponse.LineageCardResponse(card.getId(), card.getTitle()))
                .toList();
        return new OverviewResponse.DerivedCardResponse(
                derivedCard.getId(),
                derivedCard.getTitle(),
                relations.stream().map(CardRelation::getCreatedAt).max(Comparator.naturalOrder()).orElse(first.getCreatedAt()),
                first.getExperiment().getId(),
                first.getExperiment().getTitle(),
                sourceCards,
                derivedCard.getTags().stream().map(tag -> tag.getName()).sorted().toList());
    }

    private List<OverviewResponse.ExperimentMaterialResponse> findRecentExperimentMaterials(
            Long userId,
            ZonedDateTime periodStartAt,
            ZonedDateTime periodEndAt) {
        List<ExperimentMaterial> materials = recentExperimentMaterials(userId, periodStartAt, periodEndAt);
        Set<ExperimentCardKey> explorationCardKeys = materials.isEmpty()
                ? Set.of()
                : explorationRecordCardRepository.findExplorationExperimentCardKeys(
                                userId, materials.stream().map(material -> material.card().getId()).toList())
                        .stream()
                        .map(key -> new ExperimentCardKey(key.getExperimentId(), key.getCardId()))
                        .collect(Collectors.toSet());
        return materials.stream().limit(DERIVED_CARD_LIMIT)
                .map(material -> toExperimentMaterial(material, explorationCardKeys))
                .toList();
    }

    private List<ExperimentMaterial> recentExperimentMaterials(Long userId, ZonedDateTime periodStartAt,
            ZonedDateTime periodEndAt) {
        List<ExperimentMaterial> materials = new ArrayList<>();
        experimentCardRepository.findAllStandaloneMaterialsByUserId(
                        userId,
                        CardRelationType.DERIVED_FROM,
                        periodStartAt,
                        periodEndAt)
                .forEach(link -> materials.add(new ExperimentMaterial(link.getExperiment(), link.getCard(), link.getAddedAt())));
        explorationThreadCardRepository.findRecentMaterials(userId, periodStartAt, periodEndAt)
                .forEach(link -> materials.add(new ExperimentMaterial(link.getThread().getExperiment(), link.getCard(), link.getCreatedAt())));
        Map<ExperimentCardKey, ExperimentMaterial> unique = new LinkedHashMap<>();
        materials.stream().sorted(Comparator.comparing(ExperimentMaterial::addedAt).reversed())
                .forEach(material -> unique.putIfAbsent(
                        new ExperimentCardKey(material.experiment().getId(), material.card().getId()), material));
        return unique.values().stream().toList();
    }

    private OverviewResponse.ExperimentMaterialResponse toExperimentMaterial(
            ExperimentMaterial material,
            Set<ExperimentCardKey> explorationCardKeys) {
        Card card = material.card();
        ExperimentCardKey key = new ExperimentCardKey(material.experiment().getId(), card.getId());
        return new OverviewResponse.ExperimentMaterialResponse(
                card.getId(),
                card.getTitle(),
                material.addedAt(),
                material.experiment().getId(),
                material.experiment().getTitle(),
                explorationCardKeys.contains(key) ? "EXPLORATION" : "MATERIAL",
                card.getTags().stream().map(tag -> tag.getName()).sorted().toList());
    }

    private record ExperimentMaterial(Experiment experiment, Card card, ZonedDateTime addedAt) {}
    private record ExperimentCardKey(Long experimentId, Long cardId) {
    }

    private OverviewResponse.ExperimentTryResponse toExperimentTry(
            Experiment experiment,
            ExperimentExploration exploration,
            long explorationRecordCount) {
        return new OverviewResponse.ExperimentTryResponse(
                experiment.getId(),
                experiment.getTitle(),
                exploration == null || exploration.getCurrentTry() == null ? "" : exploration.getCurrentTry(),
                explorationRecordCount);
    }

    private List<OverviewResponse.AttentionSignalResponse> attentionSignals(int needsProcessingCardCount, int highSnoozeCardCount) {
        List<OverviewResponse.AttentionSignalResponse> signals = new ArrayList<>();
        if (needsProcessingCardCount > 0) {
            signals.add(new OverviewResponse.AttentionSignalResponse("processing", needsProcessingCardCount));
        }
        if (highSnoozeCardCount > 0) {
            signals.add(new OverviewResponse.AttentionSignalResponse("snooze", highSnoozeCardCount));
        }
        return List.copyOf(signals);
    }

    private CardReturnOverviewResponse.CadenceBandResponse cadenceBand(String key, Long userId, int min, int max) {
        return new CardReturnOverviewResponse.CadenceBandResponse(
                key, safeInt(cardRepository.countRecurringByUserIdAndIntervalDaysBetween(userId, min, max)));
    }

    private CardReturnOverviewResponse.SnoozeBandResponse snoozeBand(String key, Long userId, int min, int max) {
        return new CardReturnOverviewResponse.SnoozeBandResponse(
                key, safeInt(cardRepository.countActiveByUserIdAndSnoozeCountBetween(userId, min, max)));
    }

    private OverviewResponse.ActivityResponse activity(String key, long count) {
        return new OverviewResponse.ActivityResponse(key, safeInt(count));
    }

    private void validatePeriodDays(int periodDays) {
        if (!ALLOWED_PERIOD_DAYS.contains(periodDays)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "期間只支援 7、30 或 90 天");
        }
    }

    private int safeInt(long value) {
        return Math.toIntExact(value);
    }

}
