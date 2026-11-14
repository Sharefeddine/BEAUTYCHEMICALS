# Design revision — styles and UX brought back in line with `devRules`

Pass carried out between Phase 4 and Phase 5, before any new screen was written, because every later
screen inherits whatever the shell decides here. Commit `6861de8`, CI run **43583380839** (desktop
66 tests, API 25 unit + 183 integration).

The audit of the existing interface found six real gaps. The foundation (tokens, theme swap, status
badges, designed empty states, RTL mirroring) was sound and was kept; what follows is what had to
change, with the measurement that justified each fix.

## 1. Focus was invisible

`app.css` set `-fx-focus-color: transparent` on the root. A keyboard user could tab through the whole
application without ever seeing where they were — a WCAG 2.4.7 failure, and a practical problem on a
data-entry screen where one wrong keystroke goes into the wrong field.

**Now:** a 2 px ring, drawn outside the control, on buttons, icon buttons, nav items, text fields,
combo/date/choice boxes, tabs, tables, pagination, links and check/radio boxes. `-c-focus-ring` on
light surfaces, `-c-focus-ring-inverse` on the dark sidebar and on coloured fills.
`DesignSystemUiTest.focusedControlPaintsAVisibleRing` asserts that the ring appears when a control
takes focus, and disappears from the control that lost it.

## 2. Six colour pairs were below the contract

| Pair | Before | After | Contract |
| --- | --- | --- | --- |
| `-c-text-faint` on white | 2.56 | **5.44** | 4.50 (1.4.3) |
| warning badge on `-c-warning-soft` | 2.87 | **4.51** | 4.50 |
| danger badge on `-c-danger-soft` | 4.17 | **5.59** | 4.50 |
| `-c-text-muted` on `-c-surface-alt` | 4.32 | **4.94** | 4.50 |
| sidebar group label on sidebar | 3.69 | **6.74** | 4.50 |
| white on the warning KPI gradient | 3.19 | **7.09** | 4.50 |
| chart stroke on white (teal; lime replaced) | 2.49 / 1.98 | **3.74** minimum | 3.00 (1.4.11) |

`-c-text-faint` was the worst offender: dates, captions and table footnotes were effectively
unreadable at 2.56:1. The fix moved the token to `#5b6b80` rather than making every caption bolder.

The six light-theme series are now blue 5.17, teal 3.74, amber 5.02, violet 5.70, red 4.83 and slate
4.76 against white; the dark palette sits between 6.29 and 10.43 against `-c-surface`. Teal is the
weakest of the six and is only used for bars and pie slices, where the category is also named in the
legend and in the chart's accessible summary. The two line charts never rely on hue at all: the second
series is red (4.83) and carries both a dash array and square markers, against a solid blue line with
round markers.

## 3. Colours lived outside the tokens

Charts, toasts, KPI icons, progress bars, avatars, the brand mark and the login hero carried colour
literals straight in `app.css`; they did not follow the dark theme and could not be audited.

**Now:** every colour in `app.css` is a token, and both theme files declare the same 104 of them.
`tools/ui/checkDesign.py` and `DesignSystemTest.stylesheetOnlyUsesTokens` reject a literal and an
undeclared token. Chart series are `-c-chart-1…6` with `-c-chart-*-fill` companions.

## 4. The type scale was a suggestion

Roughly thirty ad-hoc font sizes, several below 10 px. **Now** nine steps — 11 · 11.5 · 12.5 · 13.5 ·
15 · 17 · 20 · 22 · 27 — and a hard floor of 11 px. The 23 inline `setStyle` calls in the controllers
became style classes (`text-meta`, `text-amount`, `list-row`, `popover-caption`, `activity-icon`,
`empty-pad`, `alert-icon`, `severity-critical`, `mini-bar`, `nav-phase-badge`, `pad-page`, …).

## 5. Arabic typography was documented but not implemented

The design-system doc promised larger Arabic text and taller rows; the code did nothing. `I18n.applyTo`
now sets `lang-ar` on the root: 15 px root size, text-heavy components scale in `em`, table rows 48 px,
nav items 42 px, and no italics or uppercase emphasis (Arabic has neither). `DesignSystemUiTest`
measures the rendered font size before and after switching to Arabic and asserts it grows, then
restores.

## 6. Screen-reader names were missing

Icon-only header controls (menu, bell, theme, language) and the pagination arrows were unlabelled;
badges and charts were silent. `A11y.describe` sets `accessibleText`, badges announce their label, and
every chart publishes a one-sentence summary of what it plots (`chart.a11y.*`, FR/AR/EN).

## Also found and fixed

* **Bidi figures.** In Arabic, `24,8 M DZD` was reordered by the right-to-left paragraph into
  `M DZD 24,8`. `Formatters` now wraps amounts, quantities, percentages and dates in a U+2066 … U+2069
  isolate while the locale is Arabic — invisible characters, correct order. `RtlFormattingTest` covers
  both directions.
