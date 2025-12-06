package com.cybergarden.metapetz.ecs

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.core.SystemManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * ClapDetector - Detects clapping gestures using hand tracking
 *
 * Detects when hands come together and apart in a clapping motion.
 * Requires at least 3 clap cycles within a time window to trigger.
 *
 * Usage:
 * 1. Create instance with coroutine scope and system manager
 * 2. Call start() to begin detection
 * 3. Set onClapDetected callback to handle clap events
 * 4. Call stop() when done
 */
class ClapDetector(
    private val scope: CoroutineScope,
    private val systemManager: SystemManager
) {
    companion object {
        private const val TAG = "ClapDetector"

        // Distance thresholds in meters (palm-to-palm distances from hand tracking)
        private const val TOGETHER_THRESHOLD = 0.30f  // Hands considered "together" when closer than this
        private const val APART_THRESHOLD = 0.50f     // Hands considered "apart" when further than this

        // Timing
        private const val CLAP_WINDOW_MS = 3000L      // Time window to complete 3 claps
        private const val REQUIRED_CLAPS = 3          // Number of clap cycles required
        private const val COOLDOWN_MS = 2000L         // Cooldown after successful detection
        private const val SAMPLE_DELAY_MS = 30L       // How often to check hand positions
    }

    // Detection state
    private enum class HandState {
        UNKNOWN,
        TOGETHER,
        APART
    }

    private var detectionJob: Job? = null
    private var isRunning = false

    // State tracking
    private var currentState = HandState.UNKNOWN
    private var clapCount = 0
    private var firstClapTimeMs = 0L
    private var lastDetectionTimeMs = 0L

    // Callback when clap is detected
    var onClapDetected: (() -> Unit)? = null

    /**
     * Start clap detection
     */
    fun start() {
        if (isRunning) {
            Log.d(TAG, "Clap detection already running")
            return
        }

        Log.d(TAG, "Starting clap detection")
        isRunning = true
        currentState = HandState.UNKNOWN
        clapCount = 0
        firstClapTimeMs = 0L

        detectionJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    detectClap()
                    delay(SAMPLE_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Clap detection error: ${e.message}")
                    delay(100)
                }
            }
        }
    }

    /**
     * Stop clap detection
     */
    fun stop() {
        Log.d(TAG, "Stopping clap detection")
        isRunning = false
        detectionJob?.cancel()
        detectionJob = null
        currentState = HandState.UNKNOWN
        clapCount = 0
    }

    /**
     * Check if detection is running
     */
    fun isDetecting(): Boolean = isRunning

    // Periodic logging for debugging
    private var lastLogTime = 0L

    /**
     * Main detection logic - called every frame
     */
    private fun detectClap() {
        val currentTime = System.currentTimeMillis()

        // Check cooldown
        if (currentTime - lastDetectionTimeMs < COOLDOWN_MS) {
            return
        }

        // Get hand positions
        val leftHandPos = getLeftHandPosition()
        val rightHandPos = getRightHandPosition()

        // Log periodically for debugging
        if (currentTime - lastLogTime > 2000) {
            lastLogTime = currentTime
            Log.d(TAG, "Hand positions: left=$leftHandPos, right=$rightHandPos")
        }

        if (leftHandPos == null || rightHandPos == null) {
            return
        }

        // Calculate distance between hands
        val distance = vectorDistance(leftHandPos, rightHandPos)

        // Log distance periodically
        if (currentTime - lastLogTime < 50) {
            Log.d(TAG, "Hand distance: $distance, state: $currentState")
        }

        // Determine new state based on distance
        val newState = when {
            distance < TOGETHER_THRESHOLD -> HandState.TOGETHER
            distance > APART_THRESHOLD -> HandState.APART
            else -> currentState // Hysteresis - keep current state in between thresholds
        }

        // Detect state transitions
        if (newState != currentState && currentState != HandState.UNKNOWN) {
            Log.d(TAG, "State transition: $currentState -> $newState")
            // State changed - check for clap cycle
            if (currentState == HandState.APART && newState == HandState.TOGETHER) {
                // Hands came together - this is a clap!
                handleClapCycle(currentTime)
            }
        }

        currentState = newState
    }

    /**
     * Handle a completed clap cycle (apart -> together)
     */
    private fun handleClapCycle(currentTime: Long) {
        // Reset if too much time has passed since first clap
        if (clapCount > 0 && currentTime - firstClapTimeMs > CLAP_WINDOW_MS) {
            Log.d(TAG, "Clap sequence timed out, resetting")
            clapCount = 0
            firstClapTimeMs = 0L
        }

        // Record this clap
        clapCount++
        if (clapCount == 1) {
            firstClapTimeMs = currentTime
        }

        Log.d(TAG, "Clap detected! Count: $clapCount/$REQUIRED_CLAPS")

        // Check if we have enough claps
        if (clapCount >= REQUIRED_CLAPS) {
            Log.d(TAG, "Clap sequence complete! Triggering callback")
            lastDetectionTimeMs = currentTime
            clapCount = 0
            firstClapTimeMs = 0L
            currentState = HandState.UNKNOWN

            // Trigger callback
            onClapDetected?.invoke()
        }
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
     * Calculate distance between two vectors
     */
    private fun vectorDistance(a: Vector3, b: Vector3): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
