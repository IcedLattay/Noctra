# Analytics Screen Specification

The agreed design for the Noctra analytics redesign. This file wins over
the older "Analytics Redesign" sketch in `SDD_DEVIATIONS.md`.

**Contents:** §1 the six questions the screen answers · §2 the views
(detail block, trend, insight, Last Night) · §3 global behavior rules
· §4 deliberate exclusions · §5 backend feeds per view · §6 UI annex
(tokens, per-component colors, measurements) · §7 locked copy strings
· §8 scroll-to-top button.

## 1. What the screen answers (six questions, nothing else)

1. How did I sleep? → Last Night panel
2. Did I do my habit? → Routine completion
3. Am I on schedule or drifting? → Bedtime pairs
4. Does the routine work for me? → Mechanism insight
5. Am I improving? → Thirty-day trend (the average line shows direction)
6. What now? → Routine tab, flags, nudges (outside this screen)

## 2. The views

### 2.1 Seven-day detail (one control moves all three charts one day per tap)

First week: fixed 7 slots starting at the first eligible night
(onboarding day if onboarding beat its routine window, else the next
day), filling left to right — upcoming slots stay empty but their date
labels show upfront as scaffolding (faint, no slot outlines: clearly
"not yet," never mistakable for missing data). Once history exceeds 7
days the window slides (rolling last 7).

- **Sleep quality line.** One dot per night. Missing nights leave a gap —
  the line never connects across gaps or fills them with zeros.
- **Routine completion.** One cell per night: filled = done, pink =
  missed (a night with no record counts as missed), yellow = still
  pending (a session awaiting its grace-period verdict — never pink).
  No totals or percentages — the cells speak.
  ⚠ IMPLEMENTATION DEPENDENCY: yellow pending requires backlog §8
  (`PENDING` in `DayStatus` + mapping in `buildCompletionStatuses`) —
  it does not exist in code today. Build §8 first; without it, pending
  sessions fall back to pink and this section is unimplementable
  as written.
- **Bedtime pairs.** Target bedtime vs actual sleep onset per night.
  A caption below reads the variability ("bedtimes varied ±42 min"),
  computed from the nights currently displayed only.

### 2.2 Thirty-day trend (own control, moves a week per tap)

Daily score dots plus a faint average line, with a small legend (dot =
"nightly score", pale line = "weekly average"). No headline verdict —
the average line's direction speaks for itself. Own ‹ range › control
moving ±7 days, with a real-date subtitle ("Aug 26 – Sep 24"). Like the
detail block, it grows from the first recorded night and slides once
history exceeds 30 days. Gaps break both lines (neither series crosses
a missing night). The average divides by available scored nights in its
trailing window (minimum 4 to draw); a missing night hosts no anchor
itself but still counts as history for its neighbors. Hidden behind a
"trend unlocks soon" placeholder until ~2 weeks of history exist.

### 2.3 Insight (no arrows, always trailing 30 days from today)

Compares routine nights vs non-routine nights in plain points, with
three outcomes (better / worse / about the same) plus a "need more
data" state below 3 scored nights per group. A verdict needs a clear
gap (5+ points) — smaller differences report "about the same."
Wording shares the trend's vocabulary ("climbing" / "steady" /
"slipping") so the average line reads without math.

### 2.4 Last Night

Latest night's score and details. No window, no navigation. Unchanged.

## 3. Rules for everything

- **Two clocks, never shared.** The 7-day block and the 30-day trend
  each own their range and their own arrows; moving one never moves
  the other.
- **Small steps.** 7-day views move a day (6 of 7 stay on screen);
  30-day moves a week (23 of 30 stay). Never jump a whole month.
- **No calendar frames.** No Monday–Sunday weeks, no month tabs, no
  "Wk 1–4". Date ranges only ("Sep 18 – Sep 24").
- **Read-only charts.** Dots, cells, and pairs take no taps (matches
  current code — only nav arrows and retry are clickable). If drill-down
  is ever wanted, it's new scope, not implied here.
- **Unknowns shown honestly.** Pending = yellow. Missing = gap.
  Nothing is ever scored, counted, or interpolated from unknowns. Days
  before the first recorded night get no slots at all — windows start
  there instead.
