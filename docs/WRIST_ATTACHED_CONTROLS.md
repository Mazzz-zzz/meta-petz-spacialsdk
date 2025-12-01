# Wrist-Attached Controls Implementation Guide for MetaPetz

## Summary of Showcase Examples

| Showcase | UI Approach | Wrist-Attached? |
|----------|-------------|-----------------|
| **Meta Spatial Scanner** | Wrist-attached buttons + spawned panels | ✅ **Yes - only example** |
| **Focus** | Floating toolbar (placed in front of user) | ❌ No |
| **Media View** | Gallery panels + head-relative positioning | ❌ No |
| **Geo Voyage** | Grabbable globe + pinnable markers | ❌ No |

**Conclusion:** Meta Spatial Scanner is the **only official showcase** with wrist-attached controls. This is the proven, tested implementation we should follow.

---

## Complete Implementation Reference

### Source Files (Tested, Production-Ready)

All files are from:
```
/examples/Meta-Spatial-SDK-Samples/Showcases/meta_spatial_scanner/
```

| File | Path | Purpose |
|------|------|---------|
| **WristAttached.xml** | `app/src/main/components/WristAttached.xml` | Component schema |
| **WristAttachedSystem.kt** | `app/src/main/java/.../ecs/WristAttachedSystem.kt` | ECS system |
| **MathUtils.kt** | `app/src/main/java/.../utils/MathUtils.kt` | Quaternion helpers |
| **Main.scene** | `app/scenes/Composition/Main.scene` | Scene with wrist entities |
| **MainActivity.kt** | `app/src/main/java/.../activities/MainActivity.kt` | Registration & handlers |
| **ui_camera_controls_view.xml** | `app/src/main/res/layout/` | Button layout |
| **ui_help_button_view.xml** | `app/src/main/res/layout/` | Button layout |

---

## How It Works

### Architecture Overview

1. **WristAttached Component** - Custom ECS component with position, rotation, hand side, and faceUser properties
2. **WristAttachedSystem** - Runs every frame, updates panel position based on hand transform
3. **Smart Visibility** - Panels only visible when user looks at palm facing them
4. **Touch/Pointer Input** - Standard Android click handlers work via Spatial SDK panel routing

### Visibility Logic

The system uses two dot product checks:
- `lookingAtHand > 0.85` - User's head forward vs direction to wrist
- `handFacingHead > 0.4` - Palm forward vs direction to head

Both must be true for the panel to be visible.

---

## 1. Component Schema (WristAttached.xml)

```xml
<ComponentSchema packageName="com.meta.pixelandtexel.scanner">
    <Enum name="HandSide">
        <EnumValue value="LEFT" />
        <EnumValue value="RIGHT" />
    </Enum>
    <Component name="WristAttached">
        <Description>A component which positions and orients the entity on the user's wrist.</Description>
        <Vector3Attribute
            name="position"
            defaultValue="0f, 0f, 0f"
            description="The position offset to apply to the object, relative to the hand."
        />
        <Vector3Attribute
            name="rotation"
            defaultValue="0f, 0f, 0f"
            description="The rotation offset in euler angles to apply to the object, relative to the hand."
        />
        <EnumAttribute name="side" defaultValue="HandSide.LEFT" />
        <BooleanAttribute
            name="faceUser"
            defaultValue="false"
            description="Whether or not to orient the entity such that it faces the user (ignores rotation offset)."
        />
    </Component>
</ComponentSchema>
```

---

## 2. ECS System (WristAttachedSystem.kt)

