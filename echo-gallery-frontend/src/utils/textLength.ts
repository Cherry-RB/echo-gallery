/**
 * 以 Unicode code point 計數，避免 emoji 被 UTF-16 的代理對拆成兩個字元。
 */
export const getTextLength = (value?: string | null) => Array.from(value ?? '').length

export const trimToTextLength = (value: string, maximum: number) =>
  Array.from(value).slice(0, maximum).join('')
