# Noctra — Future Work

Central log of deferred features, hardening tasks, release cleanup, and
deliberate non-goals — each with the **context and reasoning** so a future
implementer (or future-us) knows *why*, not just *what*. Detailed API/screen
specs for already-decided designs live in `SDD_DEVIATIONS.md`; this file is
about what is **not yet done** and **why it waits**.

Conventions: items are roughly ordered by value within each section. When an
item is completed, delete it here and record any spec deltas in
`SDD_DEVIATIONS.md`. `BACKLOG.md` remains the source of truth for active
sprint-scale work.

---

## 1. Deferred Features

### 1.1 Realtime auto-refresh + friend-request push notifications

**Context.** The SDD specifies event-driven updates: the leaderboard
re-renders via Supabase Realtime when a friend finishes a routine, and pushes
fire for incoming/accepted friend requests (deep-linked). The subscription
code exists in `FriendshipRepository`, but `SocialViewModel.init` never
starts it (dead code), and no notification dispatch exists
(`FriendRequestNotificationWorker` unimplemented).

**Why it waits.** Manual refresh fully covers the need today: pull-to-refresh
on leaderboard/friends/requests (spinner capped at 10s) plus reload on
`onResume`. Realtime + FCM is a project of its own (channels, permissions,
deep links, battery).

**When to revisit.** Together, as one update — the two share infrastructure.
Starting points: `FriendshipRepository` realtime helpers,
`SDD_DEVIATIONS.md` §§ "Manual Refresh Instead of Realtime Auto-Refresh",
"Peer Leaderboard — Notifications".

### 1.2 Encouragement feature (send + receive UI)

**Context.** Reaction-sending logic exists, but the UI entry point was lost
when the social screen split into Leaderboard + Friends, and received
reactions were never surfaced. The SDD's heart-animation-on-send is also
unimplemented (Toast only).

**Why it waits.** Needs a home in the new two-screen social IA, plus the
`encouragement_reactions` table from the schema plan (marked LATER in
`BACKLOG.md` §7 — only create it when this feature gets a UI).

**When to revisit.** Needs product input first: where does the send button
live, and where do received cheers appear?

### 1.3 Tonight's completion badge on friend cards

**Context.** SDD wants a per-friend green-star / grey-circle badge for
tonight's routine completion. Cards currently show rank, name, streak only.

**Why it waits.** Requires tonight's completion state for every friend on
every load — a fan-out query (or Realtime) that wasn't worth it pre-launch.
Pairs naturally with §1.1's realtime work.

### 1.4 "My Progress" needs a home

**Context.** The SDD's social screen had a "My Progress" tab; the
Leaderboard/Friends split orphaned it (`SocialViewModel` still computes
`myProgressState`, rendered nowhere).

**Why it waits.** No agreed placement. Candidates: a section on the Profile
screen, or part of the analytics dashboard. Product call.

### 1.5 Per-friend Shleepy avatars (stage + outfit)