```kotlin
package com.meta.pixelandtexel.scanner.ecs

import com.meta.pixelandtexel.scanner.HandSide
import com.meta.pixelandtexel.scanner.WristAttached
import com.meta.pixelandtexel.scanner.utils.MathUtils.fromSequentialPYR
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

/**
 * Manages entities that are designated to be attached to the user's wrists, responsible for
 * updating the position, rotation, and visibility of such entities based on the user's hand and
 * head movements.
 */
class WristAttachedSystem : SystemBase() {
    companion object {
        private const val TAG: String = "WristAttachedSystem"
    }

    private val wristAttachedEntities = mutableListOf<Entity>()

    /**
     * Finds new wrist-attached entities, retrieves the current transforms of the player's head and
     * hands, and then updates the pose and visibility of each tracked wrist-attached entity.
     * Visibility is determined by whether the entity (and by extension, the user's palm) is facing
     * towards the user's head, and if the head is looking at the palm.
     */
    override fun execute() {
        findNewEntities()

        // get our head and hands/controllers transforms
        val playerBody = getAvatarBody()
        if (!playerBody.head.hasComponent<Transform>() ||
            !playerBody.leftHand.hasComponent<Transform>() ||
            !playerBody.rightHand.hasComponent<Transform>()) {
            // Failed to find transform components on avatar body parts; controllers may be
            // disconnected and hands out of view
            return
        }

        val headTransform = playerBody.head.getComponent<Transform>()
        val leftHandTransform = playerBody.leftHand.getComponent<Transform>()
        val rightHandTransform = playerBody.rightHand.getComponent<Transform>()

        // now process existing entities
        for (entity in wristAttachedEntities) {
            val comp = entity.getComponent<WristAttached>()

            val handTransform = when (comp.side) {
                HandSide.LEFT -> leftHandTransform
                HandSide.RIGHT -> rightHandTransform
            }

            // calculate the new pose for the attached entity
            val quatOffset = Quaternion.fromSequentialPYR(
                comp.rotation.x, comp.rotation.y, comp.rotation.z
            )
            val rotation = handTransform.transform.q.times(quatOffset)

            // use the offset rotation as our basis orientation for translation
            val position = handTransform.transform.t + rotation.times(comp.position)

            val pose = Pose(position, if (comp.faceUser) headTransform.transform.q else rotation)
            entity.setComponent(Transform(pose))

            // hide the entity if the palm isn't facing the user's head
            val vHeadFwd = headTransform.transform.forward()
            val vAnchorFwd = rotation.times(Vector3.Forward)
            val vHeadToAnchor = (position - headTransform.transform.t).normalize()

            val lookingAtHand = vHeadFwd.dot(vHeadToAnchor) > 0.85f
            val handFacingHead = vAnchorFwd.dot(vHeadToAnchor) > 0.4f
            entity.setComponent(Visible(lookingAtHand && handFacingHead))
        }
    }

    /**
     * Handles the deletion of an entity from the system, removing the entity from the internal list
     * of tracked wrist-attached entities.
     */
    override fun delete(entity: Entity) {
        super.delete(entity)
        wristAttachedEntities.remove(entity)
    }

    /**
     * Finds new entities that should be managed by this system, querying for local entities that have
     * both WristAttached and Transform components, and adds to the wristAttachedEntities list.
     */
    private fun findNewEntities() {
        val query = Query.where { has(WristAttached.id, Transform.id) and changed(WristAttached.id) }
        for (entity in query.eval()) {
            if (wristAttachedEntities.contains(entity)) {
                continue
            }
            if (!entity.isLocal()) {
                continue
            }
            wristAttachedEntities.add(entity)
        }
    }

    /**
     * Retrieves the AvatarBody component for the local, player-controlled avatar.
     */
    private fun getAvatarBody(): AvatarBody {
        return Query.where { has(AvatarBody.id) }
            .eval()
            .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            .first()
            .getComponent<AvatarBody>()
    }
}
```

---

## 3. Math Utilities (MathUtils.kt)

```kotlin
package com.meta.pixelandtexel.scanner.utils

import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MathUtils {
    /**
     * Constructs a quaternion from an axis-angle representation of a rotation.
     *
     * @param axis The 3D Vector3 axis around which to perform the rotation.
     * @param angleDegrees The angle in degrees of rotation to perform around the axis
     * @return The Quaternion representing the rotation.
     */
    fun Quaternion.Companion.fromAxisAngle(axis: Vector3, angleDegrees: Float): Quaternion {
        val angleRadians = angleDegrees * PI / 180f
        val halfAngle = angleRadians / 2
        val sinHalfAngle = sin(halfAngle).toFloat()

        return Quaternion(
            cos(halfAngle).toFloat(),
            axis.x * sinHalfAngle,
            axis.y * sinHalfAngle,
            axis.z * sinHalfAngle,
        ).normalize()
    }

    /**
     * Creates a Quaternion representing a rotation in 3D space that is the combination of the
     * supplied rotations in degrees around the pitch, yaw, and roll axes, in that order.
     *
     * @param pitchDeg The angle in degrees to apply to the rotation around the x axis.
     * @param yawDeg The angle in degrees to apply to the rotation around the y axis.
     * @param rollDeg The angle in degrees to apply to the rotation around the z axis.
     * @return The Quaternion representing the rotation around the axes in sequential order.
     */
    fun Quaternion.Companion.fromSequentialPYR(
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
    ): Quaternion {
        return Quaternion.fromAxisAngle(Vector3.Right, pitchDeg)
            .times(Quaternion.fromAxisAngle(Vector3.Up, yawDeg))
            .times(Quaternion.fromAxisAngle(Vector3.Forward, rollDeg))
            .normalize()
    }
}
```

---

## 4. Scene Configuration (Main.scene excerpt)

