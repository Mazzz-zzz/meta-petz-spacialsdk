# Pet Jump & A* Pathfinding Implementation Plan

## Overview
Implement A* pathfinding that allows pets to navigate between floor and elevated furniture surfaces, using the jump animation when transitioning between height levels.

## Current State
- NavGrid has 15cm cells with height data per cell (`heightGrid`)
- `isWalkableElevatedSurface()` identifies valid jump targets (2x2 minimum, purple in debug)
- `getCellHeight()` returns Y position for any cell
- `moveTo()` walks pet in straight line at fixed Y height
- `playAnimation(ANIM_JUMP)` available (track 0)
- Floor cells are walkable (green/yellow), elevated surfaces are blocked but jumpable (purple)

## Implementation Steps

### Step 1: Add A* Pathfinding to NavGrid
Add pathfinding that considers floor cells AND elevated walkable surfaces:

```kotlin
data class PathNode(
    val gx: Int,
    val gz: Int,
    val height: Float
)

fun findPath(
    startX: Float, startZ: Float,
    targetX: Float, targetZ: Float
): List<PathNode>?
```

**Neighbor rules:**
- 8-directional movement on same height level
- Can step UP to adjacent cell if `isWalkableElevatedSurface()` = true
- Can step DOWN from elevated surface to floor
- Cost: normal movement = 1, height change = 5 (prefer staying on same level)

### Step 2: Track Jump Transitions in Path
Mark path segments that require jumps:

```kotlin
data class PathSegment(
    val points: List<PathNode>,
    val requiresJump: Boolean,
    val heightChange: Float  // positive = jump up, negative = jump down
)

fun getPathSegments(path: List<PathNode>): List<PathSegment>
```

**Jump thresholds:**
- Height diff > 10cm = requires jump
- Max jump UP: 0.8m (table/couch height)
- Max jump DOWN: 1.2m (can drop from higher)

### Step 3: Create Jump Movement Coroutine
Parabolic arc movement with animation:

```kotlin
private suspend fun performJump(from: Vector3, to: Vector3) {
    playAnimation(ANIM_JUMP, loop = false)

    val duration = 400L  // ms
    val arcHeight = 0.25f + (to.y - from.y).coerceAtLeast(0f) * 0.5f

    val startTime = System.currentTimeMillis()
    while (true) {
        val elapsed = System.currentTimeMillis() - startTime
        val t = (elapsed / duration.toFloat()).coerceIn(0f, 1f)

        // Horizontal: linear interpolation
        val x = lerp(from.x, to.x, t)
        val z = lerp(from.z, to.z, t)

        // Vertical: parabolic arc
        val baseY = lerp(from.y, to.y, t)
        val arc = arcHeight * 4f * t * (1f - t)  // peaks at t=0.5
        val y = baseY + arc

        updatePetPosition(x, y, z)

        if (t >= 1f) break
        delay(16)
    }

    playAnimation(ANIM_WAG, loop = true)
}
```

### Step 4: Update moveTo() for Path Following
Replace direct movement with path-based movement:

```kotlin
fun moveToWithPathfinding(target: Vector3) {
    val path = navGrid?.findPath(currentPos, target)
    if (path == null) {
        // No valid path - try direct movement or give up
        return
    }

    val segments = getPathSegments(path)

    walkJob = scope.launch {
        for (segment in segments) {
            if (segment.requiresJump) {
                performJump(segment.start, segment.end)
            } else {
                walkAlongPath(segment.points)
            }
        }
    }
}
```

### Step 5: Update Idle Wander
Include elevated surfaces as wander destinations:

```kotlin
fun getRandomWalkablePointIncludingElevated(): Vector3? {
    // 70% chance floor, 30% chance elevated surface
    val includeElevated = Random.nextFloat() < 0.3f

    if (includeElevated) {
        // Find random walkable elevated cell
        val elevatedCells = findAllWalkableElevatedCells()
        if (elevatedCells.isNotEmpty()) {
            val cell = elevatedCells.random()
            return gridToWorldWithHeight(cell.gx, cell.gz)
        }
    }

    return getRandomWalkablePoint()  // Floor point
}
```

## File Changes

### NavGrid.kt
- Add `PathNode` data class
- Add `findPath()` - A* implementation with height awareness
- Add `getNeighborsIncludingElevated()` - returns valid neighbors for pathfinding
- Add `findAllWalkableElevatedCells()` - list of purple cells
- Add `gridToWorldWithHeight()` - returns Vector3 at correct Y

### PetLocomotion.kt
- Add `moveToWithPathfinding()` - main entry point
- Add `performJump()` - jump coroutine with arc
- Add `walkAlongPath()` - walk through list of points
- Add `getPathSegments()` - split path at height changes
- Update `startIdleWander()` - use elevated destinations

## Visual Summary

```
Floor (green)     Furniture (purple)      Pet Movement
    ┌───┬───┐         ┌───┬───┐
    │ . │ . │         │ P │ P │         1. Walk on floor
    ├───┼───┤    +    ├───┼───┤    →    2. Jump UP to table
    │ . │ . │         │ P │ P │         3. Walk on table
    └───┴───┘         └───┴───┘         4. Jump DOWN to floor

    P = Purple (walkable elevated surface)
    . = Green (walkable floor)
```

## Edge Cases
- No path exists → stay in place, log warning
- Pet on furniture when it's removed → fall to floor height
- Jump interrupted by user action → complete jump first
- Target is on different elevation → pathfind to nearest accessible point

## Testing Checklist
- [ ] Pet on floor, target on table → walks + jumps up
- [ ] Pet on table, target on floor → jumps down + walks
- [ ] Pet wanders → occasionally jumps to/from furniture
- [ ] Obstacle between floors → pathfinds around
- [ ] Small furniture (< 2x2) → treated as blocked, not jumpable
