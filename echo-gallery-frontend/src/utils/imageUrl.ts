export const imageUrlError = (value?: string | null): string => {
  const source = value?.trim() ?? ''
  if (!source) return ''
  if (source.length > 2048) return '封面圖片網址不可超過 2048 個字元'
  try {
    const url = new URL(source)
    if (['http:', 'https:'].includes(url.protocol) && url.hostname) return ''
  } catch {
    // 格式不正確時回傳相同的欄位提示。
  }
  return '封面圖片網址僅接受 HTTP 或 HTTPS 網址'
}
