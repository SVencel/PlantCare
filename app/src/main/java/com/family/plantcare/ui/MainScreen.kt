package com.family.plantcare.ui


import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.family.plantcare.model.Plant
import com.family.plantcare.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import androidx.compose.material.icons.filled.Menu
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel(),
    onLogout: () -> Unit

) {
    val plants by mainViewModel.plants.collectAsState()
    val user by mainViewModel.currentUser.collectAsState()
    val selectedHouseholdId by mainViewModel.selectedHouseholdId.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var profileDropdown by remember { mutableStateOf(false) }

    var showAddScreen by remember { mutableStateOf(false) }

    if (showAddScreen) {
        AddPlantScreen(onPlantAdded = {
            showAddScreen = false
        })
    } else {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                selectedHouseholdId == null -> "Your Plants"
                                else -> "Household Plants"
                            }
                        )
                    },
                    navigationIcon = {
                        Box(modifier = Modifier.padding(start = 16.dp)) {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Private") },
                                    onClick = {
                                        mainViewModel.loadPlants(null)
                                        expanded = false
                                    }
                                )
                                user?.households?.forEach { householdId ->
                                    DropdownMenuItem(
                                        text = { Text("Household: $householdId") },
                                        onClick = {
                                            mainViewModel.loadPlants(householdId)
                                            expanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Create/Join Household") },
                                    onClick = {
                                        // TODO: Navigate to household creation screen
                                        expanded = false
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        Box(modifier = Modifier.padding(end = 16.dp)) {
                            IconButton(onClick = { profileDropdown = true }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                            }
                            DropdownMenu(
                                expanded = profileDropdown,
                                onDismissRequest = { profileDropdown = false }) {
                                user?.let {
                                    DropdownMenuItem(
                                        text = { Text("Member since: ${formatDate(it.joinDate)}") },
                                        onClick = {}
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Plant count: ${plants.size}") },
                                        onClick = {}
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Logout") },
                                        onClick = {
                                            FirebaseAuth.getInstance().signOut()
                                            onLogout()
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            },

            floatingActionButton = {
                FloatingActionButton(onClick = { showAddScreen = true }) {

                    Icon(Icons.Default.Add, contentDescription = "Add Plant")
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(plants.size) { index ->
                    PlantCard(plant = plants[index])
                }
            }
        }
    }
}

@Composable
fun PlantCard(plant: Plant) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = plant.imageUrl?.let { File(it) },
                contentDescription = plant.name,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = plant.name, // user-given name or scientific
                    style = MaterialTheme.typography.titleMedium
                )

                if (!plant.commonName.isNullOrBlank()) {
                    Text(text = "Common: ${plant.commonName}")
                }
                Text(text = "Water in ${daysUntil(plant.nextWateringDate)} days")
            }
        }
    }
}

fun daysUntil(timestamp: Long): Long {
    val diff = timestamp - System.currentTimeMillis()
    return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
