import { describe, expect, it } from 'vitest'
import { Schema } from '@milkdown/kit/prose/model'
import { EditorState, TextSelection } from '@milkdown/kit/prose/state'
import { compatibleWorkMarkdown, workMatches, workNavigationKey, workNavigationPlugin, workOutline } from './workEditorNavigation'

const schema = new Schema({ nodes: {
  doc: { content: 'block+' }, text: { group: 'inline' },
  paragraph: { content: 'inline*', group: 'block' },
  heading: { content: 'inline*', group: 'block', attrs: { level: { default: 1 } } },
}, marks: { strong: {} } })
describe('作品長文導航與相容性', () => {
  it('搜尋能跨越格式邊界，且不把不同段落拼成同一句', () => {
    const doc = schema.node('doc', null, [schema.node('paragraph', null, [schema.text('前段'), schema.text('粗體', [schema.mark('strong')]), schema.text('後段')]), schema.node('paragraph', null, [schema.text('第二段')])])
    expect(workMatches(doc, '段粗體後')).toEqual([{ from: 2, to: 6 }])
    expect(workMatches(doc, '後段第二')).toEqual([])
  })
  it('大量重複文字限制搜尋裝飾數量，標題提供精確位置與層級', () => {
    const doc = schema.node('doc', null, [schema.node('heading', { level: 2 }, [schema.text('小節')]), schema.node('paragraph', null, [schema.text('字'.repeat(3000))])])
    expect(workOutline(doc)).toEqual([{ pos: 0, level: 2, text: '小節' }])
    expect(workMatches(doc, '字')).toHaveLength(1000)
  })
  it('接受等價 Markdown 語法，拒絕文字、網址或格式遺失', () => {
    expect(compatibleWorkMarkdown('**粗體**', '__粗體__\n')).toBe(true)
    expect(compatibleWorkMarkdown('原始內容', '內容')).toBe(false)
    expect(compatibleWorkMarkdown('[來源](https://a.example)', '[來源](https://b.example)')).toBe(false)
    expect(compatibleWorkMarkdown('*斜體*', '斜體')).toBe(false)
  })
  it('巢狀標題收合不重複隱藏區塊，展開父標題保留子節狀態，跳轉會展開目標', () => {
    const doc = schema.node('doc', null, [
      schema.node('heading', { level: 1 }, [schema.text('第一節')]),
      schema.node('paragraph', null, [schema.text('前言')]),
      schema.node('heading', { level: 2 }, [schema.text('子節')]),
      schema.node('paragraph', null, [schema.text('子節內容')]),
      schema.node('heading', { level: 1 }, [schema.text('第二節')]),
      schema.node('paragraph', null, [schema.text('結語')]),
    ])
    const positions: number[] = []
    doc.forEach((_node, pos) => positions.push(pos))
    let state = EditorState.create({ doc, plugins: [workNavigationPlugin()] })
    const toggle = (pos: number) => { state = state.apply(state.tr.setMeta(workNavigationKey, { toggle: pos })) }
    const hidden = () => workNavigationKey.getState(state)!.decorations.find().filter(decoration => decoration.to > decoration.from).map(decoration => decoration.from)
    toggle(positions[2]!)
    expect(hidden()).toEqual([positions[3]])
    toggle(positions[0]!)
    expect(hidden()).toEqual([positions[1], positions[2], positions[3]])
    toggle(positions[0]!)
    expect(hidden()).toEqual([positions[3]])
    state = state.apply(state.tr.setSelection(TextSelection.create(state.doc, positions[3]! + 1)))
    expect(hidden()).toEqual([])
    expect(state.doc.eq(doc)).toBe(true)
  })
})
