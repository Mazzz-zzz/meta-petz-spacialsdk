package com.cybergarden.metapetz.model

data class Pet(
    val name: String,
    val emoji: String,
    val description: String,
    val trait: String = "Playful"
)

/**
 * Pet data from Firebase - simplified schema
 */
data class PetData(
    val firebaseKey: String = "",  // The actual Firebase push ID (e.g., "-OfblJ9LlCzvl0nUDW0x")
    val shortId: String = "",      // The user-friendly ID (e.g., "DU666")
    val name: String = "Unknown",
    val description: String = "",
    val colors: PetColors = PetColors(),
    val level: Int = 1,
    val xp: Float = 0f,            // XP as decimal (0.0 to 1.0), displayed as percentage
    val xpToNextLevel: Float = 1f, // 1.0 = 100%
    val bonesFetched: Int = 0      // Total bones successfully fetched and returned
)

data class PetColors(
    val coat: String = "#3A8DFF",
    val eye: String = "#FFFFFF",
    val snout: String = "#222222"
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
