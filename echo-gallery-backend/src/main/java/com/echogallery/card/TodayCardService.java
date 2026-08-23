package com.echogallery.card;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TodayCardService {

    private static final int BATCH_SIZE = 5;

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardService cardService;
    private final Clock clock;

    @Transactional
    public TodayBatchResponse prepare() {
        Long userId = currentUserId();
        lockUser(userId);
        TimeWindow window = todayWindow();
        return cardRepository.findLatestBatchOfferedAt(userId, window.start(), window.end())
                .map(batchTime -> existingBatch(userId, batchTime, window.end()))
                .orElseGet(() -> createBatch(userId, null, window));
    }

    @Transactional
    public TodayBatchResponse next(TodayNextRequest request) {
        Long userId = currentUserId();
        lockUser(userId);
        TimeWindow window = todayWindow();
        ZonedDateTime latestBatch = cardRepository
                .findLatestBatchOfferedAt(userId, window.start(), window.end())
                .orElseThrow(this::staleBatch);
        if (!latestBatch.toInstant().equals(request.currentBatchOfferedAt().toInstant())) {
            throw staleBatch();
        }
        return createBatch(userId, latestBatch, window);
    }

    private TodayBatchResponse existingBatch(Long userId, ZonedDateTime batchTime, ZonedDateTime tomorrow) {
        List<CardSummaryResponse> cards = cardRepository.findVisibleBatchCards(userId, batchTime, tomorrow)
                .stream()
                .map(cardService::convertToSummaryResponse)
                .toList();
        return new TodayBatchResponse(cards, batchTime);
    }

    private TodayBatchResponse createBatch(Long userId, ZonedDateTime previousBatch, TimeWindow window) {
        List<Card> candidates = cardRepository.findTodayCandidatesForUpdate(
                userId, window.start(), window.end(), PageRequest.of(0, BATCH_SIZE));
        if (candidates.isEmpty()) {
            return new TodayBatchResponse(List.of(), previousBatch);
        }

        ZonedDateTime batchTime = normalizedNow();
        if (previousBatch != null && !batchTime.toInstant().isAfter(previousBatch.toInstant())) {
            batchTime = ZonedDateTime.ofInstant(
                    previousBatch.toInstant().plus(1, ChronoUnit.MICROS), clock.getZone());
        }
        if (!batchTime.isBefore(window.end())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "日期已變更，請重新整理 Today");
        }

        ZonedDateTime offeredAt = batchTime;
        candidates.forEach(card -> card.setLastOfferedAt(offeredAt));
        List<CardSummaryResponse> cards = candidates.stream()
                .map(cardService::convertToSummaryResponse)
                .toList();
        return new TodayBatchResponse(cards, batchTime);
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "使用者不存在"));
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "尚未登入");
        }
        return userId;
    }

    private TimeWindow todayWindow() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime start = now.toLocalDate().atStartOfDay(clock.getZone());
        return new TimeWindow(start, start.plusDays(1));
    }

    private ZonedDateTime normalizedNow() {
        Instant instant = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return ZonedDateTime.ofInstant(instant, clock.getZone());
    }

    private ResponseStatusException staleBatch() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Today 批次已變更，請重新整理");
    }

    private record TimeWindow(ZonedDateTime start, ZonedDateTime end) {
    }
}
