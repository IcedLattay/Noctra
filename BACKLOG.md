# Noctra — Backlog

**NEXT SESSION**: Merge remote branch into local branch to continue where we left off.

Tracked items from the sleep-data sync design discussion (auditor/syncer flow).

## 1. Health Connect Syncer (steps 2–3 of the audit flow) — IN PROGRESS

The syncer must fetch raw data from Health Connect and store scored sleep records.

- [x] Add Health Connect client dependency
  - `androidx.health.connect:connect-client:1.1.0` in `gradle/libs.versions.toml` + `app/build.gradle.kts`
  - Note: correct coordinates are `androidx.health.connect:connect-client` — the older `androidx.health:health-connect-client` artifact is dead (2022 alpha only)
- [x] Add manifest permissions: `android.permission.health.READ_SLEEP`, `READ_HEART_RATE` + permission rationale wiring (`ACTION_SHOW_PERMISSIONS_RATIONALE` filter on `MainActivity` + `ViewPermissionUsageActivity` alias for Android 14+)
- [x] Implement real Health Connect queries in `SleepSyncManager`:
  - Sleep sessions within the "Wake-Up Anchor" window — now device-local time (was UTC, a latent bug)
  - Heart rate samples over the aggregated sleep interval → nightly average
  - Movement/restlessness → derived from `SleepSessionRecord.stages` per the wake-family spec:
    count `AWAKE`/`AWAKE_IN_BED`/`OUT_OF_BED` strictly between first and last sleep-type stage;
    leading/trailing wake blocks excluded; `SLEEPING`/`UNKNOWN`-only sessions → null (coarse-device guard)
  - `sleepOnsetTime` = start of FIRST sleep-type stage (excludes pre-sleep awake time)
- [x] Wire the pipeline: HC fetch → `parseSession()` → `aggregateSegments()` → `calculateScores()` → store
- [x] Idempotent writes: reuses existing record's id for `user_id + session_date` before upsert — provisional pass is overwritten by finalization pass
- [x] Runtime availability check via `HealthConnectClient.getSdkStatus()` (APK on API < 34, built-in on 34+)
- [x] Anchor-window logic in the syncer: `isAnchorWindowClosed()` + `isPartialData` flag on synced records
- [x] `calculateScores()` accepts nullable HR/movement with weight redistribution (0.5/0.3/0.2 → 0.7/0.3 → duration-only)
- [x] Connect the health UI flow to real permission requests — DONE, see section 1b below
- [x] Background reads: `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` declared in the manifest (Android 14+ requirement for WorkManager-driven syncs) — on-device verification still pending (see device test below)
- [ ] DB safety net: add a unique constraint on `sleep_records(user_id, session_date)` in Supabase (code-side idempotency works without it, but the constraint guards against races)
- [ ] Empirical device test: sync one real night per target device (Mi Fitness, Zepp, realme, Nothing X) and verify stages/HR arrive as expected

### 1b. Permission UI — DONE

Agreed design (final):
- [x] Syncer guard: `syncSessionDate()` checks `permissionController.getGrantedPermissions()` BEFORE any read — per-type:
  - `READ_SLEEP` not granted → return `SyncResult.PermissionDenied` immediately (never attempt sleep read → no SecurityException → no retry loop); HR-only and nothing-granted take the same early exit
  - Sleep granted + HR not → proceed, skip HR read (scoring redistributes)
