package com.cybergarden.metapetz.ecs

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.SystemManager
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * PettingDetector - Detects petting gestures when hands are near the pet and moving
 *
 * When a hand is within the pet's bounds and moving around, accumulates "petting" points.
 * When threshold is reached, triggers petting callback with XP reward and heart particles.
 *
 * Usage:
 * 1. Create instance with coroutine scope and system manager
 * 2. Set getPetEntity to provide current pet entity
 * 3. Set onPettingDetected callback to handle petting events (spawn hearts, award XP)
 * 4. Call start() to begin detection
 * 5. Call stop() when done
 */
class PettingDetector(
    private val scope: CoroutineScope,
    private val systemManager: SystemManager
) {
    companion object {
        private const val TAG = "PettingDetector"

        // Pet interaction range - how close hand must be to pet center
        private const val PET_RANGE = 0.20f  // 20cm radius around pet

        // Cumulative movement threshold to trigger petting
        private const val PETTING_THRESHOLD = 0.15f  // 15cm of movement while in range

        // Time window - reset cumulative movement after this
        private const val WINDOW_MS = 2000L

        // Cooldown after successful petting detection (allows hearts to spawn)
        private const val COOLDOWN_MS = 500L

        // How often to check hand positions
        private const val SAMPLE_DELAY_MS = 30L

        // XP reward per petting action (0.01 = 1%)
        const val XP_PER_PET = 0.01f
    }

    private var detectionJob: Job? = null
    private var isRunning = false

    // Cumulative movement tracking
    private var lastLeftHandPos: Vector3? = null
    private var lastRightHandPos: Vector3? = null
    private var cumulativeMovement: Float = 0f
    private var windowStartTimeMs: Long = 0L
    private var lastDetectionTimeMs: Long = 0L

    // Debug values (exposed for UI)
    var currentDistanceToPet: Float = -1f
        private set
    var currentCumulativeMovement: Float = 0f
        private set
    var isHandInRange: Boolean = false
        private set

    // Callback to get current pet entity
    var getPetEntity: (() -> Entity?)? = null

    // Callback when petting is detected - passes hand position for heart spawning
    var onPettingDetected: ((handPosition: Vector3) -> Unit)? = null

    // Callback for each heart spawn (called multiple times during petting)
    var onSpawnHeart: ((position: Vector3) -> Unit)? = null

    /**
     * Start petting detection
     */
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Petting detection already running")
            return
        }

        Log.d(TAG, "Starting petting detection")
        isRunning = true
        resetTracking()

        detectionJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    detectPetting()
                    delay(SAMPLE_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Petting detection error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    /**
     * Stop petting detection
     */
    fun stop() {
        Log.d(TAG, "Stopping petting detection")
        isRunning = false
        detectionJob?.cancel()
        detectionJob = null
        resetTracking()
    }

    private fun resetTracking() {
        lastLeftHandPos = null
        lastRightHandPos = null
        cumulativeMovement = 0f
        windowStartTimeMs = 0L
        currentCumulativeMovement = 0f
        isHandInRange = false
        currentDistanceToPet = -1f
    }

    /**
     * Check if detection is running
     */
    fun isDetecting(): Boolean = isRunning

    /**
     * Main detection logic - called every frame
     */
    private fun detectPetting() {
        val currentTime = System.currentTimeMillis()

        // Check cooldown
        if (currentTime - lastDetectionTimeMs < COOLDOWN_MS) {
            return
        }

        // Get pet position
        val petEntity = getPetEntity?.invoke() ?: return
        val petPos = petEntity.tryGetComponent<Transform>()?.transform?.t ?: return

        // Get hand positions
        val leftHandPos = getLeftHandPosition()
        val rightHandPos = getRightHandPosition()

        if (leftHandPos == null && rightHandPos == null) {
            return
        }

        // Check for invalid hand positions (at origin means tracking lost)
        val validLeftHand = leftHandPos != null && !isNearOrigin(leftHandPos)
        val validRightHand = rightHandPos != null && !isNearOrigin(rightHandPos)

        if (!validLeftHand && !validRightHand) {
            return
        }

        // Calculate distances to pet for each hand
        val leftDistance = if (validLeftHand) distance3D(leftHandPos!!, petPos) else Float.MAX_VALUE
        val rightDistance = if (validRightHand) distance3D(rightHandPos!!, petPos) else Float.MAX_VALUE

        // Use the closer hand
        val closerHand: Vector3
        val closerDistance: Float
        val lastPos: Vector3?
        val isLeftHand: Boolean

        if (leftDistance < rightDistance) {
            closerHand = leftHandPos!!
            closerDistance = leftDistance
            lastPos = lastLeftHandPos
            isLeftHand = true
        } else {
            closerHand = rightHandPos!!
            closerDistance = rightDistance
            lastPos = lastRightHandPos
            isLeftHand = false
        }

        currentDistanceToPet = closerDistance

        // Check if hand is within pet range
        val inRange = closerDistance < PET_RANGE
        isHandInRange = inRange

        if (inRange) {
            // Reset window if expired
            if (windowStartTimeMs == 0L) {
                windowStartTimeMs = currentTime
            } else if (currentTime - windowStartTimeMs > WINDOW_MS) {
                cumulativeMovement = 0f
                windowStartTimeMs = currentTime
            }

            // Calculate movement from last position
            if (lastPos != null) {
                val movement = distance3D(closerHand, lastPos)

                // Only count significant movement (filter out noise)
                if (movement > 0.005f) {
                    cumulativeMovement += movement
                    currentCumulativeMovement = cumulativeMovement

                    // Spawn occasional hearts while petting (every ~3cm of movement)
                    if (cumulativeMovement > 0.03f && Random.nextFloat() < 0.3f) {
                        // Spawn heart slightly above hand position
                        val heartPos = Vector3(
                            closerHand.x + (Random.nextFloat() - 0.5f) * 0.05f,
                            closerHand.y + 0.05f + Random.nextFloat() * 0.03f,
                            closerHand.z + (Random.nextFloat() - 0.5f) * 0.05f
                        )
                        onSpawnHeart?.invoke(heartPos)
                    }

                    // Check if threshold reached
                    if (cumulativeMovement >= PETTING_THRESHOLD) {
                        Log.d(TAG, "PETTING DETECTED! Cumulative movement: $cumulativeMovement")
                        lastDetectionTimeMs = currentTime

                        // Trigger callback with hand position
                        onPettingDetected?.invoke(closerHand)

                        // Reset for next detection
                        cumulativeMovement = 0f
                        windowStartTimeMs = 0L
                        currentCumulativeMovement = 0f
                    }
                }
            }

            // Store current position for next frame
            if (isLeftHand) {
                lastLeftHandPos = closerHand
            } else {
                lastRightHandPos = closerHand
            }
        } else {
            // Outside range - don't reset completely, just pause accumulation
            // This allows brief exits from range without losing progress
        }

        // Always update the "other" hand position for smooth switching
        if (validLeftHand) lastLeftHandPos = leftHandPos
        if (validRightHand) lastRightHandPos = rightHandPos
    }

    /**
     * Calculate 3D distance between two points
     */
    private fun distance3D(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Get left hand position from PlayerBodyAttachmentSystem
     */
    private fun getLeftHandPosition(): Vector3? {
        return systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.leftHand
            ?.tryGetComponent<Transform>()
            ?.transform
            ?.t
    }

    /**
     * Get right hand position from PlayerBodyAttachmentSystem
     */
    private fun getRightHandPosition(): Vector3? {
        return systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.rightHand
            ?.tryGetComponent<Transform>()
            ?.transform
            ?.t
    }

    /**
     * Check if a position is near origin (0,0,0) - indicates tracking loss
     */
    private fun isNearOrigin(pos: Vector3): Boolean {
        return pos.x * pos.x + pos.y * pos.y + pos.z * pos.z < 0.01f
    }
}
