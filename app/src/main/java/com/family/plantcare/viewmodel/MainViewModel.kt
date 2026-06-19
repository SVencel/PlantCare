package com.family.plantcare.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.plantcare.model.Plant
import com.family.plantcare.model.PlantCareInfo
import com.family.plantcare.model.Room
import com.family.plantcare.model.User
import java.util.Calendar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CompletableDeferred
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

    private val _plantsLoading = MutableStateFlow(true)
    val plantsLoading: StateFlow<Boolean> = _plantsLoading

    private val _lastSeenActivity = MutableStateFlow<Long?>(null)
    val lastSeenActivity: StateFlow<Long?> = _lastSeenActivity

    private val _currentHouseholdRooms = MutableStateFlow<List<Room>>(emptyList())
    val currentHouseholdRooms: StateFlow<List<Room>> = _currentHouseholdRooms

    private val _hemisphere = MutableStateFlow("north")
    val hemisphere: StateFlow<String> = _hemisphere

    val imageCache = mutableMapOf<String, ImageBitmap>()

    private var plantsListener: ListenerRegistration? = null
    private var activitiesListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null

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

    fun loadUserData() {
        val firebaseUser = auth.currentUser ?: return
        val userId = firebaseUser.uid
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val user = if (doc.exists()) {
                    doc.toObject(User::class.java)
                } else {
                    // Document missing (registration failed mid-way) — recreate from Auth data
                    val recovered = User(
                        id = userId,
                        email = firebaseUser.email ?: "",
                        username = firebaseUser.email?.substringBefore("@") ?: "User"
                    )
                    db.collection("users").document(userId).set(recovered)
                    recovered
                }
                _currentUser.value = user
                loadHemisphere()
                loadPlants(null)
                // Load household names
                _households.value = emptyMap()
                user?.households?.take(6)?.forEach { hid ->
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
                Log.e("MainViewModel", "Failed to load user data: ${e.message}")
            }
    }

    private val _careInfoList = mutableStateListOf<PlantCareInfo>()
    val careInfoList: List<PlantCareInfo> get() = _careInfoList

    fun loadPlantCareInfo(context: Context) {
        if (_careInfoList.isNotEmpty()) return
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
        activitiesListener?.remove()
        activitiesListener = db.collection("households").document(householdId)
            .collection("activities")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _activities.value = snapshot.documents.map { it.data ?: emptyMap() }
                }
            }
    }

    private fun loadHouseholdMembers(householdId: String) {
        membersListener?.remove()
        membersListener = db.collection("households").document(householdId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val members = snapshot.get("members") as? List<String> ?: emptyList()
                    if (members.isNotEmpty()) {
                        db.collection("users")
                            .whereIn("id", members)
                            .get()
                            .addOnSuccessListener { userDocs ->
                                _householdMembers.value = userDocs.mapNotNull { it.getString("username") }
                            }
                    } else {
                        _householdMembers.value = emptyList()
                    }
                    @Suppress("UNCHECKED_CAST")
                    val roomsData = snapshot.get("rooms") as? List<Map<String, Any>> ?: emptyList()
                    _currentHouseholdRooms.value = roomsData.mapNotNull { r ->
                        val id = r["id"] as? String ?: return@mapNotNull null
                        val name = r["name"] as? String ?: return@mapNotNull null
                        val icon = r["icon"] as? String ?: "🌿"
                        Room(id = id, name = name, icon = icon)
                    }
                }
            }
    }

    fun loadHouseholdRooms(householdId: String?) {
        if (householdId == null) { _currentHouseholdRooms.value = emptyList(); return }
        db.collection("households").document(householdId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val roomsData = doc.get("rooms") as? List<Map<String, Any>> ?: emptyList()
                    _currentHouseholdRooms.value = roomsData.mapNotNull { r ->
                        val id = r["id"] as? String ?: return@mapNotNull null
                        val name = r["name"] as? String ?: return@mapNotNull null
                        val icon = r["icon"] as? String ?: "🌿"
                        Room(id = id, name = name, icon = icon)
                    }
                } else {
                    _currentHouseholdRooms.value = emptyList()
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
            loadActivities(householdId)
        } else {
            membersListener?.remove()
            membersListener = null
            activitiesListener?.remove()
            activitiesListener = null
            _householdMembers.value = emptyList()
            _activities.value = emptyList()
            _currentHouseholdRooms.value = emptyList()
        }

        _plantsLoading.value = true
        plantsListener?.remove()
        plantsListener = query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _plants.value = snapshot.toObjects(Plant::class.java)
                _plantsLoading.value = false
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

    fun loadHemisphere() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            _hemisphere.value = doc.getString("hemisphere") ?: "north"
        }
    }

    fun setHemisphere(value: String) {
        _hemisphere.value = value
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).update("hemisphere", value)
    }

    private fun seasonalWateringFactor(): Double {
        val month = Calendar.getInstance().get(Calendar.MONTH)
        val isSouth = _hemisphere.value == "south"
        val summerMonths = if (isSouth)
            setOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY)
        else
            setOf(Calendar.JUNE, Calendar.JULY, Calendar.AUGUST)
        val winterMonths = if (isSouth)
            setOf(Calendar.JUNE, Calendar.JULY, Calendar.AUGUST)
        else
            setOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY)
        return when (month) {
            in summerMonths -> 0.85
            in winterMonths -> 1.20
            else -> 1.0
        }
    }

    fun markPlantWatered(plant: Plant): Boolean {
        val now = System.currentTimeMillis()
        val oneDayMs = 24L * 60 * 60 * 1000

        if (plant.lastWatered != null && now - plant.lastWatered < oneDayMs) {
            return false
        }

        val wateringIntervalDays = plant.wateringDays.takeIf { it > 0 } ?: 7
        val factor = seasonalWateringFactor()
        val newDate = now + (wateringIntervalDays * factor * 24L * 60 * 60 * 1000).toLong()
        val updatedHistory = (plant.wateringHistory + now).takeLast(30)
        val updatedPlant = plant.copy(
            nextWateringDate = newDate,
            lastWatered = now,
            timesWatered = plant.timesWatered + 1,
            wateringHistory = updatedHistory
        )

        // Optimistic local update prevents rapid re-watering before Firestore responds
        _plants.value = _plants.value.map { if (it.id == plant.id) updatedPlant else it }

        db.collection("plants").document(plant.id).set(updatedPlant)

        if (plant.householdId != null) {
            val userId = auth.currentUser?.uid
            val username = currentUser.value?.username ?: "Someone"
            val activity = mapOf(
                "plantName" to plant.name,
                "userId" to userId,
                "username" to username,
                "timestamp" to now
            )
            val activityRef = db.collection("households").document(plant.householdId)
                .collection("activities")

            activityRef.add(activity).addOnSuccessListener {
                activityRef.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val extra = snapshot.documents.drop(10)
                        for (doc in extra) {
                            activityRef.document(doc.id).delete()
                        }
                    }
            }
        }

        return true
    }

    fun updatePlant(plant: Plant, newName: String, newWateringDays: Int, newRoomId: String?, onResult: (Boolean) -> Unit) {
        val currentRemaining = ((plant.nextWateringDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
            .coerceAtLeast(0)
        val chosenDays = minOf(newWateringDays, currentRemaining.takeIf { it > 0 } ?: newWateringDays)
        val adjustedNextDate = System.currentTimeMillis() + chosenDays * 24L * 60 * 60 * 1000

        val updatedPlant = plant.copy(
            name = newName,
            wateringDays = newWateringDays,
            nextWateringDate = adjustedNextDate,
            roomId = newRoomId
        )

        db.collection("plants").document(plant.id)
            .set(updatedPlant)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    suspend fun checkAndDeductScan(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val userRef = db.collection("users").document(userId)
        val deferred = CompletableDeferred<Boolean>()

        userRef.get().addOnSuccessListener { doc ->
            val isPro = doc.getBoolean("isPro") ?: false
            if (isPro) { deferred.complete(true); return@addOnSuccessListener }

            val now = System.currentTimeMillis()
            val resetDate = doc.getLong("scanMonthResetDate") ?: 0L
            val scansUsed = if (now > resetDate) 0 else (doc.getLong("plantIdScansUsed")?.toInt() ?: 0)

            if (scansUsed >= 5) {
                deferred.complete(false)
            } else {
                val newResetDate = if (now > resetDate) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.timeInMillis
                } else resetDate

                userRef.update(mapOf(
                    "plantIdScansUsed" to (scansUsed + 1).toLong(),
                    "scanMonthResetDate" to newResetDate
                )).addOnCompleteListener { deferred.complete(true) }
            }
        }.addOnFailureListener { deferred.complete(false) }

        return deferred.await()
    }

    fun getRemainingScans(onResult: (Int) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(0)
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val isPro = doc.getBoolean("isPro") ?: false
            if (isPro) { onResult(-1); return@addOnSuccessListener }
            val now = System.currentTimeMillis()
            val resetDate = doc.getLong("scanMonthResetDate") ?: 0L
            val scansUsed = if (now > resetDate) 0 else (doc.getLong("plantIdScansUsed")?.toInt() ?: 0)
            onResult(5 - scansUsed)
        }.addOnFailureListener { onResult(5) }
    }

    fun updatePlantImage(plant: Plant, newImageUrl: String, onResult: (Boolean) -> Unit) {
        val updated = plant.copy(imageUrl = newImageUrl, imageBase64 = null)
        db.collection("plants").document(plant.id).set(updated)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onError("Not logged in")
        db.collection("plants").whereEqualTo("ownerId", userId).get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener {
                    db.collection("users").document(userId).delete()
                        .addOnSuccessListener {
                            auth.currentUser?.delete()
                                ?.addOnSuccessListener { onSuccess() }
                                ?.addOnFailureListener { e ->
                                    if (e is FirebaseAuthRecentLoginRequiredException) {
                                        onError("Please log out and log back in, then try again.")
                                    } else {
                                        onError("Failed to delete account: ${e.message}")
                                    }
                                }
                        }
                        .addOnFailureListener { onError("Failed to delete account.") }
                }.addOnFailureListener { onError("Failed to delete account.") }
            }
            .addOnFailureListener { onError("Failed to delete account.") }
    }

    override fun onCleared() {
        super.onCleared()
        plantsListener?.remove()
        activitiesListener?.remove()
        membersListener?.remove()
    }
}
