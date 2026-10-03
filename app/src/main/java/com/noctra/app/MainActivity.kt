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
import com.noctra.app.ui.debug.StagePreviewDialogFragment
import com.noctra.app.ui.companion.MorningSleepPopupDialog
import com.noctra.app.ui.companion.StreakNoticeDialogFragment
import com.noctra.app.ui.companion.CompanionViewModel.CompanionNotice
import com.noctra.app.ui.companion.CompanionViewModel
import com.noctra.app.ui.companion.EvolutionDialogFragment
import androidx.lifecycle.ViewModelProvider
import com.noctra.app.ui.routine.RoutineViewModel
import com.noctra.app.ui.routine.home.ResumeRoutineDialogFragment
import com.noctra.app.utils.DebugSettings
import com.noctra.app.utils.UserSession
import com.noctra.app.utils.NetworkObserver
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
import com.noctra.app.data.model.UserProfile

class MainActivity : AppCompatActivity(), DebugPanelListener {

    private var isLoading = true
    private lateinit var networkObserver: NetworkObserver

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
        //DebugSettings.setForceRoutineWindow(true) // TEMP — remove before final submission
        //DebugSettings.setSkipCompletionCheck(true) // TEMP — remove before final submission

        networkObserver = NetworkObserver(applicationContext)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Handle deep links from Supabase (e.g. password recovery)
        handleDeeplinks(intent)

