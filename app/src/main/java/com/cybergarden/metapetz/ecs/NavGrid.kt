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
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt
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
         * Returns null if polygon is invalid (empty, NaN bounds, etc.)
         */
        fun fromFloorPolygon(polygon: PetLocomotion.FloorPolygon, floorY: Float = 0f, cellSize: Float = 0.15f): NavGrid? {
            // Validate polygon has vertices
            if (polygon.vertices.isEmpty()) {
                Log.e(TAG, "Cannot create NavGrid: floor polygon has no vertices")
                return null
            }

            // Find bounding box of polygon
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE

            for (vertex in polygon.vertices) {
                // Check for NaN/Infinity
                if (vertex.x.isNaN() || vertex.x.isInfinite() || vertex.z.isNaN() || vertex.z.isInfinite()) {
                    Log.e(TAG, "Cannot create NavGrid: polygon has invalid vertex (${vertex.x}, ${vertex.z})")
                    return null
                }
                minX = minOf(minX, vertex.x)
                maxX = maxOf(maxX, vertex.x)
                minZ = minOf(minZ, vertex.z)
                maxZ = maxOf(maxZ, vertex.z)
            }

            // Validate bounds are sensible
            if (maxX <= minX || maxZ <= minZ) {
                Log.e(TAG, "Cannot create NavGrid: invalid bounds X[$minX, $maxX] Z[$minZ, $maxZ]")
                return null
            }

            // Add small margin
            val margin = cellSize
            minX -= margin
            maxX += margin
            minZ -= margin
            maxZ += margin

            // Validate floorY
            if (floorY.isNaN() || floorY.isInfinite()) {
                Log.e(TAG, "Cannot create NavGrid: invalid floorY=$floorY")
                return null
            }

            // Validate resulting grid size won't be too large
            val width = ((maxX - minX) / cellSize).toInt() + 1
            val height = ((maxZ - minZ) / cellSize).toInt() + 1
            if (width <= 0 || height <= 0 || width > 1000 || height > 1000) {
                Log.e(TAG, "Cannot create NavGrid: invalid dimensions ${width}x${height}")
                return null
            }

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

    // Height grid: Y position for each cell (defaults to floorY, elevated for furniture)
    private val heightGrid: Array<FloatArray> = Array(gridWidth) { FloatArray(gridHeight) { floorY } }

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
     * Block cells inside a polygon and set their height to the furniture's top surface.
     * Cells blocked by furniture will be shown in purple in debug visualization.
     *
     * @param corners List of (x, z) world coordinates forming the polygon (in order)
     * @param height The Y height of the furniture's top surface
     * @param padding Extra padding around furniture in meters (default 5cm)
     */
    fun blockPolygonWithHeight(corners: List<Pair<Float, Float>>, height: Float, padding: Float = 0.05f) {
        if (corners.size < 3) {
            Log.w(TAG, "blockPolygonWithHeight requires at least 3 corners, got ${corners.size}")
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
                            heightGrid[gx][gz] = height  // Store furniture height
                            blockedCount++
                        }
                    }
                }
            }
        }

        if (blockedCount > 0) {
            walkableCellsDirty = true
            Log.d(TAG, "Blocked polygon with height=${"%.2f".format(height)}m: $blockedCount cells")
        }
    }

    /**
     * Get the height at a grid cell.
     */
    fun getCellHeight(gridX: Int, gridZ: Int): Float {
        if (gridX < 0 || gridX >= gridWidth || gridZ < 0 || gridZ >= gridHeight) {
            return floorY
        }
        return heightGrid[gridX][gridZ]
    }

    /**
     * Check if a cell is elevated (has furniture on it).
     */
    fun isCellElevated(gridX: Int, gridZ: Int): Boolean {
        return getCellHeight(gridX, gridZ) > floorY + 0.05f  // 5cm threshold
    }

    /**
     * Check if a cell is part of a walkable elevated surface (at least 2x2 cells at similar height).
     * Small elevated areas (single cells or thin strips) are not walkable for pets.
     */
    fun isWalkableElevatedSurface(gridX: Int, gridZ: Int): Boolean {
        val cellHeight = getCellHeight(gridX, gridZ)
        if (cellHeight <= floorY + 0.05f) return false  // Not elevated

        val heightTolerance = 0.1f  // 10cm tolerance for "same surface"

        // Check if this cell is part of any 2x2 block at similar height
        // Check all 4 possible 2x2 blocks that could include this cell
        val offsets = listOf(
            listOf(Pair(0, 0), Pair(1, 0), Pair(0, 1), Pair(1, 1)),   // This cell is top-left
            listOf(Pair(-1, 0), Pair(0, 0), Pair(-1, 1), Pair(0, 1)), // This cell is top-right
            listOf(Pair(0, -1), Pair(1, -1), Pair(0, 0), Pair(1, 0)), // This cell is bottom-left
            listOf(Pair(-1, -1), Pair(0, -1), Pair(-1, 0), Pair(0, 0)) // This cell is bottom-right
        )

        for (block in offsets) {
            var allElevated = true
            for ((dx, dz) in block) {
                val nx = gridX + dx
                val nz = gridZ + dz
                if (nx < 0 || nx >= gridWidth || nz < 0 || nz >= gridHeight) {
                    allElevated = false
                    break
                }
                val neighborHeight = getCellHeight(nx, nz)
                if (kotlin.math.abs(neighborHeight - cellHeight) > heightTolerance) {
                    allElevated = false
                    break
                }
            }
            if (allElevated) return true  // Found a valid 2x2 block
        }

        return false  // No valid 2x2 block found
    }

    // ==================== A* PATHFINDING ====================

    /**
     * Node for A* pathfinding with grid coordinates and height.
     */
    data class PathNode(
        val gx: Int,
        val gz: Int,
        val height: Float
    )

    /**
     * Internal node for A* algorithm with costs and parent tracking.
     */
    private data class AStarNode(
        val gx: Int,
        val gz: Int,
        val height: Float,
        var gCost: Float = Float.MAX_VALUE,  // Cost from start
        var fCost: Float = Float.MAX_VALUE,  // gCost + heuristic
        var parent: AStarNode? = null
    ) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int = fCost.compareTo(other.fCost)

        fun toPathNode() = PathNode(gx, gz, height)
    }

    /**
     * Find a path from start to target using A* algorithm.
     * Supports navigation across floor AND elevated surfaces (furniture).
     *
     * @param startX World X coordinate of start position
     * @param startZ World Z coordinate of start position
     * @param targetX World X coordinate of target position
     * @param targetZ World Z coordinate of target position
     * @return List of PathNodes from start to target, or null if no path exists
     */
    fun findPath(startX: Float, startZ: Float, targetX: Float, targetZ: Float): List<PathNode>? {
        val startGx = worldToGridX(startX)
        val startGz = worldToGridZ(startZ)
        val targetGx = worldToGridX(targetX)
        val targetGz = worldToGridZ(targetZ)

        // Validate bounds
        if (startGx !in 0 until gridWidth || startGz !in 0 until gridHeight) {
            Log.w(TAG, "Start position out of bounds: ($startGx, $startGz)")
            return null
        }
        if (targetGx !in 0 until gridWidth || targetGz !in 0 until gridHeight) {
            Log.w(TAG, "Target position out of bounds: ($targetGx, $targetGz)")
            return null
        }

        // Check if target is reachable (walkable floor OR walkable elevated surface)
        val targetIsWalkable = grid[targetGx][targetGz] || isWalkableElevatedSurface(targetGx, targetGz)
        if (!targetIsWalkable) {
            Log.w(TAG, "Target is not walkable: ($targetGx, $targetGz)")
            return null
        }

        val startHeight = getCellHeight(startGx, startGz)
        val targetHeight = getCellHeight(targetGx, targetGz)

        val openSet = PriorityQueue<AStarNode>()
        val closedSet = mutableSetOf<Pair<Int, Int>>()
        val nodeMap = mutableMapOf<Pair<Int, Int>, AStarNode>()

        val startNode = AStarNode(startGx, startGz, startHeight, gCost = 0f)
        startNode.fCost = heuristic(startGx, startGz, targetGx, targetGz)
        openSet.add(startNode)
        nodeMap[Pair(startGx, startGz)] = startNode

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()!!

            // Reached target?
            if (current.gx == targetGx && current.gz == targetGz) {
                return reconstructPath(current)
            }

            closedSet.add(Pair(current.gx, current.gz))

            // Check all neighbors (8-directional + elevation transitions)
            for (neighbor in getNeighborsForPathfinding(current.gx, current.gz, current.height)) {
                val neighborKey = Pair(neighbor.gx, neighbor.gz)
                if (neighborKey in closedSet) continue

                // Calculate movement cost
                val isDiagonal = neighbor.gx != current.gx && neighbor.gz != current.gz
                val baseCost = if (isDiagonal) 1.414f else 1f
                val heightDiff = abs(neighbor.height - current.height)
                val heightCost = if (heightDiff > 0.1f) 5f else 0f  // Penalty for jumps
                val moveCost = baseCost + heightCost

                val tentativeG = current.gCost + moveCost

                val existingNode = nodeMap[neighborKey]
                if (existingNode != null) {
                    if (tentativeG < existingNode.gCost) {
                        existingNode.gCost = tentativeG
                        existingNode.fCost = tentativeG + heuristic(neighbor.gx, neighbor.gz, targetGx, targetGz)
                        existingNode.parent = current
                        // Re-add to open set with updated priority
                        openSet.remove(existingNode)
                        openSet.add(existingNode)
                    }
                } else {
                    val newNode = AStarNode(
                        neighbor.gx, neighbor.gz, neighbor.height,
                        gCost = tentativeG,
                        fCost = tentativeG + heuristic(neighbor.gx, neighbor.gz, targetGx, targetGz),
                        parent = current
                    )
                    openSet.add(newNode)
                    nodeMap[neighborKey] = newNode
                }
            }
        }

        Log.w(TAG, "No path found from ($startGx,$startGz) to ($targetGx,$targetGz)")
        return null
    }

    /**
     * Heuristic for A* (Euclidean distance).
     */
    private fun heuristic(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Float {
        val dx = (toX - fromX).toFloat()
        val dz = (toZ - fromZ).toFloat()
        return sqrt(dx * dx + dz * dz)
    }

    /**
     * Reconstruct path from A* result by following parent pointers.
     */
    private fun reconstructPath(endNode: AStarNode): List<PathNode> {
        val path = mutableListOf<PathNode>()
        var current: AStarNode? = endNode

        while (current != null) {
            path.add(0, current.toPathNode())
            current = current.parent
        }

        Log.d(TAG, "Path found with ${path.size} nodes")
        return path
    }

    /**
     * Get valid neighbors for pathfinding, including elevation transitions.
     *
     * A cell is a valid neighbor if:
     * - It's a walkable floor cell (grid[gx][gz] == true), OR
     * - It's a walkable elevated surface (isWalkableElevatedSurface == true)
     *
     * Jump constraints:
     * - Max jump UP: 0.8m
     * - Max jump DOWN: 1.2m
     */
    private fun getNeighborsForPathfinding(gx: Int, gz: Int, currentHeight: Float): List<PathNode> {
        val neighbors = mutableListOf<PathNode>()
        val maxJumpUp = 0.8f
        val maxJumpDown = 1.2f

        // 8-directional neighbors
        val directions = listOf(
            Pair(-1, -1), Pair(0, -1), Pair(1, -1),
            Pair(-1, 0),              Pair(1, 0),
            Pair(-1, 1),  Pair(0, 1),  Pair(1, 1)
        )

        for ((dx, dz) in directions) {
            val nx = gx + dx
            val nz = gz + dz

            if (nx !in 0 until gridWidth || nz !in 0 until gridHeight) continue

            val neighborHeight = getCellHeight(nx, nz)
            val heightDiff = neighborHeight - currentHeight

            // Check jump constraints
            if (heightDiff > maxJumpUp) continue      // Too high to jump up
            if (-heightDiff > maxJumpDown) continue   // Too high to drop down

            // Check if neighbor is walkable (floor OR elevated surface)
            val isFloorWalkable = grid[nx][nz]
            val isElevatedWalkable = isWalkableElevatedSurface(nx, nz)

            if (isFloorWalkable || isElevatedWalkable) {
                neighbors.add(PathNode(nx, nz, neighborHeight))
            }
        }

        return neighbors
    }

    /**
     * Get all walkable elevated cells (for random wander destinations).
     */
    fun findAllWalkableElevatedCells(): List<PathNode> {
        val cells = mutableListOf<PathNode>()
        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                if (isWalkableElevatedSurface(gx, gz)) {
                    cells.add(PathNode(gx, gz, getCellHeight(gx, gz)))
                }
            }
        }
        return cells
    }

    /**
     * Convert grid coordinates to world position with correct height.
     */
    fun gridToWorldWithHeight(gx: Int, gz: Int): Vector3 {
        return Vector3(
            minX + (gx + 0.5f) * cellSize,
            getCellHeight(gx, gz),
            minZ + (gz + 0.5f) * cellSize
        )
    }

    /**
     * Check if moving from one node to another requires a jump (height change > 10cm).
     */
    fun requiresJump(from: PathNode, to: PathNode): Boolean {
        return abs(to.height - from.height) > 0.1f
    }

    // ==================== END A* PATHFINDING ====================

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

    // ==================== MANUAL EDITING ====================

    /**
     * Manually block a cell at a world position.
     * Used for debug/edit mode to mark areas as non-walkable.
     * @return true if cell was blocked, false if out of bounds or already blocked
     */
    fun blockCellAtWorldPos(worldX: Float, worldZ: Float): Boolean {
        val gx = worldToGridX(worldX)
        val gz = worldToGridZ(worldZ)

        if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) {
            return false
        }

        if (!grid[gx][gz]) return false  // Already blocked

        grid[gx][gz] = false
        walkableCellsDirty = true
        updateDebugSphereColor(gx, gz)
        Log.d(TAG, "Manually blocked cell ($gx, $gz)")
        return true
    }

    /**
     * Manually unblock a cell at a world position.
     * Used for debug/edit mode to mark areas as walkable.
     * @return true if cell was unblocked, false if out of bounds or already walkable
     */
    fun unblockCellAtWorldPos(worldX: Float, worldZ: Float): Boolean {
        val gx = worldToGridX(worldX)
        val gz = worldToGridZ(worldZ)

        if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) {
            return false
        }

        if (grid[gx][gz]) return false  // Already walkable

        grid[gx][gz] = true
        walkableCellsDirty = true
        updateDebugSphereColor(gx, gz)
        Log.d(TAG, "Manually unblocked cell ($gx, $gz)")
        return true
    }

    /**
     * Get grid cell indices at a world position.
     * @return Pair(gx, gz) or null if out of bounds
     */
    fun getGridCellAt(worldX: Float, worldZ: Float): Pair<Int, Int>? {
        val gx = worldToGridX(worldX)
        val gz = worldToGridZ(worldZ)

        if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) {
            return null
        }
        return Pair(gx, gz)
    }

    /**
     * Update the debug sphere color for a single cell after manual edit.
     */
    private fun updateDebugSphereColor(gx: Int, gz: Int) {
        if (!debugVisualizationCreated) return

        // Calculate index in the debug entities list
        val index = gx * gridHeight + gz
        if (index >= debugEntities.size) return

        val entity = debugEntities[index]
        val isWalkable = grid[gx][gz]

        val color = if (isWalkable) {
            val distToBlocked = getDistanceToNearestBlocked(gx, gz)
            val t = (distToBlocked / 5f).coerceIn(0f, 1f)
            Color4(1f - t * 0.8f, 1f, 0.2f, 0.5f)
        } else {
            // Red for blocked
            Color4(1f, 0.2f, 0.2f, 0.5f)
        }

        entity.setComponent(Material().apply {
            baseColor = color
            unlit = true
        })
    }

    // ==================== END MANUAL EDITING ====================

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

    /**
     * Find the nearest walkable cell (floor or elevated surface) to a world position.
     * Searches within a maximum 3D distance and returns the closest valid cell.
     *
     * @param worldX World X coordinate of the target position
     * @param worldY World Y coordinate of the target position (height)
     * @param worldZ World Z coordinate of the target position
     * @param maxDistance Maximum 3D distance to search (default 0.5m = 50cm)
     * @return World position of nearest walkable cell, or null if none found within range
     */
    fun findNearestWalkableCell(worldX: Float, worldY: Float, worldZ: Float, maxDistance: Float = 0.5f): Vector3? {
        // Convert to grid coordinates for center of search
        val centerGx = worldToGridX(worldX)
        val centerGz = worldToGridZ(worldZ)

        // Calculate search radius in cells
        val searchRadiusCells = (maxDistance / cellSize).toInt() + 1

        var nearestPos: Vector3? = null
        var nearestDistSq = Float.MAX_VALUE
        val maxDistSq = maxDistance * maxDistance

        // Search in a square area around the center
        for (gx in (centerGx - searchRadiusCells)..(centerGx + searchRadiusCells)) {
            for (gz in (centerGz - searchRadiusCells)..(centerGz + searchRadiusCells)) {
                // Skip out of bounds
                if (gx < 0 || gx >= gridWidth || gz < 0 || gz >= gridHeight) continue

                // Check if this cell is walkable (floor or elevated surface)
                val isFloorWalkable = grid[gx][gz]
                val isElevatedWalkable = isWalkableElevatedSurface(gx, gz)

                if (!isFloorWalkable && !isElevatedWalkable) continue

                // Get world position with correct height
                val cellHeight = getCellHeight(gx, gz)
                val cellWorldPos = Vector3(
                    minX + (gx + 0.5f) * cellSize,
                    cellHeight,
                    minZ + (gz + 0.5f) * cellSize
                )

                // Calculate 3D distance
                val dx = cellWorldPos.x - worldX
                val dy = cellWorldPos.y - worldY
                val dz = cellWorldPos.z - worldZ
                val distSq = dx * dx + dy * dy + dz * dz

                // Check if within range and closer than current nearest
                if (distSq <= maxDistSq && distSq < nearestDistSq) {
                    nearestDistSq = distSq
                    nearestPos = cellWorldPos
                }
            }
        }

        if (nearestPos != null) {
            val dist = sqrt(nearestDistSq)
            Log.d(TAG, "Found nearest walkable cell at ${"%.2f".format(dist)}m from raycast hit")
        } else {
            Log.d(TAG, "No walkable cell found within ${"%.2f".format(maxDistance)}m of raycast hit")
        }

        return nearestPos
    }

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
     * Purple = blocked by furniture (elevated, shown at furniture height)
     * Red = blocked (floor-level obstacles like walls)
     *
     * @param showBlocked Whether to show blocked cells (purple/red spheres)
     */
    fun createDebugVisualization(showBlocked: Boolean = true) {
        // Don't recreate if already exists
        if (debugVisualizationCreated) {
            Log.d(TAG, "Debug visualization already created, skipping")
            return
        }

        // Safety check: limit total entities to prevent crashes on huge grids
        val totalCells = gridWidth * gridHeight
        val maxEntities = 10000  // Reasonable limit
        if (totalCells > maxEntities) {
            Log.e(TAG, "Grid too large for debug visualization: $totalCells cells (max: $maxEntities). Grid: ${gridWidth}x${gridHeight}")
            Log.e(TAG, "Bounds: X[$minX, $maxX] Z[$minZ, $maxZ]")
            return
        }

        Log.d(TAG, "Creating debug visualization for $totalCells cells (${gridWidth}x${gridHeight})")

        val sphereRadius = cellSize * 0.3f  // Small spheres
        val sphereScale = Vector3(1f, 0.3f, 1f)  // Flatten to disc

        try {
        for (gx in 0 until gridWidth) {
            for (gz in 0 until gridHeight) {
                val isWalkable = grid[gx][gz]
                val cellHeight = heightGrid[gx][gz]
                val isWalkableSurface = isWalkableElevatedSurface(gx, gz)  // 2x2 minimum check

                // Skip blocked cells if not showing them
                if (!isWalkable && !showBlocked) continue

                // Get world position with correct height
                val worldPos = Vector3(
                    minX + (gx + 0.5f) * cellSize,
                    cellHeight,  // Use the cell's height (floor or furniture top)
                    minZ + (gz + 0.5f) * cellSize
                )

                // Calculate color based on walkability and elevation
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
                } else if (isWalkableSurface) {
                    // Purple for walkable furniture surfaces (at least 2x2 cells)
                    Color4(0.7f, 0.2f, 1f, 0.6f)  // Purple with 60% alpha
                } else {
                    // Red for blocked cells (walls, small furniture edges, etc.)
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create debug visualization: ${e.message}", e)
            // Clean up any partially created entities
            debugEntities.forEach { try { it.destroy() } catch (_: Exception) {} }
            debugEntities.clear()
            debugVisualizationCreated = false
        }
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
        var destroyedCount = 0
        var failedCount = 0
        for (entity in debugEntities) {
            try {
                entity.destroy()
                destroyedCount++
            } catch (e: Exception) {
                failedCount++
            }
        }
        debugEntities.clear()
        debugVisualizationCreated = false
        debugVisualizationVisible = false
        if (failedCount > 0) {
            Log.w(TAG, "Cleared debug visualization: $destroyedCount destroyed, $failedCount failed")
        } else {
            Log.d(TAG, "Cleared debug visualization: $destroyedCount entities")
        }
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
