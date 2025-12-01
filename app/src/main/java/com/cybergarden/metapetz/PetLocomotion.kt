package com.cybergarden.metapetz

import android.util.Log
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.mruk.MRUKEnvironmentRaycastHitResult
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKHit
import com.meta.spatial.mruk.SurfaceType
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Sphere
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PetLocomotion - Handles point-to-move functionality for pets
 *
 * Features:
 * - Detects controller/hand pointing direction
 * - Uses MRUK raycastRoom for scene-aware surface detection (requires Space Setup)
 * - Smoothly moves pet to target with correct facing direction
 * - Shows visual target marker
 *
 * Usage:
 * 1. Create instance with coroutine scope
 * 2. Register the system via systemManager.registerSystem(locomotion.createPointingSystem(mrukFeature))
 * 3. Call setPetEntity() when pet is selected
 * 4. Call cleanup() when done
 *
 * Note: User must complete Space Setup in Settings > Environment Setup > Space Setup
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

    // Reference to the pointing system for cleanup
    private var pointingSystem: PointToMoveSystem? = null

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
     * Pet will face the direction of movement
     */
    fun moveTo(target: Vector3) {
        val pet = petEntity ?: run {
            Log.w(TAG, "moveTo called but petEntity is null - select a pet first")
            return
        }

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
                val dirX = dx / distance
                val dirZ = dz / distance

                // Calculate Y-axis rotation to face movement direction
                // atan2(x, z) gives angle from +Z axis toward +X axis
                val facingAngle = atan2(dirX, dirZ)

                Log.d(TAG, "Walking from $startPos to $target, distance: $distance, duration: ${duration}ms, facing: ${Math.toDegrees(facingAngle.toDouble())}°")

                val startTime = System.currentTimeMillis()

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                    // Interpolate position
                    val newPos = Vector3(
                        startPos.x + dx * progress,
                        startPos.y, // Keep same height (relative to parent)
                        startPos.z + dz * progress
                    )

                    // Build rotation: Y-axis rotation for facing + X-axis flip for model orientation
                    // Step 1: Y-axis rotation to face movement direction
                    val yRotation = Quaternion(
                        0f,
                        sin(facingAngle / 2).toFloat(),
                        0f,
                        cos(facingAngle / 2).toFloat()
                    )

                    // Step 2: X-axis 180° flip to orient model upright (most GLB models need this)
                    val xFlipAngle = PI.toFloat()
                    val xFlip = Quaternion(
                        sin(xFlipAngle / 2).toFloat(),
                        0f,
                        0f,
                        cos(xFlipAngle / 2).toFloat()
                    )

                    // Combine: first apply xFlip, then yRotation
                    // This makes the pet face forward in movement direction while staying upright
                    val finalRotation = multiplyQuaternions(yRotation, xFlip)

                    // Update transform
                    val newTransform = pet.getComponent<Transform>()
                    newTransform.transform.t = newPos
                    newTransform.transform.q = finalRotation
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

        // Create new marker (green disc)
        targetMarkerEntity = Entity.create(
            listOf(
                Mesh(android.net.Uri.parse("mesh://sphere")),
                Sphere(0.05f),
                Material().apply {
                    baseColor = Color4(0.2f, 1f, 0.4f, 0.8f)
                    unlit = true
                },
                Transform(Pose(position, Quaternion())),
                Scale(Vector3(1f, 0.2f, 1f)) // Flatten to disc shape
            )
        )

        // Auto-hide after delay
        scope.launch {
            delay(2000)
            targetMarkerEntity?.destroy()
            targetMarkerEntity = null
        }
    }

    /**
     * Create the pointing detection system with MRUK support
     * Register this with systemManager.registerSystem()
     *
     * Features:
     * - Persistent pointer that updates every frame showing where you're pointing
     * - DEPTH mode raycasting (works without Space Setup)
     * - Trigger press to confirm target and move pet
     *
     * @param mrukFeature Required - MRUK feature for scene-aware raycasting
     */
    fun createPointingSystem(mrukFeature: MRUKFeature): PointToMoveSystem {
        val system = PointToMoveSystem(
            mrukFeature = mrukFeature,
            floorY = floorY,
            onTargetFound = { hitPoint ->
                onTargetSet?.invoke(hitPoint)
                showTargetMarker(hitPoint)
                moveTo(hitPoint)
            }
        )
        pointingSystem = system
        return system
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopWalking()
        targetMarkerEntity?.destroy()
        targetMarkerEntity = null
        pointingSystem?.cleanup()
        pointingSystem = null
        petEntity = null
        panelEntity = null
    }

    // ========== Math Utilities ==========

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
 *
 * Features:
 * - Persistent pointer that updates every frame showing where you're pointing
 * - Raycasting priority (like MrukSample):
 *   1. raycastEnvironment (DEPTH mode) - Uses live depth sensor, works WITHOUT Space Setup
 *   2. raycastRoom (SCENE mode) - Uses scene data, requires Space Setup
 *   3. Floor plane fallback - Simple Y=0 plane intersection
 * - Trigger press to confirm target and move pet
 */
