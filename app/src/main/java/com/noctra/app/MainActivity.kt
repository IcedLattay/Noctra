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
import com.noctra.app.ui.debug.DebugPanelListener
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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