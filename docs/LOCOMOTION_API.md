# Meta Spatial SDK - 3D Model Movement & Locomotion API Reference

This document outlines the key APIs and patterns for making 3D models "walk" or move from location to location in Meta Spatial SDK.

## Table of Contents

1. [Point-to-Move (Controller/Hand Raycast)](#point-to-move-controller-hand-raycast)
2. [Core Movement Components](#core-movement-components)
3. [Transform-Based Movement](#transform-based-movement)
4. [Animation Integration](#animation-integration)
5. [Custom Movement Systems](#custom-movement-systems)
6. [Smooth Locomotion Patterns](#smooth-locomotion-patterns)
7. [Look-At and Pathfinding](#look-at-and-pathfinding)

---

## Point-to-Move (Controller/Hand Raycast)

This is the most common pattern for making a pet walk to where you point. It involves:
1. Getting the controller/hand position and direction
2. Raycasting to find where the ray hits (floor, surfaces)
3. Moving the pet to that hit position

### Option 1: Simple Controller Raycast (No MRUK)

Use controller direction to calculate a point on a virtual floor plane:

```kotlin
import com.meta.spatial.core.Query
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits

class PointToMoveSystem(
    private val petEntity: Entity,
    private val onTargetSet: (Vector3) -> Unit
) : SystemBase() {

    override fun execute() {
        // Get the player's avatar body to access controllers
        val playerBody = Query.where { has(AvatarBody.id) }
            .eval()
            .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            .firstOrNull()
            ?.getComponent<AvatarBody>() ?: return

        val rightHand = playerBody.rightHand
        val controller = rightHand.tryGetComponent<Controller>() ?: return
        val handTransform = rightHand.tryGetComponent<Transform>()?.transform ?: return

        // Check if trigger is pressed (pinch for hands, trigger for controller)
        val triggerPressed = (controller.buttonState and controller.changedButtons and
            (ButtonBits.ButtonTriggerR or ButtonBits.ButtonA)) != 0

        if (triggerPressed && controller.isActive) {
            // Get pointing direction from hand/controller
            val direction = (handTransform.q * Vector3(0f, 0f, 1f)).normalize()

            // Raycast to floor plane (Y = 0)
            val floorY = 0f
            if (direction.y < -0.1f) { // Only if pointing downward
                val t = (floorY - handTransform.t.y) / direction.y
                val hitPoint = Vector3(
                    handTransform.t.x + direction.x * t,
                    floorY,
                    handTransform.t.z + direction.z * t
                )

                // Trigger pet movement to hit point
                onTargetSet(hitPoint)
            }
        }
    }
}
```

### Option 2: MRUK Raycast (Scene-Aware)

Use MRUK (Mixed Reality Utility Kit) for accurate raycasting against real-world surfaces:

```kotlin
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKHit
import com.meta.spatial.mruk.SurfaceType

class MrukPointToMoveSystem(
    private val mrukFeature: MRUKFeature,
    private val onTargetSet: (Vector3) -> Unit
) : SystemBase() {

    override fun execute() {
        val rightHand = getRightController() ?: return
        val handPose = rightHand.tryGetComponent<Transform>()?.transform ?: return
        val controller = rightHand.tryGetComponent<Controller>() ?: return

        // Check for trigger/pinch
        val triggerPressed = (controller.buttonState and controller.changedButtons and
            ButtonBits.ButtonTriggerR) != 0

        if (triggerPressed) {
            val direction = (handPose.q * Vector3(0f, 0f, 1f)).normalize()
            val currentRoom = mrukFeature.getCurrentRoom() ?: return

            // Raycast against room surfaces (floor, walls, furniture)
            val hit: MRUKHit? = mrukFeature.raycastRoom(
                currentRoom.anchor.uuid,
                handPose.t,           // Ray origin
                direction,            // Ray direction
                Float.POSITIVE_INFINITY,  // Max distance
                SurfaceType.PLANE_VOLUME  // Hit planes and volumes
            )

            if (hit != null) {
                // Move pet to the hit position
                onTargetSet(hit.hitPosition)
            }
        }
    }

    private fun getRightController(): Entity? {
        return Query.where { has(AvatarBody.id) }
            .eval()
            .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            .firstOrNull()
            ?.getComponent<AvatarBody>()
            ?.rightHand
    }
}
```

### Option 3: Using InputListener for Mesh Clicks

Register click events on a floor mesh entity:

```kotlin
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.HitInfo

// Create a floor mesh that can be clicked
val floorEntity = Entity.create(
    listOf(
        Mesh(Uri.parse("mesh://plane")),
        Plane(width = 10f, depth = 10f),
        Transform(Pose(Vector3(0f, 0f, 0f), Quaternion())),
        Material().apply {
            baseColor = Color4(0f, 0f, 0f, 0f) // Invisible
            // Or use a visible floor texture
        }
    )
)

// Add click listener to floor
val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
sceneObjectSystem?.getSceneObject(floorEntity)?.thenAccept { sceneObject ->
    sceneObject.addInputListener(object : InputListener {
        override fun onInput(
            receiver: SceneObject,
            hitInfo: HitInfo,
            sourceOfInput: Entity,
            changed: Int,
            clicked: Int,
            downTime: Long
        ) {
            // Check if trigger was just pressed
            if ((clicked and ButtonBits.ButtonTriggerR) != 0) {
                val hitPoint = hitInfo.point
                // Move pet to click location
                movePetTo(hitPoint)
            }
        }
    })
}
```

### Complete Point-to-Move Implementation for MetaPetz

Here's how to integrate this into your existing `ImmersiveActivity.kt`:

```kotlin
// Add to ImmersiveActivity.kt

private var walkJob: Job? = null
private var targetMarkerEntity: Entity? = null

/**
 * Move pet smoothly to target position
 */
fun movePetTo(target: Vector3) {
    val pet = currentPetEntity ?: return

    walkJob?.cancel()
    walkJob = activityScope.launch {
        // Stop spinning animation
        spinningJob?.cancel()

        // Get current position (remove parent for world coords)
        val transform = pet.getComponent<Transform>()
        val startPos = transform.transform.t

        // Calculate walk duration based on distance
        val distance = kotlin.math.sqrt(
            (target.x - startPos.x).pow(2) +
            (target.z - startPos.z).pow(2)
        )
        val walkSpeed = 0.5f // meters per second
        val duration = (distance / walkSpeed * 1000).toLong()

        // Calculate facing direction (only XZ plane)
        val direction = Vector3(
            target.x - startPos.x,
            0f,
            target.z - startPos.z
        ).normalize()

        val startTime = System.currentTimeMillis()

        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            // Interpolate position
            val newPos = Vector3(
                startPos.x + (target.x - startPos.x) * progress,
                startPos.y, // Keep same height
                startPos.z + (target.z - startPos.z) * progress
            )

            // Update transform with movement rotation
            val newTransform = pet.getComponent<Transform>()
            newTransform.transform.t = newPos

            // Face movement direction (combine with model flip if needed)
            if (direction.length() > 0.01f) {
                val facingRotation = Quaternion.lookRotation(direction)
                // Combine with your existing X-flip rotation
                val xFlip = Quaternion(PI.toFloat()/2, 0f, 0f, 1f)
                newTransform.transform.q = multiplyQuaternions(facingRotation, xFlip)
            }

            pet.setComponent(newTransform)

            if (progress >= 1f) break
            delay(16)
        }

        // Resume spinning/dancing when arrived
        startSpinning()
    }
}

/**
 * Show a target marker where the pet will walk to
 */
fun showTargetMarker(position: Vector3) {
    targetMarkerEntity?.destroy()
    targetMarkerEntity = Entity.create(
        listOf(
            Mesh(Uri.parse("mesh://sphere")),
            Sphere(0.05f),
            Material().apply {
                baseColor = Color4(0f, 1f, 0f, 0.5f)
                unlit = true
            },
            Transform(Pose(position, Quaternion()))
        )
    )

    // Auto-hide after 2 seconds
    activityScope.launch {
        delay(2000)
        targetMarkerEntity?.destroy()
        targetMarkerEntity = null
    }
}
```

### Registering the Point-to-Move System

```kotlin
// In onCreate() after super.onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ... existing code ...

    // Register the point-to-move system
    systemManager.registerSystem(
        PointToMoveSystem(
            petEntity = { currentPetEntity },
            onTargetSet = { target ->
                showTargetMarker(target)
                movePetTo(target)
            }
        )
    )
}
```

### Hand Tracking Alternative (Pinch Gesture)

For hand tracking without controllers, detect pinch gesture:

```kotlin
class HandPinchMoveSystem(
    private val onTargetSet: (Vector3) -> Unit
) : SystemBase() {

    private var wasPinching = false

    override fun execute() {
        val playerBody = getPlayerBody() ?: return
        val rightHand = playerBody.rightHand
        val controller = rightHand.tryGetComponent<Controller>() ?: return
        val handPose = rightHand.tryGetComponent<Transform>()?.transform ?: return

        // For hand tracking, ButtonA represents index pinch
        val isPinching = (controller.buttonState and ButtonBits.ButtonA) != 0

        // Trigger on pinch start (not held)
        if (isPinching && !wasPinching && controller.type == ControllerType.HAND) {
            val direction = (handPose.q * Vector3(0f, 0f, 1f)).normalize()

            // Calculate floor intersection
            val floorY = 0f
            if (direction.y < -0.1f) {
                val t = (floorY - handPose.t.y) / direction.y
                val hitPoint = Vector3(
                    handPose.t.x + direction.x * t,
                    floorY,
                    handPose.t.z + direction.z * t
                )
                onTargetSet(hitPoint)
            }
        }

        wasPinching = isPinching
    }

    private fun getPlayerBody(): AvatarBody? {
        return Query.where { has(AvatarBody.id) }
            .eval()
            .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            .firstOrNull()
            ?.getComponent<AvatarBody>()
    }
}
```

---

## Core Movement Components

### Transform Component

The primary component for positioning entities in 3D space.

```kotlin
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Quaternion

// Create entity at position
Entity.create(
    listOf(
        Mesh(Uri.parse("apk:///models/pet.glb")),
        Transform(Pose(Vector3(x, y, z), Quaternion(0f, 0f, 0f)))
    )
)

// Update position
val transform = entity.getComponent<Transform>()
transform.transform.t = Vector3(newX, newY, newZ)  // t = translation
entity.setComponent(transform)
```

**Key Properties:**
- `transform.t` - Position as `Vector3(x, y, z)`
- `transform.q` - Rotation as `Quaternion`

### Scale Component

Adjusts entity size without modifying the model.

```kotlin
import com.meta.spatial.toolkit.Scale

Entity.create(
    listOf(
        Mesh(Uri.parse("model.glb")),
        Scale(Vector3(0.5f, 0.5f, 0.5f))  // 50% size
    )
)
```

### TransformParent Component

Creates parent-child relationships for relative positioning.

```kotlin
import com.meta.spatial.toolkit.TransformParent

// Child moves relative to parent
val moon = Entity.create(
    listOf(
        Mesh(Uri.parse("moon.glb")),
        TransformParent(earthEntity),
        Transform(Pose(Vector3(1f, 0f, 0f)))  // 1 meter from Earth
    )
)

// Remove parent relationship
entity.setComponent(TransformParent(Entity.nullEntity()))
```

---

## Transform-Based Movement

### Direct Position Updates

Move entity instantly to a new position:

```kotlin
fun moveEntityTo(entity: Entity, targetPosition: Vector3) {
    val transform = entity.getComponent<Transform>()
    transform.transform.t = targetPosition
    entity.setComponent(transform)
}
```

### Linear Interpolation (Lerp) Movement

Smooth movement between two points:

```kotlin
fun lerp(start: Vector3, end: Vector3, t: Float): Vector3 {
    return Vector3(
        start.x + (end.x - start.x) * t,
        start.y + (end.y - start.y) * t,
        start.z + (end.z - start.z) * t
    )
}

// Usage in coroutine
suspend fun moveEntitySmoothly(entity: Entity, target: Vector3, durationMs: Long) {
    val transform = entity.getComponent<Transform>()
    val startPosition = transform.transform.t
    val startTime = System.currentTimeMillis()

    while (true) {
        val elapsed = System.currentTimeMillis() - startTime
        val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)

        transform.transform.t = lerp(startPosition, target, progress)
        entity.setComponent(transform)

        if (progress >= 1f) break
        delay(16) // ~60 FPS
    }
}
```

---

## Animation Integration

### Animated Component (glTF Animations)

Play built-in glTF animations (walk cycles, idle, etc.):

```kotlin
import com.meta.spatial.toolkit.Animated
import com.meta.spatial.toolkit.PlaybackState
import com.meta.spatial.toolkit.PlaybackType

// Basic looping animation
entity.setComponent(Animated(System.currentTimeMillis()))

// Full configuration
entity.setComponent(
    Animated(
        startTime = System.currentTimeMillis(),
        pausedTime = 0f,
        playbackState = PlaybackState.PLAYING,
        playbackType = PlaybackType.LOOP,
        track = 0  // Animation track index
    )
)
```

**Animation Parameters:**
- `startTime: Long` - Unix timestamp when animation starts
- `pausedTime: Float` - Time in seconds to display when paused
- `playbackState` - `PLAYING` or `PAUSED`
- `playbackType` - `LOOP` (repeat) or `CLAMP` (freeze at end)
- `track: Int` - Animation index (0 = first animation)

### Get Animation Track by Name

```kotlin
// Use SceneMesh to get track ID from animation name
val walkTrackId = sceneMesh.animationNameToTrack["Walk"]
entity.setComponent(Animated(System.currentTimeMillis(), track = walkTrackId ?: 0))
```

### Switch Animations Based on State

```kotlin
enum class PetState { IDLE, WALKING, RUNNING }

fun updatePetAnimation(entity: Entity, state: PetState, animationTracks: Map<String, Int>) {
    val trackId = when (state) {
        PetState.IDLE -> animationTracks["Idle"] ?: 0
        PetState.WALKING -> animationTracks["Walk"] ?: 1
        PetState.RUNNING -> animationTracks["Run"] ?: 2
    }
    entity.setComponent(Animated(System.currentTimeMillis(), track = trackId))
}
```

---

## Custom Movement Systems

### Basic Movement System

A System that runs every frame to update entity positions:

```kotlin
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Query

class MovementSystem : SystemBase() {
    private var previousTime: Long = 0L

    override fun execute() {
        if (previousTime == 0L) {
            previousTime = System.currentTimeMillis()
        }

        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - previousTime) / 1000f  // seconds
        previousTime = currentTime

        // Query all entities with MovementComponent
        val query = Query.where { has(MovementComponent.id, Transform.id) }

        for (entity in query.eval()) {
            val movement = entity.getComponent<MovementComponent>()
            val transform = entity.getComponent<Transform>()

            // Move toward target
            val direction = (movement.target - transform.transform.t).normalize()
            val step = direction * movement.speed * deltaTime

            transform.transform.t = transform.transform.t + step
            entity.setComponent(transform)
        }
    }
}
```

### Register the System

```kotlin
class ImmersiveActivity : AppSystemActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register custom component first
        componentManager.registerComponent<MovementComponent>(MovementComponent.Companion)

        // Register system
        systemManager.registerSystem(MovementSystem())
    }
}
```

### Custom Movement Component

Define a component to store movement data:

```xml
<!-- app/src/main/assets/components/MovementComponent.xml -->
<component name="MovementComponent">
    <attribute name="target" type="Vector3" default="0, 0, 0"/>
    <attribute name="speed" type="Float" default="1.0"/>
    <attribute name="isMoving" type="Boolean" default="false"/>
</component>
```

---

## Smooth Locomotion Patterns

### Coroutine-Based Walking

Complete walking implementation with animation sync:

```kotlin
import kotlinx.coroutines.*

class PetLocomotion(
    private val entity: Entity,
    private val walkAnimationTrack: Int = 0,
    private val idleAnimationTrack: Int = 1
) {
    private var walkJob: Job? = null
    private val walkSpeed = 0.5f  // meters per second

    fun walkTo(target: Vector3, scope: CoroutineScope) {
        walkJob?.cancel()

        walkJob = scope.launch {
            // Start walk animation
            entity.setComponent(Animated(
                System.currentTimeMillis(),
                track = walkAnimationTrack,
                playbackType = PlaybackType.LOOP
            ))

            val transform = entity.getComponent<Transform>()
            val startPos = transform.transform.t
            val distance = (target - startPos).length()
            val duration = (distance / walkSpeed * 1000).toLong()

            // Face the target direction
            val direction = (target - startPos).normalize()
            val targetRotation = Quaternion.lookRotation(direction)

            val startTime = System.currentTimeMillis()

            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

                // Update position
                val newTransform = entity.getComponent<Transform>()
                newTransform.transform.t = lerp(startPos, target, progress)
                newTransform.transform.q = targetRotation
                entity.setComponent(newTransform)

                if (progress >= 1f) break
                delay(16)
            }

            // Switch to idle animation
            entity.setComponent(Animated(
                System.currentTimeMillis(),
                track = idleAnimationTrack,
                playbackType = PlaybackType.LOOP
            ))
        }
    }

    fun stop() {
        walkJob?.cancel()
        entity.setComponent(Animated(
            System.currentTimeMillis(),
            track = idleAnimationTrack
        ))
    }
}
```

### Android ValueAnimator Approach

Using Android's animation framework:

```kotlin
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

fun animateWalkTo(entity: Entity, start: Vector3, target: Vector3, durationMs: Long) {
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = AccelerateDecelerateInterpolator()

        addUpdateListener { animator ->
            val progress = animator.animatedValue as Float
            val transform = entity.getComponent<Transform>()

            transform.transform.t = lerp(start, target, progress)
            entity.setComponent(transform)
        }

        start()
    }
}
```

---

## Look-At and Pathfinding

### Make Entity Face Movement Direction

```kotlin
import com.meta.spatial.core.Quaternion

fun faceDirection(entity: Entity, direction: Vector3) {
    val transform = entity.getComponent<Transform>()

    // Calculate rotation to face direction
    val targetRotation = Quaternion.lookRotation(direction)
    transform.transform.q = targetRotation

    entity.setComponent(transform)
}

// Usage during movement
fun moveWithFacing(entity: Entity, target: Vector3) {
    val transform = entity.getComponent<Transform>()
    val direction = (target - transform.transform.t).normalize()

    // Face the direction
    transform.transform.q = Quaternion.lookRotation(direction)
    entity.setComponent(transform)
}
```

### LookAt System Example

System that makes entities continuously face a target:

```kotlin
class LookAtSystem : SystemBase() {
    override fun execute() {
        val query = Query.where { has(LookAt.id, Transform.id) }

        for (entity in query.eval()) {
            val lookAt = entity.getComponent<LookAt>()
            val transform = entity.getComponent<Transform>()

            // Get target position
            val targetPose: Pose = if (lookAt.lookAtHead) {
                getScene()!!.getViewerPose()  // Look at user's head
            } else {
                lookAt.target.getComponent<Transform>().transform
            }

            // Calculate direction and rotation
            val direction = (targetPose.t - transform.transform.t)
            val newRotation = Quaternion.lookRotation(direction)

            transform.transform.q = newRotation
            entity.setComponent(transform)
        }
    }
}
```

### Simple Waypoint Path

```kotlin
class WaypointWalker(
    private val entity: Entity,
    private val waypoints: List<Vector3>,
    private val speed: Float = 0.5f
) {
    private var currentWaypointIndex = 0
    private var isWalking = false

    suspend fun startWalking() {
        isWalking = true

        while (isWalking && currentWaypointIndex < waypoints.size) {
            val target = waypoints[currentWaypointIndex]
            walkToPoint(target)
            currentWaypointIndex++
        }
    }

    private suspend fun walkToPoint(target: Vector3) {
        val transform = entity.getComponent<Transform>()
        val start = transform.transform.t
        val distance = (target - start).length()
        val duration = (distance / speed * 1000).toLong()
        val startTime = System.currentTimeMillis()

        // Face target
        val direction = (target - start).normalize()

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

            val newTransform = entity.getComponent<Transform>()
            newTransform.transform.t = lerp(start, target, progress)
            newTransform.transform.q = Quaternion.lookRotation(direction)
            entity.setComponent(newTransform)

            if (progress >= 1f) break
            delay(16)
        }
    }

    fun stop() {
        isWalking = false
    }
}
```

---

## Utility Functions

### Vector3 Extensions

```kotlin
// Add to your utils
fun Vector3.normalize(): Vector3 {
    val len = length()
    return if (len > 0f) Vector3(x / len, y / len, z / len) else this
}

fun Vector3.length(): Float {
    return kotlin.math.sqrt(x * x + y * y + z * z)
}

operator fun Vector3.minus(other: Vector3): Vector3 {
    return Vector3(x - other.x, y - other.y, z - other.z)
}

operator fun Vector3.plus(other: Vector3): Vector3 {
    return Vector3(x + other.x, y + other.y, z + other.z)
}

operator fun Vector3.times(scalar: Float): Vector3 {
    return Vector3(x * scalar, y * scalar, z * scalar)
}

fun lerp(start: Vector3, end: Vector3, t: Float): Vector3 {
    return Vector3(
        start.x + (end.x - start.x) * t,
        start.y + (end.y - start.y) * t,
        start.z + (end.z - start.z) * t
    )
}
```

### Quaternion Slerp (Smooth Rotation)

```kotlin
fun slerp(start: Quaternion, end: Quaternion, t: Float): Quaternion {
    // Spherical linear interpolation for smooth rotation
    var dot = start.x * end.x + start.y * end.y + start.z * end.z + start.w * end.w

    val endQ = if (dot < 0f) {
        dot = -dot
        Quaternion(-end.x, -end.y, -end.z, -end.w)
    } else end

    val scale0: Float
    val scale1: Float

    if (1 - dot > 0.0001f) {
        val omega = kotlin.math.acos(dot)
        val invSin = 1f / kotlin.math.sin(omega)
        scale0 = (kotlin.math.sin((1 - t) * omega) * invSin).toFloat()
        scale1 = (kotlin.math.sin(t * omega) * invSin).toFloat()
    } else {
        scale0 = 1 - t
        scale1 = t
    }

    return Quaternion(
        scale0 * start.x + scale1 * endQ.x,
        scale0 * start.y + scale1 * endQ.y,
        scale0 * start.z + scale1 * endQ.z,
        scale0 * start.w + scale1 * endQ.w
    )
}
```

---

## Quick Reference Table

| Task | Primary API | Notes |
|------|-------------|-------|
| **Point-to-Move** | `PointToMoveSystem` + raycast | Detect controller pointing |
| Get controller | `AvatarBody.rightHand` | Via Query for AvatarBody |
| Pointing direction | `handPose.q * Vector3(0,0,1)` | Forward vector from rotation |
| Detect trigger/pinch | `ButtonBits.ButtonTriggerR` or `ButtonA` | Bitwise AND with buttonState |
| MRUK raycast | `mrukFeature.raycastRoom(...)` | Hits real-world surfaces |
| Floor intersection | `t = (floorY - origin.y) / direction.y` | Simple plane raycast |
| Set position | `Transform.transform.t = Vector3(x,y,z)` | Instant movement |
| Set rotation | `Transform.transform.q = Quaternion(...)` | Face direction |
| Smooth move | `lerp()` + coroutine/ValueAnimator | Linear interpolation |
| Play animation | `Animated(startTime, track=N)` | glTF animations |
| Loop animation | `PlaybackType.LOOP` | Continuous play |
| Pause animation | `PlaybackState.PAUSED` | Freeze frame |
| Face target | `Quaternion.lookRotation(direction)` | Orient entity |
| Per-frame logic | Create custom `SystemBase` | Runs every frame |
| Parent entity | `TransformParent(parentEntity)` | Relative positioning |

---

## Sources

- [Meta Spatial SDK - 3D Objects](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-3dobjects)
- [Meta Spatial SDK - Animations](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-animations)
- [Meta Spatial SDK - Systems](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-systems)
- [Meta Spatial SDK - Built-in Components](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-builtin-components)
- [Meta Spatial SDK - Custom Systems](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-writing-new-system)
- [Meta Spatial SDK - Queries](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-queries)
- [Meta Spatial SDK - Inputs and Controllers](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-inputs-controllers)
- [Meta Spatial SDK - ISDK Input Events](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-isdk-listen-to-input-events)
