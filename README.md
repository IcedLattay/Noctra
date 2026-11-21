# Noctra

**A gamified pre-sleep routine assistant for Filipino college students.**

Noctra guides users through a personalized wind-down routine every night, rewards consistency through Shleepy (a virtual sheep companion that grows with the user), and measures sleep quality using data from the user's smartwatch through Google Health Connect.

> IT411 Capstone and Research 2 · Team `2627-sem1-it411-37` · Cebu Institute of Technology – University

---

## Contents

- [Download](#download)
- [Features](#features)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Development Setup](#development-setup)
- [Building and Releasing the APK](#building-and-releasing-the-apk)
- [Backend (Supabase)](#backend-supabase)
- [How Sleep Data Reaches Noctra](#how-sleep-data-reaches-noctra)
- [Background Jobs](#background-jobs)
- [Known Limitations](#known-limitations)
- [Documentation](#documentation)
- [Team](#team)

---

## Download

| | |
|---|---|
| **Latest release** | [GitHub Releases](https://github.com/IcedLattay/Noctra/releases/latest) (current version: 1.2.8) |
| **Download page** | [icedlattay.github.io/Noctra](https://icedlattay.github.io/Noctra/) |
| **Demo account and device setup** | See *Noctra – Access Instructions and Device Setup Guide* in the team's Google Drive (`02_Software_and_Deployment`) |

To install, open the APK on an Android phone. If Android blocks it, allow **Install unknown apps** for your browser or file manager (Settings → Apps → Special app access). Each release also ships a `.sha256` checksum, which the in-app updater uses to verify downloads.

---

## Features

### Routine guidance
- **Onboarding:** set a target bedtime, pick wind-down activities from a library, and arrange them in order.
- **Nightly routine:** a wind-down reminder before bedtime, then a guided session through each activity with timers, instructions, animations and audio.
- **Ten activities:** Slow-Paced Breathing, White/Pink Noise, Gratitude Journaling, Progressive Muscle Relaxation, Bedtime Stretching, Mindfulness, Reading, Warm Shower, Bedtime To-Do List Writing, and Low-Stimulus Audio Listening.
- **Resume support:** an interrupted routine can be resumed where the user left off.

### Companion and gamification
- **Shleepy the sheep:** evolves through stages as the user completes routines and keeps a streak.
- **Rewards:** each completed routine earns Dream Tokens with a streak multiplier. Growth Points (XP) are awarded the next morning based on the sleep quality score (Good +160, Moderate +80, Poor −100, never below zero).
- **Shop and outfits:** Dream Tokens buy outfits for Shleepy.

### Sleep monitoring and analytics
- **Health Connect integration:** reads sleep sessions and heart rate written by the user's smartwatch app.
- **Nightly sleep quality score:** a composite of sleep duration (50%), heart rate (30%) and movement from sleep stages (20%). If a signal is missing, its weight is redistributed across the available ones.
- **Bedtime adherence:** compares when the user actually went to sleep against their target bedtime.
- **Analytics dashboard:** a 7-day detail view and a 30-day sleep trend with a trailing average line, plus generated insights.
- **Morning summary:** a pop-up with last night's results.

### Social
- **Friends:** add friends and accept or decline friend requests.
- **Leaderboard:** ranks friends by current routine streak, and lets users send encouragement reactions.

### Account
- Registration with email verification (OTP), login, password reset, and profile settings.
- **In-app updater:** checks GitHub Releases for new versions.

---

## Screenshots

<!-- TODO: add the six PNG screenshots to docs/screenshots/ using these exact file names. -->

| Onboarding | Nightly routine | Shleepy |
|:---:|:---:|:---:|
| <img src="docs/screenshots/onboarding.png" width="220" alt="Onboarding: bedtime and activity setup"> | <img src="docs/screenshots/routine.png" width="220" alt="Guided nightly routine"> | <img src="docs/screenshots/companion.png" width="220" alt="Shleepy companion screen"> |
| **Analytics** | **Leaderboard** | **Sleep Shop** |
| <img src="docs/screenshots/analytics.png" width="220" alt="Analytics dashboard"> | <img src="docs/screenshots/leaderboard.png" width="220" alt="Friends leaderboard"> | <img src="docs/screenshots/shop.png" width="220" alt="Sleep Shop outfits"> |

---

## Requirements

| Item | Requirement |
|---|---|
| **Phone** | Android 9 (API 28) or higher. iOS is not supported. |
| **Google Play services** | Required. Health Connect does not run without them. |
| **Health Connect** | Built into Android 14 and up. On Android 9–13, install it from the Google Play Store. |
| **Internet** | Needed for login, saving sessions, analytics and the leaderboard. The routine's bundled audio works offline. |
| **Smartwatch** | Optional. Needed only for sleep data. Any watch or band whose phone app writes sleep data to Health Connect (e.g., Zepp, Samsung Health, Google Health/Fitbit, Garmin Connect, Mi Fitness, or Huawei Health through Health Sync). |

**Permissions used:** Internet, notifications, exact alarms and boot completion (bedtime reminders), install packages (in-app updater), and Health Connect read access to sleep, heart rate, and health data in the background.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0, JVM target 11 |
| **Platform** | Android SDK: min 28, target and compile 36 |
| **Build** | Gradle 9.4 (Kotlin DSL), Android Gradle Plugin 9.2, version catalog (`gradle/libs.versions.toml`) |
| **UI** | Android Views with View Binding, Material Components, Navigation Component, ConstraintLayout, Flexbox |
| **Architecture** | MVVM: Fragments → ViewModels (Lifecycle) → Repositories → Supabase / Health Connect |
| **Backend** | Supabase 2.4 (Auth/GoTrue, PostgREST, Realtime) over Ktor |
| **Health data** | Android Health Connect client 1.1 |
| **Background work** | WorkManager 2.11 |
| **Charts** | MPAndroidChart 3.1 |
| **Animation** | Lottie 6.4 |
| **Concurrency and serialization** | Kotlin Coroutines, kotlinx.serialization |

---

## Project Structure

```
app/src/main/java/com/noctra/app/
├── data/
│   ├── model/          # Data classes (UserProfile, RoutineSession, SleepRecord, ShopItem, ...)
│   ├── repository/     # Data access: auth, routines, sleep records, friends, shop, sleep sync
│   └── supabase/       # Supabase client setup
├── domain/
│   └── usecase/        # Business logic: sleep scoring, bedtime adherence, rewards,
│                       # companion evolution, insights, leaderboard ranking, HR baseline
├── ui/
│   ├── auth/           # Login, registration, OTP verification, password reset
│   ├── routine/        # Onboarding, routine home, routine execution and activities
│   ├── companion/      # Shleepy, evolution, streak notices, morning sleep pop-up
│   ├── analytics/      # Analytics dashboard
│   ├── health/         # Health Connect education, permission grant, settings
│   ├── social/         # Friends, friend requests, leaderboard
│   ├── profile/        # Profile and settings
│   └── debug/          # Developer debug panel
├── workers/            # WorkManager jobs (sleep sync, missed sessions, finalization, audit)
├── receivers/          # Wind-down notification and boot receivers
├── update/             # In-app updater (GitHub Releases)
└── utils/
```

Assets such as Lottie animations and bundled audio are in `app/src/main/res/raw/`.

---

## Development Setup

### Prerequisites
- A recent version of **Android Studio** that supports Android Gradle Plugin 9.2
- **JDK 17 or newer.** Android Studio's bundled JDK works.
- Access to the team's **Supabase project** for the URL and anon key

### Steps
1. **Clone the repository.**
   ```bash
   git clone https://github.com/IcedLattay/Noctra.git
   ```
2. **Add your Supabase keys.** Create or open `local.properties` in the project root and add:
   ```properties
   SUPABASE_URL=https://<your-project-ref>.supabase.co
   SUPABASE_ANON_KEY=<your-anon-key>
   ```
   These are injected into `BuildConfig` at build time. `local.properties` is gitignored, so **never commit it**. Use only the **anon** key; never put the `service_role` key in the app.
3. **Open the project in Android Studio** and let Gradle sync.
4. **Run the app** on a physical device with Health Connect installed. An emulator works for everything except real sleep data.

---

## Building and Releasing the APK

### Build
```bash
./gradlew assembleRelease
```
Output, in `app/build/outputs/apk/release/`:
- `Noctra-<version>.apk`: the publishable APK
- `Noctra-<version>.apk.sha256`: its checksum, generated automatically

### Signing
Release builds are signed when a `keystore.properties` file exists in the project root:
```properties
storeFile=<path-to-keystore>
storePassword=<store-password>
keyAlias=<key-alias>
keyPassword=<key-password>
```
Both `keystore.properties` and keystore files are gitignored. Without them, the release build is unsigned.

### Release
1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run `./gradlew assembleRelease`.
3. Create a GitHub Release tagged `v<version>` (e.g., `v1.2.8`) and attach **both** the APK and its `.sha256` file.

The in-app updater reads the latest GitHub Release, so publishing a release makes it available to existing users.

---

## Backend (Supabase)

Noctra uses Supabase for authentication, data storage and realtime features. Its main tables include `user_profiles`, `routine_sessions`, `sleep_records`, `reward_ledger`, `friendships`, `encouragement_reactions`, `shop_items`, `user_inventory` and `activity_library`. Row Level Security is enabled on the tables the app reads and writes.

`supabase_migrations.sql` contains the **incremental migrations** applied on top of the base schema, including the activity library seed data. Run them in order in the Supabase **SQL Editor**. The base schema is not included in this repository; contact the team for database access.

> **Note:** The released APK is already connected to the team's Supabase project. Evaluators do not need to set up a backend.

---

## How Sleep Data Reaches Noctra

```
Smartwatch → watch brand app (e.g., Zepp) → Health Connect → Noctra → Supabase
```

1. The user wears the watch to sleep.
2. The watch brand app syncs the night's data when opened (with the watch connected by Bluetooth) and writes it to Health Connect.
3. Noctra reads sleep sessions and heart rate from Health Connect, computes the sleep quality score, and stores the results in Supabase.

Detailed per-brand setup steps are in the *Access Instructions and Device Setup Guide*. Verified data-sharing behavior per companion app is recorded in [`COMPANION_APP_DATA_SHARING.md`](COMPANION_APP_DATA_SHARING.md).

---

## Background Jobs

Scheduled with WorkManager when the app starts. Times are device-local, and Android may run them later than scheduled to save battery.

| Worker | Daily schedule | Purpose |
|---|---|---|
| `MorningSyncWorker` | ~9:00 AM | Reads the previous night's Health Connect data and writes a provisional sleep record and score |
| `MissedSessionCheckerWorker` | ~9:30 AM (needs internet) | Refreshes the day's reminder schedule and marks unfinished routines from recent nights (up to 14 days back) as missed |
| `SleepFinalizationWorker` | ~5:00 PM | Re-syncs yesterday's sleep data after the 4:00 PM wake-up window closes, replacing the provisional morning record |
| `AuditBackfillWorker` | ~10:00 PM (needs internet) | Backfills routine history older than 14 days, marking past nights with no completed routine as missed |

---

## Known Limitations

- **Android only.** Health Connect is Android-exclusive.
- **No watch app.** Noctra runs on the phone. Watch data reaches it only through the watch brand's phone app and Health Connect.
- **Huawei phones without Google services** cannot run Health Connect, so Noctra cannot read sleep data on them.
- **Huawei watches** require the third-party Health Sync app (paid after a 7-day trial) to forward data to Health Connect.
- **Manual sync.** Most watch apps only sync when opened with the watch connected, so data may arrive late.
- **Sleep stages** are used only as a movement signal. Stage classification (Light, Deep, REM) is not scored because consumer-device accuracy is limited.
- **Friend activity updates** use pull-to-refresh rather than realtime push. See [`FUTURE_WORK.md`](FUTURE_WORK.md).

---

## Documentation

| Document | Description |
|---|---|
| [`Noctra_SRS.docx`](Noctra_SRS.docx) | Software Requirements Specification (being updated to v2.0) |
| [`Noctra_SDD.docx`](Noctra_SDD.docx) | Software Design Description (being updated) |
| [`SDD_DEVIATIONS.md`](SDD_DEVIATIONS.md) | Where the implementation differs from the SDD, and why |
| [`COMPANION_APP_DATA_SHARING.md`](COMPANION_APP_DATA_SHARING.md) | What each watch app actually sends to Health Connect |
| [`BACKLOG.md`](BACKLOG.md) | Active development backlog |
| [`FUTURE_WORK.md`](FUTURE_WORK.md) | Deferred features and the reasoning behind them |

---

## Team

| Member |
|---|
| Escasinas, Luezyl Dominic C. |
| Lao, Hilary Danne C. |
| Guimoc, Earl John G. |
| Estrada, Julianna Carmel |
| Estela, Gerard Grant M. |

**Adviser:** Erica Jean S. Abadinas

Developed for IT411 Capstone and Research 2, Cebu Institute of Technology – University, A.Y. 2026–2027.
