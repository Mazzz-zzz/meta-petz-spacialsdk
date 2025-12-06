# MetaPetz - Mixed Reality Pet Companion for Meta Quest

A mixed reality pet companion built with Meta Spatial SDK for Meta Quest. Features advanced spatial awareness with real-time room understanding, dynamic pathfinding, and physics-based interactions.

## Technical Highlights

### NavGrid Pathfinding System

A 2D navigation grid system for intelligent pet movement that respects room boundaries and furniture:

#### Grid Creation
- **15cm cell resolution** - Optimal balance between accuracy and performance
- **Floor polygon extraction** - Creates walkable area from MRUK floor anchor bounds
- **Point-in-polygon testing** - Accurate cell classification using ray-casting algorithm

#### Intelligent Blocking

**Furniture Blocking:**
- Extracts footprints from `MRUKVolume` (3D) or `MRUKPlane` (2D) components
- Transforms local bounds to world space using absolute transforms
- Applies 15cm padding for pet clearance
- Handles wall-mounted items (skips furniture >1.5m above floor)

**Wall Blocking (Key Innovation):**
- **Pending queue pattern** - Walls often load before floor; queue stores wall data until NavGrid exists
- **Point-based blocking** - Every 15cm along wall length, blocks a 15cm diameter area
- **Solves AABB limitation** - Polygon blocking fails outside grid bounds; point-based works everywhere
- **Overlapping coverage** - Ensures no gaps in wall blocking

#### Flood Fill Optimization
- After all blocking, identifies connected walkable regions via BFS flood-fill
- Keeps only the largest connected region
- Eliminates unreachable pockets behind furniture or outside walls
- Reduces pathfinding search space significantly

#### Lag-Free Debug Visualization
- Creates all debug entities once at room load (hidden by default)
- Toggle uses `Visible` component - instant on/off, no entity recreation
- Color gradient: Green (walkable) → Yellow (near obstacles) → Red (blocked)

### Room Understanding (MRUK Integration)

- **Real-time room scanning** - Loads room data from Meta's Mixed Reality Utility Kit
- **Anchor processing** - Handles floor, walls, ceiling, and furniture anchors
- **Physics colliders** - Creates invisible wall colliders for pet containment
- **Room mesh visualization** - Toggle to show/hide room boundaries

### Physics-Based Interactions

- **Bone throwing** - Velocity-based release with physics simulation
- **Hand tracking** - Bone attaches to hand, releases on fast movement (>1 m/s)
- **Boosted trajectories** - 3x velocity multiplier with upward boost for satisfying arcs

### Cloud Persistence (Firebase)

- **Real-time sync** - Pet stats persist across sessions
- **XP/Level system** - Progression tracking with cloud backup
- **Unique device IDs** - Data isolation per device

## Architecture

```
app/src/main/java/com/cybergarden/metapetz/
├── activities/
│   └── ImmersiveActivity.kt    # Main activity, room processing, entity management
├── ecs/
│   ├── NavGrid.kt              # 2D navigation grid with blocking & pathfinding
│   ├── PetLocomotion.kt        # Pet movement with floor polygon constraints
│   └── ClapDetector.kt         # Audio-based gesture detection
├── services/
│   └── FirebaseManager.kt      # Cloud persistence
└── ui/
    └── OptionsPanelLayout.kt   # Compose UI panels
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| Platform | Meta Quest 3/3S |
| SDK | Meta Spatial SDK |
| Room Understanding | MRUK (Mixed Reality Utility Kit) |
| Language | Kotlin |
| UI | Jetpack Compose + Meta Spatial UISet |
| 3D Models | glTF/GLB |
| Architecture | Entity Component System (ECS) |
| Backend | Firebase Realtime Database |
| AI | Replicate API (background removal) |

## Building

```bash
# Debug build
./gradlew assembleDebug

# Install on connected Quest
./gradlew installDebug

# Fast Kotlin-only compile
./gradlew :app:compileDebugKotlin
```

### Prerequisites
- Android Studio
- Meta Quest device with room setup completed
- Meta Spatial Editor (for scene editing)

### API Keys
Add to `local.properties`:
```properties
REPLICATE_API_TOKEN=your_token_here
```

### Firebase Setup
1. Create project at [Firebase Console](https://console.firebase.google.com)
2. Add Android app with package `com.cybergarden.metapetz`
3. Download `google-services.json` to `app/` folder
4. Enable Realtime Database

## Key Algorithms

### Point-in-Polygon (Ray Casting)
```kotlin
fun contains(px: Float, pz: Float): Boolean {
    var inside = false
    var j = vertices.size - 1
    for (i in vertices.indices) {
        if ((vertices[i].z > pz) != (vertices[j].z > pz) &&
            px < (vertices[j].x - vertices[i].x) * (pz - vertices[i].z) /
                 (vertices[j].z - vertices[i].z) + vertices[i].x) {
            inside = !inside
        }
        j = i
    }
    return inside
}
```

### Polygon Expansion (Padding)
- Calculates signed area to detect winding order (CW vs CCW)
- Computes outward normals for each edge
- Moves vertices along angle bisectors
- Scales by `padding / cos(half-angle)` to maintain edge distance

### Flood Fill (BFS)
- Iterative queue-based to avoid stack overflow
- 4-connected neighbor checking
- Returns list of all connected walkable cells

## License

Multi-licensed under [Zero-Clause BSD](LICENSE) and [Meta Platform Technologies SDK License](https://developer.oculus.com/licenses/oculussdk/).

---

**Built for Meta Quest** | **Powered by Meta Spatial SDK**
