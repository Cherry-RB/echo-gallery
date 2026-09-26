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
import com.echogallery.experiment.ExperimentCard;
import com.echogallery.experiment.ExperimentCardRepository;
import com.echogallery.experiment.ExperimentRepository;
import com.echogallery.util.SecurityUtil;
import com.echogallery.work.WorkCardRepository;
import com.echogallery.work.WorkProgressUpdate;
import com.echogallery.work.WorkProgressUpdateRepository;
import com.echogallery.work.WorkStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OverviewService {

    private static final List<Integer> ALLOWED_PERIOD_DAYS = List.of(7, 30, 90);
    private static final int NEXT_STEP_LIMIT = 3;
    private static final int DERIVED_CARD_LIMIT = 12;

    private final CardRepository cardRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCardRepository experimentCardRepository;
    private final CardRelationRepository cardRelationRepository;
    private final WorkCardRepository workCardRepository;
    private final WorkProgressUpdateRepository workProgressUpdateRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OverviewResponse getOverview(int periodDays) {
        validatePeriodDays(periodDays);
        Long userId = SecurityUtil.getCurrentUserId();
        ZonedDateTime periodEndAt = ZonedDateTime.now(clock);
        ZonedDateTime periodStartAt = periodEndAt.minusDays(periodDays);

        List<WorkProgressUpdate> latestUpdates = workProgressUpdateRepository.findLatestByUserId(userId);
        List<WorkProgressUpdate> nextStepUpdates = latestUpdates.stream()
                .filter(update -> update.getWork().getStatus() != WorkStatus.DONE)
                .filter(update -> hasText(update.getNextStep()))
                .sorted(Comparator.comparing(WorkProgressUpdate::getCreatedAt).reversed())
                .toList();

        int recurringCardCount = safeInt(cardRepository.countRecurringByUserId(userId));
        int pausedCardCount = safeInt(cardRepository.countPausedByUserId(userId));
        int needsProcessingCardCount = safeInt(cardRepository.countNeedsProcessingByUserId(userId));
        int highSnoozeCardCount = safeInt(cardRepository.countActiveByUserIdAndSnoozeCountGreaterThanEqual(userId, 3));

        OverviewResponse.OverviewCurrentResponse current = new OverviewResponse.OverviewCurrentResponse(
                recurringCardCount,
                pausedCardCount,
                needsProcessingCardCount,
                safeInt(experimentRepository.countByUserIdAndIsArchivedFalse(userId)),
                nextStepUpdates.size(),
                nextStepUpdates.stream().limit(NEXT_STEP_LIMIT).map(this::toNextStep).toList(),
                attentionSignals(needsProcessingCardCount, highSnoozeCardCount));

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
        OverviewResponse.ClosureMetricResponse closure = new OverviewResponse.ClosureMetricResponse(
                safeInt(workProgressUpdateRepository.countWorksWithFollowUpAfterNextStep(
                        userId, periodStartAt, periodEndAt)));

        OverviewResponse.OverviewPeriodResponse period = new OverviewResponse.OverviewPeriodResponse(
                flow,
                generativity,
                closure,
                buildActivities(userId, periodStartAt, periodEndAt),
                findDerivedCards(userId, periodStartAt, periodEndAt),
                findRecentExperimentMaterials(userId, periodStartAt, periodEndAt));
        return new OverviewResponse(periodDays, periodStartAt, periodEndAt, current, period);
    }

    @Transactional(readOnly = true)
    public CardReturnOverviewResponse getCardReturnOverview() {
        Long userId = SecurityUtil.getCurrentUserId();
        CardReturnOverviewResponse.CardStateResponse state = new CardReturnOverviewResponse.CardStateResponse(
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

        ZonedDateTime startOfToday = ZonedDateTime.now(clock).toLocalDate().atStartOfDay(clock.getZone());
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
            ZonedDateTime periodEndAt) {
        return List.of(
                activity("created", cardRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("offered", cardRepository.countByUserIdAndLastOfferedAtGreaterThanEqualAndLastOfferedAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("reviewed", cardRepository.countByUserIdAndLastOpenAtGreaterThanEqualAndLastOpenAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("experiment-material", experimentCardRepository.countStandaloneMaterialsByUserIdAndAddedAtBetween(
                        userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt)),
                activity("work-linked", workCardRepository.countByWorkUserIdAndLinkedAtGreaterThanEqualAndLinkedAtLessThan(userId, periodStartAt, periodEndAt)),
                activity("derived", cardRelationRepository.countDistinctDerivedCardsByUserIdAndCreatedAtBetween(
                        userId, CardRelationType.DERIVED_FROM, periodStartAt, periodEndAt)),
                activity("work-update", workProgressUpdateRepository.countByWorkUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, periodStartAt, periodEndAt)));
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
        return experimentCardRepository.findRecentStandaloneMaterialsByUserId(
                        userId,
                        CardRelationType.DERIVED_FROM,
                        periodStartAt,
                        periodEndAt,
                        PageRequest.of(0, DERIVED_CARD_LIMIT))
                .stream()
                .map(this::toExperimentMaterial)
                .toList();
    }

    private OverviewResponse.ExperimentMaterialResponse toExperimentMaterial(ExperimentCard experimentCard) {
        Card card = experimentCard.getCard();
        return new OverviewResponse.ExperimentMaterialResponse(
                card.getId(),
                card.getTitle(),
                experimentCard.getAddedAt(),
                experimentCard.getExperiment().getId(),
                experimentCard.getExperiment().getTitle(),
                card.getTags().stream().map(tag -> tag.getName()).sorted().toList());
    }

    private OverviewResponse.NextStepResponse toNextStep(WorkProgressUpdate update) {
        return new OverviewResponse.NextStepResponse(
                update.getWork().getId(), update.getWork().getTitle(), update.getNextStep(), update.getCreatedAt());
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
