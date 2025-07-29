package com.family.plantcare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.plantcare.viewmodel.HouseholdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    householdViewModel: HouseholdViewModel = viewModel(),
    onClose: () -> Unit,
    onHouseholdChanged: (String) -> Unit,
    onHouseholdDeleted: () -> Unit
) {
    var householdName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val error by householdViewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Household") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Create Household
            OutlinedTextField(
                value = householdName,
                onValueChange = { householdName = it },
                label = { Text("New Household Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    householdViewModel.createHousehold(householdName) { newHouseholdId ->
                        onHouseholdChanged(newHouseholdId) // ✅ return id
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create Household") }

            Divider()

            // Join Household
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it },
                label = { Text("Enter 6-digit Household Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    householdViewModel.joinHouseholdByCode(joinCode) { newHouseholdId ->
                        onHouseholdChanged(newHouseholdId) // ✅ return id
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Join Household") }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Divider()

            var deleteCode by remember { mutableStateOf("") }

            OutlinedTextField(
                value = deleteCode,
                onValueChange = { deleteCode = it },
                label = { Text("Enter Household ID to Delete (testing)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    householdViewModel.deleteHousehold(deleteCode) { success ->
                        if (success) onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Household", color = MaterialTheme.colorScheme.onError)
            }

        }
    }
}