- [x] Workers + auditor map `PermissionDenied` to silent success (no retry; self-heals when user grants later)
- [x] Shared helper `HealthConnectPermissionHelper` (utils/): SDK status, `isAvailable()`, `needsInstall()` + install intent, granted-permissions check, sleep/HR permission predicates — single source of truth; `SleepSyncManager.isHealthConnectAvailable()` now delegates to it
- [x] Pre-flight screen (`HealthEducationFragment`): checklist content — (1) wear watch + Bluetooth, (2) enable "Sync with Health Connect" in companion app (Mi Fitness/Zepp/realme Link/Nothing X); "I've linked my watch" → Grant screen; Skip → confirmation dialog (same as Grant screen: "Continue without Health Connect?" warning before proceeding to Summary). Empty data, NO demo seeding
- [x] Onboarding wiring (per spec steps 3.5/4a/4b): onboarding flow is now Bedtime → Activity Library → Sequencing → **Pre-flight → Grant** → Summary → Routine Home. Health destinations + actions added to `onboarding_graph.xml`; Sequencing "Continue" routes into the pre-flight. **Pre-flight + Grant are onboarding-EXCLUSIVE** — `nav_graph.xml`'s Settings action points directly at `HealthSettingsFragment` (read-only statuses + HC deep link), so post-onboarding permission management happens entirely through Health Connect's own settings. Fragments use direct navigation (no graph-detection)
- [x] Grant screen (`HealthGrantFragment`): redesigned per wireframes — hero card with Shleepy illustration + "Why Health Connect?" explanation; two permission cards (Sleep Sessions, Heart Rate) with icons, descriptions, and status badges (Required/Granted); success card shown when all permissions granted; dynamic buttons (Grant + Skip when not all granted, Continue when all granted); badges update on permission grant; no auto-advance — stays on screen to show updated state; **Skip for now → Material confirmation dialog** ("Continue without Health Connect?" — consequences + Go back/Continue) before advancing with empty data, NO demo seeding
- [x] Settings (`HealthSettingsFragment`): read-only per-type status rows ("Sleep access — Granted ✓ / Not shared", same for heart rate) refreshed on resume; single "Open Health Connect Settings" button — grant AND revoke both happen in HC's own settings (no switch, no in-app request contract, no re-request button; HC is the single source of truth); unavailable state disables the button; deprecated `onBackPressed()` replaced with `navigateUp()`. (Design simplified from original switch+re-request spec after UX review)

## 2. Wire Syncer into Auditor (step 2 of the audit flow) — DONE

- [x] `ReconciliationAuditUseCase.execute()` — placeholder replaced with `syncSleepForDate()` per audit date
- [x] `SleepSyncManager` is now actively used by the auditor
- [x] Per-date semantics: sync skipped only when the anchor window hasn't opened (today); otherwise fetch always, upsert makes redundant writes harmless, and the verdict always re-runs (grace periods can flip PENDING → MISSED with no new data)
- [x] All sync outcomes non-fatal: `NoData`/`Unavailable` still judge on stored data; `Failed` logs a warning

## 3. MorningSyncWorker: mock data → two-phase sync — DONE

