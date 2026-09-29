# SDD Deviations

Deviations from the Software Design Description (SDD) discovered during implementation. Updated as we go.

---

## 2.3 Peer Leaderboard — Screen Structure

**SDD spec:** Single `SocialFragment` with a `TabLayout` hosting two tabs — "My Progress" and "Friends Leaderboard." Friend management (add friend, accept/decline requests) is accessed via buttons within this screen.

**Decision:** Split into two separate screens per wireframe — a "Leaderboard" screen (ranked list) and a separate "Friends" screen (manage requests, add friend). Two buttons on Profile tab to access each.

**Status:** Both screens implemented. `LeaderboardFragment` via the trophy icon, Friends screen (`SocialFragment`) via the add-friend icon on Profile. Friends screen: back + title bar with "Add by Email" text action (top-right, envelope reuses login's `ic_auth_email`), full-width `#3D1FA3` Friend Requests banner (white circle with `#3D1FA3` people icon, badge capped at "9+", chevron) as a scrolling header item + "YOUR FRIENDS" label inside `FriendAdapter`, friend rows (Shleepy avatar, black name, remove icon with confirmation dialog). The list always renders (banner stays reachable with zero friends). Screen background is an estimated vertical gradient `#EEEAF7` → white (confirm against wireframe).

---

## 2.3 Peer Leaderboard — Missing Features

### Tonight's completion badge on friend cards

**SDD spec:** Each friend card shows rank, display name, streak, and a "tonight's completion badge" — green star if completed tonight, grey circle if not yet.

**Status:** NOT IMPLEMENTED. Cards only show rank, name, and streak.

### "If no friends, show user alone on leaderboard"

**SDD spec:** If no friends have been added, user sees only themselves on the leaderboard.

**Status:** IMPLEMENTED. `FriendshipRepository.getOwnProfile()` feeds the user's data into `LeaderboardRankingUseCase`, which merges it before ranking — so the user always appears. Remaining slots are filled by grey placeholder cards (see below).

### Ranking and pinning behavior

**SDD spec:** Natural position inside the top 10; when ranked outside the top 10, show top 9 friends with the user's card pinned in the 10th position.

**Status:** IMPLEMENTED as specified in `LeaderboardRankingUseCase`.

### Bottom nav hidden on leaderboard screen

**SDD spec:** Bottom navigation bar is hidden on leaderboard/friends screens.

**Status:** IMPLEMENTED. `LeaderboardFragment` is not in `mainTabs` set so bottom nav auto-hides.

### Badge count on Profile tab icon

**SDD spec:** A badge count on the Profile tab icon shows the number of pending incoming friend requests.

**Status:** IMPLEMENTED. `UserProfileFragment.loadPendingRequestCount()` also drives the bottom-nav badge (hidden when zero).

---

## Friend Avatars as Customized Shleepy (design question)

**Want:** each friend row shows that friend's Shleepy with their current wearables, not the cream placeholder.

**How Shleepy works today:** one Lottie animation per evolution stage; wearables are layers inside the animation toggled via opacity from the user's equipped-items map (see `CompanionFragment` / `EvolutionDialogFragment`).

**Options:**
- (a) Paused `LottieAnimationView` per row (frame 0, no animation) with the friend's equipped map applied the same way. No new art needed — but requires reading each friend's equipped inventory + evolution stage (RLS + queries to design).
- (b) Static SVG per wearable combo — combinatorial explosion, not viable.
- (c) Interim: static base-stage PNG (`shleepy_*.png` already exist) without wearables.

**Status:** IMPLEMENTED. `ShleepyAvatarView` (frozen `charged_idle.json` frame 0 + equipped map via the same layer-opacity code as `CompanionFragment`) replaces the cream squares on friend + request rows. Frame is a `#522ABE` circle (clipped via outline); zoom/y-offset tunable via constants in the view. `InventoryRepository.getEquippedItemIds()` batch-reads friends' equipped items (uncached — the repo's single-user cache is strictly own-rows); `SocialViewModel.avatarEquipment` resolves itemIds to `ShopItem`s via the cached catalog after each refresh. Requires the `user_inventory` friend/pending-counterparty SELECT policy.

