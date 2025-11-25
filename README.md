# MetaPetz - Virtual Pet Companion for Meta Quest

A Tamagotchi-inspired mixed reality pet care game built with Meta Spatial SDK for Meta Quest. Take care of your virtual pet by feeding, playing, cleaning, and letting them rest while they float and dance in your real-world environment.

## Features

### 🎮 Gamification & Pet Care
- **Tamagotchi-Style Stats System** - Four dynamic stats that decay over time:
  - Hunger (decreases 5% every 5 seconds)
  - Happiness (decreases 3% every 5 seconds)
  - Health (stays stable unless affected)
  - Energy (decreases 4% every 5 seconds)

- **Interactive Care Actions**:
  - **Feed** - Restores hunger +30%, earns 10 XP
  - **Play** - Increases happiness +30%, uses energy -10%, earns 15 XP
  - **Clean** - Improves health +20%, earns 10 XP
  - **Rest** - Restores energy +40%, earns 5 XP

- **Progression System**:
  - Pet level tracking with XP display
  - Visual progress indicators for XP to next level
  - Color-coded stat bars (Green > Amber > Red)

- **Dynamic Mood System** - Pet mood changes based on overall stats:
  - "Feeling Great! 😊" (>80% stats)
  - "Doing Well 🙂" (60-80%)
  - "Needs Attention 😐" (40-60%)
  - "Not Happy 😟" (20-40%)
  - "Critical! 😢" (<20%)

### 🐾 Pet Selection
Choose from 6 adorable 3D pets:
- **Cat** 🐱 - Curious
- **Dog** 🐶 - Loyal
- **Bunny** 🐰 - Gentle
- **Bird** 🐦 - Energetic
- **Fish** 🐠 - Calm
- **Hamster** 🐹 - Playful

### ✨ Custom Pet Creation
Create your own pet using the Quest passthrough camera:
- **Take a Photo** - Capture anything through the Quest's passthrough camera
- **AI Background Removal** - Powered by Replicate's bria/remove-background model
- **Instant Pet** - Your custom creation becomes a 3D pet in MR

### 🎨 3D Visualization
- Animated 3D pet models that spin and dance
- Glowing pedestal for ambient lighting
- Smooth animations with bouncing and swaying
- Mixed reality passthrough integration

### 📱 Dual Panel Interface
- **Pet Selection Panel** - Scrollable grid of pet cards
- **Pet Info Panel** - Real-time stats, care actions, and pet display

### ☁️ Cloud Save with Firebase
- **Persistent Pet Stats** - Your pet's progress saves automatically to the cloud
- **Cross-Session Persistence** - Stats are restored when you return to the app
- **Auto-Save** - Stats save on every care action and during stat decay
- **Unique User ID** - Each device gets a unique identifier for data isolation

## Technology Stack

