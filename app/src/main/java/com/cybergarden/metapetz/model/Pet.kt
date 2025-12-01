package com.cybergarden.metapetz.model

data class Pet(
    val name: String,
    val emoji: String,
    val description: String,
    val trait: String = "Playful"
)

data class PetStats(
    val hunger: Float = 1.0f,        // 0.0 to 1.0 (1.0 = full)
    val happiness: Float = 1.0f,     // 0.0 to 1.0 (1.0 = very happy)
    val health: Float = 1.0f,        // 0.0 to 1.0 (1.0 = healthy)
    val energy: Float = 1.0f,        // 0.0 to 1.0 (1.0 = energized)
    val level: Int = 1,              // Pet level
    val xp: Int = 0,                 // Experience points
    val xpToNextLevel: Int = 100     // XP needed for next level
)

// Predefined pets list
val DEFAULT_PETS = listOf(
    Pet("Cat", "\uD83D\uDC31", "A playful feline friend", "Curious"),
    Pet("Dog", "\uD83D\uDC36", "A loyal canine companion", "Loyal"),
    Pet("Bunny", "\uD83D\uDC30", "A cute hopping rabbit", "Gentle"),
    Pet("Bird", "\uD83D\uDC26", "A chirping feathered friend", "Energetic"),
    Pet("Fish", "\uD83D\uDC20", "A swimming aquatic pal", "Calm"),
    Pet("Hamster", "\uD83D\uDC39", "A tiny furry buddy", "Playful"),
)

val CUSTOM_PET = Pet("Custom", "\u2728", "Your custom AI pet", "Unique")
