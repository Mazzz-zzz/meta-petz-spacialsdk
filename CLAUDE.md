# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MetaPetz is a Tamagotchi-inspired mixed reality pet companion app for Meta Quest, built with Meta Spatial SDK. The app features 3D animated pets that float in your environment, with a gamified stat system requiring regular care actions.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean build

# Install on connected device
./gradlew installDebug

# Compile Kotlin only (faster iteration)
./gradlew :app:compileDebugKotlin
./gradlew :app:compileReleaseKotlin
```

## Architecture

### Dual-Panel System Architecture

The app uses a **two-panel architecture** where UI and 3D content are separated:

1. **Main Panel** (`R.id.ui_example`)
   - Shows Pet Info Panel when pet is selected
   - Uses ComposeViewPanelRegistration with reactive state
   - Displays stats, care actions, and pet status

2. **Options Panel** (`R.id.options_panel`)
   - Always shows Pet Selection screen
   - Independent scrollable grid of pet cards
   - Does not change when pet is selected

**Critical**: The 3D pet entity is attached to `panelEntity` (the main panel), NOT the options panel.

### State Management Pattern

**ReactiveState for UI Updates**: `currentPet` in `ImmersiveActivity.kt` MUST be declared with `mutableStateOf`:

```kotlin
private var currentPet by mutableStateOf<String?>(null)
```

This ensures Compose panels recompose when pet selection changes. Do NOT use regular `var` or the UI won't update.

### Entity Component System (ECS)

The app uses Meta Spatial SDK's ECS pattern:

**Pet Entity Creation**:
```kotlin
Entity.create(
    listOf(
        Mesh("apk:///models/pet.glb".toUri()),
        Transform(Pose(position, rotation)),
        Scale(Vector3(0.2f, 0.2f, 0.2f)),
        TransformParent(panelEntity),  // Critical: attach to panel
        Animated(...)  // Built-in GLB animations
    )
)
```

**Custom Animations**: The spinning/dancing animation uses:
- Coroutine-based update loop in `startSpinning()`
- Quaternion multiplication for 3D rotations
- Transform component updates every frame (~60 FPS)

### Gamification System

**Stat Decay Mechanism**:
- Runs in `LaunchedEffect(petName)` coroutine
- Uses `delay(5000)` for 5-second intervals
- Only active when pet is selected
- Stats must be clamped: `max(0f, stat - decayRate)`

**Care Actions**:
- Update stats using `petStats.copy(hunger = newValue.coerceIn(0f, 1f))`
- Award XP on each action
- Trigger recomposition via state update

## File Organization

- `ImmersiveActivity.kt` - Main activity, owns 3D rendering, pet models, animation loops
- `OptionsPanelLayout.kt` - All Compose UI (selection screen, info panel, stat system)
- `app/src/main/assets/models/` - GLB 3D models (cat.glb, dog.glb, etc.)
- `app/scenes/` - Meta Spatial Editor scenes (Main.metaspatial)

## Common Patterns

### Adding a New Pet

1. Add GLB model to `app/src/main/assets/models/`
2. Update `petModels` map in `ImmersiveActivity.kt`
3. Add pet to list in `OptionsPanel` composable
4. Rebuild to compress GLB assets

### Modifying Stats

Stats are in `PetStats` data class:
- Values: 0.0f to 1.0f (percentage)
- Decay rates in `LaunchedEffect` block
- Care action effects in button `onClick` handlers

### Panel Registration

Panels are registered in `registerPanels()`:
- Use `ComposeViewPanelRegistration` for Compose UI
- Main panel checks `currentPet` state conditionally
- Options panel always shows selection screen

## Meta Spatial SDK Specifics

**Asset Loading**: Use `apk:///` URI scheme for bundled assets:
```kotlin
Mesh("apk:///models/pet.glb".toUri())
```

**Passthrough Mode**: Enabled in `onCreate()`:
```kotlin
scene.enablePassthrough(true)
```

**Panel Attachment**: Get panel entity in `onSceneReady()`:
```kotlin
panelEntity = Query.where { has(Panel.id) }
    .eval()
    .firstOrNull { it.getComponent<Panel>().panelRegistrationId == R.id.ui_example }
```

**Lighting**: Scene lighting configured in `onSceneReady()` with `scene.setLightingEnvironment()`.

## Dependencies

Key libraries used:
- Meta Spatial SDK (ECS, 3D rendering, panels)
- Meta Spatial UISet (Compose components: PrimaryCard, PrimaryButton, etc.)
- Jetpack Compose (UI framework)
- Kotlin Coroutines (async operations, animations)

## Common Issues

**Panel Not Updating**: Ensure `currentPet` uses `mutableStateOf`, not regular `var`.

**Pet Not Appearing**: Check `panelEntity` is obtained in `onSceneReady()`, not `onCreate()`.

**Model Loading Errors**: Verify GLB files are in `assets/models/` and paths use `apk:///models/` prefix.

**Stat Decay Not Working**: Ensure `LaunchedEffect` key is `petName`, not a constant.
