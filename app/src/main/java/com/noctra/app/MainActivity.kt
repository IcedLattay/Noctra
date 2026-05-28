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
import com.noctra.app.utils.UserSession
import com.noctra.app.workers.WindDownNotificationScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

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
}