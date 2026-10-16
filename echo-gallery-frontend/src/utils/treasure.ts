import type { TreasureContent, TreasureDetail, TreasureRank, TrialStatus } from '../types/treasure'

export const yieldHistoryDescription = '記錄成果帶來的新作品、回應與機會。'

export const ranks: Record<TreasureRank, string> = { STONE: '石級', BRONZE: '銅級', SILVER: '銀級', GOLD: '金級' }
export const challengeRanks: TreasureRank[] = ['BRONZE', 'SILVER', 'GOLD']
export const nextRankOf = (rank: TreasureRank): TreasureRank | null => ({ STONE: 'BRONZE', BRONZE: 'SILVER', SILVER: 'GOLD', GOLD: null } as const)[rank]
export function challengeState(trial: import('../types/treasure').TreasureTrial, rank: TreasureRank): string {
  if (trial.status === 'PASSED') return '已通過'
  if (trial.targetRank !== nextRankOf(rank)) return '預先規劃'
  if (trial.status === 'NOT_PASSED') return '未通過・可再嘗試'
  if (trial.status === 'ATTEMPTED') return '已嘗試・待確認'
  return '待嘗試'
}
export const trialStatuses: Record<TrialStatus, string> = { PLANNED: '準備挑戰', ATTEMPTED: '已嘗試', NOT_PASSED: '未通過', CANCELLED: '已取消', PASSED: '已通過' }
export const eventKinds = { COLLECTED: '收入館藏', TRIAL_ATTEMPTED: '嘗試紀錄', PROMOTED: '晉級', YIELD: '價值產出' }

export const rankGuides: Record<TreasureRank, { stage: string; summary: string; meaning: string; examples: { title: string; criteria: string }[] }> = {
  STONE: { stage: '取得成果', summary: '完成成果並入藏，作為起點，不另設檢定。', meaning: '真正完成一件作品、專案或成果，收入館藏。這是成長的起點，入藏不需要額外檢定。', examples: [] },
  BRONZE: { stage: '踏出私人領域', summary: '投稿、對外介紹或邀請試用，讓成果接觸外界。', meaning: '讓成果真正接觸外部世界：投稿、對外介紹、分享或邀請試用，留下可觀察的行動紀錄。', examples: [
    { title: '小說：完成一次正式投稿', criteria: '選定合適的投稿管道，依規則提交完成的小說，留下投稿日期與提交紀錄；不以獲得錄用作為本次條件。' },
    { title: 'MVP：向外部使用者介紹並邀請試用', criteria: '選定目標使用者，實際向對方介紹產品並發出試用邀請，留下介紹內容、邀請日期與紀錄；不以一定收到回覆作為本次條件。' },
    { title: '成果：對外分享或展示', criteria: '選定適合的外部對象或媒介，實際分享或展示成果，留下日期、分享內容與紀錄；不需要公開給所有人。' },
  ] },
  SILVER: { stage: '接受外部驗證', summary: '取得外部回饋，據此改善並留下驗證紀錄。', meaning: '取得真實讀者、使用者或合作對象的具體回應，根據回饋改善成果，留下外部驗證與改進的證據。', examples: [
    { title: '小說：取得讀者回饋並完成修訂', criteria: '邀請適合的讀者實際閱讀小說，取得具體意見，據此完成一次有意義的修訂，記錄回饋與修改內容。讀者數量可自行設定。' },
    { title: 'MVP：取得使用者回饋並改善產品', criteria: '邀請目標使用者實際試用，記錄對方遇到的問題或需求，根據回饋完成一次產品改善，留下試用與修改紀錄。' },
    { title: '成果：與外部對象驗證用途', criteria: '請合適的外部對象在真實情境使用或體驗成果，取得具體回應，完成相應調整並記錄驗證結果。' },
  ] },
  GOLD: { stage: '形成持續價值', summary: '獲得實際採用，或形成持續的讀者與使用者連結。', meaning: '成果獲得實際採用、刊登，或與外部讀者、使用者形成持續連結，證明它實現了價值；不必商業化或獲得權威認可。', examples: [
    { title: '小說：作品獲得實際刊登或採用', criteria: '讓作品獲得合適媒介的實際刊登或採用，留下採用通知與成果紀錄。這是可選路線，不是所有作品的必要門檻。' },
    { title: 'MVP：建立持續使用並驗證核心需求', criteria: '自行設定觀察期間與可確認的使用條件，取得真實使用者持續使用的紀錄，結合回饋確認產品解決了哪個核心需求；不要求付費。' },
    { title: '成果：建立持續的外部連結', criteria: '選定讀者、使用者或合作對象，自訂觀察期間與持續互動條件，記錄再次閱讀、採用、回饋或延伸應用，確認成果持續提供的價值。' },
  ] },
}

export function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Taipei', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const get = (type: string) => parts.find(part => part.type === type)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}

export function contentOf(t?: TreasureDetail): TreasureContent {
  return {
    title: t?.title ?? '', description: t?.description ?? '', artifactUrl: t?.artifactUrl ?? '', coverImageUrl: t?.coverImageUrl ?? '',
    acquiredOn: t?.acquiredOn ?? today(), sourceCardId: t?.sourceCard?.id ?? null, sourceExperimentId: t?.sourceExperiment?.id ?? null,
    sourceTreasureIds: t?.sourceTreasures.map(source => source.id) ?? [],
  }
}

export function errorMessage(error: unknown): string {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message ?? '儲存失敗，請稍後再試'
}
