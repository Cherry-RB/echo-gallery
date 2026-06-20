import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/vue-query';
import { cardApi } from './api/card';
import { ElMessage } from 'element-plus';

// 簡單定義卡片與無限捲動快取的基礎型別，提升程式碼強健度
interface CardDTO {
    id: string | number;
    likeCount: number;
    likeAvailableAt: string | null;
    isArchived: boolean;
    [key: string]: any; // 保留其他後端自訂欄位
}

interface InfiniteCardData {
    pages: CardDTO[][];
    pageParams: any[];
}

// =====================================================
// TanStack Query 樂觀更新核心邏輯
// 核心組合式函式 (Composable)：管理所有卡片狀態的互動
// =====================================================
export const useCardStatus = () => {
    
    // 取得 TanStack Query 的全局快取管理器（像是一個前端的臨時資料庫快取中心）
    const queryClient = useQueryClient();

    // =====================================================
    // 🎯 內部共用核心工具 1：通用快取備份 (用於出錯還原)
    // =====================================================
    const prepareSnapshot = async (id: string | number) => {
        // 1. 取消所有相關的網路請求，避免舊資料回流覆蓋了樂觀更新
        await queryClient.cancelQueries({ queryKey: ['cards'] });
        await queryClient.cancelQueries({ queryKey: ['card', id] });

        // 2. 獲取當前單張卡片的詳情快照
        const previousCardDetail = queryClient.getQueryData<CardDTO>(['card', id]);
        // 3. 進階：找出所有以 ['cards'] 開頭的快取（例如包含篩選條件的 ['cards', { status: 'active' }]）
        const queryCache = queryClient.getQueryCache();
        const matchingQueries = queryCache.findAll({ queryKey: ['cards'] });
        
        // 紀錄所有符合的快取現狀，用於錯誤還原
        const previousListsSnapshot = matchingQueries.map(query => ({
            queryKey: query.queryKey,
            data: query.state.data
        }));
        
        return { previousListsSnapshot, previousCardDetail };
    };
    // =====================================================
    // 🎯 內部共用核心工具 2：精準修改單張卡片快取的通用手術刀
    // =====================================================
    const updateLocalCache = (id: string | number, patchFields: Partial<CardDTO>) => {
        // A. 使用 setQueriesData 模糊更新所有開頭為 ['cards'] 的列表快取（不論是否帶篩選參數）
        queryClient.setQueriesData<InfiniteData<CardDTO[]> | CardDTO[]>({ queryKey: ['cards'] }, (old: any) => {
            // 如果目前根本還沒有快取資料（例如還沒開過首頁），就什麼都不做直接返回
            if (!old) return old;
            // 處理使用 useInfiniteQuery 的二維陣列結構 { pages: [ [card, card], [...] ] }
            // 結構是二維陣列：{ pages: [ [card1, card2], [card3, card4] ] } 
            if (old.pages && Array.isArray(old.pages)) {
                return {
                    ...old, // 保留原本分頁的其他資訊（例如 pageParams）
                    // 迴圈遍歷每一個分頁（第一層陣列）
                    pages: old.pages.map((page: CardDTO[]) => 
                        // 迴圈遍歷該分頁裡的每一張卡片（第二層陣列）
                        page.map((card: CardDTO) =>
                            // 檢查這張卡片的 ID 是否就是我們要操作的那張卡片？
                            String(card.id) === String(id) 
                            ? { ...card, ...patchFields } // 用解構語法，把舊卡片的所有欄位，疊加覆蓋上我們新傳進來的修改欄位
                            : card // 沒找到的卡片，原封不動傳回去
                        )
                    )
                };
            }
            // 備用：處理一般非無限捲動的一維陣列清單快取 [card, card]
            if (Array.isArray(old)) {
                return old.map((card: CardDTO) =>
                    String(card.id) === String(id) ? { ...card, ...patchFields } : card
                );
            }
            return old;
        });
        // B. 同步更新「單張卡片詳情頁」的快取
        queryClient.setQueryData<CardDTO>(['card', id], (old) => {
            // 如果詳情頁還沒有快取，就不處理
            if (!old) return old;
            // 找到了就直接進行物件欄位疊加覆蓋
            return { ...old, ...patchFields };
        });
    };
    // =====================================================
    // 🎯 內部共用核心工具 3：發生錯誤時的統一彈回與錯誤提示
    // =====================================================
    const handleMutationError = (err: any, id: string | number, context: any) => {
        // 檢查剛剛在 onMutate 備份的快照在不在？
        // 如果在，直接塞回快取，畫面瞬間彈回原本的樣子
        // 還原所有列表快取
        if (context?.previousListsSnapshot) {
            context.previousListsSnapshot.forEach(({ queryKey, data }: any) => {
                queryClient.setQueryData(queryKey, data);
            });
        }
        // 還原詳情頁快取
        if (context?.previousCardDetail){
            queryClient.setQueryData(['card', id], context.previousCardDetail);
        }
        
        // 解析後端報錯的客製化訊息（例如："星星冷卻中"、"權限不足"），如果沒有就給預設字串
        const errorMsg = err.response?.data?.message || "操作失敗，請稍後再試";
        // 跳出 Element Plus 的紅色驚嘆號提示
        ElMessage.error(errorMsg);
    };
    // =====================================================
    // 🚀 功能 1. 點擊星星 Mutation (保留你的樂觀更新邏輯)
    // =====================================================
    const starMutation = useMutation({
        // 發送給後端 Axios 的網路請求
        mutationFn: ({ id, starStatus }: { id: string | number; starStatus: boolean }) => 
            cardApi.toggleCardStar(id, { starStatus }),
        
        // 點擊瞬間立刻執行：樂觀更新核心
        onMutate: async ({ id, starStatus }) => {
            // 把目前的快取拍照備份，並取得備份物件
            const snapshot = await prepareSnapshot(id);
            
            // 計算預期數值 (下一次可以再點的 7 天冷卻時間)
            const now = new Date();
            const sevenDaysLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
            const nextLikeAvailableAt = starStatus ? sevenDaysLater : null;

            // 樂觀更新：前端星星秒變，並將該卡片的「點讚次數」加減（透過找出舊資料當前數量計算）
            let currentLikeCount = 0;
            if (snapshot.previousCardDetail?.likeCount !== undefined) {
                // 優先使用詳情頁快取（最準確）
                currentLikeCount = snapshot.previousCardDetail.likeCount;
            } else if (snapshot.previousListsSnapshot && snapshot.previousListsSnapshot.length > 0) {
                // 如果沒有詳情頁，遍歷所有符合的列表快取去撈取點讚數
                const firstValidList: any = snapshot.previousListsSnapshot.find(snap => snap.data);
                firstValidList?.data?.pages?.forEach((page: CardDTO[]) => {
                    const found = page.find((c) => String(c.id) === String(id));
                    if (found) currentLikeCount = found.likeCount;
                });
            }

            const likeDelta = starStatus ? 1 : -1;

            // 執行樂觀更新
            updateLocalCache(id, {
                likeAvailableAt: nextLikeAvailableAt,
                likeCount: Math.max(0, currentLikeCount + likeDelta)
            });

            // // 把備份交給 TanStack Query 保管。如果等一下失敗了，onError 就拿得到它
            return snapshot;
        },
        // 後端成功後執行
        onSuccess: (updatedCard, variables) => {
            // 後端成功後，用後端精準的最新 DTO (含排序好的 tags、精準互動時間) 覆蓋
            updateLocalCache(variables.id, updatedCard);
        },
        // 後端失敗時執行：直接打包丟給工具 3 安全氣囊處理
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });


    // =====================================================
    // 🚀 功能 2. 封存卡片 Mutation (樂觀更新：立刻變灰)
    // =====================================================
    const archiveMutation = useMutation({
        // 【打後端】
        mutationFn: ({ id, archivedStatus }: { id: string | number; archivedStatus: boolean }) =>
            cardApi.toggleArchive(id, { archivedStatus }),
        
        // 【點擊瞬間立刻執行】
        onMutate: async ({ id, archivedStatus }) => {
            // 備份快照
            const snapshot = await prepareSnapshot(id);
            
            // 樂觀更新：立刻讓前端卡片進入封存狀態 (觸發 CSS 變灰)
            updateLocalCache(id, { isArchived: archivedStatus });
            
            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            // DTO 覆蓋快取
            updateLocalCache(variables.id, updatedCard);
            // 跳出 Element Plus 的成功通知
            ElMessage.success(updatedCard.isArchived ? "卡片已成功封存" : "卡片已取消封存");
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });
    // =====================================================
    // 🚀 功能 3. 稍後再看 Mutation (樂觀更新：立刻變灰)
    // =====================================================
    const snoozeMutation = useMutation({
        // 【打後端】
        mutationFn: ({ id, nextIntervalDays }: { id: string | number; nextIntervalDays: number }) =>
            cardApi.snoozeCard(id, { nextIntervalDays }),
        
        // 【點擊瞬間立刻執行】
        onMutate: async ({ id }) => {
            const snapshot = await prepareSnapshot(id);
            
            // 從前端大陣列中將其剔除，瀑布流會立刻重新排版，卡片原地消失
            // 使用 setQueriesData 確保所有開頭為 ['cards'] 的列表都同步剔除該卡片
            queryClient.setQueriesData<InfiniteData<CardDTO[]>>({ queryKey: ['cards'] }, (oldData: any) => {
                if (!oldData || !oldData.pages) return oldData;
                return {
                    ...oldData,
                    // 遍歷當前載入的每一頁，直接 filter 掉這張卡片
                    pages: oldData.pages.map((page: any[]) => {
                        return page.filter(card => String(card.id) !== String(id));
                    })
                };
            });
            
            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            // updateLocalCache(variables.id, updatedCard);
            // 因為大清單（['cards']）在 onMutate 已經清乾淨了，這裡完全不需動它。
            // 我們只需要同步更新「單張卡片詳情頁」的快取，確保系統底層資料的一致性
            queryClient.setQueryData(['card', variables.id], updatedCard);
            ElMessage.success("已排程，卡片將於下次回流日再次出現");
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });
    // =====================================================
    // 🚀 功能 4. 已讀(進入卡片詳情/打開卡片連結) Mutation
    // =====================================================
    const readMutation = useMutation({
        // 【打後端】
        mutationFn: ({ id }: { id: string | number; intervalDays?: number }) =>
            cardApi.readCard(id),
        
        // 【點擊瞬間立刻執行】
        onMutate: async ({ id }) => {
            const snapshot = await prepareSnapshot(id);

            // 樂觀更新：立刻 filter 掉
            queryClient.setQueriesData<InfiniteData<CardDTO[]>>({ queryKey: ['cards'] }, (oldData: any) => {
                if (!oldData || !oldData.pages) return oldData;
                return {
                    ...oldData,
                    pages: oldData.pages.map((page: any[]) => 
                        page.filter(card => String(card.id) !== String(id))
                    )
                };
            });

            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            queryClient.setQueryData(['card', variables.id], updatedCard);
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) => 
            handleMutationError(err, variables.id, context)
    });


    // =====================================================
    // 📤 統一對外暴露的接口
    // =====================================================
    return {
        handleToggleStar: starMutation.mutate,
        handleToggleArchive: archiveMutation.mutate,
        handleSnoozeCard: snoozeMutation.mutate,
        handleReadCard: readMutation.mutate,
        
        // 如果你有需要按鈕讀條(Loading) 狀態也可以順便拿出去
        isStarPending: starMutation.isPending,
        isArchivePending: archiveMutation.isPending,
        isSnoozePending: snoozeMutation.isPending,
        isReadPending: readMutation.isPending,
    };
};


    // const { mutate: handleToggleStar } = useMutation({
    // // 1. 實際發送 API
    // mutationFn: ({ id, starStatus }: { id: string | number; starStatus: boolean }) => 
    //     cardApi.toggleCardStar(id, { starStatus }),

    // // 2. 點擊瞬間立刻執行 (樂觀更新：讓星星秒亮/秒暗)
    // onMutate: async ({ id, starStatus }) => {
    //     // 取消正在進行的 refetch，避免舊資料蓋過我們的樂觀更新
    //     await queryClient.cancelQueries({ queryKey: ['cards'] });
    //     await queryClient.cancelQueries({ queryKey: ['card', id] }); // 同步取消詳情頁快取

    //     // 備份當前的快取狀態 (Snapshot)，若出錯可以用來還原
    //     const previousCards = queryClient.getQueryData(['cards']);
    //     const previousCardDetail = queryClient.getQueryData(['card', id]);

    //     // 計算預期的最新數值 (維持你的 Bilibili 7天冷卻邏輯)
    //     const now = new Date();
    //     const sevenDaysLater = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
    //     const nextLikeAvailableAt = starStatus ? sevenDaysLater : null;
    //     const likeDelta = starStatus ? 1 : -1;

    //     // 更新「瀑布流列表」的快取 (前端先假裝成功）(需處理使用 useInfiniteQuery 的 pages 二維陣列結構)
    //     queryClient.setQueryData(['cards'], (old: any) => {
    //     if (!old) return old;
    //     return {
    //         ...old,
    //         pages: old.pages.map((page: any) => {
    //         return page.map((card: any) => {
    //             if (String(card.id) === String(id)) {
    //             return {
    //                 ...card,
    //                 likeAvailableAt: nextLikeAvailableAt,
    //                 likeCount: Math.max(0, card.likeCount + likeDelta)
    //             };
    //             }
    //             return card;
    //         });
    //         })
    //     };
    //     });

    //     // 返回備份內容
    //     return { previousCards, previousCardDetail };
    // },

    // // 3. API 成功後，精準覆蓋，絕不刷新 100 頁！
    // onSuccess: (updatedCard, variables) => {
    //     queryClient.setQueryData(['cards'], (old: any) => {
    //     if (!old) return old;
    //     return {
    //         ...old,
    //         pages: old.pages.map((page: any) => {
    //         return page.map((card: any) => {
    //             // 🎯 找到了！只用後端回傳的「最新真實 DTO」替換這單獨一張卡片
    //             // 包含後端排序好的 tags 順序、精準的冷卻時間，其他 99 頁原封不動！
    //             if (String(card.id) === String(variables.id)) {
    //             return updatedCard; 
    //             }
    //             return card;
    //         });
    //         })
    //     };
    //     });
    //     // 同步精準更新單張卡片的詳情快取（確保點進去也是最新資料）
    //     queryClient.setQueryData(['card', variables.id], updatedCard);
    // },

    // // 4. 當後端錯誤或失效時，自動回復原本樣子 (例如星星冷卻中 400 Bad Request)
    // onError: (err: any, variables, context) => {
    //     // 還原成點擊前的快取模樣（星星彈回原狀）
    //     if (context?.previousCards) {
    //     queryClient.setQueryData(['cards'], context.previousCards);
    //     }
    //     if (context?.previousCardDetail) {
    //     queryClient.setQueryData(['card', variables.id], context.previousCardDetail);
    //     }
    //     // 抓取後端噴出來的 "星星冷卻中，目前無法點亮"
    //     const errorMsg = err.response?.data?.message || "操作失敗，請稍後再試";
        
    //     // 列表頁也跳出精緻的紅色驚嘆號！
    //     ElMessage.error(errorMsg);
    // },
    // // 🧹 功成身退：把原本的強制 invalidate 刪除
    // onSettled: () => {
    //     // 這裡留空！不再叫瀏覽器去發送大範圍的列表重新撈取請求。
    // }
    // });
// }