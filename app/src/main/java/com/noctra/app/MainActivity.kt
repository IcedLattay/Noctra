package com.noctra.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.data.utils.RoutinePersistenceHelper
import com.noctra.app.ui.debug.DebugPanelListener
import com.noctra.app.ui.routine.RoutineViewModel
import com.noctra.app.ui.routine.home.ResumeRoutineDialogFragment
import com.noctra.app.utils.DebugSettings
import com.noctra.app.utils.UserSession
import com.noctra.app.workers.WindDownNotificationScheduler
import com.noctra.app.workers.WindDownNotificationWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.domain.usecase.SleepQualityProcessingUseCase
import com.noctra.app.utils.DemoDataSeeder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.launch
import android.widget.Toast

class MainActivity : AppCompatActivity(), DebugPanelListener {

    /** Bottom nav is hidden for onboarding and the entire routine execution chain. */
    private val executionDestinations = setOf(
        R.id.bedtimeConfigFragment,
        R.id.activityLibraryFragment,
        R.id.routineSequencingFragment,
        R.id.onboardingSummaryFragment,
        R.id.routineStartFragment,
        R.id.healthConnectSetupFragment,
        R.id.breathingActivityFragment,
        R.id.audioscapeActivityFragment,
        R.id.gratitudeJournalingActivityFragment,
        R.id.genericTimerActivityFragment,
        R.id.timesUpTransitionFragment,
        R.id.routineCompletionOverlayFragment
    )

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored */ }

    // Same instance the routine execution fragments obtain via
    // activityViewModels() — Activity-scoped, so this and those fragments
    // all share one ViewModel/ViewModelStore.
    private val routineViewModel: RoutineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        DebugSettings.setForceRoutineWindow(true) // TEMP — remove before final submission
        DebugSettings.setSkipCompletionCheck(true) // TEMP — remove before final submission

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Navigation UI Logic
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 1. Visibility Logic
            bottomNav.visibility = when {
                destination.id == R.id.settingsFragment -> View.GONE
                destination.id in executionDestinations -> View.GONE
                else -> View.VISIBLE
            }

            // 2. Ensure "Companion" stays selected when in Customization
            if (destination.id == R.id.customizationFragment) {
                bottomNav.menu.findItem(R.id.companionFragment).isChecked = true
            }
        }

        requestNotificationPermissionIfNeeded()
        WindDownNotificationScheduler.scheduleNext(applicationContext)

        checkOnboardingStatus()
        registerResumeDialogResultListener()
        observeRecoveryState()
    }

    override fun onResume() {
        super.onResume()
        checkMorningAfterCleanup()
        checkResumableRoutine()
    }

    // ─── Global Resume Popup (tasks 7 & 8) ───────────────────────────────────

    /**
     * Trigger for The Global Resume Popup (task 7/10).
     *
     * FLAG: spec says "only if the user is logged in," but no explicit
     * login-check method was visible anywhere in this codebase (UserSession,
     * UserProfileRepository). Using onboardingCompleted as the closest
     * available proxy — same check checkOnboardingStatus() already uses
     * just below. Confirm with leader if a more precise login-state check
     * exists or should be added.
     */
    private fun checkResumableRoutine() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                val profile = UserProfileRepository().getOrCreateProfile(userId)
                if (!profile.onboardingCompleted) return@launch

                routineViewModel.checkRecoveryState()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "checkResumableRoutine failed", e)
            }
        }
    }

    /**
     * Reacts to RoutineViewModel.recoveryState — shows the Resume Dialog
     * (task 6) whenever checkRecoveryState() determines there's something
     * resumable. Launched once in onCreate(); repeatOnLifecycle handles
     * pausing/resuming this collector automatically, so it's safe against
     * duplicate collectors across multiple onResume() calls.
     */
    private fun observeRecoveryState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                routineViewModel.recoveryState.collect { state ->
                    if (state is RoutineViewModel.RecoveryState.Resumable) {
                        showResumeDialogIfNeeded(state)
                    }
                }
            }
        }
    }

    private fun showResumeDialogIfNeeded(state: RoutineViewModel.RecoveryState.Resumable) {
        // Guard against showing a duplicate dialog if recoveryState re-emits
        // while one is already on screen.
        if (supportFragmentManager.findFragmentByTag(ResumeRoutineDialogFragment.TAG) != null) return

        ResumeRoutineDialogFragment.newInstance(
            currentStepIndex = state.stepIndex,
            totalSteps = state.totalSteps,
            awayMinutes = state.awayMinutes
        ).show(supportFragmentManager, ResumeRoutineDialogFragment.TAG)
    }

    /**
     * Handles the dialog's result — "Resume Routine" (task 7) vs
     * "Not Now" (task 8).
     *
     * FLAG (race condition risk): confirmResume() does async DB work
     * before it can emit the navigation event that actually jumps into the
     * right activity fragment. That event is only listened for once
     * RoutineStartFragment is on screen and subscribed — so this navigates
     * there immediately after calling confirmResume(), relying on the DB
     * round-trip taking longer than the synchronous navigation + fragment
     * subscription. This should hold in practice but isn't strictly
     * guaranteed. RoutineStartFragment (not available to edit here) may
     * need a small follow-up change to explicitly know "I was opened to
     * resume" rather than assuming a fresh Begin Routine tap.
     *
     * "Not Now" (task 8): declineResume() deliberately does NOT touch
     * RoutinePersistenceHelper — the cached session stays intact so a
     * Passive Resume button can still appear elsewhere (e.g. My Routines
     * tab), per the spec. That button's own UI logic lives outside this
     * file.
     */
    private fun registerResumeDialogResultListener() {
        supportFragmentManager.setFragmentResultListener(
            ResumeRoutineDialogFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val resumed = bundle.getBoolean(ResumeRoutineDialogFragment.RESULT_RESUMED)
            if (resumed) {
                routineViewModel.confirmResume()
                navigateToRoutineStart()
            } else {
                routineViewModel.declineResume()
            }
        }
    }

    private fun navigateToRoutineStart() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        navHostFragment.navController.navigate(R.id.routineStartFragment)
    }

    /**
     * Morning After Cleanup (Cleanup & Enforcements, task 9/10).
     *
     * Catches the case where the device screen stayed off all night mid-
     * routine. On the next onResume, if it's been 8+ hours since the last
     * recorded routine activity, clear the local resume cache and send the
     * user straight to AnalyticsDashboardFragment — so they never see last
     * night's exercise screen again.
     *
     * FLAG: the spec (Aug 16 backlog) calls for "8 hours since
     * session_start_time" specifically, but RoutinePersistenceHelper (task
     * 3) only stores last_activity_timestamp, not a separate session-start
     * time. Using last_activity_timestamp here as the closest available
     * proxy. For a routine abandoned mid-sleep these are close in practice,
     * but they're not literally the same value the spec names — flagging
     * for leader confirmation; a dedicated session_start_time field may
     * need to be added to RoutinePersistenceHelper if the distinction
     * actually matters (e.g. if someone does several steps over 40+ minutes
     * before falling asleep).
     */
    private fun checkMorningAfterCleanup() {
        if (!RoutinePersistenceHelper.hasActiveSession()) return

        val lastActivity = RoutinePersistenceHelper.getLastActivityTimestamp()
        if (lastActivity == 0L) return

        val gapMillis = System.currentTimeMillis() - lastActivity
        val eightHoursMillis = 8L * 60 * 60 * 1000

        if (gapMillis > eightHoursMillis) {
            RoutinePersistenceHelper.clear()

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host) as NavHostFragment
            val navController = navHostFragment.navController

            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .build()
            navController.navigate(R.id.analyticsDashboardFragment, null, navOptions)
        }
    }

    private fun checkOnboardingStatus() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                val profile = UserProfileRepository().getOrCreateProfile(userId)

                // Only perform the auto-redirect if we are currently at the start of onboarding.
                // This prevents overriding deep links (like the routine notification).
                val currentDest = navController.currentDestination?.id
                if (profile.onboardingCompleted && currentDest == R.id.bedtimeConfigFragment) {
                    // If onboarding is done, jump to the Companion screen
                    // and clear the onboarding screens from the backstack
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.bedtimeConfigFragment, true)
                        .build()
                    navController.navigate(R.id.companionFragment, null, navOptions)
                }
            } catch (e: Exception) {
                // If network fails, we'll stay on onboarding or current screen
                android.util.Log.e("MainActivity", "Onboarding check failed", e)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ─── DebugPanelListener ───────────────────────────────────────────────────

    override fun onResetOnboarding() {
        lifecycleScope.launch {
            val userId = UserSession.getUserId(applicationContext)
            UserProfileRepository().resetOnboarding(userId)
            Toast.makeText(this@MainActivity, "Onboarding reset. Restart app to see flow.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onForceRoutineWindowOpen() {
        DebugSettings.setForceRoutineWindow(true)
        Toast.makeText(this, "Routine window forced open!", Toast.LENGTH_SHORT).show()
    }

    override fun onFireWindDownNotification() {
        val request = OneTimeWorkRequestBuilder<WindDownNotificationWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
        Toast.makeText(this, "Notification triggered!", Toast.LENGTH_SHORT).show()
    }

    override fun onSimulateMorningSync() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)

                // 1. Generate realistic mock data
                val durationMinutes = Random.nextInt(330, 540)
                val avgHeartRate = Random.nextDouble(55.0, 75.0)
                val movementCount = Random.nextInt(0, 50)
                val hrBaseline = 60.0

                val scores = SleepQualityProcessingUseCase().calculateScores(
                    durationMinutes = durationMinutes,
                    avgHeartRate = avgHeartRate,
                    movementCount = movementCount,
                    hrBaseline = hrBaseline
                )

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                val mockRecord = SleepRecord(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    sessionDate = today,
                    sleepOnsetTime = Instant.now().minusSeconds((durationMinutes * 60).toLong()).toString(),
                    wakeTime = Instant.now().toString(),
                    sleepDurationMinutes = durationMinutes,
                    avgHeartRateBpm = avgHeartRate,
                    movementEventCount = movementCount,
                    hrBaselineAtScoring = hrBaseline,
                    durationScore = scores.durationScore,
                    heartRateScore = scores.heartRateScore,
                    movementScore = scores.movementScore,
                    compositeScore = scores.compositeScore,
                    dataCaptureSuccess = true
                )

                SleepRecordRepository().insertSleepRecord(mockRecord)

                // Clear the "last shown" flag so the CompanionFragment shows it immediately
                getSharedPreferences("noctra_prefs", MODE_PRIVATE).edit().remove("last_shown_sleep_date").apply()

                Toast.makeText(this@MainActivity, "Morning sync simulated! Check Companion tab.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Simulate Morning Sync failed", e)
                Toast.makeText(this@MainActivity, "Sync simulation failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSimulateMissedNight() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                val yesterday = LocalDate.now().minusDays(1).toString()

                // 1. Record a missed session
                val missedSession = RoutineSession(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    sessionDate = yesterday,
                    startTimestamp = Instant.now().minusSeconds(86400).toString(),
                    isCompleted = false
                )
                RoutineSessionRepository().insertSessions(listOf(missedSession))

                // 2. Reset streak and queue devolution penalty
                val repo = RewardLedgerRepository()
                val ledger = repo.getRewardLedger(userId)
                if (ledger != null) {
                    repo.updateRewardLedger(ledger.copy(
                        currentStreak = 0,
                        devolutionPending = true,
                        lastUpdated = OffsetDateTime.now().toString()
                    ))
                }

                Toast.makeText(this@MainActivity, "Missed night simulated. Streak reset.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Simulate Missed Night failed", e)
                Toast.makeText(this@MainActivity, "Missed night simulation failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onTriggerEvolution() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                RewardLedgerRepository().addXp(userId, 5000)
                Toast.makeText(this@MainActivity, "XP boosted by 5000!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Trigger Evolution failed", e)
                Toast.makeText(this@MainActivity, "Evolution trigger failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSeedDemoData() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                DemoDataSeeder(SleepRecordRepository(), RoutineSessionRepository()).seedLastSevenDays(
                    userId,
                    java.time.LocalTime.of(22, 0)
                )
                Toast.makeText(this@MainActivity, "7 days of data seeded!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Seed Demo Data failed", e)
                Toast.makeText(this@MainActivity, "Seeding failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onClearDemoData() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext)
                SleepRecordRepository().deleteAllForUser(userId)
                RoutineSessionRepository().deleteAllForUser(userId)
                Toast.makeText(this@MainActivity, "All analytics data cleared!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Clear Demo Data failed", e)
                Toast.makeText(this@MainActivity, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}