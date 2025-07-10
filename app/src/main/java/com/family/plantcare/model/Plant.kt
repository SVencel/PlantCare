package com.family.plantcare.model

data class Plant(
    val id: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val ownerId: String? = null,
    val householdId: String? = null,
    val nextWateringDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
