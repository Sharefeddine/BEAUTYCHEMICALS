# ChemManager ERP — Design System

The interface is built from one stylesheet foundation (`app.css`) plus two theme files
(`theme-light.css`, `theme-dark.css`). Every colour is a CSS variable declared on `.root`, so a
theme is a variable swap, not a second stylesheet with duplicated rules.

```
app.css          structure, spacing, typography, component rules  (uses -c-* variables)
theme-light.css  declares the light palette on .root
theme-dark.css   overrides the same variables on .root
```

`ThemeManager.apply(scene)` sets `scene.getStylesheets()` to `[app.css, theme-active.css]` and
the popups created in code (notification panel, toasts) reuse `ThemeManager.activeStylesheets()`
so they inherit the active palette.

---

## 1. Colour tokens

| Token | Light | Dark | Usage |
| --- | --- | --- | --- |
| `-c-bg` | `#f5f7fa` | `#12151a` | application background |
| `-c-surface` | `#ffffff` | `#1b2027` | cards, tables, dialogs |
| `-c-surface-alt` | `#eef1f6` | `#222932` | table headers, hovers, inactive tabs |
| `-c-border` | `#dde3ec` | `#2c333d` | separators, card outline |
| `-c-text` | `#1c2430` | `#e8ecf2` | primary text |
| `-c-text-muted` | `#66748a` | `#93a1b5` | hints, subtitles, meta |
| `-c-primary` | `#0f6fd1` | `#3d94e8` | primary buttons, links, selection |
| `-c-primary-soft` | rgba | rgba | selected nav item, chips |
| `-c-sidebar` | `#10243c` | `#0b1218` | sidebar background (always dark) |
| `-c-sidebar-text` | `#c7d5e6` | `#a9b8c9` | sidebar labels |
| `-c-success` | `#1f9d55` | `#2ecc71` | paid, approved, released |
| `-c-warning` | `#d98b12` | `#f0a92b` | expiring, pending, low stock |
| `-c-danger` | `#d64545` | `#ef5b5b` | overdue, rejected, expired, DELETE |
| `-c-info` | `#2f7fd1` | `#59a5e8` | informational badges, DRAFT |
| `-c-chart-1…6` | blue, teal, amber, violet, red, grey | lighter variants | chart series, always in this order |

Semantic colours are only ever used through `StatusBadge` and the chart factory, so a status
looks identical in every screen: `PAID`/`APPROVED`/`RELEASED` → success, `PARTIAL`/`PENDING`/
`QUARANTINE` → warning, `REJECTED`/`EXPIRED`/`CANCELLED` → danger, `DRAFT` → info, `CLOSED` →
neutral.

## 2. Typography

| Role | Size / weight | Style class |
| --- | --- | --- |
| Page title | 20 px / 600 | `.page-title` |
| Page subtitle | 12.5 px / 400, muted | `.page-subtitle` |
| Card title | 14 px / 600 | `.card-title` |
| Card subtitle | 11.5 px / 400, muted | `.card-subtitle` |
| KPI value | 22 px / 700, tabular figures | `.kpi-value` |
| KPI label | 11.5 px / 500, uppercase, muted, letter-spacing .04em | `.kpi-label` |
| Body / table cell | 12.5 px / 400 | default |
| Meta / hint | 11 px / 400, muted | `.text-muted`, `.field-hint` |
| Sidebar group | 10.5 px / 600, uppercase, letter-spacing .08em | `.nav-group-title` |

Fonts: `Segoe UI` on Windows, `Noto Sans Arabic`/`Dubai` fallback for Arabic; the CSS font
stack lets JavaFX pick the platform default so no font file ships with the application.
Numerals stay Western in Arabic (Algerian business convention) via `ar-DZ-u-nu-latn`.

## 3. Spacing, radius, elevation

* Spacing scale: `4 · 8 · 12 · 16 · 20 · 24 · 32` (`-sp-1 … -sp-7`).
* Card padding 16 px, gap between cards 16 px, screen gutter 20 px.
* Radius: 10 px cards, 8 px inputs/buttons, 999 px pills/badges.
* Elevation: `-shadow-card` (`0 1px 3px rgba(16,36,60,.08)`) on cards, `-shadow-popup`
  (`0 12px 32px rgba(11,18,24,.18)`) on the notification panel and dialogs.
