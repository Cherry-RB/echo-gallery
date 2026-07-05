package com.echogallery.card;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.tag.Tag;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import com.echogallery.util.SecurityUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // 自動注入 Repository
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<CardSummaryResponse> getCardList(CardListRequest request){

        // 1. 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        // 2. 防禦性程式設計：處理分頁參數預設值
        // 前端通常習慣從第 1 頁開始算（pageNumber），但 Spring Data JPA 的 PageRequest 是從第 0 頁開始
        int page = (request.getPageNumber() != null && request.getPageNumber() > 0) ? request.getPageNumber() - 1 : 0;
        int size = (request.getPageSize() != null && request.getPageSize() > 0) ? request.getPageSize() : 10;

        // 3. 建立分頁與排序條件（依據建立時間降冪排序）
        Pageable pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());

        // 4. 根據看板類型，查出該使用者的分頁卡片資料
        BoardType boardType = BoardType.from(request.getBoardType());
        Page<Card> cardPage = switch (boardType) {
            case TODAY -> cardRepository.findTodayCards(userId, ZonedDateTime.now(), pageable);
            case ALL -> cardRepository.findByUserIdAndIsArchivedFalse(userId, pageable);
            case HOT -> cardRepository.findHotCards(userId, pageable);
            case RANDOM -> cardRepository.findRandomCards(userId, PageRequest.of(0, size));
            case ARCHIVED -> cardRepository.findByUserIdAndIsArchivedTrue(userId, pageable);
            case SNOOZED -> cardRepository.findSnoozedCards(userId, pageable);
        };

        // 5. 轉換為 Response DTO 列表回傳
        return cardPage.getContent().stream()
                .map(this::convertToSummaryResponse)
                .toList();
    }

    @Transactional
    public CardDetailResponse getCardDetailById(Long id){
        // 1. 從安全上下文抓取目前發出請求的用戶 ID（對應你前端攔截器帶入的 Token 身份）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            // 故意拋出 403 Forbidden！這會直接觸發你前端 Axios 攔截器的 case 403 -> "權限不足，拒絕存取"
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權檢視此卡片");
        }

        // 4. 通過檢查，封裝回傳
        return convertToDetailResponse(card);
    }

    @Transactional
    public CardDetailResponse updateCard(Long cardId, CardRequest request) {

        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            // 故意拋出 403 Forbidden！這會直接觸發你前端 Axios 攔截器的 case 403 -> "權限不足，拒絕存取"
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權修改此卡片");
        }

        // 4. 處理標籤安全性：走「查無此標籤才建立」的邏輯
        Set<Tag> associatedTags = associateTags(card.getUser(), request.getTags());

        // 5. 將 DTO 轉換為 Entity，並綁定目前登入的使用者
        card.setType(request.getType());
        card.setTitle(request.getTitle());
        card.setUrl(request.getUrl());
        card.setCoverImageUrl(request.getCoverImageUrl());
        card.setReason(request.getReason());
        card.setSummary(request.getSummary());
        card.setContent(request.getContent());
        // 透過 clear() 與 addAll() 保持 PersistentSet 的引用，讓 Hibernate 聰明地只做差集 SQL 更新
        card.updateTags(associatedTags);
        // 只有當前端有傳入新的 intervalDays 且跟原本不同時，才重新計算時間
        if (request.getIntervalDays() != null && !java.util.Objects.equals(request.getIntervalDays(), card.getIntervalDays())) {
            card.setIntervalDays(request.getIntervalDays());
            card.setNextShowAt(ZonedDateTime.now().plusDays(request.getIntervalDays()));
        }
        card.setArchived(request.getIsArchived());

        // 6. 存入資料庫
        // Card savedCard = cardRepository.save(card);

        // 7. 將結果包裝成 Response DTO 回傳
        return convertToDetailResponse(card);
    }

    @Transactional
    public CardDetailResponse createCard(CardRequest request) {

        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long userId = SecurityUtil.getCurrentUserId();

        // 2. 驗證使用者是否存在
        // getReferenceById 取代 findById：延遲載入的代理物件（Proxy），這個代理物件只帶有 ID。當你把它設定給 Card 並儲存時，JPA 只會去抓這個 Proxy 的 ID 寫入 cards.user_id 欄位，全程不會發送查詢 User 的 SQL，直接省掉一次資料庫 I/O
        User user = userRepository.getReferenceById(userId);

        // 3. 處理標籤安全性：走「查無此標籤才建立」的邏輯
        Set<Tag> associatedTags = associateTags(user, request.getTags());

        // 4. 將 DTO 轉換為 Entity，並綁定目前登入的使用者
        Card card = Card.builder()
                .user(user)
                .type(request.getType())
                .title(request.getTitle())
                .coverImageUrl(request.getCoverImageUrl())
                .url(request.getUrl())
                // .sourceType(request.getSourceType())
                .summary(request.getSummary())
                .content(request.getContent())
                .reason(request.getReason())
                .tags(associatedTags) // 關聯安全的標籤庫
                .intervalDays(request.getIntervalDays())
                .nextShowAt(ZonedDateTime.now().plusDays(request.getIntervalDays()!=null ? request.getIntervalDays():10)) // 預設 10 天後回流
                .isArchived(false)
                .build();

        // 3. 存入資料庫
        Card savedCard = cardRepository.save(card);

        // 4. 將結果包裝成 Response DTO 回傳
        return convertToDetailResponse(savedCard);
    }

    @Transactional
    public CardDetailResponse deleteCardById(Long cardId){

        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            // 故意拋出 403 Forbidden！這會直接觸發你前端 Axios 攔截器的 case 403 -> "權限不足，拒絕存取"
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權刪除此卡片");
        }

        // 先將要回傳的資料轉為 DTO（因為刪除後該物件就不應再被操作）
        CardDetailResponse response = convertToDetailResponse(card);

        // 4. 執行刪除
        cardRepository.delete(card); // 既然上面已經拿到 card 實體，直接 delete(card) 效能更好

        // 5. 正確回傳 DTO
        return response;
    }

    private Set<Tag> associateTags(User user, String[] requestTags){

        if(requestTags==null){
            return new java.util.HashSet<>(); // ✨ 永遠回傳空集合，絕對不回傳 null
        }

        // 使用 Arrays.stream 去重，避免同一次請求帶入重複字串導致非預期錯誤
        List<String> distinctTags = java.util.Arrays.stream(requestTags)
            .filter(java.util.Objects::nonNull)
            .map(String::trim)
            .filter(tag -> !tag.isEmpty()) // 防止空白標籤入庫
            .distinct()
            .toList();

        // 3. 處理標籤安全性：走「查無此標籤才建立」的邏輯
        Set<Tag> associatedTags = new HashSet<>();

        for (String tagname : distinctTags){
            // 嚴格隔離：只找「當前登入使用者」的標籤庫
            Tag tag = tagRepository.findByUserIdAndName(user.getId(), tagname)
                        .orElseGet(()->{
                            // 找不到就為該使用者新創一個
                            Tag newTag = Tag.builder().user(user).name(tagname).build();
                            return tagRepository.save(newTag);
                        });
            associatedTags.add(tag);
        }

        return associatedTags;
    }

    private CardSummaryResponse convertToSummaryResponse(Card card){
        CardSummaryResponse response = new CardSummaryResponse();
        response.setId(card.getId());
        response.setType(card.getType());
        response.setTitle(card.getTitle());
        response.setReason(card.getReason());
        response.setSummary(card.getSummary());
        response.setTags(convertTags(card.getTags()));
        response.setLikeAvailableAt(card.getLikeAvailableAt());
        response.setLikeCount(card.getLikeCount());
        response.setUrl(card.getUrl());
        response.setNextShowAt(card.getNextShowAt());
        response.setCreatedAt(card.getCreatedAt());
        response.setIsArchived(card.isArchived());
        response.setIntervalDays(card.getIntervalDays());
        return response;
    }

    private CardDetailResponse convertToDetailResponse(Card card) {
        CardDetailResponse response = new CardDetailResponse();
        response.setId(card.getId());
        response.setType(card.getType());
        response.setTitle(card.getTitle());
        response.setTags(convertTags(card.getTags()));
        response.setCoverImageUrl(card.getCoverImageUrl());
        response.setReason(card.getReason());
        response.setSummary(card.getSummary());
        response.setContent(card.getContent());
        response.setUrl(card.getUrl());
        response.setIntervalDays(card.getIntervalDays());
        response.setNextShowAt(card.getNextShowAt());
        response.setOpenCount(card.getOpenCount());
        response.setLikeAvailableAt(card.getLikeAvailableAt());
        response.setLastLikedAt(card.getLastLikedAt());
        response.setLikeCount(card.getLikeCount());
        response.setIsArchived(card.isArchived());
        response.setLastOpenAt(card.getLastOpenAt());
        response.setLastInteractionAt(card.getLastInteractionAt());
        response.setCreatedAt(card.getCreatedAt());
        response.setUpdatedAt(card.getUpdatedAt());
        response.setSourceType(card.getSourceType());
        return response;
    }

    private List<String> convertTags(Set<Tag> tags){
        // 💡 補上這個邏輯：將 Set<Tag> 轉為 List<String> 填入 DTO
        if (tags != null) {
            List<String> tagNames = tags.stream()
                    .map(Tag::getName)
                    .sorted()
                    .toList();
            return tagNames; // 確保你的 CardResponse 內有 private List<String> tags; 欄位
        }
        return null;
    }

    // ==================== 1. 星號 / 取消星號功能 ====================
    @Transactional
    public CardDetailResponse toggleCardStar(Long cardId, CardStatusRequest request) {
        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權操作此卡片的星星狀態");
        }

        ZonedDateTime now = ZonedDateTime.now();

        // 4. 根據 request.getStarStatus() 判斷是「點亮星星(true)」還是「熄滅星星(false)」
        if (Boolean.TRUE.equals(request.getStarStatus())) {

            // 🔒 樂觀更新防禦防線：檢查當前時間是否已經過了冷卻截止時間
            if (card.getLikeAvailableAt() != null && now.isBefore(card.getLikeAvailableAt())) {
                // 如果冷卻時間還沒到，直接拋出 400 Bad Request，防止前端繞過介面直接敲 API
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "星星冷卻中，目前無法點亮");
            }

            card.setLikeCount(card.getLikeCount() + 1);
            card.setLastLikedAt(now);
            card.setLikeAvailableAt(now.plusDays(7)); // ✨ 核心：設定 7 天後才能再次點亮

        } else {
            // 熄滅星星（取消點讚）邏輯
            // 防禦性設計：確保星星數最低為 0，不會扣到變成負數
            card.setLikeCount(Math.max(0, card.getLikeCount() - 1));

            // 取消點讚後，清除冷卻時間，讓使用者可以隨時重新點亮
            card.setLikeAvailableAt(null);
        }

        // 5. 紀錄最後互動時間 (方便後端做排序或統計)
        card.setLastInteractionAt(now);

        // 6. 儲存回資料庫 (在 @Transactional 內，其實不寫 save 也會自動 flush，但寫出來語意更清晰)
        cardRepository.save(card);

        // 7. 直接將更新後的實體轉成你現有的 CardDetailResponse 回傳
        return convertToDetailResponse(card);
    }

    // ==================== 2. 封存 / 取消封存功能 ====================
    @Transactional
    public CardDetailResponse toggleCardArchive(Long cardId, CardStatusRequest request) {
        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權操作此卡片的封存狀態");
        }

        // 4. 更新封存狀態
        if (Boolean.TRUE.equals(request.getArchivedStatus())) {
            card.setArchived(Boolean.TRUE);
        } else {
            card.setArchived(Boolean.FALSE);
        }

        return convertToDetailResponse(card);
    }

    // ==================== 3. 稍後再看（延遲回流）功能 ====================
    @Transactional
    public CardDetailResponse snoozeCard(Long cardId, CardStatusRequest request) {
        // 1. 安全地從安全上下文取得目前登入的 userId（防範前端越權傳參）
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // 2. 查詢資料庫是否有這張卡片
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        // 3. 核心安全檢查：這張卡片的主人，是目前登入的這名用戶嗎？
        if (!card.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權延遲此卡片");
        }

        // 4. 更新下次回流日期
        // 優先讀取 request -> 再讀取卡片本身的設定 -> 預設為 10 天
        int daysToPlus = request.getNextIntervalDays();
        if(daysToPlus <= 0){
            daysToPlus = (card.getIntervalDays() != null) ? card.getIntervalDays() : 10;
        }

        // 5. 更新下次回流時間，不碰 lastInteractionAt
        card.setNextShowAt(ZonedDateTime.now().plusDays(daysToPlus));

        return convertToDetailResponse(card);
    }

    @Transactional
    public CardDetailResponse readCard(Long cardId) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "卡片不存在"));

        if (!card.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "您無權操作此卡片");
        }

        // 更新統計與互動時間相關欄位
        card.setOpenCount(card.getOpenCount() + 1);
        card.setLastOpenAt(ZonedDateTime.now());
        card.setLastInteractionAt(ZonedDateTime.now());

        // 推進回流時間：若有設定天數就用卡片的，否則預設 10 天
        int days = card.getIntervalDays() != null ? card.getIntervalDays() : 10;
        card.setNextShowAt(ZonedDateTime.now().plusDays(days));

        // 如果這張卡之前一直被稍後再看，現在終於被打開詳情，可重置 snoozeCount。
        card.setSnoozeCount(0);

        return convertToDetailResponse(card);
    }

}
