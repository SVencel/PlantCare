package com.family.plantcare.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HouseholdViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success

    private fun generateJoinCode(): String {
        return (100000..999999).random().toString()
    }

    private fun ensureUniqueJoinCode(onReady: (String) -> Unit) {
        val code = generateJoinCode()
        db.collection("households").whereEqualTo("joinCode", code).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onReady(code)
                } else {
                    ensureUniqueJoinCode(onReady) // retry
                }
            }
    }

    fun createHousehold(name: String, onSuccess: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (name.isBlank()) {
            _error.value = "Household name cannot be empty"
            return
        }

        ensureUniqueJoinCode { joinCode ->
            val newDoc = db.collection("households").document()
            val householdId = newDoc.id
            val household = mapOf(
                "id" to householdId,
                "name" to name,
                "joinCode" to joinCode,
                "members" to listOf(uid)
            )

            newDoc.set(household).addOnSuccessListener {
                db.collection("users").document(uid)
                    .update("households", FieldValue.arrayUnion(householdId))
                    .addOnSuccessListener {
                        _success.value = true
                        onSuccess(householdId) // ✅ pass ID
                    }
            }.addOnFailureListener { e ->
                _error.value = "Failed: ${e.message}"
            }
        }
    }

    fun joinHouseholdByCode(code: String, onSuccess: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("households").whereEqualTo("joinCode", code).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val householdId = doc.id
                    doc.reference.update("members", FieldValue.arrayUnion(uid))
                    db.collection("users").document(uid)
                        .update("households", FieldValue.arrayUnion(householdId))
                        .addOnSuccessListener {
                            _success.value = true
                            onSuccess(householdId)
                        }
                } else {
                    _error.value = "Invalid join code"
                }
            }.addOnFailureListener { e ->
                _error.value = "Failed to join: ${e.message}"
            }
    }

    fun leaveHousehold(householdId: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        val householdRef = db.collection("households").document(householdId)
        householdRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                _error.value = "Household not found"
                onResult(false)
                return@addOnSuccessListener
            }

            val members = snapshot.get("members") as? List<String> ?: emptyList()

            // Remove current user
            householdRef.update("members", FieldValue.arrayRemove(uid))
            db.collection("users").document(uid)
                .update("households", FieldValue.arrayRemove(householdId))

            val updatedMembers = members.filterNot { it == uid }
            if (updatedMembers.isEmpty()) {
                // ✅ Last member → delete household
                // Delete its plants first
                db.collection("plants").whereEqualTo("householdId", householdId).get()
                    .addOnSuccessListener { plants ->
                        for (plantDoc in plants) {
                            db.collection("plants").document(plantDoc.id).delete()
                        }
                        householdRef.delete().addOnSuccessListener {
                            _success.value = true
                            onResult(true)
                        }
                    }
            } else {
                _success.value = true
                onResult(true)
            }
        }.addOnFailureListener { e ->
            _error.value = "Failed: ${e.message}"
            onResult(false)
        }
    }


    fun deleteHousehold(householdId: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return

        val householdRef = db.collection("households").document(householdId)
        householdRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                _error.value = "Household not found"
                onResult(false)
                return@addOnSuccessListener
            }

            val members = snapshot.get("members") as? List<String> ?: emptyList()

            // Remove household from each member's record
            members.forEach { memberId ->
                db.collection("users").document(memberId)
                    .update("households", FieldValue.arrayRemove(householdId))
            }

            // Optionally: delete plants belonging to this household
            db.collection("plants").whereEqualTo("householdId", householdId).get()
                .addOnSuccessListener { plants ->
                    for (plantDoc in plants) {
                        db.collection("plants").document(plantDoc.id).delete()
                    }
                }

            // Finally, delete household
            householdRef.delete()
                .addOnSuccessListener {
                    _success.value = true
                    onResult(true)
                }
                .addOnFailureListener { e ->
                    _error.value = "Failed to delete: ${e.message}"
                    onResult(false)
                }
        }.addOnFailureListener { e ->
            _error.value = "Failed to fetch household: ${e.message}"
            onResult(false)
        }
    }

}
