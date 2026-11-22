import { dayjs } from 'element-plus'
import type { GoalDefinition, GoalKind } from '../types/goal'

export function goalDates() {
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Taipei' })
  return { startsOn: today, endsOn: dayjs(today).add(7, 'day').format('YYYY-MM-DD') }
}
export function hasGoalCards(definition: Pick<GoalDefinition, 'sources'>) {
  return definition.sources.some(source => !source.excluded && source.kind !== 'TREASURE')
}
export const metricGroups: Array<{ title: string; kinds: GoalKind[]; keys: string[]; defaults: string[] }> = [
  { title: '探索紀錄', kinds: ['EXPERIMENT'], keys: ['M21'], defaults: [] },
  { title: '卡片', kinds: ['TAG', 'CARD'], keys: ['M01', 'M02', 'M03', 'M04', 'M05', 'M06'], defaults: ['M01', 'M02', 'M03'] },
  { title: '作品', kinds: ['WORK'], keys: ['M13', 'M14'], defaults: ['M13', 'M14'] },
  { title: '展品', kinds: ['TREASURE'], keys: ['M15', 'M16', 'M17', 'M18'], defaults: ['M15', 'M18'] },
  { title: '自行補充', kinds: [], keys: ['M19', 'M20'], defaults: [] },
]
export const metricDescriptions: Record<string, string> = {
  M21: '成功新增一筆探索紀錄時，依建立當下時間計入目標；重試、編輯與刪除不會重複計算。',
  M01: '現在符合來源規則的卡片，按卡片去重，單位：張。',
  M02: '本期新保存的相關卡片；把舊卡加入目標不算新增，單位：張。',
  M03: '本期修改過筆記或內文的卡片，同一卡片改多次仍算一張。',
  M04: '有修改卡片內容的日期；不代表修改次數或投入工時。',
  M05: '本期完成今日回顧的相關卡片；目標看板單純翻閱不計入。',
  M06: '再次加入探索材料或作品來源的既有卡片，單位：張。',
  M13: '相關作品目前處於哪個階段，不由文字修改推算進度。',
  M14: '本期標記初版完成的作品，按作品去重，單位：件。',
  M15: '目前關聯的展品，可包含既有成果，單位：件。',
  M16: '本期收入館藏的成果，入藏日期不等於實際完成日期，單位：件。',
  M17: '分別呈現試煉嘗試與通過，不只計算成功結果。',
  M18: '依實際發生日期計算收益紀錄，不限金錢，單位：筆。',
  M19: '之後可手動填寫站外活動，自訂行為與衡量單位。',
  M20: '之後可分項評估有效性、進展與方向認同，不合成總分。',
}
