package com.family.plantcare.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.family.plantcare.model.Plant
import com.family.plantcare.model.Room
import com.family.plantcare.ui.theme.WaterGoodGreen
import com.family.plantcare.ui.theme.WaterGoodGreenContainer
import com.family.plantcare.ui.theme.WaterNowRed
import com.family.plantcare.ui.theme.WaterNowRedContainer
import com.family.plantcare.ui.theme.WaterSoonAmber
import com.family.plantcare.ui.theme.WaterSoonAmberContainer
import com.family.plantcare.viewmodel.HouseholdViewModel
import com.family.plantcare.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar as JavaCalendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel(),
    onLogout: () -> Unit
) {
    val plants by mainViewModel.plants.collectAsState()
    val plantsLoading by mainViewModel.plantsLoading.collectAsState()
    val user by mainViewModel.currentUser.collectAsState()
    val selectedHouseholdId by mainViewModel.selectedHouseholdId.collectAsState()
    val householdViewModel: HouseholdViewModel = viewModel()
    val householdMap by mainViewModel.households.collectAsState()
    val householdMembers by mainViewModel.householdMembers.collectAsState()

    val currentHousehold = selectedHouseholdId?.let { householdMap[it] }
    val currentHouseholdName = currentHousehold?.first
    val currentHouseholdCode = currentHousehold?.second

    val currentHouseholdRooms by mainViewModel.currentHouseholdRooms.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }
    var profileDropdown by remember { mutableStateOf(false) }
    var showAddScreen by remember { mutableStateOf(false) }
    var showQuickAddScreen by remember { mutableStateOf(false) }
    var showHouseholdScreen by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var selectedHouseholdForLeave by remember { mutableStateOf<String?>(null) }
    var selectedPlant by remember { mutableStateOf<Plant?>(null) }
    var showHouseholdInfoDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoomFilter by remember { mutableStateOf<String?>(null) }
    var showAddRoomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedHouseholdId) { selectedRoomFilter = null }

    if (showAddScreen) {
        AddPlantScreen(onPlantAdded = { showAddScreen = false }, onCancel = { showAddScreen = false })
        return
    }

    if (showQuickAddScreen) {
        QuickAddScreen(onPlantAdded = { showQuickAddScreen = false }, onCancel = { showQuickAddScreen = false })
        return
    }

    val filteredPlants = if (searchQuery.isBlank()) plants
    else plants.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.commonName?.contains(searchQuery, ignoreCase = true) == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = if (selectedHouseholdId != null) Modifier.clickable { showHouseholdInfoDialog = true } else Modifier
                    ) {
                        val hasNewActivity by mainViewModel.hasNewActivity.collectAsState()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    selectedHouseholdId == null -> "Your Plants"
                                    currentHouseholdName != null -> "$currentHouseholdName Plants"
                                    else -> "Household Plants"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (hasNewActivity) {
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Private") }, onClick = { mainViewModel.loadPlants(null); expanded = false })
                            val householdNames by mainViewModel.households.collectAsState()
                            user?.households?.take(6)?.forEach { householdId ->
                                val name = householdNames[householdId] ?: "Household"
                                DropdownMenuItem(
                                    text = { Text("Household: $name") },
                                    onClick = { mainViewModel.loadPlants(householdId); expanded = false },
                                    trailingIcon = {
                                        IconButton(onClick = { selectedHouseholdForLeave = householdId; showLeaveDialog = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Leave")
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(text = { Text("Create/Join Household") }, onClick = { expanded = false; showHouseholdScreen = true })
                        }
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        IconButton(onClick = { profileDropdown = true }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                        }
                        val hemisphere by mainViewModel.hemisphere.collectAsState()
                        var showHemisphereDialog by remember { mutableStateOf(false) }

                        DropdownMenu(expanded = profileDropdown, onDismissRequest = { profileDropdown = false }) {
                            user?.let {
                                DropdownMenuItem(text = { Text("Member since: ${formatDate(it.joinDate)}") }, onClick = {})
                                DropdownMenuItem(text = { Text("Plant count: ${plants.size}") }, onClick = {})
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("🌍 Region: ${if (hemisphere == "south") "Southern Hemisphere" else "Northern Hemisphere"}") },
                                onClick = { profileDropdown = false; showHemisphereDialog = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = { FirebaseAuth.getInstance().signOut(); onLogout() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
                                onClick = { profileDropdown = false; showDeleteAccountDialog = true }
                            )
                        }

                        if (showHemisphereDialog) {
                            AlertDialog(
                                onDismissRequest = { showHemisphereDialog = false },
                                title = { Text("Your Region") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("This adjusts seasonal watering schedules to match your climate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        listOf("north" to "🌍 Northern Hemisphere\nEurope, North America, Asia", "south" to "🌏 Southern Hemisphere\nAustralia, South America, South Africa").forEach { (value, label) ->
                                            val lines = label.split("\n")
                                            Surface(
                                                onClick = { mainViewModel.setHemisphere(value); showHemisphereDialog = false },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (hemisphere == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(lines[0], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text(lines[1], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showHemisphereDialog = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { showQuickAddScreen = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text("⚡", fontSize = 18.sp)
                }
                FloatingActionButton(onClick = { showAddScreen = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Plant")
                }
            }
        }
    ) { padding ->
        val isUserLoading = user == null
        val roomFilteredPlants = if (selectedRoomFilter != null)
            filteredPlants.filter { it.roomId == selectedRoomFilter }
        else filteredPlants
        val todayPlants = roomFilteredPlants.filter { daysUntil(it.nextWateringDate).toInt() == 0 }
        val futurePlants = roomFilteredPlants.filter { daysUntil(it.nextWateringDate) > 0 }.sortedBy { daysUntil(it.nextWateringDate) }

        val seasonMonth = remember { JavaCalendar.getInstance().get(JavaCalendar.MONTH) }
        val hemisphere by mainViewModel.hemisphere.collectAsState()
        val isSouthHemi = hemisphere == "south"
        val summerMonths = if (isSouthHemi) setOf(JavaCalendar.DECEMBER, JavaCalendar.JANUARY, JavaCalendar.FEBRUARY)
                           else setOf(JavaCalendar.JUNE, JavaCalendar.JULY, JavaCalendar.AUGUST)
        val winterMonths = if (isSouthHemi) setOf(JavaCalendar.JUNE, JavaCalendar.JULY, JavaCalendar.AUGUST)
                           else setOf(JavaCalendar.DECEMBER, JavaCalendar.JANUARY, JavaCalendar.FEBRUARY)
        val (seasonEmoji, seasonMsg) = when (seasonMonth) {
            in summerMonths -> "☀️" to "Summer: watering intervals are 15% shorter to beat the heat."
            in winterMonths -> "❄️" to "Winter: watering intervals are 20% longer — plants rest more."
            else -> "" to ""
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar (only when plants exist)
            if (plants.isNotEmpty() && !isUserLoading && !plantsLoading) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search plants…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Room chip filter bar
            if (selectedHouseholdId != null && currentHouseholdRooms.isNotEmpty() && !isUserLoading && !plantsLoading) {
                RoomFilterRow(
                    rooms = currentHouseholdRooms,
                    selectedRoomId = selectedRoomFilter,
                    onRoomSelected = { selectedRoomFilter = it },
                    onAddRoom = { showAddRoomDialog = true }
                )
            }

            // Seasonal smart watering banner
            if (seasonMsg.isNotEmpty() && !isUserLoading && !plantsLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    color = if (seasonMonth in summerMonths) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "$seasonEmoji $seasonMsg",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            when {
                isUserLoading || plantsLoading -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)) {
                        items(4) { PlantCardSkeleton() }
                    }
                }
                filteredPlants.isEmpty() && searchQuery.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No plants match \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                roomFilteredPlants.isEmpty() && selectedRoomFilter != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🪴", fontSize = 48.sp)
                            Text("No plants in this room yet", style = MaterialTheme.typography.titleMedium)
                            Text("Add a plant and assign it here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                plants.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🌱", fontSize = 64.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("No plants yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("Tap + to add your first plant", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("⚡ Tap the lightning bolt to quick-add from our library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    val useRoomGrouping = selectedHouseholdId != null && currentHouseholdRooms.isNotEmpty() && searchQuery.isBlank() && selectedRoomFilter == null
                    LazyColumn(contentPadding = PaddingValues(bottom = 104.dp, top = 8.dp)) {
                        // Always show "Needs Water Today" banner at top
                        if (todayPlants.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(WaterNowRedContainer)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Needs Water Today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = WaterNowRed)
                                        Text("${todayPlants.size} plant${if (todayPlants.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = WaterNowRed.copy(alpha = 0.75f))
                                    }
                                    Surface(shape = RoundedCornerShape(8.dp), color = WaterNowRed, modifier = Modifier.clickable { todayPlants.forEach { mainViewModel.markPlantWatered(it) } }) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Done, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Text("Water all", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (useRoomGrouping) {
                            val plantsByRoom = filteredPlants.groupBy { it.roomId }
                            currentHouseholdRooms.forEach { room ->
                                val roomPlants = (plantsByRoom[room.id] ?: emptyList()).sortedBy { daysUntil(it.nextWateringDate) }
                                if (roomPlants.isNotEmpty()) {
                                    item(key = "header_${room.id}") {
                                        Row(
                                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(room.icon, fontSize = 16.sp)
                                            Text(room.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    items(roomPlants, key = { it.id }) { plant ->
                                        PlantSwipeItem(plant = plant, mainViewModel = mainViewModel, snackbarHostState = snackbarHostState, onSelect = { selectedPlant = it })
                                    }
                                }
                            }
                            val unassigned = (plantsByRoom[null] ?: emptyList()).sortedBy { daysUntil(it.nextWateringDate) }
                            if (unassigned.isNotEmpty()) {
                                item(key = "header_unassigned") {
                                    Text("Other", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp))
                                }
                                items(unassigned, key = { it.id }) { plant ->
                                    PlantSwipeItem(plant = plant, mainViewModel = mainViewModel, snackbarHostState = snackbarHostState, onSelect = { selectedPlant = it })
                                }
                            }
                        } else {
                            items(todayPlants, key = { it.id }) { plant ->
                                PlantSwipeItem(plant = plant, mainViewModel = mainViewModel, snackbarHostState = snackbarHostState, onSelect = { selectedPlant = it })
                            }
                            if (futurePlants.isNotEmpty()) {
                                item {
                                    Text("Upcoming", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
                                }
                            }
                            items(futurePlants, key = { it.id }) { plant ->
                                PlantSwipeItem(plant = plant, mainViewModel = mainViewModel, snackbarHostState = snackbarHostState, onSelect = { selectedPlant = it })
                            }
                        }
                    }
                }
            }
        }

        selectedPlant?.let { plant ->
            PlantDetailDialog(
                plant = plant,
                onDismiss = { selectedPlant = null },
                mainViewModel = mainViewModel,
                householdViewModel = householdViewModel
            )
        }

        if (showHouseholdScreen) {
            Dialog(onDismissRequest = { showHouseholdScreen = false }) {
                Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight(), shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                    HouseholdScreen(
                        onClose = { showHouseholdScreen = false },
                        onHouseholdChanged = { newId -> mainViewModel.reloadUser(); mainViewModel.loadPlants(newId); showHouseholdScreen = false },
                        onHouseholdDeleted = { mainViewModel.reloadUser(); mainViewModel.loadPlants(null); showHouseholdScreen = false }
                    )
                }
            }
        }

        if (showLeaveDialog && selectedHouseholdForLeave != null) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Leave Household") },
                text = { Text("Are you sure? If you are the last member, the household will be deleted permanently.") },
                confirmButton = {
                    TextButton(onClick = {
                        householdViewModel.leaveHousehold(selectedHouseholdForLeave!!) { success ->
                            if (success) { mainViewModel.reloadUser(); mainViewModel.loadPlants(null); expanded = false }
                            showLeaveDialog = false
                        }
                    }) { Text("Yes, Leave") }
                },
                dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") } }
            )
        }

        if (showDeleteAccountDialog) {
            val scope = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                title = { Text("Delete Account") },
                text = { Text("This will permanently delete your account and all your private plants. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteAccountDialog = false
                        mainViewModel.deleteAccount(
                            onSuccess = { FirebaseAuth.getInstance().signOut(); onLogout() },
                            onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") } }
            )
        }

        if (showAddRoomDialog && selectedHouseholdId != null) {
            var newRoomName by remember { mutableStateOf("") }
            var newRoomIcon by remember { mutableStateOf("🌿") }
            val roomIcons = listOf("🌿", "🛋️", "🛏️", "🍳", "🚿", "🏡", "🌞", "💡", "🪟", "📚")
            AlertDialog(
                onDismissRequest = { showAddRoomDialog = false },
                title = { Text("Add Room") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            roomIcons.forEach { icon ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (icon == newRoomIcon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { newRoomIcon = icon }
                                        .padding(4.dp)
                                ) { Text(icon, fontSize = 20.sp) }
                            }
                        }
                        OutlinedTextField(
                            value = newRoomName,
                            onValueChange = { newRoomName = it },
                            label = { Text("Room name") },
                            placeholder = { Text("e.g. Living Room") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            householdViewModel.addRoom(selectedHouseholdId!!, newRoomName.trim(), newRoomIcon) {
                                showAddRoomDialog = false
                            }
                        },
                        enabled = newRoomName.isNotBlank()
                    ) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { showAddRoomDialog = false }) { Text("Cancel") } }
            )
        }

        if (showHouseholdInfoDialog && selectedHouseholdId != null) {
            LaunchedEffect(Unit) { mainViewModel.markActivitiesSeen() }
            val activities by mainViewModel.activities.collectAsState()
            val hasNewActivity by mainViewModel.hasNewActivity.collectAsState()
            var newRoomName by remember { mutableStateOf("") }
            var selectedRoomIcon by remember { mutableStateOf("🌿") }
            val roomIcons = listOf("🌿", "🛋️", "🛏️", "🍳", "🚿", "🏡", "🌞", "💡", "🪟", "📚")

            Dialog(onDismissRequest = { showHouseholdInfoDialog = false }) {
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(
                            (currentHouseholdName ?: "Household Info") + if (hasNewActivity) " ✨" else "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        currentHouseholdCode?.let { Text("Join code: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Members
                            Text("👥 Members", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (householdMembers.isEmpty()) Text("No members found.", style = MaterialTheme.typography.bodySmall)
                            else householdMembers.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Rooms
                            Text("🏠 Rooms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (currentHouseholdRooms.isEmpty()) {
                                Text("No rooms yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                currentHouseholdRooms.forEach { room ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${room.icon} ${room.name}", style = MaterialTheme.typography.bodyMedium)
                                        IconButton(onClick = {
                                            householdViewModel.removeRoom(selectedHouseholdId!!, room.id) {}
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove room", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            // Add room
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                roomIcons.forEach { icon ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (icon == selectedRoomIcon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable { selectedRoomIcon = icon }
                                            .padding(4.dp)
                                    ) { Text(icon, fontSize = 18.sp) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newRoomName,
                                    onValueChange = { newRoomName = it },
                                    label = { Text("Room name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newRoomName.isNotBlank()) {
                                            householdViewModel.addRoom(selectedHouseholdId!!, newRoomName.trim(), selectedRoomIcon) {
                                                newRoomName = ""
                                            }
                                        }
                                    },
                                    enabled = newRoomName.isNotBlank()
                                ) { Text("Add") }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Activity
                            Text("📝 Recent Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (activities.isEmpty()) Text("No recent activity yet.", style = MaterialTheme.typography.bodySmall)
                            else activities.take(5).forEach { act ->
                                val plantName = act["plantName"] as? String ?: "Unknown"
                                val displayName = act["username"] as? String ?: act["userId"] as? String ?: "Someone"
                                val ts = act["timestamp"] as? Long
                                Text("• $displayName watered $plantName${ts?.let { " (${formatDate(it)})" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showHouseholdInfoDialog = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomFilterRow(
    rooms: List<Room>,
    selectedRoomId: String?,
    onRoomSelected: (String?) -> Unit,
    onAddRoom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedRoomId == null,
            onClick = { onRoomSelected(null) },
            label = { Text("All") }
        )
        rooms.forEach { room ->
            FilterChip(
                selected = selectedRoomId == room.id,
                onClick = { onRoomSelected(if (selectedRoomId == room.id) null else room.id) },
                label = { Text("${room.icon} ${room.name}") }
            )
        }
        Surface(
            onClick = onAddRoom,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("Add Room", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PlantCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(Color.Gray.copy(alpha = alpha)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
                Box(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = alpha)))
                Box(modifier = Modifier.width(80.dp).height(20.dp).clip(RoundedCornerShape(50.dp)).background(Color.Gray.copy(alpha = alpha)))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun PlantSwipeItem(
    plant: Plant,
    mainViewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onSelect: (Plant) -> Unit
) {
    val dismissState = rememberDismissState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dismissState.currentValue) {
        val direction = dismissState.dismissDirection
        val fraction = dismissState.progress.fraction
        when (dismissState.currentValue) {
            DismissValue.DismissedToStart -> {
                if (direction == DismissDirection.EndToStart && fraction >= 0.5f) showDeleteConfirm = true
                dismissState.animateTo(DismissValue.Default, tween(200))
            }
            DismissValue.DismissedToEnd -> {
                if (direction == DismissDirection.StartToEnd && fraction >= 0.25f) {
                    val tooEarly = daysUntil(plant.nextWateringDate) > 1
                    if (tooEarly) {
                        scope.launch { snackbarHostState.showSnackbar("🚫 Too early to water ${plant.name}!") }
                    } else {
                        val success = mainViewModel.markPlantWatered(plant)
                        scope.launch { snackbarHostState.showSnackbar(if (success) "💧 ${plant.name} watered!" else "🚫 Too early to water ${plant.name}!") }
                    }
                }
                dismissState.animateTo(DismissValue.Default, tween(200))
            }
            else -> {}
        }
    }

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
        dismissThresholds = { dir -> FractionalThreshold(if (dir == DismissDirection.EndToStart) 0.5f else 0.25f) },
        background = {
            val direction = dismissState.dismissDirection
            val isTooEarly = daysUntil(plant.nextWateringDate) > 1
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (direction == DismissDirection.StartToEnd) {
                        if (isTooEarly) { Icon(Icons.Default.Close, "Too early", tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Too Early!", color = MaterialTheme.colorScheme.error) }
                        else { Icon(Icons.Default.Done, "Watered", tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Watered", color = MaterialTheme.colorScheme.primary) }
                    } else if (direction == DismissDirection.EndToStart) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        dismissContent = { PlantCard(plant = plant, onClick = { onSelect(plant) }) }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete ${plant.name}?") },
            confirmButton = { TextButton(onClick = { mainViewModel.deletePlant(plant); showDeleteConfirm = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PlantCard(plant: Plant, onClick: () -> Unit) {
    val days = daysUntil(plant.nextWateringDate)
    val (statusColor, statusBg, statusLabel) = when {
        days == 0L -> Triple(WaterNowRed, WaterNowRedContainer, "Water today")
        days <= 2L -> Triple(WaterSoonAmber, WaterSoonAmberContainer, "In $days day${if (days == 1L) "" else "s"}")
        else -> Triple(WaterGoodGreen, WaterGoodGreenContainer, "In $days days")
    }
    val streak = calculateStreak(plant.wateringHistory, plant.wateringDays)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) {
                PlantImage(plant = plant, modifier = Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(plant.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!plant.commonName.isNullOrBlank()) {
                    Text(plant.commonName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = statusBg) {
                        Text("💧 $statusLabel", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                    if (streak >= 2) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("🔥 $streak", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlantDetailDialog(
    plant: Plant,
    onDismiss: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
    householdViewModel: HouseholdViewModel = viewModel()
) {
    var editMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(plant.name) }
    var wateringDays by remember { mutableStateOf(plant.wateringDays.toString()) }
    var editRoomId by remember { mutableStateOf(plant.roomId) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showAIDialog by remember { mutableStateOf(false) }
    var showDiagnosisDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streak = calculateStreak(plant.wateringHistory, plant.wateringDays)
    val rooms by mainViewModel.currentHouseholdRooms.collectAsState()
    val currentRoomName = rooms.find { it.id == plant.roomId }?.let { "${it.icon} ${it.name}" }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                val userId = mainViewModel.currentUser.value?.id ?: return@launch
                val bytes = withContext(Dispatchers.IO) {
                    compressImageToBytes(context, uri)
                }
                if (bytes != null) {
                    val ref = FirebaseStorage.getInstance().reference.child("plants/$userId/${System.currentTimeMillis()}.jpg")
                    val deferred = CompletableDeferred<String?>()
                    ref.putBytes(bytes).continueWithTask { task -> if (!task.isSuccessful) throw task.exception!!; ref.downloadUrl }
                        .addOnSuccessListener { deferred.complete(it.toString()) }
                        .addOnFailureListener { deferred.complete(null) }
                    val newUrl = deferred.await()
                    if (newUrl != null) mainViewModel.updatePlantImage(plant, newUrl) {}
                }
                isUploadingPhoto = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Camera permission denied.", Toast.LENGTH_SHORT).show()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraOutputUri != null) {
            isUploadingPhoto = true
            scope.launch {
                val userId = mainViewModel.currentUser.value?.id ?: return@launch
                val bytes = withContext(Dispatchers.IO) { compressImageToBytes(context, cameraOutputUri!!) }
                if (bytes != null) {
                    val ref = FirebaseStorage.getInstance().reference.child("plants/$userId/${System.currentTimeMillis()}.jpg")
                    val deferred = CompletableDeferred<String?>()
                    ref.putBytes(bytes).continueWithTask { task -> if (!task.isSuccessful) throw task.exception!!; ref.downloadUrl }
                        .addOnSuccessListener { deferred.complete(it.toString()) }
                        .addOnFailureListener { deferred.complete(null) }
                    val newUrl = deferred.await()
                    if (newUrl != null) mainViewModel.updatePlantImage(plant, newUrl) {}
                }
                isUploadingPhoto = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    PlantImage(plant = plant, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
                    if (editMode) {
                        if (isUploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SmallFloatingActionButton(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        val file = File.createTempFile("plant_", ".jpg", context.cacheDir)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        cameraOutputUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("📷", fontSize = 14.sp)
                                }
                                SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("🖼", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                if (editMode) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Plant Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = wateringDays, onValueChange = { wateringDays = it }, label = { Text("Water every X days") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (plant.householdId != null && rooms.isNotEmpty()) {
                        Text("Move to room:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val roomOptions: List<Pair<String?, String>> = listOf(null to "No Room") +
                            rooms.map { it.id to "${it.icon} ${it.name}" }
                        DropdownMenuBox(
                            options = roomOptions,
                            selected = editRoomId,
                            onSelected = { editRoomId = it }
                        )
                    }
                } else {
                    Text(plant.name, style = MaterialTheme.typography.headlineSmall)
                    plant.commonName?.let { Text("Common name: $it", style = MaterialTheme.typography.bodyMedium) }
                    currentRoomName?.let {
                        Text("Room: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Water every ${plant.wateringDays} day(s)", style = MaterialTheme.typography.bodyMedium)
                    Text("Next watering in ${daysUntil(plant.nextWateringDate)} days", style = MaterialTheme.typography.bodyMedium)
                    if (streak >= 2) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("🔥 $streak watering streak", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Added: ${formatDate(plant.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Watering history
                    if (plant.wateringHistory.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text("💧 Watering History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        plant.wateringHistory.sortedDescending().take(10).forEach { ts ->
                            Text("• ${formatDate(ts)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (!editMode) {
                    Button(
                        onClick = { showAIDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) { Text("🤖 Ask AI Assistant") }
                    Button(
                        onClick = { showDiagnosisDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) { Text("🔬 Diagnose Problem") }
                    Spacer(Modifier.height(4.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).padding(end = 8.dp)) { Text("Close") }
                    if (editMode) {
                        Button(
                            onClick = {
                                val days = wateringDays.toIntOrNull()
                                if (name.isNotBlank() && days != null && days > 0) {
                                    isSaving = true
                                    mainViewModel.updatePlant(plant, name, days, editRoomId) { success ->
                                        isSaving = false
                                        Toast.makeText(context, if (success) "Plant updated!" else "Failed to update plant", Toast.LENGTH_SHORT).show()
                                        if (success) onDismiss()
                                    }
                                } else {
                                    Toast.makeText(context, "Invalid input", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
                        }
                    } else {
                        Button(onClick = { editMode = true }, modifier = Modifier.weight(1f).padding(start = 8.dp)) { Text("Edit") }
                    }
                }
            }
        }
    }

    if (showAIDialog) {
        PlantAIAssistantDialog(plant = plant, onDismiss = { showAIDialog = false })
    }

    if (showDiagnosisDialog) {
        PlantDiagnosisDialog(plant = plant, onDismiss = { showDiagnosisDialog = false })
    }
}

private fun compressImageToBytes(context: android.content.Context, uri: Uri, maxSizeKb: Int = 400): ByteArray? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val maxDim = 1080
        val raw = maxOf(opts.outWidth, opts.outHeight)
        val sample = if (raw > maxDim) raw / maxDim else 1
        val bitmapOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bitmapOpts) } ?: return null
        var quality = 85
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
        while (stream.size() / 1024 > maxSizeKb && quality > 20) {
            stream.reset(); quality -= 10
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
        }
        stream.toByteArray()
    } catch (e: Exception) { null }
}

fun calculateStreak(history: List<Long>, wateringDays: Int): Int {
    if (history.isEmpty()) return 0
    val sorted = history.sortedDescending()
    val maxGapMs = (wateringDays + 1) * 24L * 60 * 60 * 1000
    var streak = 1
    for (i in 0 until sorted.size - 1) {
        if (sorted[i] - sorted[i + 1] <= maxGapMs) streak++ else break
    }
    return streak
}

@Composable
fun PlantImage(
    plant: Plant,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    when {
        plant.imageUrl != null -> {
            AsyncImage(
                model = plant.imageUrl,
                contentDescription = plant.name,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
        plant.imageBase64 != null -> {
            val cachedImage = remember { mainViewModel.imageCache[plant.id] }
            val imageBitmap = remember(plant.imageBase64) {
                cachedImage ?: run {
                    val bitmap = try {
                        val bytes = Base64.decode(plant.imageBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) { null }
                    if (bitmap != null) mainViewModel.imageCache[plant.id] = bitmap
                    bitmap
                }
            }
            if (imageBitmap != null) {
                Image(bitmap = imageBitmap, contentDescription = plant.name, modifier = modifier, contentScale = ContentScale.Crop)
            } else {
                PlantImageFallback(modifier)
            }
        }
        else -> PlantImageFallback(modifier)
    }
}

@Composable
private fun PlantImageFallback(modifier: Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("🌱", fontSize = 28.sp)
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