* Sidebar width 236 px (expanded) / 64 px (collapsed, icons only, tooltips kept).

## 4. Components

| Component | Class | Behaviour |
| --- | --- | --- |
| `KpiCard` | `KpiCard(icon, accentStyle)` | title, value, hint, optional trend chip, tooltip; values are set by the controller from `KpiSet` |
| `Card` | `Card(titleKey, subtitleKey)` | white surface, optional header action, `body`, `bodyBox`, `flush` mode for tables |
| `DataTable<T>` | `new DataTable<>(PageSource, prompt)` | sortable columns, paging footer (`table.range`), empty placeholder, row activation (double click), `Column.text/amount/quantity/date/node` renderers |
| `ChartFactory` | `salesVersusPurchases`, `revenueVersusExpenses`, `productionByMonth`, `categoryPie`, `horizontalRanking` | creates a themed chart from `DashboardData` series; identical configuration in every screen |
| `StatusBadge` | `success/warning/danger/info/neutral`, `severity/alert/production/hazard/active(...)` | pill with translated label for a domain code |
| `FormFields` | `field`, `fieldKey`, `row`, `sectionTitle`, `description`, `error` | label + input + hint + inline error |
| `Dialogs` | `info`, `warning`, `error`, `confirm`, `chooseSaveFile`, `chooseOpenFile` | themed dialogs with owner stage |
| `Toasts` | `install(StackPane)`, `success/info/warning/error` | stacked, auto-dismissing notifications inside the content host |
| `ModulePlaceholder` | `create(module, navigate)` | planned capabilities + phase badge for modules not delivered yet |
| `Icons` | `glyph(Icon, size)`, `node(Icon, styleClass)` | single mapping between our `Icon` enum and FontAwesome glyphs |

Rules: an interactive control always has a tooltip when it is icon-only; destructive actions
always go through `Dialogs.confirm`; a screen never renders an empty region — it shows the
translated empty state of `DataTable` or of `Card`.

## 5. Layout

```
BorderPane(main-window)
├── left   sidebar (VBox)  : brand, nav ScrollPane (8 groups / 18 modules), version footer
├── top    header (HBox)   : burger, page title + subtitle, spacer, search, bell+badge,
│                            theme, language, user menu (avatar initials + name + role)
├── center StackPane       : contentFxmlHost (module screens) + overlayLayer (toasts)
└── bottom status bar      : data source, API state, last refresh, clock
```

Dashboard composition: range toolbar → KPI `FlowPane` (5 cards) → charts `FlowPane`
(2–3 cards) → rankings `FlowPane` (3 cards) → alerts `FlowPane` (2 cards) → operations row.
Every grid is a `FlowPane` with wrapping, so the layout reflows on narrow windows and the
application works from 1280×720 up to full HD without a separate mobile design.

## 6. RTL and localisation rules

1. `I18n.setLocale(ar)` sets `NodeOrientation.RIGHT_TO_LEFT` on the scene root; JavaFX then
   mirrors HBox/VBox order, table column order and text alignment automatically.
2. Icons that imply direction (chevrons, arrows, back/forward) are wrapped in
   `NodeOrientation.LEFT_TO_RIGHT` so they keep their semantic direction.
3. No text is baked into FXML/CSS: every label comes from the bundle (`i18n.*`), every number,
   date and amount from `Formatters` (locale aware, `DZD` currency, `dd/MM/yyyy`).
4. Layouts use logical padding (`-fx-padding`, gaps) and never absolute `LayoutX/Y`, so the
   mirroring is safe.
5. Tables keep numbers right-aligned in both orientations (`.numeric-column`).
6. The language switch rebuilds the shell and the current screen in place — no restart.

## 7. Accessibility & quality bar

* Contrast ≥ 4.5:1 for body text in both themes (checked against the surface tokens).
* Minimum hit target 28 px; icon buttons 32×32.
* Keyboard: `Tab` order follows the visual order, `Enter` submits the login form, `Esc`
  closes dialogs, tables support arrow navigation.
* Focus is always visible (`.button:focused` outline in `-c-primary`).
* Every list has a translated empty state; every long operation shows a busy state and cannot
  be triggered twice.
* No colour-only meaning: status pills carry a text label, alerts carry an icon.