- **Fair denominators.** Rates and averages skip pending nights
  everywhere. (Pre-first-data nights need no skipping — they aren't
  shown.)
- **Eligibility.** A night counts only if onboarding completed before
  its routine window closed — and the window itself starts there. Join
  at 11 PM with an 8 PM bedtime → day one never renders; every chart
  starts the next day. Rare by construction (only onboarding day
  itself), but nobody's first mark is for hours that passed before
  they arrived.
- **Session-date attribution (4 AM rule).** Routine sessions starting
  before 4 AM stamp the previous calendar day
  (`RoutineWindowProvider.resolveRoutineSessionDate`), mirroring the
  sleep pipeline's wake-up anchor. A 1 AM routine and its sleep both
  land on the same date — streaks, insight joins, and cells stay
  aligned. No schema change (`session_date` column unchanged, only
  values). Safe: the bedtime picker caps at 2 AM (latest start ~3 AM),
  so no legal start ever falls near the boundary.
- **Bedtime moves marked, history kept.** A move draws a marker line
  (plus optional shading for the adjustment nights). Nothing is ever
  wiped.
- **Chart implementation.** Extend the existing custom views
  (`RoutineCompletionRowView`, `BedtimeAdherenceChartView`) for the
  7-day block; hand-roll one new custom View for the 30-day trend
  (gap-breaking + min-4 averaging + custom legend fight chart
  libraries; ~200 lines, no new dependency).
- **Show early, gate late.** Real content shows from night one (no
  waiting states on the 7-day views); only the 30-day trend waits
  (~2 weeks).
- **Batched loading.** One load, one loading state for the content
  block: a centered purple (`#522ABE`) spinner while the batch
  resolves (same component as the companion Shleepy loader). Unlike
  companion (animation vs network = different clocks), every analytics
  card awaits the same backend round trip, so nothing meaningful
  arrives early — and a single snapshot keeps insight and charts
  mutually consistent. The existing `loadWeek` batch already does
  this; keep it.
- **Motivation without frames.** A "Week N" counter gives the journey
  feeling; no calendar needed. Week 1 starts at the first eligible
  night (same anchor as the windows), not profile creation — the
  journey starts when nights start counting. Falls back to profile
  `created_at` when no eligible night exists yet.

## 4. Left out on purpose

- **Monthly views/tabs** (short stub weeks mislead; resets punish).
- **Four-week bars** (pairs + trend already say it).
- **Standalone progress card** (verdict lives in the average line itself).
- **Percent-framed insights** (points read off the same scale as the line).

## 5. Behind the curtains (backend per view)

All reads go through `AnalyticsViewModel`; no view queries anything directly.

- **Quality line + Last Night:** `sleep_records.composite_score` by
  `session_date`. Last Night = most recent row, ignoring windows.
- **Completion cells:** `routine_sessions.status` by `session_date`
  (COMPLETED filled; anything else decided = missed/pink; PENDING =
  yellow; no row = missed/pink).
- **Bedtime pairs + variability:** `sleep_records.sleep_onset_time` vs
  `user_profiles.target_bedtime`; variability = standard deviation of
  the displayed onsets. Window start and Week N key off the first
  eligible night (profile `created_at` only as fallback).
- **Trend + average:** same composite column, wider range; average =
  trailing-7 mean of scored nights (gaps shrink the divisor, never
  zero-fill).
- **Mechanism insight:** `InsightGenerationUseCase` on trailing-30-day
  records + sessions.
- **Loading/refresh:** single batched load on view creation; no swipe
  refresh (revisit-only data, reload on revisit covers it); offline
  surfaces the existing offline placeholder with retry.

## 6. UI annex (so an implementer never has to guess from pixels)

