package com.cybergarden.metapetz

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.core.Color4
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Sphere
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * PetLocomotion - Handles point-to-move functionality for pets
 *
 * Features:
 * - Detects controller/hand pointing direction
 * - Raycasts to floor plane to find target position
 * - Smoothly moves pet to target with facing direction
 * - Shows visual target marker
 *
 * Usage:
 * 1. Create instance with coroutine scope
 * 2. Register the system via systemManager.registerSystem(locomotion.createPointingSystem())
 * 3. Call setPetEntity() when pet is selected
 * 4. Call cleanup() when done
 */
class PetLocomotion(
    private val scope: CoroutineScope,
    private val floorY: Float = 0f,
    private val walkSpeed: Float = 0.5f // meters per second
) {
    companion object {
        private const val TAG = "PetLocomotion"
    }

    // Current pet entity to move
    private var petEntity: Entity? = null
    private var panelEntity: Entity? = null

    // Walking state
    private var walkJob: Job? = null
    private var isWalking = false

    // Target marker
    private var targetMarkerEntity: Entity? = null

    // Callbacks
    var onWalkStart: (() -> Unit)? = null
    var onWalkEnd: (() -> Unit)? = null
    var onTargetSet: ((Vector3) -> Unit)? = null

    /**
     * Set the pet entity to control
     */
    fun setPetEntity(entity: Entity?, panel: Entity? = null) {
        petEntity = entity
        panelEntity = panel
        Log.d(TAG, "Pet entity set: ${entity != null}")
    }

    /**
     * Get if pet is currently walking
     */
    fun isCurrentlyWalking(): Boolean = isWalking

    /**
     * Stop current walk immediately
     */
    fun stopWalking() {
        walkJob?.cancel()
        walkJob = null
        isWalking = false
    }

    /**
     * Move pet to target position with smooth interpolation
     */
    fun moveTo(target: Vector3) {
        val pet = petEntity ?: return

        // Cancel any existing walk
        walkJob?.cancel()

        walkJob = scope.launch {
            isWalking = true
            onWalkStart?.invoke()

            try {
                // Get current position
                val transform = pet.getComponent<Transform>()
                val startPos = transform.transform.t

                // Calculate distance and duration
                val dx = target.x - startPos.x
                val dz = target.z - startPos.z
                val distance = sqrt(dx * dx + dz * dz)

                // Skip if already at target
                if (distance < 0.05f) {
                    isWalking = false
                    onWalkEnd?.invoke()
                    return@launch
                }

                val duration = (distance / walkSpeed * 1000).toLong().coerceAtLeast(100)

                // Calculate facing direction (XZ plane only)
                val direction = normalizeVector(Vector3(dx, 0f, dz))

                // Calculate facing rotation
                val facingRotation = if (direction.x != 0f || direction.z != 0f) {
                    lookRotation(direction)
                } else {
                    transform.transform.q
                }

                val startTime = System.currentTimeMillis()

                Log.d(TAG, "Walking from $startPos to $target, distance: $distance, duration: ${duration}ms")

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                    // Interpolate position
                    val newPos = Vector3(
                        startPos.x + dx * progress,
                        startPos.y, // Keep same height (relative to parent)
                        startPos.z + dz * progress
                    )

                    // Update transform
                    val newTransform = pet.getComponent<Transform>()
                    newTransform.transform.t = newPos

                    // Apply facing rotation combined with model orientation fix
                    // Most models need 180° X-axis flip to be upright
                    val xFlipRadians = PI.toFloat()
                    val xFlip = Quaternion(
                        kotlin.math.sin(xFlipRadians / 2).toFloat(),
                        0f,
                        0f,
                        kotlin.math.cos(xFlipRadians / 2).toFloat()
                    )
                    newTransform.transform.q = multiplyQuaternions(facingRotation, xFlip)

                    pet.setComponent(newTransform)

                    if (progress >= 1f) break
                    delay(16) // ~60 FPS
                }

                Log.d(TAG, "Walk complete")

            } catch (e: Exception) {
                Log.e(TAG, "Walk error: ${e.message}")
            } finally {
                isWalking = false
                onWalkEnd?.invoke()
            }
        }
    }

    /**
     * Show a visual marker at target position
     */
    fun showTargetMarker(position: Vector3) {
        // Remove existing marker
        targetMarkerEntity?.destroy()

        // Create new marker (green sphere)
        targetMarkerEntity = Entity.create(
            listOf(
                Mesh(android.net.Uri.parse("mesh://sphere")),
                Sphere(0.03f),
                Material().apply {
                    baseColor = Color4(0.2f, 1f, 0.4f, 0.7f)
                    unlit = true
                },
                Transform(Pose(position, Quaternion())),
                Scale(Vector3(1f, 0.3f, 1f)) // Flatten to disc shape
            )
        )

        // Auto-hide after delay
        scope.launch {
            delay(1500)
            targetMarkerEntity?.destroy()
            targetMarkerEntity = null
        }
    }

    /**
     * Create the pointing detection system
     * Register this with systemManager.registerSystem()
     */
    fun createPointingSystem(): PointToMoveSystem {
        return PointToMoveSystem(
            floorY = floorY,
            onTargetFound = { hitPoint ->
                onTargetSet?.invoke(hitPoint)
                showTargetMarker(hitPoint)
                moveTo(hitPoint)
            }
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopWalking()
        targetMarkerEntity?.destroy()
        targetMarkerEntity = null
        petEntity = null
        panelEntity = null
    }

    // ========== Math Utilities ==========

    private fun normalizeVector(v: Vector3): Vector3 {
        val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        return if (len > 0.0001f) {
            Vector3(v.x / len, v.y / len, v.z / len)
        } else {
            Vector3(0f, 0f, 1f)
        }
    }

    private fun lookRotation(forward: Vector3): Quaternion {
        // Create rotation that faces the forward direction
        // Simplified version for XZ plane movement
        val angle = kotlin.math.atan2(forward.x, forward.z)
        return Quaternion(
            0f,
            kotlin.math.sin(angle / 2).toFloat(),
            0f,
            kotlin.math.cos(angle / 2).toFloat()
        )
    }

    private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
        return Quaternion(
            q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y,
            q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x,
            q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w,
            q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        )
    }
}