---

## 2.3 Peer Leaderboard — Notifications

### FriendRequestNotificationWorker

**SDD spec:** Event-driven notification handler triggered by Supabase Realtime. Push notification for new friend requests with deep-link to `FriendRequestsFragment`. Push notification for accepted requests with deep-link to leaderboard.

**Status:** NOT IMPLEMENTED. Realtime subscription code exists in `FriendshipRepository` but no notification dispatch.

---

## 2.3 Peer Leaderboard — Encouragement

### Heart animation on sent encouragement

**SDD spec:** Sent reactions show a brief heart animation on the recipient's card.

**Status:** NOT IMPLEMENTED. Only a Toast message.

---

## "Your Rank" Pill

**SDD spec:** Not mentioned — wireframe-only element. Pill straddles the top edge of the current user's card, centered horizontally, in `#522ABE` (same shade as the card outline).

**Status:** IMPLEMENTED. Always visible on the current user's card, including when alone on the leaderboard.

---

## Leaderboard Card Outlines (wireframe-only)

**SDD spec:** Not mentioned.

**Status:** IMPLEMENTED. Current user's card has a 2dp `#522ABE` outline; friends' cards have a 1dp `#CBCBCB` outline. Real medal icons (18×24dp, rank numeral baked in) stick out over the top-right corner of top-3 cards.

---

## Placeholder Cards for Empty Slots (new UX decision)

**SDD spec:** Not mentioned. SDD only says the user sees themselves alone when they have no friends.

**Status:** IMPLEMENTED. List is padded to 10 cards — empty slots reuse the same item layout with a broken-line `#CBCBCB` outline (4dp dash / 3dp gap) and a lighter `#EBEBEB` fill, content hidden, no shadow — so spacing stays identical whether the leaderboard is full or not.

---

## Scrolling Banner (UX decision)

**SDD spec / wireframe:** Static screen; scroll behavior unspecified.

**Status:** IMPLEMENTED. The "Compete with Friends" banner is a header item (position 0) inside `LeaderboardAdapter`, so it scrolls with the cards. The top bar (back + title) stays fixed. Banner background is an angled gradient `#EEEAF7` (top-left) → `#FEF9E7` (bottom-right).

---

---

## Manual Refresh Instead of Realtime Auto-Refresh

**SDD spec:** Leaderboard re-renders automatically via Supabase Realtime when a friend completes a routine or a request event arrives.

**Decision:** Deferred. The Realtime subscription in `SocialViewModel.init` never starts (dead code — see Temp Code below). Replaced with pull-to-refresh (`SwipeRefreshLayout` on `LeaderboardFragment`, spinner capped at 10s) plus the existing reload-on-`onResume`. Revisit together with push notifications in a future update.

---

## Friends Screen (wireframe)

- Both buttons filled purple (wireframe shows Friend Requests outlined — unified per user decision).
- Badge with pending request count (capped at "9+") on the Friend Requests button. SDD only specifies a badge on the Profile tab icon — this button badge is new.
- Friend cards reuse the leaderboard friend outline (1dp `#CBCBCB`).
- Temp icon still in use until asset arrives: `ic_add_friend` for Add by Email. Remove-friend (`ic_remove_friend`), accept (`ic_check`), and decline (`ic_cross`) SVGs are in.
- Remove Friend confirmation dialog redesigned per wireframe: custom rounded white modal, purple title ("Remove <Name>?"), grey description, red pill "Remove Friend" button (`#E63956`), grey pill "Never Mind" button (`#EBEBEB` with purple text).
- Add-friend success: SDD specifies an in-sheet "Request Sent!" confirmation state, but per user decision success now dismisses the sheet and shows a toast instead. Errors stay as red inline text under the input.

---

## Friend Requests Screen (wireframe, Received tab)

