package com.cybergarden.metapetz.ecs

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
import com.meta.spatial.toolkit.Animated
import com.meta.spatial.toolkit.PlaybackState
import com.meta.spatial.toolkit.PlaybackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt
import kotlin.random.Random

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
    private var floorY: Float = 0f,
    private val walkSpeed: Float = 0.5f // meters per second
) {
    companion object {
        private const val TAG = "PetLocomotion"

        // Animation track indices (from metadog.glb)
        const val ANIM_EAT = 0        // "eat" - eating/pickup animation (use for bone fetch)
        const val ANIM_JUMP = 1       // "jump"
        const val ANIM_IDLE = 2       // "sit"
        const val ANIM_WAG = 3        // "wag"
        const val ANIM_WALKLOOP = 4   // "walkloop"
    }

    /**
     * Fetch state machine states
     */
    enum class FetchState {
        IDLE,           // Not fetching
        MOVING_TO_BONE, // Walking/pathfinding to bone
        PICKING_UP,     // Playing pickup animation (placeholder)
        RETURNING       // Bringing bone back to player
    }

    /**
     * 2D point for floor polygon vertices (X, Z coordinates).
     */
    data class Point2D(val x: Float, val z: Float)

    /**
     * Floor bounds as a 2D polygon for constraining pet movement.
     * Supports irregular room shapes (L-shaped, T-shaped, etc.)
     * Vertices should be in order (clockwise or counter-clockwise).
     */
    class FloorPolygon(val vertices: List<Point2D>) {

        /**
         * Check if a point is inside the polygon using ray casting algorithm.
         */
        fun contains(x: Float, z: Float): Boolean {
            if (vertices.size < 3) return false

            var inside = false
            var j = vertices.size - 1

            for (i in vertices.indices) {
                val vi = vertices[i]
                val vj = vertices[j]

                // Ray casting: count edge crossings
                if ((vi.z > z) != (vj.z > z) &&
                    x < (vj.x - vi.x) * (z - vi.z) / (vj.z - vi.z) + vi.x) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }

        /**
         * Clamp a position to stay inside the polygon.
         * If inside, returns the position unchanged.
         * If outside, returns the nearest point on the polygon edge.
         */
        fun clamp(position: Vector3): Vector3 {
            if (contains(position.x, position.z)) {
                return position
            }

            // Find nearest point on polygon edge
            val nearest = nearestPointOnEdge(position.x, position.z)
            return Vector3(nearest.x, position.y, nearest.z)
        }

        /**
         * Find the nearest point on the polygon edge to the given point.
         */
        private fun nearestPointOnEdge(x: Float, z: Float): Point2D {
            if (vertices.size < 2) return Point2D(x, z)

            var nearestPoint = vertices[0]
            var nearestDistSq = Float.MAX_VALUE

            for (i in vertices.indices) {
                val v1 = vertices[i]
                val v2 = vertices[(i + 1) % vertices.size]

                // Find nearest point on this edge segment
                val edgePoint = nearestPointOnSegment(x, z, v1, v2)
                val dx = edgePoint.x - x
                val dz = edgePoint.z - z
                val distSq = dx * dx + dz * dz

                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq
                    nearestPoint = edgePoint
                }
            }

            return nearestPoint
        }

        /**
         * Find the nearest point on a line segment to a given point.
         */
        private fun nearestPointOnSegment(px: Float, pz: Float, v1: Point2D, v2: Point2D): Point2D {
            val dx = v2.x - v1.x
            val dz = v2.z - v1.z
            val lengthSq = dx * dx + dz * dz

            if (lengthSq < 0.0001f) {
                // Segment is essentially a point
                return v1
            }

            // Project point onto line, clamped to segment
            val t = ((px - v1.x) * dx + (pz - v1.z) * dz) / lengthSq
            val tClamped = t.coerceIn(0f, 1f)

            return Point2D(
                v1.x + tClamped * dx,
                v1.z + tClamped * dz
            )
        }

        companion object {
            /**
             * Create a rectangular floor polygon from center and half-sizes.
             */
            fun fromRect(centerX: Float, centerZ: Float, halfSizeX: Float, halfSizeZ: Float): FloorPolygon {
                return FloorPolygon(listOf(
                    Point2D(centerX - halfSizeX, centerZ - halfSizeZ), // bottom-left
                    Point2D(centerX + halfSizeX, centerZ - halfSizeZ), // bottom-right
                    Point2D(centerX + halfSizeX, centerZ + halfSizeZ), // top-right
                    Point2D(centerX - halfSizeX, centerZ + halfSizeZ)  // top-left
                ))
            }
        }
    }

    // Current pet entity to move
    private var petEntity: Entity? = null
    private var panelEntity: Entity? = null

    // Floor polygon for constraining movement
    private var floorPolygon: FloorPolygon? = null

    // Walking state
    private var walkJob: Job? = null
    private var isWalking = false

    // Jumping state
    private var isJumping = false

    // Idle wander state
    private var idleWanderJob: Job? = null
    private var isIdleWandering = false
    private var wanderCenterX = 0f
    private var wanderCenterZ = 0f
    private var wanderRadius = 2.0f  // Default wander radius in meters

    // Fetch state
    private var fetchState = FetchState.IDLE
    private var fetchJob: Job? = null
    private var fetchTargetBone: Entity? = null
    private var isFetching = false

    // Sit state
    private var sitJob: Job? = null
    private var isSitting = false

    // Target marker
    private var targetMarkerEntity: Entity? = null

    // Reference to the pointing system for cleanup
    private var pointingSystem: PointToMoveSystem? = null

    // MRUK reference for collision raycasting
    private var mrukFeature: MRUKFeature? = null

    // Thrown bones reference for pushing
    private var thrownBones: MutableList<Entity>? = null

    // Navigation grid for avoiding furniture when wandering
    private var navGrid: NavGrid? = null

    // Room mode flag - true = room scan with pathfinding, false = outside mode with bounded area
    private var isRoomMode: Boolean = false

    // Collision settings
    private val collisionRadius = 0.15f  // Pet's collision radius in meters
    private val bonePushRadius = 0.25f   // Distance at which pet pushes bones
    private val bonePushStrength = 0.08f // How hard to push bones per frame

    // Callbacks
    var onWalkStart: (() -> Unit)? = null
    var onWalkEnd: (() -> Unit)? = null
    var onTargetSet: ((Vector3) -> Unit)? = null

    // Attention system - lambda to check if pet is paying attention
    var isAttentive: (() -> Boolean)? = null

    // Fetch callbacks
    var onFetchStart: ((Entity) -> Unit)? = null           // Called when pet starts fetching bone
    var onFetchPickup: ((Entity) -> Unit)? = null          // Called when pet picks up bone
    var onFetchReturning: ((Entity) -> Unit)? = null       // Called when pet starts returning with bone
    var onFetchComplete: ((Entity) -> Unit)? = null        // Called when pet returns with bone
    var onFetchCancelled: (() -> Unit)? = null             // Called if fetch is interrupted

    // Mouth bone callbacks - ImmersiveActivity handles actual entity creation
    var onSpawnMouthBone: ((Entity, Vector3?) -> Entity?)? = null  // Spawn bone, tween from world pos to mouth
    var onDropBone: ((Vector3) -> Unit)? = null                    // Drop bone at position as pickupable

    // Head entity provider for returning to player during fetch
    var getHeadEntity: (() -> Entity?)? = null

    // Sit callbacks
    var onSitStart: (() -> Unit)? = null              // Called when pet starts sitting
    var onSitBored: (() -> Unit)? = null              // Called when pet gets bored and stops sitting
    var onSitInterrupted: (() -> Unit)? = null        // Called when sit is interrupted by another action

    // Animation state callbacks (for accessory offset adjustments)
    var onJumpStart: (() -> Unit)? = null             // Called when jump animation starts
    var onJumpEnd: (() -> Unit)? = null               // Called when jump animation ends
    var onEatStart: (() -> Unit)? = null              // Called when eat/pickup animation starts
    var onEatEnd: (() -> Unit)? = null                // Called when eat/pickup animation ends

    // Mouth bone entity (parented to pet during fetch return)
    private var mouthBoneEntity: Entity? = null

    /**
     * Set the pet entity to control
     */
    fun setPetEntity(entity: Entity?, panel: Entity? = null) {
        petEntity = entity
        panelEntity = panel
        Log.d(TAG, "Pet entity set: ${entity != null}")
    }

    /**
     * Set MRUK feature for collision raycasting
     */
    fun setMrukFeature(mruk: MRUKFeature) {
        mrukFeature = mruk
        Log.d(TAG, "MRUK feature set for collision raycasting")
    }

    /**
     * Set thrown bones list for pushing
     */
    fun setThrownBones(bones: MutableList<Entity>) {
        thrownBones = bones
        Log.d(TAG, "Thrown bones reference set")
    }

    /**
     * Set navigation grid for pathfinding (avoiding furniture)
     */
    fun setNavGrid(grid: NavGrid?) {
        navGrid = grid
        if (grid != null) {
            Log.d(TAG, "NavGrid set: ${grid.gridWidth}x${grid.gridHeight} cells, ${grid.getWalkableCellCount()} walkable")
        } else {
            Log.d(TAG, "NavGrid cleared")
        }
    }

    /**
     * Set room mode for locomotion.
     * @param roomMode true = room scan with pathfinding, false = outside mode with bounded area
     */
    fun setRoomMode(roomMode: Boolean) {
        isRoomMode = roomMode
        Log.d(TAG, "Room mode set: $roomMode (${if (roomMode) "pathfinding enabled" else "bounded area mode"})")
    }

    /**
     * Set the floor Y height for outside mode.
     * This affects where the pet walks/stands when not using NavGrid pathfinding.
     * @param y The floor Y position in world coordinates
     */
    fun setFloorY(y: Float) {
        floorY = y
        Log.d(TAG, "Floor Y set to: $y")
    }

    /**
     * Get the current floor Y height
     */
    fun getFloorY(): Float = floorY

    /**
     * Get current room mode
     */
    fun getRoomMode(): Boolean = isRoomMode

    /**
     * Push nearby bones away from pet position
     * Skips the fetch target bone so pet can reach it
     */
    private fun pushNearbyBones(petPos: Vector3) {
        val bones = thrownBones ?: return

        for (bone in bones) {
            // Skip the bone we're trying to fetch!
            if (bone == fetchTargetBone) continue

            try {
                val boneTransform = bone.tryGetComponent<Transform>() ?: continue
                val bonePos = boneTransform.transform.t

                // Calculate distance (XZ plane only)
                val dx = bonePos.x - petPos.x
                val dz = bonePos.z - petPos.z
                val distance = sqrt(dx * dx + dz * dz)

                // Push bone if within radius
                if (distance < bonePushRadius && distance > 0.01f) {
                    // Push direction (away from pet)
                    val pushX = dx / distance
                    val pushZ = dz / distance

                    // Push strength decreases with distance
                    val pushAmount = bonePushStrength * (1f - distance / bonePushRadius)

                    // Update bone position
                    boneTransform.transform.t = Vector3(
                        bonePos.x + pushX * pushAmount,
                        bonePos.y,
                        bonePos.z + pushZ * pushAmount
                    )
                    bone.setComponent(boneTransform)
                }
            } catch (e: Exception) {
                // Bone might be destroyed
            }
        }
    }

    /**
     * Play a specific animation on the pet entity
     * @param track Animation track index (ANIM_WAG, ANIM_WALKLOOP, etc.)
     * @param loop Whether to loop the animation
     */
    private fun playAnimation(track: Int, loop: Boolean = true) {
        val pet = petEntity ?: return
        try {
            pet.setComponent(
                Animated(
                    startTime = System.currentTimeMillis(),
                    playbackState = PlaybackState.PLAYING,
                    playbackType = if (loop) PlaybackType.LOOP else PlaybackType.CLAMP,
                    track = track
                )
            )
            Log.d(TAG, "Playing animation track: $track, loop: $loop")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play animation: ${e.message}")
        }
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

    // Turn to face player job (for smooth tweening)
    private var turnToFaceJob: Job? = null

    /**
     * Turn the pet to face the player's head position with smooth tweening.
     * @param headEntity The player's head entity
     */
    fun turnToFacePlayer(headEntity: Entity?) {
        val pet = petEntity ?: return
        val headPos = headEntity?.tryGetComponent<Transform>()?.transform?.t ?: return

        // Cancel any existing turn job
        turnToFaceJob?.cancel()

        try {
            val transform = pet.getComponent<Transform>()
            val petPos = transform.transform.t

            // Calculate direction to head (XZ plane only)
            val dx = headPos.x - petPos.x
            val dz = headPos.z - petPos.z
            val distance = sqrt(dx * dx + dz * dz)

            if (distance > 0.01f) {
                val direction = Vector3(dx / distance, 0f, dz / distance)
                val targetRotation = Quaternion.lookRotationAroundY(direction)
                val startRotation = transform.transform.q

                Log.d(TAG, "Pet turning to face player at $headPos (tweening)")

                // Smooth tween over ~0.5 seconds
                turnToFaceJob = scope.launch {
                    val duration = 500L
                    val startTime = System.currentTimeMillis()

                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                        // Ease out cubic for smooth deceleration
                        val eased = 1f - (1f - t) * (1f - t) * (1f - t)

                        val currentTransform = pet.tryGetComponent<Transform>() ?: break
                        currentTransform.transform.q = startRotation.slerp(targetRotation, eased)
                        pet.setComponent(currentTransform)

                        if (t >= 1f) break
                        delay(16) // ~60 FPS
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to turn pet to face player: ${e.message}")
        }
    }

    // Continuous face player tracking
    private var facePlayerJob: Job? = null
    private var isFacingPlayer = false

    /**
     * Start continuously facing the player with smooth rotation
     */
    fun startFacingPlayer(headEntityProvider: () -> Entity?) {
        if (isFacingPlayer) return

        Log.d(TAG, "Starting continuous face player mode")
        isFacingPlayer = true

        facePlayerJob = scope.launch {
            val rotationSpeed = 0.1f  // Slerp factor per frame

            while (isActive && isFacingPlayer) {
                try {
                    // Skip rotation when pet is walking or sitting
                    // Walk has its own rotation, sitting should lock rotation
                    if (isWalking || isSitting) {
                        delay(16)
                        continue
                    }

                    val pet = petEntity ?: continue
                    val headEntity = headEntityProvider()
                    val headPos = headEntity?.tryGetComponent<Transform>()?.transform?.t

                    if (headPos != null) {
                        val transform = pet.getComponent<Transform>()
                        val petPos = transform.transform.t

                        // Calculate direction to head (XZ plane only)
                        val dx = headPos.x - petPos.x
                        val dz = headPos.z - petPos.z
                        val distance = sqrt(dx * dx + dz * dz)

                        if (distance > 0.01f) {
                            val direction = Vector3(dx / distance, 0f, dz / distance)
                            val targetRotation = Quaternion.lookRotationAroundY(direction)

                            // Smooth slerp towards target
                            val currentRotation = transform.transform.q
                            val smoothedRotation = currentRotation.slerp(targetRotation, rotationSpeed)

                            transform.transform.q = smoothedRotation
                            pet.setComponent(transform)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors, keep trying
                }

                delay(16) // ~60 FPS
            }
        }
    }

    /**
     * Stop continuously facing the player
     */
    fun stopFacingPlayer() {
        if (isFacingPlayer) {
            Log.d(TAG, "Stopping continuous face player mode")
        }
        isFacingPlayer = false
        facePlayerJob?.cancel()
        facePlayerJob = null
    }

    /**
     * Move pet to target position with collision detection
     * Uses MRUK raycast to detect obstacles and stop/slide along them
     */
    fun moveTo(target: Vector3) {
        val pet = petEntity ?: run {
            Log.w(TAG, "moveTo called but petEntity is null - select a pet first")
            return
        }

        // Interrupt sitting if active
        interruptSit()

        // Clamp target to floor polygon if set
        val clampedTarget = floorPolygon?.clamp(target) ?: target
        if (clampedTarget != target) {
            Log.d(TAG, "Target clamped to floor polygon: $target -> $clampedTarget")
        }

        // Cancel any existing walk
        walkJob?.cancel()

        walkJob = scope.launch {
            isWalking = true

            // Start walk animation
            playAnimation(ANIM_WALKLOOP, loop = true)

            onWalkStart?.invoke()

            try {
                val smoothTime = 1f / 60f
                val rotationSpeed = 0.15f
                var prevTime = System.currentTimeMillis()

                // Stuck detection
                var lastProgressCheckTime = System.currentTimeMillis()
                var lastProgressPos: Vector3? = null
                val STUCK_CHECK_INTERVAL_MS = 1000L  // Check every 1 second
                val STUCK_TIMEOUT_MS = 2000L         // Cancel if stuck for 2 seconds
                val MIN_PROGRESS_DISTANCE = 0.05f    // Must move at least 5cm per check
                var stuckStartTime: Long? = null

                while (isActive) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = (currentTime - prevTime) / 1000f
                    prevTime = currentTime

                    // Get current position
                    val transform = pet.getComponent<Transform>()
                    val currentPos = transform.transform.t

                    // Stuck detection - check progress periodically
                    if (currentTime - lastProgressCheckTime >= STUCK_CHECK_INTERVAL_MS) {
                        if (lastProgressPos != null) {
                            val progressDx = currentPos.x - lastProgressPos!!.x
                            val progressDz = currentPos.z - lastProgressPos!!.z
                            val progressDistance = sqrt(progressDx * progressDx + progressDz * progressDz)

                            if (progressDistance < MIN_PROGRESS_DISTANCE) {
                                // Not making progress
                                if (stuckStartTime == null) {
                                    stuckStartTime = currentTime
                                    Log.d(TAG, "Pet appears stuck, starting timeout")
                                } else if (currentTime - stuckStartTime!! >= STUCK_TIMEOUT_MS) {
                                    Log.d(TAG, "Pet stuck for too long, canceling walk")
                                    break
                                }
                            } else {
                                // Making progress, reset stuck timer
                                stuckStartTime = null
                            }
                        }
                        lastProgressPos = Vector3(currentPos.x, currentPos.y, currentPos.z)
                        lastProgressCheckTime = currentTime
                    }

                    // Calculate direction to target
                    val dx = clampedTarget.x - currentPos.x
                    val dz = clampedTarget.z - currentPos.z
                    val distanceToTarget = sqrt(dx * dx + dz * dz)

                    // Check if arrived (5cm threshold for close approach)
                    if (distanceToTarget < 0.05f) {
                        Log.d(TAG, "Arrived at target")
                        break
                    }

                    // Normalize direction
                    val dirX = dx / distanceToTarget
                    val dirZ = dz / distanceToTarget
                    val direction = Vector3(dirX, 0f, dirZ)

                    // Calculate step distance for this frame
                    val stepDistance = walkSpeed * deltaTime.coerceIn(0.001f, 0.05f)

                    // Check for obstacles in movement direction
                    val collision = checkCollision(currentPos, direction, stepDistance)

                    // Calculate new position - with wall sliding
                    var moveX = dirX * stepDistance
                    var moveZ = dirZ * stepDistance
                    var actualDirection = direction

                    if (!collision.canMove) {
                        // First move up to the wall
                        moveX = dirX * collision.allowedDistance
                        moveZ = dirZ * collision.allowedDistance

                        // Then try to slide along the wall
                        if (collision.slideDirection != null) {
                            val remainingDistance = stepDistance - collision.allowedDistance
                            if (remainingDistance > 0.001f) {
                                // Check if slide direction is clear
                                val slideCollision = checkCollision(
                                    Vector3(currentPos.x + moveX, currentPos.y, currentPos.z + moveZ),
                                    collision.slideDirection,
                                    remainingDistance
                                )
                                val slideStep = if (slideCollision.canMove) remainingDistance else slideCollision.allowedDistance
                                moveX += collision.slideDirection.x * slideStep
                                moveZ += collision.slideDirection.z * slideStep
                            }
                        }
                    }

                    // In OUTSIDE mode, pet should stay at floor + offset height
                    // (no elevation changes like ROOM mode)
                    val targetY = floorY + petModelYOffset
                    val newPos = Vector3(
                        currentPos.x + moveX,
                        targetY,
                        currentPos.z + moveZ
                    )

                    // Target rotation - face movement direction
                    val targetRotation = Quaternion.lookRotationAroundY(direction)

                    // Smooth rotation with slerp
                    val currentRotation = transform.transform.q
                    val smoothFactor = smoothOver(deltaTime, rotationSpeed, smoothTime)
                    val smoothedRotation = currentRotation.slerp(targetRotation, smoothFactor)

                    // Update transform
                    transform.transform.t = newPos
                    transform.transform.q = smoothedRotation
                    pet.setComponent(transform)

                    // Push bones out of the way
                    pushNearbyBones(newPos)

                    delay(16) // ~60 FPS
                }

                Log.d(TAG, "Walk complete")

            } catch (e: Exception) {
                Log.e(TAG, "Walk error: ${e.message}")
            } finally {
                isWalking = false

                // Return to idle animation (wag)
                playAnimation(ANIM_WAG, loop = true)

                onWalkEnd?.invoke()
            }
        }
    }

    // ==================== PATHFINDING WITH JUMPS ====================

    // Gravity for falling
    private val gravity = 9.8f  // m/s^2
    private var verticalVelocity = 0f

    // Vertical offset for pet model (origin at center, not feet)
    // This raises the pet so its feet are on the surface instead of its center
    // Pet scale is 0.2f, model height ~1 unit, so 75% offset = 0.15f
    private val petModelYOffset = 0.15f  // Raise by 75% of model height to put feet on ground

    /**
     * Move pet to target using A* pathfinding with jump support.
     * Uses smooth waypoint-based movement - no snapping.
     * Automatically jumps UP when needed, falls with gravity when going DOWN.
     *
     * IMPORTANT: Only uses pathfinding in ROOM MODE. In OUTSIDE MODE, falls back to direct movement.
     * In ROOM MODE, if pathfinding fails, the pet will NOT move (no fallback to prevent Y glitches).
     */
    fun moveToWithPathfinding(target: Vector3) {
        val pet = petEntity ?: run {
            Log.w(TAG, "moveToWithPathfinding called but petEntity is null")
            return
        }

        // OUTSIDE MODE: Use direct movement without pathfinding
        if (!isRoomMode) {
            Log.d(TAG, "Outside mode - using direct movement (no pathfinding)")
            moveTo(target)
            return
        }

        // ROOM MODE: Use NavGrid pathfinding - NO FALLBACK to prevent Y level glitches
        val grid = navGrid ?: run {
            Log.w(TAG, "Room mode but navGrid is null - NOT moving (no fallback to prevent Y glitch)")
            return
        }

        // Get current position
        val currentPos = pet.tryGetComponent<Transform>()?.transform?.t ?: return

        // Find path using A*
        val path = grid.findPath(currentPos.x, currentPos.z, target.x, target.z)
        if (path == null || path.size < 2) {
            Log.w(TAG, "No path found in room mode - NOT moving (no fallback to prevent Y glitch)")
            return
        }

        Log.d(TAG, "Path found with ${path.size} nodes, starting smooth pathfinding")

        // Interrupt sitting if active
        interruptSit()

        // Cancel any existing walk
        walkJob?.cancel()

        walkJob = scope.launch {
            isWalking = true
            onWalkStart?.invoke()
            playAnimation(ANIM_WALKLOOP, loop = true)

            try {
                // Convert path to world positions
                val waypoints = path.map { node ->
                    grid.gridToWorldWithHeight(node.gx, node.gz)
                }.toMutableList()

                var currentWaypointIndex = 1  // Start heading toward second waypoint (first is current pos)
                val waypointReachThreshold = 0.10f  // 10cm - when to advance to next waypoint
                val smoothTime = 1f / 60f
                val rotationSpeed = 0.15f
                var prevTime = System.currentTimeMillis()
                verticalVelocity = 0f

                while (isActive && currentWaypointIndex < waypoints.size) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = (currentTime - prevTime) / 1000f
                    prevTime = currentTime

                    val transform = pet.getComponent<Transform>()
                    val currentPos = transform.transform.t
                    val targetWaypoint = waypoints[currentWaypointIndex]

                    // Calculate horizontal direction to waypoint
                    val dx = targetWaypoint.x - currentPos.x
                    val dz = targetWaypoint.z - currentPos.z
                    val horizontalDist = sqrt(dx * dx + dz * dz)

                    // Check if we've reached this waypoint (horizontal only)
                    if (horizontalDist < waypointReachThreshold) {
                        currentWaypointIndex++
                        Log.d(TAG, "Reached waypoint, advancing to $currentWaypointIndex/${waypoints.size}")
                        continue
                    }

                    // Normalize horizontal direction
                    val dirX = dx / horizontalDist
                    val dirZ = dz / horizontalDist

                    // Calculate horizontal step
                    val stepDistance = walkSpeed * deltaTime.coerceIn(0.001f, 0.05f)
                    val newX = currentPos.x + dirX * stepDistance
                    val newZ = currentPos.z + dirZ * stepDistance

                    // Handle vertical movement (apply offset for model center origin)
                    val targetY = targetWaypoint.y + petModelYOffset
                    val heightDiff = targetY - currentPos.y
                    var newY = currentPos.y

                    if (heightDiff > 0.1f && !isJumping) {
                        // Need to jump UP - perform jump arc
                        isJumping = true
                        onJumpStart?.invoke()
                        playAnimation(ANIM_JUMP, loop = false)
                        performJumpArc(currentPos, Vector3(targetWaypoint.x, targetWaypoint.y + petModelYOffset, targetWaypoint.z))
                        isJumping = false
                        onJumpEnd?.invoke()
                        playAnimation(ANIM_WALKLOOP, loop = true)
                        // After jump, continue to next iteration
                        continue
                    } else if (heightDiff < -0.1f) {
                        // Falling DOWN - apply gravity
                        verticalVelocity += gravity * deltaTime
                        newY = currentPos.y - verticalVelocity * deltaTime

                        // Don't fall below target
                        if (newY <= targetY) {
                            newY = targetY
                            verticalVelocity = 0f
                        }
                    } else {
                        // Same level or small difference - smoothly interpolate
                        newY = currentPos.y + (targetY - currentPos.y) * 0.1f
                        verticalVelocity = 0f
                    }

                    // Smooth rotation toward movement direction
                    val direction = Vector3(dirX, 0f, dirZ)
                    val targetRotation = Quaternion.lookRotationAroundY(direction)
                    val currentRotation = transform.transform.q
                    val smoothFactor = smoothOver(deltaTime, rotationSpeed, smoothTime)
                    val smoothedRotation = currentRotation.slerp(targetRotation, smoothFactor)

                    // Update transform - NO SNAPPING
                    transform.transform.t = Vector3(newX, newY, newZ)
                    transform.transform.q = smoothedRotation
                    pet.setComponent(transform)

                    pushNearbyBones(Vector3(newX, newY, newZ))

                    delay(16)
                }

                Log.d(TAG, "Pathfinding movement complete")

            } catch (e: Exception) {
                Log.e(TAG, "Pathfinding movement error: ${e.message}")
            } finally {
                isWalking = false
                isJumping = false
                verticalVelocity = 0f
                playAnimation(ANIM_WAG, loop = true)
                onWalkEnd?.invoke()
            }
        }
    }

    /**
     * Perform a jump arc from current position to target.
     * Only used for jumping UP onto surfaces.
     */
    private suspend fun performJumpArc(from: Vector3, to: Vector3) {
        val pet = petEntity ?: return

        val duration = 450L  // milliseconds
        val heightDiff = (to.y - from.y).coerceAtLeast(0f)
        val arcHeight = 0.2f + heightDiff * 0.6f  // Arc peaks above the target

        val startTime = System.currentTimeMillis()

        while (coroutineContext.isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val t = (elapsed / duration.toFloat()).coerceIn(0f, 1f)

            // Smooth easing for more natural jump
            val easedT = t * t * (3f - 2f * t)  // Smoothstep

            // Horizontal: smooth interpolation
            val x = lerp(from.x, to.x, easedT)
            val z = lerp(from.z, to.z, easedT)

            // Vertical: parabolic arc that lands at target height
            val baseY = lerp(from.y, to.y, easedT)
            val arc = arcHeight * 4f * t * (1f - t)  // Peaks at t=0.5
            val y = baseY + arc

            // Face jump direction
            val dx = to.x - from.x
            val dz = to.z - from.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist > 0.01f) {
                val direction = Vector3(dx / dist, 0f, dz / dist)
                val targetRotation = Quaternion.lookRotationAroundY(direction)
                val transform = pet.getComponent<Transform>()
                transform.transform.t = Vector3(x, y, z)
                transform.transform.q = targetRotation
                pet.setComponent(transform)
            } else {
                val transform = pet.getComponent<Transform>()
                transform.transform.t = Vector3(x, y, z)
                pet.setComponent(transform)
            }

            pushNearbyBones(Vector3(x, y, z))

            if (t >= 1f) break
            delay(16)
        }

        Log.d(TAG, "Jump arc complete: ${from.y} -> ${to.y}")
    }

    /**
     * Linear interpolation helper.
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    // ==================== END PATHFINDING ====================

    /**
     * Collision result containing movement info and slide direction
     */
    data class CollisionResult(
        val canMove: Boolean,
        val allowedDistance: Float,
        val slideDirection: Vector3?  // Direction to slide along wall (null if no collision)
    )

    /**
     * Check for collision in movement direction using MRUK raycast
     * @return CollisionResult with movement info and slide direction for wall sliding
     * NOTE: Only active in ROOM MODE - outside mode skips MRUK collision entirely
     */
    private fun checkCollision(position: Vector3, direction: Vector3, desiredDistance: Float): CollisionResult {
        // OUTSIDE MODE: Skip MRUK collision detection entirely
        if (!isRoomMode) {
            return CollisionResult(true, desiredDistance, null)
        }

        val mruk = mrukFeature ?: return CollisionResult(true, desiredDistance, null)
        val currentRoom = mruk.getCurrentRoom() ?: return CollisionResult(true, desiredDistance, null)

        try {
            // Raycast from pet position in movement direction
            val rayOrigin = Vector3(position.x, floorY + 0.1f, position.z)
            val rayDistance = desiredDistance + collisionRadius

            val hit = mruk.raycastRoom(
                currentRoom.anchor.uuid,
                rayOrigin,
                direction,
                rayDistance,
                SurfaceType.PLANE_VOLUME
            )

            if (hit != null) {
                val hitDx = hit.hitPosition.x - position.x
                val hitDz = hit.hitPosition.z - position.z
                val hitDistance = sqrt(hitDx * hitDx + hitDz * hitDz) - collisionRadius

                if (hitDistance <= desiredDistance) {
                    val allowedDistance = (hitDistance - 0.02f).coerceAtLeast(0f)

                    // Calculate slide direction (perpendicular to wall normal)
                    // Wall normal points away from wall, we want to slide parallel to wall
                    val wallNormal = hit.hitNormal
                    // Project movement direction onto wall plane: slide = dir - (dir·normal)*normal
                    val dotProduct = direction.x * wallNormal.x + direction.z * wallNormal.z
                    val slideX = direction.x - dotProduct * wallNormal.x
                    val slideZ = direction.z - dotProduct * wallNormal.z
                    val slideMag = sqrt(slideX * slideX + slideZ * slideZ)

                    val slideDir = if (slideMag > 0.01f) {
                        Vector3(slideX / slideMag, 0f, slideZ / slideMag)
                    } else null

                    return CollisionResult(false, allowedDistance, slideDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Raycast error: ${e.message}")
        }

        return CollisionResult(true, desiredDistance, null)
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
     * - DEPTH mode raycasting (hits furniture surfaces)
     * - Snaps to nearest walkable grid cell within 50cm (room mode only)
     * - Trigger press to confirm target and move pet
     * - Attention-gated: pointer only works when pet has attention
     * - Mode-aware: Uses pathfinding in room mode, direct movement in outside mode
     *
     * @param mrukFeature Required - MRUK feature for scene-aware raycasting
     */
    fun createPointingSystem(mrukFeature: MRUKFeature): PointToMoveSystem {
        val system = PointToMoveSystem(
            mrukFeature = mrukFeature,
            getFloorY = { floorY },  // Dynamic getter - always gets current floorY
            getNavGrid = { navGrid },  // Dynamic getter - always gets current navGrid
            isRoomMode = { isRoomMode },  // Dynamic getter - always gets current mode
            isAttentive = { isAttentive?.invoke() ?: false },
            onTargetFound = { hitPoint ->
                onTargetSet?.invoke(hitPoint)
                showTargetMarker(hitPoint)
                moveToWithPathfinding(hitPoint)  // This internally checks isRoomMode
            }
        )
        pointingSystem = system
        return system
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopFetch()
        stopSit()
        stopIdleWander()
        stopWalking()
        stopFacingPlayer()
        targetMarkerEntity?.destroy()
        targetMarkerEntity = null
        pointingSystem?.cleanup()
        pointingSystem = null
        petEntity = null
        panelEntity = null
    }

    /**
     * Set the wander area for idle mode
     * @param centerX X coordinate of wander area center
     * @param centerZ Z coordinate of wander area center
     * @param radius Maximum distance from center the pet can wander
     */
    fun setWanderArea(centerX: Float, centerZ: Float, radius: Float) {
        this.wanderCenterX = centerX
        this.wanderCenterZ = centerZ
        this.wanderRadius = radius
        Log.d(TAG, "Wander area set: center=($centerX, $centerZ), radius=$radius")
    }

    /**
     * Set the floor polygon for constraining pet movement.
     * All movement targets will be clamped to stay within this polygon.
     * @param polygon The floor polygon or null to disable bounds
     */
    fun setFloorPolygon(polygon: FloorPolygon?) {
        this.floorPolygon = polygon
        if (polygon != null) {
            Log.d(TAG, "Floor polygon set with ${polygon.vertices.size} vertices")
        } else {
            Log.d(TAG, "Floor polygon cleared")
        }
    }

    /**
     * Convenience method to set a rectangular floor polygon from center and half-size.
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param halfSizeX Half-width in X direction
     * @param halfSizeZ Half-width in Z direction
     */
    fun setFloorPolygonFromRect(centerX: Float, centerZ: Float, halfSizeX: Float, halfSizeZ: Float) {
        setFloorPolygon(FloorPolygon.fromRect(centerX, centerZ, halfSizeX, halfSizeZ))
    }

    /**
     * Get current floor polygon (for external use, e.g., debugging)
     */
    fun getFloorPolygon(): FloorPolygon? = floorPolygon

    /**
     * Start idle wander mode - pet will randomly pick points and walk to them
     * The pet will wait a random time between walks (2-6 seconds)
     * Uses NavGrid when available to pick walkable points that avoid furniture
     */
    fun startIdleWander() {
        if (petEntity == null) {
            Log.w(TAG, "Cannot start idle wander - no pet entity set")
            return
        }

        if (isIdleWandering) {
            Log.d(TAG, "Idle wander already running")
            return
        }

        Log.d(TAG, "Starting idle wander mode (isRoomMode=$isRoomMode, navGrid=${navGrid != null})")
        isIdleWandering = true

        idleWanderJob = scope.launch {
            while (isActive && isIdleWandering) {
                // Wait random time before next wander (2-6 seconds)
                val waitTime = Random.nextLong(2000, 6000)
                Log.d(TAG, "Idle wander: waiting ${waitTime}ms before next move")
                delay(waitTime)

                // Skip if no longer wandering or walking
                if (!isIdleWandering) break
                if (isWalking || isJumping) {
                    Log.d(TAG, "Idle wander: pet is walking/jumping, waiting...")
                    continue
                }

                // Pick a random walkable point based on mode
                val target: Vector3? = if (isRoomMode && navGrid != null) {
                    // ROOM MODE: Use NavGrid to pick random walkable points (floor OR elevated surface)
                    val grid = navGrid!!

                    // 30% chance to target an elevated surface (furniture)
                    val tryElevated = Random.nextFloat() < 0.3f

                    if (tryElevated) {
                        val elevatedCells = grid.findAllWalkableElevatedCells()
                        if (elevatedCells.isNotEmpty()) {
                            val cell = elevatedCells[Random.nextInt(elevatedCells.size)]
                            val pos = grid.gridToWorldWithHeight(cell.gx, cell.gz)
                            Log.d(TAG, "Idle wander (ROOM): targeting elevated surface at $pos (height=${cell.height})")
                            pos
                        } else {
                            // No elevated surfaces, fall back to floor
                            grid.getRandomWalkablePoint()?.also {
                                Log.d(TAG, "Idle wander (ROOM): no elevated surfaces, floor point $it")
                            }
                        }
                    } else {
                        // Target floor
                        grid.getRandomWalkablePoint()?.also {
                            Log.d(TAG, "Idle wander: floor point $it")
                        }
                    }
                } else {
                    // OUTSIDE MODE: Use circular wander area (no NavGrid)
                    val randomAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val randomDistance = Random.nextFloat() * wanderRadius
                    val targetX = wanderCenterX + randomDistance * kotlin.math.cos(randomAngle)
                    val targetZ = wanderCenterZ + randomDistance * kotlin.math.sin(randomAngle)
                    val targetY = floorY
                    Vector3(targetX, targetY, targetZ).also {
                        Log.d(TAG, "Idle wander (OUTSIDE): circular area point $it")
                    }
                }

                // Fallback in case target is null (shouldn't happen)
                val finalTarget = target ?: run {
                    val randomAngle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val randomDistance = Random.nextFloat() * wanderRadius
                    val targetX = wanderCenterX + randomDistance * kotlin.math.cos(randomAngle)
                    val targetZ = wanderCenterZ + randomDistance * kotlin.math.sin(randomAngle)
                    val targetY = floorY
                    Vector3(targetX, targetY, targetZ).also {
                        Log.d(TAG, "Idle wander: fallback circular point $it")
                    }
                }

                Log.d(TAG, "Idle wander: moving to $finalTarget (isRoomMode=$isRoomMode)")

                // Move to the random point - method chosen based on mode
                // In room mode, uses A* pathfinding (no fallback if path fails)
                // In outside mode, uses direct movement
                moveToWithPathfinding(finalTarget)

                // Wait for walk/jump to complete
                while ((isWalking || isJumping) && isActive && isIdleWandering) {
                    delay(100)
                }
            }

            Log.d(TAG, "Idle wander loop ended")
        }
    }

    /**
     * Stop idle wander mode
     */
    fun stopIdleWander() {
        if (isIdleWandering) {
            Log.d(TAG, "Stopping idle wander mode")
        }
        isIdleWandering = false
        idleWanderJob?.cancel()
        idleWanderJob = null
    }

    /**
     * Check if idle wander is active
     */
    fun isIdleWanderActive(): Boolean = isIdleWandering

    /**
     * Temporarily pause idle wander (e.g., when user interacts with pet)
     * Call resumeIdleWander() to continue
     */
    fun pauseIdleWander() {
        if (isIdleWandering) {
            Log.d(TAG, "Pausing idle wander")
            stopIdleWander()
        }
    }

    /**
     * Resume idle wander after pause
     */
    fun resumeIdleWander() {
        if (!isIdleWandering && petEntity != null) {
            Log.d(TAG, "Resuming idle wander")
            startIdleWander()
        }
    }

    // ==================== BONE FETCHING ====================

    /**
     * Start fetching a bone.
     *
     * Two modes:
     * - OUTSIDE MODE (isRoomMode=false): Direct movement to bone, then direct return to player
     * - ROOM MODE (isRoomMode=true): Pathfinding to bone, then pathfinding return to player
     *
     * @param boneEntity The bone entity to fetch
     * @param onBoneDestroy Callback to destroy the bone entity when picked up (called from ImmersiveActivity)
     */
    fun startFetch(boneEntity: Entity, onBoneDestroy: (Entity) -> Unit) {
        val pet = petEntity ?: run {
            Log.w(TAG, "startFetch called but petEntity is null")
            return
        }

        if (isFetching) {
            Log.w(TAG, "Already fetching, ignoring new fetch request")
            return
        }

        Log.d(TAG, "Starting fetch sequence (isRoomMode=$isRoomMode)")

        // Stop any current activity
        interruptSit()
        stopWalking()
        stopIdleWander()
        stopFacingPlayer()

        isFetching = true
        fetchState = FetchState.MOVING_TO_BONE
        fetchTargetBone = boneEntity

        onFetchStart?.invoke(boneEntity)

        fetchJob = scope.launch {
            try {
                // === PHASE 1: Move to bone ===
                var bonePos = boneEntity.tryGetComponent<Transform>()?.transform?.t
                if (bonePos == null) {
                    Log.w(TAG, "Bone has no transform, cancelling fetch")
                    cancelFetchInternal()
                    return@launch
                }

                Log.d(TAG, "Phase 1: Moving to bone at $bonePos")

                // Move to bone - method depends on mode
                if (isRoomMode) {
                    // ROOM MODE: Use pathfinding
                    // Find nearest walkable cell that also considers HEIGHT
                    // This prevents the dog from jumping to elevated surfaces when the bone is on the floor
                    val grid = navGrid
                    val targetPos = if (grid != null) {
                        grid.findNearestWalkableCell(bonePos.x, bonePos.y, bonePos.z, maxDistance = 1.0f)
                            ?: bonePos  // Fallback to bone pos if no walkable cell found
                    } else {
                        bonePos
                    }
                    Log.d(TAG, "Fetch target adjusted from $bonePos to $targetPos (height-aware)")
                    moveToWithPathfinding(targetPos)
                } else {
                    // OUTSIDE MODE: Direct movement
                    moveTo(bonePos)
                }

                // Wait for walk coroutine to start (it sets isWalking = true internally)
                delay(100)

                // Wait for movement to complete
                while (isWalking && isActive) {
                    delay(50)
                }

                if (!isActive || !isFetching) {
                    Log.d(TAG, "Fetch cancelled during move to bone")
                    cancelFetchInternal()
                    return@launch
                }

                // Verify pet is actually close to bone before picking up
                val PICKUP_DISTANCE = 0.35f  // Must be within 35cm to pick up (accounts for pathfinding variance)
                val petPos = pet.tryGetComponent<Transform>()?.transform?.t
                val currentBonePos = boneEntity.tryGetComponent<Transform>()?.transform?.t

                if (petPos != null && currentBonePos != null) {
                    val dx = currentBonePos.x - petPos.x
                    val dz = currentBonePos.z - petPos.z
                    val distToBone = sqrt(dx * dx + dz * dz)

                    Log.d(TAG, "Distance to bone: $distToBone (need < $PICKUP_DISTANCE)")

                    if (distToBone > PICKUP_DISTANCE) {
                        Log.w(TAG, "Too far from bone ($distToBone), cancelling fetch")
                        cancelFetchInternal()
                        return@launch
                    }
                }

                // Get tween start position (at bone location, which is now close to pet)
                val tweenStartPos = currentBonePos?.let { Vector3(it.x, it.y, it.z) }
                Log.d(TAG, "Bone is close enough, tween will start from: $tweenStartPos")

                // === PHASE 2: Pick up bone ===
                fetchState = FetchState.PICKING_UP
                Log.d(TAG, "Phase 2: Picking up bone")

                // FIRST: Destroy the physics bone immediately (remove from physics world)
                onFetchPickup?.invoke(boneEntity)
                onBoneDestroy(boneEntity)
                Log.d(TAG, "Physics bone destroyed")

                // THEN: Spawn mouth bone that tweens from pet's feet to mouth
                mouthBoneEntity = onSpawnMouthBone?.invoke(pet, tweenStartPos)
                if (mouthBoneEntity != null) {
                    Log.d(TAG, "Mouth bone spawned, will tween from $tweenStartPos to mouth")
                }

                // Play eat animation for bone pickup
                onEatStart?.invoke()
                playAnimation(ANIM_EAT, loop = false)

                // Wait for eat animation and tween to complete
                delay(1000)
                onEatEnd?.invoke()

                // === PHASE 3: Return to player ===
                fetchState = FetchState.RETURNING
                Log.d(TAG, "Phase 3: Returning to player")

                // Notify that pet is returning with bone
                val targetBone = fetchTargetBone
                if (targetBone != null) {
                    onFetchReturning?.invoke(targetBone)
                }

                // Start walk animation (with bone in mouth)
                playAnimation(ANIM_WALKLOOP, loop = true)

                // Get player head position
                val headEntity = getHeadEntity?.invoke()
                val headPos = headEntity?.tryGetComponent<Transform>()?.transform?.t

                if (headPos == null) {
                    Log.w(TAG, "Cannot find player head position, ending fetch at current location")
                    completeFetch()
                    return@launch
                }

                // Calculate return position (in front of player, not exactly at head)
                val petPosNow = pet.tryGetComponent<Transform>()?.transform?.t ?: Vector3(0f, 0f, 0f)
                val toPlayer = Vector3(headPos.x - petPosNow.x, 0f, headPos.z - petPosNow.z)
                val distToPlayer = sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z)

                // Stop 0.5m in front of player
                val returnDistance = (distToPlayer - 0.5f).coerceAtLeast(0.1f)
                val returnPos = if (distToPlayer > 0.01f) {
                    Vector3(
                        petPosNow.x + (toPlayer.x / distToPlayer) * returnDistance,
                        floorY,
                        petPosNow.z + (toPlayer.z / distToPlayer) * returnDistance
                    )
                } else {
                    headPos
                }

                Log.d(TAG, "Returning to position near player: $returnPos")

                // Move back to player - method depends on mode
                if (isRoomMode) {
                    // ROOM MODE: Use pathfinding
                    moveToWithPathfinding(returnPos)
                } else {
                    // OUTSIDE MODE: Direct movement
                    moveTo(returnPos)
                }

                // Wait for walk coroutine to start
                delay(100)

                // Wait for return movement to complete
                while (isWalking && isActive) {
                    delay(50)
                }

                if (!isActive || !isFetching) {
                    Log.d(TAG, "Fetch cancelled during return")
                    cancelFetchInternal()
                    return@launch
                }

                // === PHASE 4: Drop bone and complete fetch ===
                completeFetch()

            } catch (e: Exception) {
                Log.e(TAG, "Fetch error: ${e.message}")
                cancelFetchInternal()
            }
        }
    }

    /**
     * Complete the fetch sequence successfully
     */
    private fun completeFetch() {
        Log.d(TAG, "Fetch complete!")

        val bone = fetchTargetBone
        val pet = petEntity

        // Get drop position (in front of pet's current position, at pet's feet level)
        val dropPos = if (pet != null) {
            val petTransform = pet.tryGetComponent<Transform>()?.transform
            if (petTransform != null) {
                val petPos = petTransform.t
                val forward = petTransform.q * Vector3(0f, 0f, 0.15f) // 15cm in front
                // Use pet's Y minus offset to get ground level where pet is standing
                val groundY = petPos.y - petModelYOffset
                Vector3(petPos.x + forward.x, groundY, petPos.z + forward.z)
            } else {
                null
            }
        } else {
            null
        }

        // Destroy mouth bone and drop as pickupable
        if (mouthBoneEntity != null) {
            mouthBoneEntity?.destroy()
            mouthBoneEntity = null
            Log.d(TAG, "Mouth bone destroyed")

            // Spawn pickupable bone at drop position
            if (dropPos != null) {
                onDropBone?.invoke(dropPos)
                Log.d(TAG, "Dropped pickupable bone at $dropPos")
            }
        }

        fetchState = FetchState.IDLE
        isFetching = false
        fetchTargetBone = null
        fetchJob = null

        // Return to wag animation
        playAnimation(ANIM_WAG, loop = true)

        // Notify completion
        if (bone != null) {
            onFetchComplete?.invoke(bone)
        }
    }

    /**
     * Cancel fetch internally (called on error or interruption)
     */
    private fun cancelFetchInternal() {
        Log.d(TAG, "Fetch cancelled internally")

        // Clean up mouth bone if it exists
        if (mouthBoneEntity != null) {
            // Get drop position before destroying (at pet's feet level)
            val pet = petEntity
            val dropPos = if (pet != null) {
                val petPos = pet.tryGetComponent<Transform>()?.transform?.t
                if (petPos != null) {
                    // Use pet's Y minus offset to get ground level where pet is standing
                    val groundY = petPos.y - petModelYOffset
                    Vector3(petPos.x, groundY, petPos.z)
                } else null
            } else null

            mouthBoneEntity?.destroy()
            mouthBoneEntity = null

            // Drop bone at current position
            if (dropPos != null) {
                onDropBone?.invoke(dropPos)
                Log.d(TAG, "Dropped bone due to fetch cancellation at $dropPos")
            }
        }

        fetchState = FetchState.IDLE
        isFetching = false
        fetchTargetBone = null
        fetchJob = null

        playAnimation(ANIM_WAG, loop = true)

        onFetchCancelled?.invoke()
    }

    /**
     * Cancel any active fetch
     */
    fun cancelFetch() {
        if (isFetching) {
            Log.d(TAG, "Cancelling fetch")
            fetchJob?.cancel()
            cancelFetchInternal()
        }
    }

    /**
     * Check if pet is currently fetching
     */
    fun isFetching(): Boolean = isFetching

    /**
     * Get current fetch state
     */
    fun getFetchState(): FetchState = fetchState

    /**
     * Stop all fetch-related activity (called during cleanup)
     */
    private fun stopFetch() {
        fetchJob?.cancel()
        fetchJob = null
        fetchState = FetchState.IDLE
        isFetching = false
        fetchTargetBone = null
        // Clean up mouth bone
        mouthBoneEntity?.destroy()
        mouthBoneEntity = null
    }

    // ==================== END BONE FETCHING ====================

    // ==================== SITTING ====================

    /**
     * Start the sit command.
     * Pet will play sit animation and face the player.
     * After 2-5 seconds (random), the pet gets bored and stops sitting.
     * Sitting can be interrupted by walking, fetching, or other actions.
     *
     * @param headEntityProvider Function to get the player's head entity for facing
     */
    fun startSit(headEntityProvider: () -> Entity?) {
        val pet = petEntity ?: run {
            Log.w(TAG, "startSit called but petEntity is null")
            return
        }

        // Don't start sit if already sitting
        if (isSitting) {
            Log.d(TAG, "Already sitting - extending sit duration")
            extendSit()
            return
        }

        // Don't start sit during fetch
        if (isFetching) {
            Log.d(TAG, "Cannot sit while fetching")
            return
        }

        Log.d(TAG, "Starting sit")

        // Stop any current activity
        stopWalking()
        stopIdleWander()

        isSitting = true

        // Play sit animation
        playAnimation(ANIM_IDLE, loop = false)  // Hold at end frame, no twerking

        // Notify callback
        onSitStart?.invoke()


        // Start boredom timer (2-5 seconds)
        startSitBoredomTimer()
    }

    /**
     * Extend the sit duration by resetting the boredom timer.
     * Called when user claps while pet is already sitting.
     */
    fun extendSit() {
        if (!isSitting) return

        Log.d(TAG, "Extending sit duration")
        startSitBoredomTimer()
    }

    /**
     * Start or restart the boredom timer for sitting.
     * Pet will stop sitting after 5 seconds.
     */
    private fun startSitBoredomTimer() {
        sitJob?.cancel()
        sitJob = scope.launch {
            // Fixed duration of 5 seconds
            val boredomTime = 5000L
            Log.d(TAG, "Sit boredom timer started: ${boredomTime}ms")

            delay(boredomTime)

            if (isActive && isSitting) {
                Log.d(TAG, "Pet got bored of sitting")
                stopSitInternal(bored = true)
            }
        }
    }

    /**
     * Stop sitting (internal).
     * @param bored True if stopped due to boredom, false if interrupted
     */
    private fun stopSitInternal(bored: Boolean) {
        if (!isSitting) return

        Log.d(TAG, "Stopping sit (bored=$bored)")

        sitJob?.cancel()
        sitJob = null
        isSitting = false

        // Stop facing player
        stopFacingPlayer()

        // Return to wag animation
        playAnimation(ANIM_WAG, loop = true)

        // Notify appropriate callback
        if (bored) {
            onSitBored?.invoke()
        } else {
            onSitInterrupted?.invoke()
        }
    }

    /**
     * Interrupt sitting due to another action (walk, fetch, etc.)
     * Called externally when starting other activities.
     */
    fun interruptSit() {
        if (isSitting) {
            Log.d(TAG, "Sit interrupted by another action")
            stopSitInternal(bored = false)
        }
    }

    /**
     * Check if pet is currently sitting
     */
    fun isSitting(): Boolean = isSitting

    /**
     * Stop sitting completely (called during cleanup)
     */
    private fun stopSit() {
        sitJob?.cancel()
        sitJob = null
        isSitting = false
    }

    // ==================== END SITTING ====================

    /**
     * Frame-rate independent smoothing function (from AnimationsSample DroneSystem)
     * Returns a smooth interpolation factor based on delta time
     */
    private fun smoothOver(dt: Float, convergenceFraction: Float, smoothTime: Float): Float {
        return 1f - Math.pow(1.0 - convergenceFraction.toDouble(), (dt / smoothTime).toDouble()).toFloat()
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
 * - Mode-aware: Only snaps to NavGrid in room mode, uses direct raycasting in outside mode
 */
class PointToMoveSystem(
    private val mrukFeature: MRUKFeature,
    private val getFloorY: () -> Float = { 0f },  // Dynamic getter for floor Y
    private val getNavGrid: () -> NavGrid? = { null },  // Dynamic getter for navGrid
    private val isRoomMode: () -> Boolean = { false },   // Dynamic getter for room mode
    private val isAttentive: () -> Boolean = { true },
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

        // Get the pointer entity
        val pointer = getOrCreatePointer()

        // Check if pet has attention - if not, hide pointer and skip
        if (!isAttentive()) {
            pointer.setComponent(Visible(false))
            return
        }

        // Periodically log status for debugging
        if (currentTime - lastRoomCheckTime > roomCheckInterval) {
            lastRoomCheckTime = currentTime
            val rooms = mrukFeature.rooms
            val currentRoom = mrukFeature.getCurrentRoom()
            Log.d(TAG, "MRUK status - rooms: ${rooms.size}, currentRoom: ${currentRoom != null}, attentive: true")
            if (currentRoom != null) {
                Log.d(TAG, "Current room anchors: ${currentRoom.anchors.size}")
            }
        }

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

        // Raycast to find hit point based on mode
        // ROOM MODE: Try depth first (hits furniture), then scene, then floor
        // OUTSIDE MODE: Only use floor plane (no MRUK raycasting at all)
        val inRoomMode = isRoomMode()
        val rawHitPoint = if (inRoomMode) {
            tryDepthRaycast(rightHandPose.t, rightHandDirection)
                ?: trySceneRaycast(rightHandPose.t, rightHandDirection)
                ?: tryFloorPlaneRaycast(rightHandPose.t, rightHandDirection)
        } else {
            // OUTSIDE MODE: Only use floor plane raycasting - completely ignore MRUK
            tryFloorPlaneRaycast(rightHandPose.t, rightHandDirection)
        }

        // Snap to nearest walkable grid cell within 50cm ONLY in room mode with NavGrid
        val navGrid = getNavGrid()
        val hitPoint = if (inRoomMode && rawHitPoint != null && navGrid != null) {
            navGrid.findNearestWalkableCell(rawHitPoint.x, rawHitPoint.y, rawHitPoint.z, 0.5f)
        } else {
            rawHitPoint
        }

        if (hitPoint != null) {
            // Update pointer position and make visible
            pointer.setComponent(Transform(Pose(hitPoint, Quaternion())))
            pointer.setComponent(Visible(true))
        } else {
            // No hit or no walkable cell nearby - hide pointer
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
            Log.d(TAG, "Trigger pressed - moving to: $hitPoint (snapped to grid)")
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

        // Get current floor Y (dynamic for outside mode floor offset)
        val floorY = getFloorY()

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

/**
 * NavGridEditSystem - Allows manual editing of NavGrid cells via pointing.
 *
 * When enabled:
 * - Shows an orange pointer at the targeted NavGrid cell
 * - Pinch/trigger toggles the cell between walkable/blocked
 * - Updates debug visualization in real-time
 */
class NavGridEditSystem(
    private val mrukFeature: MRUKFeature,
    private val getNavGrid: () -> NavGrid?,
    private val floorY: Float = 0f
) : SystemBase() {

    companion object {
        private const val TAG = "NavGridEditSystem"
    }

    var isEnabled = false
        set(value) {
            field = value
            Log.d(TAG, "Edit mode ${if (value) "enabled" else "disabled"}")
            if (!value) {
                pointerEntity?.setComponent(Visible(false))
            }
        }

    private var pointerEntity: Entity? = null
    private var lastTriggerTime = 0L
    private val triggerCooldown = 200L  // Fast response for editing

    private fun getRightHand(): Entity? {
        return systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.rightHand
    }

    private fun getOrCreatePointer(): Entity {
        pointerEntity?.let { return it }

        // Create an orange sphere as the edit pointer
        val pointer = Entity.create(
            listOf(
                Mesh(android.net.Uri.parse("mesh://sphere")),
                Sphere(0.05f),  // Slightly larger for visibility
                Material().apply {
                    baseColor = Color4(1f, 0.5f, 0f, 0.9f)  // Orange
                    unlit = true
                },
                Transform(Pose(Vector3(0f, -100f, 0f), Quaternion())),
                Scale(Vector3(1f, 0.5f, 1f)),  // Disc shape
                Visible(false)
            )
        )
        pointerEntity = pointer
        Log.d(TAG, "Created edit pointer entity")
        return pointer
    }

    override fun execute() {
        if (!isEnabled) return

        val currentTime = System.currentTimeMillis()
        val pointer = getOrCreatePointer()
        val navGrid = getNavGrid()

        if (navGrid == null) {
            pointer.setComponent(Visible(false))
            return
        }

        // Get right hand pose
        val rightHand = getRightHand()
        val rightHandPose = rightHand?.tryGetComponent<Transform>()?.transform

        if (rightHandPose == null || rightHandPose == Pose()) {
            pointer.setComponent(Visible(false))
            return
        }

        // Get pointing direction
        val direction = (rightHandPose.q * Vector3(0f, 0f, 1f)).normalize()

        // Raycast to floor plane
        val hitPoint = raycastFloorPlane(rightHandPose.t, direction, navGrid.floorY)

        if (hitPoint != null) {
            // Check if this is a valid grid cell
            val gridCell = navGrid.getGridCellAt(hitPoint.x, hitPoint.z)

            if (gridCell != null) {
                // Show pointer at cell center
                val cellCenter = navGrid.gridToWorld(gridCell.first, gridCell.second)
                pointer.setComponent(Transform(Pose(
                    Vector3(cellCenter.x, cellCenter.y + 0.02f, cellCenter.z),
                    Quaternion()
                )))
                pointer.setComponent(Visible(true))

                // Update pointer color based on current cell state
                val isWalkable = navGrid.isWalkable(hitPoint.x, hitPoint.z)
                pointer.setComponent(Material().apply {
                    baseColor = if (isWalkable) {
                        Color4(1f, 0.3f, 0f, 0.9f)  // Orange-red (will block)
                    } else {
                        Color4(0f, 1f, 0.3f, 0.9f)  // Green (will unblock)
                    }
                    unlit = true
                })

                // Check for trigger/pinch to toggle cell
                val controller = rightHand.tryGetComponent<Controller>()
                val triggerMask = ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR or ButtonBits.ButtonA
                val isPinch = if (controller != null) {
                    (controller.buttonState and triggerMask) != 0
                } else {
                    false
                }

                if (isPinch && (currentTime - lastTriggerTime > triggerCooldown)) {
                    lastTriggerTime = currentTime

                    // Toggle the cell
                    if (isWalkable) {
                        navGrid.blockCellAtWorldPos(hitPoint.x, hitPoint.z)
                        Log.d(TAG, "Blocked cell at (${gridCell.first}, ${gridCell.second})")
                    } else {
                        navGrid.unblockCellAtWorldPos(hitPoint.x, hitPoint.z)
                        Log.d(TAG, "Unblocked cell at (${gridCell.first}, ${gridCell.second})")
                    }
                }
            } else {
                pointer.setComponent(Visible(false))
            }
        } else {
            pointer.setComponent(Visible(false))
        }
    }

    /**
     * Simple floor plane raycast
     */
    private fun raycastFloorPlane(origin: Vector3, direction: Vector3, planeY: Float): Vector3? {
        // If pointing roughly horizontal or up, no floor hit
        if (direction.y >= -0.01f) return null

        // Calculate intersection with Y = planeY
        val t = (planeY - origin.y) / direction.y
        if (t < 0 || t > 20f) return null  // Max 20m distance

        return Vector3(
            origin.x + direction.x * t,
            planeY,
            origin.z + direction.z * t
        )
    }

    fun cleanup() {
        pointerEntity?.destroy()
        pointerEntity = null
    }
}
