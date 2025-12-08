package com.cybergarden.metapetz.ecs

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.core.SystemManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ClapDetector - Detects clapping gestures using cumulative hand displacement
 *
 * When hands are close together (< ACTIVE_RANGE), tracks total movement.
 * If cumulative displacement exceeds threshold within time window, triggers clap.
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

        // Only track displacement when hands are closer than this
        private const val ACTIVE_RANGE = 0.10f

        // Cumulative displacement threshold to trigger clap
        private const val DISPLACEMENT_THRESHOLD = 0.40f

        // Time window - reset cumulative displacement after this
        private const val WINDOW_MS = 2000L

        // Cooldown after successful detection
        private const val COOLDOWN_MS = 2000L

        // How often to check hand positions
        private const val SAMPLE_DELAY_MS = 30L

        // ===== RAISE HAND GESTURE (for sit command) =====
        // Right hand must raise this much (Y direction) to trigger sit
        private const val RAISE_HAND_THRESHOLD = 0.50f  // 50cm

        // Left hand must move less than this ratio of right hand movement
        // If right hand moves 50cm, left must move < 5cm (10%)
        private const val LEFT_HAND_MAX_RATIO = 0.10f

        // Time window for raise hand gesture
        private const val RAISE_HAND_WINDOW_MS = 2000L

        // Cooldown after raise hand detection
        private const val RAISE_HAND_COOLDOWN_MS = 2000L
    }

    private var detectionJob: Job? = null
    private var isRunning = false

    // Cumulative displacement tracking (clap)
    private var lastDistance: Float = -1f
    private var cumulativeDisplacement: Float = 0f
    private var windowStartTimeMs: Long = 0L
    private var lastDetectionTimeMs: Long = 0L

    // Current hand distance (exposed for debug UI)
    var currentDistance: Float = 0f
        private set

    // Cumulative displacement (exposed for debug UI)
    var currentCumulative: Float = 0f
        private set

    // Raise hand tracking (sit gesture)
    private var lastRightHandY: Float? = null
    private var lastLeftHandY: Float? = null
    private var cumulativeRightHandRaise: Float = 0f
    private var cumulativeLeftHandMovement: Float = 0f
    private var raiseHandWindowStartMs: Long = 0L
    private var lastRaiseHandDetectionMs: Long = 0L
    private var raiseHandTriggeredThisWindow: Boolean = false  // Prevent multiple triggers per window

    // Raise hand cumulative values (exposed for debug UI)
    var currentRightHandRaise: Float = 0f
        private set
    var currentLeftHandMovement: Float = 0f
        private set

    // Callback when clap is detected
    var onClapDetected: (() -> Unit)? = null

    // Callback when raise hand gesture is detected (for sit command)
    var onRaiseHandDetected: (() -> Unit)? = null

    // Lambda to check if pet is attentive (for gating trigger button accumulation)
    var isAttentive: (() -> Boolean)? = null

    // Callback when hands enter active range (for debug)
    var onHandsTogether: (() -> Unit)? = null

    // Callback when hands leave active range (for debug)
    var onHandsApart: (() -> Unit)? = null

    // Track if we're in active range
    private var wasInRange = false

    // Track grip button state for controller input
    private var wasGripPressed = false

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
        resetTracking()

        detectionJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    detectClap()
                    detectRaiseHand()
                    delay(SAMPLE_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Gesture detection error: ${e.message}")
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
        resetTracking()
    }

    private fun resetTracking() {
        lastDistance = -1f
        cumulativeDisplacement = 0f
        windowStartTimeMs = 0L
        currentCumulative = 0f
        wasInRange = false
        // Reset raise hand tracking
        resetRaiseHandTracking()
    }

    private fun resetRaiseHandTracking() {
        lastRightHandY = null
        lastLeftHandY = null
        cumulativeRightHandRaise = 0f
        cumulativeLeftHandMovement = 0f
        raiseHandWindowStartMs = 0L
        raiseHandTriggeredThisWindow = false
        currentRightHandRaise = 0f
        currentLeftHandMovement = 0f
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

        // Check for controller grip button press (adds 0.1 to cumulative)
        checkControllerGripButton(currentTime)

        // Get hand positions
        val leftHandPos = getLeftHandPosition()
        val rightHandPos = getRightHandPosition()

        if (leftHandPos == null || rightHandPos == null) {
            return
        }

        // Check for invalid hand positions (at origin means tracking lost)
        if (isNearOrigin(leftHandPos) || isNearOrigin(rightHandPos)) {
            return
        }

        // Calculate horizontal plane distance (X + Z, ignoring Y height)
        val dx = rightHandPos.x - leftHandPos.x
        val dz = rightHandPos.z - leftHandPos.z
        val distance = sqrt(dx * dx + dz * dz)

        // Ignore invalid readings (0 distance = tracking glitch)
        if (distance < 0.01f) {
            return
        }

        currentDistance = distance  // Store for debug UI

        // Check if hands are within active range
        val inRange = distance < ACTIVE_RANGE

        // Detect entering/leaving range for debug sounds
        if (inRange && !wasInRange) {
            onHandsTogether?.invoke()
            Log.d(TAG, "Entered active range, distance: $distance")
        } else if (!inRange && wasInRange) {
            onHandsApart?.invoke()
            Log.d(TAG, "Left active range, distance: $distance")
        }
        wasInRange = inRange

        // Only track displacement when in range
        if (inRange) {
            // Reset window if expired
            if (windowStartTimeMs == 0L) {
                windowStartTimeMs = currentTime
            } else if (currentTime - windowStartTimeMs > WINDOW_MS) {
                Log.d(TAG, "Window expired, resetting. Cumulative was: $cumulativeDisplacement")
                cumulativeDisplacement = 0f
                windowStartTimeMs = currentTime
            }

            // Calculate displacement from last reading
            if (lastDistance >= 0) {
                val displacement = abs(distance - lastDistance)
                cumulativeDisplacement += displacement
                currentCumulative = cumulativeDisplacement  // For debug UI

                // Log periodically
                if (currentTime - lastLogTime > 500) {
                    lastLogTime = currentTime
                    Log.d(TAG, "Cumulative: $cumulativeDisplacement / $DISPLACEMENT_THRESHOLD")
                }

                // Check if threshold reached
                if (cumulativeDisplacement >= DISPLACEMENT_THRESHOLD) {
                    Log.d(TAG, "CLAP DETECTED! Cumulative displacement: $cumulativeDisplacement")
                    lastDetectionTimeMs = currentTime
                    cumulativeDisplacement = 0f
                    windowStartTimeMs = 0L
                    currentCumulative = 0f

                    // Trigger callback
                    onClapDetected?.invoke()
                }
            }

            lastDistance = distance
        } else {
            // Outside range - reset tracking
            lastDistance = -1f
            // Don't reset cumulative here - it resets on window timeout
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
     * Check if a position is near origin (0,0,0) - indicates tracking loss
     */
    private fun isNearOrigin(pos: Vector3): Boolean {
        return pos.x * pos.x + pos.y * pos.y + pos.z * pos.z < 0.01f
    }

    /**
     * Check for controller grip button press and add to cumulative displacement
     */
    private fun checkControllerGripButton(currentTime: Long) {
        val rightHand = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.rightHand ?: return

        val controller = rightHand.tryGetComponent<Controller>() ?: return

        // Use right trigger - controller only
        val isGripPressed = (controller.buttonState and ButtonBits.ButtonTriggerR) != 0

        // Detect press (transition from not pressed to pressed)
        if (isGripPressed && !wasGripPressed) {
            // Only accumulate clap points when pet is NOT attentive (idle/wandering)
            val petAttentive = isAttentive?.invoke() ?: false
            if (petAttentive) {
                Log.d(TAG, "Grip pressed but pet is attentive - ignoring")
                wasGripPressed = isGripPressed
                return
            }

            // Start window if not started
            if (windowStartTimeMs == 0L) {
                windowStartTimeMs = currentTime
            }

            // Add 0.1 to cumulative
            cumulativeDisplacement += 0.1f
            currentCumulative = cumulativeDisplacement
            Log.d(TAG, "Grip pressed! Cumulative: $cumulativeDisplacement / $DISPLACEMENT_THRESHOLD")

            // Check if threshold reached
            if (cumulativeDisplacement >= DISPLACEMENT_THRESHOLD) {
                Log.d(TAG, "CLAP DETECTED via grip button! Cumulative: $cumulativeDisplacement")
                lastDetectionTimeMs = currentTime
                cumulativeDisplacement = 0f
                windowStartTimeMs = 0L
                currentCumulative = 0f
                onClapDetected?.invoke()
            }
        }

        wasGripPressed = isGripPressed
    }

    /**
     * Detect "raise right hand" gesture for sit command.
     *
     * Triggers when:
     * - Right hand raises ~50cm (cumulative Y movement upward)
     * - Left hand stays relatively still (< 10% of right hand movement)
     * - Within 2 second window
     *
     * This is geared toward right-handed users giving a "sit" hand signal.
     */
    private fun detectRaiseHand() {
        val currentTime = System.currentTimeMillis()

        // Check cooldown
        if (currentTime - lastRaiseHandDetectionMs < RAISE_HAND_COOLDOWN_MS) {
            return
        }

        // Get hand positions
        val leftHandPos = getLeftHandPosition()
        val rightHandPos = getRightHandPosition()

        if (leftHandPos == null || rightHandPos == null) {
            return
        }

        // Check for invalid hand positions (at origin means tracking lost)
        if (isNearOrigin(leftHandPos) || isNearOrigin(rightHandPos)) {
            return
        }

        val rightY = rightHandPos.y
        val leftY = leftHandPos.y

        // Start window on first valid sample
        if (raiseHandWindowStartMs == 0L) {
            raiseHandWindowStartMs = currentTime
            lastRightHandY = rightY
            lastLeftHandY = leftY
            return
        }

        // Reset window if expired
        if (currentTime - raiseHandWindowStartMs > RAISE_HAND_WINDOW_MS) {
            resetRaiseHandTracking()
            raiseHandWindowStartMs = currentTime
            lastRightHandY = rightY
            lastLeftHandY = leftY
            return
        }

        // Calculate deltas (only if we have previous values)
        val prevRightY = lastRightHandY
        val prevLeftY = lastLeftHandY

        if (prevRightY != null && prevLeftY != null) {
            val rightDeltaY = rightY - prevRightY
            val leftDeltaY = abs(leftY - prevLeftY)

            // Only accumulate upward movement for right hand (positive Y delta)
            // This ensures we're tracking a raise, not just any movement
            if (rightDeltaY > 0.001f) {  // Small threshold to ignore noise
                cumulativeRightHandRaise += rightDeltaY
            }

            // Accumulate absolute Y movement for left hand (should stay still)
            if (leftDeltaY > 0.001f) {  // Small threshold to ignore noise
                cumulativeLeftHandMovement += leftDeltaY
            }

            // Update debug values
            currentRightHandRaise = cumulativeRightHandRaise
            currentLeftHandMovement = cumulativeLeftHandMovement

            // Check if gesture is complete (only trigger once per window)
            if (cumulativeRightHandRaise >= RAISE_HAND_THRESHOLD && !raiseHandTriggeredThisWindow) {
                // Calculate max allowed left hand movement
                val maxLeftMovement = cumulativeRightHandRaise * LEFT_HAND_MAX_RATIO

                // Left hand must have moved at least a tiny bit (not null/zero tracking)
                // but less than the threshold ratio
                val leftHandValid = cumulativeLeftHandMovement > 0.001f &&
                                   cumulativeLeftHandMovement < maxLeftMovement

                // Mark as triggered so we don't fire multiple times
                raiseHandTriggeredThisWindow = true

                if (leftHandValid) {
                    Log.d(TAG, "RAISE HAND DETECTED! Right: $cumulativeRightHandRaise, Left: $cumulativeLeftHandMovement (max: $maxLeftMovement)")
                    lastRaiseHandDetectionMs = currentTime
                    // Don't reset immediately - let window timer reset so user can see the result
                    onRaiseHandDetected?.invoke()
                } else {
                    // Left hand moved too much or not at all - probably a different gesture
                    Log.d(TAG, "Raise hand rejected - Left hand movement: $cumulativeLeftHandMovement (max: $maxLeftMovement)")
                    // Don't reset immediately - let window timer reset so user can see why it failed
                }
            }
        }

        // Store current positions for next frame
        lastRightHandY = rightY
        lastLeftHandY = leftY
    }
}
