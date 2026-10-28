import { describe, expect, it } from 'vitest'
import { plainWork, renderWork, workDocument } from './workMarkdown'
describe('作品 Markdown', () => {
  it('勾選清單、刪除線與表格在閱讀及純文字輸出中保留內容', () => {
    const body = '- [x] 已完成\n- [ ] 待處理\n\n~~刪除~~\n\n| 欄位 |\n| --- |\n| 內容 |'
    const html = renderWork(body)
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('checked')
    expect(html).toContain('<s>刪除</s>')
    expect(html).toContain('<table>')
    expect(plainWork('標題', body)).toContain('待處理')
    expect(plainWork('標題', body)).toContain('[x] 已完成')
    expect(plainWork('標題', body)).toContain('[ ] 待處理')
    expect(plainWork('標題', body)).toContain('內容')
  })
  it('支援格式與程式碼並禁止原始 HTML、危險網址', () => {
    const html = renderWork('# 標題\n\n**粗體**\n\n- 清單\n\n> 引用\n\n```js\nalert(1)\n```\n\n<script>alert(1)</script>\n\n[連結](javascript:alert(1))')
    expect(html).toContain('<h1>標題</h1>'); expect(html).toContain('<strong>粗體</strong>')
    expect(html).toContain('<ul>'); expect(html).toContain('<blockquote>'); expect(html).toContain('<code')
    expect(html).not.toContain('<script>'); expect(html).not.toContain('href="javascript:')
  })
  it('純文字去除格式，文件只有標題與正文，圖片保留網址', () => {
    expect(plainWork('標題', '**粗體**\n\n![示意](https://example.com/image.png)')).toContain('粗體')
    expect(plainWork('標題', '**粗體**')).not.toContain('**')
    expect(workDocument('標題', '正文')).toBe('# 標題\n\n正文')
    expect(renderWork('![圖](https://example.com/a.png)')).toContain('src="https://example.com/a.png"')
  })
})
