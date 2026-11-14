# ChemManager ERP — Design System

The interface is built from one stylesheet foundation (`app.css`) plus two theme files
(`theme-light.css`, `theme-dark.css`). **Every colour is a token declared in the theme files**;
`app.css` only ever refers to a token name, so a theme is a variable swap and no screen can
smuggle in a literal colour.

```
app.css          structure, spacing, typography, component rules  (uses -c-* tokens only)
theme-light.css  the light palette, declared on .root
theme-dark.css   the same token names with the dark values
contrast-pairs.txt  the contrast contract the two gates below enforce
```

`ThemeManager.apply(scene)` sets `scene.getStylesheets()` to `[app.css, theme-active.css]` and
the popups created in code (notification panel, toasts) reuse `ThemeManager.activeStylesheets()`
so they inherit the active palette.

Two gates keep this document honest, and both run before a push:

* `tools/ui/checkDesign.py` — token parity between the themes, the contrast contract, no colour
  literal in `app.css`, no dangling token, the 11 px type floor and this table's values;
* `DesignSystemTest` (JUnit, desktop job) — reads the same files from the classpath and fails the
  build when a pair drops below its threshold.

## 1. Colour tokens

Semantic colours are only ever used through `StatusBadge` and the chart factory, so a status
looks identical in every screen: `PAID`/`APPROVED`/`RELEASED` → success, `PARTIAL`/`PENDING`/
`QUARANTINE` → warning, `REJECTED`/`EXPIRED`/`CANCELLED` → danger, `DRAFT` → info, `CLOSED` →
neutral. A colour that has to carry white text is a **fill** token (`-c-fill-*`), never the
text token of the same name: on the dark theme the text tokens are light, and light text on a
light fill is unreadable.

### Surfaces and text

| Token | Light | Dark |
| --- | --- | --- |
| `-c-bg` | `#f4f6fb` | `#0a111f` |
| `-c-surface` | `#ffffff` | `#111a2b` |
| `-c-surface-alt` | `#f1f4fa` | `#1a2438` |
| `-c-border` | `#e3e8f2` | `#23304a` |
| `-c-border-subtle` | `#eef1f7` | `#1b2740` |
| `-c-border-strong` | `#cfd7e6` | `#33425e` |
| `-c-text` | `#0f172a` | `#e7edf7` |
| `-c-text-muted` | `#57647a` | `#a3b2c7` |
| `-c-text-faint` | `#5b6b80` | `#8296ad` |
| `-c-hover` | `#eef2f9` | `#17233a` |
| `-c-row-alt` | `#fbfcfe` | `#131d2f` |

### Brand, primary and focus

| Token | Light | Dark |
| --- | --- | --- |
| `-c-primary` | `#1d4ed8` | `#7db3ff` |
| `-c-primary-dark` | `#1e40af` | `#5b9bf7` |
| `-c-primary-soft` | `#e8effd` | `#1c2f52` |
| `-c-primary-soft-faint` | `#f4f8ff` | `#16233c` |
| `-c-primary-border` | `#b9cdf7` | `#2f4a78` |
| `-c-accent` | `#0f766e` | `#2dd4bf` |
| `-c-focus-ring` | `#1d4ed8` | `#a8ccff` |
| `-c-focus-ring-inverse` | `#ffffff` | `#ffffff` |

### Fills that carry white text

| Token | Light | Dark |
| --- | --- | --- |
| `-c-fill-primary` | `#2563eb` | `#2563eb` |
| `-c-fill-primary-dark` | `#1d4ed8` | `#1d4ed8` |
| `-c-fill-success` | `#15803d` | `#15803d` |
| `-c-fill-danger` | `#b91c1c` | `#b91c1c` |
| `-c-fill-warning` | `#b45309` | `#b45309` |
| `-c-fill-info` | `#0369a1` | `#0369a1` |
| `-c-fill-accent` | `#6d28d9` | `#6d28d9` |
| `-c-fill-brand-from` | `#0e7490` | `#0e7490` |
| `-c-fill-brand-to` | `#1d4ed8` | `#1d4ed8` |
| `-c-on-fill` | `#ffffff` | `#ffffff` |

### Semantic status

| Token | Light | Dark |
| --- | --- | --- |
| `-c-success` | `#15803d` | `#4ade80` |
| `-c-success-soft` | `#e6f7ec` | `#14351f` |
| `-c-success-strong` | `#166534` | `#86efac` |
| `-c-warning` | `#d97706` | `#fbbf24` |
| `-c-warning-soft` | `#fef3c7` | `#3a2a12` |
| `-c-warning-strong` | `#b45309` | `#fcd34d` |
| `-c-danger` | `#dc2626` | `#f87171` |
| `-c-danger-soft` | `#fdeaea` | `#3a1a1c` |
| `-c-danger-strong` | `#b91c1c` | `#fca5a5` |
| `-c-info` | `#0284c7` | `#7dd3fc` |
| `-c-info-soft` | `#e3f3fd` | `#122e40` |
| `-c-info-strong` | `#0369a1` | `#a5e3ff` |