```yaml
entities:
  com.meta.models.Scene:
    - components:
        com.meta.components.Name:
          {}
        com.meta.components.Scene:
          nodes:
            - ref:CameraControlsPanel
            - ref:HelpButtonPanel
      tag: Scene
  com.meta.models.SceneNode:
    # CameraControlsPanel - upper wrist button
    - components:
        com.meta.components.Animatable:
          {}
        com.meta.components.Name:
          name: CameraControlsPanel
        com.meta.components.PointerNodeInverseComponent:
          {}
        com.meta.components.SceneNode:
          rotation.format: Euler
          scale:
            - 0.04
            - 0.04
            - 1
          componentVersion: 1
        com.meta.pixelandtexel.scanner.WristAttached:
          position:
            - 0.08
            - -0.04
            - -0.02
          rotation:
            - -30
            - -55
            - -90
          faceUser: true
        com.meta.spatial.toolkit.Panel:
          panel: "@layout/ui_camera_controls_view"
        com.meta.spatial.toolkit.PanelDimensions:
          {}
        com.meta.spatial.toolkit.Visible:
          isVisible: false
      tag: CameraControlsPanel

    # HelpButtonPanel - lower wrist button
    - components:
        com.meta.components.Animatable:
          {}
        com.meta.components.Name:
          name: HelpButtonPanel
        com.meta.components.PointerNodeInverseComponent:
          {}
        com.meta.components.SceneNode:
          rotation.format: Euler
          scale:
            - 0.04
            - 0.04
            - 1
          componentVersion: 1
        com.meta.pixelandtexel.scanner.WristAttached:
          faceUser: true
          rotation:
            - -30
            - -55
            - -90
          position:
            - 0.07
            - -0.08
            - -0.03
        com.meta.spatial.toolkit.Panel:
          panel: "@layout/ui_help_button_view"
        com.meta.spatial.toolkit.PanelDimensions:
          {}
        com.meta.spatial.toolkit.Visible:
          isVisible: false
      tag: HelpButtonPanel
```

---

## 5. Panel Registration (MainActivity.kt)

### Registration in onCreate()

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ... other setup ...

    // Register wrist-attached component and system
    componentManager.registerComponent<WristAttached>(WristAttached.Companion, SendRate.DEFAULT)
    systemManager.registerSystem(WristAttachedSystem())
}
```

### Panel Registration

```kotlin
override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.ui_help_button_view) {
            config {
                themeResourceId = R.style.PanelAppThemeTransparent
                includeGlass = false
                layoutWidthInDp = 80f
                width = 0.04f
                height = 0.04f
                layerConfig = LayerConfig()
                layerBlendType = PanelShapeLayerBlendType.MASKED
                enableLayerFeatheredEdge = true
            }
            panel {
                val helpBtn = rootView?.findViewById<ImageButton>(R.id.help_btn)
                    ?: throw RuntimeException("Missing help button")

                helpBtn.setOnClickListener {
                    // Handle button press - show help panel, etc.
                    tipManager.showHelpPanel()
                }
            }
        },
        PanelRegistration(R.layout.ui_camera_controls_view) {
            config {
                themeResourceId = R.style.PanelAppThemeTransparent
                includeGlass = false
                layoutWidthInDp = 80f
                width = 0.04f
                height = 0.04f
                layerConfig = LayerConfig()
                layerBlendType = PanelShapeLayerBlendType.MASKED
                enableLayerFeatheredEdge = true
            }
            panel {
                cameraControlsBtn = rootView?.findViewById(R.id.camera_play_btn)
                    ?: throw RuntimeException("Missing camera play/pause button")

                cameraControlsBtn?.setOnClickListener {
                    // Toggle camera scanning on/off
                    when (objectDetectionFeature.status) {
                        CameraStatus.PAUSED -> startScanning()
                        CameraStatus.SCANNING -> stopScanning()
                    }
                }
            }
        },
        // ... other panels ...
    )
}
```

---

## 6. Button Layout XML

### ui_help_button_view.xml

```xml
<?xml version="1.0" encoding="utf-8" ?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageButton
        android:id="@+id/help_btn"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@drawable/ic_question_24"
        android:contentDescription="@string/help_button_description"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintDimensionRatio="1:1"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### ui_camera_controls_view.xml

