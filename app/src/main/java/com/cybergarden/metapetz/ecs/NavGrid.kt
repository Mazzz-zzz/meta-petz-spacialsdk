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
import com.meta.spatial.toolkit.Visible
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
    val cellSize: Float = 0.15f,  // 15cm cells
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
        fun fromFloorPolygon(polygon: PetLocomotion.FloorPolygon, floorY: Float = 0f, cellSize: Float = 0.15f): NavGrid {
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
     * Block cells inside a polygon defined by world-space corners.
     * Uses ray-casting algorithm for point-in-polygon test.
     *
     * @param corners List of (x, z) world coordinates forming the polygon (in order)
     * @param padding Extra padding around furniture in meters (default 5cm)
     */
    fun blockPolygon(corners: List<Pair<Float, Float>>, padding: Float = 0.05f) {
        if (corners.size < 3) {
            Log.w(TAG, "blockPolygon requires at least 3 corners, got ${corners.size}")
            return
        }

        // Expand polygon outward by padding amount
        val paddedCorners = expandPolygon(corners, padding)

        // Find bounding box of padded polygon for efficient grid search
        val minX = paddedCorners.minOf { it.first }
        val maxX = paddedCorners.maxOf { it.first }
        val minZ = paddedCorners.minOf { it.second }
        val maxZ = paddedCorners.maxOf { it.second }

        val minGx = worldToGridX(minX)
        val maxGx = worldToGridX(maxX)
        val minGz = worldToGridZ(minZ)
        val maxGz = worldToGridZ(maxZ)

        var blockedCount = 0
        for (gx in minGx..maxGx) {
            for (gz in minGz..maxGz) {
                if (gx in 0 until gridWidth && gz in 0 until gridHeight) {
                    if (grid[gx][gz]) {
                        val cellWorld = gridToWorld(gx, gz)
                        if (pointInPolygon(cellWorld.x, cellWorld.z, paddedCorners)) {
                            grid[gx][gz] = false
                            blockedCount++
                        }
                    }
                }
            }
        }

        if (blockedCount > 0) {
            walkableCellsDirty = true
            Log.d(TAG, "Blocked polygon with ${corners.size} corners: $blockedCount cells")
        }
    }

    /**
     * Point-in-polygon test using ray casting algorithm.
     * Casts a ray from the point to the right and counts edge crossings.
     */
    private fun pointInPolygon(x: Float, z: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1

        for (i in 0 until n) {
            val xi = polygon[i].first
            val zi = polygon[i].second
            val xj = polygon[j].first
            val zj = polygon[j].second

            // Check if edge crosses the horizontal ray from (x, z) going right
            if ((zi > z) != (zj > z)) {
                // Calculate x-coordinate of intersection
                val intersectX = (xj - xi) * (z - zi) / (zj - zi) + xi
                if (x < intersectX) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    /**
     * Expand polygon outward by a padding distance.
     * Moves each vertex outward along the angle bisector.
     * Automatically detects polygon winding order.
     */
    private fun expandPolygon(corners: List<Pair<Float, Float>>, padding: Float): List<Pair<Float, Float>> {
        if (padding <= 0f) return corners

        val n = corners.size
        val expanded = mutableListOf<Pair<Float, Float>>()

        // Calculate signed area to determine winding order
        // Positive = CCW, Negative = CW
        var signedArea = 0f
        for (i in 0 until n) {
            val curr = corners[i]
            val next = corners[(i + 1) % n]
            signedArea += (next.first - curr.first) * (next.second + curr.second)
        }
        // Flip direction if polygon is CW (negative area means we need to reverse normals)
        val windingSign = if (signedArea > 0) -1f else 1f

        for (i in 0 until n) {
            val prev = corners[(i - 1 + n) % n]
            val curr = corners[i]
            val next = corners[(i + 1) % n]

            // Edge vectors
            val e1x = curr.first - prev.first
            val e1z = curr.second - prev.second
            val e2x = next.first - curr.first
            val e2z = next.second - curr.second

            // Normalize edge vectors
            val len1 = kotlin.math.sqrt(e1x * e1x + e1z * e1z)
            val len2 = kotlin.math.sqrt(e2x * e2x + e2z * e2z)

            if (len1 < 0.0001f || len2 < 0.0001f) {
                expanded.add(curr)
                continue
            }

            val n1x = e1x / len1
            val n1z = e1z / len1
            val n2x = e2x / len2
            val n2z = e2z / len2

            // Outward normals (perpendicular, adjusted for winding order)
            val out1x = n1z * windingSign
            val out1z = -n1x * windingSign
            val out2x = n2z * windingSign
            val out2z = -n2x * windingSign

            // Bisector direction (average of outward normals)
            var bisectX = out1x + out2x
            var bisectZ = out1z + out2z
            val bisectLen = kotlin.math.sqrt(bisectX * bisectX + bisectZ * bisectZ)

            if (bisectLen < 0.0001f) {
                // Edges are parallel, just use one normal
                expanded.add(Pair(curr.first + out1x * padding, curr.second + out1z * padding))
            } else {
                bisectX /= bisectLen
                bisectZ /= bisectLen

                // Scale by padding / cos(half-angle) to maintain distance from edges
                val dot = out1x * bisectX + out1z * bisectZ
                val scale = if (dot > 0.1f) padding / dot else padding

                expanded.add(Pair(curr.first + bisectX * scale, curr.second + bisectZ * scale))
            }
        }

        return expanded
    }

    /**
     * Keep only the largest connected walkable region.
     * Uses flood-fill to find all connected regions, then blocks all but the largest.
     * Call this after blocking all furniture and walls.
     */
    fun keepLargestConnectedRegion() {
        // Track which cells have been visited
        val visited = Array(gridWidth) { BooleanArray(gridHeight) { false } }
        val regions = mutableListOf<MutableList<Pair<Int, Int>>>()

        // Find all connected regions using flood-fill
        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                if (grid[gx][gz] && !visited[gx][gz]) {
                    // Start a new region
                    val region = mutableListOf<Pair<Int, Int>>()
                    floodFill(gx, gz, visited, region)
                    regions.add(region)
                }
            }
        }

        if (regions.isEmpty()) {
            Log.w(TAG, "No walkable regions found!")
            return
        }

        // Find the largest region
        val largestRegion = regions.maxByOrNull { it.size }!!
        val largestSize = largestRegion.size
        val totalRemoved = regions.sumOf { it.size } - largestSize

        Log.d(TAG, "Found ${regions.size} walkable regions. Largest: $largestSize cells. Removing $totalRemoved cells from smaller regions.")

        // Convert largest region to a set for fast lookup
        val keepCells = largestRegion.toSet()

        // Block all cells not in the largest region
        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                if (grid[gx][gz] && !keepCells.contains(Pair(gx, gz))) {
                    grid[gx][gz] = false
                }
            }
        }

        walkableCellsDirty = true
        Log.d(TAG, "Kept largest region with $largestSize walkable cells")
    }

    /**
     * Flood-fill helper using iterative BFS to avoid stack overflow.
     */
    private fun floodFill(startX: Int, startZ: Int, visited: Array<BooleanArray>, region: MutableList<Pair<Int, Int>>) {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startZ))
        visited[startX][startZ] = true

        while (queue.isNotEmpty()) {
            val (gx, gz) = queue.removeFirst()
            region.add(Pair(gx, gz))

            // Check 4-connected neighbors
            val neighbors = listOf(
                Pair(gx - 1, gz),
                Pair(gx + 1, gz),
                Pair(gx, gz - 1),
                Pair(gx, gz + 1)
            )

            for ((nx, nz) in neighbors) {
                if (nx in 0 until gridWidth && nz in 0 until gridHeight) {
                    if (grid[nx][nz] && !visited[nx][nz]) {
                        visited[nx][nz] = true
                        queue.add(Pair(nx, nz))
                    }
                }
            }
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
    private var debugVisualizationCreated = false
    private var debugVisualizationVisible = false

    /**
     * Create debug visualization spheres for each grid cell (hidden by default).
     * Call this once after the room is fully loaded and furniture is blocked.
     * Use showDebugVisualization() and hideDebugVisualization() to toggle visibility.
     *
     * Green = walkable (far from furniture)
     * Yellow = walkable but near blocked cells
     * Red = blocked (furniture)
     *
     * @param showBlocked Whether to show blocked cells (red spheres)
     */
    fun createDebugVisualization(showBlocked: Boolean = true) {
        // Don't recreate if already exists
        if (debugVisualizationCreated) {
            Log.d(TAG, "Debug visualization already created, skipping")
            return
        }

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
                        Scale(sphereScale),
                        Visible(false)  // Start hidden
                    )
                )
                debugEntities.add(entity)
            }
        }

        debugVisualizationCreated = true
        debugVisualizationVisible = false
        Log.d(TAG, "Created ${debugEntities.size} debug visualization entities (hidden)")
    }

    /**
     * Show the debug visualization (toggle Visible component).
     * Fast operation - just toggles visibility on existing entities.
     */
    fun showDebugVisualization() {
        if (!debugVisualizationCreated) {
            Log.w(TAG, "Debug visualization not created yet")
            return
        }
        if (debugVisualizationVisible) return

        for (entity in debugEntities) {
            entity.setComponent(Visible(true))
        }
        debugVisualizationVisible = true
        Log.d(TAG, "Showing debug visualization (${debugEntities.size} entities)")
    }

    /**
     * Hide the debug visualization (toggle Visible component).
     * Fast operation - just toggles visibility on existing entities.
     */
    fun hideDebugVisualization() {
        if (!debugVisualizationCreated) return
        if (!debugVisualizationVisible) return

        for (entity in debugEntities) {
            entity.setComponent(Visible(false))
        }
        debugVisualizationVisible = false
        Log.d(TAG, "Hiding debug visualization")
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
        debugVisualizationCreated = false
        debugVisualizationVisible = false
        Log.d(TAG, "Cleared debug visualization")
    }

    /**
     * Check if debug visualization is currently active.
     */
    fun isDebugVisualizationActive(): Boolean = debugVisualizationCreated

    /**
     * Check if debug visualization is currently visible.
     */
    fun isDebugVisualizationVisible(): Boolean = debugVisualizationVisible
}
