package com.cybergarden.metapetz.services

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.cybergarden.metapetz.model.Accessory
import com.cybergarden.metapetz.model.PetColors
import com.cybergarden.metapetz.model.PetData
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseManager(private val context: Context) {

    private val db: FirebaseDatabase

    init {
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:000000000000:android:metapetz")
                .setDatabaseUrl("https://metapet-hackathon-default-rtdb.asia-southeast1.firebasedatabase.app")
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        db = FirebaseDatabase.getInstance("https://metapet-hackathon-default-rtdb.asia-southeast1.firebasedatabase.app")
    }

    private val TAG = "FirebaseManager"

    private val PREFS_NAME = "metapetz_prefs"
    private val USER_ID_KEY = "user_id"
    private val CLAIMED_PETS_KEY = "claimed_pet_ids"  // Comma-separated list of shortIds

    val userId: String by lazy { getOrCreateUserId() }

    private fun getOrCreateUserId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val stableUserId = "device_$androidId"
        val savedId = prefs.getString(USER_ID_KEY, null)
        if (savedId != stableUserId) {
            prefs.edit().putString(USER_ID_KEY, stableUserId).apply()
            createUserDocument(stableUserId)
        }
        return stableUserId
    }

    // ========== MULTI-PET CLAIMING ==========

    /**
     * Get list of all claimed pet short IDs
     */
    fun getClaimedPetIds(): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idsString = prefs.getString(CLAIMED_PETS_KEY, null) ?: return emptyList()
        return idsString.split(",").filter { it.isNotBlank() }
    }

    /**
     * Add a pet ID to claimed list
     */
    private fun addClaimedPetId(shortId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = getClaimedPetIds().toMutableSet()
        currentIds.add(shortId)
        prefs.edit().putString(CLAIMED_PETS_KEY, currentIds.joinToString(",")).apply()
        Log.d(TAG, "Added claimed pet: $shortId, total: ${currentIds.size}")
    }

    /**
     * Remove a pet ID from claimed list
     */
    fun removeClaimedPetId(shortId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = getClaimedPetIds().toMutableSet()
        currentIds.remove(shortId)
        prefs.edit().putString(CLAIMED_PETS_KEY, currentIds.joinToString(",")).apply()
        Log.d(TAG, "Removed claimed pet: $shortId, remaining: ${currentIds.size}")
    }

    /**
     * Clear all claimed pets
     */
    fun clearAllClaimedPets() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(CLAIMED_PETS_KEY).apply()
        Log.d(TAG, "Cleared all claimed pets")
    }

    /**
     * Load all claimed pets data from Firebase
     */
    fun loadAllClaimedPets(onResult: (List<PetData>) -> Unit) {
        val claimedIds = getClaimedPetIds()
        if (claimedIds.isEmpty()) {
            onResult(emptyList())
            return
        }

        val pets = mutableListOf<PetData>()
        var remaining = claimedIds.size

        for (shortId in claimedIds) {
            getPetByShortId(shortId) { petData, _ ->
                if (petData != null) {
                    pets.add(petData)
                }
                remaining--
                if (remaining == 0) {
                    onResult(pets.sortedBy { it.name })
                }
            }
        }
    }

    // ========== PET LOOKUP & CLAIMING ==========

    fun getPetByShortId(shortId: String, onResult: (PetData?, String?) -> Unit) {
        db.reference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userPath = userSnapshot.key ?: continue
                    for (petSnapshot in userSnapshot.child("pets").children) {
                        if (petSnapshot.child("shortId").getValue(String::class.java) == shortId) {
                            onResult(parsePetData(petSnapshot, userPath), userPath)
                            return
                        }
                    }
                }
                onResult(null, null)
            }
            override fun onCancelled(error: DatabaseError) { onResult(null, null) }
        })
    }

    fun claimPet(shortId: String, onResult: (Boolean, String?, PetData?) -> Unit) {
        // Check if already claimed by this user
        if (getClaimedPetIds().contains(shortId)) {
            onResult(false, "You already own this pet!", null)
            return
        }

        getPetByShortId(shortId) { petData, userPath ->
            if (petData == null || userPath == null) {
                onResult(false, "Pet not found", null)
                return@getPetByShortId
            }
            checkPetOwnership(userPath, petData.firebaseKey) { currentOwner ->
                if (currentOwner != null && currentOwner != userId) {
                    onResult(false, "This pet is already adopted!", null)
                } else {
                    setPetOwnership(userPath, petData.firebaseKey, userId) { success ->
                        if (success) {
                            addClaimedPetId(shortId)
                            onResult(true, null, petData)
                        } else {
                            onResult(false, "Failed to claim pet", null)
                        }
                    }
                }
            }
        }
    }

    private fun checkPetOwnership(userPath: String, petKey: String, onResult: (String?) -> Unit) {
        db.reference.child("users").child(userPath).child("pets").child(petKey).child("ownerId")
            .get()
            .addOnSuccessListener { onResult(it.getValue(String::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    private fun setPetOwnership(userPath: String, petKey: String, ownerId: String, onResult: (Boolean) -> Unit) {
        db.reference.child("users").child(userPath).child("pets").child(petKey).child("ownerId")
            .setValue(ownerId)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ========== USER & PET MANAGEMENT ==========

    private fun createUserDocument(userId: String) {
        val userData = mapOf(
            "createdAt" to System.currentTimeMillis(),
            "lastActive" to System.currentTimeMillis(),
            "deviceId" to Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        )
        db.reference.child("users").child(userId).updateChildren(userData)
    }

    fun updateLastActive() {
        db.reference.child("users").child(userId).child("lastActive").setValue(System.currentTimeMillis())
    }

    fun getFirstPetFromUser(targetUserId: String, onResult: (PetData?) -> Unit) {
        db.reference.child("users").child(targetUserId).child("pets").limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val pet = snapshot.children.firstOrNull()
                    onResult(if (pet != null) parsePetData(pet, targetUserId) else null)
                }
                override fun onCancelled(error: DatabaseError) { onResult(null) }
            })
    }

    fun updatePetXp(targetUserId: String, petKey: String, newXp: Float, onComplete: ((Boolean) -> Unit)? = null) {
        db.reference.child("users").child(targetUserId).child("pets").child(petKey).child("xp")
            .setValue(newXp)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    fun updatePetLevel(targetUserId: String, petKey: String, newLevel: Int, onComplete: ((Boolean) -> Unit)? = null) {
        db.reference.child("users").child(targetUserId).child("pets").child(petKey).child("level")
            .setValue(newLevel)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    private fun parsePetData(snapshot: DataSnapshot, userPath: String = ""): PetData {
        val colors = snapshot.child("colors")

        // Parse accessories array
        val accessoriesList = mutableListOf<Accessory>()
        val accessoriesSnapshot = snapshot.child("accessories")
        for (accSnapshot in accessoriesSnapshot.children) {
            val accessory = Accessory(
                type = accSnapshot.child("type").getValue(String::class.java) ?: "",
                ass1 = accSnapshot.child("ass1").getValue(String::class.java) ?: "#FFFFFF",
                ass2 = accSnapshot.child("ass2").getValue(String::class.java) ?: "#FFFFFF",
                scale = accSnapshot.child("scale").getValue(Double::class.java)?.toFloat() ?: 1.0f
            )
            if (accessory.type.isNotEmpty()) {
                accessoriesList.add(accessory)
            }
        }

        return PetData(
            firebaseKey = snapshot.key ?: "",
            shortId = snapshot.child("shortId").getValue(String::class.java) ?: snapshot.key ?: "",
            name = snapshot.child("name").getValue(String::class.java) ?: "Unknown",
            description = snapshot.child("description").getValue(String::class.java) ?: "",
            colors = PetColors(
                coat = colors.child("coat").getValue(String::class.java) ?: "#3A8DFF",
                eye = colors.child("eye").getValue(String::class.java) ?: "#FFFFFF",
                snout = colors.child("snout").getValue(String::class.java) ?: "#222222"
            ),
            level = snapshot.child("level").getValue(Int::class.java) ?: 1,
            xp = snapshot.child("xp").getValue(Double::class.java)?.toFloat() ?: 0f,
            xpToNextLevel = snapshot.child("xpToNextLevel").getValue(Double::class.java)?.toFloat() ?: 1f,
            bonesFetched = snapshot.child("bonesFetched").getValue(Int::class.java) ?: 0,
            userPath = userPath,
            accessories = accessoriesList
        )
    }

    fun incrementBonesFetched(targetUserId: String, petKey: String, onComplete: ((Int?) -> Unit)? = null) {
        val ref = db.reference.child("users").child(targetUserId).child("pets").child(petKey).child("bonesFetched")
        ref.get().addOnSuccessListener { snapshot ->
            val newCount = (snapshot.getValue(Int::class.java) ?: 0) + 1
            ref.setValue(newCount)
                .addOnSuccessListener { onComplete?.invoke(newCount) }
                .addOnFailureListener { onComplete?.invoke(null) }
        }.addOnFailureListener { onComplete?.invoke(null) }
    }
}
