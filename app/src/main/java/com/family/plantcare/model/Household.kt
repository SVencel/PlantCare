package com.family.plantcare.model

data class Household(
    val id: String = "",
    val name: String = "",
    val joinCode: String = "",
    val members: List<String> = emptyList()
)