- [x] Mock data generation removed — worker no longer fabricates random sleep records
- [x] Rewired to call the shared syncer (`syncSessionDate` for yesterday's session date) — no aggregation logic duplicated
- [x] Two-phase sync implemented:
  - `MorningSyncWorker` ~9 AM = provisional pass (anchor window open → `is_partial_data = true`, morning recap data)
  - `SleepFinalizationWorker` (new) ~5 PM = finalization pass (window closed → overwrites provisional record via idempotent upsert)
- [x] Anchor-window logic lives in the syncer itself (`isAnchorWindowClosed()`), not in scheduler timing
- Note: `NoData`/`HealthConnectUnavailable` return `Result.success()` (nothing to retry); only hard failures retry

## 4. Morning recap popup gating — DONE

The popup must never re-show after the finalization pass rewrites the record.

- [x] Trigger: first app open of the day → inline provisional sync of yesterday's session date in `loadData()` (gated on the recap flag so later resumes don't re-hit Health Connect); 9 AM worker stays as backup
- [x] Popup shows the provisional/draft score — final corrections land silently in the Progress tab
- [x] Show once per session date: `last_shown_sleep_date` flag now stores the SESSION date (yesterday) the recap was shown for — not the calendar day, and not record existence (records get written twice)
- [x] Empty sync result → popup silently skipped (condition requires a record dated yesterday's session date)
- [x] Bug fixed en route: old condition required `sessionDate == today` (only worked with mock data that dated records today) — real records are dated yesterday, so the popup would never have shown

## 5. Sleep scoring adjustments for real data — PARTIALLY DONE

- [x] `SleepQualityProcessingUseCase`: `movementCount`/`movementScore` nullable — null-and-redistribute composite weights (done during syncer implementation)
- [ ] Validate movement thresholds after real data: ensure the universal range (tuned against mock data) produces a reasonable score distribution — device inconsistency is already mitigated by movement's low weight (20%)
- [x] HR baseline no longer hardcoded — it's a nullable `hrBaseline` parameter on `syncSessionDate()` (callers pass null for now)
- [ ] Baseline learning: null for the first 7 nights, then learned from the user's sleep history (design per comments in `SleepQualityProcessingUseCase`)
- [ ] Composite fallback weights when components are missing are a guess — validate against spec

## 6. Misc (do FIRST on return: verify step-5 fixes on device — granted-state swap, black headings, left-aligned why-body, skip→summary flow)

- [x] Verify step-5 fixes on device — done (all fixes verified, navigation crash fixed)

- [ ] `backend/` directory is empty (only `.idea/`) — confirm whether a backend service is planned or Supabase-only is final
- [x] Onboarding skipped Health Connect screens on resume: step mapping predated them (3 → summary). Renumbered with health steps (3 → Pre-flight, 4 → Grant, 5 → Summary) + step saves on all health exits
- [x] Sequencing Confirm crashed (`action_routineSequencing_to_healthEducation` not found): `nav_graph.xml` carried a stale inline copy of the whole onboarding graph without health screens, shadowing `onboarding_graph.xml`. Replaced with `<include>`, ported the `editMode` arg, made Confirm edit-aware (edit flow pops back to Routine tab instead of entering onboarding-only health flow)
- [ ] Edit Routine flow doesn't persist the re-sequenced routine (pre-existing gap, noted in code as next phase) — fix: call `updateRoutineConfiguration` on edit-mode Confirm
- [x] Onboarding step counters updated to 5 steps, summary excluded (bedtime 1/5, library 2/5, sequencing 3/5, pre-flight 4/5 per wireframe, grant 5/5)
- [x] Onboarding draft persistence: `draft_bedtime` + `draft_activity_ids` on `user_profiles` (Migration 4 — run it), saved per advance, restored when the shared ViewModel is empty, cleared on complete. Active routine config untouched until Summary. Verified on device.
- [x] Onboarding is one-way (forward only): Back buttons removed (Library, Sequencing, TEMP health backs). No logout button — not standard in setup funnels; abandon-and-resume covers it
- [ ] Login always resumes onboarding at step 1 instead of the saved step — undiagnosed; suspects: stale APK, wrong account's row, or write lost to app-kill timing. `MainActivity` now logs `Onboarding resume: userId/step/completed` — reproduce and send the line
- [x] Activity Library 3-segment progress bar now tracks selection state (observer updated everything except the segments)
- [ ] Activity cards still use purple circle placeholders — replace with per-activity illustrations
- [x] Step 1: hint card text ("This is the time…") to black — done
- [x] Step 2: activity names on cards to black — done
- [x] Step 2: move the activity details dialog here from step 3 (info icon button at top-right of cards); info button + dialog removed from Sequencing — done
- [ ] Step 2 wireframe update needed: info icon button on activity cards (replaces long-press)
- [x] Step 5 "skipped popup": investigated — not a bug. Both permissions were already granted, so HC instantly returns the full set and one-shot flow advances. Behaves correctly.
- [x] Step 5 auto-skip: if all permissions already granted when step 5 opens, skips straight to Summary — done
- [x] Step 3: removed "Tap ⓘ for details" from instructions — done
- [x] Step 1: Continue button font weight → regular — done
- [x] Summary: replace card icons — done (user-provided SVGs: moon/clock/checkmark-in-circle)
- [x] Summary: fix checkmark icon visibility (white stroke → black) — done
- [x] Step 5: icon colors — sleep #522ABE, heart #FB2C36, stages #615FFF — done
- [x] Skip bottom sheet: Continue button → flat purple, Go back text → black — done
- [x] Step 4 + 5: Skip this step button → black text, same style both screens — done
- [x] Summary → Main navigation crash: nested graphs can't cross-navigate; fixed by rebuilding nav graph — done
- [x] Summary: permissions-status card UI — rebuilt with Health Settings rows (icon circles, Granted pills, dividers, no outline)
- [ ] Activity details dialog, fleshed-out design (a basic name + description + duration dialog exists on Sequencing via the info button; long-press is reserved for drag, so the dialog opens from ⓘ only)
- [x] Checkmark badge on selected cards removed (selected = purple stroke only); dimmed unselectable cards no longer ripple
- [x] Cross-account inventory bleed: unequipping on account A also appears unequipped on account B. Was a reads-only bug: `getUserInventory(userId)` returned the static cache without checking ownership, and logout cleared nothing. Fixed by keying the cache on user ID + `clearCache()` on logout. (`ShopRepository.cachedItems` audited — harmless, global catalog.)
- [x] Stale previous-account flash on login: activity-scoped VMs (`Companion`, `Social`, `Routine`) survive logout and render old state until fresh data arrives. Fixed with `onLogout()` resets on all three, called from `performLogout()` alongside the cache clear.
- [x] Settings bedtime pill design — reviewed; current full-width pill + picker sheet is fine, closing the item
- [ ] Dead field cleanup: `user_profiles.health_connect_granted` is never read or written (live permission checks cover all decisions) — remove from model (and DB if desired)
- [ ] My Routines tab layout change — stack all routine cards vertically (not grid/carousel)
- [x] Profile screen picture reflects the user's own Shleepy (frozen charged frame of equipped outfit)

## 7. DB migrations (validated against code — user to run in Supabase)

Code expects these schemas (verified in `RoutineSession.kt`, `RewardLedger.kt`, audit logic):

- [x] **DONE — routine_sessions status**: `status TEXT DEFAULT 'PENDING'` column already exists
- [x] **DONE — reward_ledger rename**: `devolution_pending` → `has_first_miss` already applied
- [ ] **DISCUSS — routine_sessions.session_status**: `session_status TEXT` column (IN_PROGRESS / ABANDONED_PENDING_DIAGNOSIS) — tracks whether user finished routine before Safety Net expired. Currently redundant with local cache (RoutinePersistenceHelper). Decide whether to add to DB or rely on cache only.
- [x] **DONE — friendships**: table created per SDD ERD (`id`, `requester_id`/`receiver_id` TEXT FK → `user_profiles`, `status`, `created_at`/`updated_at`) + Realtime publication + RLS policies
- [ ] **LATER — encouragement_reactions**: only needed when the encouragement feature gets a UI (parked for future update)
- [x] **DONE — user_profiles drafts**: `draft_bedtime TEXT`, `draft_activity_ids JSONB` columns already exist
- [ ] **RUN — user_profiles onboarding_completed_at**: `onboarding_completed_at TIMESTAMPTZ` (Migration 5 in `supabase_migrations.sql`) — audit eligibility + Week N anchoring; set once by `markOnboardingComplete()`

## 8. Analytics: PENDING state in completion chart — NOTED, NOT STARTED

`routine_sessions.status` is now three-state, but the chart mapping (`AnalyticsDashboardFragment.buildCompletionStatuses`, ~line 192) collapses PENDING into pink INCOMPLETE — mislabeling not-yet-decided days (today all day, yesterday during grace period) as failures.

- [ ] Add `PENDING` to `RoutineCompletionRowView.DayStatus` + amber color (`noctra_health_yellow` #FFC90E) in `colorForStatus`
- [ ] Map `session.status == "PENDING" -> PENDING` before the else in `buildCompletionStatuses`
- [ ] (Optional) append "· N pending" to the completion summary label
- [x] Analytics loading state — decided: keep batched `loadWeek` with a single centered purple spinner (see ANALYTICS_SPEC.md)
- Already correct, no change needed: summary count (`count { COMPLETED }`) and insight generation (`filter { COMPLETED }`) — PENDING is invisible to both, which is the right semantics until the auditor resolves it

## 9. Data & Connectivity Policy — IN PROGRESS

Cloud-First (Online-Only) architecture. All core operations require active internet.

- [x] `NetworkObserver` utility: `ConnectivityManager`-based, emits `Flow<Boolean>` + `checkNow()` sync check
- [x] Offline state layout (`layout_offline.xml`): title, description, retry button, hint
- [x] Offline strings: title, description, retry, hint
- [x] Offline illustration (`ic_offline_shleepy.xml`): Shleepy sad + crossed-out WiFi, converted from user SVG
- [x] **Guard `MainActivity`**: check network at startup → if offline, show offline UI and hide `NavHostFragment`
- [x] **Repository error handling**: try-catch `IOException`/`SocketTimeoutException` in `SleepRecordRepository`, `UserProfileRepository`, `RoutineSessionRepository` → logs and re-throws
- [x] **Strict block policy**: no local modifications while offline (prevent split-brain)
- [x] **Per-tab offline placeholder**: when connectivity drops mid-session, each tab shows a "No internet connection" + retry (with spinner) instead of raw error
  - [x] Create `layout_no_internet.xml`: centered text + retry button with hidden ProgressBar
  - [x] Create `ic_refresh.xml`: Material refresh icon for retry button
  - [x] Create `ui/common/UiState.kt`: sealed class (`Loading`, `Success<T>`, `Offline`)
  - [x] Update `CompanionViewModel`: catch network errors → emit `Offline`, add `retry()`
  - [x] Update `CompanionFragment`: observe offline state, toggle placeholder
  - [x] Update `RoutineHomeViewModel`: add `Offline` variant to sealed class
  - [x] Update `RoutineHomeFragment`: observe offline state, toggle placeholder
  - [x] Update `AnalyticsViewModel`: catch network errors → emit `Offline`, add `retry()`
  - [x] Update `AnalyticsDashboardFragment`: observe offline state, toggle placeholder
  - [x] Update `UserProfileViewModel`: catch network errors → emit `Offline`, add `retry()`
  - [x] Update `UserProfileFragment`: observe offline state, toggle placeholder

## 10. Leaderboard & Friends — IN PROGRESS

Split from the SDD's single `SocialFragment` into two screens per wireframe (see `SDD_DEVIATIONS.md`).

Done:
- [x] `LeaderboardFragment` + `fragment_leaderboard.xml` per wireframe (gradient banner, ranked cards, medal placeholders, "Your Rank" pill, grey placeholder slots)
- [x] SDD ranking: natural top-10 position; pinned at bottom (top 9 + user) only when ranked outside top 10
- [x] Own card always present via `getOwnProfile()`; list padded to 10 with placeholder cards
- [x] Pull-to-refresh (`SwipeRefreshLayout`, 10s spinner cap) instead of Realtime auto-refresh (deferred, see below)
- [x] Trophy icon on Profile → leaderboard; bottom nav auto-hides (not in `mainTabs`)
- [x] Supabase `friendships` table + Realtime + RLS (see §7)

Pending assets (from user):
- [x] Grant screen heart-rate row icon (`ic_heart_rate`, native `#00A63E`)
- [ ] Pre-flight step icons: bluetooth + sync/refresh (temp: `ic_health_pulse`, `ic_nav_performance`)
- [x] Grant screen: sleep stages icon (`ic_sleep_stages`), Grant button shield icon (`ic_shield`, white-tinted)
- [x] Real medal icons (`ic_medal_gold/silver/bronze`, with rank numerals baked in)
- [x] Current streak flame icon (`ic_flame`)
- [x] Longest streak torch icon (`longest_streak`)
- [x] Total routines icon (`ic_checklist`)
- [x] Add by Email envelope icon (reused `ic_auth_email` from login screen)
- [x] Remove-friend icon (`ic_remove_friend`, `#D4183D` stroke)
- [x] Request accept check icon (`ic_check`, tinted white on purple circle)
- [x] Request decline cross icon (`ic_cross` on grey circle)
- [x] Add-friend sheet Shleepy illustration (`add_friend_illustration.png`; PNG chosen over vector since illustration relies on radial gradients)

Still to build:
- [x] Friends screen redesign (`SocialFragment` per wireframe, badge on Friend Requests button)
- [x] Friend avatars — first pass (frozen Charged-stage Lottie) has been reworked: rows now use a static vector (`avatar_shleepy.xml`, sheep on `#BBB7C7` circle with matching outline); all other-users' inventory reads removed (`getEquippedOutfits`, `avatarEquipment` flow, adapter `equipment` maps)
- [x] Skeleton loaders for leaderboard, friends, and friend-request screens (friends + profile done)
- [ ] Skeleton loaders for leaderboard and friend-request screens (friends + profile pattern to follow)
- [x] Profile avatar: frozen charged-stage frame of the equipped outfit (`UserProfileFragment` + `outfitAsset` in VM; status card keeps stage art per decision)
- [x] Pull-to-refresh on the Profile screen (`SwipeRefreshLayout` + `isLoading`/`hasLoaded` in VM)
- [x] Add by Email / Friend Requests placement — verified fine on device, closing
- [x] Badge count on Profile tab icon (pending request count → bottom nav badge)
- [x] Pull-to-refresh on Friends and Friend Requests screens (same pattern as leaderboard)
- [x] Temp cleanup — verified: no mock scaffolding or `LeaderboardDebug` logs remain in social code (only intentional debug simulator + shop seed data)

Parked for future update:
- Encouragement feature (logic exists, no UI entry point after split)
- Tonight's completion badge on cards
- My Progress summary (needs a home after split)
- Friend request push notifications (`FriendRequestNotificationWorker`)
- Realtime auto-refresh (manual refresh covers it for now)

## 11. Routine Tab — Begin Button Shows Past the Window (verify, likely TEMP-flag artifact)

Reported: the begin-routine button still shows after the routine window closes.

Suspect first: `MainActivity` line 73 sets `DebugSettings.setForceRoutineWindow(true)` (TEMP — remove before submission), and `RoutineHomeViewModel.loadHomeState()` treats that flag as an open window — so on debug builds the button shows 24/7 by design. The real window logic (`RoutineWindowProvider.isTimeInWindow` + expired-state handling) can only be assessed with the flag OFF.

- [x] Investigate today rendering MISSED in routine completion when no session row exists yet → root cause: chart disagreed with auditor (`determineStatus` says today-no-row = PENDING); fixed by mirroring the rule in `buildCompletionStatuses`
- [ ] Check merged routine-polish additions on device: edit-mode save flow persists (rest verified: home list, resume fallback, timer/keyboard/TimesUp, real windows + full durations, step-2 Back)
- [ ] See resume dialog rework in a real uncontrolled flow (preview verified only)
- [ ] Verify select-screen card rework in actual flows (onboarding + edit): badge, info button, no duration, spacing
- [ ] Decide details-popup mechanic on Routine home list: dedicated button vs long-press (discoverability vs clean look)
- [ ] Gate resume on freshness + window: Resumable short-circuits before window/completion checks on a timestamp-only local cache — resume works outside the window and potentially next-day; decide age limit and whether window applies
- [x] Verify routine v2 merge on device: back-blocker mid-routine, restyled player layouts, overlay icons, breathing freeze, preview button + auto-dismiss
- [x] Per-night bedtime snapshot: `target_bedtime` on `sleep_records`, first-write-wins at sync; null = current-target fallback rendered normally; adherence + variability read stamps; no history table; audit walk covers trailing 14 on return (plus stamp-only NoData pass for already-synced rows)
- [x] Adherence per-day target labels: hour-only ("10 PM") above the rings, one per day
- [x] Merge `origin/revision/ui-polish-routine-cards` into `merge/routine-cards` (7 conflicts: step-2 badge+info hybrid, step-3 visuals+chevrons hybrid, Companion ours) — build green, device verify pending
- [x] Merge `origin/feature/revamped-analytics` into `merge/audit-analytics` (99344d0): 4 conflicts resolved (UserProfile fields kept both, Migration 5 ours + 6 `created_at`, CompanionFragment convergent guards, SDD tail); eligibility UI cut (000ed70); `createdAt` pre-fill fix; build green — device run pending (no device attached at merge time)
- [ ] Reproduce with `forceRoutineWindow` off (or after TEMP removal); if the button still shows past the window, debug `isTimeInWindow`/expired-state handling for real
- [ ] Resume-routine dialog fix + polish pass (styling, copy, spacing — flagged during dialog preview review)

## 12. Thesis Document Revisions (problem reframed: adherence → regularity → quality)

- [ ] Rewrite problem statement: drop SOL-as-problem; state irregular timing + unstructured nights as problem, routine→regularity as mechanism, adult-transfer as gap
- [ ] Align objectives/research questions to the three meters (adherence, variability, quality) — every claimed variable must have a database column
- [ ] Write the SOL-vs-adherence distinction into the manuscript (preempts "why don't you measure latency")
- [ ] Evidence section: link-1 citations (Chaput 2020, Phillips 2017, Windred 2024, Sleep Health 2024) + link-2 with pediatric caveat (Mindell 2009/2015) + mechanism support (Trauer 2015, Jansson-Fröjmark 2024, JAMA 2024 component analysis)
- [ ] Methodology: bedtime-change controls (audit trail of target moves; adjustment-window shading; freeze-vs-log decision before data collection)
- [ ] Propagate the reframed problem into Noctra_SDD.docx / Noctra_SRS.docx / SDD_revisions.docx so all documents agree
- [ ] Optional app work to fully close the loop: surface SOL stat (session-start→onset gap) on analytics
