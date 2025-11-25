package com.cybergarden.metapetz

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID

/**
 * Manages Firebase Realtime Database operations for MetaPetz app.
 * Handles user identification and pet stats persistence.
 */
class FirebaseManager(private val context: Context) {

    // Use the asia-southeast1 region URL
    private val db = FirebaseDatabase.getInstance("https://metapet-hackathon-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val TAG = "FirebaseManager"

    // Generate or retrieve a unique user ID
    val userId: String by lazy {
        getOrCreateUserId()
    }

    private fun getOrCreateUserId(): String {
        val prefs = context.getSharedPreferences("metapetz_prefs", Context.MODE_PRIVATE)
        var savedId = prefs.getString("user_id", null)

        if (savedId == null) {
            // Create a new unique ID using Android ID + UUID
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            savedId = "${androidId}_${UUID.randomUUID().toString().take(8)}"
            prefs.edit().putString("user_id", savedId).apply()

            // Create user document in database
            createUserDocument(savedId)
        }

        return savedId
    }

    private fun createUserDocument(userId: String) {
        val userData = mapOf(
            "createdAt" to System.currentTimeMillis(),
            "lastActive" to System.currentTimeMillis(),
            "deviceId" to Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        )

        db.reference
            .child("users")
            .child(userId)
            .updateChildren(userData)
            .addOnSuccessListener {
                Log.d(TAG, "User created: $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating user", e)
            }
    }

    /**
     * Save pet stats to Realtime Database
     */
    fun savePetStats(petName: String, stats: PetStats, onComplete: ((Boolean) -> Unit)? = null) {
        val petData = mapOf(
            "hunger" to stats.hunger.toDouble(),
            "happiness" to stats.happiness.toDouble(),
            "health" to stats.health.toDouble(),
            "energy" to stats.energy.toDouble(),
            "level" to stats.level,
            "xp" to stats.xp,
            "xpToNextLevel" to stats.xpToNextLevel,
            "lastUpdated" to System.currentTimeMillis()
        )

        db.reference
            .child("users")
            .child(userId)
            .child("pets")
            .child(petName)
            .setValue(petData)
            .addOnSuccessListener {
                Log.d(TAG, "Pet stats saved: $petName")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving pet stats", e)
                onComplete?.invoke(false)
            }
    }

    /**
     * Load pet stats from Realtime Database
     */
    fun loadPetStats(petName: String, onResult: (PetStats?) -> Unit) {
        db.reference
            .child("users")
            .child(userId)
            .child("pets")
            .child(petName)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val stats = PetStats(
                            hunger = (snapshot.child("hunger").getValue(Double::class.java) ?: 1.0).toFloat(),
                            happiness = (snapshot.child("happiness").getValue(Double::class.java) ?: 1.0).toFloat(),
                            health = (snapshot.child("health").getValue(Double::class.java) ?: 1.0).toFloat(),
                            energy = (snapshot.child("energy").getValue(Double::class.java) ?: 1.0).toFloat(),
                            level = snapshot.child("level").getValue(Int::class.java) ?: 1,
                            xp = snapshot.child("xp").getValue(Int::class.java) ?: 0,
                            xpToNextLevel = snapshot.child("xpToNextLevel").getValue(Int::class.java) ?: 100
                        )
                        Log.d(TAG, "Pet stats loaded: $petName")
                        onResult(stats)
                    } else {
                        Log.d(TAG, "No saved stats for: $petName")
                        onResult(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error loading pet stats", error.toException())
                    onResult(null)
                }
            })
    }

    /**
     * Update user's last active timestamp
     */
    fun updateLastActive() {
        db.reference
            .child("users")
            .child(userId)
            .child("lastActive")
            .setValue(System.currentTimeMillis())
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating last active", e)
            }
    }

    /**
     * Get all saved pets for current user
     */
    fun getAllPets(onResult: (List<String>) -> Unit) {
        db.reference
            .child("users")
            .child(userId)
            .child("pets")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val petNames = snapshot.children.map { it.key ?: "" }.filter { it.isNotEmpty() }
                    onResult(petNames)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting all pets", error.toException())
                    onResult(emptyList())
                }
            })
    }
}
