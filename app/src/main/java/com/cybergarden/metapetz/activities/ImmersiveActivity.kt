package com.cybergarden.metapetz.activities

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.cybergarden.metapetz.BuildConfig
import com.cybergarden.metapetz.R
import com.cybergarden.metapetz.ecs.PetLocomotion
import com.cybergarden.metapetz.model.PetData
import com.cybergarden.metapetz.services.FirebaseManager
import com.cybergarden.metapetz.ui.OptionsPanel
import com.cybergarden.metapetz.ui.PetInfoPanel
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_WIDTH
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.physics.Physics
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.physics.PhysicsState
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.runtime.SceneAudioAsset
import com.meta.spatial.runtime.SceneAudioPlayer
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Box
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Audio
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.isdk.IsdkFeature
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkInputListenerSystem
import com.meta.spatial.runtime.PointerEventType
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKLoadDeviceResult
import com.meta.spatial.mruk.MRUKStartEnvrionmentRaycasterResult
import com.meta.spatial.mruk.MRUKAnchor
import com.meta.spatial.mruk.MRUKRoom
import com.meta.spatial.mruk.MRUKLabel
import com.meta.spatial.mruk.AnchorProceduralMesh
import com.meta.spatial.mruk.AnchorProceduralMeshConfig
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Vector4
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.Hittable
import com.meta.spatial.toolkit.Animated
import com.meta.spatial.toolkit.PlaybackState
import com.meta.spatial.toolkit.PlaybackType
import com.meta.spatial.core.Query
import com.meta.spatial.core.Vector2
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.core.Color4
import com.cybergarden.metapetz.model.PetColors
import com.cybergarden.metapetz.utils.GlbColorizer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ImmersiveActivity : AppSystemActivity() {
  private val activityScope = CoroutineScope(Dispatchers.Main)

  private var currentPet by mutableStateOf<String?>(null)
  private var currentPetData by mutableStateOf<PetData?>(null)
  private var currentPetEntity: Entity? = null
  private var spinningJob: Job? = null
  private var panelEntity: Entity? = null
  private var boneEntity: Entity? = null
  private var boneHand: Entity? = null
  private var boneSampleJob: Job? = null
  private var lastBonePos: Vector3? = null
  private var lastBoneSampleNs: Long = 0L

  // Room boundary colliders
  private val roomColliderEntities = mutableListOf<Entity>()

  // AnchorProceduralMesh for automatic physics colliders on walls/floor/ceiling
  // This is the proper Meta SDK way to create room boundary colliders
  private var procMeshSpawner: AnchorProceduralMesh? = null

  // Audio
  private val boneSound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/bone_hit.wav")
  }
  private val boneSoundPlayer: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, boneSound)
  }
  private val boneFastSound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/bone_hit.wav") // reuse placeholder
  }
  private val boneFastPlayer: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, boneFastSound)
  }

  // Pet locomotion system for point-to-move functionality
  private val petLocomotion: PetLocomotion by lazy {
    PetLocomotion(activityScope, floorY = 0f, walkSpeed = 0.5f).apply {
      onWalkStart = {
        // Stop spinning/dancing when walking starts
        spinningJob?.cancel()
        spinningJob = null
        Log.d(TAG, "Pet started walking")
      }
      onWalkEnd = {
        // Pet stays at destination with wag animation (handled in PetLocomotion)
        Log.d(TAG, "Pet finished walking - staying at destination")
      }
    }
  }

  private fun floorHeight(): Float {
    // Use LOCAL_FLOOR origin to keep drops on floor plane
    return 0f
  }

  // Firebase Manager for cloud persistence (lazy so available during registerPanels)
  val firebaseManager: FirebaseManager by lazy {
    FirebaseManager(applicationContext).also { it.updateLastActive() }
  }

  companion object {
    private const val TAG = "ImmersiveActivity"
    private const val SCENE_PERMISSION = "com.oculus.permission.USE_SCENE"
    private const val SCENE_PERMISSION_REQUEST = 1002
  }

  // MRUK Feature for scene-aware raycasting
  private lateinit var mrukFeature: MRUKFeature

  // Pet model file paths in assets
  private val petModels = mapOf(
      "Cat" to "apk:///models/cat.glb",
      "Dog" to "apk:///models/metadog.glb",
      "Bunny" to "apk:///models/bunny.glb",
      "Bird" to "apk:///models/bird.glb",
      "Fish" to "apk:///models/fish.glb",
      "Hamster" to "apk:///models/hamster.glb",
  )

  override fun registerFeatures(): List<SpatialFeature> {
    // Initialize MRUK for scene-aware raycasting
    Log.d(TAG, "=== REGISTERING MRUK FEATURE ===")
    mrukFeature = MRUKFeature(this, systemManager)
    Log.d(TAG, "MRUKFeature created")

    val features =
        mutableListOf<SpatialFeature>(
            PhysicsFeature(spatial),
            VRFeature(this),
            IsdkFeature(this, spatial, systemManager),  // Enable hand tracking and controller interactions
            ComposeFeature(),
            mrukFeature  // Add MRUK for room/scene awareness
        )
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      // Disabled - too noisy in logs
      // features.add(OVRMetricsFeature(this, OVRMetricsDataModel() { numberOfMeshes() }))
      features.add(DataModelInspectorFeature(spatial, this.componentManager))
    }
    return features
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NetworkedAssetLoader.init(
        File(applicationContext.getCacheDir().canonicalPath),
        OkHttpAssetFetcher(),
    )

    // Register grabbable component for ISDK grabbing
    componentManager.registerComponent<IsdkGrabbable>(IsdkGrabbable.Companion)
    checkAndRequestScenePermission()

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    // Register the point-to-move system for pet locomotion
    // Uses MRUK raycastRoom - requires Space Setup to be completed
    systemManager.registerSystem(petLocomotion.createPointingSystem(mrukFeature))
    Log.d(TAG, "Point-to-move system registered with MRUK raycastRoom")

    loadGLXF()
  }

  private var scenePermissionGranted = false

  private fun checkAndRequestScenePermission() {
    Log.d(TAG, "=== CHECKING SCENE PERMISSION ===")
    Log.d(TAG, "SCENE_PERMISSION = $SCENE_PERMISSION")
    val currentPermission = ContextCompat.checkSelfPermission(this, SCENE_PERMISSION)
    Log.d(TAG, "Current permission status: $currentPermission (GRANTED=${PackageManager.PERMISSION_GRANTED}, DENIED=${PackageManager.PERMISSION_DENIED})")

    if (currentPermission != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Permission NOT granted - requesting...")
      ActivityCompat.requestPermissions(this, arrayOf(SCENE_PERMISSION), SCENE_PERMISSION_REQUEST)
    } else {
      Log.d(TAG, "Permission ALREADY granted")
      scenePermissionGranted = true
    }
  }

  private fun loadSceneFromDevice() {
    Log.d(TAG, "Loading scene from device for MRUK raycasting...")

    mrukFeature.loadSceneFromDevice().whenComplete { result: MRUKLoadDeviceResult, error: Throwable? ->
      if (result == MRUKLoadDeviceResult.SUCCESS) {
        Log.d(TAG, "=== MRUK SCENE LOADED SUCCESSFULLY ===")
        val rooms = mrukFeature.rooms
        Log.d(TAG, "Total rooms loaded: ${rooms.size}")

        rooms.forEachIndexed { index, room ->
          Log.d(TAG, "--- Room $index ---")
          Log.d(TAG, "  Anchors count: ${room.anchors.size}")

          // Log each anchor's details
          room.anchors.forEachIndexed { anchorIdx, anchorEntity ->
            try {
              val transform = anchorEntity.tryGetComponent<Transform>()?.transform
              val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>()
              Log.d(TAG, "  Anchor $anchorIdx:")
              Log.d(TAG, "    Entity ID: ${anchorEntity.id}")
              Log.d(TAG, "    UUID: ${mrukAnchor?.uuid}")
              Log.d(TAG, "    Position: ${transform?.t}")
              Log.d(TAG, "    Rotation: ${transform?.q}")
            } catch (e: Exception) {
              Log.e(TAG, "  Anchor $anchorIdx: Error reading - ${e.message}")
            }
          }
        }

        // MRUK wall creation disabled - using simple 3x3 room instead
        Log.d(TAG, "MRUK data loaded but not using it for walls yet")
      } else {
        Log.w(TAG, "Scene load result: $result - user may need to complete Space Setup")
        Log.e(TAG, "MRUK: Failed to load scene - result: $result")
      }
      if (error != null) {
        Log.e(TAG, "Scene load error: ${error.message}", error)
      }
    }
  }

  override fun onSceneReady() {
    super.onSceneReady()
    Log.d(TAG, "=== ON SCENE READY ===")
    Log.d(TAG, "mrukFeature initialized: ${::mrukFeature.isInitialized}")
    Log.d(TAG, "mrukFeature.rooms.size: ${mrukFeature.rooms.size}")
    Log.d(TAG, "scenePermissionGranted: $scenePermissionGranted")

    // MRUK DISABLED FOR DEBUGGING
    // Start the environment raycaster for DEPTH mode (works without Space Setup)
    // Must be called after spatial system is initialized (in onSceneReady)
    // val envRaycasterResult = mrukFeature.startEnvironmentRaycaster()
    // if (envRaycasterResult == MRUKStartEnvrionmentRaycasterResult.SUCCESS) {
    //   Log.d(TAG, "Environment raycaster started successfully - DEPTH mode ready")
    // } else {
    //   Log.w(TAG, "Environment raycaster failed to start: $envRaycasterResult")
    // }

    // Now that OpenXR is initialized, load scene data if permission was granted
    // if (scenePermissionGranted) {
    //   Log.d(TAG, "Loading MRUK scene from onSceneReady (OpenXR ready)")
    //   loadSceneFromDevice()
    // }

    // Enable recentering when user holds Meta button
    scene.setReferenceSpace(com.meta.spatial.runtime.ReferenceSpace.LOCAL_FLOOR)

    scene.setViewOrigin(0.0f, 0.0f, 2.0f, 180.0f)

    // Configure bright lighting to illuminate the pet
    scene.setLightingEnvironment(
        ambientColor = Vector3(1.5f, 1.5f, 1.5f),  // Bright ambient light
        sunColor = Vector3(3.0f, 3.0f, 3.0f),      // Bright directional light
        sunDirection = -Vector3(0f, -1f, 1f),      // Light from above and front
        environmentIntensity = 1.0f
    )

    // Get the WebviewPanel entity to attach pet to it
    panelEntity = Query.where { has(Panel.id) }
        .eval()
        .firstOrNull {
          it.getComponent<Panel>().panelRegistrationId == R.id.ui_example
        }

    // Add a simple physics floor to catch dynamic objects (like the bone)
    createPhysicsFloor()

    // Walls are now created on-demand via "Setup Room" button

    // Grabbable handler: keeps physics while held and restores on release
    val inputSystem = systemManager.tryFindSystem<IsdkInputListenerSystem>()
    if (inputSystem == null) {
      Log.w(TAG, "IsdkInputListenerSystem not found - bone grabbing disabled")
    } else {
      Log.d(
          TAG,
          "PointerEventType ids hover=${PointerEventType.Hover.id} unhover=${PointerEventType.Unhover.id} select=${PointerEventType.Select.id} unselect=${PointerEventType.Unselect.id}"
      )
      inputSystem.setInputListener(
          object : InputListener {
            private val selectCounts = mutableMapOf<Long, Int>()
            private val savedStates = mutableMapOf<Entity, PhysicsState>()

            private fun isGrabbablePhysics(ent: Entity): Boolean =
                ent.hasComponent<IsdkGrabbable>() && ent.hasComponent<Physics>()

            override fun onPointerEvent(
                receiver: SceneObject,
                hitInfo: HitInfo,
                type: Int,
                sourceOfInput: Entity,
                scrollInfo: Vector2,
                semanticType: Int,
            ) {
              val ent = receiver.entity ?: return
              if (!isGrabbablePhysics(ent)) return

              Log.d(TAG, "Bone pointer event type=$type semantic=$semanticType id=${ent.id}")

              val isSelectType = type == PointerEventType.Select.id || type == 4 // some builds report 4
              val isUnselectType = type == PointerEventType.Unselect.id || type == 0 // some builds report 0

              when {
                type == PointerEventType.Hover.id -> {
                  val scale = ent.getComponent<Scale>().scale
                  val newScale = Vector3(scale.x + 0.02f, scale.y + 0.02f, scale.z + 0.02f)
                  ent.setComponent(Scale(newScale))
                  Log.d(TAG, "Bone hover enter id=${ent.id}")
                }
                type == PointerEventType.Unhover.id -> {
                  val scale = ent.getComponent<Scale>().scale
                  val newScale = Vector3(scale.x - 0.02f, scale.y - 0.02f, scale.z - 0.02f)
                  ent.setComponent(Scale(newScale))
                  Log.d(TAG, "Bone hover exit id=${ent.id}")
                }
                isSelectType -> {
                  val count = (selectCounts[ent.id] ?: 0) + 1
                  selectCounts[ent.id] = count
                  if (count == 1) {
                    // Only allow our bone to be grabbed
                    if (boneEntity != null && ent != boneEntity) {
                      Log.d(TAG, "Ignoring grab on non-bone entity id=${ent.id}")
                      return
                    } else {
                      boneEntity = ent
                    }

                    val physics = ent.getComponent<Physics>()
                    if (!savedStates.contains(ent)) {
                      savedStates[ent] =
                          if (physics.state == PhysicsState.KINEMATIC) PhysicsState.DYNAMIC
                          else physics.state
                    }
                    physics.state = PhysicsState.KINEMATIC
                    ent.setComponent(physics)

                    // Parent to hand for stable grab
                    val handEntity = if (sourceOfInput == Entity.nullEntity()) getRightHandEntity() else sourceOfInput
                    attachToHand(ent, handEntity)
                    startBoneSampling(ent, handEntity)
                    Log.d(TAG, "Bone grabbed id=${ent.id}, hand=${handEntity?.id}")
                  }
                }
                isUnselectType -> {
                  val count = (selectCounts[ent.id] ?: 0) - 1
                  selectCounts[ent.id] = count
                  if (count <= 0) {
                    val physics = ent.getComponent<Physics>()
                    physics.state = savedStates.remove(ent) ?: PhysicsState.DYNAMIC
                    ent.setComponent(physics)
                    detachFromHand(ent)
                    selectCounts.remove(ent.id)
                    if (ent == boneEntity) boneEntity = null
                    stopBoneSampling()

                    Log.d(TAG, "Bone released id=${ent.id}")
                  }
                }
              }
            }
          }
      )
      Log.d(TAG, "IsdkInputListenerSystem listener registered for bone grab")
    }
  }

  /**
   * Get the head entity from PlayerBodyAttachmentSystem
   */
  private fun getHeadEntity(): Entity? {
    return systemManager
        .tryFindSystem<PlayerBodyAttachmentSystem>()
        ?.tryGetLocalPlayerAvatarBody()
        ?.head
  }

  /**
   * Get the left hand entity from PlayerBodyAttachmentSystem
   */
  private fun getLeftHandEntity(): Entity? {
    return systemManager
        .tryFindSystem<PlayerBodyAttachmentSystem>()
        ?.tryGetLocalPlayerAvatarBody()
        ?.leftHand
  }

  /**
   * Get the right hand entity from PlayerBodyAttachmentSystem
   */
  private fun getRightHandEntity(): Entity? {
    return systemManager
        .tryFindSystem<PlayerBodyAttachmentSystem>()
        ?.tryGetLocalPlayerAvatarBody()
        ?.rightHand
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Log.d(TAG, "=== PERMISSION RESULT ===")
    Log.d(TAG, "requestCode: $requestCode, permissions: ${permissions.toList()}, results: ${grantResults.toList()}")

    when (requestCode) {
      SCENE_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(TAG, "Scene permission GRANTED")
          scenePermissionGranted = true
          Log.d(TAG, "Calling loadSceneFromDevice()...")
          loadSceneFromDevice()
        } else {
          Log.e(TAG, "Scene permission DENIED - grantResults: ${grantResults.toList()}")
        }
      }
    }
  }

  fun selectDemoPet(petData: PetData) {
    // Use Dog model for demo pet, but with the actual PetData from Firebase
    Log.d(TAG, "selectDemoPet called with: name=${petData.name}, desc=${petData.description}, level=${petData.level}, xp=${petData.xp}")
    currentPet = "Dog"
    currentPetData = petData
    Log.d(TAG, "currentPetData set to: ${currentPetData?.name}")
    spawnPetModel("Dog", petData.colors)
  }

  fun selectPet(petName: String) {
    currentPet = petName
    currentPetData = PetData(
      name = petName,
      description = "Your $petName companion",
      level = 1,
      xp = 0
    )
    val colors = currentPetData?.colors ?: PetColors()
    spawnPetModel(petName, colors)
  }

  private fun spawnPetModel(petName: String, colors: PetColors) {
    // Cancel previous spinning animation
    spinningJob?.cancel()
    spinningJob = null

    // Remove previous pet if it exists
    currentPetEntity?.destroy()
    currentPetEntity = null

    // Load the new pet model from assets
    val modelPath = petModels[petName]
    if (modelPath != null) {
      activityScope.launch {
        try {
          // Create entity with GLB mesh as a child of the panel
          // Initial rotation: 180° around X-axis to flip upright
          val xFlipRadians = PI.toFloat()
          val initialRotation = Quaternion(
              kotlin.math.sin(xFlipRadians / 2).toFloat(),
              0f,
              0f,
              kotlin.math.cos(xFlipRadians / 2).toFloat()
          )

          // Get the panel entity to attach to
          val panel = panelEntity ?: Entity.nullEntity()

          // Colorize the GLB file with the pet's colors
          val assetPath = modelPath.removePrefix("apk:///")
          val colorHash = colors.hashCode().toString(16)
          val outputFileName = "${petName}_${colorHash}.glb"

          val meshUri = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val colorizedFile = GlbColorizer.colorizeGlb(
              this@ImmersiveActivity,
              assetPath,
              colors,
              outputFileName
            )
            if (colorizedFile != null) {
              // Use file:// URI for the colorized model
              "file://${colorizedFile.absolutePath}"
            } else {
              // Fallback to original model
              Log.w(TAG, "Failed to colorize model, using original")
              modelPath
            }
          }

          Log.d(TAG, "Loading pet model from: $meshUri")

          // Create pet entity with basic components
          currentPetEntity = Entity.create(
              listOf(
                  Mesh(meshUri.toUri()),
                  Transform(
                      Pose(
                          // Local position: centered directly in front of panel
                          Vector3(0f, 0.2f, 0.2f),
                          initialRotation
                      )
                  ),
                  Scale(Vector3(0.2f, 0.2f, 0.2f)),
                  TransformParent(panel),
                  // Play wag animation at spawn
                  Animated(
                      startTime = System.currentTimeMillis(),
                      playbackState = PlaybackState.PLAYING,
                      playbackType = PlaybackType.LOOP,
                      track = PetLocomotion.ANIM_WAG
                  )
              )
          )

          // Update locomotion system with new pet entity
          petLocomotion.setPetEntity(currentPetEntity, panel)

          // Start spinning animation
          startSpinning()
        } catch (e: Exception) {
          Log.e(TAG, "Error loading pet model: ${e.message}", e)
        }
      }
    }
  }

  private fun startSpinning() {
    val entity = currentPetEntity ?: return

    spinningJob = activityScope.launch {
      var angle = 0f
      val rotationSpeed = 0.5f // Degrees per frame (slow rotation)
      var time = 0f // Time tracker for animations

      while (isActive) {
        try {
          // Update rotation around Y axis
          angle = (angle + rotationSpeed) % 360f
          val yRotRadians = angle * PI.toFloat() / 180f

          // Quaternion for Y-axis rotation (spinning)
          val qy = Quaternion(0f, kotlin.math.sin(yRotRadians / 2), 0f, kotlin.math.cos(yRotRadians / 2))

          // All pets need 180° X-axis flip to orient upright
          val xFlipRadians = PI.toFloat()
          val qx = Quaternion(kotlin.math.sin(xFlipRadians / 2).toFloat(), 0f, 0f, kotlin.math.cos(xFlipRadians / 2).toFloat())
          // Combine rotations: first flip, then spin (qy * qx)
          val rotation = multiplyQuaternions(qy, qx)

          // Dancing animation: bouncing up and down with side-to-side sway
          time += 0.016f // Increment time (16ms frame time)
          val bounceHeight = kotlin.math.sin(time * 3f) * 0.03f // Bounce up/down
          val sideToSide = kotlin.math.sin(time * 2f) * 0.02f // Sway left/right

          // Base position + dancing movements
          val baseY = 0.2f
          val baseX = 0f
          val dancing = Vector3(
              baseX + sideToSide, // X: side-to-side sway
              baseY + bounceHeight, // Y: bouncing motion
              0.2f // Z: fixed distance in front
          )

          // Update entity transform with dancing position
          entity.setComponent(
              Transform(
                  Pose(
                      dancing,
                      rotation
                  )
              )
          )

          delay(16) // ~60 FPS
        } catch (e: Exception) {
          // Entity might have been destroyed, stop spinning
          break
        }
      }
    }
  }

  // Helper function to multiply two quaternions
  private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
    return Quaternion(
        q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y,
        q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x,
        q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w,
        q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
    )
  }

  /**
   * Convert a hex color string (e.g., "#3A8DFF") to Color4
   */
  private fun hexToColor4(hex: String): Color4 {
    val color = android.graphics.Color.parseColor(hex)
    return Color4(
        android.graphics.Color.red(color) / 255f,
        android.graphics.Color.green(color) / 255f,
        android.graphics.Color.blue(color) / 255f,
        1f
    )
  }

  /**
   * Setup room walls using AnchorProceduralMesh - the proper Meta SDK way.
   * This automatically creates physics colliders that are properly anchored to the room.
   * The colliders stay fixed in world space because they're parented to room anchors.
   */
  private fun setupRoom() {
    Log.d(TAG, "=== SETUP ROOM CALLED ===")

    // Clear any existing walls
    if (roomColliderEntities.isNotEmpty()) {
      Log.d(TAG, "Clearing ${roomColliderEntities.size} existing room colliders")
      roomColliderEntities.forEach { it.destroy() }
      roomColliderEntities.clear()
    }

    // Create a single test wall - 1x1 meter, right in front at world origin
    createTestWall()
  }

  /**
   * Create a single 1x1 meter test wall at a fixed world position.
   * This wall has NO parent - it should stay fixed in world space.
   */
  private fun createTestWall() {
    Log.d(TAG, "=== CREATING TEST WALL ===")

    // Create a 1x1 meter wall, 1 meter in front of world origin, at eye height
    val wallPos = Vector3(0f, 1f, -1f)  // 1m high, 1m in front
    val wallSize = Vector3(1f, 1f, 0.1f)  // 1m x 1m, 10cm thick (thicker for better collision)

    Log.d(TAG, "Creating test wall at WORLD position: $wallPos")
    Log.d(TAG, "Wall size: $wallSize")

    // Create physics collider - matching the working floor pattern exactly
    // Box + Transform + Physics in same order as createPhysicsFloor()
    val physicsEntity = Entity.create(
        listOf(
            Box(wallSize),
            Transform(Pose(wallPos, Quaternion())),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = wallSize
              restitution = 0.8f
            }
        )
    )
    roomColliderEntities.add(physicsEntity)
    Log.d(TAG, "Physics wall created with entity id: ${physicsEntity.id}")

    // Create visible mesh separately (so we can see where the wall is)
    val visualEntity = Entity.create(
        listOf(
            Mesh(android.net.Uri.parse("mesh://box")),
            Box(wallSize),
            Transform(Pose(wallPos, Quaternion())),
            Material().apply {
              baseColor = Color4(0f, 1f, 0f, 0.5f)  // Semi-transparent green
              unlit = true
            }
        )
    )
    roomColliderEntities.add(visualEntity)
    Log.d(TAG, "Visual wall created with entity id: ${visualEntity.id}")
  }

  // TODO: MRUK-based wall creation - not used yet
  // private fun createProceduralMeshColliders() { ... }

  /**
   * Spawn a physics-enabled bone near the player.
   * Randomizes a lateral offset and places it slightly forward of the head, clamped to floor.
   */
  private fun spawnBoneToy() {
    Log.d(TAG, "SpawnBoneToy invoked")
    val hand = getRightHandEntity() ?: getLeftHandEntity() ?: getHeadEntity()
    val handTransform = hand?.getComponent<Transform>()?.transform

    if (handTransform == null) {
      Log.w(TAG, "Cannot spawn bone - no hand/head transform")
      return
    }

    try {
      // Only one bone at a time
      boneSampleJob?.cancel()
      boneEntity?.destroy()
      boneEntity = null
      boneHand = null
      lastBonePos = null
      lastBoneSampleNs = 0L

      // Spawn slightly forward in local hand space and parent to hand so it starts equipped
      val localOffset = Vector3(0f, 0f, 0.08f)
      val pose = Pose(localOffset, Quaternion())

      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri(), hittable = MeshCollision.LineTest),
              Transform(pose),
              Scale(Vector3(0.2f, 0.2f, 0.2f)),
              Visible(true),
              Hittable(MeshCollision.LineTest),
              // Box collider for touch grab support (ray + touch grabs)
              Box(Vector3(0.2f, 0.06f, 0.35f)),
              IsdkGrabbable(),
              Physics().apply {
                state = PhysicsState.KINEMATIC // start attached to hand; will be set to dynamic on release
                shape = "box"
                dimensions = Vector3(0.2f, 0.06f, 0.35f)
                restitution = 0.2f
              }
          )
      )
      boneEntity = entity

      attachToHand(entity, hand)

      boneSoundPlayer.play(handTransform.t, 0.8f, false)

      Log.d(TAG, "Spawned bone toy attached to hand=${hand.id} (physDims=Vector3(0.2,0.06,0.35))")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn bone toy: ${e.message}", e)
    }
  }

  private fun rotateVector(q: Quaternion, v: Vector3): Vector3 {
    val qx = q.x
    val qy = q.y
    val qz = q.z
    val qw = q.w

    val ix = qw * v.x + qy * v.z - qz * v.y
    val iy = qw * v.y + qz * v.x - qx * v.z
    val iz = qw * v.z + qx * v.y - qy * v.x
    val iw = -qx * v.x - qy * v.y - qz * v.z

    return Vector3(
        ix * qw + iw * -qx + iy * -qz - iz * -qy,
        iy * qw + iw * -qy + iz * -qx - ix * -qz,
        iz * qw + iw * -qz + ix * -qy - iy * -qx
    )
  }
  private fun vectorDiff(a: Vector3, b: Vector3): Vector3 = Vector3(a.x - b.x, a.y - b.y, a.z - b.z)
  private fun vectorScale(v: Vector3, s: Float): Vector3 = Vector3(v.x * s, v.y * s, v.z * s)
  private fun vectorLength(v: Vector3): Float = kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        // Registering Pet Info Panel (shows stats when pet is selected)
        ComposeViewPanelRegistration(
            R.id.ui_example,
            composeViewCreator = { _, context ->
              ComposeView(context).apply {
                setContent {
                  if (currentPetData != null) {
                    PetInfoPanel(
                        petData = currentPetData!!,
                        onClose = {
                          currentPet = null
                          currentPetData = null
                          petLocomotion.setPetEntity(null, null) // Clear locomotion
                          currentPetEntity?.destroy()
                          currentPetEntity = null
                        }
                    )
                  } else {
                    // Show welcome message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                      Text(
                          text = "Select a pet to get started! (v4)",
                          fontSize = 24.sp,
                          color = Color.White,
                          textAlign = androidx.compose.ui.text.style.TextAlign.Center
                      )
                    }
                  }
                }
              }
            },
            settingsCreator = { _ -> UIPanelSettings() },
        ),
        // Registering a Compose panel for pet selection
        ComposeViewPanelRegistration(
            R.id.options_panel,
            composeViewCreator = { _, context ->
              ComposeView(context).apply {
                setContent {
                  OptionsPanel(
                      onSelectPet = ::selectPet,
                      onSelectDemoPet = ::selectDemoPet,
                      onSpawnBone = ::spawnBoneToy,
                      onSetupRoom = ::setupRoom,
                      firebaseManager = firebaseManager
                  )
                }
              }
            },
            settingsCreator = {
              UIPanelSettings(
                  shape =
                      QuadShapeOptions(width = OPTIONS_PANEL_WIDTH, height = OPTIONS_PANEL_HEIGHT),
                  style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                  display = DpPerMeterDisplayOptions(),
              )
            },
        ),
    )
  }

  override fun onSpatialShutdown() {
    spinningJob?.cancel()
    petLocomotion.cleanup()
    mrukFeature.stopEnvironmentRaycaster()
    // Clean up AnchorProceduralMesh spawner
    procMeshSpawner?.destroy()
    procMeshSpawner = null
    // Clean up any manual room boundary colliders
    roomColliderEntities.forEach { it.destroy() }
    roomColliderEntities.clear()
    super.onSpatialShutdown()
  }

  private fun loadGLXF(): Job {
    return activityScope.launch {
      glXFManager.inflateGLXF(
          "apk:///scenes/Composition.glxf".toUri(),
          keyName = "example_key_name",
      )
    }
  }
  private fun createPhysicsFloor() {
    val floorY = floorHeight() - 0.02f
    Entity.create(
        listOf(
            Box(Vector3(10f, 0.04f, 10f)), // large thin box as collider
            Transform(Pose(Vector3(0f, floorY, 0f))),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = Vector3(10f, 0.04f, 10f)
            }
        )
    )
  }

  /**
   * Create a quaternion for rotation around the Y axis (yaw).
   */
  private fun quaternionFromYaw(yaw: Float): Quaternion {
    val half = yaw * 0.5f
    val s = sin(half)
    val c = cos(half)
    // Rotation around Y axis: (0, sin(half), 0, cos(half))
    return Quaternion(0f, s, 0f, c)
  }

  /**
   * Transform a point from local space to world space using a Pose.
   */
  private fun transformPoint(pose: Pose, localPoint: Vector3): Vector3 {
    // Apply rotation then translation: worldPoint = rotation * localPoint + translation
    val rotated = rotateVector(pose.q, localPoint)
    return Vector3(
        rotated.x + pose.t.x,
        rotated.y + pose.t.y,
        rotated.z + pose.t.z
    )
  }

  private fun createWall(position: Vector3, dimensions: Vector3, name: String) {
    val components = mutableListOf(
        Box(dimensions),
        Transform(Pose(position)),
        Physics().apply {
          state = PhysicsState.KINEMATIC
          shape = "box"
          this.dimensions = dimensions
          restitution = 0.8f  // Bouncy walls
        }
    )

    // Add visible mesh with semi-transparent material for debugging
    if (DEBUG_SHOW_COLLIDERS) {
      components.add(Mesh(android.net.Uri.parse("mesh://box")))
      components.add(Scale(dimensions))  // Scale the unit cube to wall dimensions
      components.add(Material().apply {
        baseColor = Color4(0.2f, 0.5f, 1f, 0.3f)  // Semi-transparent blue
        unlit = true
      })
    }

    val wallEntity = Entity.create(components)
    roomColliderEntities.add(wallEntity)
    Log.d(TAG, "Created $name at $position with size $dimensions")
  }

  // Debug flag - set to true to see collider boxes
  private val DEBUG_SHOW_COLLIDERS = true
  private fun attachToHand(ent: Entity, hand: Entity?) {
    val parent = hand ?: return
    if (parent == Entity.nullEntity()) return
    ent.setComponent(TransformParent(parent))
  }

  private fun detachFromHand(ent: Entity) {
    ent.setComponent(TransformParent(Entity.nullEntity()))
  }

  private fun startBoneSampling(ent: Entity, hand: Entity?) {
    if (hand == null || hand == Entity.nullEntity()) return
    boneHand = hand
    lastBonePos = hand.tryGetComponent<Transform>()?.transform?.t
    lastBoneSampleNs = System.nanoTime()

    boneSampleJob?.cancel()
    boneSampleJob = activityScope.launch {
      while (isActive) {
        try {
          val h = boneHand
          if (h == null || h == Entity.nullEntity()) {
            delay(30)
            continue
          }
          val handPose = h.tryGetComponent<Transform>()?.transform
          val prevPos = lastBonePos
          val prevTime = lastBoneSampleNs
          val now = System.nanoTime()
          if (handPose != null && prevPos != null && prevTime != 0L) {
            val dt = (now - prevTime) / 1_000_000_000.0f
            if (dt > 0f) {
              val delta = vectorDiff(handPose.t, prevPos)
              val vel = vectorScale(delta, 1f / dt)
              Log.d(TAG, "Bone velocity sample dt=$dt vel=$vel")
              val speed = vectorLength(vel)
              if (speed > 1f) {
                // Capture current world position and velocity before destroying
                val worldPos = handPose.t
                val worldRot = handPose.q
                // Multiply velocity for more distance and add upward boost
                val multiplier = 3f
                val upwardBoost = 1.5f
                val throwVel = Vector3(vel.x * multiplier, vel.y * multiplier + upwardBoost, vel.z * multiplier)

                // Stop sampling first
                stopBoneSampling()

                // Destroy the held bone completely
                boneEntity?.destroy()
                boneEntity = null

                // Play sound at release position
                boneFastPlayer.play(worldPos, 0.6f, false)

                // Spawn a fresh dynamic bone at the release position with velocity
                spawnThrownBone(worldPos, worldRot, throwVel)

                Log.d(TAG, "Bone thrown at speed=$speed vel=$throwVel pos=$worldPos")
                break
              }
              lastBonePos = handPose.t
              lastBoneSampleNs = now
            }
          } else {
            lastBonePos = handPose?.t ?: lastBonePos
            lastBoneSampleNs = if (handPose != null) now else lastBoneSampleNs
          }
          delay(30)
        } catch (e: Exception) {
          Log.e(TAG, "Bone sample error: ${e.message}")
          delay(30)
        }
      }
    }
  }

  /**
   * Spawn a new bone at given world position/rotation with initial velocity (for throwing).
   * This bone is NOT attached to the hand - it's fully dynamic from the start.
   */
  private fun spawnThrownBone(position: Vector3, rotation: Quaternion, velocity: Vector3) {
    try {
      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri(), hittable = MeshCollision.LineTest),
              Transform(Pose(position, rotation)),
              Scale(Vector3(0.2f, 0.2f, 0.2f)),
              Visible(true),
              Hittable(MeshCollision.LineTest),
              Box(Vector3(0.2f, 0.06f, 0.35f)),
              Physics().apply {
                state = PhysicsState.DYNAMIC
                shape = "box"
                dimensions = Vector3(0.2f, 0.06f, 0.35f)
                restitution = 0.4f
                linearVelocity = velocity
              }
          )
      )
      // Don't track this as boneEntity - it's a free-flying bone
      Log.d(TAG, "Spawned thrown bone at pos=$position vel=$velocity id=${entity.id}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn thrown bone: ${e.message}", e)
    }
  }

  private fun stopBoneSampling() {
    boneSampleJob?.cancel()
    boneSampleJob = null
    boneHand = null
    lastBonePos = null
    lastBoneSampleNs = 0L
  }

}
