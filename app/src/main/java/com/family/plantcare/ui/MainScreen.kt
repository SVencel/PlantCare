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
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.rememberDismissState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Done
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
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
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
            PlantList(
                plants = plants,
                onDelete = { plant -> mainViewModel.deletePlant(plant) },
                onWatered = { plant -> mainViewModel.markPlantWatered(plant) },
                modifier = Modifier.padding(padding)
            )

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
                model = plant.imageUrl ?: "",
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


@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlantList(
    plants: List<Plant>,
    onDelete: (Plant) -> Unit,
    onWatered: (Plant) -> Unit,
    modifier: Modifier = Modifier
) {
    var plantToConfirmDelete by remember { mutableStateOf<Plant?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Box {
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            items(
                items = plants,
                key = { plant -> plant.id }
            ) { plant ->
                AnimatedVisibility(
                    visible = true,
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
                ) {
                    val dismissState = rememberDismissState(
                        confirmStateChange = { state ->
                            when (state) {
                                DismissValue.DismissedToStart -> { // left swipe → delete
                                    plantToConfirmDelete = plant
                                    false
                                }
                                DismissValue.DismissedToStart, DismissValue.DismissedToEnd -> false
                                DismissValue.DismissedToEnd -> { // right swipe → watered
                                    onWatered(plant)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("💧 ${plant.name} watered!")
                                    }
                                    false
                                }
                                else -> false
                            }
                        }
                    )

                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(
                            DismissDirection.StartToEnd, // right swipe = watered
                            DismissDirection.EndToStart   // left swipe = delete
                        ),
                        background = {
                            val direction = dismissState.dismissDirection
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
                                        Icon(
                                            imageVector = Icons.Default.Done,
                                            contentDescription = "Watered",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Watered", color = MaterialTheme.colorScheme.primary)
                                    } else if (direction == DismissDirection.EndToStart) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        },
                        dismissContent = { PlantCard(plant = plant) }
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }

    // Delete confirmation
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
    return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
