package com.family.plantcare.viewmodel

import androidx.lifecycle.ViewModel
import com.family.plantcare.model.Plant
import com.family.plantcare.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    fun loadPlants(householdId: String?) {
        _selectedHouseholdId.value = householdId
        val userId = auth.currentUser?.uid ?: return
        val query = db.collection("plants").whereEqualTo(
            if (householdId == null) "ownerId" else "householdId",
            if (householdId == null) userId else householdId
        )

        query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.toObjects(Plant::class.java)
                _plants.value = list
            }
        }
    }
}
