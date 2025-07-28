package com.family.plantcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.Manifest
import android.os.Build
import androidx.core.app.ActivityCompat
import com.family.plantcare.ui.LoginScreen
import com.family.plantcare.ui.MainScreen
import com.google.firebase.FirebaseApp
import com.family.plantcare.ui.theme.PlantCareTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.work.*
import com.family.plantcare.notifications.WateringWorker
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

        Log.d("PlantCare", "Firebase initialized!")

        scheduleDailyWateringCheck()

        setContent {
            PlantCareTheme {
                val currentUser = FirebaseAuth.getInstance().currentUser
                var isLoggedIn by remember { mutableStateOf(currentUser != null) }

                if (isLoggedIn) {
                    MainScreen(
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
