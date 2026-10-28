import type { Node } from '@milkdown/kit/prose/model'
import { Plugin, PluginKey, TextSelection } from '@milkdown/kit/prose/state'
import { Decoration, DecorationSet } from '@milkdown/kit/prose/view'
import { renderWork } from './workMarkdown'

export type WorkHeading = { pos: number; level: number; text: string }
export type WorkMatch = { from: number; to: number }

export function workOutline(doc: Node): WorkHeading[] {
  const headings: WorkHeading[] = []
  doc.forEach((node, pos) => {
    if (node.type.name === 'heading') headings.push({ pos, level: node.attrs.level, text: node.textContent || '未命名標題' })
  })
  return headings
}

export function workMatches(doc: Node, query: string): WorkMatch[] {
  if (!query) return []
  let text = ''
  const positions: number[] = []
  doc.descendants((node, pos) => {
    if (node.isBlock && text) { text += '\n'; positions.push(-1) }
    if (node.isText) {
      text += node.text
      for (let i = 0; i < node.text!.length; i++) positions.push(pos + i)
    }
  })
  const matches: WorkMatch[] = []
  // 逐筆搜尋保留跨格式的文字位置；限制裝飾數量，避免大量重複文字阻塞編輯。
  for (let index = text.indexOf(query); index >= 0 && matches.length < 1000; index = text.indexOf(query, index + query.length)) {
    const from = positions[index]!, last = positions[index + query.length - 1]!
    if (from >= 0 && last >= 0) matches.push({ from, to: last + 1 })
  }
  return matches
}

export function compatibleWorkMarkdown(original: string, serialized: string): boolean {
  const normalize = (value: string) => {
    const root = document.createElement('div')
    root.innerHTML = renderWork(value)
    root.querySelectorAll('li > p').forEach(p => p.replaceWith(...p.childNodes))
    return root.innerHTML.replace(/>\s+</g, '><').trim()
  }
  return normalize(original) === normalize(serialized)
}

type NavigationState = { folded: Set<number>; query: string; decorations: DecorationSet }
export const workNavigationKey = new PluginKey<NavigationState>('work-navigation')

export function workNavigationPlugin() {
  return new Plugin<NavigationState>({
    key: workNavigationKey,
    state: {
      init: () => ({ folded: new Set(), query: '', decorations: DecorationSet.empty }),
      apply(tr, previous) {
        const meta = tr.getMeta(workNavigationKey) as { toggle?: number; query?: string } | undefined
        if (!tr.docChanged && !meta && (!tr.selectionSet || !previous.folded.size)) return previous
        const folded = new Set([...previous.folded].map(pos => tr.mapping.map(pos)).filter(pos => tr.doc.nodeAt(pos)?.type.name === 'heading'))
        if (meta?.toggle !== undefined) {
          if (folded.has(meta.toggle)) folded.delete(meta.toggle)
          else folded.add(meta.toggle)
        }
        const headings: (WorkHeading & { start: number; end: number })[] = []
        const blocks: { node: Node; pos: number }[] = [], stack: typeof headings = []
        const decorations: Decoration[] = [], collapsedEnds = new Map<number, number>()
        // 一次走訪建立標題區段，避免每個收合標題各自重掃全文。
        tr.doc.forEach((node, pos) => {
          blocks.push({ node, pos })
          if (node.type.name !== 'heading') return
          while (stack.length && stack.at(-1)!.level >= node.attrs.level) stack.pop()!.end = pos
          const heading = { pos, level: node.attrs.level, text: node.textContent || '未命名標題', start: pos + node.nodeSize, end: tr.doc.content.size }
          headings.push(heading); stack.push(heading)
        })
        for (const heading of headings) {
          if (tr.selectionSet && tr.selection.from >= heading.start && tr.selection.from < heading.end) folded.delete(heading.pos)
          const collapsed = folded.has(heading.pos)
          decorations.push(Decoration.widget(heading.pos + 1, view => {
            const button = document.createElement('button')
            button.type = 'button'; button.className = 'work-heading-toggle'
            button.textContent = collapsed ? '▸' : '▾'
            button.setAttribute('aria-label', `${collapsed ? '展開' : '收合'}：${heading.text}`)
            button.setAttribute('aria-expanded', String(!collapsed))
            button.addEventListener('mousedown', event => event.preventDefault())
            button.addEventListener('click', () => {
              if (view.composing) return
              view.dispatch(view.state.tr.setSelection(TextSelection.near(view.state.doc.resolve(heading.pos + 1))).setMeta(workNavigationKey, { toggle: heading.pos }))
              view.focus()
            })
            return button
          }, { key: `heading-${heading.pos}-${collapsed}`, side: -1 }))
          if (collapsed) collapsedEnds.set(heading.pos, heading.end)
        }
        let hiddenUntil = 0
        for (const { node, pos } of blocks) {
          if (pos < hiddenUntil) decorations.push(Decoration.node(pos, pos + node.nodeSize, { class: 'work-folded-block' }))
          hiddenUntil = Math.max(hiddenUntil, collapsedEnds.get(pos) ?? 0)
        }
        const query = meta?.query ?? previous.query
        tr.doc.descendants((node, pos) => {
          if (node.type.name !== 'list_item' || node.attrs.checked === null) return
          decorations.push(Decoration.widget(pos + 1, view => {
            const button = document.createElement('button')
            button.type = 'button'; button.className = 'work-task-check'
            button.textContent = node.attrs.checked ? '☑' : '☐'
            button.setAttribute('role', 'checkbox')
            button.setAttribute('aria-checked', String(node.attrs.checked))
            button.setAttribute('aria-label', `${node.attrs.checked ? '取消完成' : '標記完成'}：${node.textContent}`)
            button.addEventListener('mousedown', event => event.preventDefault())
            button.addEventListener('click', () => {
              if (view.editable && !view.composing) view.dispatch(view.state.tr.setNodeMarkup(pos, undefined, { ...node.attrs, checked: !node.attrs.checked }))
            })
            return button
          }, { key: `task-${pos}-${node.attrs.checked}`, side: -1 }))
        })
        for (const match of workMatches(tr.doc, query)) decorations.push(Decoration.inline(match.from, match.to, { class: 'work-search-match' }))
        return { folded, query, decorations: DecorationSet.create(tr.doc, decorations) }
      },
    },
    props: { decorations: state => workNavigationKey.getState(state)?.decorations },
  })
}
