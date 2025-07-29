package com.family.plantcare.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberDismissState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.family.plantcare.model.Plant
import com.family.plantcare.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.FractionalThreshold
import androidx.compose.ui.input.pointer.pointerInput
import com.family.plantcare.viewmodel.HouseholdViewModel
import java.text.SimpleDateFormat
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
    val householdViewModel: HouseholdViewModel = viewModel()


    var expanded by remember { mutableStateOf(false) }
    var profileDropdown by remember { mutableStateOf(false) }
    var showAddScreen by remember { mutableStateOf(false) }
    var showHouseholdScreen by remember { mutableStateOf(false) }

    var showLeaveDialog by remember { mutableStateOf(false) }
    var selectedHouseholdForLeave by remember { mutableStateOf<String?>(null) }


    // ✅ Track the selected plant for details
    var selectedPlant by remember { mutableStateOf<Plant?>(null) }

    if (showAddScreen) {
        AddPlantScreen(
            onPlantAdded = { showAddScreen = false },
            onCancel = { showAddScreen = false }
        )
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
                                val householdNames by mainViewModel.households.collectAsState()

                                user?.households?.take(6)?.forEach { householdId ->
                                    val name = householdNames[householdId] ?: "Household"

                                    DropdownMenuItem(
                                        text = { Text("Household: $name") },
                                        onClick = {
                                            mainViewModel.loadPlants(householdId)
                                            expanded = false
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                selectedHouseholdForLeave = householdId
                                                showLeaveDialog = true
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Leave")
                                            }
                                        }
                                    )

                                }


                                DropdownMenuItem(
                                    text = { Text("Create/Join Household") },
                                    onClick = {
                                        expanded = false
                                        showHouseholdScreen = true
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
        )
        { padding ->
            PlantList(
                plants = plants,
                onDelete = { plant -> mainViewModel.deletePlant(plant) },
                onWatered = { plant -> mainViewModel.markPlantWatered(plant) },                onSelect = { plant -> selectedPlant = plant },
                modifier = Modifier.padding(padding)
            )

            // ✅ Show detail dialog if a plant is selected
            selectedPlant?.let { plant ->
                PlantDetailDialog(
                    plant = plant,
                    onDismiss = { selectedPlant = null }
                )
            }

            if (showHouseholdScreen) {
                Dialog(onDismissRequest = { showHouseholdScreen = false }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 6.dp
                    ) {
                        HouseholdScreen(
                            onClose = { showHouseholdScreen = false },
                            onHouseholdChanged = { newHouseholdId ->
                                mainViewModel.reloadUser()
                                mainViewModel.loadPlants(newHouseholdId)
                                showHouseholdScreen = false
                            },
                            onHouseholdDeleted = {
                                mainViewModel.reloadUser()
                                mainViewModel.loadPlants(null) // back to personal plants
                                showHouseholdScreen = false
                            }
                        )

                    }
                }
            }

            if (showLeaveDialog && selectedHouseholdForLeave != null) {
                AlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title = { Text("Leave Household") },
                    text = { Text("Are you sure you want to leave this household? If you are the last member, it will be deleted permanently.") },
                    confirmButton = {
                        TextButton(onClick = {
                            householdViewModel.leaveHousehold(selectedHouseholdForLeave!!) { success ->
                                if (success) {
                                    mainViewModel.reloadUser()
                                    mainViewModel.loadPlants(null)
                                    expanded = false
                                }
                                showLeaveDialog = false
                            }
                        }) { Text("Yes, Leave") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLeaveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PlantCard(plant: Plant, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = plant.imageUrl ?: "",
                contentDescription = plant.name,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = plant.name, style = MaterialTheme.typography.titleMedium)
                if (!plant.commonName.isNullOrBlank()) {
                    Text(text = "Common: ${plant.commonName}")
                }
                Text(text = "Water in ${daysUntil(plant.nextWateringDate)} days")
            }
        }
    }
}

@Composable
fun PlantDetailDialog(plant: Plant, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = plant.imageUrl ?: "",
                    contentDescription = plant.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Text(plant.name, style = MaterialTheme.typography.headlineSmall)
                plant.commonName?.let {
                    Text("Common name: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Water every ${plant.wateringDays} day(s)", style = MaterialTheme.typography.bodyMedium)
                Text("Next watering in ${daysUntil(plant.nextWateringDate)} days", style = MaterialTheme.typography.bodyMedium)

                Text("Times watered: ${plant.timesWatered}", style = MaterialTheme.typography.bodyMedium)

                Text("Added: ${formatDate(plant.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlantList(
    plants: List<Plant>,
    onDelete: (Plant) -> Unit,
    onWatered: (Plant) -> Boolean,
    onSelect: (Plant) -> Unit,
    modifier: Modifier = Modifier
) {
    var plantToConfirmDelete by remember { mutableStateOf<Plant?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(items = plants, key = { it.id }) { plant ->
                val dismissState = rememberDismissState()

                // ✅ React to changes after state updates
                LaunchedEffect(dismissState.currentValue) {
                    val direction = dismissState.dismissDirection
                    val fraction = dismissState.progress.fraction

                    when (dismissState.currentValue) {
                        DismissValue.DismissedToStart -> {
                            if (direction == DismissDirection.EndToStart && fraction >= 0.5f) {
                                plantToConfirmDelete = plant
                            }
                            dismissState.animateTo(DismissValue.Default,tween(200))
                        }
                        DismissValue.DismissedToEnd -> {
                            if (direction == DismissDirection.StartToEnd && fraction >= 0.25f) {
                                val success = onWatered(plant)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (success) "💧 ${plant.name} watered!"
                                        else "🚫 Too early to water ${plant.name}!"
                                    )
                                }
                            }
                            dismissState.animateTo(DismissValue.Default, tween(200))
                        }
                        else -> {}
                    }
                }

                SwipeToDismiss(
                    state = dismissState,
                    directions = setOf(
                        DismissDirection.StartToEnd,
                        DismissDirection.EndToStart
                    ),
                    dismissThresholds = { direction ->
                        FractionalThreshold(
                            if (direction == DismissDirection.EndToStart) 0.5f else 0.25f
                        )
                    },
                    background = {
                        val direction = dismissState.dismissDirection
                        val isEarly = direction == DismissDirection.StartToEnd &&
                                plant.lastWatered != null &&
                                System.currentTimeMillis() < plant.nextWateringDate -
                                (plant.wateringDays * 24 * 60 * 60 * 1000 / 3)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = when (direction) {
                                DismissDirection.StartToEnd -> Alignment.CenterStart
                                DismissDirection.EndToStart -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (direction == DismissDirection.StartToEnd) {
                                    if (isEarly) {
                                        Icon(Icons.Default.Close, "Too early", tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Too Early!", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Icon(Icons.Default.Done, "Watered", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Watered", color = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (direction == DismissDirection.EndToStart) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    dismissContent = {
                        PlantCard(plant = plant, onClick = { onSelect(plant) })
                    }
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    if (plantToConfirmDelete != null) {
        AlertDialog(
            onDismissRequest = { plantToConfirmDelete = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete ${plantToConfirmDelete!!.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    val plant = plantToConfirmDelete!!
                    plantToConfirmDelete = null
                    onDelete(plant)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { plantToConfirmDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}


fun daysUntil(timestamp: Long): Long {
    val diff = timestamp - System.currentTimeMillis()
    return kotlin.math.ceil(diff / (1000.0 * 60 * 60 * 24)).toLong().coerceAtLeast(0)
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