### Sidebar

| Token | Light | Dark |
| --- | --- | --- |
| `-c-sidebar` | `linear-gradient(to bottom, -c-sidebar-top 0%, -c-sidebar-base 60%, -c-sidebar-deep 100%)` | `linear-gradient(to bottom, -c-sidebar-top 0%, -c-sidebar-base 60%, -c-sidebar-deep 100%)` |
| `-c-sidebar-top` | `#0e1a2f` | `#070d18` |
| `-c-sidebar-base` | `#101f38` | `#0b1424` |
| `-c-sidebar-deep` | `#0c1729` | `#070d18` |
| `-c-sidebar-border` | `rgba(255, 255, 255, 0.07)` | `rgba(255, 255, 255, 0.06)` |
| `-c-sidebar-hover` | `rgba(255, 255, 255, 0.07)` | `rgba(255, 255, 255, 0.06)` |
| `-c-sidebar-active` | `#1d4ed8` | `#1d4ed8` |
| `-c-sidebar-text` | `#c3cfe0` | `#c3cfe0` |
| `-c-sidebar-text-strong` | `#ffffff` | `#ffffff` |
| `-c-sidebar-muted` | `#9fb0c6` | `#9fb0c6` |
| `-c-sidebar-group` | `#8fa3bd` | `#93a5bc` |
| `-c-scroll-thumb-inverse` | `rgba(255, 255, 255, 0.22)` | `rgba(255, 255, 255, 0.22)` |

### Charts (series order is fixed)

| Token | Light | Dark |
| --- | --- | --- |
| `-c-chart-1` | `#2563eb` | `#60a5fa` |
| `-c-chart-2` | `#0d9488` | `#2dd4bf` |
| `-c-chart-3` | `#b45309` | `#fbbf24` |
| `-c-chart-4` | `#7c3aed` | `#a78bfa` |
| `-c-chart-5` | `#dc2626` | `#f87171` |
| `-c-chart-6` | `#64748b` | `#94a3b8` |
| `-c-chart-1-fill` | `rgba(37, 99, 235, 0.16)` | `rgba(96, 165, 250, 0.18)` |
| `-c-chart-2-fill` | `rgba(13, 148, 136, 0.16)` | `rgba(45, 212, 191, 0.18)` |
| `-c-chart-3-fill` | `rgba(180, 83, 9, 0.16)` | `rgba(251, 191, 36, 0.18)` |
| `-c-chart-4-fill` | `rgba(124, 58, 237, 0.16)` | `rgba(167, 139, 250, 0.18)` |
| `-c-chart-5-fill` | `rgba(220, 38, 38, 0.16)` | `rgba(248, 113, 113, 0.18)` |
| `-c-chart-6-fill` | `rgba(100, 116, 139, 0.16)` | `rgba(148, 163, 184, 0.18)` |

### KPI icon gradients

| Token | Light | Dark |
| --- | --- | --- |
| `-c-kpi-primary-from` | `#2563eb` | `#2563eb` |
| `-c-kpi-primary-to` | `#1d4ed8` | `#1d4ed8` |
| `-c-kpi-success-from` | `#15803d` | `#15803d` |
| `-c-kpi-success-to` | `#166534` | `#166534` |
| `-c-kpi-warning-from` | `#b45309` | `#b45309` |
| `-c-kpi-warning-to` | `#92400e` | `#92400e` |
| `-c-kpi-danger-from` | `#b91c1c` | `#b91c1c` |
| `-c-kpi-danger-to` | `#991b1b` | `#991b1b` |
| `-c-kpi-info-from` | `#0369a1` | `#0369a1` |
| `-c-kpi-info-to` | `#075985` | `#075985` |
| `-c-kpi-accent-from` | `#7c3aed` | `#7c3aed` |
| `-c-kpi-accent-to` | `#6d28d9` | `#6d28d9` |
| `-c-kpi-teal-from` | `#0f766e` | `#0f766e` |
| `-c-kpi-teal-to` | `#115e59` | `#115e59` |

### Toasts

| Token | Light | Dark |
| --- | --- | --- |
| `-c-toast-success` | `#065f46` | `#065f46` |
| `-c-toast-info` | `#1e3a8a` | `#1e3a8a` |
| `-c-toast-warning` | `#92400e` | `#92400e` |
| `-c-toast-danger` | `#7f1d1d` | `#7f1d1d` |

### Progress bars and avatars