/**
 * System that detects controller/hand pointing and triggers movement
 * Runs every frame to check for trigger/pinch input
 */
class PointToMoveSystem(
    private val floorY: Float = 0f,
    private val onTargetFound: (Vector3) -> Unit
) : SystemBase() {

    companion object {
        private const val TAG = "PointToMoveSystem"
    }

    // Track button state to detect press (not hold)
    private var wasPressed = false

    // Cooldown to prevent rapid-fire
    private var lastTriggerTime = 0L
    private val triggerCooldown = 500L // ms

    override fun execute() {
        // Get player's avatar body
        val playerBody = Query.where { has(AvatarBody.id) }
            .eval()
            .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            .firstOrNull()
            ?.getComponent<AvatarBody>() ?: return

        // Get right hand/controller
        val rightHand = playerBody.rightHand
        val controller = rightHand.tryGetComponent<Controller>() ?: return
        val handPose = rightHand.tryGetComponent<Transform>()?.transform ?: return

        // Skip if controller not active
        if (!controller.isActive) {
            wasPressed = false
            return
        }

        // Check for trigger press (controller) or pinch (hand)
        // ButtonTriggerR = controller trigger, ButtonA = hand pinch
        val isPressed = (controller.buttonState and
            (ButtonBits.ButtonTriggerR or ButtonBits.ButtonA)) != 0

        val currentTime = System.currentTimeMillis()

        // Detect press start (transition from not pressed to pressed)
        if (isPressed && !wasPressed && (currentTime - lastTriggerTime > triggerCooldown)) {
            lastTriggerTime = currentTime

            // Get pointing direction (forward from hand orientation)
            val direction = multiplyQuaternionVector(handPose.q, Vector3(0f, 0f, 1f))
            val normalizedDir = normalizeVector(direction)

            // Only process if pointing somewhat downward
            if (normalizedDir.y < -0.1f) {
                // Calculate intersection with floor plane
                // Ray: P = origin + t * direction
                // Plane: y = floorY
                // Solve: origin.y + t * direction.y = floorY
                val t = (floorY - handPose.t.y) / normalizedDir.y

                if (t > 0f && t < 20f) { // Valid range (in front, not too far)
                    val hitPoint = Vector3(
                        handPose.t.x + normalizedDir.x * t,
                        floorY,
                        handPose.t.z + normalizedDir.z * t
                    )

                    Log.d(TAG, "Target found at: $hitPoint")
                    onTargetFound(hitPoint)
                }
            }
        }

        wasPressed = isPressed
    }

    private fun normalizeVector(v: Vector3): Vector3 {
        val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        return if (len > 0.0001f) {
            Vector3(v.x / len, v.y / len, v.z / len)
        } else {
            Vector3(0f, 0f, 1f)
        }
    }

    private fun multiplyQuaternionVector(q: Quaternion, v: Vector3): Vector3 {
        // Rotate vector by quaternion: q * v * q^-1
        val qx = q.x
        val qy = q.y
        val qz = q.z
        val qw = q.w

        // Calculate q * v
        val ix = qw * v.x + qy * v.z - qz * v.y
        val iy = qw * v.y + qz * v.x - qx * v.z
        val iz = qw * v.z + qx * v.y - qy * v.x
        val iw = -qx * v.x - qy * v.y - qz * v.z

        // Calculate result * q^-1
        return Vector3(
            ix * qw + iw * -qx + iy * -qz - iz * -qy,
            iy * qw + iw * -qy + iz * -qx - ix * -qz,
            iz * qw + iw * -qz + ix * -qy - iy * -qx
        )
    }

    private fun sqrt(x: Float): Float = kotlin.math.sqrt(x)
}