- Same lavender gradient background as Friends screen; white tab bar with `#522ABE` indicator and selected text.
- Request rows reuse the friend outline (1dp `#CBCBCB`): Shleepy avatar, black bold name + muted email, grey circle decline + purple circle accept (real `ic_cross` / `ic_check` SVGs).
- Sent tab built per wireframe (single cancel button); missing-tabs bug fixed (tabs were never added to the `TabLayout`).

---

## Health Connect: One-Shot Grant (decision)

The Grant button fires the request popup exactly once; whatever comes back (all, partial, denied) is final and advances to Summary — the sync pipeline redistributes scoring around actual grants. This also sidesteps a device-verified quirk where re-requests flash and die with an empty result. Repeat management lives in Health Settings (deep link). Row pills are static "Read only" labels (access type); the granted-state screen, Continue logic, and status re-checks were removed.

**Subsequent updates:**
- Pills removed from step 5 cards entirely — no badges on the rows now.
- If all data is already granted when step 5 opens, it skips straight to Summary on arrival (no Grant button shown). Saves the current step so resume is clean.
- Summary now carries a Health Data status card: live per-datum status (Sleep Segments: Granted ✓ / Not shared, Heart Rate: same, Sleep Stages mirrors sleep). Refreshes on every resume so post-grant changes reflect.
- "Manage in Settings" link removed from Summary — just the status rows.

---

## Action Results: StateFlow → SharedFlow (bugfix, SDD-aligned)

**Problem:** `actionResult` was a shared nullable `StateFlow` consumed by 3 collectors (Friends, Requests, bottom sheet). Whoever processed an event first cleared it to null, so the others saw no change and missed it — e.g. the add-friend error only toasted (Friends screen observer) while the bottom sheet never showed its inline text.

**Fix:** Converted to `SharedFlow` with `extraBufferCapacity = 1`, per the SDD's own rule that one-shot events use `SharedFlow`. Every emission now reaches every active collector exactly once; `clearActionResult()` removed.

---

## Insert Pattern: Maps Instead of Models (bugfix)

**Problem:** Our Supabase client uses `explicitNulls = true`, so inserting a model with a null `id` sends `"id": null`, overriding the DB `gen_random_uuid()` default → not-null violation.

**Fix:** Inserts on tables with DB-generated defaults (`friendships`, `encouragement_reactions`) use `mapOf(...)` without the `id` key. Apply the same pattern to any future table with a DB default.

---

## Onboarding — Resume Step Renumbering (bugfix)

**Problem:** resume-step numbering predated the health screens (3 meant Summary), so resumes skipped Pre-flight + Grant.

**Fix:** 3 → Pre-flight, 4 → Grant, 5 → Summary, with step saves on every health exit (continue + both skips). `markOnboardingComplete` stamps 5 (was a stale 4).

---

## Onboarding — Single Graph Source of Truth (bugfix)

**Problem:** `nav_graph.xml` carried a stale inline duplicate of the whole onboarding graph (no health screens/actions), shadowing `onboarding_graph.xml` — Sequencing Confirm crashed, and the edit flow would have too.

**Fix:** inline copy replaced with `<include>`; `editMode` arg ported; edit-mode Confirm pops back to the Routine tab instead of entering onboarding-only health flow. (Pre-existing gap, not fixed: edit flow never persists the re-sequence.)

---

## Onboarding — One-Way Flow (UX decision)

No Back buttons anywhere in onboarding (removed from Library, Sequencing, TEMP health backs); no logout button (not standard in setup funnels). System-back gesture left alone. Resume relies on saved step + drafts.

---

## Onboarding — Draft Persistence (new)

Each step saves its own draft on advance (`draft_bedtime` + `draft_activity_ids` on `user_profiles`, Migration 4) and restores when the shared ViewModel is empty (fresh-process resume). The active routine config is untouched until Summary, which also clears the draft.

---

## Onboarding — Wireframe Deltas Worth Recording

