package com.family.plantcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.Manifest
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.plantcare.ui.LoginScreen
import com.family.plantcare.ui.MainScreen
import com.google.firebase.FirebaseApp
import com.family.plantcare.ui.theme.PlantCareTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.work.*
import com.family.plantcare.notifications.WateringWorker
import com.family.plantcare.viewmodel.MainViewModel
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialize Firebase
        FirebaseApp.initializeApp(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        scheduleDailyWateringCheck()

        setContent {
            PlantCareTheme {
                val currentUser = FirebaseAuth.getInstance().currentUser
                var isLoggedIn by remember { mutableStateOf(currentUser != null) }
                val mainViewModel: MainViewModel = viewModel()

                if (isLoggedIn) {
                    LaunchedEffect(Unit) {
                        mainViewModel.reloadUser() // ✅ always repopulate household names on app start
                    }

                    MainScreen(
                        mainViewModel = mainViewModel,
                        onLogout = { isLoggedIn = false }
                    )
                } else {
                    LoginScreen(onLoginSuccess = {
                        isLoggedIn = true
                    })
                }
            }
        }
    }

    private fun scheduleDailyWateringCheck() {
        val workRequest = PeriodicWorkRequestBuilder<WateringWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS) // first run delay
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "watering_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
