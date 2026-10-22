import MarkdownIt from 'markdown-it'
// 禁用原始 HTML；網址交由解析器檢查，避免預覽執行來源中的程式。
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: false })
export const renderWork = (body: string) => markdown.render(body)
export const workDocument = (title: string, body: string) => `# ${title.replace(/[\r\n]/g, ' ')}\n\n${body}`
export function plainWork(title: string, body: string) {
  const container = document.createElement('div')
  container.innerHTML = renderWork(workDocument(title, body))
  container.querySelectorAll('br').forEach(n => n.replaceWith('\n'))
  container.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,pre,blockquote').forEach(n => n.append('\n'))
  container.querySelectorAll('img').forEach(n => n.replaceWith(n.alt ? `${n.alt} (${n.src})` : n.src))
  return container.textContent?.trim() ?? ''
}
export function downloadWork(title: string, body: string) {
  const url = URL.createObjectURL(new Blob([workDocument(title, body)], { type: 'text/markdown;charset=utf-8' }))
  const a = document.createElement('a'); a.href = url; a.download = `${title.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 100) || '作品'}.md`
  a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
