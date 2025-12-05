# Pet Collision & Jump System Implementation Plan

## Problem
- Bones react to furniture colliders, but dog phases through everything
- Dog needs physical presence to push bones and avoid/jump over furniture

## Architecture Overview

### Current State
- Pet movement: Transform interpolation in `PetLocomotion.moveTo()`
- No physics on pet entity
- Floor polygon constraints (2D boundary only)
- Bones: `Physics(state=DYNAMIC)` - react to walls
- Furniture: Visual meshes only (no physics colliders for bones to hit... wait, bones DO react to furniture?)

### Proposed Hybrid System

```
┌─────────────────────────────────────────────────────────────┐
│                    PET LOCOMOTION                           │
├─────────────────────────────────────────────────────────────┤
│  moveTo(target)                                             │
│    ├── Cast ray forward to detect obstacles                 │
│    ├── If small obstacle → triggerJump()                    │
│    ├── If large obstacle → pathAround() (future)            │
│    └── Update position + check bone collisions              │
├─────────────────────────────────────────────────────────────┤
│  Pet Entity Components:                                     │
│    - Mesh, Transform, Scale, Animated (existing)            │
│    - Physics(KINEMATIC) + Sphere collider (NEW)             │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Steps

### Step 1: Add Jump Animation Constant
File: `PetLocomotion.kt`

```kotlin
companion object {
    const val ANIM_IDLE = 0
    const val ANIM_WALK = 1
    const val ANIM_WAG = 2
    const val ANIM_WALKLOOP = 3
    const val ANIM_JUMP = 4  // NEW - verify track index in metadog.glb
}
```

### Step 2: Add Physics Collider to Pet Entity
File: `ImmersiveActivity.kt` in `selectPet()`

Add a kinematic sphere collider to the pet entity. Kinematic means:
- It doesn't get pushed by physics forces
- But it DOES push dynamic objects (bones) on collision

```kotlin
currentPetEntity = Entity.create(
    listOf(
        Mesh(meshUri.toUri()),
        Transform(...),
        Scale(Vector3(0.2f, 0.2f, 0.2f)),
        TransformParent(panel),
        Animated(...),
        // NEW: Physics collider for bone pushing
        Sphere(0.15f),  // Collision sphere radius
        Physics().apply {
            state = PhysicsState.KINEMATIC
            dimensions = Vector3(0.3f, 0.3f, 0.3f)
        }
    )
)
```

### Step 3: Add Obstacle Detection to PetLocomotion
File: `PetLocomotion.kt`

Add MRUK reference and obstacle detection:

```kotlin
class PetLocomotion(
    private val scope: CoroutineScope,
    private val floorY: Float = 0f,
    private val walkSpeed: Float = 0.5f
) {
    // NEW: MRUK reference for raycasting
    private var mrukFeature: MRUKFeature? = null

    // NEW: Jump state
    private var isJumping = false
    private val jumpHeight = 0.3f  // meters
    private val jumpDistance = 0.5f  // meters to clear

    fun setMrukFeature(mruk: MRUKFeature) {
        this.mrukFeature = mruk
    }

    /**
     * Check for obstacles in movement path using MRUK raycast
     */
    private fun detectObstacleAhead(currentPos: Vector3, direction: Vector3): ObstacleInfo? {
        val mruk = mrukFeature ?: return null
        val currentRoom = mruk.getCurrentRoom() ?: return null

        // Cast ray at knee height (0.2m) to detect furniture
        val rayOrigin = Vector3(currentPos.x, floorY + 0.2f, currentPos.z)
        val hit = mruk.raycastRoom(
            currentRoom.anchor.uuid,
            rayOrigin,
            direction,
            1.0f,  // Look 1m ahead
            SurfaceType.PLANE_VOLUME
        )

        if (hit != null) {
            val distance = vectorLength(vectorDiff(hit.hitPosition, rayOrigin))
            // Determine if jumpable based on anchor type
            val isJumpable = isJumpableObstacle(hit)
            return ObstacleInfo(hit.hitPosition, distance, isJumpable)
        }
        return null
    }

    data class ObstacleInfo(
        val position: Vector3,
        val distance: Float,
        val isJumpable: Boolean
    )
}
```

### Step 4: Integrate Jump into Movement Loop
File: `PetLocomotion.kt` in `moveTo()`

Modify the movement loop to handle jumping:

```kotlin
fun moveTo(target: Vector3) {
    // ... existing setup code ...

    walkJob = scope.launch {
        isWalking = true
        playAnimation(ANIM_WALKLOOP, loop = true)

        // ... existing code ...

        while (isActive) {
            // Check for obstacles ahead (every few frames)
            if (frameCount % 10 == 0 && !isJumping) {
                val obstacle = detectObstacleAhead(currentPos, direction)
                if (obstacle != null && obstacle.distance < 0.5f && obstacle.isJumpable) {
                    triggerJump()
                }
            }

            // Calculate Y position (includes jump arc if jumping)
            val baseY = startPos.y
            val jumpY = if (isJumping) calculateJumpArc(jumpProgress) else 0f

            val newPos = Vector3(
                startPos.x + dx * progress,
                baseY + jumpY,
                startPos.z + dz * progress
            )

            // ... rest of movement code ...
        }
    }
}