| Token | Light | Dark |
| --- | --- | --- |
| `-c-progress-ok-from` | `#15803d` | `#15803d` |
| `-c-progress-ok-to` | `#166534` | `#166534` |
| `-c-progress-warn-from` | `#b45309` | `#b45309` |
| `-c-progress-warn-to` | `#92400e` | `#92400e` |
| `-c-progress-danger-from` | `#b91c1c` | `#b91c1c` |
| `-c-progress-danger-to` | `#991b1b` | `#991b1b` |
| `-c-avatar-from` | `#2563eb` | `#2563eb` |
| `-c-avatar-to` | `#0f766e` | `#0f766e` |

### Login hero

| Token | Light | Dark |
| --- | --- | --- |
| `-c-hero-from` | `#0b1b33` | `#0b1b33` |
| `-c-hero-mid` | `#123a6b` | `#123a6b` |
| `-c-hero-to` | `#0f766e` | `#0f766e` |
| `-c-hero-text` | `#ffffff` | `#ffffff` |
| `-c-hero-text-muted` | `#d7e3f4` | `#cfdcef` |
| `-c-hero-badge-bg` | `rgba(255, 255, 255, 0.16)` | `rgba(255, 255, 255, 0.14)` |

### Elevation, scrollbars and tooltips

| Token | Light | Dark |
| --- | --- | --- |
| `-c-shadow` | `rgba(15, 23, 42, 0.06)` | `rgba(0, 0, 0, 0.35)` |
| `-c-shadow-strong` | `rgba(15, 23, 42, 0.14)` | `rgba(0, 0, 0, 0.55)` |
| `-c-shadow-toast` | `rgba(15, 23, 42, 0.28)` | `rgba(0, 0, 0, 0.65)` |
| `-c-scroll-thumb` | `rgba(100, 116, 139, 0.34)` | `rgba(148, 163, 184, 0.28)` |
| `-c-scroll-thumb-hover` | `rgba(100, 116, 139, 0.55)` | `rgba(148, 163, 184, 0.5)` |
| `-c-tooltip-bg` | `#0f172a` | `#020617` |
| `-c-tooltip-text` | `#f8fafc` | `#e2e8f0` |

### Contrast contract

`src/main/resources/css/contrast-pairs.txt` lists 62 foreground/background pairs with
their minimum ratio: 52 at 4.50 (WCAG 2.2 AA 1.4.3, text) and 10 at 3.00
(1.4.11 non-text and 2.4.13 focus appearance). Both gates evaluate every pair in **both**
themes, so a dark-mode regression cannot hide behind a passing light theme.

## 2. Typography

One nine-step scale, nothing below 11 px:

| Step | Size | Used by |
| --- | --- | --- |
| 1 | 11 px | meta, hints, chart axes, badges, sidebar group labels |
| 2 | 11.5 px | field labels, toolbar buttons, KPI titles |
| 3 | 12.5 px | body text, table cells, menu items, tab labels |
| 4 | 13.5 px | card titles, root size, dialog titles |
| 5 | 15 px | section titles (`h2`), toast icons |
| 6 | 17 px | header title, card headings on landing screens |
| 7 | 20 px | KPI value, login title |
| 8 | 22 px | page title (`h1`) |
| 9 | 27 px | login hero title |

Sizes live in `app.css` only. A controller never calls `setStyle` for typography, padding or
colour — it adds a style class (`text-meta`, `list-row`, `empty-pad`, …). Fonts: the CSS stack
lets JavaFX pick the platform font (`Segoe UI` on Windows, `Noto Sans Arabic`/`Cairo` for Arabic).
Numerals stay Western in Arabic (Algerian business convention) via `ar-DZ-u-nu-latn`.

**Arabic** runs one step larger: `I18n` puts `lang-ar` on the scene root, the root becomes 15 px
and the text-heavy components scale from it in `em`, with 48 px table rows and 42 px nav items.
No letter-spacing, no italics and no uppercase are used for Arabic anywhere.

## 3. Spacing, radius, elevation

* Spacing scale: `4 · 8 · 12 · 16 · 20 · 24 · 32`.
* Card padding 14–18 px, gap between cards 16 px, screen gutter 20–22 px.
* Radius: 14 px cards, 9 px inputs/buttons, 999 px pills/badges.
* Elevation: `-c-shadow` on cards, `-c-shadow-strong` on popovers and dialogs, `-c-shadow-toast`
  on toasts (all three tokens are themed, so dark mode does not glow).
* Sidebar width 268 px expanded / 76 px collapsed (icons only, tooltips kept).

## 4. Components

