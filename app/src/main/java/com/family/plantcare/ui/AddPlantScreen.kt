package com.family.plantcare.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.family.plantcare.BuildConfig
import com.family.plantcare.model.Plant
import com.family.plantcare.model.Room
import com.family.plantcare.viewmodel.MainViewModel
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

private val httpClient = OkHttpClient()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlantScreen(
    viewModel: MainViewModel = viewModel(),
    onPlantAdded: () -> Unit,
    onCancel: () -> Unit
) {
    val user by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var scientificName by remember { mutableStateOf("") }
    var commonName by remember { mutableStateOf<String?>(null) }
    var confidencePercent by remember { mutableStateOf<Int?>(null) }
    val gbifUrl by remember { mutableStateOf<String?>(null) }

    var nickname by remember { mutableStateOf("") }
    var selectedHousehold by remember { mutableStateOf<String?>(null) }
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var wateringDays by remember { mutableStateOf("99") }
    var localError by remember { mutableStateOf<String?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var sunlightAdvice by remember { mutableStateOf<String?>(null) }

    var isIdentifying by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var remainingScans by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(true) {
        viewModel.loadPlantCareInfo(context)
        viewModel.getRemainingScans { remainingScans = it }
    }

    LaunchedEffect(selectedHousehold) {
        viewModel.loadHouseholdRooms(selectedHousehold)
        selectedRoomId = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) imageUri = cameraOutputUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File.createTempFile("plant_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            localError = "Camera permission denied. Use Gallery instead."
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File.createTempFile("plant_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun compressToBytes(uri: Uri, maxSizeKb: Int = 400): ByteArray? {
        return try {
            // Sample size to avoid OOM on large camera photos
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxDim = 1080
            val raw = maxOf(opts.outWidth, opts.outHeight)
            val sample = if (raw > maxDim) raw / maxDim else 1
            val bitmapOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bitmapOpts)
            } ?: return null
            var quality = 85
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            while (stream.size() / 1024 > maxSizeKb && quality > 20) {
                stream.reset(); quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            stream.toByteArray()
        } catch (e: Exception) { null }
    }

    suspend fun uploadToStorage(userId: String, uri: Uri): String? {
        val bytes = withContext(Dispatchers.IO) { compressToBytes(uri) } ?: return null
        val ref = FirebaseStorage.getInstance().reference
            .child("plants/$userId/${System.currentTimeMillis()}.jpg")
        val deferred = CompletableDeferred<String?>()
        ref.putBytes(bytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                ref.downloadUrl
            }
            .addOnSuccessListener { deferred.complete(it.toString()) }
            .addOnFailureListener { deferred.complete(null) }
        return deferred.await()
    }

    fun savePlant() {
        localError = null
        if (scientificName.trim().isEmpty() && nickname.trim().isEmpty()) {
            localError = "Please name your plant or identify it first."
            return
        }
        val days = wateringDays.toIntOrNull()
        if (days == null || days <= 0) { localError = "Enter a valid number of days."; return }
        val userId = user?.id ?: run { localError = "Session error — please log out and log in again."; return }

        isSaving = true
        scope.launch {
            val capturedUri = imageUri
            val imageUrl = if (capturedUri != null) {
                val url = uploadToStorage(userId, capturedUri)
                if (url == null) { localError = "Failed to upload image. Check your connection."; isSaving = false; return@launch }
                url
            } else null

            val careInfo = viewModel.careInfoList.firstOrNull {
                it.name.equals(scientificName.trim(), ignoreCase = true) ||
                        it.commonName.equals(commonName?.trim(), ignoreCase = true)
            }

            val plant = Plant(
                name = nickname.ifBlank { scientificName.trim() },
                ownerId = if (selectedHousehold == null) userId else null,
                householdId = selectedHousehold,
                roomId = selectedRoomId,
                wateringDays = days,
                nextWateringDate = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000,
                imageUrl = imageUrl,
                commonName = commonName,
                confidence = confidencePercent?.toDouble(),
                gbifUrl = gbifUrl,
                oxygenOutput = careInfo?.oxygenOutput ?: 0.1
            )

            viewModel.addPlant(plant)
            Toast.makeText(context, "Plant added!", Toast.LENGTH_SHORT).show()
            isSaving = false
            onPlantAdded()
        }
    }

    suspend fun identifyPlant(uri: Uri) {
        localError = null
        try {
            val canScan = viewModel.checkAndDeductScan()
            if (!canScan) {
                showPaywall = true
                return
            }

            val imageBytes = withContext(Dispatchers.IO) { compressToBytes(uri) }
                ?: throw Exception("Could not open image")

            val base64 = withContext(Dispatchers.IO) {
                "data:image/jpeg;base64," +
                    android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            }

            val body = JSONObject().apply {
                put("images", JSONArray().put(base64))
                put("similar_images", false)
            }

            val request = Request.Builder()
                .url("https://plant.id/api/v3/identification?details=common_names")
                .addHeader("Api-Key", BuildConfig.PLANT_ID_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val (isSuccessful, statusCode, responseBody) = withContext(Dispatchers.IO) {
                val response = httpClient.newCall(request).execute()
                Triple(response.isSuccessful, response.code, response.body?.string())
            }

            if (isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val suggestions = json.getJSONObject("result")
                    .getJSONObject("classification")
                    .getJSONArray("suggestions")

                if (suggestions.length() > 0) {
                    val first = suggestions.getJSONObject(0)
                    scientificName = first.getString("name")
                    val details = first.optJSONObject("details")
                    val commonNames = details?.optJSONArray("common_names")
                    commonName = if (commonNames != null && commonNames.length() > 0)
                        commonNames.getString(0) else null
                    confidencePercent = (first.getDouble("probability") * 100).toInt()

                    viewModel.getRemainingScans { remainingScans = it }

                    val careInfo = viewModel.careInfoList.firstOrNull {
                        it.name.trim().lowercase() == scientificName.trim().lowercase() ||
                            it.commonName.trim().lowercase() == (commonName ?: "").trim().lowercase()
                    }
                    careInfo?.let { wateringDays = it.wateringDays.toString(); sunlightAdvice = it.sunlight }
                } else {
                    localError = "Plant not recognized. Try another image."
                }
            } else {
                localError = "Identification failed ($statusCode). Try again."
            }
        } catch (e: Exception) {
            localError = "Failed to identify plant: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a Plant") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 100.dp)
            ) {
                // Camera / Gallery row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                        Text("📷 Camera")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text("🖼 Gallery")
                    }
                }

                remainingScans?.let { remaining ->
                    if (remaining >= 0) {
                        Text(
                            if (remaining == 0) "No scans left this month — upgrade to Pro"
                            else "$remaining scan${if (remaining == 1) "" else "s"} remaining this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remaining == 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showPaywall) {
                    AlertDialog(
                        onDismissRequest = { showPaywall = false },
                        title = { Text("Monthly limit reached") },
                        text = { Text("You've used all 5 free plant scans this month. Upgrade to Pro for unlimited high-accuracy scans.") },
                        confirmButton = {
                            Button(onClick = { showPaywall = false }) { Text("Upgrade to Pro") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPaywall = false }) { Text("Maybe later") }
                        }
                    )
                }

                imageUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )

                    LaunchedEffect(uri) {
                        isIdentifying = true
                        identifyPlant(uri)
                        isIdentifying = false
                    }

                    if (isIdentifying) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    confidencePercent?.let { Text("Confidence: $it%", style = MaterialTheme.typography.bodySmall) }
                    commonName?.let { Text("Common name: $it", style = MaterialTheme.typography.bodySmall) }

                    if (!isIdentifying && (sunlightAdvice != null || wateringDays != "99")) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                sunlightAdvice?.let {
                                    Text("☀️ Suggested Sunlight", style = MaterialTheme.typography.labelMedium)
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (wateringDays != "99") {
                                    Spacer(Modifier.height(8.dp))
                                    Text("💧 Suggested Watering", style = MaterialTheme.typography.labelMedium)
                                    Text("Every $wateringDays day(s)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "✅ These values were auto-filled based on plant data.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    label = { Text("Your name for the plant (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = wateringDays, onValueChange = { wateringDays = it },
                    label = { Text("Water every X days") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Text("Assign to:")

                val householdMap by viewModel.households.collectAsState()
                val householdOptions = listOf(null) + (user?.households ?: emptyList())

                DropdownMenuBox(
                    options = householdOptions.map { id ->
                        id to (if (id == null) "Private" else (householdMap[id]?.first ?: "Household"))
                    },
                    selected = selectedHousehold,
                    onSelected = { selectedHousehold = it }
                )

                val rooms by viewModel.currentHouseholdRooms.collectAsState()
                if (selectedHousehold != null && rooms.isNotEmpty()) {
                    Text("Room:")
                    val roomOptions: List<Pair<String?, String>> = listOf(null to "No Room") +
                        rooms.map { it.id to "${it.icon} ${it.name}" }
                    DropdownMenuBox(
                        options = roomOptions,
                        selected = selectedRoomId,
                        onSelected = { selectedRoomId = it }
                    )
                }

                if (localError != null) {
                    Text(localError!!, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { savePlant() },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text("Save Plant")
                }
            }
        }
    }
}

@Composable
fun DropdownMenuBox(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.find { it.first == selected }?.second ?: "Private")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); expanded = false })
            }
        }
    }
}
