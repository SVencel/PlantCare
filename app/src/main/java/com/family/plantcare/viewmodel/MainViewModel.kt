package com.family.plantcare.viewmodel

import android.content.Context
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _plants = MutableStateFlow<List<Plant>>(emptyList())
    val plants: StateFlow<List<Plant>> = _plants

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _selectedHouseholdId = MutableStateFlow<String?>(null)
    val selectedHouseholdId: StateFlow<String?> = _selectedHouseholdId

    private val _households = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    // householdId -> (name, joinCode)
    val households: StateFlow<Map<String, Pair<String, String>>> = _households

    private val _householdMembers = MutableStateFlow<List<String>>(emptyList())
    val householdMembers: StateFlow<List<String>> = _householdMembers

    private val _activities = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val activities: StateFlow<List<Map<String, Any>>> = _activities

    private val _lastSeenActivity = MutableStateFlow<Long?>(null)
    val lastSeenActivity: StateFlow<Long?> = _lastSeenActivity

    // ✅ Tells if there’s new unseen activity
    val hasNewActivity: StateFlow<Boolean> = combine(
        activities,
        lastSeenActivity,
        selectedHouseholdId
    ) { activityList, seenTime, householdId ->
        if (householdId == null || activityList.isEmpty()) {
            // 🔹 No household or no activities → no badge
            false
        } else {
            val newest = activityList.maxOfOrNull { it["timestamp"] as? Long ?: 0L } ?: 0L
            seenTime == null || newest > seenTime
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)


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
                            sunlight = obj.getString("sunlight"),
                            oxygenOutput = obj.optDouble("oxygenOutput", 0.1)
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

    fun reloadUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val updatedUser = snapshot.toObject(User::class.java)
                _currentUser.value = updatedUser

                // ✅ Clear household map before repopulating
                _households.value = emptyMap()

                updatedUser?.households?.take(6)?.forEach { hid ->
                    db.collection("households").document(hid).get()
                        .addOnSuccessListener { hSnap ->
                            if (hSnap != null && hSnap.exists()) {
                                val name = hSnap.getString("name") ?: "Unknown"
                                val code = hSnap.getString("joinCode") ?: "------"
                                _households.value += (hid to (name to code))
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainViewModel", "Failed to reload user: ${e.message}")
            }
    }

    // ✅ Called when user opens the Household Info dialog
    fun markActivitiesSeen() {
        _lastSeenActivity.value = System.currentTimeMillis()
    }

    private fun loadActivities(householdId: String) {
        db.collection("households").document(householdId)
            .collection("activities")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _activities.value = snapshot.documents.map { it.data ?: emptyMap() }
                }
            }
    }

    private fun loadHouseholdMembers(householdId: String) {
        val householdRef = db.collection("households").document(householdId)
        householdRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val members = snapshot.get("members") as? List<String> ?: emptyList()

                if (members.isNotEmpty()) {
                    db.collection("users")
                        .whereIn("id", members)
                        .get()
                        .addOnSuccessListener { userDocs ->
                            val usernames = userDocs.mapNotNull { it.getString("username") }
                            _householdMembers.value = usernames
                        }
                } else {
                    _householdMembers.value = emptyList()
                }
            }
        }
    }

    fun loadPlants(householdId: String?) {
        _selectedHouseholdId.value = householdId
        val userId = auth.currentUser?.uid ?: return
        val query = db.collection("plants").whereEqualTo(
            if (householdId == null) "ownerId" else "householdId",
            householdId ?: userId
        )

        if (householdId != null) {
            loadHouseholdMembers(householdId)
            loadActivities(householdId) // ✅ start listening for activities
        } else {
            _householdMembers.value = emptyList()
            _activities.value = emptyList() // ✅ clear activities when private
        }

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

        // ✅ Log the activity in the household if applicable
        if (plant.householdId != null) {
            val userId = auth.currentUser?.uid
            val activity = mapOf(
                "plantName" to plant.name,
                "userId" to userId,
                "timestamp" to now
            )
            db.collection("households").document(plant.householdId)
                .collection("activities")
                .add(activity)
        }

        return true
    }

    fun updatePlant(plant: Plant, newName: String, newWateringDays: Int, onResult: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Calculate current remaining days
        val currentRemaining = ((plant.nextWateringDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
            .coerceAtLeast(0)

        // Choose the smaller of new vs remaining days
        val chosenDays = minOf(newWateringDays, currentRemaining.takeIf { it > 0 } ?: newWateringDays)

        val adjustedNextDate = System.currentTimeMillis() + chosenDays * 24L * 60 * 60 * 1000

        val updatedPlant = plant.copy(
            name = newName,
            wateringDays = newWateringDays,
            nextWateringDate = adjustedNextDate
        )

        db.collection("plants").document(plant.id)
            .set(updatedPlant)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
