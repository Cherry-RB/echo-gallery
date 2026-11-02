import { useMutation, useQueryClient } from '@tanstack/vue-query';
import { cardApi } from './api/cardApi';
import { ElMessage } from 'element-plus';
import type { BoardType } from '../types/board';
import type { CardDto, TodayBatchResponse, UpdateCardRequest } from '../types/card';
import { removeTodayCard, todayBatchQueryKey, updateTodayCard } from './todayBatchCache';
import { cardDetailQueryKey } from './cardDetailQuery';

// 簡單定義卡片與無限捲動快取的基礎型別，提升程式碼強健度
interface CardDTO {
    id: string | number;
    likeCount: number;
    likeAvailableAt: string | null;
    isArchived: boolean;
    [key: string]: any; // 保留其他後端自訂欄位
}

// 集中處理陣列、一般分頁與無限分頁，保留各格式的游標及其他資訊。
function mapCardLists(data: unknown, transform: (cards: CardDTO[]) => CardDTO[]): unknown {
    if (Array.isArray(data)) return transform(data);
    if (!data || typeof data !== 'object') return data;
    if ('pages' in data && Array.isArray(data.pages)) {
        return { ...data, pages: data.pages.map(page => mapCardLists(page, transform)) };
    }
    if ('content' in data && Array.isArray(data.content)) return { ...data, content: transform(data.content) };
    return data;
}

