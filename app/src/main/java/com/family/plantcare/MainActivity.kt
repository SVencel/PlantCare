package com.family.plantcare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.family.plantcare.ui.theme.PlantCareTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialize Firebase
        FirebaseApp.initializeApp(this)
        Log.d("PlantCare", "Firebase initialized!")

        setContent {
            PlantCareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WelcomeScreen()
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🌱 Welcome to PlantCare!", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Firebase has been initialized successfully.")
    }
}