**Context.** Friend/request rows currently share one static vector
(`avatar_shleepy.xml`). Showing each friend's real Shleepy needs their stage
*and* outfit: outfit is queryable (`user_profiles.outfit_equipped`), but
friend models carry no stage/XP, and the Lottie-per-row cost was already
judged heavy once (see `SDD_DEVIATIONS.md` §§ "Friend Avatars…", "Friend
Avatars — Static Vector").

**Why it waits.** Blocked on queryable friend XP/stage (ledger reads + RLS
review) and a perf decision (frozen frames vs. static per-stage art).
`ShleepyAvatarView` is kept as the swap-in point.

### 1.6 Routine Home shimmer skeleton

**Context.** `RoutineHomeFragment` line 161 carries `TODO: optional shimmer
skeleton`. Companion + Friends now have in-place/pulse skeletons; Routine
Home still pops in.

**Why it waits.** Cosmetic, lowest priority of the skeleton rollout. Follow
the companion pattern when touched.

### 1.7 Backup rules (`data_extraction_rules.xml`)

**Context.** The auto-backup rules file carries a stock TODO to scope
`<include>`/`<exclude>`. SharedPreferences holds onboarding drafts, routine
cache, and user id — restoring all of it blindly onto a new device can
resurrect stale routine state.

**Why it waits.** Harmless defaults for now; revisit during release
hardening and decide per-key: user id + prefs yes, routine execution cache
probably no.

---

## 2. Data Integrity & Hardening

### 2.1 Unique constraint on `sleep_records(user_id, session_date)`

**Context.** Code-side idempotency (reuse existing id before upsert) works,
but without the DB constraint a race can insert duplicates. Already noted in
`BACKLOG.md` §1 — apply in the Supabase dashboard; no app change needed.

### 2.2 Shop economy guards (unique purchase + non-negative balance + atomic XP)

**Context.** `purchaseItem()` inserts a random-UUID row with no uniqueness on
`(user_id, item_id)` (same outfit buyable twice); the balance check-then-
deduct in `purchaseAndEquip()` and the read-add-write in `addXp()` are both
racy under concurrent writers (`RewardLedgerRepository`, `CompanionViewModel`
`purchaseAndEquip`/`addXp`).

**Why it waits (deliberately).** The only concurrent-writer scenario is two
humans sharing one account (see §4.1) — a violated assumption for a health
app, not a supported flow. If this is ever hardened: unique constraint on
`user_inventory(user_id, item_id)`, `CHECK (token_balance >= 0)`, and an
atomic increment RPC for XP/balance. Do not add client-side locking; it
cannot fix cross-device races.

### 2.3 Empirical device verification (still open)

**Context.** From `BACKLOG.md` §1, never closed: sync one real night per
target companion app (Mi Fitness, Zepp, realme Link, Nothing X) and verify
stages/HR arrive as the parser expects; plus on-device verification of
`READ_HEALTH_DATA_IN_BACKGROUND` for the WorkManager syncs. Scoring
calibration (§5) likewise wants ~1–2 weeks of real data.

**Why it waits.** Requires physical devices + time, not code. Do before any
"works with your watch" claim.

---

## 3. Release Cleanup Checklist

Remove or resolve before any production submission:

- [ ] `MainActivity` lines 73–74: `DebugSettings.setForceRoutineWindow(true)`
      / `setSkipCompletionCheck(true)` — TEMP, force the routine window open
      and skip completion checks.
- [ ] `RoutineHomeViewModel` line 118 area: same `setSkipCompletionCheck(true)`
      call on the home path.
- [ ] Activity TEMP testing flags: `GratitudeJournalingActivityFragment`,
      `GenericTimerActivityFragment`, `BreathingActivityFragment`,
      `AudioscapeActivityFragment` (each marked `TEMPORARY FOR TESTING`).
- [ ] `CompanionUiState.isAnimationLoaded` — unused field (spinner driven
      imperatively); remove or wire up.
- [ ] Dead `empty_state` block in `fragment_social.xml` (never toggled; the
      adapter's `item_friends_empty` row is the live empty state).
- [ ] `FriendshipRepository` realtime `filter` deprecation warnings (2) —
      functional, migrate at convenience.
- [ ] Onboarding edit flow never persists the re-sequence (pre-existing gap
      noted in `SDD_DEVIATIONS.md`) — fix or cut the edit path.
- [ ] Debug panel: access is `BuildConfig.DEBUG`-gated (long-press app
      version in Settings) so it cannot appear in release builds — but
      confirm the `debug_graph` include and `DebugPanelFragment` are
      acceptable to keep in the shipped artifact, including the zero-write
      preview tooling.

---

## 4. Deliberate Non-Goals (decided, with rationale)

### 4.1 Multi-device / shared-account hardening — NO

Two people on one account cannot corrupt auth, leak data across users, or
cause unrecoverable loss (verified against the write paths). Worst case is
that one account's own numbers become inconsistent (lost XP increments,
double-spend to negative tokens, flip-flopping single-row fields, two
bodies' sleep data overwriting each other). All of it stems from violating
one-human-per-account — the correct assumption for a health app. See §2.2
for the cheap fixes if this is ever reconsidered.

### 4.2 Pull-to-refresh on analytics — NO

The dashboard reloads on every view creation, has prev/next week navigation,
and an offline retry path. The only gap is data changing mid-view (a sync
finalizing while the screen is open) — too narrow to justify the gesture.
Revisit only if the screen gains live-updating content.

### 4.3 Realtime leaderboard (for now) — covered by manual refresh

Recorded here so it isn't re-litigated: pull-to-refresh + reload-on-resume
is the standing replacement (see §1.1 for the full revisit plan).
