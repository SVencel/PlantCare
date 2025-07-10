package com.family.plantcare.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.family.plantcare.model.User
import com.family.plantcare.model.Household
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LoginViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun registerUser(email: String, password: String, username: String, householdName: String?) {
        _isLoading.value = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: return@addOnSuccessListener
                if (householdName.isNullOrBlank()) {
                    saveUser(User(id = userId, email = email, username = username))
                } else {
                    createOrJoinHousehold(householdName, userId) { householdIds ->
                        saveUser(User(id = userId, email = email, username = username, households = householdIds))
                    }
                }
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    fun loginUser(email: String, password: String, onSuccess: () -> Unit) {
        _isLoading.value = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                _isLoading.value = false
                onSuccess()
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    private fun saveUser(user: User) {
        db.collection("users").document(user.id)
            .set(user)
            .addOnSuccessListener {
                _isLoading.value = false
                Log.d("LoginViewModel", "User saved successfully.")
            }
            .addOnFailureListener {
                _isLoading.value = false
                _error.value = it.message
            }
    }

    private fun createOrJoinHousehold(name: String, userId: String, onComplete: (List<String>) -> Unit) {
        db.collection("households")
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    // Household doesn't exist → create it
                    val doc = db.collection("households").document()
                    val newHousehold = Household(id = doc.id, name = name, members = listOf(userId))
                    doc.set(newHousehold).addOnSuccessListener {
                        onComplete(listOf(doc.id))
                    }
                } else {
                    // Household exists → join it
                    val doc = query.documents.first()
                    val householdId = doc.id
                    val household = doc.toObject(Household::class.java)
                    val updatedMembers = (household?.members ?: emptyList()).plus(userId).distinct()
                    db.collection("households").document(householdId)
                        .update("members", updatedMembers)
                        .addOnSuccessListener {
                            onComplete(listOf(householdId))
                        }
                }
            }
    }
}