| Component | Class | Behaviour |
| --- | --- | --- |
| `KpiCard` | `KpiCard(icon, accentStyle)` | title, value, hint, optional trend chip, tooltip; values come from `KpiSet`, never literals |
| `Card` | `Card(titleKey, subtitleKey)` | surface, optional header action, `body`, `bodyBox`, `flush` mode for tables |
| `DataTable<T>` | `new DataTable<>(PageSource, prompt)` | sortable columns, paging footer (`.range-label`), designed empty state, row activation |
| `ChartFactory` | `salesVersusPurchases`, `revenueVersusExpenses`, `productionByMonth`, `categoryPie`, `horizontalRanking` | themed series from `-c-chart-*`, a text summary for screen readers, a second cue (dash pattern + marker shape) on line series |
| `StatusBadge` | `success/warning/danger/info/neutral`, `severity/alert/production/hazard/active(...)` | pill with translated label, accessible text and a text label so colour is never the only cue |
| `FormFields` | `field`, `fieldKey`, `row`, `sectionTitle`, `description`, `error` | label + input + hint + inline error |
| `Dialogs` | `info`, `warning`, `error`, `confirm`, `chooseSaveFile`, `chooseOpenFile` | themed dialogs with owner stage, `Esc` closes |
| `Toasts` | `install(StackPane)`, `success/info/warning/error` | stacked, auto-dismissing, `-c-toast-*` backgrounds |
| `ModulePlaceholder` | `create(module, navigate)` | planned capabilities + the phase that delivers them |
| `Icons` | `glyph(Icon, size)`, `badge(Icon, styleClass, size)`, `node(Icon, styleClass)` | one mapping between the `Icon` enum and FontAwesome glyphs |

Rules: an icon-only control always has a tooltip **and** an accessible name; destructive actions
go through `Dialogs.confirm`; a screen never renders an empty region.

## 5. Layout

```
BorderPane(main-window)
├── left   sidebar (VBox)  : brand, nav ScrollPane (8 groups / 18 modules), version footer
├── top    header (HBox)   : burger, page title + subtitle, spacer, search, bell+badge,
│                            theme, language, user menu (avatar initials + name + role)
├── center StackPane       : contentFxmlHost (module screens) + overlayLayer (toasts)
└── bottom status bar      : data source, API state, last refresh, clock
```

Every grid is a `FlowPane` with wrapping, so the layout reflows on narrow windows and works
from 1280×720 up to full HD without a separate mobile design.

## 6. RTL and localisation rules

1. `I18n.applyTo(root)` sets `NodeOrientation.RIGHT_TO_LEFT` and the `lang-ar` class; JavaFX then
   mirrors HBox/VBox order, table column order and text alignment automatically.
2. Icons that imply direction (chevrons, arrows, back/forward) are wrapped in
   `NodeOrientation.LEFT_TO_RIGHT` so they keep their semantic direction.
3. No text is baked into FXML/CSS: every label comes from the bundle, every number, date and
   amount from `Formatters` (locale aware, `DZD`, `dd/MM/yyyy`).
4. Layouts use logical padding and gaps, never absolute `LayoutX/Y`, so mirroring is safe.
5. Tables keep numbers right-aligned in both orientations (`.numeric-column`).
6. The language switch rebuilds the shell and the current screen in place — no restart.

## 7. Accessibility & quality bar

* Contrast: the contract of section 1 is enforced by both gates, in both themes.
* **Focus is always visible**: a 2 px `-c-focus-ring` ring drawn outside the control on buttons,
  icon buttons, nav items, fields, combo boxes, tabs, table rows, pagination and links; the ring
  uses `-c-focus-ring-inverse` on the dark sidebar and on coloured fills.
* Minimum hit target 28 px; icon buttons 32×32.
* Keyboard: `Tab` follows the visual order, `Enter` submits, `Esc` closes, `Ctrl+F` focuses the
* Every icon-only control sets `accessibleText`; every chart publishes a text summary of its
  series; every badge keeps a text label, so colour is never the only carrier of meaning.
* Every list has a designed empty state; every long operation shows a busy state and cannot be
  triggered twice.

## Where a colour may come from

Only three places hold a colour: `theme-light.css`, `theme-dark.css` and the contrast contract in
`contrast-pairs.txt`. `app.css` refers to tokens and never to a literal, and **no Java or FXML file
carries one either** — `tools/ui/checkDesign.py` and `DesignSystemTest` reject a hex value or a
`-fx-font-size` anywhere under `src/main/java` and `src/main/resources/fxml`, an inline `style=`
attribute in FXML, and any `setStyle` other than the documented table alignment.

Code that has to paint outside the scene graph reads the palette through `ThemePalette`
(`-c-bg` for the window background, `-c-fill-brand-from` / `-c-fill-brand-to` / `-c-on-fill` for the
application icon), and every dialog calls `Dialogs.applyTheme`, because a `Dialog` opens its own
window and inherits neither the stylesheets nor the Arabic typography of the main scene.
