# Echo Gallery Design System

> 本文件是 Echo Gallery UI 開發的長期 source of truth。新增或修改介面前，先判斷它應套用的既有 grammar；不要以單一畫面方便為由建立新的 layout 規則。

## 目的與判斷原則

Echo Gallery 以共享的 visual grammar 維持一致性，而不是讓所有畫面長得完全相同。

- 相同語意，使用相同設計規則。
- 不同語意，可以有有意識的差異。
- 每一項差異都必須有 semantic 或 UX 理由。
- spacing、typography、surface 與 responsive 行為優先使用本文件的既有規則。
- 不以機械式 token 替換掩蓋舊 workaround；先判斷 CSS 的語意。

## Source of truth 與 reference implementation

優先順序如下：已人工驗收的 reference implementation、本文、Dialog baseline `ac2ffd4`、既有舊頁面、個人設計判斷。若文字規範與已驗收畫面衝突，修正文件，不改變 reference 的 render。

### Page / surface references

| Grammar | Reference |
| --- | --- |
| Collection | `src/views/boards/TodayBoard.vue`、`src/components/Board-Flex.vue` |
| Persistent filter rail + result workspace | `src/views/SearchView.vue`、`src/views/center/TagCenter.vue` |
| Workspace | `src/views/work/WorkList.vue` |
| Detail | `src/views/CardDetail.vue` |

### Dialog references

| Pattern | Reference |
| --- | --- |
| Compact form | `src/components/work/WorkMaterialManager.vue` 的「編輯素材備註」 |
| Form | `src/views/work/WorkList.vue` 的「發起議題」 |
| Large form | `src/views/CardDetail.vue` 的「編輯卡片」 |
| Picker / workspace | `src/components/CardPickerDialog.vue` |
| Content-heavy | `src/views/experiment/ExperimentDetail.vue` 的「整理探索紀錄」 |

## Tokens

Tokens 定義於 `src/style.css`。component 不得自創無意義的 spacing、radius 或 typography 數值。

### Spacing

| Token | Value | Use |
| --- | --- | --- |
| `--space-2xs` | 4px | icon / inline micro gap |
| `--space-xs` | 8px | inline gap、短標題間距 |
| `--space-sm` | 12px | related controls、field-adjacent gap |
| `--space-md` | 16px | component gap、collection card padding |
| `--space-lg` | 24px | section gap、panel padding、desktop page gutter |
| `--space-xl` | 32px | major section / empty state padding |
| `--space-2xl` | 40px | rare large separation |
| `--space-3xl` | 48px | detail reading terminal spacing |

`--space-1` through `--space-6` are legacy aliases only; new code uses semantic token names above.

### Typography

| Role | Size | Weight | Line height | Use |
| --- | ---: | ---: | ---: | --- |
| Page title | 28px (mobile 24px) | 600 | 38px | page identity |
| Section title | 18px | 600 | 27px | section heading、dialog title |
| Card title | 17px | 600 | 26px | collection / summary card title |
| Body | 15px | 400 | 26px | reading content |
| UI label | 14px | 400 | 21px | inputs、buttons、ordinary UI |
| Secondary | 14px | 400 | 21px | descriptions and supporting UI |
| Metadata | 12px | 500 | 18px | date、status、helper、counter |

Aliases such as `--type-caption` and `--type-meta` preserve existing Element Plus-facing code, but resolve to the roles above.

### Radius and widths

| Token / rule | Value / behavior |
| --- | --- |
| `--radius-sm` | 6px: compact controls / utility treatment |
| `--radius-md` | 8px: cards, panels, inputs where a custom surface is needed |
| `--radius-lg` | 12px: large composed surface only |
| `--content-max-width` | 1280px contained page maximum |
| `--reading-max-width` | 760px readable long-form content |
| Page gutter | desktop 24px; mobile 16px |
| Workspace padding | desktop 16px; mobile 12px |
| Panel padding | desktop 24px; mobile 16px |

## Page shells

Shell is layout grammar, never a content category. A new Dashboard, Garden or analysis page must first use one of these shells.

### Auth Shell

For Login and Register only: a centered, single-purpose authentication surface with fluid narrow width. `Logout` is an action / redirect, not an Auth page.

### Collection Shell

Structure: `PageHeader → optional Toolbar / FilterToolbar → collection workspace → grid/list/masonry or empty state`.

- Use for Today, All Cards, Search and their views.
- Header-to-workspace separation uses `--space-lg`; card grids use their collection-specific layout but shared card typography and padding.
- Complex filters are a toolbar, not another page shell or a stack of decorative cards.
- Mobile lets actions wrap and collections become a single column as needed.

#### Persistent filter rail + result workspace

Use this Collection Shell variant when people must keep query or selection controls visible while inspecting a card collection.

Structure: `filter / selection rail → result toolbar → grey result canvas → card grid → pagination`.

