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
import com.echogallery.card.CardRepository;
import com.echogallery.tag.Tag;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.AuthDto;
import com.echogallery.user.AuthService;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;
import com.echogallery.issue.Issue;
import com.echogallery.issue.IssueCard;
import com.echogallery.issue.IssueCardStatus;
import com.echogallery.issue.IssueCardRepository;
import com.echogallery.issue.IssueUpdateRepository;
import com.echogallery.issue.IssueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DemoSessionService {
    private final DemoProperties properties;
    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final IssueRepository issueRepository;
    private final IssueCardRepository issueCardRepository;
    private final IssueUpdateRepository issueUpdateRepository;
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
                    .isArchived(archived)
                    .snoozeCount(entry.snoozeCount() == null ? 0 : entry.snoozeCount()).build();
            entry.tags().forEach(name -> card.getTags().add(tags.get(name)));
            cards.add(cardRepository.save(card));
        }
        Issue issue = issueRepository.save(Issue.builder().user(user).title(catalog.issueTitle())
                .description("Demo 用的議題素材原型，可查看卡片如何回到目前脈絡。").build());
        for (int cardIndex = 0; cardIndex < catalog.cards().size(); cardIndex++) {
            String issueStatus = catalog.cards().get(cardIndex).issueStatus();
            if (issueStatus != null) {
                issueCardRepository.save(IssueCard.builder().issue(issue).card(cards.get(cardIndex))
                        .status(IssueCardStatus.valueOf(issueStatus)).build());
            }
        }
    }

    private void deleteSession(User user) {
        clearUserData(user);
        userRepository.delete(user);
    }

    private void clearUserData(User user) {
        for (Issue issue : issueRepository.findAllByUserId(user.getId())) {
            issueUpdateRepository.deleteByIssueId(issue.getId());
            issueCardRepository.deleteByIssueId(issue.getId());
        }
        issueRepository.deleteAll(issueRepository.findAllByUserId(user.getId()));
        cardRepository.deleteAll(cardRepository.findAllByUserId(user.getId()));
        cardRepository.flush();
        tagRepository.deleteAll(tagRepository.findAllByUserId(user.getId()));
        tagRepository.flush();
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo 體驗目前未開放");
    }

}