* **`FxTasks` poisoned itself.** A closed stage shut the worker pool down for the rest of the JVM, so
  the next sign-in waited 30 s on a rejected task. The pool is now rebuilt after a shutdown
  (`FxTasksTest`); this was a real sign-out/sign-in bug, not only a test-order annoyance.

## Coverage — what the pass actually touched

Every scene loads its styles from the same three stylesheets, so the tokens, the type scale and the
focus ring reach all of them without per-screen work. What did need a check is the code each screen
brings: an inline style, a hardcoded colour or a window built outside the shell would have quietly
opted out. The audit below is exhaustive over `src/main/java` and `src/main/resources/fxml`.

| Surface | Files | State |
| --- | --- | --- |
| Shell — sidebar, header, notifications, profile menu, clock | `main-window.fxml`, `MainWindowController` | tokens only, focus ring, icon buttons named, `lang-ar` on the root, navigation rebuilt on language change |
| Login | `login.fxml`, `LoginController` | tokens only (hero gradient is a token), focus ring, retranslates in place |
| Dashboard (11 KPI cards, 5 charts, ranking, alerts) | `dashboard.fxml`, `DashboardController`, `ChartFactory`, `KpiCard` | 23 inline styles became classes, charts on chart tokens with dash + marker cues, accessible summaries, no inline size left |
| Products, raw materials, formulations | `products.fxml`, `raw-materials.fxml`, `formulations.fxml` + controllers | shared `DataTable` / `StatusBadge` / `Filters` components carry the styling |
| Inventory, purchasing, suppliers | `inventory.fxml`, `purchasing.fxml`, `suppliers.fxml` + controllers | as above; the six dialogs now themed (see below) |
| Users, settings | `users.fxml`, `settings.fxml` + controllers | inline font sizes replaced by `text-tiny` / `text-small`, inline paddings by `settings-column` / `card-pad` / `side-list` |
| Nine planned modules (production, sales, customers, distribution, finance, expenses, employees, reports, safety) | one shared `ModuleShellController` + `ModulePlaceholder` | phase badge, capabilities, no fake data; the honest placeholder is styled once and inherited nine times |
| Dialogs — formulation editor, formulation comparison, stock adjustment, stock transfer, goods receipt, purchase order | six classes | **fixed in this pass**: they added a class but never loaded the theme, so they rendered in the default JavaFX look and stayed left-to-right in Arabic. All six now call `Dialogs.applyTheme`, which sets the stylesheets, the `dialog-pane` class and `lang-ar` |
| Alerts and confirmations | `Dialogs` | same entry point, so warnings and confirmations follow the theme and the language |
| Window background, application icon | `ThemeManager`, `AppIcon` | **fixed in this pass**: two hex literals moved to `ThemePalette`, which reads `-c-bg` and `-c-fill-brand-from/to` from the active theme |

Result: **zero** colour literals in `src/main/java`, **zero** `-fx-font-size` and **zero** `style=`
attributes in FXML, and exactly **one** `setStyle` in the project (the documented table alignment).

## Gates added

| Gate | Where | Fails on |
| --- | --- | --- |
| `tools/ui/checkDesign.py` | CI, before the build | colour literal, undeclared token, missing token in one theme, contrast below the pair's threshold, size below 11 px, size off the scale, `em` outside `.lang-ar`, doc table that no longer matches the theme files |
| `DesignSystemTest` (5 tests) | desktop job | the same contract, plus theme parity and a documented minimum of tokens and rows |
| `DesignSystemUiTest` (3 tests) | desktop job | a focus ring that does not paint or does not move, Arabic typography that does not apply, an icon-only control without a name |
| `checkDesign.py` source rules + `DesignSystemTest.codeCarriesNoColourOfItsOwn` | CI and desktop job | a colour literal or a font size anywhere in `src/main/java` or FXML, an inline `style=` in FXML, a second `setStyle` |
| `DesignSystemTest.themePaletteReadsTheThemeTokens` | desktop job | a token the theme files no longer declare, which would break the window background or the icon |

## Recorded decisions

1. **One `setStyle` is kept**, in `DataTable`, with a comment: JavaFX cannot align a `TableColumn`
   through a style class. Documented in `docs/devRules.md` §4.7 as the single allowed exception.
2. **No new key for the phase badge.** The placeholder badge reuses the existing `phase.expected` key
   with `module.phase()` instead of introducing `module.planned_phase` — one source of truth per
   string.
3. **Contrast pairs live in a data file, not in the check script**, so the contract can be read and
   discussed without reading Python.
4. **Arabic keeps Latin digits** (`ar-DZ` with `nu-latn`), as Algerian business documents do; the
   bidi isolate is what keeps them in order, not an Arabic-Indic digit set.

## What is unchanged

Layout, information hierarchy, navigation model, tables, the designed empty states and the honest
phase-placeholders already followed `docs/08-ux-guidelines.md`. Colour is still never the only status
cue, figures are still right-aligned and tabular, `Ctrl+F` and `Esc` still behave as documented.
