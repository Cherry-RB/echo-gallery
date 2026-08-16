package com.echogallery.sidebar;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.card.CardRepository;
import com.echogallery.card.CardService;
import com.echogallery.card.CardGrowthStatus;
import com.echogallery.card.CardStatsProjection;
import com.echogallery.tag.TagRepository;
import com.echogallery.tag.TagDto;
import com.echogallery.util.SecurityUtil;
import com.echogallery.work.WorkRepository;
import com.echogallery.work.WorkStatsProjection;
import com.echogallery.work.WorkStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SidebarService {

    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final CardService cardService;
    private final WorkRepository workRepository;

    @Transactional(readOnly = true)
    public SidebarStatsResponse getSidebarStats() {

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        CardStatsProjection cardStats = cardRepository.findActiveStats(
                userId,
                cardService.getStartOfTomorrowTaipei(),
                10,
                CardGrowthStatus.SEED,
                CardGrowthStatus.GROWING,
                CardGrowthStatus.MATURE);
        WorkStatsProjection workStats = workRepository.findStats(
                userId,
                List.of(WorkStatus.IDEA, WorkStatus.DRAFT, WorkStatus.ACTIVE));

        return new SidebarStatsResponse(
                cardStats.getTotalCards(),
                workStats.getTotalWorks(),
                workStats.getUnfinishedWorks(),
                cardStats.getTodayEchoCards(),
                cardStats.getHighSnoozeCards(),
                cardStats.getSeedCards(),
                cardStats.getGrowingCards(),
                cardStats.getMatureCards());
    }

    @Transactional(readOnly = true)
    public List<TagDto> getTopTags() {

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        // 建立一個分頁請求：第 0 頁（第一頁），每頁取 limit 筆（例如 10 筆）
        Pageable topN = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("name").descending());

        // 傳入 Repository
        return tagRepository.findTopTagsWithCardCount(userId, topN);
    }

}