- Step counting excludes the summary (5 steps; SDD-era copy said "of 3"/"of 4").
- Grant rows describe *data* read, not permissions: the Sleep Stages row is display-only (no such permission; mirrors Sleep).
- Skip uses a shared bottom sheet on both health screens (not a system dialog).
- Add-friend success is toast + dismiss (SDD specifies an in-sheet confirmation state).
- Activity details dialog moved from step 3 (Sequencing) to step 2 (Library) — opened via info icon button at top-right of each card (not long-press, not on Sequencing).
- Step 5 cards have no pills — status is shown on Summary instead.
- Summary carries a Health Data status card (live per-datum status with green/grey icons + colored text) — not in SDD.

---

## Temp Code to Remove Before Release

- ~~Leaderboard mock scaffolding + debug logs — removed.~~
- ~~Friends/Requests mock scaffolding — removed.~~
- `FriendshipRepository` realtime `filter` deprecation warnings (2) — still functional, migrate to the new `filter` method when convenient.

---

## Onboarding — Summary → Main Navigation (bugfix)

**SDD spec:** Not addressed — nested navigation graphs weren't considered.

**Problem:** `onboarding_graph` is a nested graph inside `nav_graph`. Nested graphs are isolated — they cannot navigate to destinations in other nested graphs (`main_graph`). Every approach failed: actions defined in `onboarding_graph` pointing to `main_graph` destinations, global actions at root level (NavController still scoped to nested graph), `popBackStack()` to root (root's start destination was still `onboarding_graph`).

**Fix:** Rebuild the entire nav graph from `OnboardingSummaryFragment`. Inflate a fresh `nav_graph`, set `main_graph` as the start destination, and replace `navController.graph`. Same thing `MainActivity` does on startup — no cross-graph navigation needed.

---

## Analytics Redesign — Monthly Approach Superseded

**Prior work:** A Weekly/Monthly toggle analytics screen was built on `feature/monthly-analytics` (adds `MonthlyReportFragment`, `MonthlyReportViewModel`, `MonthlyInsightGenerationUseCase`, four-week adherence bars, a month selector, and JSON report export).

**Superseded by:** `ANALYTICS_SPEC.md` — the agreed analytics redesign. Per its §4 ("Left out on purpose"), the following are **explicitly excluded** and are being removed on `feature/revamped-analytics`:

- Monthly views/tabs — short stub weeks mislead; resets punish
- Four-week bars — pairs + trend already convey it
- Standalone progress card — the verdict lives in the trend's average line
- Percent-framed insights — points read off the same scale as the line

**Also inverted by the spec:** calendar frames (Mon–Sun weeks, month tabs, "Wk 1–4") are banned in favour of plain date ranges; navigation becomes two independent clocks (7-day block moves ±1 day, 30-day trend moves ±7 days) instead of a weekly/monthly mode switch.

**Status:** Monthly implementation reverted. The revamped build is tracked in `feature/revamped-analytics`, documented against `ANALYTICS_SPEC.md` section by section.

---

## Analytics Redesign — Phase 1 Design Tokens

**Spec:** `ANALYTICS_SPEC.md` §6 (UI annex), with the "Overwrite rule" — where a resource already exists for a component, the new value wins; write over, don't duplicate.

**Overwrite blast radius — checked, zero outside analytics.** Before overwriting, every consumer of the six affected colors was traced. `completion_green`, `completion_pink`, `adherence_target`, `adherence_on_time`, `adherence_slight_delay` and `adherence_connector` are referenced only by `BedtimeAdherenceChartView`, `RoutineCompletionRowView`, and the analytics `legend_dot_*` drawables. So the spec's global overwrite is safe here and does not recolour companion, profile, or shop UI.

**Overwritten:** `completion_green` `#7BC97D`→`#00A63E` · `completion_pink` `#F08FA1`→`#D4183D` · `adherence_on_time` `#2E9F66`→`#00A63E` · `adherence_slight_delay` `#E8A33D`→`#F0B100` · `adherence_target` `#A78BFA`→`#444444` (hollow ring) · `adherence_no_data` `#1A1342`→`#83828C` (chart dates are muted) · `adherence_connector` `#A78BFA`→`#83828C`.

**Added:** `analytics_card_bg` `#EEEAF9` · `analytics_score` `#7746FF` · `analytics_muted` `#83828C` · `analytics_trend_dot` `#8457FF` · `analytics_trend_avg` `#D0BEFF` · `analytics_axis` `#C6B8EC` · `analytics_card_outline` `#E2DCF3` (faint card stroke — value not given in spec, inferred).

**Two judgement calls:**

- **Pending yellow not tokenised.** The spec names `analytics_pending` `#FFC90E` but also names "shared yellows" as the example of what *not* to duplicate. `noctra_health_yellow` is already exactly `#FFC90E` (used by one health-status dot), so pending reuses it rather than adding a second name for one value. Phase 4d must tint pending cells with `@color/noctra_health_yellow`.
- **`adherence_connector` `#83828C` inferred.** The spec gives no connector colour for the bedtime-pairs chart. It previously was lavender `#A78BFA`, which would have clashed against the new `#444444` target ring, so it was moved to the muted neutral.

**Resolved — section headings follow the wireframe:** spec §6 describes section labels as "black, ALL CAPS, 13sp, `letterSpacing 0.05`", but `NEW_ANALYTICS_UI.png` renders them in title case at roughly 18sp ("Sleep Score", "Bedtime Adherence", "Routine Completion", "Insights"). Confirmed with the user: the wireframe wins, per the spec's own "wireframe is visual truth" precedence. `AnalyticsSectionHeading` (18sp black bold, Poppins) is the style the dashboard consumes. `AnalyticsSectionLabel` is retained only as the literal §6 definition; if nothing ends up using it by Phase 8, delete it rather than leave two near-identical heading styles to confuse the next reader.

---

## Analytics Redesign — Phase 2 Data Layer

**Spec:** `ANALYTICS_SPEC.md` §5 (backend per view) and §3 (global rules).

**Migration 5 added.** The spec's Eligibility rule and its window anchoring both needed columns `user_profiles` did not carry:
- `onboarding_completed_at TIMESTAMPTZ` — stamped by `markOnboardingComplete`. Required to exclude a night whose routine window closed before onboarding finished.
- `created_at TIMESTAMPTZ DEFAULT now()` (`IF NOT EXISTS`) — the §3 fallback anchor when no `sleep_records`/`routine_sessions` rows exist yet.

Both are nullable, so no insert default is overridden and no backfill is required. The model mirrors them as nullable so rows predating the migration still decode.

**Three judgement calls:**

- **Unknown onboarding timestamp means "everything eligible."** `resolveEligibilityStart()` returns null when neither `onboarding_completed_at` nor `created_at` is readable, and null is read downstream as *treat every night as eligible*. Hiding real historical data is a worse failure than showing a possibly-unfair mark, and the spec itself calls the case "rare by construction."
- **`DayStatus.INCOMPLETE` and `NO_DATA` were removed, not aliased.** A first attempt kept them as computed properties returning `MISSED`. That would make `when (status) { DayStatus.NO_DATA -> … }` silently match `MISSED` — precisely the confusion the four-state model exists to prevent. `NO_DATA` grey is now free for `INELIGIBLE`, which is the honest use of it: a night the user could not have attempted should not read as a failure.
- **Variability uses the mean of the displayed onsets**, not the target bedtime as the mean, because the spec defines it as the standard deviation of the displayed onsets. This also means it does not depend on an in-flight target-bedtime read. With a target 20 min from the mean this yields the same number, so the SDD-era intent is preserved.

**Not yet consumed:** `trendPoints`, `trendAverages`, `hasEnoughForTrend` and `weekCounter` are computed and exposed but have no UI until Phases 4–5. The detail block still renders through the legacy `SleepQualityChartConfig` / `BedtimeAdherenceChartView`, which still assume Mon–Sun day labels — Phase 4 replaces their day labelling with real dates.

---

## Updates

*Add new deviations here as they are discovered.*
