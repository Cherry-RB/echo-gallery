package com.echogallery.sidebar;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.card.CardRepository;
import com.echogallery.card.CardService;
import com.echogallery.tag.TagRepository;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SidebarService {

    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final CardService cardService;

    @Transactional(readOnly = true)
    public SidebarStatsResponse getSidebarStats() {

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        long todayEcho = cardRepository.countByUserIdAndIsArchivedFalseAndNextShowAtLessThan(userId, cardService.getStartOfTomorrowTaipei()); // nextShowAt <= today
        long total = cardRepository.countByUserIdAndIsArchivedFalse(userId);
        long highSnooze = cardRepository.countByUserIdAndIsArchivedFalseAndSnoozeCountGreaterThan(userId, 10);   // snoozeCount > 10

        return new SidebarStatsResponse(total, todayEcho, highSnooze);
    }

    @Transactional(readOnly = true)
    public List<TagRankingResponse> getTopTags() {

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        // 建立一個分頁請求：第 0 頁（第一頁），每頁取 limit 筆（例如 10 筆）
        Pageable topN = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("name").descending());

        // 傳入 Repository
        return tagRepository.findTopTagsByCardCount(userId, topN);
    }

}
