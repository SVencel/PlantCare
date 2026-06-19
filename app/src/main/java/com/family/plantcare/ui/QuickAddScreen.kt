package com.family.plantcare.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.plantcare.model.Plant
import com.family.plantcare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    viewModel: MainViewModel = viewModel(),
    onPlantAdded: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsState()
    val careInfoList = viewModel.careInfoList
    val householdMap by viewModel.households.collectAsState()
    val rooms by viewModel.currentHouseholdRooms.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedHouseholdId by remember { mutableStateOf<String?>(null) }
    var selectedRoomId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(true) { viewModel.loadPlantCareInfo(context) }

    LaunchedEffect(selectedHouseholdId) {
        viewModel.loadHouseholdRooms(selectedHouseholdId)
        selectedRoomId = null
    }

    val filteredList = remember(searchQuery, careInfoList.size) {
        if (searchQuery.isBlank()) careInfoList.toList()
        else careInfoList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.commonName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Add") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Assign to:", style = MaterialTheme.typography.labelMedium)
                val householdOptions = listOf(null) + (user?.households ?: emptyList())
                DropdownMenuBox(
                    options = householdOptions.map { id ->
                        id to (if (id == null) "Private" else (householdMap[id]?.first ?: "Household"))
                    },
                    selected = selectedHouseholdId,
                    onSelected = { selectedHouseholdId = it }
                )

                if (selectedHouseholdId != null && rooms.isNotEmpty()) {
                    val roomOptions: List<Pair<String?, String>> = listOf(null to "No Room") +
                        rooms.map { it.id to "${it.icon} ${it.name}" }
                    DropdownMenuBox(
                        options = roomOptions,
                        selected = selectedRoomId,
                        onSelected = { selectedRoomId = it }
                    )
                }
            }

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

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No plants found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)) {
                    items(filteredList) { info ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    val userId = user?.id ?: return@clickable
                                    val plant = Plant(
                                        name = info.commonName.ifBlank { info.name },
                                        commonName = if (info.commonName.isNotBlank()) info.name else null,
                                        ownerId = if (selectedHouseholdId == null) userId else null,
                                        householdId = selectedHouseholdId,
                                        roomId = selectedRoomId,
                                        wateringDays = info.wateringDays,
                                        nextWateringDate = System.currentTimeMillis() + info.wateringDays * 24L * 60 * 60 * 1000,
                                        oxygenOutput = info.oxygenOutput
                                    )
                                    viewModel.addPlant(plant)
                                    Toast.makeText(context, "${plant.name} added!", Toast.LENGTH_SHORT).show()
                                    onPlantAdded()
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    info.commonName.ifBlank { info.name },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (info.commonName.isNotBlank()) {
                                    Text(
                                        info.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "💧 Every ${info.wateringDays} day(s)  ·  ☀️ ${info.sunlight}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