- The rail owns draft query or selection controls. Its fields remain feature-specific; do not force tag selection and multi-criteria search into one generic form component.
- The result toolbar owns applied-state context, result count and presentation controls. It must distinguish draft conditions from the conditions that produced the current results.
- Sorting is a presentation control: it may apply immediately without submitting unrelated draft filters.
- The result canvas is the collection scroll owner. Cards do not acquire independent scrollbars merely to fit the viewport.
- Pagination belongs at the end of the grey result canvas, desktop right-aligned, with current page, total pages and page size visible. It is not a separate white footer surface.
- `SearchView.vue` and `TagCenter.vue` are verified references for this grammar. Their rails share layout rules, not business-state components.
- On mobile, the rail stacks before the result workspace; result toolbar controls wrap without hiding result context.

### Workspace Shell

Structure: `PageHeader → workspace canvas → semantic sections / panels`.

- Use for Works, Experiments, Overview, Return Overview and management interfaces.
- Aggregate data does not create a separate page shell. Overview and Return Overview follow the same Workspace Shell and page rhythm as Work List; only their internal data geometry differs.
- The canvas may be page-background without becoming an extra card. Panels only mark meaningful independently actionable or readable groups.
- Management-specific split panes are allowed when their work requires concurrent context.

### Detail Shell

Structure: `detail navigation → primary content → secondary context / actions`.

- Use for Card, Work and Experiment detail.
- Long reading content uses `--reading-max-width`; details may use an adjacent property/context area where it has a distinct semantic purpose.
- On mobile, split layouts stack; do not preserve desktop side columns by shrinking them.

## Surface taxonomy

| Surface | Purpose | Treatment |
| --- | --- | --- |
| Workspace canvas | groups a page's working area | page background and workspace padding; no automatic border |
| Content panel | independently readable/actionable section | `--panel-padding`, `--radius-md`, border, normal background |
| Collection card | repeated card in a collection | 16px padding, `--radius-md`, border, normal background |
| Summary card | concise status / decision summary | panel padding, `--radius-md`, border; may carry semantic colour |
| Utility row | compact control or selection state | no independent panel unless it is itself a state boundary |
| Stat unit | a value plus label within a summary | no nested card; separated by grid / divider only |

Every background, border and padding layer must name its semantic boundary. Do not build `workspace → panel → card → card` merely to create spacing.

## Header, section, toolbar and empty state

- `PageHeader` owns title, description and top-level actions. It has a 24px desktop gap below it; mobile stacks actions beneath copy.
- `SectionHeader` owns eyebrow, title, description and section-local actions. Section body follows at 16px.
- `WorkspaceToolbar` aligns controls at the bottom, wraps controls instead of compressing them, and remains content-specific.
- `FilterToolbar` is a control region, not a card-inside-card. Use a surface only when filters themselves are a separate editing task.
- `AppEmptyState` is used for a collection or panel with no content; it has centered content and no extra nested card.

## Forms

Element Plus form-item rhythm is the default. Do not globally reset label, input or field spacing.

- Use `FormSection` only for a real semantic group, not for every consecutive field.
- A section follows another section at `--space-lg`.
- Intro, helper and counter text use secondary or metadata roles; they do not create a new surface.
- Form actions belong to the owning page or overlay footer, not between ordinary fields.

## Selection lists and content truncation

- 同一流程中的多選清單使用同一套 control family，不混用 native checkbox 與 Element Plus checkbox。
- Selection row 的整列是可辨識的互動單位；checkbox 與多行文字頂端對齊，selected state 使用共用 primary 色彩。
- 名稱、標籤、表單選項等需要完整辨識的內容預設允許換行，不得以固定高度或 `overflow: hidden` 意外裁切。
- `text-overflow: ellipsis` 與 line clamp 只用於明確的 collection preview、摘要或卡片標題預覽；使用時必須有可理解的截斷語意。

## Dialog design system

Dialog visual language follows the baseline Element Plus rhythm preserved by `AppDialog.vue` and the verified references above.

- The caller owns width via a fluid CSS expression such as `min(620px, calc(100vw - 32px))`.
- Width categories are functional references, not tokens: 520 compact note, 560 small form, 620 general form, 680 edit form, 760 content-heavy, 820 large edit, 900 comparison, 980 picker.
- Desktop viewport gutter and max-height follow the baseline `AppDialog`; mobile uses a 12px outer inset and fluid width.
- Do not globally impose body padding, `flex: 1`, `overflow-y: auto`, grey large-form body, or a heavy footer.
- Footer remains a light action boundary; it must not become a third visual panel.
- Scroll ownership is local: natural-height dialogs remain natural; long form bodies scroll when needed; picker/workspace content owns its constrained scroll.
- Pattern labels (`confirmation`, `compact-form`, `form`, `large-form`, `picker`, `workspace`) are functional classification only. They do not prescribe one width, padding or scroll implementation.

