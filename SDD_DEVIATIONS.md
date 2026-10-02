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
- `CompanionUiState.isAnimationLoaded` — unused field (spinner visibility is driven imperatively in `applyIdleAnimation()`); remove or wire up before release.

---

## Onboarding — Summary → Main Navigation (bugfix)

**SDD spec:** Not addressed — nested navigation graphs weren't considered.

**Problem:** `onboarding_graph` is a nested graph inside `nav_graph`. Nested graphs are isolated — they cannot navigate to destinations in other nested graphs (`main_graph`). Every approach failed: actions defined in `onboarding_graph` pointing to `main_graph` destinations, global actions at root level (NavController still scoped to nested graph), `popBackStack()` to root (root's start destination was still `onboarding_graph`).

**Fix:** Rebuild the entire nav graph from `OnboardingSummaryFragment`. Inflate a fresh `nav_graph`, set `main_graph` as the start destination, and replace `navController.graph`. Same thing `MainActivity` does on startup — no cross-graph navigation needed.

---

## Shleepy Customization — Packaged Outfits (design decision)

**SDD spec:** Layered accessory system (hat, outfit, accessory, footwear) with Lottie opacity toggling per layer.

**Decision:** Replaced with packaged outfits — one complete Lottie JSON per outfit per stage. Single `OUTFIT` shop category, no layer opacity. Shop UI shows text-only buttons (outfit names) in a 3-column uniform grid.

**Status:** IMPLEMENTED. `ShleepyAvatarView` loads `shleepy_{stage}_{outfit}.json` by name. `CompanionFragment` swaps entire animation on equip. Smoke overlay view wired up (awaiting animation asset). Friend avatars use same approach (frozen frame 0).

---

## Equipped Outfit Schema Change (design decision)

**SDD spec:** `user_inventory.is_equipped` boolean column tracks which item is equipped per user.

**Decision:** Moved equipped state to `profiles.outfit_equipped` (UUID FK → `shop_items.item_id`). `user_inventory` is now a pure ownership table (no `is_equipped` column). New users get default outfit auto-equipped via a DB trigger (`equip_default_outfit`) that fires `BEFORE INSERT` on `profiles`.

**Why:** "What is this user wearing?" becomes a single-column lookup on `profiles` instead of a filtered scan of `user_inventory`. Eliminates the risk of multiple items being marked equipped simultaneously (was enforced only by app logic). Simplifies friend-avatar queries for the social screens.

**Migration applied:**
1. `ALTER TABLE profiles ADD COLUMN outfit_equipped UUID REFERENCES shop_items(item_id)`
2. `INSERT INTO user_inventory` with default outfit for all existing users
3. `UPDATE profiles SET outfit_equipped = <default_id>` for all existing users
4. `ALTER TABLE user_inventory DROP COLUMN is_equipped`
5. Created `equip_default_outfit()` function + `on_profile_created` trigger

**Status:** IMPLEMENTED. `InventoryRepository` reads/writes `outfit_equipped` on profiles. `UserProfile` model includes `outfitEquipped` field. `CompanionViewModel` and `SocialViewModel` updated.

---

## Updates

*Add new deviations here as they are discovered.*

---

## Shleepy Customization — Shop Item Cards (frontend)

**SDD spec:** Not detailed — shop UI was text-only outfit-name buttons in a 3-column grid (interim state).

**Status:** IMPLEMENTED. Cards now show SVG outfit previews (11 `outfit_*.xml` VectorDrawables in `res/drawable/`, converted from the provided SVGs; `outfit_default.xml` regenerated via Android Studio Vector Asset). `ShopItemAdapter` maps `item.label.lowercase()` → `outfit_{label}` drawable via `getIdentifier()`; cards with no matching drawable hide the preview. Card background is a `layer-list` gradient (white → `#DBD8CE` over a solid `#DBD8CE` base); CardView itself is transparent. Equipped state = same gradient + purple (`noctra_purple`) outline — no checkmark, no solid fill. Unowned items show the price pill as an overlay pinned to the card's bottom-right corner (ConstraintLayout, not in flow), so showing/hiding it never changes card height. No text labels on cards.

---

## Shleepy Customization — Smoke Overlay on Outfit Switch (frontend)

**SDD spec:** Not specified.

**Status:** IMPLEMENTED. `smoke.json` (Lottie) lives in `res/raw/`; `smokeOverlay` view sits inside `shleepyFrame` declared *after* `petAnimationView` so it renders on top. It plays ONLY on actual outfit switches — `applyIdleAnimation(playSmoke)` + `previousOutfitAsset` tracking in `CompanionFragment` (initial load, stage-only changes, and tapped-animation returns pass `false`). Cropping fix worth recording: the 390dp Lottie inside the 220×280dp frame requires `clipChildren="false"` + `clipToPadding="false"` on `shleepyFrame` AND both ancestor layouts (root FrameLayout + `companionRoot`), otherwise parents re-clip the overflow.

---

## Companion Screen — Skeleton Loaders (frontend)

**SDD spec:** Not specified (screen previously popped in all at once).

**Status:** IMPLEMENTED. In-place skeletons that swap 1:1 with the real components (same slots/margins, all above the floor view — an earlier full-screen overlay approach was abandoned because it rendered *behind* the floor): `skeletonTokenPill` (same constraints as `tokenContainer`), `skeletonSpinner` (indeterminate `ProgressBar`, purple `#522ABE`, centered in `shleepyFrame`, nudged 48dp down), `skeletonStageLabel` / `skeletonXpCard` / `skeletonCustomize` (inside `companionPanel` with matching sizes; stage stack pushed down 40dp). Placeholders are solid white (`#FFFFFF`) with a 0.6→1.0 alpha pulse. Loading is independent per component: token/stage/XP/customize swap when `evolutionState` arrives; the spinner hides only when the Lottie file is found and starts playing. Caveats for SDD revision: (1) token swap is gated on `evolutionState != null` as the data-ready signal, not on `isLoading`; (2) `applyIdleAnimation()` only runs inside the `evolutionState` block, so the spinner effectively waits for data first, then the animation file — if an animation JSON is ever missing the spinner spins forever; (3) `CompanionUiState.isAnimationLoaded` was added but is currently unused (spinner driven imperatively) — remove or wire up before release.

---

## Shleepy Customization — Shop Grid Behind Shleepy + Top Fade (frontend)

**SDD spec:** Not specified.

**Status:** IMPLEMENTED. `shleepyFrame` carries `elevation="8dp"` so the shop grid (`shopPanel`) scrolls *behind* Shleepy and the smoke instead of hard-clipping at a visible panel edge. Touches pass through the Lotties to the cards because neither Lottie view is clickable (tap-animation listener removed — see below). First-row position is preserved by a margin/padding compensation pair, currently `shopPanel marginTop="45dp"` + `rvShopItems paddingTop="14dp"` (net 59dp below Shleepy's feet; change both together). A 27dp top fade (`bg_shop_fade_top`, `#D0C0EA` → transparent) overlays the grid via FrameLayout for smooth scroll cropping. Note: `shleepyFrame` itself is transparent, so cards show through around the sheep artwork edges while passing behind — accepted as the intended "sliding behind Shleepy" look.

---

## Shleepy Customization — Tapped Animations Removed (design decision)

**SDD spec:** Tap-triggered Shleepy reactions were assumed (tapped-animation JSONs per outfit per stage).

**Decision:** Removed entirely — no tapped JSONs will be provided. `triggerTappedAnimation()`, the `petAnimationView` click listener, the `isPlayingTappedAnimation` flag/guards, and the now-unused animator imports are deleted from `CompanionFragment`. Tapping Shleepy does nothing.

---

## Companion Layout Constants (frontend reference)

Values tuned during customization work, for the SDD layout spec: `shleepyFrame` marginTop 100dp normal / 16dp customize (Shleepy rises 84dp); `companionFloor` height 340dp normal / 510dp customize, gradient `#937AC2` (bottom) → `#D8C8EF` (top), declared *before* `shleepyFrame` in XML so it renders behind content. Mode transitions animate via a plain `ChangeBounds()` in a `TransitionSet` on the root — lesson learned: do NOT `addTarget()` individual ConstraintLayout children (breaks the animation) and do not drive it with a manual ValueAnimator.

---

## Correction: Profiles Table Name (backend)

The "Equipped Outfit Schema Change" entry above says `profiles` — the actual Supabase table is **`user_profiles`**. Column is `user_profiles.outfit_equipped` (UUID FK → `shop_items.item_id`); trigger `on_profile_created` fires `BEFORE INSERT` on `user_profiles`. Catalog state: 11 outfit rows in `shop_items` (auto-generated UUIDs, single `OUTFIT` category); `user_inventory` holds default-outfit rows for existing users.

---

## Evolution Level-Up — Fullscreen Reworked into Styled Dialog (frontend)

**SDD spec:** Not detailed — implemented as a fullscreen takeover (`Theme_Black_NoTitleBar_Fullscreen`, dark purple bg, pulsing glow, "SHLEEPY IS EVOLVING!" + "Awesome!" button).

**Decision:** Reworked into a standard dialog popup matching the streak-notice styling: transparent window, white 24dp card with 24dp side margins, outfit-aware Shleepy Lottie (190dp) overlapping the card top, poppinsbold 20sp `#16056E` title ("Shleepy just moved up an energy level!"), poppinsregular 14sp `#7B6FA0` centered description, full-width 56dp purple `#5C25F0` pill "Awesome!" button (28dp radius). Description copy phrases levels as states, not labels ("went from feeling drained to waking up… will be full of energy in no time"), with a maxed-out variant at Zen Master. Per-level state phrases live in `EvolutionDialogFragment.stageStates`.

**Plumbing:** `CompanionViewModel` now emits `EvolutionEvent(newStage, oldLevel)` (was just `EvolutionState`) so the dialog knows both levels; `PendingDialog.Evolution(oldLevel, newLevel)`; `EvolutionDialogFragment.newInstance(oldLevel, newLevel)`. Dialog still advances the existing Recap → Notice → Evolution queue.

**Preview tooling (debug only):** "Preview Evolution Screen" button in the debug panel (Section 4) opens the dialog for the next stage with zero writes — no XP added, no ledger touched. Requires a prior Companion-tab visit so outfit data is loaded, otherwise the Shleepy area stays blank. (The older "Trigger Evolution" button still writes +5000 XP.)

---

## Friend Avatars — Static Vector Instead of Lottie + Inventory Reads (design decision)

**Prior state:** `ShleepyAvatarView` rendered a frozen frame-0 Lottie per friend's stage + outfit, fed by batch `user_profiles.outfit_equipped` reads (`InventoryRepository.getEquippedOutfits` → `SocialViewModel.avatarEquipment` → adapter `equipment` maps). Stage was hardcoded to Charged (friend models carry no stage).

**Decision:** Avatars are now a single static vector (`avatar_shleepy.xml`, sheep on `#BBB7C7` circle with a thin baked-in `#BBB7C7` ring; SVG ellipses converted to arc-paths in rotated groups since vectors lack `<ellipse>`). All other-users' outfit reads removed: `getEquippedOutfits()` deleted, `avatarEquipment` flow + `loadAvatarEquipment()` deleted, adapter `equipment` maps deleted, `InventoryRepository`/`ShopRepository` fields removed from `SocialViewModel`. `ShleepyAvatarView` is a bare `FrameLayout` with the vector as its (stretch-to-fill) background — the swap-in point when per-user art returns. Deleted now-unused `bg_avatar_circle_purple.xml`.

**For SDD revision:** friend rows show identical static avatars; per-friend Shleepy (stage + outfit) is deferred until friend stage/XP is queryable.

---

## Friends Screen — Skeleton Loaders + Empty State (frontend)

**SDD spec:** Not specified.

**Status:** IMPLEMENTED per wireframe. Transparent overlay (`skeleton_view`) in `fragment_social.xml` mirroring the real layout 1:1 with identical paddings/margins: two equal 110×28dp top pills (wireframe's narrower pill widened to match), 64dp banner placeholder (matches real 12+40+12 banner), 96×18dp label placeholder (margins 20/12 like "YOUR FRIENDS"), and exactly 3 row cards at 80dp each (16+48+16, like real rows) with 48dp avatar circle + name bar + 24dp action circle positioned as the real internals. Fills: white pills/cards (existing skeleton drawables) + `#E4DFEE` gray for avatar/name/action (`bg_skeleton_inner`, `bg_skeleton_circle`); whole overlay pulses with the shared 0.6→1.0 anim. Shown on initial load only (hidden on first `leaderboardState` emission; swipe refreshes use the `SwipeRefreshLayout` spinner). No overlay background — the real screen bg shows through behind the 1:1 placeholders.

**Empty state:** `FriendAdapter` gains a third view type — a centered muted "No friends yet" row (`item_friends_empty.xml`) under the header when the friend list is empty. (Note: `fragment_social.xml` also contains an older full-screen `empty_state` block that is never toggled — still dead code.)

---


## Analytics � see ANALYTICS_SPEC.md

The analytics requirements live exclusively in ANALYTICS_SPEC.md (authoritative spec: views, windows, navigation, copy, backend, rejected alternatives). The rolling-window redesign notes that previously lived here were superseded by that file.

---

## Audit — Anchor Seeding, Eligibility, Backfill Worker (design decision)

**Gap:** the audit range anchors on `lastSessionDate`/oldest-pending, neither of which exists for fresh accounts — every open audited [today] only, so pre-anchor days were never examined (no rows, no verdicts).

**Changes (all on `feature/auth-sync-polish`):**
- `markOnboardingComplete()` stamps `onboarding_completed_at` (Migration 5); `ensureAuditAnchor()` seeds `lastSessionDate` = onboarding-minus-1 at onboarding end, fill-only (never rewinds — rewinding would re-audit judged dates and double-count streaks/XP).
- `auditDate()` extracted from the audit loop (open path keeps full verdict/write/penalty rules). The backfill worker intentionally does NOT share it — row-writing only (see below). (An `isNightEligible` gate briefly existed; removed — every night from onboarding counts, no exemptions.)
- `AuditBackfillWorker` (daily 22:00): covers [onboarding day, open-audit cap) in ≤30-day chunks with a persisted per-user cursor. Row-writing only — pendings flip straight to MISSED, empty past dates get MISSED rows; no grace re-checks, no ledger folds.

**Full audit behavior (for the record — open path + workers):**
- *Open-path audit (every app open):* bulk-flip PENDING older than 14 days → walk [anchor+1 … today] (anchor = oldest pending, else `lastSessionDate`+1, else today; 14-day cap) → per date: sync sleep from Health Connect → verdict → write/update row → streak fold → save ledger once.
- *Verdicts:* COMPLETED stays; empty past day → MISSED row written; PENDING → 1-hour sleep-onset rule can still COMPLETE it, else >24h → MISSED; today with no row → PENDING, no row.
- *Penalties:* PENDING touches nothing; COMPLETED +1 streak (best updates longest); first MISSED sets warning, consecutive MISSED zeroes streak. >14-day absence zeroes streak + warns on sight, without fabricating rows.
- *Sleep sync per date:* wake-up-anchor window (sessions ending 4 AM–4 PM next day); wide fetch (prior noon → anchor end) with in-code end-inside-anchor filter, because HC matches interval starts; provisional pass ~9 AM (`isPartialData`), finalization ~5 PM overwrites idempotently.
- *Daily worker (9:30 AM):* yesterday empty → MISSED row + same penalty chain. Check-then-insert on both paths prevents duplicates.
- *Backfill worker (10 PM, new):* as above — the only writer covering pre-cap history.

