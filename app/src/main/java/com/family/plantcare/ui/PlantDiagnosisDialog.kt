package com.family.plantcare.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.family.plantcare.BuildConfig
import com.family.plantcare.model.Plant
import com.family.plantcare.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private data class DiagnosisResult(
    val isHealthy: Boolean,
    val healthProbability: Int,
    val diseases: List<DiseaseEntry>
)

private data class DiseaseEntry(
    val name: String,
    val probability: Int,
    val description: String,
    val treatments: List<String>
)

@Composable
fun PlantDiagnosisDialog(
    plant: Plant,
    viewModel: MainViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var diagnosisResult by remember { mutableStateOf<DiagnosisResult?>(null) }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) imageUri = cameraOutputUri
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("diag_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File.createTempFile("diag_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        isAnalyzing = true
        errorText = null
        diagnosisResult = null

        val canScan = viewModel.checkAndDeductScan()
        if (!canScan) {
            errorText = "No scans remaining this month. Upgrade to Pro for unlimited scans."
            isAnalyzing = false
            return@LaunchedEffect
        }

        try {
            val imageBytes = withContext(Dispatchers.IO) {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                val raw = maxOf(opts.outWidth, opts.outHeight)
                val sample = if (raw > 1080) raw / 1080 else 1
                val bitmapOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bitmapOpts)
                } ?: return@withContext null
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            } ?: throw Exception("Could not process image")

            val base64 = withContext(Dispatchers.IO) {
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val body = JSONObject().apply {
                put("images", JSONArray().put(base64))
                put("similar_images", false)
            }

            val request = Request.Builder()
                .url("https://plant.id/api/v3/health_assessment?details=description,treatment")
                .addHeader("Api-Key", BuildConfig.PLANT_ID_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val (isSuccessful, statusCode, responseBody) = withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                Triple(response.isSuccessful, response.code, response.body?.string())
            }

            if (isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val result = json.getJSONObject("result")
                val isHealthy = result.getJSONObject("is_healthy").getBoolean("binary")
                val healthyProb = result.getJSONObject("is_healthy").getDouble("probability")

                val diseases = mutableListOf<DiseaseEntry>()
                if (!isHealthy) {
                    val suggestions = result.optJSONObject("disease")?.optJSONArray("suggestions")
                    suggestions?.let { arr ->
                        for (i in 0 until minOf(arr.length(), 3)) {
                            val d = arr.getJSONObject(i)
                            val details = d.optJSONObject("details")
                            val treatments = mutableListOf<String>()
                            details?.optJSONObject("treatment")?.let { t ->
                                t.optJSONArray("biological")?.let { bio ->
                                    for (j in 0 until minOf(bio.length(), 2)) treatments.add(bio.getString(j))
                                }
                                t.optJSONArray("prevention")?.let { prev ->
                                    for (j in 0 until minOf(prev.length(), 2)) treatments.add(prev.getString(j))
                                }
                                t.optJSONArray("chemical")?.let { chem ->
                                    for (j in 0 until minOf(chem.length(), 1)) treatments.add(chem.getString(j))
                                }
                            }
                            diseases.add(
                                DiseaseEntry(
                                    name = d.getString("name"),
                                    probability = (d.getDouble("probability") * 100).toInt(),
                                    description = details?.optString("description") ?: "",
                                    treatments = treatments
                                )
                            )
                        }
                    }
                }

                diagnosisResult = DiagnosisResult(
                    isHealthy = isHealthy,
                    healthProbability = (healthyProb * 100).toInt(),
                    diseases = diseases
                )
            } else {
                errorText = "Analysis failed ($statusCode). Try again."
            }
        } catch (e: Exception) {
            errorText = "Error: ${e.message}"
        } finally {
            isAnalyzing = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🔬 Diagnose Plant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(plant.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (imageUri == null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🍂", fontSize = 48.sp)
                                Text(
                                    "Take a photo of the affected leaves or area",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                                Text("📷 Camera")
                            }
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Text("🖼 Gallery")
                            }
                        }
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        )

                        if (isAnalyzing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Analyzing plant health…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        errorText?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        diagnosisResult?.let { result ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (result.isHealthy) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (result.isHealthy) "✅" else "⚠️", fontSize = 28.sp)
                                    Column {
                                        Text(
                                            if (result.isHealthy) "Plant looks healthy!" else "Issues detected",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (result.isHealthy) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            "${result.healthProbability}% health score",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = (if (result.isHealthy) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            result.diseases.forEach { disease ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                disease.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    "${disease.probability}%",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                        if (disease.description.isNotBlank()) {
                                            Text(
                                                disease.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (disease.treatments.isNotEmpty()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text("💊 Treatment:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            disease.treatments.forEach { t ->
                                                Text("• $t", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!isAnalyzing) {
                            OutlinedButton(
                                onClick = { imageUri = null; diagnosisResult = null; errorText = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Try another photo") }
                        }
                    }
                }
            }
        }
    }
}