        // Check connectivity before proceeding
        checkConnectivityAndInit(navController, bottomNav)
    }

    private fun checkConnectivityAndInit(
        navController: androidx.navigation.NavController,
        bottomNav: BottomNavigationView
    ) {
        if (!networkObserver.checkNow()) {
            showOfflineUI()
            return
        }

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

    // ─── Offline UI ──────────────────────────────────────────────────────────

    private fun showOfflineUI() {
        isLoading = false
        val navHost = findViewById<View>(R.id.nav_host)
        val offlineView = findViewById<View>(R.id.offlineView)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        navHost.visibility = View.GONE
        bottomNav.visibility = View.GONE
        offlineView.visibility = View.VISIBLE

        offlineView.findViewById<android.widget.Button>(R.id.btnRetry).setOnClickListener {
            if (networkObserver.checkNow()) {
                hideOfflineUI()
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host) as NavHostFragment
                val navController = navHostFragment.navController
                val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
                checkOnboardingStatus(navController, bottomNav)
            }
        }
    }

    private fun hideOfflineUI() {
        val navHost = findViewById<View>(R.id.nav_host)
        val offlineView = findViewById<View>(R.id.offlineView)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        offlineView.visibility = View.GONE
        navHost.visibility = View.VISIBLE
        bottomNav.visibility = View.VISIBLE
    }

    // ─── Global Resume Popup (tasks 7 & 8) ───────────────────────────────────

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
        if (supportFragmentManager.findFragmentByTag(ResumeRoutineDialogFragment.TAG) != null) return

        ResumeRoutineDialogFragment.newInstance(
            currentStepIndex = state.stepIndex,
            totalSteps = state.totalSteps,
            awayMinutes = state.awayMinutes
        ).show(supportFragmentManager, ResumeRoutineDialogFragment.TAG)
    }

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

    // ─── Morning After Cleanup (task 9/10) ──────────────────────────────────

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
                        android.util.Log.d(
                            "MainActivity",
                            "Onboarding resume: userId=$userId step=${profile.onboardingStep} completed=${profile.onboardingCompleted}"
                        )
                        val onboardingGraph = graph.findNode(R.id.onboarding_graph) as androidx.navigation.NavGraph
                        val startStep = when (profile.onboardingStep) {
                            1 -> R.id.activityLibraryFragment
                            2 -> R.id.routineSequencingFragment
                            3 -> R.id.healthEducationFragment
                            4 -> R.id.healthGrantFragment
                            5 -> R.id.onboardingSummaryFragment
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

    override fun onPreviewEvolution() {
        // Zero-write preview: shows the evolution dialog for the next stage directly.
        // Rendered animation comes from the already-loaded CompanionViewModel state,
        // so visit the Companion tab first for the Shleepy preview to appear.
        val companionVm = ViewModelProvider(this)[CompanionViewModel::class.java]
        val currentLevel = companionVm.uiState.value.evolutionState?.stageLevel ?: 1
        val previewLevel = (currentLevel + 1).coerceAtMost(5)
        EvolutionDialogFragment.newInstance(currentLevel, previewLevel)
            .show(supportFragmentManager, "EvolutionPreview")
    }

    override fun onPreviewStageAnimations() {
        StagePreviewDialogFragment()
            .show(supportFragmentManager, "StagePreview")
    }

    override fun onPreviewMorningRecap() {
        MorningSleepPopupDialog.newInstance(82, 7)
            .show(supportFragmentManager, "MorningPreview")
    }

    override fun onPreviewStreakRestored() {
        StreakNoticeDialogFragment.newInstance(CompanionNotice.RESTORED)
            .show(supportFragmentManager, "StreakPreview")
    }

    override fun onPreviewStreakLost() {
        StreakNoticeDialogFragment.newInstance(CompanionNotice.LOST)
            .show(supportFragmentManager, "StreakPreview")
    }

    override fun onPreviewStreakWarning() {
        StreakNoticeDialogFragment.newInstance(CompanionNotice.WARNING)
            .show(supportFragmentManager, "StreakPreview")
    }

    override fun onPreviewResumeDialog() {
        ResumeRoutineDialogFragment.newInstance(1, 3, 25L)
            .show(supportFragmentManager, "ResumePreview")
    }

    override fun onDumpSleepSession() {
        lifecycleScope.launch {
            try {
                val context = applicationContext
                if (!com.noctra.app.utils.HealthConnectPermissionHelper.isAvailable(context)) {
                    Toast.makeText(context, "Health Connect unavailable", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                val granted = com.noctra.app.utils.HealthConnectPermissionHelper.getGrantedPermissions(client)
                if (!com.noctra.app.utils.HealthConnectPermissionHelper.hasSleepPermission(granted)) {
                    Toast.makeText(context, "Sleep permission not granted", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val now = java.time.Instant.now()
                val response = client.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        androidx.health.connect.client.records.SleepSessionRecord::class,
                        androidx.health.connect.client.time.TimeRangeFilter.between(
                            now.minus(48, java.time.temporal.ChronoUnit.HOURS), now
                        )
                    )
                )
                val latest = response.records.maxByOrNull {
                    it.endTime.epochSecond
                }
                if (latest == null) {
                    android.util.Log.d("SleepDump", "no sessions in last 48h")
                    Toast.makeText(context, "No sessions in last 48h", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                android.util.Log.d("SleepDump", "window ${latest.startTime} -> ${latest.endTime} (${latest.stages.size} stages, by ${latest.metadata.dataOrigin.packageName})")
                latest.stages.forEach { stage ->
                    val name = when (stage.stage) {
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
                        else -> "UNKNOWN(${stage.stage})"
                    }
                    android.util.Log.d("SleepDump", "  $name ${stage.startTime} -> ${stage.endTime}")
                }
                Toast.makeText(context, "Dumped ${latest.stages.size} stages — see logcat", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("SleepDump", "dump failed", e)
                Toast.makeText(applicationContext, "Dump failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDumpHeartRate() {
        lifecycleScope.launch {
            try {
                val context = applicationContext
                if (!com.noctra.app.utils.HealthConnectPermissionHelper.isAvailable(context)) {
                    Toast.makeText(context, "Health Connect unavailable", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                val granted = com.noctra.app.utils.HealthConnectPermissionHelper.getGrantedPermissions(client)
                if (!com.noctra.app.utils.HealthConnectPermissionHelper.hasHeartRatePermission(granted)) {
                    Toast.makeText(context, "Heart rate permission not granted", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val now = java.time.Instant.now()
                val twoDaysAgo = now.minus(48, java.time.temporal.ChronoUnit.HOURS)
                // Latest sleep window first, so samples are shown in context
                val sessions = client.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        androidx.health.connect.client.records.SleepSessionRecord::class,
                        androidx.health.connect.client.time.TimeRangeFilter.between(twoDaysAgo, now)
                    )
                ).records
                val latest = sessions.maxByOrNull { it.endTime.epochSecond }
                val windowStart: java.time.Instant
                val windowEnd: java.time.Instant
                val windowLabel: String
                if (latest != null) {
                    windowStart = latest.startTime
                    windowEnd = latest.endTime
                    windowLabel = "latest sleep window $windowStart -> $windowEnd"
                } else {
                    windowStart = now.minus(12, java.time.temporal.ChronoUnit.HOURS)
                    windowEnd = now
                    windowLabel = "last 12h (no sleep session found)"
                }
                val samples = client.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        androidx.health.connect.client.records.HeartRateRecord::class,
                        androidx.health.connect.client.time.TimeRangeFilter.between(windowStart, windowEnd)
                    )
                ).records.flatMap { it.samples }
                    .sortedBy { it.time.epochSecond }
                if (samples.isEmpty()) {
                    android.util.Log.d("HeartDump", "no HR samples in $windowLabel")
                    Toast.makeText(context, "No HR samples in window", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val avg = samples.map { it.beatsPerMinute }.average()
                android.util.Log.d("HeartDump", "${samples.size} samples in $windowLabel, avg=${"%.1f".format(avg)} bpm")
                samples.forEach { sample ->
                    android.util.Log.d("HeartDump", "  ${sample.time} -> ${sample.beatsPerMinute} bpm")
                }
                Toast.makeText(context, "Dumped ${samples.size} samples — see logcat", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("HeartDump", "dump failed", e)
                Toast.makeText(applicationContext, "Dump failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResyncLastNight() {
        lifecycleScope.launch {
            try {
                val userId = UserSession.getUserId(applicationContext) ?: return@launch
                val yesterday = java.time.LocalDate.now().minusDays(1)
                val result = com.noctra.app.data.repository.SleepSyncManager()
                    .syncSessionDate(userId, yesterday)
                Toast.makeText(applicationContext, "Resync: $result", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Resync failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackfillNow() {
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "AuditBackfillNow",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<com.noctra.app.workers.AuditBackfillWorker>()
                .build()
        )
        Toast.makeText(applicationContext, "Backfill enqueued — watch logcat", Toast.LENGTH_SHORT).show()
    }

    override fun onPreviewSequencing() {
        try {
            val navHost =
                supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment
            val controller = navHost.navController
            val args = android.os.Bundle().apply { putBoolean("previewMode", true) }
            try {
                controller.navigate(R.id.action_global_previewSequencing, args)
            } catch (e: Exception) {
                // Nested-graph scoping can reject the cross-graph hop:
                // step back to the main graph first, then go direct. Back
                // from the preview then lands on the pre-debug screen.
                controller.popBackStack()
                controller.navigate(R.id.editRoutineSequencingFragment, args)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Preview arrange screen failed", e)
            Toast.makeText(applicationContext, "Preview failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPreviewBreathing() = previewActivityFlow(
        labels = setOf("Slow-Paced Breathing"),
        actionId = R.id.action_global_previewBreathing
    )

    override fun onPreviewAudioscape() = previewActivityFlow(
        labels = setOf(
            "Bedtime To-Do List Writing", "Reading", "White/Pink Noise",
            "Warm Shower", "Mindfulness", "Low-Stimulus Audio Listening"
        ),
        actionId = R.id.action_global_previewAudioscape
    )

    override fun onPreviewGratitude() = previewActivityFlow(
        labels = setOf("Gratitude Journaling"),
        actionId = R.id.action_global_previewGratitude
    )

    override fun onPreviewTimer() = previewActivityFlow(
        labels = setOf("Progressive Muscle Relaxation", "Bedtime Stretching"),
        actionId = R.id.action_global_previewTimer
    )

    /**
     * Debug preview: seeds the shared RoutineViewModel with one library
     * activity (memory only, writes disabled via previewMode) and opens
     * that flow's player. Back returns to the debug panel.
     */
    private fun previewActivityFlow(labels: Set<String>, actionId: Int) {
        lifecycleScope.launch {
            try {
                val vm = androidx.lifecycle.ViewModelProvider(this@MainActivity)
                    .get(com.noctra.app.ui.routine.RoutineViewModel::class.java)
                val library = com.noctra.app.data.repository.RoutineRepository().getActivityLibrary()
                val activity = labels.mapNotNull { label ->
                    library.firstOrNull { it.label == label }
                }.firstOrNull() ?: library.firstOrNull() ?: return@launch
                vm.setupSession(listOf(activity), "", 0)
                vm.previewMode = true
                val navHost =
                    supportFragmentManager.findFragmentById(R.id.nav_host) as androidx.navigation.fragment.NavHostFragment
                try {
                    navHost.navController.navigate(actionId)
                } catch (e: Exception) {
                    navHost.navController.popBackStack()
                    navHost.navController.navigate(actionId)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Preview activity flow failed", e)
                Toast.makeText(applicationContext, "Preview failed: ${e.message}", Toast.LENGTH_LONG).show()
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