Tokens (exact, from `colors.xml`): text `#000000`, muted `#83828C`
(`analytics_muted`), cards `#EEEAF9` (`analytics_card_bg`, 20dp radius,
faint outline), completion green `#00A63E` / pink `#D4183D`,
bedtime on-time `#00A63E` / slight `#F0B100` / late `#D4183D` / target
ring `#444444`, score `#7746FF` (`analytics_score`), trend dots
`#8457FF` / average `#D0BEFF` / axis `#C6B8EC`, pending yellow
`#FFC90E`. Quality bands (`quality_*`) unchanged. Overwrite rule: where
a resource already exists for a component (e.g. shared yellows), the
new value wins — write over, don't duplicate. Fonts:
`poppinsbold` headings/labels, `poppinsregular` body. Section labels:
black, ALL CAPS, 13sp, `letterSpacing 0.05`. Screen padding: 20dp
horizontal.

Per-component color map:
1. **Header** — as-is (untouched).
2. **Common** — card backgrounds `#EEEAF9`; nav arrows + text black;
   all chart labels black.
3. **Last Night** — "LAST NIGHT", date, classification, and all four
   data values black; score `#7746FF`; the four labels `#83828C`.
4. **Bedtime adherence** — target dots hollow with `#444444` outline;
   on-time `#00A63E`, slight `#F0B100`, late `#D4183D`; legend labels
   black; chart dates `#83828C`; variability caption black.
5. **Routine completion** — dates `#83828C`; completed `#00A63E`,
   pending `#F0B100`, missed `#D4183D`.
6. **30-day trend** (future build) — axis ticks `#83828C`, axis lines
   `#C6B8EC`, score dots `#8457FF`, average line `#D0BEFF`.
7. **Insight** — bulb icon `#8457FF`, text black.

Per view (wireframe is visual truth; values below bind it):
- Last Night: 48sp bold score; 12–14sp grey stat labels with black
  values; date top-right of card.
- Quality line: dots [MEASURE: diameter from wireframe, ~10dp];
  connectors 2dp `analytics_muted`; y-gridlines at 0/25/50/75/100 faint grey.
- Completion cells: pill cells, 12dp gaps [MEASURE: cell height/width
  ratio from wireframe]; legend dots 8dp + 12sp black labels.
- Bedtime pairs: target = hollow dashed ring `#444444`, actual = filled
  dot [MEASURE: diameters from wireframe]; caption 13sp black below chart.
- Trend: dots 3dp, average 2dp faint; legend swatches + 12sp black
  labels; y 0/25/50/75/100, x first/mid/last dates. Axis lines span
  exactly label-edge to label-edge (top of "100" flush with line end,
  bottom of "0" flush with line start; same idea on x) — tick labels
  never stick out past the line. Plot area minimum 200dp tall so dots
  never read cramped [MEASURE against wireframe density].
- Insight card: light-lavender fill (`noctra_lavender_bg` `#EDE9FB`),
  bulb icon, 14sp dark body (locked strings in §7).
- FAB: 56dp circle, `noctra_purple` `#5C25F0`, white ↑, 16dp from
  bottom nav + screen end.

[MEASURE] items need one human pass with the wireframe open — everything
else is exact above.

## 7. Final copy (locked strings, placeholders in {braces})

- **Mechanism, better:** "Your sleep quality is about {X} points higher
  on nights you complete your routine. Try to keep it up for better
  sleep!"
- **Mechanism, worse:** "Your sleep quality has been higher on nights
  without the routine. One off week doesn't mean much — keep building
  the habit."
- **Mechanism, similar:** "Your sleep quality is similar with and
  without the routine. More consistency may reveal clearer patterns."
- **Mechanism, insufficient data:** "Need more data to generate an
  insight. Keep logging routines and sleep to unlock comparisons."
- **Variability caption:** "Bedtimes varied ±{N} min this week"
  (N = SD of displayed onsets, rounded).
- **Trend placeholder:** "Your 30-day trend unlocks after ~2 weeks of
  nights."
- **Range subtitles:** "{Mon} D – {Mon} D, YYYY" ("Aug 26 – Sep 24,
  2026"); detail header shows its own 7-day range the same way.

## 8. Scroll-to-top button

- Standard `FloatingActionButton` (↑ glyph), bottom-end, 16dp margins
  clear of the bottom nav.
- Appears once scrolled past ~1.5 screens, hides near the top; tap =
  `smoothScrollTo(0)`.
- Driven by the scroll listener (~20 lines); no library, no gesture
  conflicts with the (nonexistent) swipe-refresh here.
