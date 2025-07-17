package com.family.plantcare.ui

import android.util.Patterns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.plantcare.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    loginViewModel: LoginViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var household by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    val isLoading by loginViewModel.isLoading.collectAsState()
    val error by loginViewModel.error.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show backend error (from Firebase) in a snackbar
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    fun validateAndSubmit() {
        localError = null
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()

        when {
            trimmedEmail.isEmpty() -> localError = "Email is required."
            !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() -> localError = "Invalid email address."
            password.length < 6 -> localError = "Password must be at least 6 characters."
            isRegisterMode && trimmedUsername.isEmpty() -> localError = "Username is required."
            else -> {
                if (isRegisterMode) {
                    loginViewModel.registerUser(trimmedEmail, password, trimmedUsername, household.trim())
                } else {
                    loginViewModel.loginUser(trimmedEmail, password, onLoginSuccess)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Text(
                    text = if (isRegisterMode) "🌿 Create Your PlantCare Account" else "🌿 Welcome to PlantCare",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = household,
                        onValueChange = { household = it },
                        label = { Text("Household (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (localError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = localError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { validateAndSubmit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRegisterMode) "Register" else "Login")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = {
                        isRegisterMode = !isRegisterMode
                        localError = null
                    }) {
                        Text(
                            if (isRegisterMode)
                                "Already have an account? Login here"
                            else
                                "Don't have an account? Register here"
                        )
                    }
                }
            }
        }
    }
}
