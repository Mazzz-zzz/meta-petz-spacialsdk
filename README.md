# MetaPetz - Mixed Reality Pet Companion for Meta Quest

**[www.metapetz.com](https://www.metapetz.com)**

A Tamagotchi-style virtual pet that lives in your real space. Your pet understands your room, walks around furniture, responds to gestures, and saves progress to the cloud.

## What You Can Do

- **Care for your pet** - Feed, play, and heal to keep stats up. Stats decay over time like a real Tamagotchi.
- **Customize appearance** - Change coat, eye, and snout colors. Add hats (party, wizard, spinning).
- **Play fetch** - Throw a bone with your hand, pet brings it back.
- **Use gestures** - Clap to get attention, raise hand for sit, pet them for XP.
- **Take photos** - Capture passthrough photos with your pet in the scene.
- **Scan QR codes** - Look up pet IDs via QR scanning.

## Attention & Command System

Gesture-based attention system with activity states reflecting real pet behavior:

**Activity States:**

- **IDLE** - Pet wanders around autonomously
- **FACING_PLAYER** - Pet has attention, continuously smooth-tracks your head
- **WALKING** - Pet walking to commanded position (maintains attention)
- **SITTING** - Pet sitting on command (5s duration, awards 2% XP)
- **FETCHING** - Pet fetching a thrown bone (awards 5% XP on completion)

**Gestures:**

| Gesture       | Action                              |
| ------------- | ----------------------------------- |
| Clap hands    | Get pet's attention (whistle plays) |
| Raise hand    | Sit command (5s, awards XP)         |
| Point & click | Walk to location                    |
| Pet with hand | Petting animation + XP              |
| Throw bone    | Fetch game (awards XP on return)    |
| Wrist button  | Spawn bone from wrist UI            |

## Tech Stack

| Component          | Technology                                        |
| ------------------ | ------------------------------------------------- |
| Platform           | Meta Quest 3/3S (HorizonOS)                       |
| SDK                | Meta Spatial SDK                                  |
| Room Understanding | MRUK                                              |
| Language           | Kotlin                                            |
| UI                 | Jetpack Compose + Meta Spatial UISet              |
| 3D Models          | glTF/GLB with runtime colorization                |
| Backend            | Firebase Realtime Database                        |
| AI                 | Replicate API (background removal, 3D generation) |
| QR Scanning        | ZXing                                             |
| Camera             | CameraX + HorizonOS headset camera                |

## Architecture

```
app/src/main/java/com/cybergarden/metapetz/
├── activities/
│   └── ImmersiveActivity.kt      # Main VR activity, entity management
├── ecs/
│   ├── NavGrid.kt                # 2D pathfinding grid (15cm resolution)
│   ├── PetLocomotion.kt          # Movement, jumping, animations
│   ├── ClapDetector.kt           # Clap gesture detection
│   ├── PettingDetector.kt        # Hand petting detection
│   ├── WristAttachedSystem.kt    # Wrist UI positioning
│   └── QRCodeSystem.kt           # QR detection via MRUK
├── model/
│   └── Pet.kt                    # Pet data, colors, accessories
├── services/
│   ├── FirebaseManager.kt        # Cloud persistence
│   ├── ReplicateManager.kt       # AI image/3D API
│   ├── PhotoCaptureManager.kt    # Headset camera
│   └── QRScannerManager.kt       # QR code scanning
├── utils/
│   ├── GlbColorizer.kt           # Runtime GLB recoloring
│   └── MathUtils.kt              # Quaternion helpers
└── ui/
    ├── OptionsPanelLayout.kt     # Main Compose UI panel
    ├── layouts/                  # PetInfo, PetSelection, PhotoCapture
    ├── components/               # PetCard, StatBar, CareActionButton
    └── theme/                    # Theme.kt, Constants.kt

app/src/shaders/
├── solidColor.vert/frag          # Transparent solid color
├── edgeOnly.vert/frag            # Wireframe edges
└── furnitureOccluder.vert/frag   # Furniture occlusion
```

## Technical Highlights

### NavGrid Pathfinding System

A 2D navigation grid (15cm resolution) for intelligent pet movement:

- **Floor polygon extraction** from MRUK anchors with point-in-polygon ray casting
- **Furniture blocking** via `MRUKVolume`/`MRUKPlane` footprints with padding
- **Wall blocking** using pending queue pattern (handles async anchor loading)
- **Flood fill optimization** keeps only largest connected walkable region

### Room Understanding (MRUK Integration)

- **Real-time room scanning** - Loads room data from Meta's Mixed Reality Utility Kit
- **Anchor processing** - Handles floor, walls, ceiling, and furniture anchors
- **Physics colliders** - Creates invisible wall colliders for pet containment

### Physics-Based Interactions

- **Bone throwing** - Velocity-based release with physics simulation
- **Hand tracking** - Bone attaches to hand, releases on fast movement (>1 m/s)
- **Boosted trajectories** - 3x velocity multiplier with upward boost for satisfying arcs

### Cloud Persistence (Firebase)

- **Device-based isolation** - Each device gets unique ID, data stored under `/pets/{deviceId}`
- **Pet stats sync** - Hunger, happiness, health, colors, accessories persist
- **XP/Level system** - Progression tracking with automatic cloud backup
- **Offline support** - Firebase SDK handles connectivity, syncs when back online

### Runtime GLB Colorization

Custom GLB parser for dynamic pet color customization without external tools:

- **Binary parsing** - Reads GLB header, JSON chunk, and BIN chunk
- **Material injection** - Modifies `baseColorFactor` in PBR materials
- **Name-based mapping** - Maps material names (coat, eye, snout) to hex colors
- **Cache output** - Writes modified GLB with proper 4-byte alignment

### Custom Shaders

GLSL shaders for room mesh visualization:

- **Solid Color** (`solidColor.vert/frag`) - Unlit transparent rendering with `customColor` uniform
- **Edge-Only** (`edgeOnly.vert/frag`) - UV-based wireframe effect, configurable thickness in meters
- **Furniture Occluder** (`furnitureOccluder.vert/frag`) - Furniture visibility occlusion

```glsl
// Edge detection (edgeOnly.frag)
vec2 edgeDist = min(uv, 1.0 - uv);
vec2 thicknessUV = thicknessMeters * fwidth(uv) / fwidth(worldPosition);
if (edgeDist.x >= thicknessUV.x && edgeDist.y >= thicknessUV.y) discard;
```

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

## Building

```bash
./gradlew assembleDebug      # Build
./gradlew installDebug       # Install on Quest
```

### Prerequisites

- Android Studio
- Meta Quest with room setup completed

### Configuration

Add to `local.properties`:

```properties
REPLICATE_API_TOKEN=your_token_here
```

Firebase setup:

1. Create project at [Firebase Console](https://console.firebase.google.com)
2. Add Android app: `com.cybergarden.metapetz`
3. Download `google-services.json` to `app/`
4. Enable Realtime Database

## Documentation

See `/docs/` for detailed guides:

- `LOCOMOTION_API.md` - Pet movement system
- `navgrid.md` - Pathfinding implementation
- `WRIST_ATTACHED_CONTROLS.md` - Wrist UI reference


**[www.metapetz.com](https://www.metapetz.com)** | **Built for Meta Quest** | **Powered by Meta Spatial SDK**
