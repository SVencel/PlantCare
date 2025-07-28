package com.family.plantcare.model

data class Plant(
    val id: String = "",
    val name: String = "",
    val commonName: String? = null,
    val confidence: Double? = null,
    val gbifUrl: String? = null,
    val imageUrl: String? = null,
    val ownerId: String? = null,
    val householdId: String? = null,
    val wateringDays: Int = 7,
    val nextWateringDate: Long = 0L,
    val lastWatered: Long? = null,
    val timesWatered: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
