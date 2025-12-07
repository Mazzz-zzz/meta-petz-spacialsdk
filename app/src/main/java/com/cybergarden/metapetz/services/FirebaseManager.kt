package com.cybergarden.metapetz.services

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.cybergarden.metapetz.model.PetColors
import com.cybergarden.metapetz.model.PetData
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
     * Get the first pet from a specific user (e.g., "demo") with full PetData
     */
    fun getFirstPetFromUser(targetUserId: String, onResult: (PetData?) -> Unit) {
        db.reference
            .child("users")
            .child(targetUserId)
            .child("pets")
            .limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val firstPetSnapshot = snapshot.children.firstOrNull()
                    if (firstPetSnapshot != null) {
                        val petData = parsePetData(firstPetSnapshot)
                        Log.d(TAG, "First pet from $targetUserId: ${petData.name}")
                        onResult(petData)
                    } else {
                        Log.d(TAG, "No pets found for $targetUserId")
                        onResult(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting first pet from $targetUserId", error.toException())
                    onResult(null)
                }
            })
    }

    /**
     * Update a pet's XP value
     * @param targetUserId The user who owns the pet
     * @param petFirebaseKey The pet's Firebase key (the push ID like "-OfblJ9LlCzvl0nUDW0x")
     * @param newXp The new XP value (0.0 to 1.0 representing 0% to 100%)
     */
    fun updatePetXp(targetUserId: String, petFirebaseKey: String, newXp: Float, onComplete: ((Boolean) -> Unit)? = null) {
        db.reference
            .child("users")
            .child(targetUserId)
            .child("pets")
            .child(petFirebaseKey)
            .child("xp")
            .setValue(newXp)
            .addOnSuccessListener {
                Log.d(TAG, "Updated XP for $petFirebaseKey to $newXp")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating XP for $petFirebaseKey", e)
                onComplete?.invoke(false)
            }
    }

    /**
     * Update a pet's level
     * @param targetUserId The user who owns the pet
     * @param petFirebaseKey The pet's Firebase key (the push ID like "-OfblJ9LlCzvl0nUDW0x")
     * @param newLevel The new level value
     */
    fun updatePetLevel(targetUserId: String, petFirebaseKey: String, newLevel: Int, onComplete: ((Boolean) -> Unit)? = null) {
        db.reference
            .child("users")
            .child(targetUserId)
            .child("pets")
            .child(petFirebaseKey)
            .child("level")
            .setValue(newLevel)
            .addOnSuccessListener {
                Log.d(TAG, "Updated level for $petFirebaseKey to $newLevel")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating level for $petFirebaseKey", e)
                onComplete?.invoke(false)
            }
    }

    /**
     * Parse a DataSnapshot into PetData
     */
    private fun parsePetData(snapshot: DataSnapshot): PetData {
        val colorsSnapshot = snapshot.child("colors")
        val colors = PetColors(
            coat = colorsSnapshot.child("coat").getValue(String::class.java) ?: "#3A8DFF",
            eye = colorsSnapshot.child("eye").getValue(String::class.java) ?: "#FFFFFF",
            snout = colorsSnapshot.child("snout").getValue(String::class.java) ?: "#222222"
        )

        return PetData(
            firebaseKey = snapshot.key ?: "",  // The actual Firebase push ID (e.g., "-OfblJ9LlCzvl0nUDW0x")
            shortId = snapshot.child("shortId").getValue(String::class.java) ?: snapshot.key ?: "",
            name = snapshot.child("name").getValue(String::class.java) ?: "Unknown",
            description = snapshot.child("description").getValue(String::class.java) ?: "",
            colors = colors,
            level = snapshot.child("level").getValue(Int::class.java) ?: 1,
            xp = snapshot.child("xp").getValue(Double::class.java)?.toFloat() ?: 0f,
            xpToNextLevel = snapshot.child("xpToNextLevel").getValue(Double::class.java)?.toFloat() ?: 1f,
            bonesFetched = snapshot.child("bonesFetched").getValue(Int::class.java) ?: 0
        )
    }

    /**
     * Increment the bones fetched counter for a pet.
     * Called when the pet successfully returns a bone to the player.
     * @param targetUserId The user who owns the pet
     * @param petFirebaseKey The pet's Firebase key
     * @param onComplete Callback with the new count, or null on failure
     */
    fun incrementBonesFetched(targetUserId: String, petFirebaseKey: String, onComplete: ((Int?) -> Unit)? = null) {
        val petRef = db.reference
            .child("users")
            .child(targetUserId)
            .child("pets")
            .child(petFirebaseKey)
            .child("bonesFetched")

        petRef.get().addOnSuccessListener { snapshot ->
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            val newCount = currentCount + 1

            petRef.setValue(newCount)
                .addOnSuccessListener {
                    Log.d(TAG, "Incremented bonesFetched for $petFirebaseKey: $currentCount -> $newCount")
                    onComplete?.invoke(newCount)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error incrementing bonesFetched for $petFirebaseKey", e)
                    onComplete?.invoke(null)
                }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error reading bonesFetched for $petFirebaseKey", e)
            onComplete?.invoke(null)
        }
    }
}
