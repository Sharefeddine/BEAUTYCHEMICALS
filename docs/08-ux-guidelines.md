# ChemManager ERP — UX & UI guidelines

Rules this interface follows, and the evidence behind them. Taken from current dashboard/ERP
research and Arabic RTL guidance, then applied to what JavaFX can actually render.

Sources consulted (November 2026):

* dashboard hierarchy, KPI anatomy, table density, empty states, tabular figures —
  [5of10](https://5of10.com/articles/dashboard-design-best-practices/),
  [Wolfpixel](https://www.wolfpixel.agency/blogs/dashboard-design-best-practices),
  [What If Design](https://whatifdesign.co/feeds/blog/dashboard-ux-best-practices)
* chart choice per question, "admin dashboards: search + one great table" —
  [F1Studioz](https://f1studioz.com/blog/saas-dashboard-design/),
  [EPC Group](https://www.epcgroup.net/power-bi-dashboard-design-best-practices-enterprise-2026)
* RTL mirroring, Arabic typography, mixed-direction text —
  [Creative 4 All](https://creative4all.com/en/blog/arabic-rtl-website-design/),
  [RTL for Arabic apps](https://maniruzzamanjubayer.com/blog/arabic-rtl-interface-design-uae),
  [Gotomena](https://gotomena.com/blog/arabic-ux-rtl-web-design)

---

## 1. Hierarchy: conclusion first, detail on demand

| Rule | Applied in ChemManager |
| --- | --- |
| Three tiers: headline status → trends → records | Dashboard: KPI row → charts/rankings → alert lists + activity feed |
| The most important figure sits top-left of the scanning path | First KPI card is the period revenue, in DZD, with its comparison |
| 3–6 hero KPIs, not fifteen | The dashboard groups its tiles into "activité", "stock", "production", each row readable in one glance |
| A number without a reference is noise | Every KPI card carries its context (period, count, or previous value) instead of a bare figure |
| Admin work is search + table, not charts | Products, raw materials, users: one searchable, paginated table with a stable footer; charts stay on the dashboard |

## 2. Tables carry the work

| Rule | Applied |
| --- | --- |
| Search first | Every `DataTable` opens with a debounced search field; `Ctrl+F` focuses it from anywhere on the screen |
| Sorting and page size are explicit | Page sizes 10/20/50/100, footer shows `first–last of total`, position is preserved on reload |
| Right-aligned numbers | `Column.amount` / `Column.quantity` right-align; text stays left |
| Readable density | Row height, alternating band and hover come from the design system (`app.css`), not per screen |
| Empty rows must not look like data | Rows beyond the dataset blend into the card (last rule of `app.css`, so it wins over `:odd`/`:hover`) |
| Every state is designed | Loading indicator, error placeholder with the reason, empty state (below) |

## 3. Empty states, not blank screens

A table with no rows says **why** it is empty and **what to do next**:

* search returned nothing → `Aucun résultat pour « {0} »` + "clear the search (Esc) or change the
  filters";
* dataset genuinely empty → `Aucun enregistrement à afficher` + the same hint;
* load failure → `error.loading` with the message, plus a toast.

Implemented in `DataTable#updateEmptyState` with the last known total, in FR / AR / EN.

## 4. Charts follow the question

| Question | Chart used |
| --- | --- |
| How has it changed over time? | line/area (`ChartFactory`) with at most 4 series |
| How do categories compare? | horizontal bars, sorted by value |
| What is the composition? | pie only for a handful of slices |
| What is the current value? | KPI card, no chart |

No 3D, no dual axes, no decorative animation on data.

## 5. Arabic (RTL) rules

| Rule | Applied |
| --- | --- |
| Mirror what follows reading order | `I18n` flips the scene to `RTL`: sidebar on the right, mirrored padding, chevrons swapped (`Icons`), dashed progress bars reversed |
| Never mirror what is not directional | Logos, product images, clocks, media controls stay as they are |
| Arabic needs more vertical room | `I18n.applyTo(root)` sets `lang-ar`, the root becomes 15 px, the text-heavy components scale from it in `em`, table rows go to 48 px and nav items to 42 px; no letter-spacing anywhere |
| No fake italics or uppercase for Arabic | Emphasis uses weight and colour |
| Keep numbers, phones, e-mails, codes LTR inside Arabic text | Formatting goes through `Formatters`, which wraps every amount, quantity, percentage and date in a U+2066 … U+2069 isolate while the locale is Arabic, so `1 250,00 DZD` and `dd/MM/yyyy` stay in one piece instead of being reordered by the paragraph direction |
| Western digits in Algeria | `Formatters` pins Latin digits in all three languages (1 250,00 — not ١٢٥٠) |
| Right-aligned numeric cells stay right-aligned | In RTL the table mirroring makes them read from the right, which matches the flow |

## 6. Keyboard and focus

| Shortcut | Effect |
| --- | --- |
| `Ctrl+F` | focus the search field of the current screen (select all) |
| `Esc` | clear the search, then close the dialog |
| `Tab` / `Shift+Tab` | a 2 px `-c-focus-ring` ring, drawn outside the control, on fields, buttons, icon buttons, nav items, combo boxes, tabs, tables and pagination — `-c-focus-ring-inverse` on the dark sidebar and on coloured fills |
| `Enter` on a row | opens the record (`DataTable#onActivate`) |

## 7. Language and theme

* FR / AR / EN switch at runtime without restart; the whole layout mirrors for Arabic and the
  Arabic typography rules of `app.css` switch on with it.
* Light and dark themes are palette swaps in `theme-light.css` / `theme-dark.css`; **no screen
  hardcodes a colour** — `app.css` refers to tokens only, which two gates enforce
  (`tools/ui/checkDesign.py` before a push, `DesignSystemTest` in the desktop job).
* Status is never colour-only: every badge pairs an icon and a label with its colour
  (`StatusBadge`), which keeps the screen readable for colour-blind users and in print.
* Charts never rely on hue alone: series 1 and 2 differ by line dash pattern and marker shape as
  well as by colour, and every chart carries a one-sentence accessible summary of what it plots.
* Icon-only controls get an accessible name from `A11y` (`menuButton`, pagination arrows, …), so a
  screen reader announces them instead of "button".

## 7b. Measured contrast

`src/main/resources/css/contrast-pairs.txt` holds 62 foreground/background pairs with their
minimum ratio, evaluated in both themes: 52 at 4.50 (WCAG 2.2 AA 1.4.3 text) and 10 at 3.00
(1.4.11 non-text, 2.4.13 focus appearance). The audit that produced the earlier version measured
`-c-text-faint` at 2.56:1 on white, warning badges at 2.87:1, danger badges at 4.17:1 and the
sidebar group labels at 3.69:1 — all of them are fixed by the current token values, and the two
gates keep them fixed.

## 8. What JavaFX does not give us (documented, not faked)

| Expectation from web research | Constraint | Decision |
| --- | --- | --- |
| Tabular (monospaced) figures in tables | JavaFX CSS has no `font-feature-settings` | Numbers are right-aligned with a fixed decimal separator, which aligns cents between rows; no font hacks |
| Sticky table headers | `TableView` virtualises rows only, not headers | Table header already sits outside the scrolling body, so it behaves as sticky |
| Skeleton loaders | No CSS animations on JavaFX nodes without a `Timeline` | Progress indicator + placeholder text; skeletons would need hand-written timelines with no measurable gain |
| Touch targets of 44 px | Desktop-only application, mouse + keyboard | Not applicable; row height stays denser on purpose |

## 9. Checklist for a new screen

1. Does it open with the primary action and a search field?
2. Does every table have loading, error and empty states?
3. Are numbers right-aligned, dates localised, currency in DZD?
4. Does it work in Arabic (mirrored) and in both themes?
5. Are all strings in the three bundles (parity test enforces it)?
6. Is every button wired to something real — no placeholder actions?
7. Are the keyboard shortcuts (`Ctrl+F`, `Esc`, `Enter`) behaving?
