package com.noctra.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
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
            // 1. Hide bottom nav on certain destinations
            bottomNav.visibility = when (destination.id) {
                R.id.settingsFragment -> View.GONE
                else -> View.VISIBLE
            }

            // 2. Ensure "Companion" stays selected when in Customization
            if (destination.id == R.id.customizationFragment) {
                bottomNav.menu.findItem(R.id.companionFragment).isChecked = true
            }
        }
    }
}