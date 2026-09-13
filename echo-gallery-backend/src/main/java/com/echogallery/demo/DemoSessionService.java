package com.echogallery.demo;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.Card;
import com.echogallery.card.CardGrowthStatus;
import com.echogallery.card.CardRepository;
import com.echogallery.tag.Tag;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.AuthDto;
import com.echogallery.user.AuthService;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;
import com.echogallery.work.Work;
import com.echogallery.work.WorkCard;
import com.echogallery.work.WorkCardStatus;
import com.echogallery.work.WorkCardRepository;
import com.echogallery.work.WorkProgressUpdateRepository;
import com.echogallery.work.WorkRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DemoSessionService {
    private final DemoProperties properties;
    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final WorkRepository workRepository;
    private final WorkCardRepository workCardRepository;
    private final WorkProgressUpdateRepository workProgressUpdateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final Clock clock;
    private final DemoCatalog demoCatalog;

    @Transactional
    public AuthDto.AuthResponse create(String libraryKey) {
        ensureEnabled();
        DemoLibrary library = DemoLibrary.fromKey(libraryKey);
        ZonedDateTime now = ZonedDateTime.now(clock);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = userRepository.save(User.builder()
                .username("Demo " + library.getKey() + " " + suffix)
                .email("demo-" + suffix + "@echogallery.local")
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .showContentPreview(true)
                .demoSession(true)
                .demoLibrary(library.getKey())
                .demoExpiresAt(now.plusHours(properties.getSessionTtlHours()))
                .createdAt(now)
                .updatedAt(now)
                .build());
        seed(user, library, now);
        return authService.generateAuthResponse(user);
    }

    @Transactional
    public AuthDto.AuthResponse resetCurrent() {
        ensureEnabled();
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "尚未登入");
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Demo 工作階段不存在"));
        if (!user.isDemoSession() || user.getDemoExpiresAt() == null || !user.getDemoExpiresAt().isAfter(ZonedDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "此帳號不可重設 Demo 資料");
        }
        clearUserData(user);
        seed(user, DemoLibrary.fromKey(user.getDemoLibrary()), ZonedDateTime.now(clock));
        return authService.generateAuthResponse(user);
    }

    @Transactional
    public void clearExpiredSessions() {
        if (!properties.isEnabled() || !properties.getCleanup().isEnabled()) return;
        userRepository.findByDemoSessionTrueAndDemoExpiresAtBefore(ZonedDateTime.now(clock))
                .forEach(this::deleteSession);
    }

    private void seed(User user, DemoLibrary library, ZonedDateTime now) {
        DemoCatalog.Library catalog = demoCatalog.library(library);
        Map<String, Tag> tags = new HashMap<>();
        for (DemoCatalog.Entry entry : catalog.cards()) {
            entry.tags().forEach(name -> tags.computeIfAbsent(name,
                    key -> tagRepository.save(Tag.builder().user(user).name(key).build())));
        }
        java.util.List<Card> cards = new java.util.ArrayList<>();
        for (DemoCatalog.Entry entry : catalog.cards()) {
            boolean archived = Boolean.TRUE.equals(entry.archived());
            Card card = Card.builder()
                    .user(user).type(entry.type()).title(entry.title())
                    .summary(entry.summary()).reason(entry.reason()).content(entry.content())
                    .url(entry.url())
                    .intervalDays(entry.intervalDays())
                    .nextShowAt(entry.dayOffset() == null ? null : now.toLocalDate().plusDays(entry.dayOffset()).atStartOfDay(clock.getZone()))
                    .isArchived(archived).growthStatus(entry.growthStatus() == null ? CardGrowthStatus.UNMARKED : CardGrowthStatus.valueOf(entry.growthStatus()))
                    .snoozeCount(entry.snoozeCount() == null ? 0 : entry.snoozeCount()).build();
            entry.tags().forEach(name -> card.getTags().add(tags.get(name)));
            cards.add(cardRepository.save(card));
        }
        Work work = workRepository.save(Work.builder().user(user).title(catalog.workTitle())
                .description("Demo 用的議題素材原型，可查看卡片如何回到目前脈絡。").build());
        for (int cardIndex = 0; cardIndex < catalog.cards().size(); cardIndex++) {
            String workStatus = catalog.cards().get(cardIndex).workStatus();
            if (workStatus != null) {
                workCardRepository.save(WorkCard.builder().work(work).card(cards.get(cardIndex))
                        .status(WorkCardStatus.valueOf(workStatus)).build());
            }
        }
    }

    private void deleteSession(User user) {
        clearUserData(user);
        userRepository.delete(user);
    }

    private void clearUserData(User user) {
        for (Work work : workRepository.findAllByUserId(user.getId())) {
            workProgressUpdateRepository.deleteByWorkId(work.getId());
            workCardRepository.deleteByWorkId(work.getId());
        }
        workRepository.deleteAll(workRepository.findAllByUserId(user.getId()));
        cardRepository.deleteAll(cardRepository.findAllByUserId(user.getId()));
        cardRepository.flush();
        tagRepository.deleteAll(tagRepository.findAllByUserId(user.getId()));
        tagRepository.flush();
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo 體驗目前未開放");
    }

}