private fun triggerJump() {
    isJumping = true
    jumpStartTime = System.currentTimeMillis()
    playAnimation(ANIM_JUMP, loop = false)

    scope.launch {
        delay(500)  // Jump animation duration
        isJumping = false
        playAnimation(ANIM_WALKLOOP, loop = true)
    }
}

private fun calculateJumpArc(progress: Float): Float {
    // Parabolic arc: peaks at 50% progress
    return jumpHeight * 4f * progress * (1f - progress)
}
```

### Step 5: Add Bone Push Force
File: `PetLocomotion.kt`

Add bone collision detection in movement loop:

```kotlin
// In moveTo() loop, after position update:
checkBoneCollisions(newPos)

private fun checkBoneCollisions(petPos: Vector3) {
    val pushRadius = 0.2f  // How close before pushing
    val pushForce = 2.0f   // Impulse strength

    // Query all entities with Physics component
    Query.where { has(Physics.id) }.eval().forEach { entity ->
        val physics = entity.tryGetComponent<Physics>() ?: return@forEach
        if (physics.state != PhysicsState.DYNAMIC) return@forEach

        val bonePos = entity.tryGetComponent<Transform>()?.transform?.t ?: return@forEach
        val distance = vectorLength(vectorDiff(bonePos, petPos))

        if (distance < pushRadius) {
            // Calculate push direction (away from pet)
            val pushDir = vectorNormalize(vectorDiff(bonePos, petPos))

            // Apply impulse via velocity change
            // Note: May need to use Physics.linearVelocity or similar
            val impulse = Vector3(
                pushDir.x * pushForce,
                0.5f,  // Slight upward pop
                pushDir.z * pushForce
            )

            entity.setComponent(physics.apply {
                linearVelocity = impulse
            })

            Log.d(TAG, "Pushed bone away from pet")
        }
    }
}
```

### Step 6: Pass Thrown Bones List to PetLocomotion
File: `ImmersiveActivity.kt`

More efficient than querying all physics entities:

```kotlin
// In ImmersiveActivity
petLocomotion.setThrownBones(thrownBones)

// In PetLocomotion
private var thrownBones: List<Entity> = emptyList()

fun setThrownBones(bones: List<Entity>) {
    this.thrownBones = bones
}
```

## File Changes Summary

| File | Changes |
|------|---------|
| `PetLocomotion.kt` | Add ANIM_JUMP, obstacle detection, jump arc, bone pushing |
| `ImmersiveActivity.kt` | Add Physics+Sphere to pet entity, pass MRUK and bones to locomotion |

## Testing Plan

1. **Basic collision**: Spawn bone, walk dog into it → bone should move
2. **Jump trigger**: Place dog path over furniture edge → dog should jump
3. **Jump arc**: Verify dog clears 0.3m height obstacle
4. **Animation**: Verify jump animation plays and returns to walk
5. **Edge cases**: Dog at boundary + obstacle, multiple bones, etc.

## Future Enhancements

- Path-around for large obstacles (pathfinding)
- Different jump heights based on obstacle size
- Nose-push animation for bones
- Sound effects for jump landing
