package com.noctra.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.noctra.app.workers.WindDownNotificationScheduler

class MainActivity : AppCompatActivity() {

    /** Bottom nav is hidden for the entire routine execution chain. */
    private val executionDestinations = setOf(
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility = if (destination.id in executionDestinations) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        requestNotificationPermissionIfNeeded()
        WindDownNotificationScheduler.scheduleNext(applicationContext)
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