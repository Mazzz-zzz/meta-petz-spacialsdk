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
            shortId = snapshot.child("shortId").getValue(String::class.java) ?: snapshot.key ?: "",
            name = snapshot.child("name").getValue(String::class.java) ?: "Unknown",
            description = snapshot.child("description").getValue(String::class.java) ?: "",
            colors = colors,
            level = snapshot.child("level").getValue(Int::class.java) ?: 1,
            xp = snapshot.child("xp").getValue(Int::class.java) ?: 0,
            xpToNextLevel = snapshot.child("xpToNextLevel").getValue(Int::class.java) ?: 100
        )
    }
}