```xml
<?xml version="1.0" encoding="utf-8" ?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ImageButton
        android:id="@+id/camera_play_btn"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="@drawable/ic_play_circle_24"
        android:contentDescription="@string/camera_controls_button_description"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintDimensionRatio="1:1"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## Position Reference for Left Wrist

| Button | position | rotation | Notes |
|--------|----------|----------|-------|
| Upper (camera) | `[0.08, -0.04, -0.02]` | `[-30, -55, -90]` | Closer to wrist |
| Lower (help) | `[0.07, -0.08, -0.03]` | `[-30, -55, -90]` | Below upper button |

### Position Explained

- `x = 0.07-0.08` → Offset toward thumb side (meters)
- `y = -0.04 to -0.08` → Below palm center, negative = down (meters)
- `z = -0.02 to -0.03` → Slight offset toward user (meters)

### Rotation Explained (Euler angles in degrees)

- Pitch `-30°` → Tilt panel toward user's view
- Yaw `-55°` → Rotate toward center of view
- Roll `-90°` → Align with wrist orientation

---

## MetaPetz Implementation Plan

### Files to Create

1. **`app/src/main/components/WristAttached.xml`**
   - Copy from scanner, change package to `com.cybergarden.metapetz`

2. **`app/src/main/java/com/cybergarden/metapetz/ecs/WristAttachedSystem.kt`**
   - Copy and adapt imports

3. **`app/src/main/java/com/cybergarden/metapetz/utils/MathUtils.kt`**
   - Copy quaternion helpers

4. **`app/src/main/res/layout/ui_wrist_pet_btn.xml`**
   - Pet selection button

5. **`app/src/main/res/layout/ui_wrist_care_btn.xml`**
   - Care actions button

6. **`app/src/main/res/layout/ui_wrist_camera_btn.xml`**
   - Photo capture button

### Files to Modify

1. **`ImmersiveActivity.kt`**
   - Register component/system in `onCreate()`
   - Add panel registrations in `registerPanels()`

2. **`app/scenes/Composition/Main.scene`**
   - Add wrist-attached panel entities

3. **`res/values/ids.xml`**
   - Add panel IDs for wrist buttons

### Proposed Button Layout for MetaPetz

| Button | Position | Function |
|--------|----------|----------|
| Pet Selection | `[0.08, -0.04, -0.02]` | Opens pet grid |
| Care Actions | `[0.07, -0.08, -0.03]` | Opens stats (if pet selected) |
| Camera | `[0.06, -0.12, -0.04]` | Opens photo capture |

---

## Alternative: Focus-Style Floating Toolbar

If wrist-attachment proves problematic, Focus demonstrates a simpler approach:

```kotlin
// Place toolbar in front of user at fixed distance
fun placeInFront(entity: Entity?, offset: Vector3 = Vector3(0f)) {
    val headPose = getHeadPose()
    val distanceFromUser = 0.7f
    var newPos = headPose.t + headPose.q * Vector3.Forward * distanceFromUser
    newPos.y = headPose.t.y - 0.35f  // Lower for toolbar

    var newRot = Quaternion.lookRotation(newPos - headPose.t)
    entity?.setComponent(Transform(Pose(newPos, newRot)))
}

fun getHeadPose(): Pose {
    val head = Query.where { has(AvatarAttachment.id) }
        .eval()
        .filter { it.isLocal() && it.getComponent<AvatarAttachment>().type == "head" }
        .first()
    return head.getComponent<Transform>().transform
}
```

This is less immersive but simpler to implement.

---

## Key Configuration Details

### Panel Config for Wrist Buttons

| Property | Value | Purpose |
|----------|-------|---------|
| `width` | `0.04f` | 4cm physical width |
| `height` | `0.04f` | 4cm physical height |
| `layoutWidthInDp` | `80f` | Android layout width |
| `themeResourceId` | `R.style.PanelAppThemeTransparent` | Transparent background |
| `includeGlass` | `false` | No glass effect |
| `layerBlendType` | `MASKED` | Proper alpha handling |
| `enableLayerFeatheredEdge` | `true` | Soft edges |

### Visibility Thresholds

| Check | Threshold | Purpose |
|-------|-----------|---------|
| `lookingAtHand` | `> 0.85` | Head must face toward hand |
| `handFacingHead` | `> 0.4` | Palm must face toward head |

---

## Recommendation

**Use Meta Spatial Scanner's wrist-attached approach** because:

1. ✅ It's the only officially tested implementation
2. ✅ More immersive and accessible than floating panels
3. ✅ Smart visibility prevents UI clutter
4. ✅ Natural palm-up gesture is intuitive
5. ✅ All code is production-ready and well-documented
6. ✅ Works with both hand tracking and controllers

---

## References

- Source: `/examples/Meta-Spatial-SDK-Samples/Showcases/meta_spatial_scanner/`
- Alternative (floating toolbar): `/examples/Meta-Spatial-SDK-Samples/Showcases/focus/`
- Meta Spatial SDK Docs: https://developers.meta.com/horizon/develop/spatial-sdk
