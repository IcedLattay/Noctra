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

## Monthly Analytics Implementation

**SDD spec:** Module 4 — Routine Auditing & Analytics, sections 4.1 (Monthly View) and 4.3 (Monthly Progress Overview).

**Decision:** Implemented as a dual-mode AnalyticsDashboardFragment (Weekly/Monthly toggle) with a separate MonthlyReportFragment for the full month report, per SDD sections 4.1 and 4.3.

**Status:** IMPLEMENTED.

**Files created:**
- `AnalyticsViewMode.kt` — Enum for WEEKLY/MONTHLY toggle
- `MonthlyInsightGenerationUseCase.kt` — Week 1 vs Week 4 comparison insight
- `MonthlyReportModel.kt` — Serializable JSON export model
- `MonthlyReportViewModel.kt` — ViewModel for MonthlyReportFragment + export
- `MonthlyReportFragment.kt` — Full monthly report screen with export
- `fragment_monthly_report.xml` — Monthly report layout
- `item_weekly_breakdown_row.xml` — Per-week breakdown row layout
- Multiple drawables: `card_monthly_outline.xml`, `card_monthly_lavender.xml`, `card_export_outline.xml`, `bg_gray_track.xml`, `bg_progress_button.xml`, `ic_download.xml`, `bg_view_toggle_*.xml`, `bg_quality_badge.xml`

**Files modified:**
- `AnalyticsViewModel.kt` — Added monthly mode, month navigation, weekly breakdown computation
- `AnalyticsDashboardFragment.kt` — Added toggle, monthly content views, monthly charts, "View Full Monthly Report" button
- `fragment_analytics_dashboard.xml` — Added toggle, monthly view sections, bar chart, monthly completion, insight card
- `nav_graph.xml` — Added `monthlyReportFragment` destination + action
- `SDD_DEVIATIONS.md` — This entry

**Deviations from SDD:**
- `MonthlyReportFragment` uses manual fragment transaction fallback instead of Navigation Component deep link (bottom nav hiding requirement)
- Export uses Supabase Storage `from("reports").insert(jsonString)` — actual bucket name may need adjustment based on Supabase project config
- `monthly_reports` metadata table insert is wrapped in try/catch since the table may not exist yet

---

## Updates

*Add new deviations here as they are discovered.*
