# Noctra — Backlog

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

## 6. Misc

- [ ] `backend/` directory is empty (only `.idea/`) — confirm whether a backend service is planned or Supabase-only is final

## 7. DB migrations (validated against code — user to run in Supabase)

Code expects these schemas (verified in `RoutineSession.kt`, `RewardLedger.kt`, audit logic):

- [ ] **REQUIRED — routine_sessions status**: add `status TEXT DEFAULT 'PENDING'`, backfill from `is_completed` (true→COMPLETED, false→MISSED), then drop `is_completed`. Code has zero `is_completed` references; session inserts/auditor fail without `status`
- [ ] **REQUIRED — reward_ledger rename**: `devolution_pending` → `has_first_miss` (matches `RewardLedger.kt:13`). Without it, every ledger write (streaks/tokens/XP) fails on unknown column
- [ ] **OPTIONAL — routine_sessions.was_healed** (BOOLEAN DEFAULT false): zero code references today; safe to add now, but the Kotlin model needs a matching `@SerialName("was_healed")` field when the heal/restore feature is built
- Timing: run BEFORE on-device testing — current code writes both columns immediately

## 8. Analytics: PENDING state in completion chart — NOTED, NOT STARTED

`routine_sessions.status` is now three-state, but the chart mapping (`AnalyticsDashboardFragment.buildCompletionStatuses`, ~line 192) collapses PENDING into pink INCOMPLETE — mislabeling not-yet-decided days (today all day, yesterday during grace period) as failures.

- [ ] Add `PENDING` to `RoutineCompletionRowView.DayStatus` + amber color (`noctra_health_yellow` #FFC90E) in `colorForStatus`
- [ ] Map `session.status == "PENDING" -> PENDING` before the else in `buildCompletionStatuses`
- [ ] (Optional) append "· N pending" to the completion summary label
- Already correct, no change needed: summary count (`count { COMPLETED }`) and insight generation (`filter { COMPLETED }`) — PENDING is invisible to both, which is the right semantics until the auditor resolves it
