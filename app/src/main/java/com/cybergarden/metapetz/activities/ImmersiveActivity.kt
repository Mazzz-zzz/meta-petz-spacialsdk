package com.cybergarden.metapetz.activities

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import kotlin.math.sqrt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
import com.cybergarden.metapetz.ecs.ClapDetector
import com.cybergarden.metapetz.ecs.NavGrid
import com.cybergarden.metapetz.ecs.PetLocomotion
import androidx.compose.ui.text.font.FontWeight
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
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.runtime.SceneMaterialAttribute
import com.meta.spatial.runtime.SceneMaterialDataType
import com.meta.spatial.runtime.BlendMode
import com.meta.spatial.runtime.SceneMesh
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.SceneTexture
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
import com.meta.spatial.mruk.MRUKSceneEventListener
import com.meta.spatial.mruk.AnchorProceduralMesh
import com.meta.spatial.mruk.AnchorProceduralMeshConfig
import com.meta.spatial.mruk.MRUKPlane
import java.util.concurrent.CompletableFuture
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
  private var isEnvironmentSetup by mutableStateOf(false)
  private var isDebugGridEnabled by mutableStateOf(false)
  private var spinningJob: Job? = null
  private var panelEntity: Entity? = null
  private var boneEntity: Entity? = null
  private var boneHand: Entity? = null
  private var boneSampleJob: Job? = null
  private var lastBonePos: Vector3? = null
  private var lastBoneSampleNs: Long = 0L

  // Track thrown bones for pickup
  private val thrownBones = mutableListOf<Entity>()
  private val thrownBoneTimes = mutableMapOf<Long, Long>() // Entity ID -> throw timestamp
  private var bonePickupJob: Job? = null
  private val BONE_PICKUP_DISTANCE = 0.15f // Distance in meters to trigger pickup
  private val BONE_PICKUP_COOLDOWN_MS = 800L // Cooldown before bone can be picked up after throw

  // Room boundary colliders
  private val roomColliderEntities = mutableListOf<Entity>()

  // Custom wall material for transparent green walls
  private lateinit var wallMaterial: SceneMaterial

  // MRUK procedural mesh spawner - creates meshes for room anchors automatically
  private var procMeshSpawner: AnchorProceduralMesh? = null

  // MRUK scene event listener
  private var sceneEventListener: MRUKSceneEventListener? = null

  // Edge geometry for room bounds (walls, floors, ceiling)
  private val roomEdgeEntities = mutableListOf<Entity>()
  private lateinit var edgeBoxMaterial: SceneMaterial
  private lateinit var furnitureEdgeMaterial: SceneMaterial

  // Physics colliders for room bounds (walls from room scan)
  private val roomBoundsPhysicsEntities = mutableListOf<Entity>()

  // Navigation grid for pathfinding (avoids furniture)
  private var navGrid: NavGrid? = null

  // Debug visibility toggles (reactive for Compose UI)
  private var isRoomMeshVisible by mutableStateOf(true)

  // Labels that represent room bounds (walls, floor, ceiling)
  private val roomBoundsLabels = setOf(MRUKLabel.WALL_FACE, MRUKLabel.FLOOR, MRUKLabel.CEILING)

  // Labels that represent furniture to block in NavGrid
  private val furnitureLabels = setOf(
      MRUKLabel.TABLE, MRUKLabel.COUCH, MRUKLabel.BED, MRUKLabel.STORAGE,
      MRUKLabel.SCREEN, MRUKLabel.LAMP, MRUKLabel.PLANT, MRUKLabel.OTHER
  )

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

  // Whistle audio for clap attention
  private val whistleSound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/whistle.wav")
  }
  private val whistlePlayer: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, whistleSound)
  }

  // Bark sounds for debug feedback
  private val bark1Sound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/bark1.wav")
  }
  private val bark1Player: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, bark1Sound)
  }
  private val bark2Sound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/bark2.wav")
  }
  private val bark2Player: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, bark2Sound)
  }

  // Bone throw cooldown - prevent immediate re-grab after throwing
  private var boneGrabTimeMs: Long = 0L
  private val BONE_THROW_COOLDOWN_MS = 1500L

  // Attention system - pet pays attention when clapped at
  private var isPetAttentive by mutableStateOf(false)
  private var attentionResumeJob: Job? = null
  private val ATTENTION_TIMEOUT_MS = 5000L

  // XP gain while attention is held (0.01 = 1%, 1.0 = 100% full bar)
  private var xpGainJob: Job? = null
  private val XP_GAIN_PER_TICK = 0.01f  // 1% per tick (stored as 0.01)
  private val XP_GAIN_INTERVAL_MS = 2000L

  // Hand distance for debug UI (updated by clap detector)
  private var handDistance by mutableStateOf(0f)
  private var cumulativeDisplacement by mutableStateOf(0f)

  // Clap detector for calling pet's attention
  private val clapDetector: ClapDetector by lazy {
    ClapDetector(activityScope, systemManager).apply {
      // Play bone sound when cumulative displacement threshold reached
      onClapDetected = {
        val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
        if (headPos != null) {
          boneSoundPlayer.play(headPos, 1.0f, false)
        }
        Log.d(TAG, "CLAP TRIGGERED! Playing bone sound and getting attention")
        callPetAttention()
      }
      // Debug sounds for entering/leaving active range
      onHandsTogether = {
        val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
        if (headPos != null) {
          bark1Player.play(headPos, 1.0f, false)
        }
        Log.d(TAG, "Entered active range")
      }
      onHandsApart = {
        val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
        if (headPos != null) {
          bark2Player.play(headPos, 1.0f, false)
        }
        Log.d(TAG, "Left active range")
      }
    }
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
        // Reset attention timeout - pet keeps attention until timeout
        Log.d(TAG, "Pet finished walking - resetting attention timeout")
        resetAttentionTimeout()
      }
      // Tell PetLocomotion about our attention state
      isAttentive = { isPetAttentive }
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
    const val EDGE_THICKNESS = 0.02f // 2cm edge thickness for room bounds
  }

  // MRUK Feature for scene-aware raycasting
  private lateinit var mrukFeature: MRUKFeature

  // Pet model file paths in assets
  private val petModels = mapOf(
      "Dog" to "apk:///models/metadog.glb",
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

    // NOTE: Do NOT call setReferenceSpace() or setViewOrigin() when using MRUK!
    // MRUK anchors are stored in STAGE reference space. Changing reference space
    // shifts the coordinate system but MRUK anchors don't get transformed, causing misalignment.
    // scene.setReferenceSpace(com.meta.spatial.runtime.ReferenceSpace.LOCAL_FLOOR)  // DISABLED for MRUK

    // NOTE: Do NOT call setViewOrigin() when using MRUK!
    // MRUK anchors are in world space, so any view origin offset breaks alignment.
    // scene.setViewOrigin(0.0f, 0.0f, 2.0f, 180.0f)  // DISABLED for MRUK compatibility

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

    // Initialize custom wall material and mesh creator
    initWallMeshCreator()

    // Walls are now created on-demand via "Setup Room" button

    // Start bone pickup proximity checking
    startBonePickupCheck()

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

    // Start clap detection for calling pet's attention
    clapDetector.start()
    Log.d(TAG, "Clap detector started")

    // Start periodic distance updates for debug UI
    activityScope.launch {
      while (true) {
        handDistance = clapDetector.currentDistance
        cumulativeDisplacement = clapDetector.currentCumulative
        delay(100) // Update 10 times per second
      }
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
      xp = 0f
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

          // Create pet entity with basic components and kinematic collider
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
                  ),
                  // Kinematic collider for pushing bones
                  Box(Vector3(0.2f, 0.2f, 0.2f)),  // Pet-sized box collider
                  Physics().apply {
                      state = PhysicsState.KINEMATIC
                      shape = "box"
                      dimensions = Vector3(0.2f, 0.2f, 0.2f)
                  }
              )
          )

          // Update locomotion system with new pet entity
          petLocomotion.setPetEntity(currentPetEntity, panel)
          petLocomotion.setMrukFeature(mrukFeature)  // Enable collision raycasting
          petLocomotion.setThrownBones(thrownBones)  // Enable bone pushing

          // Set wander area based on current head position
          val headEntity = getHeadEntity()
          val headTransform = headEntity?.tryGetComponent<Transform>()?.transform
          val wanderCenterX = headTransform?.t?.x ?: 0f
          val wanderCenterZ = headTransform?.t?.z ?: 0f
          petLocomotion.setWanderArea(wanderCenterX, wanderCenterZ, 2.0f)

          // Start idle wander mode
          petLocomotion.startIdleWander()

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
  // Room boundary settings
  private val roomHalfSize = 2.5f  // 5m / 2 = 2.5m from center to wall
  private val roomWallHeight = 2.5f
  private val roomWallThickness = 0.1f

  private fun setupRoom() {
    Log.d(TAG, "=== SETUP ROOM (Outside) CALLED ===")

    // Clear all bones when environment is reset
    clearAllBones()

    // Clear any existing manual room walls
    if (roomColliderEntities.isNotEmpty()) {
      Log.d(TAG, "Clearing ${roomColliderEntities.size} existing room colliders")
      roomColliderEntities.forEach { it.destroy() }
      roomColliderEntities.clear()
    }

    // Clear any existing room scan data (mutual exclusivity)
    clearRoomBoundsEdges()

    // Clear old NavGrid and debug visualization
    navGrid?.clearDebugVisualization()
    navGrid = null
    petLocomotion.setNavGrid(null)
    isDebugGridEnabled = false  // Reset checkbox state

    // Destroy procMeshSpawner to remove furniture meshes from room scan
    procMeshSpawner?.destroy()
    procMeshSpawner = null
    Log.d(TAG, "Destroyed procMeshSpawner to clear room scan meshes")

    // Get head position to center the room on the user
    val headEntity = getHeadEntity()
    val headTransform = headEntity?.tryGetComponent<Transform>()?.transform
    val roomCenterX = headTransform?.t?.x ?: 0f
    val roomCenterZ = headTransform?.t?.z ?: 0f

    Log.d(TAG, "Centering room on head position: ($roomCenterX, $roomCenterZ)")

    // Create 5x5 meter room with 4 walls (no ceiling) centered on head
    createRoomWalls(roomCenterX, roomCenterZ)

    // Set floor polygon for pet locomotion (keep pet inside the room)
    petLocomotion.setFloorPolygonFromRect(roomCenterX, roomCenterZ, roomHalfSize, roomHalfSize)

    // Mark environment as set up
    isEnvironmentSetup = true
  }

  /**
   * Create a 5x5 meter room with 4 walls centered on the given position.
   * Each wall has physics collider + visible green mesh.
   * @param centerX X coordinate of room center (from head position)
   * @param centerZ Z coordinate of room center (from head position)
   */
  private fun createRoomWalls(centerX: Float, centerZ: Float) {
    Log.d(TAG, "=== CREATING 5x5m ROOM WALLS centered at ($centerX, $centerZ) ===")

    val floorY = floorHeight()
    val wallCenterY = floorY + roomWallHeight / 2

    // Front wall (negative Z from center)
    createModularWall(
        position = Vector3(centerX, wallCenterY, centerZ - roomHalfSize),
        width = roomHalfSize * 2,
        height = roomWallHeight,
        rotation = 0f,
        name = "FrontWall"
    )

    // Back wall (positive Z from center)
    createModularWall(
        position = Vector3(centerX, wallCenterY, centerZ + roomHalfSize),
        width = roomHalfSize * 2,
        height = roomWallHeight,
        rotation = 0f,
        name = "BackWall"
    )

    // Left wall (negative X from center)
    createModularWall(
        position = Vector3(centerX - roomHalfSize, wallCenterY, centerZ),
        width = roomHalfSize * 2,
        height = roomWallHeight,
        rotation = 90f,
        name = "LeftWall"
    )

    // Right wall (positive X from center)
    createModularWall(
        position = Vector3(centerX + roomHalfSize, wallCenterY, centerZ),
        width = roomHalfSize * 2,
        height = roomWallHeight,
        rotation = 90f,
        name = "RightWall"
    )

    Log.d(TAG, "Created ${roomColliderEntities.size} wall entities (4 walls x 2 = 8 entities)")
  }

  /**
   * Create a single wall with physics collider + visible mesh.
   * @param position Center position of the wall
   * @param width Width of the wall (along its face)
   * @param height Height of the wall
   * @param rotation Y-axis rotation in degrees (0 = facing Z, 90 = facing X)
   * @param name Debug name for logging
   */
  private fun createModularWall(
      position: Vector3,
      width: Float,
      height: Float,
      rotation: Float,
      name: String
  ) {
    // Wall dimensions: width x height x thickness
    val wallSize = Vector3(width, height, roomWallThickness)
    val wallRotation = Quaternion(0f, rotation, 0f)

    Log.d(TAG, "Creating $name at $position, size=$wallSize, rotation=$rotation°")

    // Physics collider (invisible)
    val physicsEntity = Entity.create(
        listOf(
            Box(wallSize),
            Transform(Pose(position, wallRotation)),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = wallSize
              restitution = 0.8f
            }
        )
    )
    roomColliderEntities.add(physicsEntity)

    // Visual edges - use edge boxes like room scan (4 thin boxes forming outline)
    // Physics collider above handles collision, these are just for aesthetics
    val rotationRad = rotation * PI.toFloat() / 180f
    val wallQuaternion = Quaternion(0f, sin(rotationRad / 2f), 0f, cos(rotationRad / 2f))
    val wallPose = Pose(position, wallQuaternion)
    val edgeEntities = createPlaneOutlineEdges(
        centerPose = wallPose,
        width = width,
        height = height,
        thickness = EDGE_THICKNESS
    )
    roomColliderEntities.addAll(edgeEntities)
  }

  /**
   * Scan room using MRUK - loads scene data and triggers Space Setup if needed.
   * This follows the MixedRealitySample pattern for proper room mesh alignment.
   * The AnchorProceduralMesh automatically creates visible meshes for all room anchors.
   */
  private fun scanRoom() {
    Log.d(TAG, "=== SCAN ROOM (MRUK) ===")

    // Clear all bones when environment is reset
    clearAllBones()

    // Clear any existing manual room data (mutual exclusivity)
    if (roomColliderEntities.isNotEmpty()) {
      Log.d(TAG, "Clearing ${roomColliderEntities.size} existing manual room colliders")
      roomColliderEntities.forEach { it.destroy() }
      roomColliderEntities.clear()
    }

    // Clear any existing room scan data (in case re-scanning)
    clearRoomBoundsEdges()

    // Clear old NavGrid and debug visualization
    navGrid?.clearDebugVisualization()
    navGrid = null
    petLocomotion.setNavGrid(null)
    isDebugGridEnabled = false  // Reset checkbox state

    // Recreate procMeshSpawner if it was destroyed (e.g., by setupRoom)
    if (procMeshSpawner == null) {
      Log.d(TAG, "Recreating procMeshSpawner for room scan")
      procMeshSpawner = AnchorProceduralMesh(
          mrukFeature,
          mapOf(
              MRUKLabel.TABLE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.COUCH to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.WINDOW_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.DOOR_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.STORAGE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.BED to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.SCREEN to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.LAMP to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.PLANT to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.OTHER to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
          )
      )
    }

    Log.d(TAG, "Requesting scene capture to ensure fresh room data...")

    // Always request scene capture first to ensure up-to-date room data
    // This launches the Space Setup UI if no scene exists, or updates existing data
    scene.requestSceneCapture().whenComplete { _, captureError ->
      if (captureError != null) {
        Log.e(TAG, "Scene capture error: ${captureError.message}", captureError)
        // Try loading existing scene data even if capture failed
        loadSceneFromDeviceWithLogging()
      } else {
        Log.d(TAG, "Scene capture completed - loading scene data...")
        loadSceneFromDeviceWithLogging()
      }
    }
  }

  /**
   * Load scene from device and log room data.
   * Called after scene capture completes.
   */
  private fun loadSceneFromDeviceWithLogging() {
    mrukFeature.loadSceneFromDevice().whenComplete { result: MRUKLoadDeviceResult, error: Throwable? ->
      if (error != null) {
        Log.e(TAG, "loadSceneFromDevice error: ${error.message}", error)
      }
      if (result == MRUKLoadDeviceResult.SUCCESS) {
        Log.d(TAG, "=== MRUK SCENE LOADED SUCCESSFULLY ===")
        Log.d(TAG, "AnchorProceduralMesh will now create visible meshes for all room anchors")
        logMrukRoomData()

        // Clear any room bounds that may have been created by onAnchorAdded callbacks during load
        // This ensures we don't have duplicates when we manually process anchors below
        clearRoomBoundsEdges()

        // Manually process existing anchors to create wall colliders
        // This is needed because onAnchorAdded only fires for NEW anchors,
        // not anchors that already exist when re-scanning
        for (room in mrukFeature.rooms) {
          Log.d(TAG, "Processing ${room.anchors.size} existing anchors for room ${room.anchor.uuid}")
          for (anchor in room.anchors) {
            onAnchorAddedHandler(room, anchor)
          }
        }

        // Mark environment as set up
        isEnvironmentSetup = true
      } else {
        Log.e(TAG, "MRUK load failed with result: $result")
        Log.w(TAG, "Please set up your room in Quest Settings > Physical Space > Space Setup")
      }
    }
  }

  /**
   * Log all MRUK room and anchor data.
   */
  private fun logMrukRoomData() {
    val rooms = mrukFeature.rooms
    Log.d(TAG, "")
    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
    Log.d(TAG, "MRUK DATA: ${rooms.size} room(s) found")
    Log.d(TAG, "═══════════════════════════════════════════════════════════════")

    rooms.forEachIndexed { roomIndex, room ->
      Log.d(TAG, "")
      Log.d(TAG, "┌─────────────────────────────────────────────────────────────┐")
      Log.d(TAG, "│ ROOM $roomIndex                                              │")
      Log.d(TAG, "├─────────────────────────────────────────────────────────────┤")
      Log.d(TAG, "│ Room anchor UUID: ${room.anchor.uuid}")
      Log.d(TAG, "│ Anchors count: ${room.anchors.size}")
      Log.d(TAG, "└─────────────────────────────────────────────────────────────┘")

      // Log each anchor
      room.anchors.forEachIndexed { anchorIndex, anchorEntity ->
        val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>()
        val transform = anchorEntity.tryGetComponent<Transform>()?.transform

        if (mrukAnchor != null) {
          Log.d(TAG, "")
          Log.d(TAG, "  ┌── ANCHOR $anchorIndex ──────────────────────────────────────")
          Log.d(TAG, "  │ UUID: ${mrukAnchor.uuid}")

          // Transform
          if (transform != null) {
            Log.d(TAG, "  │ Position: (${String.format("%.3f", transform.t.x)}, ${String.format("%.3f", transform.t.y)}, ${String.format("%.3f", transform.t.z)})")
            Log.d(TAG, "  │ Rotation: (${String.format("%.3f", transform.q.x)}, ${String.format("%.3f", transform.q.y)}, ${String.format("%.3f", transform.q.z)}, ${String.format("%.3f", transform.q.w)})")
          }

          // Try to get all component types on this anchor to see what's available
          Log.d(TAG, "  │ Components:")
          Log.d(TAG, "  │   - Has MRUKAnchor: true")
          Log.d(TAG, "  │   - Has Transform: ${anchorEntity.hasComponent<Transform>()}")
          Log.d(TAG, "  │   - Has Box: ${anchorEntity.hasComponent<Box>()}")
          Log.d(TAG, "  │   - Has Physics: ${anchorEntity.hasComponent<Physics>()}")

          Log.d(TAG, "  └────────────────────────────────────────────────────────────")
        } else {
          Log.d(TAG, "  [ANCHOR $anchorIndex] No MRUKAnchor component")
          if (transform != null) {
            Log.d(TAG, "    Position: (${transform.t.x}, ${transform.t.y}, ${transform.t.z})")
          }
        }
      }
    }

    Log.d(TAG, "")
    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
    Log.d(TAG, "END MRUK DATA")
    Log.d(TAG, "═══════════════════════════════════════════════════════════════")
  }

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
                  Column(modifier = Modifier.fillMaxSize()) {
                    // Debug overlay - attention state and hand distance
                    Text(
                        text = "ATTENTION: ${if (isPetAttentive) "TRUE" else "FALSE"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPetAttentive) Color.Green else Color.Red,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "DIST: ${String.format("%.2f", handDistance)}m  CUMUL: ${String.format("%.2f", cumulativeDisplacement)}/0.40",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            handDistance >= 0.10f -> Color.Red        // Outside active range
                            cumulativeDisplacement >= 0.30f -> Color.Green  // Almost triggered
                            cumulativeDisplacement > 0f -> Color.Yellow     // Accumulating
                            else -> Color.White                        // In range, not accumulating
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    if (currentPetData != null) {
                      PetInfoPanel(
                          petData = currentPetData!!,
                          onClose = {
                            currentPet = null
                            currentPetData = null
                            petLocomotion.stopIdleWander() // Stop idle wander
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
                            text = "Select a pet to get started! (v5)",
                            fontSize = 24.sp,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                      }
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
                      onScanRoom = ::scanRoom,
                      onQuit = ::quitApp,
                      firebaseManager = firebaseManager,
                      isEnvironmentSetup = isEnvironmentSetup,
                      isDebugGridEnabled = isDebugGridEnabled,
                      onDebugGridToggle = ::toggleDebugGrid,
                      isRoomMeshVisible = isRoomMeshVisible,
                      onRoomMeshToggle = ::toggleRoomMesh
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
    clapDetector.stop()
    attentionResumeJob?.cancel()
    petLocomotion.cleanup()
    mrukFeature.stopEnvironmentRaycaster()
    // Clean up bone pickup system
    stopBonePickupCheck()
    thrownBones.forEach { it.destroy() }
    thrownBones.clear()
    thrownBoneTimes.clear()
    // Remove MRUK scene event listener
    sceneEventListener?.let { mrukFeature.removeSceneEventListener(it) }
    sceneEventListener = null
    // Clean up room bounds edge entities
    clearRoomBoundsEdges()
    // Clean up any room boundary colliders
    roomColliderEntities.forEach { it.destroy() }
    roomColliderEntities.clear()
    // Destroy procedural mesh spawner
    procMeshSpawner?.destroy()
    super.onSpatialShutdown()
  }

  /**
   * Quit the app.
   */
  private fun quitApp() {
    Log.d(TAG, "Quitting app...")
    finish()
  }

  /**
   * Toggle the NavGrid debug visualization.
   */
  private fun toggleDebugGrid(enabled: Boolean) {
    Log.d(TAG, "Toggle debug grid: $enabled")
    isDebugGridEnabled = enabled
    val grid = navGrid
    if (grid != null) {
      if (enabled) {
        grid.createDebugVisualization(showBlocked = true)
      } else {
        grid.clearDebugVisualization()
      }
    }
  }

  /**
   * Toggle the room mesh visibility (walls, floor, furniture edges).
   */
  private fun toggleRoomMesh(visible: Boolean) {
    Log.d(TAG, "Toggle room mesh visibility: $visible")
    isRoomMeshVisible = visible

    // Toggle visibility of room edge entities (walls, floor, ceiling)
    for (entity in roomEdgeEntities) {
      entity.setComponent(Visible(visible))
    }

    // Toggle visibility of furniture procedural meshes
    // AnchorProceduralMesh doesn't have a visibility toggle, so we destroy/recreate
    if (!visible) {
      procMeshSpawner?.destroy()
      procMeshSpawner = null
    } else if (procMeshSpawner == null && isEnvironmentSetup) {
      // Recreate procMeshSpawner when making visible again
      procMeshSpawner = AnchorProceduralMesh(
          mrukFeature,
          mapOf(
              MRUKLabel.TABLE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.COUCH to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.WINDOW_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.DOOR_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.STORAGE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.BED to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.SCREEN to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.LAMP to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.PLANT to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
              MRUKLabel.OTHER to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
          )
      )
    }
  }

  /**
   * Called when clap is detected - pet turns to face player and pays attention.
   */
  private fun callPetAttention() {
    // Don't do anything if no pet is spawned
    if (currentPetEntity == null) {
      Log.d(TAG, "Clap detected but no pet spawned - ignoring")
      return
    }

    Log.d(TAG, "Clap detected! Calling pet attention")

    // Play whistle sound at head position
    val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
    if (headPos != null) {
      whistlePlayer.play(headPos, 1.0f, false)
    }

    // Set pet as attentive
    isPetAttentive = true

    // Cancel any current walk and stop idle wander
    petLocomotion.stopWalking()
    petLocomotion.stopIdleWander()

    // Start continuously facing the player with smooth rotation
    petLocomotion.startFacingPlayer { getHeadEntity() }

    // Start XP gain coroutine
    startXpGain()

    // Reset the attention timeout
    resetAttentionTimeout()
  }

  /**
   * Start gaining XP while pet has attention (1% per tick)
   */
  private fun startXpGain() {
    xpGainJob?.cancel()
    xpGainJob = activityScope.launch {
      while (isActive && isPetAttentive) {
        delay(XP_GAIN_INTERVAL_MS)

        val petData = currentPetData ?: continue

        // Add 1% XP per tick (0.01 stored, displayed as 1%)
        var newXp = petData.xp + XP_GAIN_PER_TICK
        var newLevel = petData.level

        // Level up if XP >= 1.0 (100%)
        if (newXp >= 1f) {
          newXp = 0f
          newLevel += 1
          Log.d(TAG, "LEVEL UP! Now level $newLevel")
        }

        Log.d(TAG, "Adding 1% XP, new total: ${(newXp * 100).toInt()}%, level: $newLevel")

        // Update local state
        currentPetData = petData.copy(xp = newXp, level = newLevel)

        // Update database using Firebase key (the actual path in the database)
        firebaseManager.updatePetXp("demoUser", petData.firebaseKey, newXp)
        if (newLevel != petData.level) {
          firebaseManager.updatePetLevel("demoUser", petData.firebaseKey, newLevel)
        }
      }
    }
  }

  /**
   * Stop XP gain
   */
  private fun stopXpGain() {
    xpGainJob?.cancel()
    xpGainJob = null
    Log.d(TAG, "XP gain stopped")
  }

  /**
   * Reset the attention timeout - pet will lose attention after ATTENTION_TIMEOUT_MS.
   */
  private fun resetAttentionTimeout() {
    attentionResumeJob?.cancel()
    attentionResumeJob = activityScope.launch {
      delay(ATTENTION_TIMEOUT_MS)
      Log.d(TAG, "Attention timeout - pet loses attention and resumes wandering")
      isPetAttentive = false
      stopXpGain()  // Stop XP accumulation when attention is lost
      petLocomotion.stopFacingPlayer()
      petLocomotion.startIdleWander()
    }
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
   * Initialize custom wall material for transparent green walls and MRUK procedural mesh spawner.
   * Uses a hybrid approach:
   * - Room bounds (walls, floor, ceiling) use geometry-based edge boxes
   * - Furniture uses UV-based edge shader
   */
  private fun initWallMeshCreator() {
    // Create simple translucent green material for edge box geometry (room bounds)
    // Uses solidColor shader - simple unlit color with alpha blending
    edgeBoxMaterial = SceneMaterial.custom(
        "solidColor",
        arrayOf(
            SceneMaterialAttribute("customColor", SceneMaterialDataType.Vector4)
        )
    ).apply {
        setBlendMode(BlendMode.TRANSLUCENT)
        setAttribute("customColor", Vector4(0f, 1f, 0f, 0.35f)) // green with 35% alpha
    }
    Log.d(TAG, "Edge box material initialized (solidColor shader)")

    // Create edge-only shader material for furniture (box-like objects)
    furnitureEdgeMaterial = SceneMaterial.custom(
        "edgeOnly",
        arrayOf(
            SceneMaterialAttribute("customColor", SceneMaterialDataType.Vector4),
            SceneMaterialAttribute("edgeParams", SceneMaterialDataType.Vector4)
        )
    ).apply {
        setBlendMode(BlendMode.TRANSLUCENT)
        setAttribute("customColor", Vector4(0f, 1f, 0f, 0.3f)) // green with 30% alpha
        setAttribute("edgeParams", Vector4(EDGE_THICKNESS, 0f, 0f, 0f)) // thickness = 2cm
    }
    Log.d(TAG, "Furniture edge material initialized (edgeOnly shader)")

    // Keep wallMaterial for backwards compatibility with manual wall creation
    wallMaterial = edgeBoxMaterial

    // Create AnchorProceduralMesh for FURNITURE ONLY - NOT room bounds
    // Room bounds (FLOOR, WALL_FACE, CEILING) will use geometry-based edges via onAnchorAdded
    procMeshSpawner = AnchorProceduralMesh(
        mrukFeature,
        mapOf(
            // Furniture uses edge shader (works well for box-like objects with good UVs)
            MRUKLabel.TABLE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.COUCH to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.WINDOW_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.DOOR_FRAME to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.STORAGE to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.BED to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.SCREEN to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.LAMP to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.PLANT to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            MRUKLabel.OTHER to AnchorProceduralMeshConfig(furnitureEdgeMaterial, true),
            // Note: FLOOR, WALL_FACE, CEILING are NOT included here
            // They will be handled by onAnchorAdded with geometry boxes
        )
    )
    Log.d(TAG, "AnchorProceduralMesh initialized for furniture with edge shader")

    // Register scene event listener to handle room loading events
    sceneEventListener = object : MRUKSceneEventListener {
        override fun onRoomAdded(room: MRUKRoom) {
            Log.d(TAG, "=== MRUK ROOM ADDED ===")
            Log.d(TAG, "Room UUID: ${room.anchor.uuid}")
            Log.d(TAG, "Room has ${room.anchors.size} anchors")
            // Procedural meshes are automatically created by AnchorProceduralMesh for furniture
        }

        override fun onRoomUpdated(room: MRUKRoom) {
            Log.d(TAG, "=== MRUK ROOM UPDATED ===")
            Log.d(TAG, "Room UUID: ${room.anchor.uuid}")
        }

        override fun onRoomRemoved(room: MRUKRoom) {
            Log.d(TAG, "=== MRUK ROOM REMOVED ===")
            Log.d(TAG, "Room UUID: ${room.anchor.uuid}")
            // Clean up edge entities when room is removed
            clearRoomBoundsEdges()
        }

        override fun onAnchorAdded(room: MRUKRoom, anchor: Entity) {
            // Create edge geometry for room bounds anchors (walls, floor, ceiling)
            onAnchorAddedHandler(room, anchor)
        }
    }
    mrukFeature.addSceneEventListener(sceneEventListener!!)
    Log.d(TAG, "MRUKSceneEventListener registered with onAnchorAdded for room bounds edges")
  }

  /**
   * Create a SceneMesh box with custom material for visual walls.
   */
  private fun createWallSceneMesh(wallSize: Vector3): SceneMesh {
    val halfX = wallSize.x / 2f
    val halfY = wallSize.y / 2f
    val halfZ = wallSize.z / 2f
    return SceneMesh.box(Vector3(-halfX, -halfY, -halfZ), Vector3(halfX, halfY, halfZ), wallMaterial)
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
              Box(Vector3(0.04f, 0.03f, 0.12f)),  // Smaller bone collider
              Physics().apply {
                state = PhysicsState.DYNAMIC
                shape = "box"
                dimensions = Vector3(0.04f, 0.03f, 0.12f)
                restitution = 0.1f  // Less bouncy
                linearVelocity = velocity
              }
          )
      )
      // Track thrown bone for pickup with timestamp
      thrownBones.add(entity)
      thrownBoneTimes[entity.id] = System.currentTimeMillis()
      Log.d(TAG, "Spawned thrown bone at pos=$position vel=$velocity id=${entity.id}, tracked=${thrownBones.size} bones")
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

  /**
   * Start continuous proximity checking for bone pickup.
   * Checks both hands against all thrown bones.
   */
  private fun startBonePickupCheck() {
    bonePickupJob?.cancel()
    bonePickupJob = activityScope.launch {
      while (isActive) {
        try {
          // Skip if player is already holding a bone
          if (boneEntity != null) {
            delay(100)
            continue
          }

          // Get hand positions
          val rightHand = getRightHandEntity()
          val leftHand = getLeftHandEntity()
          val rightHandPos = rightHand?.tryGetComponent<Transform>()?.transform?.t
          val leftHandPos = leftHand?.tryGetComponent<Transform>()?.transform?.t

          // Check each thrown bone for proximity
          val bonesToRemove = mutableListOf<Entity>()
          val currentTime = System.currentTimeMillis()
          for (bone in thrownBones) {
            try {
              // Check cooldown - skip if bone was thrown too recently
              val throwTime = thrownBoneTimes[bone.id] ?: 0L
              if (currentTime - throwTime < BONE_PICKUP_COOLDOWN_MS) {
                continue // Still in cooldown
              }

              val boneTransform = bone.tryGetComponent<Transform>()?.transform
              if (boneTransform == null) {
                // Bone entity may have been destroyed
                bonesToRemove.add(bone)
                continue
              }
              val bonePos = boneTransform.t

              // Check distance to right hand
              if (rightHandPos != null) {
                val distRight = vectorLength(vectorDiff(bonePos, rightHandPos))
                if (distRight < BONE_PICKUP_DISTANCE) {
                  Log.d(TAG, "Bone pickup triggered - right hand dist=$distRight")
                  pickupBone(bone, rightHand)
                  bonesToRemove.add(bone)
                  break // Only pick up one bone at a time
                }
              }

              // Check distance to left hand
              if (leftHandPos != null) {
                val distLeft = vectorLength(vectorDiff(bonePos, leftHandPos))
                if (distLeft < BONE_PICKUP_DISTANCE) {
                  Log.d(TAG, "Bone pickup triggered - left hand dist=$distLeft")
                  pickupBone(bone, leftHand)
                  bonesToRemove.add(bone)
                  break // Only pick up one bone at a time
                }
              }
            } catch (e: Exception) {
              // Bone may have been destroyed, mark for removal
              bonesToRemove.add(bone)
            }
          }

          // Clean up any removed bones from tracking
          bonesToRemove.forEach { thrownBoneTimes.remove(it.id) }
          thrownBones.removeAll(bonesToRemove)

          delay(50) // Check ~20 times per second
        } catch (e: Exception) {
          Log.e(TAG, "Bone pickup check error: ${e.message}")
          delay(100)
        }
      }
    }
    Log.d(TAG, "Bone pickup check started")
  }

  /**
   * Pick up a thrown bone - destroy it and create a new one attached to the hand.
   */
  private fun pickupBone(thrownBone: Entity, hand: Entity?) {
    if (hand == null || hand == Entity.nullEntity()) {
      Log.w(TAG, "Cannot pickup bone - no hand entity")
      return
    }

    try {
      // Get the bone's current position for sound
      val bonePos = thrownBone.tryGetComponent<Transform>()?.transform?.t

      // Destroy the thrown bone
      thrownBone.destroy()
      Log.d(TAG, "Destroyed thrown bone for pickup")

      // Create a new bone attached to the hand
      val localOffset = Vector3(0f, 0f, 0.08f)
      val pose = Pose(localOffset, Quaternion())

      val newBone = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri(), hittable = MeshCollision.LineTest),
              Transform(pose),
              Scale(Vector3(0.2f, 0.2f, 0.2f)),
              Visible(true),
              Hittable(MeshCollision.LineTest),
              Box(Vector3(0.2f, 0.06f, 0.35f)),
              IsdkGrabbable(),
              Physics().apply {
                state = PhysicsState.KINEMATIC // Attached to hand
                shape = "box"
                dimensions = Vector3(0.2f, 0.06f, 0.35f)
                restitution = 0.2f
              }
          )
      )

      // Track as the held bone and attach to hand
      boneEntity = newBone
      attachToHand(newBone, hand)
      startBoneSampling(newBone, hand)

      // Play pickup sound
      if (bonePos != null) {
        boneSoundPlayer.play(bonePos, 0.8f, false)
      }

      Log.d(TAG, "Picked up bone and attached to hand=${hand.id}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to pickup bone: ${e.message}", e)
    }
  }

  /**
   * Stop bone pickup checking.
   */
  private fun stopBonePickupCheck() {
    bonePickupJob?.cancel()
    bonePickupJob = null
  }

  /**
   * Clear all bones (held and thrown) when environment is reset.
   */
  private fun clearAllBones() {
    Log.d(TAG, "Clearing all bones - held: ${boneEntity != null}, thrown: ${thrownBones.size}")

    // Stop bone sampling if active
    boneSampleJob?.cancel()
    boneSampleJob = null
    boneHand = null
    lastBonePos = null
    lastBoneSampleNs = 0L

    // Destroy held bone
    boneEntity?.destroy()
    boneEntity = null

    // Destroy all thrown bones
    thrownBones.forEach { it.destroy() }
    thrownBones.clear()
    thrownBoneTimes.clear()

    Log.d(TAG, "All bones cleared")
  }

  // --- Room bounds edge geometry functions ---

  private fun clearRoomBoundsEdges() {
    for (entity in roomEdgeEntities) {
      entity.destroy()
    }
    roomEdgeEntities.clear()

    for (entity in roomBoundsPhysicsEntities) {
      entity.destroy()
    }
    roomBoundsPhysicsEntities.clear()

    Log.d(TAG, "Cleared all room bounds edge and physics entities")
  }

  /**
   * Called when an anchor is added to a room. Creates edge geometry for room bounds anchors
   * and blocks furniture in NavGrid.
   */
  private fun onAnchorAddedHandler(room: MRUKRoom, anchorEntity: Entity) {
    // Get the MRUKAnchor component to check its labels
    val anchorComponent = anchorEntity.tryGetComponent<MRUKAnchor>() ?: return

    // Check anchor labels
    val anchorLabels = mutableListOf<String>()
    for (i in 0 until anchorComponent.labelsCount) {
      anchorComponent.labels[i]?.let { anchorLabels.add(it) }
    }

    val hasRoomBoundsLabel = anchorLabels.any { labelName ->
      roomBoundsLabels.any { it.name == labelName }
    }

    val hasFurnitureLabel = anchorLabels.any { labelName ->
      furnitureLabels.any { it.name == labelName }
    }

    // Get the anchor's transform/pose
    val transform = anchorEntity.tryGetComponent<Transform>()
    if (transform == null) {
      Log.d(TAG, "Anchor has no Transform component, skipping: $anchorLabels")
      return
    }
    val anchorPose = transform.transform

    // Block furniture in NavGrid
    if (hasFurnitureLabel) {
      blockFurnitureInNavGrid(anchorEntity, anchorPose, anchorLabels)
    }

    // Handle room bounds (walls, floor, ceiling)
    if (!hasRoomBoundsLabel) {
      return
    }

    // Get the MRUKPlane component for plane bounds (walls, floors, ceilings have this)
    val planeComponent = anchorEntity.tryGetComponent<MRUKPlane>()
    if (planeComponent == null) {
      Log.d(TAG, "Anchor has no MRUKPlane component, skipping: $anchorLabels")
      return
    }

    // Calculate width and height from plane min/max
    val width = planeComponent.max.x - planeComponent.min.x
    val height = planeComponent.max.y - planeComponent.min.y

    Log.d(TAG, "Creating edges for anchor: labels=$anchorLabels, size=${width}x${height}")

    // Create the 4 edge boxes for this plane
    val edges = createPlaneOutlineEdges(
        centerPose = anchorPose,
        width = width,
        height = height,
        thickness = EDGE_THICKNESS
    )
    roomEdgeEntities.addAll(edges)

    // Create physics collider for wall faces only (not floor/ceiling)
    val isWall = anchorLabels.any { it == MRUKLabel.WALL_FACE.name }
    if (isWall) {
      createWallPhysicsCollider(anchorPose, width, height)
    }

    // Extract floor polygon from FLOOR anchor
    val isFloor = anchorLabels.any { it == MRUKLabel.FLOOR.name }
    if (isFloor) {
      extractFloorPolygon(anchorPose, planeComponent)
    }
  }

  /**
   * Block a furniture anchor's footprint in the NavGrid.
   * Uses the anchor's bounds to mark cells as non-walkable.
   */
  private fun blockFurnitureInNavGrid(anchorEntity: Entity, anchorPose: Pose, labels: List<String>) {
    val grid = navGrid ?: return

    // Try to get bounds from MRUKPlane component (furniture usually has this)
    val planeComponent = anchorEntity.tryGetComponent<MRUKPlane>()

    val halfSizeX: Float
    val halfSizeZ: Float

    if (planeComponent != null) {
      // Use plane bounds
      halfSizeX = (planeComponent.max.x - planeComponent.min.x) / 2f
      halfSizeZ = (planeComponent.max.y - planeComponent.min.y) / 2f  // Y in plane = Z in world for floor-level
    } else {
      // No size info available, use default furniture size (0.5m x 0.5m)
      halfSizeX = 0.25f
      halfSizeZ = 0.25f
      Log.d(TAG, "Furniture has no MRUKPlane, using default size: $labels")
    }

    // Block the footprint in NavGrid
    grid.blockRect(anchorPose.t.x, anchorPose.t.z, halfSizeX, halfSizeZ)
    Log.d(TAG, "Blocked furniture in NavGrid: $labels at (${anchorPose.t.x}, ${anchorPose.t.z}), size ${halfSizeX*2}x${halfSizeZ*2}")
    Log.d(TAG, "NavGrid now has ${grid.getWalkableCellCount()} walkable cells")

    // Refresh debug visualization if enabled
    if (isDebugGridEnabled) {
      grid.createDebugVisualization(showBlocked = true)
    }
  }

  /**
   * Extract the floor polygon from the FLOOR anchor's plane bounds.
   * Creates a polygon from the plane's min/max corners transformed to world space.
   * Also creates the NavGrid for pathfinding.
   */
  private fun extractFloorPolygon(anchorPose: Pose, planeComponent: MRUKPlane) {
    // Clear any existing NavGrid before creating a new one (ensures only 1 at a time)
    navGrid?.let { existingGrid ->
      Log.d(TAG, "Clearing existing NavGrid before creating new one")
      existingGrid.clearDebugVisualization()
    }
    navGrid = null

    // Get the floor plane's local corners from min/max
    val minX = planeComponent.min.x
    val maxX = planeComponent.max.x
    val minY = planeComponent.min.y
    val maxY = planeComponent.max.y

    // Floor plane local coordinates: X = width, Y = depth (since floor is horizontal)
    // Create 4 corners in local space (Z = 0 for the plane surface)
    val localCorners = listOf(
        Vector3(minX, minY, 0f),  // bottom-left
        Vector3(maxX, minY, 0f),  // bottom-right
        Vector3(maxX, maxY, 0f),  // top-right
        Vector3(minX, maxY, 0f)   // top-left
    )

    // Transform corners to world space
    val worldCorners = localCorners.map { local ->
      val rotated = anchorPose.q.times(local)
      Vector3(
          anchorPose.t.x + rotated.x,
          anchorPose.t.y + rotated.y,
          anchorPose.t.z + rotated.z
      )
    }

    // Create floor polygon from world X/Z coordinates
    val vertices = worldCorners.map { corner ->
      PetLocomotion.Point2D(corner.x, corner.z)
    }

    val floorPolygon = PetLocomotion.FloorPolygon(vertices)
    petLocomotion.setFloorPolygon(floorPolygon)

    Log.d(TAG, "Floor polygon set from FLOOR anchor with ${vertices.size} vertices")
    vertices.forEachIndexed { i, v ->
      Log.d(TAG, "  Vertex $i: (${v.x}, ${v.z})")
    }

    // Create NavGrid from floor polygon for pathfinding
    val floorY = anchorPose.t.y
    navGrid = NavGrid.fromFloorPolygon(floorPolygon, floorY)
    petLocomotion.setNavGrid(navGrid)
    Log.d(TAG, "NavGrid created: ${navGrid?.gridWidth}x${navGrid?.gridHeight} cells, ${navGrid?.getWalkableCellCount()} walkable")

    // Create debug visualization if enabled
    if (isDebugGridEnabled) {
      navGrid?.createDebugVisualization(showBlocked = true)
    }
  }

  /**
   * Creates a physics collider for a wall anchor from room scan.
   * The collider matches the wall's full dimensions.
   */
  private fun createWallPhysicsCollider(pose: Pose, width: Float, height: Float) {
    val wallThickness = 0.1f
    val wallSize = Vector3(width, height, wallThickness)

    Log.d(TAG, "Creating wall physics collider: size=$wallSize at ${pose.t}")

    val physicsEntity = Entity.create(
        listOf(
            Box(wallSize),
            Transform(pose),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = wallSize
              restitution = 0.8f
            }
        )
    )
    roomBoundsPhysicsEntities.add(physicsEntity)
  }

  /**
   * Creates 4 edge box entities outlining a rectangular plane.
   *
   * The plane is defined by:
   * - centerPose: position and orientation of the plane center
   * - width: horizontal extent (along local X axis)
   * - height: vertical extent (along local Y axis)
   * - thickness: how thick the edge boxes should be
   *
   * The plane's local coordinate system:
   * - X axis: horizontal (width direction)
   * - Y axis: vertical (height direction)
   * - Z axis: normal to the plane (points outward)
   */
  private fun createPlaneOutlineEdges(
      centerPose: Pose,
      width: Float,
      height: Float,
      thickness: Float
  ): List<Entity> {
    val entities = mutableListOf<Entity>()
    val halfWidth = width / 2f
    val halfHeight = height / 2f
    val halfThick = thickness / 2f

    // We need to create 4 edges: top, bottom, left, right
    // Each edge is positioned relative to the plane center using the plane's orientation

    // Edge definitions: (localOffset, boxSize)
    // - Top edge: at +Y, spans full width
    // - Bottom edge: at -Y, spans full width
    // - Left edge: at -X, spans full height (minus corners to avoid overlap)
    // - Right edge: at +X, spans full height (minus corners to avoid overlap)

    data class EdgeDef(
        val localOffset: Vector3,
        val boxHalfSize: Vector3
    )

    val edgeDefs = listOf(
        // Top edge: horizontal bar at top
        EdgeDef(
            localOffset = Vector3(0f, halfHeight, 0f),
            boxHalfSize = Vector3(halfWidth, halfThick, halfThick)
        ),
        // Bottom edge: horizontal bar at bottom
        EdgeDef(
            localOffset = Vector3(0f, -halfHeight, 0f),
            boxHalfSize = Vector3(halfWidth, halfThick, halfThick)
        ),
        // Left edge: vertical bar at left (shortened to fit between top/bottom)
        EdgeDef(
            localOffset = Vector3(-halfWidth, 0f, 0f),
            boxHalfSize = Vector3(halfThick, halfHeight - thickness, halfThick)
        ),
        // Right edge: vertical bar at right (shortened to fit between top/bottom)
        EdgeDef(
            localOffset = Vector3(halfWidth, 0f, 0f),
            boxHalfSize = Vector3(halfThick, halfHeight - thickness, halfThick)
        )
    )

    for ((index, edgeDef) in edgeDefs.withIndex()) {
      // Transform local offset to world position using the plane's pose
      val worldOffset = centerPose.q.times(edgeDef.localOffset)
      val worldPos = centerPose.t + worldOffset

      // Create edge entity with transform only (SceneObject handles the mesh)
      val edgePose = Pose(worldPos, centerPose.q)

      val entity = Entity.create(
          listOf(
              Transform(edgePose)
          )
      )

      // Create scene object with box mesh and material
      val min = -edgeDef.boxHalfSize
      val max = edgeDef.boxHalfSize
      val boxMesh = SceneMesh.box(
          Vector3(min.x, min.y, min.z),
          Vector3(max.x, max.y, max.z),
          edgeBoxMaterial
      )
      val sceneObject = SceneObject(scene, boxMesh, "roomEdge_${index}", entity)
      systemManager.findSystem<SceneObjectSystem>().addSceneObject(
          entity,
          CompletableFuture<SceneObject>().apply { complete(sceneObject) }
      )

      entities.add(entity)
    }

    return entities
  }

}
