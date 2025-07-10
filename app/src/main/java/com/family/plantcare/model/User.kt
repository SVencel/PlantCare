package com.family.plantcare.model

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val joinDate: Long = System.currentTimeMillis(),
    val households: List<String> = emptyList()
)
