# Companion App Data Sharing

What phone-side companion apps actually deliver to Health Connect — and what
that means for Noctra's sleep scoring. Behavior is per **companion app**
(Mi Fitness, Zepp, …), not per watch brand: two watches from one brand can
share differently depending on the app and its sync settings.

Rule: cells marked ✅ / ❌ are device-verified. Everything else is
**TO VERIFY** — do not build scoring assumptions on it.

---

## 1. What Noctra needs from Health Connect

| Signal | HC record type | Used for | If missing |
|---|---|---|---|
| Sleep sessions (start/end) | `SleepSessionRecord` | Duration score (50%), session anchoring | Nothing to score — no record at all |
| Sleep stages (awake/light/deep/REM + times) | stages inside `SleepSessionRecord` | Movement/restlessness (20%), derived from wake blocks between first/last sleep-type stage | Movement = null, weights redistribute |
| HR samples overlapping sleep | `HeartRateRecord` (timestamps within session) | HR score (30%), future 7-night baseline | HR = null, weights redistribute |
| SpO2 readings overnight | `OxygenSaturationRecord` | Breathing-disturbance flag (future, not scored) | Flag skipped silently |

Current scoring profiles (see `SleepQualityProcessingUseCase`):
- **Full:** duration + movement + HR.
- **No-nocturnal-HR:** duration + movement (redistributed weights).
- **Sessions only:** duration + schedule consistency. Honest floor — label it
  as such, don't inflate.

Planned (not built): runtime capability detection per user — probe which of
the above exist and select the profile automatically, instead of hardcoding
per-brand behavior.

---

## 2. App profiles

### 2.1 Mi Fitness (Xiaomi/Redmi) — PARTIALLY VERIFIED

Tested: Redmi smartwatch, one night.

| Data | Shares? | Notes |
|---|---|---|
| Sleep sessions | ✅ YES | Entries land in HC with correct times (e.g. 2:15–6:11am) |
| Sleep stages | ✅ YES | Full stage detail inside the session (light/deep/awake segments with timestamps) — movement score fully supported |
| HR during sleep | ✅ YES (corrected) | Initially judged absent — actually present. HC app groups samples into 30-min display buckets (min–max); drilling in shows individual timestamped samples (~1 per 10 min overnight, matching Xiaomi's documented at-rest cadence). The buckets are UI-only, not API objects |
| SpO2 overnight | ❓ LEAD, TO VERIFY | ROOK's Mi Fitness integration docs list SpO2 via HC (`saturation_avg_percentage_int`, `saturation_granular_data_array`, `body_oxygenation_event`) — but gated on the user enabling the datatype in Mi Fitness → Settings → Health Connect, and on all-day SpO2 tracking being on in the band/app. Check our HC dump for `OxygenSaturationRecord`; absence may just mean the toggles were off during the test |
| Background/sync cadence | ❓ TO VERIFY | Check "Sync with Health Connect" is on; note sync delay after wake |

**Impact today:** Noctra on Mi Fitness runs the no-nocturnal-HR profile *if*
stages arrive, else sessions-only. The adjacent-HR-anchor idea (last sample
before onset / first after wake) is untested — verify sample density near
bedtime before relying on it.

**Open questions:**
1. Do stages arrive? (single most important unknown)
2. Does SpO2 arrive? (unlocks the breathing flag)
3. How long after wake does the night's data land in HC? (sets worker timing
   expectations — a 9AM provisional pass is useless if sync lands at noon)

### 2.2 Zepp (Amazfit) — UNTESTED

| Data | Shares? | Notes |
|---|---|---|
| Sleep sessions | ❓ | Fill in after one test night |
| Sleep stages | ❓ | |
| HR during sleep | ❓ | Zepp is historically generous with HR; verify overlap, not just presence |
| SpO2 overnight | ❓ | |
| Background/sync cadence | ❓ | |

### 2.3 realme Link — UNTESTED

Same table. Fill in after one test night.

### 2.4 Nothing X — UNTESTED

Same table. Fill in after one test night.

---

## 3. How to test a new app (protocol)

One normal night, then before opening Noctra:

1. Confirm the companion app's "Sync with Health Connect" toggle is on.
2. Note wake time; note when (if ever) the night appears in HC.
3. Dump the night's HC records (HC Toolbox / sample app): list record types,
   count HR samples overlapping the session, check stage types present,
   check SpO2 records.
4. Record results in §2 using ✅/❌ only — no assumptions, no "probably".

## 4. History

- Redmi/Mi Fitness first test: HR confirmed present-but-daytime-only; sleep
  sessions, stages, and SpO2 still unverified. Scoring implication (nullable
  HR path) already supported by the pipeline — no code change needed, only
  calibration once stages are confirmed.
