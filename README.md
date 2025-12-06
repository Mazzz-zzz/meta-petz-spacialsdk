# MetaPetz - Mixed Reality Pet Companion for Meta Quest

**[www.metapetz.com](https://www.metapetz.com)**

A mixed reality pet companion built with Meta Spatial SDK for Meta Quest. Features advanced spatial awareness with real-time room understanding, dynamic pathfinding, and physics-based interactions.

## Features

### Customizable Pets

Create unique virtual companions with full color customization:

- **Multiple pet types** - Dogs, cats, and more with animated GLB models
- **Color picker UI** - Customize coat, eye, and snout colors via hex values
- **Runtime recoloring** - Colors applied by modifying GLB materials on-the-fly
- **Persistent customization** - Pet appearance saved to Firebase, restored on launch

### Tamagotchi-Style Care

- **Stat system** - Hunger, happiness, and health decay over time
- **Care actions** - Feed, play, and heal to maintain pet wellbeing
- **XP progression** - Earn experience from interactions, level up your pet
- **Visual feedback** - Pet animations respond to stat levels

## Technical Highlights

### NavGrid Pathfinding System

A 2D navigation grid (15cm resolution) for intelligent pet movement:

- **Floor polygon extraction** from MRUK anchors with point-in-polygon ray casting
- **Furniture blocking** via `MRUKVolume`/`MRUKPlane` footprints with padding
- **Wall blocking** using pending queue pattern (handles async anchor loading)
- **Flood fill optimization** keeps only largest connected walkable region
- **Lag-free debug visualization** using `Visible` component toggle

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

Real-time database for pet state persistence across sessions:

- **Device-based isolation** - Each device gets unique ID, data stored under `/pets/{deviceId}`
- **Pet stats sync** - Hunger, happiness, health values persist and restore on launch
- **XP/Level system** - Progression tracking with automatic cloud backup
- **Offline support** - Firebase SDK handles connectivity, syncs when back online

Database structure:
```json
{
  "pets": {
    "device_abc123": {
      "name": "Buddy",
      "hunger": 0.75,
      "happiness": 0.9,
      "health": 1.0,
      "xp": 1250,
      "level": 5
    }
  }
}
```

### Runtime GLB Colorization

Custom GLB parser for dynamic pet color customization without external tools:

- **Binary parsing** - Reads GLB header, JSON chunk, and BIN chunk
- **Material injection** - Modifies `baseColorFactor` in PBR materials
- **Name-based mapping** - Maps material names (coat, eye, snout) to hex colors
- **Cache output** - Writes modified GLB with proper 4-byte alignment

### Custom Transparent Shaders

GLSL shaders for room mesh visualization:

- **Solid Color** (`solidColor.vert/frag`) - Unlit transparent rendering with `customColor` uniform
- **Edge-Only** (`edgeOnly.vert/frag`) - UV-based wireframe effect, configurable thickness in meters

```glsl
// Edge detection (edgeOnly.frag)
vec2 edgeDist = min(uv, 1.0 - uv);
vec2 thicknessUV = thicknessMeters * fwidth(uv) / fwidth(worldPosition);
if (edgeDist.x >= thicknessUV.x && edgeDist.y >= thicknessUV.y) discard;
```

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
├── utils/
│   └── GlbColorizer.kt         # Runtime GLB material color modification
└── ui/
    └── OptionsPanelLayout.kt   # Compose UI panels

app/src/shaders/
├── solidColor.vert/frag        # Unlit transparent solid color shader
└── edgeOnly.vert/frag          # UV-based edge wireframe shader
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
4. Enable Realtime Database with these rules:
```json
{
  "rules": {
    "pets": {
      "$deviceId": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```
5. No authentication required - data isolated by device ID

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

**[www.metapetz.com](https://www.metapetz.com)** | **Built for Meta Quest** | **Powered by Meta Spatial SDK**