function findCard(data: unknown, id: string | number): CardDTO | undefined {
    let found: CardDTO | undefined;
    mapCardLists(data, cards => {
        found ??= cards.find(card => String(card.id) === String(id));
        return cards;
    });
    return found;
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
        await queryClient.cancelQueries({ queryKey: todayBatchQueryKey });
        await queryClient.cancelQueries({ queryKey: cardDetailQueryKey(id) });

        // 2. 獲取當前單張卡片的詳情快照
        const previousCardDetail = queryClient.getQueryData<CardDTO>(cardDetailQueryKey(id));
        const previousTodayBatch = queryClient.getQueryData<TodayBatchResponse>(todayBatchQueryKey);
        // 3. 進階：找出所有以 ['cards'] 開頭的快取（例如包含篩選條件的 ['cards', { status: 'active' }]）
        const queryCache = queryClient.getQueryCache();
        const matchingQueries = queryCache.findAll({ queryKey: ['cards'] });

        // 紀錄所有符合的快取現狀，用於錯誤還原
        const previousListsSnapshot = matchingQueries.map(query => ({
            queryKey: query.queryKey,
            data: query.state.data
        }));

        return { previousListsSnapshot, previousCardDetail, previousTodayBatch };
    };
    // =====================================================
    // 🎯 內部共用核心工具 2：精準修改單張卡片快取的通用手術刀
    // =====================================================
    const updateLocalCache = (id: string | number, patchFields: Partial<CardDTO>) => {
        queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old =>
            updateTodayCard(old, id, patchFields as Partial<CardDto>));
        // A. 使用 setQueriesData 模糊更新所有開頭為 ['cards'] 的列表快取（不論是否帶篩選參數）
        queryClient.setQueriesData({ queryKey: ['cards'] }, old => mapCardLists(old, cards =>
            cards.map(card => String(card.id) === String(id) ? { ...card, ...patchFields } : card)));
        // B. 同步更新「單張卡片詳情頁」的快取
        queryClient.setQueryData<CardDTO>(cardDetailQueryKey(id), (old) => {
            // 如果詳情頁還沒有快取，就不處理
            if (!old) return old;
            // 找到了就直接進行物件欄位疊加覆蓋
            return { ...old, ...patchFields };
        });
    };
    const removeFromLists = (id: string | number, queryKey: readonly unknown[] = ['cards']) => {
        queryClient.setQueriesData({ queryKey }, old => mapCardLists(old, cards =>
            cards.filter(card => String(card.id) !== String(id))));
    };
    const invalidateCardRelations = () => {
        // 封存或刪除會改變來源可用性、數量與目標納入結果，刷新已存在的相關查詢。
        for (const key of ['cards', 'tags', 'sidebar', 'overview', 'goals', 'experiments', 'experiment',
            'experiment-thread-summaries', 'experiment-thread', 'card-exploration-threads',
            'works', 'work-candidates', 'treasures', 'issues', 'issue', 'issueCards']) {
            void queryClient.invalidateQueries({ queryKey: [key] });
        }
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
            queryClient.setQueryData(cardDetailQueryKey(id), context.previousCardDetail);
        }
        if (context?.previousTodayBatch) {
            queryClient.setQueryData(todayBatchQueryKey, context.previousTodayBatch);
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
            const cachedCard = snapshot.previousCardDetail
                ?? snapshot.previousTodayBatch?.cards.find(card => String(card.id) === String(id))
                ?? snapshot.previousListsSnapshot.map(snap => findCard(snap.data, id)).find(Boolean);
            const currentLikeCount = cachedCard?.likeCount ?? 0;

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
            queryClient.invalidateQueries({ queryKey: ['cards'] });
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
            if (archivedStatus) {
                queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));
            }

            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            // DTO 覆蓋快取
            updateLocalCache(variables.id, updatedCard);
            invalidateCardRelations();

            // 跳出 Element Plus 的成功通知
            ElMessage.success(updatedCard.isArchived ? "卡片已封存" : "卡片已還原");
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });

    // =====================================================
    // 🚀 功能 3. 暫停 / 恢復回流 Mutation
    // =====================================================
    const pauseMutation = useMutation({
        mutationFn: ({ id }: { id: string | number }) => cardApi.pauseCard(id),
        onMutate: async ({ id }) => {
            const snapshot = await prepareSnapshot(id);
            updateLocalCache(id, { intervalDays: null, nextShowAt: null });
            queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));
            return snapshot;
        },
        onSuccess: (updatedCard, variables) => {
            updateLocalCache(variables.id, updatedCard);
            queryClient.invalidateQueries({ queryKey: ['cards'] });
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
            queryClient.invalidateQueries({ queryKey: ['issueCards'] });
            ElMessage.success('卡片回流已暫停');
        },
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });

    const resumeMutation = useMutation({
        mutationFn: ({ id, intervalDays }: { id: string | number; intervalDays: number }) =>
            cardApi.resumeCard(id, intervalDays),
        onMutate: async ({ id }) => prepareSnapshot(id),
        onSuccess: (updatedCard, variables) => {
            updateLocalCache(variables.id, updatedCard);
            queryClient.invalidateQueries({ queryKey: ['cards'] });
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
            queryClient.invalidateQueries({ queryKey: ['issueCards'] });
            ElMessage.success(`已恢復每 ${updatedCard.intervalDays} 天回流`);
        },
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });

    const recurrenceMutation = useMutation({
        mutationFn: ({ id, intervalDays, deferCurrentOccurrence = false }: {
            id: string | number;
            intervalDays: number;
            deferCurrentOccurrence?: boolean;
        }) => cardApi.updateRecurrence(id, intervalDays, deferCurrentOccurrence),
        onMutate: async ({ id, deferCurrentOccurrence = false }) => {
            const snapshot = await prepareSnapshot(id);
            if (deferCurrentOccurrence) {
                queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));
            }
            return snapshot;
        },
        onSuccess: (updatedCard, variables) => {
            updateLocalCache(variables.id, updatedCard);
            if (variables.deferCurrentOccurrence) {
                queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old =>
                    removeTodayCard(old, variables.id));
            }
            queryClient.invalidateQueries({ queryKey: ['cards'] });
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
            queryClient.invalidateQueries({ queryKey: ['issueCards'] });
            ElMessage.success(variables.deferCurrentOccurrence
                ? `已稍後再看，將於 ${updatedCard.intervalDays} 天後回流`
                : `已調整為每 ${updatedCard.intervalDays} 天回流`);
        },
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });

    // =====================================================
    // 🚀 功能 4. 稍後再看 Mutation (樂觀更新：立刻變灰)
    // =====================================================
    const snoozeMutation = useMutation({
        // 【打後端】
        mutationFn: ({ id, nextIntervalDays }: { id: string | number; nextIntervalDays: number }) =>
            cardApi.snoozeCard(id, { nextIntervalDays }),

        // 【點擊瞬間立刻執行】
        onMutate: async ({ id }) => {
            const snapshot = await prepareSnapshot(id);

            queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));

            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            // Today 已在 onMutate 移除；其他看板依各自條件重新查詢。
            queryClient.setQueryData(cardDetailQueryKey(variables.id), updatedCard);
            queryClient.invalidateQueries({ queryKey: ['cards', 'snoozed'] });
            queryClient.invalidateQueries({ queryKey: ['cards', 'search'] });

            // 通知右側側邊欄過期，觸發自動重撈
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });

            ElMessage.success("已排程，卡片將於下次回流日再次出現");
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) => handleMutationError(err, variables.id, context)
    });
    // =====================================================
    // 🚀 功能 5. 已讀(進入卡片詳情/打開卡片連結) Mutation
    // =====================================================
    const readMutation = useMutation({
        // 【打後端】
        mutationFn: ({ id }: { id: string | number; sourceBoard : BoardType ;intervalDays?: number }) =>
            cardApi.readCard(id),

        // 【點擊瞬間立刻執行】
        onMutate: async ({ id, sourceBoard }) => {
            const snapshot = await prepareSnapshot(id);

            if (sourceBoard === 'today') {
                queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));
            }

            // 樂觀更新：立刻 filter 掉
            removeFromLists(id, ['cards', sourceBoard]);

            return snapshot;
        },
        // 【後端成功後執行】
        onSuccess: (updatedCard, variables) => {
            updateLocalCache(variables.id, updatedCard);
            queryClient.setQueryData(cardDetailQueryKey(variables.id), updatedCard);
            // 通知右側側邊欄過期，觸發自動重撈
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
        },
        // 【後端失敗時執行】
        onError: (err, variables, context) =>
            handleMutationError(err, variables.id, context)
    });
    // =====================================================
    // 🚀 功能 6. 新建卡片 Mutation
    // =====================================================
    const createCardMutation = useMutation({
        mutationFn: cardApi.createCard,
        // 後端成功建立卡片後執行
        onSuccess: () => {
            ElMessage.success('卡片已建立，留待日後重逢。');

            // 🌟 自動刷新右側側邊欄（統計、標籤排行）
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
            // 🌟 自動刷新主頁瀑布流
            queryClient.invalidateQueries({ queryKey: ['cards'] });
            // 卡片建立時如果帶有新標籤，讓全域標籤快取失效同步更新
            queryClient.invalidateQueries({ queryKey: ['tags'] });
        },
        onError: (err) => {
            ElMessage.error('建立卡片失敗，請稍後再試');
            console.error(err);
        }
    });

    // =====================================================
    // 🚀 功能 7. 更新卡片 Mutation
    // =====================================================
    // 💡 注意：因為 useMutation 的 mutationFn 只能接收一個參數，
    //    所以我們用解構賦值包成一個物件傳入 { id, data }
    const updateCardMutation = useMutation({
        mutationFn: ({ id, data }: { id: string | number; data: UpdateCardRequest }) => cardApi.updateCard(id, data),
        // 後端成功修改卡片後執行
        onSuccess: (_updatedCard, variables) => {
            ElMessage.success('卡片變更已儲存');

            // 🌟 讓當前這張卡片的詳細資料快取失效，觸發 Detail 頁面自動重撈
            queryClient.invalidateQueries({ queryKey: cardDetailQueryKey(variables.id) });
            // 🌟 同步刷新側邊欄與主頁瀑布流
            queryClient.invalidateQueries({ queryKey: ['sidebar'] });
            queryClient.invalidateQueries({ queryKey: ['cards'] });

            // 卡片建立時如果帶有新標籤，讓全域標籤快取失效同步更新
            queryClient.invalidateQueries({ queryKey: ['tags'] });
        },
        onError: (err) => {
            ElMessage.error('儲存修改失敗');
            console.error(err);
        }
    });

    // =====================================================
    // 🚀 功能 9. 刪除卡片 Mutation
    // =====================================================
    const deleteCardMutation = useMutation({
      mutationFn: ({ id }: { id: string | number }) =>
        cardApi.deleteCard(id),

      onMutate: async ({ id }) => {
          const snapshot = await prepareSnapshot(id);
          queryClient.setQueryData<TodayBatchResponse>(todayBatchQueryKey, old => removeTodayCard(old, id));
          removeFromLists(id);
          return snapshot;
      },

      onSuccess: (_, variables) => {
          // 移除詳細頁快取
          queryClient.removeQueries({ queryKey: cardDetailQueryKey(variables.id) });
          invalidateCardRelations();
          ElMessage.success('卡片刪除成功');
      },

      onError:(err, variables, context)=>{
          handleMutationError(
              err,
              variables.id,
              context
          );
      }
    })


    // =====================================================
    // 📤 統一對外暴露的接口
    // =====================================================
    return {
        handleToggleStar: starMutation.mutate,
        handleToggleArchive: archiveMutation.mutate,
        handlePauseCard: pauseMutation.mutate,
        handleResumeCard: resumeMutation.mutate,
        handleUpdateRecurrence: recurrenceMutation.mutate,
        handleSnoozeCard: snoozeMutation.mutate,
        handleReadCard: readMutation.mutate,
        handleCreateCard: createCardMutation.mutate,
        handleUpdateCard: updateCardMutation.mutate,
        handleDeleteCard: deleteCardMutation.mutateAsync,

        // 如果你有需要按鈕讀條(Loading) 狀態也可以順便拿出去
        isStarPending: starMutation.isPending,
        isArchivePending: archiveMutation.isPending,
        isPausePending: pauseMutation.isPending,
        isResumePending: resumeMutation.isPending,
        isRecurrencePending: recurrenceMutation.isPending,
        isSnoozePending: snoozeMutation.isPending,
        isReadPending: readMutation.isPending,
        isCreatePending: createCardMutation.isPending,
        isUpdatePending: updateCardMutation.isPending,
        isDeletePending: deleteCardMutation.isPending,
    };
};
