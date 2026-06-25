package com.noctra.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import com.noctra.app.ui.debug.DebugPanelListener
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { isLoading }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        // Check onboarding status and handle permissions if already completed
        checkOnboardingStatus(navController, bottomNav)
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

            // 2. Ensure "Companion" stays selected when in Customization
            if (destination.id == R.id.customizationFragment) {
                bottomNav.menu.findItem(R.id.companionFragment).isChecked = true
            }
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