## Drawer, popover and MessageBox

- Drawer preserves edge-originated navigation or temporary workspace behavior. It may share tokens, but is not a Dialog variant.
- Drawer body 與其直接內容只能由其中一層管理主要 padding，避免外層與內層重複留白。
- Popover preserves anchor positioning and compact contextual content. Do not force dialog dimensions or scrolling into it.
- Element Plus MessageBox keeps its native confirmation / prompt interaction. Use shared typography and colour roles only where supported; do not recreate it as an AppDialog without a UX need.

## Responsive principles

- Primary breakpoints: 760px for mobile composition, 900px for detail split panes, and 1200px for broad management layouts.
- Mobile page gutter is 16px; workspace padding is 12px; panels use 16px.
- Header actions wrap or stack. Grid and split panes become one column when content can no longer remain readable.
- Dialogs are fluid within the viewport. Long content must have an explicit, local scroll owner.
- A mobile override must preserve information hierarchy; it must not rely on small text or clipped controls to retain a desktop layout.

## Valid exceptions

| Exception | Reason |
| --- | --- |
| Card Detail reading width and property rail | reading and metadata have distinct cognitive roles |
| Work Detail split workspace | issue context, updates and materials are deliberately concurrent |
| Experiment stage colour | stage identity is domain information, not decoration |
| Masonry collection | variable card length requires masonry placement; it still uses Collection Shell spacing |
| Navigation drawer | temporary navigation has edge-overlay interaction |
| Card Picker plain settings rail | a short selection summary must not masquerade as a full-height panel |

Document any future exception beside its component and add it here only when it is reusable or materially affects architecture.

## AI development checklist

### Before a new page

1. Which existing shell matches its layout grammar?
2. Which existing surface represents each semantic boundary?
3. Can an existing toolbar, section header or empty state be used?
4. Is a new shell truly necessary, or is this a variant / exception?

### Before a new dialog

1. Find the closest verified dialog reference.
2. Choose width by content and use fluid viewport width.
3. Decide scroll ownership from the content type.
4. Keep Element Plus form rhythm unless a semantic group needs an override.
5. Do not add global dialog padding, body flex or body scrolling.

### Before adding CSS

1. Use existing token first.
2. Classify the rule as shared grammar, semantic component style, valid exception or legacy workaround.
3. Do not create a token for a one-off positional correction.
4. Verify desktop and mobile render and check for unnecessary surface nesting.

## Anti-patterns

- A separate page shell for every feature name.
- Arbitrary px values used only to compensate for a local layout mistake.
- Decorative nested cards or borders without a semantic boundary.
- Turning every section into a card.
- A global dialog rule that changes all body padding, height or scrolling.
- Letting a dialog pattern auto-select its visual width.
- Forcing drawer, popover or MessageBox into Dialog grammar.
- Over-componentizing solely for DRY when semantic ownership differs.
- Changing a verified interaction pattern merely to look more uniform.

## Implementation audit

This inventory records the current production UI scope. `Compliant` means the surface uses an existing shell or explicit exception; it does not mean every geometric value is a global token.

| Surface | Status | Grammar / rationale |
| --- | --- | --- |
| Login, Register | Compliant | Auth Shell |
| Logout | Action only | redirect / session action; no page surface |
| Today, All Cards | Compliant | Collection Shell and Masonry exception |
| Card Search | Compliant | Collection Shell persistent filter rail + result workspace |
| Tag Center | Compliant | Persistent filter rail + result workspace; tag selection is its rail-specific semantics |
| Work List | Compliant | Workspace Shell |
| Work Detail | Compliant exception | concurrent detail workspace |
| Experiment List | Compliant | Workspace / collection variant |
| Experiment Detail | Compliant | Detail Shell with stage semantics |
| Overview, Return Overview | Compliant | Workspace Shell shared with Work List; aggregate-stat geometry is internal content only |
| Card Detail / create mode | Compliant exception | Detail Shell with reading width and property rail |
| Gardens and legacy board paths | Redirect only | no independent render surface |
| `Home.vue`, `CardCreate.vue`, `SearchBar.vue`, `CardTextModeFooter.vue` | Legacy / unused | not referenced by current production route or component graph; do not use as a new UI reference |

### Geometry audit

Direct values remain valid only in these categories:

- data geometry: chart columns, tracks, status dots, progress markers and Masonry sizing;
- interaction geometry: icon hit targets, native control affordances and compact picker rows;
- baseline restoration: verified Dialog / Picker density from `ac2ffd4`;
- responsive composition: split-pane thresholds and viewport-constrained content heights.

New code must still use the tokens in this document for ordinary page, panel, card, form and toolbar spacing. A direct value outside these categories requires an adjacent reason or a Design System update.
