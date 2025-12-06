package com.cybergarden.metapetz.ecs

import android.util.Log
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Sphere
import com.meta.spatial.toolkit.Transform
import kotlin.random.Random

/**
 * NavGrid - 2D navigation grid for pet pathfinding
 *
 * Uses 10x10cm cells to represent walkable floor space.
 * Cells can be blocked by furniture to prevent pet wandering into obstacles.
 *
 * Usage:
 * 1. Create grid with floor bounds
 * 2. Initialize from floor polygon to mark walkable cells
 * 3. Block furniture rectangles
 * 4. Use getRandomWalkablePoint() for wander destinations
 */
class NavGrid(
    val cellSize: Float = 0.3f,  // 30cm cells
    val minX: Float,
    val maxX: Float,
    val minZ: Float,
    val maxZ: Float,
    val floorY: Float = 0f
) {
    companion object {
        private const val TAG = "NavGrid"

        /**
         * Create a NavGrid from a FloorPolygon's bounding box.
         */
        fun fromFloorPolygon(polygon: PetLocomotion.FloorPolygon, floorY: Float = 0f, cellSize: Float = 0.3f): NavGrid {
            // Find bounding box of polygon
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE

            for (vertex in polygon.vertices) {
                minX = minOf(minX, vertex.x)
                maxX = maxOf(maxX, vertex.x)
                minZ = minOf(minZ, vertex.z)
                maxZ = maxOf(maxZ, vertex.z)
            }

            // Add small margin
            val margin = cellSize
            minX -= margin
            maxX += margin
            minZ -= margin
            maxZ += margin

            val grid = NavGrid(cellSize, minX, maxX, minZ, maxZ, floorY)
            grid.initFromFloorPolygon(polygon)
            return grid
        }
    }

    // Grid dimensions
    val gridWidth: Int = ((maxX - minX) / cellSize).toInt() + 1
    val gridHeight: Int = ((maxZ - minZ) / cellSize).toInt() + 1

    // Walkability grid: true = walkable, false = blocked
    private val grid: Array<BooleanArray> = Array(gridWidth) { BooleanArray(gridHeight) { false } }

    // Cache of walkable cells for fast random selection
    private val walkableCells = mutableListOf<Pair<Int, Int>>()
    private var walkableCellsDirty = true

    init {
        Log.d(TAG, "NavGrid created: ${gridWidth}x${gridHeight} cells, bounds=($minX,$minZ) to ($maxX,$maxZ)")
    }

    /**
     * Initialize walkable cells from a FloorPolygon.
     * All cells inside the polygon are marked as walkable.
     */
    fun initFromFloorPolygon(polygon: PetLocomotion.FloorPolygon) {
        var walkableCount = 0

        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                val worldPos = gridToWorld(gx, gz)
                if (polygon.contains(worldPos.x, worldPos.z)) {
                    grid[gx][gz] = true
                    walkableCount++
                }
            }
        }

        walkableCellsDirty = true
        Log.d(TAG, "Floor polygon applied: $walkableCount walkable cells")
    }

    /**
     * Block a rectangular area (furniture footprint).
     * All cells overlapping the rectangle are marked as non-walkable.
     *
     * @param centerX World X coordinate of rectangle center
     * @param centerZ World Z coordinate of rectangle center
     * @param halfSizeX Half-width in X direction
     * @param halfSizeZ Half-width in Z direction
     * @param padding Extra padding around furniture (default 5cm)
     */
    fun blockRect(centerX: Float, centerZ: Float, halfSizeX: Float, halfSizeZ: Float, padding: Float = 0.05f) {
        val paddedHalfX = halfSizeX + padding
        val paddedHalfZ = halfSizeZ + padding

        val minGx = worldToGridX(centerX - paddedHalfX)
        val maxGx = worldToGridX(centerX + paddedHalfX)
        val minGz = worldToGridZ(centerZ - paddedHalfZ)
        val maxGz = worldToGridZ(centerZ + paddedHalfZ)

        var blockedCount = 0
        for (gx in minGx..maxGx) {
            for (gz in minGz..maxGz) {
                if (gx in 0 until gridWidth && gz in 0 until gridHeight) {
                    if (grid[gx][gz]) {
                        grid[gx][gz] = false
                        blockedCount++
                    }
                }
            }
        }

        if (blockedCount > 0) {
            walkableCellsDirty = true
            Log.d(TAG, "Blocked rect at ($centerX, $centerZ) size ${halfSizeX*2}x${halfSizeZ*2}: $blockedCount cells")
        }
    }

    /**
     * Check if a world position is walkable.
     */
    fun isWalkable(worldX: Float, worldZ: Float): Boolean {
        val gx = worldToGridX(worldX)
        val gz = worldToGridZ(worldZ)

        if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) {
            return false
        }

        return grid[gx][gz]
    }

    /**
     * Get a random walkable point for wandering.
     * Returns null if no walkable cells exist.
     */
    fun getRandomWalkablePoint(): Vector3? {
        rebuildWalkableCacheIfNeeded()

        if (walkableCells.isEmpty()) {
            Log.w(TAG, "No walkable cells available!")
            return null
        }

        val randomIndex = Random.nextInt(walkableCells.size)
        val (gx, gz) = walkableCells[randomIndex]
        return gridToWorld(gx, gz)
    }

    /**
     * Get a random walkable point within a maximum distance from a center point.
     * Useful for constrained wandering.
     */
    fun getRandomWalkablePointNear(centerX: Float, centerZ: Float, maxDistance: Float): Vector3? {
        rebuildWalkableCacheIfNeeded()

        val maxDistSq = maxDistance * maxDistance
        val nearbyCells = walkableCells.filter { (gx, gz) ->
            val pos = gridToWorld(gx, gz)
            val dx = pos.x - centerX
            val dz = pos.z - centerZ
            dx * dx + dz * dz <= maxDistSq
        }

        if (nearbyCells.isEmpty()) {
            Log.w(TAG, "No walkable cells near ($centerX, $centerZ) within $maxDistance")
            return null
        }

        val randomIndex = Random.nextInt(nearbyCells.size)
        val (gx, gz) = nearbyCells[randomIndex]
        return gridToWorld(gx, gz)
    }

    /**
     * Convert world X coordinate to grid X index.
     */
    fun worldToGridX(worldX: Float): Int {
        return ((worldX - minX) / cellSize).toInt()
    }

    /**
     * Convert world Z coordinate to grid Z index.
     */
    fun worldToGridZ(worldZ: Float): Int {
        return ((worldZ - minZ) / cellSize).toInt()
    }

    /**
     * Convert grid coordinates to world position (center of cell).
     */
    fun gridToWorld(gridX: Int, gridZ: Int): Vector3 {
        return Vector3(
            minX + (gridX + 0.5f) * cellSize,
            floorY,
            minZ + (gridZ + 0.5f) * cellSize
        )
    }

    /**
     * Rebuild the walkable cells cache if dirty.
     */
    private fun rebuildWalkableCacheIfNeeded() {
        if (!walkableCellsDirty) return

        walkableCells.clear()
        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                if (grid[gx][gz]) {
                    walkableCells.add(Pair(gx, gz))
                }
            }
        }
        walkableCellsDirty = false
        Log.d(TAG, "Rebuilt walkable cache: ${walkableCells.size} cells")
    }

    /**
     * Get total walkable cell count.
     */
    fun getWalkableCellCount(): Int {
        rebuildWalkableCacheIfNeeded()
        return walkableCells.size
    }

    /**
     * Get total cell count.
     */
    fun getTotalCellCount(): Int = gridWidth * gridHeight

    // Debug visualization entities
    private val debugEntities = mutableListOf<Entity>()

    /**
     * Create debug visualization spheres for each grid cell.
     * Green = walkable (far from furniture)
     * Yellow = walkable but near blocked cells
     * Red = blocked (furniture)
     *
     * @param showBlocked Whether to show blocked cells (red spheres)
     * @return List of created entities for cleanup
     */
    fun createDebugVisualization(showBlocked: Boolean = true): List<Entity> {
        // Clear any existing debug entities
        clearDebugVisualization()

        val sphereRadius = cellSize * 0.3f  // Small spheres
        val sphereScale = Vector3(1f, 0.3f, 1f)  // Flatten to disc

        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                val worldPos = gridToWorld(gx, gz)
                val isWalkable = grid[gx][gz]

                // Skip blocked cells if not showing them
                if (!isWalkable && !showBlocked) continue

                // Calculate distance to nearest blocked cell for color gradient
                val color = if (isWalkable) {
                    val distToBlocked = getDistanceToNearestBlocked(gx, gz)
                    // Green (far) to Yellow (near) gradient based on distance
                    val t = (distToBlocked / 5f).coerceIn(0f, 1f)  // 5 cells = max green
                    Color4(
                        1f - t * 0.8f,  // R: 1.0 (yellow) -> 0.2 (green)
                        1f,              // G: always 1.0
                        0.2f,            // B: low
                        0.5f             // A: semi-transparent
                    )
                } else {
                    // Red for blocked
                    Color4(1f, 0.2f, 0.2f, 0.5f)
                }

                val entity = Entity.create(
                    listOf(
                        Mesh(android.net.Uri.parse("mesh://sphere")),
                        Sphere(sphereRadius),
                        Material().apply {
                            baseColor = color
                            unlit = true
                        },
                        Transform(Pose(worldPos, Quaternion())),
                        Scale(sphereScale)
                    )
                )
                debugEntities.add(entity)
            }
        }

        Log.d(TAG, "Created ${debugEntities.size} debug visualization entities")
        return debugEntities.toList()
    }

    /**
     * Calculate distance (in cells) to nearest blocked cell.
     * Used for color gradient visualization.
     */
    private fun getDistanceToNearestBlocked(gx: Int, gz: Int): Int {
        // Search in expanding squares around the cell
        val maxSearch = 10  // Max search radius

        for (radius in 1..maxSearch) {
            // Check cells at this radius
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    // Only check cells on the perimeter of this radius
                    if (kotlin.math.abs(dx) != radius && kotlin.math.abs(dz) != radius) continue

                    val checkX = gx + dx
                    val checkZ = gz + dz

                    if (checkX in 0 until gridWidth && checkZ in 0 until gridHeight) {
                        if (!grid[checkX][checkZ]) {
                            // Found a blocked cell
                            return radius
                        }
                    }
                }
            }
        }

        return maxSearch  // No blocked cell found within search radius
    }

    /**
     * Clear all debug visualization entities.
     */
    fun clearDebugVisualization() {
        debugEntities.forEach { it.destroy() }
        debugEntities.clear()
        Log.d(TAG, "Cleared debug visualization")
    }

    /**
     * Check if debug visualization is currently active.
     */
    fun isDebugVisualizationActive(): Boolean = debugEntities.isNotEmpty()
}
