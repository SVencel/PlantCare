package com.family.plantcare.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.plantcare.model.Plant
import com.family.plantcare.model.PlantCareInfo
import com.family.plantcare.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import org.json.JSONArray
import java.io.File

class MainViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _plants = MutableStateFlow<List<Plant>>(emptyList())
    val plants: StateFlow<List<Plant>> = _plants

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _selectedHouseholdId = MutableStateFlow<String?>(null)
    val selectedHouseholdId: StateFlow<String?> = _selectedHouseholdId

    init {
        loadUserData()
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                _currentUser.value = user
                loadPlants(null) // Load private plants by default
            }
    }

    private val _careInfoList = mutableStateListOf<PlantCareInfo>()
    val careInfoList: List<PlantCareInfo> get() = _careInfoList

    fun loadPlantCareInfo(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = context.assets.open("plant_care_info.json").bufferedReader().use { it.readText() }
                val list = JSONArray(json)
                for (i in 0 until list.length()) {
                    val obj = list.getJSONObject(i)
                    _careInfoList.add(
                        PlantCareInfo(
                            name = obj.getString("name"),
                            commonName = obj.getString("commonName"),
                            wateringDays = obj.getInt("wateringDays"),
                            sunlight = obj.getString("sunlight")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PlantCare", "Failed to load fallback data", e)
            }
        }
    }


    fun addPlant(plant: Plant) {
        val doc = db.collection("plants").document()
        db.collection("plants").document(doc.id).set(plant.copy(id = doc.id))
    }

    fun uploadImageAndGetUrl(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference
        val fileName = "plants/${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child(fileName)

        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()

        if (bytes == null) {
            onResult(null)
            return
        }

        val uploadTask = imageRef.putBytes(bytes)
        uploadTask.addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                onResult(downloadUri.toString()) // ✅ Firebase URL
            }.addOnFailureListener { onResult(null) }
        }.addOnFailureListener { onResult(null) }
    }


    fun loadPlants(householdId: String?) {
        _selectedHouseholdId.value = householdId
        val userId = auth.currentUser?.uid ?: return
        val query = db.collection("plants").whereEqualTo(
            if (householdId == null) "ownerId" else "householdId",
            householdId ?: userId
        )

        query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.toObjects(Plant::class.java)
                _plants.value = list
            }
        }
    }

    fun deletePlant(plant: Plant) {
        val docRef = db.collection("plants").document(plant.id)

        docRef.delete()
            .addOnSuccessListener {
                _plants.value = _plants.value.filterNot { it.id == plant.id }
                Log.d("DeletePlant", "Successfully deleted plant: ${plant.name}")
            }
            .addOnFailureListener {
                Log.e("DeletePlant", "Failed to delete plant: ${it.message}")
            }
    }

    fun markPlantWatered(plant: Plant): Boolean {
        val now = System.currentTimeMillis()
        val wateringIntervalDays = plant.wateringDays.takeIf { it > 0 } ?: 7
        val threshold = plant.nextWateringDate - (wateringIntervalDays * 24 * 60 * 60 * 1000 / 3)

        // 🚫 block early watering
        if (plant.lastWatered != null && now < threshold) {
            return false
        }

        val newDate = now + wateringIntervalDays * 24 * 60 * 60 * 1000
        val updatedPlant = plant.copy(
            nextWateringDate = newDate,
            lastWatered = now,
            timesWatered = plant.timesWatered + 1
        )

        db.collection("plants").document(plant.id).set(updatedPlant)
        return true
    }



}