class PointToMoveSystem(
    private val mrukFeature: MRUKFeature,
    private val floorY: Float = 0f,
    private val onTargetFound: (Vector3) -> Unit
) : SystemBase() {

    companion object {
        private const val TAG = "PointToMoveSystem"
    }

    // Persistent pointer entity - shows where you're pointing in real-time
    private var pointerEntity: Entity? = null

    // Cooldown to prevent rapid-fire
    private var lastTriggerTime = 0L
    private val triggerCooldown = 500L // ms

    // Log room status periodically
    private var lastRoomCheckTime = 0L
    private val roomCheckInterval = 5000L // Check every 5 seconds

    /**
     * Get right hand/controller using PlayerBodyAttachmentSystem (like MrukSample)
     */
    private fun getRightHand(): Entity? {
        return systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.rightHand
    }

    /**
     * Create or get the persistent pointer entity
     */
    private fun getOrCreatePointer(): Entity {
        pointerEntity?.let { return it }

        // Create a green glowing sphere as the pointer
        val pointer = Entity.create(
            listOf(
                Mesh(android.net.Uri.parse("mesh://sphere")),
                Sphere(0.03f), // Small sphere
                Material().apply {
                    baseColor = Color4(0.2f, 1f, 0.4f, 1f) // Green
                    unlit = true
                },
                Transform(Pose(Vector3(0f, -100f, 0f), Quaternion())), // Start hidden
                Scale(Vector3(1f, 0.3f, 1f)), // Flatten slightly
                Visible(false)
            )
        )
        pointerEntity = pointer
        Log.d(TAG, "Created persistent pointer entity")
        return pointer
    }

    override fun execute() {
        val currentTime = System.currentTimeMillis()

        // Periodically log status for debugging
        if (currentTime - lastRoomCheckTime > roomCheckInterval) {
            lastRoomCheckTime = currentTime
            val rooms = mrukFeature.rooms
            val currentRoom = mrukFeature.getCurrentRoom()
            Log.d(TAG, "MRUK status - rooms: ${rooms.size}, currentRoom: ${currentRoom != null}")
            if (currentRoom != null) {
                Log.d(TAG, "Current room anchors: ${currentRoom.anchors.size}")
            }
        }

        // Get the pointer entity
        val pointer = getOrCreatePointer()

        // Get right hand using PlayerBodyAttachmentSystem (like the MrukSample does)
        val rightHand = getRightHand()
        val rightHandPose = rightHand?.tryGetComponent<Transform>()?.transform

        if (rightHandPose == null || rightHandPose == Pose()) {
            // No valid hand pose - hide pointer
            pointer.setComponent(Visible(false))
            return
        }

        // Get pointing direction using quaternion * operator (like MrukSample)
        val rightHandDirection = (rightHandPose.q * Vector3(0f, 0f, 1f)).normalize()

        // ALWAYS raycast to update pointer position (every frame)
        val hitPoint = tryDepthRaycast(rightHandPose.t, rightHandDirection)
            ?: trySceneRaycast(rightHandPose.t, rightHandDirection)
            ?: tryFloorPlaneRaycast(rightHandPose.t, rightHandDirection)

        if (hitPoint != null) {
            // Update pointer position and make visible
            pointer.setComponent(Transform(Pose(hitPoint, Quaternion())))
            pointer.setComponent(Visible(true))
        } else {
            // No hit - hide pointer
            pointer.setComponent(Visible(false))
        }

        // Check for trigger press to confirm target
        val controller = rightHand.tryGetComponent<Controller>()
        val triggerMask = ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR or ButtonBits.ButtonA
        val isPressDetected = if (controller != null) {
            val changedMask = controller.changedButtons and triggerMask
            val pressedMask = controller.buttonState and triggerMask
            (changedMask != 0) || (pressedMask != 0 && (currentTime - lastTriggerTime > triggerCooldown))
        } else {
            false
        }

        if (isPressDetected && hitPoint != null && (currentTime - lastTriggerTime > triggerCooldown)) {
            lastTriggerTime = currentTime
            Log.d(TAG, "Trigger pressed - moving to: $hitPoint")
            onTargetFound(hitPoint)
        }
    }

    /**
     * Raycast using DEPTH mode (raycastEnvironment) - Uses live depth sensor
     * Works WITHOUT Space Setup! This is the primary method.
     */
    private fun tryDepthRaycast(origin: Vector3, direction: Vector3): Vector3? {
        try {
            val depthResult = mrukFeature.raycastEnvironment(origin, direction)
            if (depthResult.result == MRUKEnvironmentRaycastHitResult.SUCCESS) {
                return depthResult.point
            }
        } catch (e: Exception) {
            Log.e(TAG, "DEPTH raycast exception: ${e.message}")
        }
        return null
    }

    /**
     * Raycast using SCENE mode (raycastRoom) - Uses scene data
     * Requires Space Setup to be completed.
     */
    private fun trySceneRaycast(origin: Vector3, direction: Vector3): Vector3? {
        val currentRoom = mrukFeature.getCurrentRoom() ?: return null

        try {
            val hit: MRUKHit? = mrukFeature.raycastRoom(
                currentRoom.anchor.uuid,
                origin,
                direction,
                20f, // max distance in meters
                SurfaceType.PLANE_VOLUME // Hit planes and volumes (floor, walls, furniture)
            )

            if (hit != null) {
                return hit.hitPosition
            }
        } catch (e: Exception) {
            Log.e(TAG, "SCENE raycast exception: ${e.message}")
        }

        return null
    }

    /**
     * Fallback: raycast against virtual floor plane at Y=floorY
     * Used when both DEPTH and SCENE raycasting fail
     */
    private fun tryFloorPlaneRaycast(origin: Vector3, direction: Vector3): Vector3? {
        // Need to be pointing downward to hit floor (direction.y must be negative)
        if (direction.y > 0.1f) {
            return null
        }

        // If nearly horizontal, the intersection would be very far away - skip
        if (direction.y > -0.05f) {
            return null
        }

        // Calculate intersection with floor plane
        val t = (floorY - origin.y) / direction.y

        if (t > 0f && t < 10f) {
            return Vector3(
                origin.x + direction.x * t,
                floorY,
                origin.z + direction.z * t
            )
        }

        return null
    }

    /**
     * Cleanup the pointer entity
     */
    fun cleanup() {
        pointerEntity?.destroy()
        pointerEntity = null
    }
}
