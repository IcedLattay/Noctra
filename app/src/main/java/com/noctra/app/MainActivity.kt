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
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import com.noctra.app.ui.debug.DebugPanelListener
import com.noctra.app.ui.routine.RoutineViewModel
import com.noctra.app.ui.routine.home.ResumeRoutineDialogFragment
import com.noctra.app.utils.DebugSettings
import com.noctra.app.utils.UserSession
import com.noctra.app.workers.WindDownNotificationScheduler
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.domain.usecase.SleepQualityProcessingUseCase
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity(), DebugPanelListener {

    private var isLoading = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // After notification permission is handled, check for alarm permission
        requestAlarmPermissionIfNeeded()
    }

    // Same instance the routine execution fragments obtain via
    // activityViewModels() — Activity-scoped, so this and those fragments
    // all share one ViewModel/ViewModelStore.
    private val routineViewModel: RoutineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { isLoading }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        DebugSettings.setForceRoutineWindow(true) // TEMP — remove before final submission
        DebugSettings.setSkipCompletionCheck(true) // TEMP — remove before final submission

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Handle deep links from Supabase (e.g. password recovery)
        handleDeeplinks(intent)

        registerResumeDialogResultListener()
        observeRecoveryState()

        // Check onboarding status and handle permissions if already completed
        checkOnboardingStatus(navController, bottomNav)
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
     * FLAG: spec says "only if the user is logged in." UserSession.getUserId()
     * is now nullable per the auth restructure (feature/health-connect-
     * permission-ui) — a null userId itself is a reasonably direct "not
     * logged in" signal, used here instead of the old onboardingCompleted
     * proxy.
     */
    private fun checkResumableRoutine() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
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
     * subscription. Needs on-device testing (Flag 13).
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
     * FLAG: spec calls for "8 hours since session_start_time" specifically,
     * but RoutinePersistenceHelper only stores last_activity_timestamp.
     * Using last_activity_timestamp here as the closest available proxy.
     *
     * FIXED (Flag 25, crash found via on-device testing): blindly navigating
     * to analyticsDashboardFragment crashed the app if the user was sitting
     * on the Login screen (auth_graph) with a stale cached session —
     * analyticsDashboardFragment only exists inside main_graph, so
     * NavController threw IllegalArgumentException and killed the app on
     * every resume. This looked like a login failure but was actually a
     * crash loop unrelated to auth. Now wrapped in try/catch so a failed
     * navigation attempt degrades gracefully instead of crashing — the
     * cache still gets cleared either way, which is the important part.
     */
    private fun checkMorningAfterCleanup() {
        if (!RoutinePersistenceHelper.hasActiveSession()) return

        val lastActivity = RoutinePersistenceHelper.getLastActivityTimestamp()
        if (lastActivity == 0L) return

        val gapMillis = System.currentTimeMillis() - lastActivity
        val eightHoursMillis = 8L * 60 * 60 * 1000

        if (gapMillis > eightHoursMillis) {
            RoutinePersistenceHelper.clear()

            try {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host) as NavHostFragment
                val navController = navHostFragment.navController

                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
                navController.navigate(R.id.analyticsDashboardFragment, null, navOptions)
            } catch (e: Exception) {
                // Not logged in yet, or otherwise not in main_graph — the
                // cache is already cleared above, which is what actually
                // matters here. Nothing else to do if we can't navigate.
                android.util.Log.w("MainActivity", "checkMorningAfterCleanup: navigation skipped (not in main_graph)", e)
            }
        }
    }

    private fun handleDeeplinks(intent: android.content.Intent?) {
        intent?.let {
            try {
                SupabaseClient.client.handleDeeplinks(it)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Deep link handling failed", e)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeeplinks(intent)
    }

    private fun checkOnboardingStatus(navController: androidx.navigation.NavController, bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            try {
                val auth = SupabaseClient.client.auth

                // 1. Wait for Supabase to finish loading from storage
                auth.awaitInitialization()

                // 2. Double-check the current session status
                val session = auth.currentSessionOrNull()

                android.util.Log.d("MainActivity", "Session check: ${session?.user?.id != null}")

                val navInflater = navController.navInflater
                val graph = navInflater.inflate(R.navigation.nav_graph)

                if (session == null) {
                    // Not logged in, set the Auth Group as start
                    graph.setStartDestination(R.id.auth_graph)
                    navController.graph = graph
                } else {
                    val userId = UserSession.getUserId(applicationContext) ?: throw Exception("User ID not found")
                    val profile = UserProfileRepository().getOrCreateProfile(userId)
                    if (profile.onboardingCompleted) {
                        // Fully onboarded, set the Main Group as start
                        graph.setStartDestination(R.id.main_graph)
                        navController.graph = graph

                        // Handle background tasks for onboarded users
                        if (com.noctra.app.utils.NotificationPreferences.isWindDownEnabled(applicationContext)) {
                            requestNotificationPermissionIfNeeded()
                        }
                        WindDownNotificationScheduler.scheduleNext(applicationContext)
                    } else {
                        // Mid-flow recovery: Set the Onboarding Group as start
                        // and then adjust the internal start of that group
                        val onboardingGraph = graph.findNode(R.id.onboarding_graph) as androidx.navigation.NavGraph
                        val startStep = when (profile.onboardingStep) {
                            1 -> R.id.activityLibraryFragment
                            2 -> R.id.routineSequencingFragment
                            3 -> R.id.onboardingSummaryFragment
                            else -> R.id.bedtimeConfigFragment
                        }
                        onboardingGraph.setStartDestination(startStep)

                        graph.setStartDestination(R.id.onboarding_graph)
                        navController.graph = graph
                    }
                }

                // 3. ONLY after the graph is set, connect the BottomNav
                setupNavigationUI(navController, bottomNav)

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Onboarding check failed", e)
                // Fallback to default
                navController.setGraph(R.navigation.nav_graph)
                setupNavigationUI(navController, bottomNav)
            } finally {
                isLoading = false
            }
        }
    }

    private fun setupNavigationUI(navController: androidx.navigation.NavController, bottomNav: BottomNavigationView) {
        bottomNav.setupWithNavController(navController)

        // Navigation UI Logic
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 1. Visibility Logic: Show only for the 4 main tabs
            val mainTabs = setOf(
                R.id.companionFragment,
                R.id.routineHomeFragment,
                R.id.analyticsDashboardFragment,
                R.id.userProfileFragment
            )
            bottomNav.visibility = if (destination.id in mainTabs) View.VISIBLE else View.GONE
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // If notifications already granted, still check if alarms are needed
                requestAlarmPermissionIfNeeded()
            }
        } else {
            // Older version, skip notifications and check alarms
            requestAlarmPermissionIfNeeded()
        }
    }

    private fun requestAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = android.content.Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    // ─── DebugPanelListener ───────────────────────────────────────────────────

    override fun onResetOnboarding() {
        lifecycleScope.launch {
            val userId = UserSession.getUserId(applicationContext) ?: return@launch
            UserProfileRepository().resetOnboarding(userId)
            Toast.makeText(this@MainActivity, "Onboarding reset. Restart app to see flow.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onForceRoutineWindowOpen() {
        DebugSettings.setForceRoutineWindow(true)
        Toast.makeText(this, "Routine window forced open!", Toast.LENGTH_SHORT).show()
    }

    override fun onFireWindDownNotification() {
        val intent = android.content.Intent(this, com.noctra.app.receivers.WindDownNotificationReceiver::class.java)
        sendBroadcast(intent)
        Toast.makeText(this, "Notification triggered!", Toast.LENGTH_SHORT).show()
    }

    override fun onSimulateMorningSync() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext) ?: return@launch

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
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
                val yesterday = LocalDate.now().minusDays(1).toString()

                // 1. Record a missed session
                val missedSession = RoutineSession(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    sessionDate = yesterday,
                    startTimestamp = Instant.now().minusSeconds(86400).toString(),
                    status = "MISSED"
                )
                RoutineSessionRepository().insertSessions(listOf(missedSession))

                // 2. Queue warning (or reset streak if already warned)
                val repo = RewardLedgerRepository()
                val ledger = repo.getRewardLedger(userId)
                if (ledger != null) {
                    val wasWarned = ledger.hasFirstMiss
                    repo.updateRewardLedger(ledger.copy(
                        currentStreak = if (wasWarned) 0 else ledger.currentStreak,
                        hasFirstMiss = true,
                        lastUpdated = OffsetDateTime.now().toString()
                    ))
                }

                Toast.makeText(this@MainActivity, "Missed night simulated.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Simulate Missed Night failed", e)
                Toast.makeText(this@MainActivity, "Missed night simulation failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onTriggerEvolution() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
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
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
                com.noctra.app.domain.usecase.DataSeedingUseCase().seedMockData(userId)
                Toast.makeText(this@MainActivity, "Shop items and 7 days of data seeded!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Seed Demo Data failed", e)
                Toast.makeText(this@MainActivity, "Seeding failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onClearDemoData() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
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