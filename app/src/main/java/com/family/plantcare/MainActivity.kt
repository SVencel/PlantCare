package com.family.plantcare

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.family.plantcare.notifications.WateringWorker
import com.family.plantcare.ui.LoginScreen
import com.family.plantcare.ui.MainScreen
import com.family.plantcare.ui.OnboardingScreen
import com.family.plantcare.ui.theme.PlantCareTheme
import com.family.plantcare.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        scheduleDailyWateringCheck()

        val prefs = getSharedPreferences("plantcare", MODE_PRIVATE)

        setContent {
            PlantCareTheme {
                var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }
                var isLoggedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
                val mainViewModel: MainViewModel = viewModel()

                when {
                    !onboardingDone -> {
                        OnboardingScreen(onFinish = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            onboardingDone = true
                        })
                    }
                    isLoggedIn -> {
                        LaunchedEffect(Unit) { mainViewModel.loadUserData() }
                        MainScreen(mainViewModel = mainViewModel, onLogout = { isLoggedIn = false })
                    }
                    else -> {
                        LoginScreen(onLoginSuccess = { isLoggedIn = true })
                    }
                }
            }
        }
    }

    private fun scheduleDailyWateringCheck() {
        val workRequest = PeriodicWorkRequestBuilder<WateringWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "watering_check", ExistingPeriodicWorkPolicy.UPDATE, workRequest
        )
    }
}