- **Platform**: Meta Quest (Mixed Reality)
- **SDK**: Meta Spatial SDK
- **Backend**: Firebase Realtime Database
- **AI**: Replicate API (bria/remove-background model)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Meta Spatial UISet
- **3D Models**: glTF/GLB format
- **Architecture**: Entity Component System (ECS)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/cybergarden/metapetz/
│   │   ├── ImmersiveActivity.kt       # Main activity, 3D rendering
│   │   ├── OptionsPanelLayout.kt      # UI components, gamification logic
│   │   ├── FirebaseManager.kt         # Cloud persistence with Firebase
│   │   ├── ReplicateManager.kt        # AI background removal API
│   │   └── PhotoCaptureManager.kt     # Quest passthrough camera capture
│   ├── assets/
│   │   ├── models/                    # 3D pet models and pedestals
│   │   └── scenes/                    # Meta Spatial Editor scenes
│   └── res/
│       └── layout/                    # XML layouts
├── google-services.json               # Firebase configuration
local.properties                       # API keys (gitignored)
```

## Building & Running

### Prerequisites
- Android Studio Arctic Fox or later
- Meta Quest device or simulator
- [Meta Spatial Editor](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-editor-overview)

### Build Steps
1. Clone the repository
2. Open project in Android Studio
3. Set up API keys (see below)
4. Connect Meta Quest device or start simulator
5. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

### API Keys Setup
To enable the custom pet creation feature, you need to add your Replicate API token to `local.properties`:

```properties
# Add this to your local.properties file (already gitignored)
REPLICATE_API_TOKEN=your_replicate_api_token_here
```

**Getting a Replicate API Token:**
1. Sign up at [replicate.com](https://replicate.com)
2. Go to Account Settings → API Tokens
3. Create a new token and copy it to `local.properties`

> **Note:** The `local.properties` file is gitignored and will not be committed. Each developer needs to add their own API token.

### Development
Edit the scene using Meta Spatial Editor:
```
app/scenes/Main.metaspatial
```

### Firebase Setup
The app uses Firebase Realtime Database for cloud persistence. To set up your own Firebase project:

1. **Create Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com)
   - Create a new project or use existing one

2. **Add Android App**
   - In Project Settings, add an Android app
   - Package name: `com.cybergarden.metapetz`
   - Download `google-services.json` and place in `app/` folder

3. **Enable Realtime Database**
   - Go to Build → Realtime Database
   - Create database (choose your region)
   - Set rules for development:
     ```json
     {
       "rules": {
         ".read": true,
         ".write": true
       }
     }
     ```

4. **Update Database URL** (if using non-US region)
   - In `FirebaseManager.kt`, update the database URL:
     ```kotlin
     private val db = FirebaseDatabase.getInstance("https://YOUR-PROJECT-ID.REGION.firebasedatabase.app")
     ```

### Firebase Data Structure
```
users/
  {userId}/
    createdAt: timestamp
    lastActive: timestamp
    deviceId: string
    pets/
      {petName}/
        hunger: float
        happiness: float
        health: float
        energy: float
        level: int
        xp: int
        xpToNextLevel: int
        lastUpdated: timestamp
```

## Gameplay Loop

1. **Select a Pet** - Choose from 6 different pets in the options panel
2. **Watch Your Pet** - See your pet appear in 3D with spinning/dancing animations
3. **Monitor Stats** - Keep an eye on the stat bars in the info panel
4. **Take Care** - Use care action buttons to maintain your pet's wellbeing
5. **Level Up** - Earn XP through care actions to increase your pet's level
6. **Switch Pets** - Return to selection and choose a different pet anytime

## Key Implementation Details

### State Management
- Uses Compose `mutableStateOf` for reactive UI updates
- Coroutine-based stat decay system
- Real-time stat updates on user actions

### 3D Rendering
- Entity Component System architecture
- Transform, Scale, Mesh components for pet models
- Animated component for built-in GLB animations
- Custom spinning animation with quaternion rotations

### Panel System
- ComposeViewPanelRegistration for reactive Compose panels
- LaunchedEffect for time-based stat decay
- Scrollable columns for long content

## Meta Spatial SDK Gradle Plugin

This project includes the Spatial SDK Gradle Plugin for:
- [Spatial Editor integration](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-editor#use-the-spatial-sdk-gradle-plugin)
- Build-related features like [custom shaders](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-custom-shaders)

Meta collects telemetry data from the Spatial SDK Gradle Plugin to help improve MPT Products. See the [Supplemental Meta Platforms Technologies Privacy Policy](https://www.meta.com/legal/privacy-policy/) for details.

## Credits

Built with:
- Meta Spatial SDK
- Meta Spatial UISet components
- Firebase Realtime Database
- Jetpack Compose
- Kotlin Coroutines

## License

The Meta Spatial SDK Templates package is multi-licensed.

The majority of the project is licensed under the [Zero-Clause BSD License](https://github.com/meta-quest/Meta-Spatial-SDK-Templates/tree/main/LICENSE).

The [Meta Platform Technologies SDK license](https://developer.oculus.com/licenses/oculussdk/) applies to the Meta Spatial SDK and supporting material, and to the assets used in this project. The [MPT SDK license](https://github.com/meta-quest/Meta-Spatial-SDK-Templates/tree/main/MixedRealityTemplate/app/src/main/assets/LICENSE.md) can be found in the asset folder.

All supporting materials in `app/src/main/assets` including 3D models, videos, sounds, and others are licensed under the [MPT SDK license](https://developer.oculus.com/licenses/oculussdk/).

---

**Made for Meta Quest** | **Powered by Meta Spatial SDK**
