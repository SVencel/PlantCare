package com.family.plantcare.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.family.plantcare.model.Plant
import com.family.plantcare.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.*

@Composable
fun AddPlantScreen(
    viewModel: MainViewModel = viewModel(),
    onPlantAdded: () -> Unit
) {
    val user by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var plantName by remember { mutableStateOf("") }
    var selectedHousehold by remember { mutableStateOf<String?>(null) }
    var wateringDays by remember { mutableStateOf("7") }
    var localError by remember { mutableStateOf<String?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val householdOptions = listOf(null) + (user?.households ?: emptyList())

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imageUri = it
        }
    }

    fun savePlant() {
        localError = null
        if (plantName.trim().isEmpty()) {
            localError = "Plant name is required."
            return
        }

        val days = wateringDays.toIntOrNull()
        if (days == null || days <= 0) {
            localError = "Enter a valid number of days."
            return
        }

        val userId = user?.id ?: return
        val plant = Plant(
            name = plantName.trim(),
            ownerId = if (selectedHousehold == null) userId else null,
            householdId = selectedHousehold,
            nextWateringDate = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000,
            imageUrl = imageUri.toString()
        )

        viewModel.addPlant(plant)
        Toast.makeText(context, "Plant added!", Toast.LENGTH_SHORT).show()
        onPlantAdded()
    }

    suspend fun identifyPlant(uri: Uri) {
        localError = null
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open image input stream")

            val imageBytes = inputStream.readBytes()
            val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("organs", "leaf")
                .addFormDataPart("images", "plant.jpg", requestBody)
                .build()

            val request = Request.Builder()
                .url("https://my-api.plantnet.org/v2/identify/all?api-key=2b10Z5n4IfDbDSuExx51diyu")
                .post(multipartBody)
                .build()

            val client = OkHttpClient()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

            response.body?.let { body ->
                val json = JSONObject(body.string())
                val suggestions = json.getJSONArray("results")
                if (suggestions.length() > 0) {
                    val first = suggestions.getJSONObject(0)
                    val species = first.getJSONObject("species")
                    val scientificName = species.getString("scientificNameWithoutAuthor")
                    plantName = scientificName
                } else {
                    localError = "No plant identified."
                }
            } ?: run {
                localError = "Failed to get response from PlantNet."
            }

        } catch (e: Exception) {
            localError = "Failed to identify plant: ${e.message}"
        }
    }


    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add a Plant", style = MaterialTheme.typography.headlineSmall)

                Button(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("Pick Image")
                }

                imageUri?.let {
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    LaunchedEffect(it) {
                        identifyPlant(it)
                    }
                }

                OutlinedTextField(
                    value = plantName,
                    onValueChange = { plantName = it },
                    label = { Text("Plant name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = wateringDays,
                    onValueChange = { wateringDays = it },
                    label = { Text("Water every X days") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Assign to:")

                DropdownMenuBox(
                    options = householdOptions,
                    selected = selectedHousehold,
                    onSelected = { selectedHousehold = it }
                )

                if (localError != null) {
                    Text(text = localError!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { savePlant() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Plant")
                }
            }

        }
    }
}


@Composable
fun DropdownMenuBox(
    options: List<String?>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = selected ?: "Private")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { householdId ->
            DropdownMenuItem(
                text = { Text(householdId ?: "Private") },
                onClick = {
                    onSelected(householdId)
                    expanded = false
                }
            )
        }
    }
}

