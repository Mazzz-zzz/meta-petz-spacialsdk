@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_IS_NOT_ENABLED")

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
import com.cybergarden.metapetz.ecs.NavGridEditSystem
import com.cybergarden.metapetz.ecs.PetLocomotion
import com.cybergarden.metapetz.ecs.PettingDetector
import com.cybergarden.metapetz.ecs.QRCodeSystem
import androidx.compose.ui.text.font.FontWeight
import com.cybergarden.metapetz.model.Accessory
import com.cybergarden.metapetz.model.PetData
import com.cybergarden.metapetz.services.FirebaseManager
import com.cybergarden.metapetz.services.QRScannerManager
import com.cybergarden.metapetz.ui.OptionsPanel
import com.cybergarden.metapetz.ui.PetInfoPanel
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_WIDTH
import com.cybergarden.metapetz.ui.theme.BROWSER_PANEL_WIDTH
import com.cybergarden.metapetz.ui.theme.BROWSER_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.theme.ROOM_PICKER_PANEL_WIDTH
import com.cybergarden.metapetz.ui.theme.ROOM_PICKER_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.RoomPickerPanel
import com.cybergarden.metapetz.ui.RoomPickerInfo
import com.cybergarden.metapetz.ui.BrowserPanel
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.compose.composePanel
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
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
import com.meta.spatial.toolkit.Sphere
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
import com.meta.spatial.mruk.MRUKStartTrackerResult
import com.meta.spatial.mruk.MRUKAnchor
import com.meta.spatial.mruk.MRUKRoom
import com.meta.spatial.mruk.MRUKLabel
import com.meta.spatial.mruk.MRUKSceneEventListener
import com.meta.spatial.mruk.AnchorProceduralMesh
import com.meta.spatial.mruk.AnchorProceduralMeshConfig
import com.meta.spatial.mruk.MRUKPlane
import com.meta.spatial.mruk.MRUKVolume
import com.meta.spatial.mruk.Tracker
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.toolkit.getAbsoluteTransform
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
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller
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
  private val accessoryEntities = mutableListOf<Entity>()  // Hat and other accessories attached to pet
  private var showAccessories by mutableStateOf(true)  // Toggle accessory visibility
  private var isEnvironmentSetup by mutableStateOf(false)
  private var isRoomMode by mutableStateOf(false)  // true = scanned room with pathfinding, false = outside mode
  private var showRoomPicker by mutableStateOf(false)  // Show room selection panel
  private var availableRooms by mutableStateOf<List<RoomPickerInfo>>(emptyList())  // Rooms for picker
  private var roomPickerPanelEntity: Entity? = null  // Panel entity for room picker
  private var isDebugGridEnabled by mutableStateOf(false)
  private var isNavGridEditMode by mutableStateOf(false)
  private var navGridEditSystem: NavGridEditSystem? = null
  private var spinningJob: Job? = null
  private var panelEntity: Entity? = null
  private var showBrowserPanel by mutableStateOf(false)
  private var browserPanelEntity: Entity? = null
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
  private lateinit var furnitureOccluderMaterial: SceneMaterial

  // Physics colliders for room bounds (walls from room scan)
  private val roomBoundsPhysicsEntities = mutableListOf<Entity>()

  // Navigation grid for pathfinding (avoids furniture)
  private var navGrid: NavGrid? = null

  // Pending wall data to block after NavGrid is created
  data class PendingWall(val worldPos: Vector3, val worldRot: Quaternion, val width: Float)
  private val pendingWalls = mutableListOf<PendingWall>()

  // Current room tracking - only process anchors from the room user is in
  private var currentProcessedRoomUuid: String? = null
  private var currentRoomLabel by mutableStateOf<String?>(null)  // For UI display
  private var debugHeadPosLabel by mutableStateOf<String?>(null)  // Debug: head position and detected room
  private var lastRecenterTime: Long = 0  // Timestamp of last recenter for debug label cooldown
  private var roomChangeCheckJob: Job? = null
  private var panelFollowJob: Job? = null


  // Loading state for heavy room processing
  private var isRoomProcessing by mutableStateOf(false)

  // Debug visibility toggles (reactive for Compose UI)
  private var isRoomMeshVisible by mutableStateOf(false)  // Room mesh (walls/floor) hidden by default
  private var isFurnitureOccluderVisible by mutableStateOf(true)  // Furniture occluders shown by default

  // Labels that represent room bounds (walls, floor, ceiling)
  private val roomBoundsLabels = setOf(MRUKLabel.WALL_FACE, MRUKLabel.FLOOR, MRUKLabel.CEILING)

  // Labels that represent furniture to block in NavGrid
  private val furnitureLabels = setOf(
      MRUKLabel.TABLE, MRUKLabel.COUCH, MRUKLabel.BED, MRUKLabel.STORAGE,
      MRUKLabel.SCREEN, MRUKLabel.LAMP, MRUKLabel.PLANT, MRUKLabel.OTHER
  )

  // Debug: Furniture footprints as 4 world-space XZ corners
  data class FurnitureQuad(
      val corners: List<Pair<Float, Float>>,  // 4 corners in XZ world space
      val label: String
  ) {
    // Point-in-polygon using cross product (works for convex quads)
    fun containsPoint(px: Float, pz: Float): Boolean {
      if (corners.size != 4) return false
      var sign: Int? = null
      for (i in 0 until 4) {
        val (x1, z1) = corners[i]
        val (x2, z2) = corners[(i + 1) % 4]
        val cross = (x2 - x1) * (pz - z1) - (z2 - z1) * (px - x1)
        val currentSign = if (cross > 0) 1 else if (cross < 0) -1 else 0
        if (currentSign != 0) {
          if (sign == null) sign = currentSign
          else if (sign != currentSign) return false
        }
      }
      return true
    }
  }
  private val furnitureQuads = mutableListOf<FurnitureQuad>()
  private val furnitureDebugSpheres = mutableListOf<Entity>()  // Reserved for future debug visualization
  private val furniturePhysicsBoxes = mutableListOf<Entity>()  // Reserved for future debug visualization

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
  private val bark3Sound: SceneAudioAsset by lazy {
    SceneAudioAsset.loadLocalFile("audio/bark3.wav")
  }
  private val bark3Player: SceneAudioPlayer by lazy {
    SceneAudioPlayer(scene, bark3Sound)
  }

  /**
   * Play a random bark sound (1-3) at the pet's position
   */
  private fun playRandomBark() {
    val petPos = currentPetEntity?.tryGetComponent<Transform>()?.transform?.t ?: return
    when ((1..3).random()) {
      1 -> bark1Player.play(petPos, 1.0f, false)
      2 -> bark2Player.play(petPos, 1.0f, false)
      3 -> bark3Player.play(petPos, 1.0f, false)
    }
    Log.d(TAG, "Playing random bark")
  }

  // Bone throw cooldown - prevent immediate re-grab after throwing
  private var boneGrabTimeMs: Long = 0L
  private val BONE_THROW_COOLDOWN_MS = 1500L

  // Attention system - activity-based attention tracking
  enum class AttentionActivity {
    SPAWNING,       // Just spawned, doing intro spin animation - no other rotations allowed
    IDLE,           // Wandering around, no attention
    FACING_PLAYER,  // Has attention, continuously facing player - can timeout
    WALKING,        // Walking to commanded position - has attention
    SITTING,        // Sitting on command - has boredom timeout
    FETCHING        // Actively fetching bone - NO timeout
  }

  private var isPetAttentive by mutableStateOf(false)
  private var currentActivity by mutableStateOf(AttentionActivity.IDLE)
  private var attentionResumeJob: Job? = null
  private val ATTENTION_TIMEOUT_MS = 5000L

  // Fetch system - pet has bone in mouth
  private var petHasBone by mutableStateOf(false)

  // Fetch debug states for UI
  private var debugFetchState by mutableStateOf("IDLE")
  private var debugDistanceToBone by mutableStateOf(-1f)
  private var debugBonePickedUp by mutableStateOf(false)
  private var debugReturningBone by mutableStateOf(false)

  // XP gain while attention is held (0.01 = 1%, 1.0 = 100% full bar)
  private var xpGainJob: Job? = null
  private val XP_GAIN_PER_TICK = 0.01f  // 1% per tick (stored as 0.01)
  private val XP_GAIN_INTERVAL_MS = 2000L
  private val SIT_XP_BONUS = 0.02f  // 2% XP bonus when sitting

  // Move command limit per attention session (prevents XP farming)
  private var moveCommandCount = 0
  private val MAX_MOVE_COMMANDS = 2  // Pet gets tired after 2 move commands

  // Attention indicator - fading disc above pet's head
  private var attentionIndicatorEntity: Entity? = null
  private var attentionIndicatorJob: Job? = null
  private val INDICATOR_HEIGHT_OFFSET = 0.35f  // Height above pet

  // Hand distance for debug UI (updated by clap detector)
  private var handDistance by mutableStateOf(0f)
  private var cumulativeDisplacement by mutableStateOf(0f)


  // Clap detector for calling pet's attention and raise hand for sit
  private val clapDetector: ClapDetector by lazy {
    ClapDetector(activityScope, systemManager).apply {
      // Only accumulate trigger button points when pet is NOT attentive (idle)
      isAttentive = { isPetAttentive }
      // Play bone sound when cumulative displacement threshold reached
      onClapDetected = {
        val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
        if (headPos != null) {
          boneSoundPlayer.play(headPos, 1.0f, false)
        }
        Log.d(TAG, "CLAP TRIGGERED! Playing bone sound and getting attention")
        callPetAttention()
      }
      // Raise hand gesture triggers sit command (only when pet is attentive)
      onRaiseHandDetected = {
        Log.d(TAG, "RAISE HAND DETECTED! Checking if pet is attentive...")
        triggerSitFromGesture()
      }
      // Debug callbacks for entering/leaving active range
      onHandsTogether = {
        Log.d(TAG, "Entered active range")
      }
      onHandsApart = {
        Log.d(TAG, "Left active range")
      }
    }
  }

  // Petting detector for detecting when player pets the animal
  private val pettingDetector: PettingDetector by lazy {
    PettingDetector(activityScope, systemManager).apply {
      getPetEntity = { currentPetEntity }
      onPettingDetected = { handPosition ->
        Log.d(TAG, "PETTING DETECTED! Awarding XP")
        awardPettingXp()
        // Spawn a burst of hearts with wider spread
        repeat(5) {
          val heartPos = Vector3(
              handPosition.x + (Random.nextFloat() - 0.5f) * 0.25f,
              handPosition.y + 0.05f + Random.nextFloat() * 0.1f,
              handPosition.z + (Random.nextFloat() - 0.5f) * 0.25f
          )
          spawnHeartParticle(heartPos)
        }
        // If pet is idle, accumulate petting toward getting attention
        if (currentActivity == AttentionActivity.IDLE) {
          cumulativePettingForAttention++
          Log.d(TAG, "Petting count: $cumulativePettingForAttention / $PETTING_ATTENTION_THRESHOLD")
          if (cumulativePettingForAttention >= PETTING_ATTENTION_THRESHOLD) {
            Log.d(TAG, "Enough petting! Pet now has your attention (silent)")
            cumulativePettingForAttention = 0
            callPetAttention(silent = true)
          }
        } else {
          // Reset counter if pet already has attention
          cumulativePettingForAttention = 0
        }
      }
      onSpawnHeart = { position ->
        spawnHeartParticle(position)
      }
    }
  }

  // Active heart particles for cleanup
  private val activeHearts = mutableListOf<Entity>()

  // Cumulative petting count for getting attention (resets when attention is gained or timeout)
  private var cumulativePettingForAttention = 0
  private val PETTING_ATTENTION_THRESHOLD = 3  // Need 3 petting detections to get attention

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
        // Don't interfere during fetch - fetch coroutine manages its own state
        if (currentActivity == AttentionActivity.FETCHING) {
          Log.d(TAG, "Pet finished walking during fetch - skipping")
        } else if (moveCommandCount >= MAX_MOVE_COMMANDS) {
          // Pet hit move limit - lose attention
          Log.d(TAG, "Walk finished - pet loses attention (too many commands)")
          isPetAttentive = false
          currentActivity = AttentionActivity.IDLE
          stopXpGain()
          hideAttentionIndicator()
          petLocomotion.stopFacingPlayer()
          startIdleWander()
        } else if (isPetAttentive && currentActivity == AttentionActivity.WALKING) {
          // Return to FACING_PLAYER if still attentive
          Log.d(TAG, "Walk finished - returning to FACING_PLAYER")
          currentActivity = AttentionActivity.FACING_PLAYER
          petLocomotion.startFacingPlayer { this@ImmersiveActivity.getHeadEntity() }
          resetAttentionTimeout()
        }
      }
      // Tell PetLocomotion about attention state - accept move commands when FACING_PLAYER or WALKING
      isAttentive = { isPetAttentive && (currentActivity == AttentionActivity.FACING_PLAYER || currentActivity == AttentionActivity.WALKING) }
      // Provide head entity for fetch return
      getHeadEntity = { this@ImmersiveActivity.getHeadEntity() }
      // Fetch callbacks
      onFetchStart = { bone ->
        Log.d(TAG, "Pet started fetching bone id=${bone.id}")
        isPetAttentive = true
        currentActivity = AttentionActivity.FETCHING  // Lock attention during fetch
        attentionResumeJob?.cancel()  // Cancel any pending timeout
        showAttentionIndicator()  // Show indicator during fetch (activity != IDLE)
        // Debug states
        debugFetchState = "MOVING_TO_BONE"
        debugBonePickedUp = false
        debugReturningBone = false
        // Start tracking distance to bone
        startDistanceTracking(bone)
      }
      onFetchPickup = { bone ->
        Log.d(TAG, "Pet picked up bone id=${bone.id}")
        petHasBone = true
        // Debug states
        debugFetchState = "PICKING_UP"
        debugBonePickedUp = true
        debugDistanceToBone = 0f
      }
      onFetchReturning = { bone ->
        Log.d(TAG, "Pet returning with bone id=${bone.id}")
        // Debug states
        debugFetchState = "RETURNING"
        debugReturningBone = true
        debugDistanceToBone = -1f  // No longer tracking bone distance
        stopDistanceTracking()
      }
      onFetchComplete = { bone ->
        Log.d(TAG, "Pet completed fetch!")
        petHasBone = false
        currentActivity = AttentionActivity.IDLE
        // Resume idle wander after fetch
        isPetAttentive = false
        hideAttentionIndicator()
        startIdleWander()
        // Debug states
        debugFetchState = "IDLE"
        debugBonePickedUp = false
        debugReturningBone = false
        debugDistanceToBone = -1f
        stopDistanceTracking()

        // Increment bones fetched counter and add XP in Firebase
        currentPetData?.let { petData ->
          // Add 5% XP (0.05) for successful fetch
          var newXp = petData.xp + 0.05f
          var newLevel = petData.level
          if (newXp >= 1f) {
            newXp = 0f
            newLevel += 1
            Log.d(TAG, "Level up from fetch! New level: $newLevel")
            firebaseManager.updatePetLevel("demoUser", petData.firebaseKey, newLevel)
          }
          firebaseManager.updatePetXp("demoUser", petData.firebaseKey, newXp)

          // Increment bones fetched counter
          firebaseManager.incrementBonesFetched("demoUser", petData.firebaseKey) { newCount ->
            if (newCount != null) {
              Log.d(TAG, "Bones fetched updated to $newCount, XP: ${(newXp * 100).toInt()}%")
              // Update local state for UI with both changes
              currentPetData = petData.copy(bonesFetched = newCount, xp = newXp, level = newLevel)
            }
          }
        }
      }
      onFetchCancelled = {
        Log.d(TAG, "Fetch was cancelled")
        petHasBone = false
        currentActivity = AttentionActivity.IDLE
        isPetAttentive = false
        hideAttentionIndicator()
        startIdleWander()
        // Debug states
        debugFetchState = "IDLE"
        debugBonePickedUp = false
        debugReturningBone = false
        debugDistanceToBone = -1f
        stopDistanceTracking()
      }
      // Mouth bone callbacks
      onSpawnMouthBone = { petEntity, boneWorldPos ->
        spawnMouthBoneWithTween(petEntity, boneWorldPos)
      }
      onDropBone = { position ->
        spawnDroppedBone(position)
      }
      // Sit callbacks
      onSitStart = {
        Log.d(TAG, "Pet started sitting")
      }
      // Animation callbacks for accessory offset adjustments
      onJumpStart = {
        Log.d(TAG, "Jump started - adjusting hat offset")
        animateHatOffset(HAT_OFFSET_JUMP, 80L)
      }
      onJumpEnd = {
        Log.d(TAG, "Jump ended - restoring hat offset")
        animateHatOffset(HAT_OFFSET_DEFAULT, 100L)
      }
      onEatStart = {
        Log.d(TAG, "Eat started - adjusting hat offset")
        animateHatOffset(HAT_OFFSET_EAT, 150L)
      }
      onEatEnd = {
        Log.d(TAG, "Eat ended - restoring hat offset")
        animateHatOffset(HAT_OFFSET_DEFAULT, 150L)
      }
      onSitBored = {
        Log.d(TAG, "Pet got bored of sitting")
        currentActivity = AttentionActivity.IDLE
        isPetAttentive = false
        stopXpGain()
        hideAttentionIndicator()
        startIdleWander()
      }
      onSitInterrupted = {
        Log.d(TAG, "Pet sit was interrupted")
        // Activity state is managed by whatever interrupted the sit
      }
      // Track move commands and set WALKING state
      onTargetSet = { _ ->
        moveCommandCount++
        Log.d(TAG, "Move command $moveCommandCount/$MAX_MOVE_COMMANDS - setting WALKING state")

        // Stop facing player while walking
        petLocomotion.stopFacingPlayer()

        // Set WALKING state (still has attention)
        currentActivity = AttentionActivity.WALKING
        showAttentionIndicator()  // Keep showing indicator during walk

        if (moveCommandCount >= MAX_MOVE_COMMANDS) {
          Log.d(TAG, "Pet tired of being bossed around - will lose attention after walk")
        }
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

  // QR Code System using MRUK tracker
  private lateinit var qrCodeSystem: QRCodeSystem
  private var qrScanCallback: ((String?) -> Unit)? = null
  private var isQRScanning by mutableStateOf(false)

  companion object {
    private const val TAG = "ImmersiveActivity"
    private const val SCENE_PERMISSION = "com.oculus.permission.USE_SCENE"
    private const val SCENE_PERMISSION_REQUEST = 1002
    private const val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
    private const val CAMERA_PERMISSION_REQUEST = 1003
    private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
    private const val HEADSET_CAMERA_PERMISSION_REQUEST = 1004
    const val EDGE_THICKNESS = 0.02f // 2cm edge thickness for room bounds
  }

  // MRUK Feature for scene-aware raycasting
  private lateinit var mrukFeature: MRUKFeature

  // QR Scanner using Camera2 + ML Kit (more reliable than MRUK QR tracker)
  private var qrScannerManager: QRScannerManager? = null

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
    requestCameraPermission()  // Request camera permission at startup

    // Initialize QR Code System using MRUK tracker
    qrCodeSystem = QRCodeSystem { petId ->
      // QR code detected - call the callback on main thread
      runOnUiThread {
        Log.d(TAG, "QR Code callback with pet ID: $petId")
        isQRScanning = false
        qrScanCallback?.invoke(petId)
        qrScanCallback = null
        // Stop the tracker after successful scan
        mrukFeature.stopTrackers()
      }
    }
    systemManager.registerSystem(qrCodeSystem)
    Log.d(TAG, "QRCodeSystem registered")

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

  fun requestCameraPermission() {
    Log.d(TAG, "=== CHECKING CAMERA PERMISSION ===")
    val currentPermission = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
    if (currentPermission != PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Camera permission NOT granted - requesting...")
      ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_REQUEST)
    } else {
      Log.d(TAG, "Camera permission ALREADY granted")
    }
  }

  private fun startQRScan(onResult: (String?) -> Unit) {
    Log.d(TAG, "Starting QR scan with Camera2 + ML Kit...")

    // Check headset camera permission first
    if (checkSelfPermission(HEADSET_CAMERA_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Headset camera permission not granted, requesting...")
      qrScanCallback = onResult
      requestPermissions(arrayOf(HEADSET_CAMERA_PERMISSION), HEADSET_CAMERA_PERMISSION_REQUEST)
      return
    }

    // Initialize scanner if needed
    if (qrScannerManager == null) {
      qrScannerManager = QRScannerManager(this)
      if (!qrScannerManager!!.initialize()) {
        Log.e(TAG, "Failed to initialize QR scanner")
        onResult(null)
        return
      }
    }

    isQRScanning = true
    qrScanCallback = onResult

    qrScannerManager?.startScanning { result ->
      runOnUiThread {
        Log.d(TAG, "QR scan result: $result")
        isQRScanning = false
        qrScanCallback?.invoke(result)
        qrScanCallback = null
      }
    }
  }

  private fun stopQRScan() {
    Log.d(TAG, "Stopping QR scan...")
    isQRScanning = false
    qrScanCallback = null
    qrScannerManager?.stopScanning()
  }

  private var browserPanelId = 9000  // Counter for dynamic panel IDs
  private var currentBrowserUrl = "https://metapetz.com"  // Current URL for browser panel
  private var roomPickerPanelId = 9500  // Counter for room picker panel IDs

  private fun openBrowserPanel() {
    openBrowserWithUrl("https://metapetz.com")
  }

  private fun openPetPage(petShortId: String) {
    val url = "https://metapetz.com/view/$petShortId"
    Log.d(TAG, "Opening pet page: $url")
    openBrowserWithUrl(url)
  }

  private fun openBrowserWithUrl(url: String) {
    currentBrowserUrl = url
    Log.d(TAG, "Opening browser panel")
    showBrowserPanel = true

    if (browserPanelEntity == null) {
      // Position panel in front of the user at eye level
      val position = Vector3(0f, 1.4f, -1.2f)
      val panelId = browserPanelId++

      // First register the panel dynamically
      registerPanel(
        PanelRegistration(panelId) {
          config {
            width = BROWSER_PANEL_WIDTH
            height = BROWSER_PANEL_HEIGHT
            layoutWidthInDp = BROWSER_PANEL_WIDTH * 1000  // Convert meters to dp
            themeResourceId = R.style.PanelAppThemeTransparent
          }
          composePanel {
            setContent {
              BrowserPanel(
                onClose = ::closeBrowserPanel,
                onEnterPetId = { closeBrowserPanel() },
                initialUrl = currentBrowserUrl
              )
            }
          }
        }
      )

      // Then create the panel entity
      browserPanelEntity = Entity.createPanelEntity(
        panelId,
        Transform(Pose(position, Quaternion(1f, 0f, 0f, 0f))),
        Grabbable(true, GrabbableType.FACE)
      )
      Log.d(TAG, "Browser panel entity created at $position with ID $panelId")
    }
  }

  private fun closeBrowserPanel() {
    Log.d(TAG, "Closing browser panel")
    showBrowserPanel = false

    browserPanelEntity?.destroy()
    browserPanelEntity = null
    Log.d(TAG, "Browser panel entity destroyed")
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

    // Start panel follow job (moves panel closer when user walks away)
    startPanelFollowJob()

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

    // Start petting detection for XP and hearts
    pettingDetector.start()
    Log.d(TAG, "Petting detector started")

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
   * Called when the user recenters (resets their position/boundary).
   * This happens when switching roomscale boundaries or resetting position.
   */
  override fun onRecenter() {
    super.onRecenter()
    Log.d(TAG, "=== ON RECENTER ===")

    // Play bark to indicate recenter detected
    bark1Player.play(Vector3(0f, 1.5f, 0f), 1.0f, false)

    // Check MRUK room state
    val mrukRoom = mrukFeature.getCurrentRoom()
    val mrukUuid = mrukRoom?.anchor?.uuid?.toString()
    val allRooms = mrukFeature.rooms
    val totalRooms = allRooms.size

    Log.d(TAG, "MRUK getCurrentRoom: ${mrukUuid?.take(8) ?: "NULL"}")
    Log.d(TAG, "Our processed room: ${currentProcessedRoomUuid?.take(8) ?: "NULL"}")
    Log.d(TAG, "Total rooms loaded: $totalRooms")
    Log.d(TAG, "isRoomMode: $isRoomMode")

    // List ALL rooms that MRUK knows about with their anchor positions
    Log.d(TAG, "=== ALL MRUK ROOMS ===")
    val roomList = mutableListOf<String>()
    var closestRoom: MRUKRoom? = null
    var closestDist = Float.MAX_VALUE

    allRooms.forEachIndexed { index, room ->
      val uuid = room.anchor.uuid.toString()
      val shortUuid = uuid.take(8)
      val isOurs = uuid == currentProcessedRoomUuid

      // Find floor anchor to get room position
      var floorPos: Vector3? = null
      for (anchorEntity in room.anchors) {
        val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>() ?: continue
        // Check labels for FLOOR
        var isFloor = false
        for (i in 0 until mrukAnchor.labelsCount) {
          if (mrukAnchor.labels[i] == MRUKLabel.FLOOR.name) {
            isFloor = true
            break
          }
        }
        if (isFloor) {
          floorPos = anchorEntity.tryGetComponent<Transform>()?.transform?.t
          break
        }
      }

      val anchorPos = floorPos ?: Vector3(0f, 0f, 0f)
      val distFromOrigin = sqrt(anchorPos.x * anchorPos.x + anchorPos.z * anchorPos.z)

      // Track closest to origin
      if (distFromOrigin < closestDist) {
        closestDist = distFromOrigin
        closestRoom = room
      }

      val marker = if (isOurs) "[OURS]" else ""
      Log.d(TAG, "  Room $index: $shortUuid floor:(${String.format("%.2f", anchorPos.x)},${String.format("%.2f", anchorPos.z)}) dist:${String.format("%.2f", distFromOrigin)} $marker")
      roomList.add("$shortUuid:${String.format("%.1f", distFromOrigin)}m")
    }

    val closestUuid = closestRoom?.anchor?.uuid?.toString()?.take(8) ?: "?"
    Log.d(TAG, "  -> Closest to origin: $closestUuid (${String.format("%.2f", closestDist)}m)")

    // Get viewer pose to see offset from anchor space
    val viewerPose = scene.getViewerPose()
    val vp = viewerPose.t
    Log.d(TAG, "  -> Viewer pose: (${String.format("%.2f", vp.x)}, ${String.format("%.2f", vp.y)}, ${String.format("%.2f", vp.z)})")

    // Check which room's floor the viewer is actually inside (in anchor space)
    // Viewer at (0,0,0) in viewer space, but floors are in anchor space
    // The room we're in should have its floor near viewer position... but coordinate spaces differ
    // Log the offset from viewer to each floor
    Log.d(TAG, "=== VIEWER TO FLOOR OFFSETS ===")
    allRooms.forEachIndexed { index, room ->
      val uuid = room.anchor.uuid.toString().take(8)
      // Find floor anchor
      var floorPos: Vector3? = null
      for (anchorEntity in room.anchors) {
        val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>() ?: continue
        var isFloor = false
        for (i in 0 until mrukAnchor.labelsCount) {
          if (mrukAnchor.labels[i] == MRUKLabel.FLOOR.name) {
            isFloor = true
            break
          }
        }
        if (isFloor) {
          floorPos = anchorEntity.tryGetComponent<Transform>()?.transform?.t
          break
        }
      }
      if (floorPos != null) {
        val offsetX = vp.x - floorPos.x
        val offsetZ = vp.z - floorPos.z
        Log.d(TAG, "  Room $uuid: floor(${String.format("%.2f", floorPos.x)},${String.format("%.2f", floorPos.z)}) offset(${String.format("%.2f", offsetX)},${String.format("%.2f", offsetZ)})")
      }
    }

    // Update debug label with room distances from origin
    val oursShort = currentProcessedRoomUuid?.take(8) ?: "NULL"
    lastRecenterTime = System.currentTimeMillis()  // Set cooldown
    debugHeadPosLabel = "[$totalRooms] ${roomList.joinToString(" ")}\nVP:(${String.format("%.1f", vp.x)},${String.format("%.1f", vp.z)}) Ours:$oursShort"

    // If in room mode and room changed, rebuild
    if (isRoomMode && mrukUuid != null && mrukUuid != currentProcessedRoomUuid) {
      Log.d(TAG, "=== ROOM CHANGED AFTER RECENTER - REBUILDING ===")
      activityScope.launch {
        rebuildForNewRoom(mrukRoom, mrukUuid)
      }
    }

    // If in room mode and multiple rooms exist, show room picker for manual confirmation
    if (isRoomMode && totalRooms > 1) {
      Log.d(TAG, "Multiple rooms detected on recenter - showing room picker")
      showRoomPickerDialog()
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
      HEADSET_CAMERA_PERMISSION_REQUEST -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(TAG, "Headset camera permission GRANTED - starting QR scan")
          // If we have a pending callback, start the scan
          qrScanCallback?.let { callback ->
            startQRScan(callback)
          }
        } else {
          Log.e(TAG, "Headset camera permission DENIED")
          qrScanCallback?.invoke(null)
          qrScanCallback = null
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

  // Flag to prevent multiple concurrent pet spawns
  private var isSpawningPet = false

  private fun spawnPetModel(petName: String, colors: PetColors) {
    // Prevent multiple concurrent spawns
    if (isSpawningPet) {
      Log.d(TAG, "Already spawning a pet, ignoring duplicate spawn request")
      return
    }
    isSpawningPet = true

    // Cancel previous spinning animation
    spinningJob?.cancel()
    spinningJob = null

    // Remove previous pet and accessories if they exist
    cleanupAccessories()
    currentPetEntity?.destroy()
    currentPetEntity = null

    // Load the new pet model from assets
    val modelPath = petModels[petName]
    if (modelPath != null) {
      activityScope.launch {
        try {
          // Create entity with GLB mesh as a child of the panel
          // Initial rotation: 180� around X-axis to flip upright
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

          // Spawn accessories (hats, etc.) from pet data
          currentPetData?.accessories?.let { accessories ->
            spawnAccessories(currentPetEntity!!, accessories)
          }

          // Set wander area based on current head position
          val headEntity = getHeadEntity()
          val headTransform = headEntity?.tryGetComponent<Transform>()?.transform
          val wanderCenterX = headTransform?.t?.x ?: 0f
          val wanderCenterZ = headTransform?.t?.z ?: 0f
          petLocomotion.setWanderArea(wanderCenterX, wanderCenterZ, 2.0f)

          // In room mode, move pet to walkable position BEFORE spin animation
          var spawnPosition: Vector3? = null
          if (isRoomMode && navGrid != null) {
            val walkablePos = navGrid?.getRandomWalkablePoint()
            if (walkablePos != null) {
              // Remove parent to put pet in world space
              currentPetEntity?.setComponent(TransformParent(Entity.nullEntity()))

              // Get current rotation (the flip rotation)
              val currentTransform = currentPetEntity?.tryGetComponent<Transform>()?.transform
              val rotation = currentTransform?.q ?: Quaternion(1f, 0f, 0f, 0f)

              // Add pet model Y offset (feet should be on surface, not center)
              val petModelYOffset = 0.15f
              spawnPosition = Vector3(walkablePos.x, walkablePos.y + petModelYOffset, walkablePos.z)

              currentPetEntity?.setComponent(Transform(Pose(spawnPosition, rotation)))
              Log.d(TAG, "Room mode: positioned pet at walkable spawn $spawnPosition before spin")
            } else {
              Log.w(TAG, "Room mode: no walkable position found, pet stays at panel for spin")
            }
          }

          // Start spinning animation (sets SPAWNING state)
          // Idle wander will start after spin completes
          startSpinning(spawnPosition)

          // Spawn complete - allow new spawns after a brief delay
          delay(500)  // Small delay to ensure everything is set up
          isSpawningPet = false
          Log.d(TAG, "Pet spawn complete, ready for new spawns")
        } catch (e: Exception) {
          Log.e(TAG, "Error loading pet model: ${e.message}", e)
          isSpawningPet = false  // Reset on error too
        }
      }
    } else {
      isSpawningPet = false  // Reset if model path not found
    }
  }

  private val SPAWN_SPIN_DURATION_MS = 3000L  // Spin for 3 seconds on spawn

  // Accessory model mapping: type -> asset path
  private val accessoryModels = mapOf(
    "partyhat" to "models/partyhattex.glb",
    "spinhat" to "models/spinhattex.glb",
    "wizhat" to "models/wizhattex.glb"
  )

  // Hat offset above pet's head (local space, relative to pet entity)
  private val HAT_OFFSET_DEFAULT = Vector3(0f, 0.126f, 0.04f)  // Above head, slightly forward
  private val HAT_OFFSET_JUMP = Vector3(0f, 0.15f, 0.061f)      // During jump: higher + more forward
  private val HAT_OFFSET_EAT = Vector3(0f, 0.10f, 0.073f)       // During eat: lower + more forward (head bends down)
  // 180 degree rotation around X axis to flip upside down
  private val HAT_ROTATION = Quaternion(1f, 0f, 0f, 0f)  // 180° X flip
  private val HAT_BASE_SCALE = 0.15f  // Base scale for hats (they're huge by default)
  private var hatOffsetAnimJob: Job? = null  // Job for animating hat offset

  /**
   * Spawn accessories (hats, etc.) and attach them to the pet entity.
   * Uses similar pattern to bone attachment - TransformParent with local offset.
   */
  private fun spawnAccessories(petEntity: Entity, accessories: List<Accessory>) {
    // Clean up any existing accessories
    cleanupAccessories()

    for (accessory in accessories) {
      val modelPath = accessoryModels[accessory.type]
      if (modelPath == null) {
        Log.w(TAG, "Unknown accessory type: ${accessory.type}")
        continue
      }

      try {
        // Colorize the accessory GLB with ass1/ass2 colors
        val colorizedFile = GlbColorizer.colorizeAccessoryGlb(
          this,
          modelPath,
          accessory,
          "${accessory.type}_${accessory.ass1.replace("#", "")}_${accessory.ass2.replace("#", "")}.glb"
        )

        val meshUri = if (colorizedFile != null) {
          "file://${colorizedFile.absolutePath}"
        } else {
          // Fallback to original model
          Log.w(TAG, "Failed to colorize accessory, using original")
          "apk:///$modelPath"
        }

        // Apply base scale * accessory scale
        val finalScale = HAT_BASE_SCALE * accessory.scale
        Log.d(TAG, "Spawning accessory ${accessory.type} from: $meshUri with scale $finalScale (base=$HAT_BASE_SCALE * acc=${accessory.scale})")

        // Create accessory entity parented to pet
        val accessoryEntity = Entity.create(
          listOf(
            Mesh(meshUri.toUri()),
            Transform(Pose(HAT_OFFSET_DEFAULT, HAT_ROTATION)),
            Scale(Vector3(finalScale, finalScale, finalScale)),
            Visible(true),
            TransformParent(petEntity)
          )
        )

        accessoryEntities.add(accessoryEntity)
        Log.d(TAG, "Spawned accessory: ${accessory.type} (entity id=${accessoryEntity.id})")

      } catch (e: Exception) {
        Log.e(TAG, "Error spawning accessory ${accessory.type}: ${e.message}", e)
      }
    }

    Log.d(TAG, "Spawned ${accessoryEntities.size} accessories")
  }

  /**
   * Clean up all accessory entities
   */
  private fun cleanupAccessories() {
    hatOffsetAnimJob?.cancel()
    hatOffsetAnimJob = null
    for (entity in accessoryEntities) {
      try {
        entity.destroy()
      } catch (e: Exception) {
        Log.w(TAG, "Error destroying accessory entity: ${e.message}")
      }
    }
    accessoryEntities.clear()
  }

  /**
   * Toggle accessory visibility
   */
  private fun setAccessoriesVisible(visible: Boolean) {
    showAccessories = visible
    for (entity in accessoryEntities) {
      try {
        entity.setComponent(Visible(visible))
      } catch (e: Exception) {
        Log.w(TAG, "Error setting accessory visibility: ${e.message}")
      }
    }
    Log.d(TAG, "Accessories visibility set to: $visible")
  }

  /**
   * Animate all accessories to a target offset over duration
   */
  private fun animateHatOffset(targetOffset: Vector3, durationMs: Long = 100L) {
    hatOffsetAnimJob?.cancel()
    hatOffsetAnimJob = activityScope.launch {
      if (accessoryEntities.isEmpty()) return@launch

      // Get current offset from first accessory
      val firstAccessory = accessoryEntities.firstOrNull() ?: return@launch
      val currentTransform = firstAccessory.tryGetComponent<Transform>()?.transform ?: return@launch
      val startOffset = currentTransform.t

      val startTime = System.currentTimeMillis()
      while (isActive) {
        val elapsed = System.currentTimeMillis() - startTime
        val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)

        // Lerp to target offset
        val newOffset = Vector3(
          startOffset.x + (targetOffset.x - startOffset.x) * t,
          startOffset.y + (targetOffset.y - startOffset.y) * t,
          startOffset.z + (targetOffset.z - startOffset.z) * t
        )

        // Apply to all accessories
        for (entity in accessoryEntities) {
          try {
            entity.setComponent(Transform(Pose(newOffset, HAT_ROTATION)))
          } catch (e: Exception) {
            // Entity might be destroyed
          }
        }

        if (t >= 1f) break
        delay(16) // ~60fps
      }
    }
  }

  private fun startSpinning(worldSpawnPosition: Vector3? = null) {
    val entity = currentPetEntity ?: return

    // Set SPAWNING state to prevent other rotations from interfering
    currentActivity = AttentionActivity.SPAWNING
    val isWorldSpace = worldSpawnPosition != null
    Log.d(TAG, "Starting spawn spin animation (${SPAWN_SPIN_DURATION_MS}ms), worldSpace=$isWorldSpace")

    spinningJob = activityScope.launch {
      var angle = 0f
      val rotationSpeed = 0.5f // Degrees per frame (slow rotation)
      var time = 0f // Time tracker for animations
      val startTime = System.currentTimeMillis()

      while (isActive) {
        try {
          // Check if spin duration has elapsed
          val elapsed = System.currentTimeMillis() - startTime
          if (elapsed >= SPAWN_SPIN_DURATION_MS) {
            Log.d(TAG, "Spawn spin complete, transitioning to IDLE and starting wander")
            currentActivity = AttentionActivity.IDLE
            petLocomotion.startIdleWander()  // Start wandering after spin completes
            break
          }

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

          // Calculate position based on whether we're in world space or local (panel) space
          val position = if (worldSpawnPosition != null) {
            // World space: bounce around the spawn position
            Vector3(
                worldSpawnPosition.x + sideToSide,
                worldSpawnPosition.y + bounceHeight,
                worldSpawnPosition.z
            )
          } else {
            // Local space (panel): use original local positions
            Vector3(
                0f + sideToSide,  // X: side-to-side sway
                0.2f + bounceHeight,  // Y: bouncing motion
                0.2f  // Z: fixed distance in front
            )
          }

          // Update entity transform with dancing position
          entity.setComponent(
              Transform(
                  Pose(
                      position,
                      rotation
                  )
              )
          )

          delay(16) // ~60 FPS
        } catch (e: Exception) {
          // Entity might have been destroyed, stop spinning
          currentActivity = AttentionActivity.IDLE
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

    // Set mode to OUTSIDE (not room scan mode)
    isRoomMode = false
    Log.d(TAG, "Mode set to OUTSIDE (isRoomMode=false)")

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

    // Clear old NavGrid and debug visualization - OUTSIDE MODE DOES NOT USE NAVGRID/PATHFINDING
    navGrid?.clearDebugVisualization()
    navGrid = null
    petLocomotion.setNavGrid(null)
    petLocomotion.setRoomMode(false)  // Tell locomotion system we're in outside mode
    isDebugGridEnabled = false  // Reset checkbox state
    furnitureQuads.clear()  // Clear furniture debug data
    furnitureDebugSpheres.forEach { it.destroy() }  // Destroy purple corner spheres
    furnitureDebugSpheres.clear()
    // Destroy furniture physics boxes from room mode
    furniturePhysicsBoxes.forEach { it.destroy() }
    furniturePhysicsBoxes.clear()

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

    Log.d(TAG, "Creating $name at $position, size=$wallSize, rotation=$rotation�")

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
   * Reset MRUK-related state for re-scanning.
   * Does NOT recreate MRUKFeature (causes issues with spatial framework).
   */
  private fun resetMrukState() {
    Log.d(TAG, "=== RESETTING MRUK STATE FOR RE-SCAN ===")

    // Remove scene event listener
    sceneEventListener?.let {
      mrukFeature.removeSceneEventListener(it)
      Log.d(TAG, "Removed scene event listener")
    }
    sceneEventListener = null

    // Stop any active trackers
    mrukFeature.stopTrackers()
    mrukFeature.stopEnvironmentRaycaster()
    Log.d(TAG, "Stopped MRUK trackers and raycaster")

    // Destroy procMeshSpawner - will be recreated in scanRoom()
    procMeshSpawner = null
    Log.d(TAG, "Cleared procMeshSpawner")

    // Clear tracked room UUID
    currentProcessedRoomUuid = null
    Log.d(TAG, "Cleared currentProcessedRoomUuid")

    // Clear pending walls
    pendingWalls.clear()
    Log.d(TAG, "Cleared pending walls")

    Log.d(TAG, "MRUK state reset complete")
  }

  // Track if this is a re-scan to force room picker
  private var isRescanMode = false

  /**
   * Scan room using MRUK - loads scene data and triggers Space Setup if needed.
   * This follows the MixedRealitySample pattern for proper room mesh alignment.
   * The AnchorProceduralMesh automatically creates visible meshes for all room anchors.
   */
  private fun scanRoom() {
    Log.d(TAG, "=== SCAN ROOM (MRUK) ===")

    // Set mode to ROOM (room scan mode with pathfinding)
    isRoomMode = true
    Log.d(TAG, "Mode set to ROOM (isRoomMode=true)")

    // Clear all bones when environment is reset
    clearAllBones()

    // Clear any existing manual room data (mutual exclusivity - OUTSIDE MODE DATA)
    if (roomColliderEntities.isNotEmpty()) {
      Log.d(TAG, "Clearing ${roomColliderEntities.size} existing manual room colliders (outside mode)")
      roomColliderEntities.forEach { it.destroy() }
      roomColliderEntities.clear()
    }

    // Clear outside mode floor polygon - ROOM MODE USES NAVGRID INSTEAD
    petLocomotion.setFloorPolygon(null)
    petLocomotion.setRoomMode(true)  // Tell locomotion system we're in room mode

    // Clear any existing room scan data (in case re-scanning)
    clearRoomBoundsEdges()

    // Clear old NavGrid and debug visualization (will be recreated from room scan)
    navGrid?.clearDebugVisualization()
    navGrid = null
    petLocomotion.setNavGrid(null)
    isDebugGridEnabled = false  // Reset checkbox state
    furnitureQuads.clear()  // Clear furniture debug data
    furnitureDebugSpheres.forEach { it.destroy() }  // Destroy purple corner spheres
    furnitureDebugSpheres.clear()
    // Destroy old furniture physics boxes
    furniturePhysicsBoxes.forEach { it.destroy() }
    furniturePhysicsBoxes.clear()
    Log.d(TAG, "Cleared furniture physics boxes for room scan")

    // Recreate procMeshSpawner if it was destroyed
    if (procMeshSpawner == null) {
      Log.d(TAG, "Recreating procMeshSpawner for room scan")
      procMeshSpawner = AnchorProceduralMesh(
          mrukFeature,
          mapOf(
              // Furniture: physics colliders only, no visual mesh (null material)
              MRUKLabel.TABLE to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.COUCH to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.BED to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.STORAGE to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.SCREEN to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.LAMP to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.PLANT to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.OTHER to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.WINDOW_FRAME to AnchorProceduralMeshConfig(null, true),
              MRUKLabel.DOOR_FRAME to AnchorProceduralMeshConfig(null, true),
          )
      )
    }

    // Clear existing MRUK rooms before scanning - this ensures fresh room detection
    // From MRUK sample: mrukFeature.clearRooms() clears all loaded room data
    Log.d(TAG, "Clearing existing MRUK rooms before scan...")
    mrukFeature.clearRooms()
    Log.d(TAG, "MRUK rooms cleared")

    Log.d(TAG, "Requesting scene capture via MRUK...")

    // Request scene capture, then load from device (pattern from MRUK sample)
    // Using mrukFeature.requestSceneCapture() as per MRUK sample
    mrukFeature.requestSceneCapture().whenComplete { _, captureError ->
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
   * Only processes the CURRENT room the user is in (not all scanned rooms).
   */
  private fun loadSceneFromDeviceWithLogging() {
    isRoomProcessing = true
    mrukFeature.loadSceneFromDevice().whenComplete { result: MRUKLoadDeviceResult, error: Throwable? ->
      if (error != null) {
        Log.e(TAG, "loadSceneFromDevice error: ${error.message}", error)
        isRoomProcessing = false
      }
      if (result == MRUKLoadDeviceResult.SUCCESS) {
        Log.d(TAG, "=== MRUK SCENE LOADED SUCCESSFULLY ===")
        logMrukRoomData()

        // Get the CURRENT room - or fall back to first room if detection fails
        var currentRoom = mrukFeature.getCurrentRoom()
        if (currentRoom == null) {
          Log.w(TAG, "getCurrentRoom() returned NULL - falling back to first available room")
          Log.w(TAG, "Total rooms available: ${mrukFeature.rooms.size}")

          // Use first room as fallback
          currentRoom = mrukFeature.rooms.firstOrNull()
          if (currentRoom == null) {
            Log.e(TAG, "No rooms available at all")
            isRoomProcessing = false
            isEnvironmentSetup = false
            return@whenComplete
          }
          Log.d(TAG, "Using first room as fallback: ${currentRoom.anchor.uuid}")
        }

        val roomUuid = currentRoom.anchor.uuid.toString()
        Log.d(TAG, "=== PROCESSING CURRENT ROOM ONLY ===")
        Log.d(TAG, "Current room UUID: $roomUuid")
        Log.d(TAG, "Total rooms loaded: ${mrukFeature.rooms.size} (only processing current)")

        // Process room on background thread to avoid UI jank
        activityScope.launch {
          processRoomAnchors(currentRoom, roomUuid)
        }
      } else {
        Log.e(TAG, "MRUK load failed with result: $result")
        Log.w(TAG, "Please set up your room in Quest Settings > Physical Space > Space Setup")
        isRoomProcessing = false
      }
    }
  }

  /**
   * Process anchors for a specific room. Runs heavy work on background thread.
   */
  private suspend fun processRoomAnchors(room: MRUKRoom, roomUuid: String) {
    // Clear any existing room data first (on main thread for entity operations)
    clearRoomBoundsEdges()

    // Track which room we're processing
    currentProcessedRoomUuid = roomUuid
    currentRoomLabel = "Room: ${roomUuid.take(8)}..."  // Short UUID for display

    Log.d(TAG, "Processing ${room.anchors.size} anchors for current room $roomUuid")

    // Process anchors (entity creation must be on main thread)
    for (anchor in room.anchors) {
      onAnchorAddedHandler(room, anchor)
    }

    // Process any walls that were queued before NavGrid was created
    processPendingWalls()

    // Finalize NavGrid - heavy computation on background thread
    navGrid?.let { grid ->
      Log.d(TAG, "Starting NavGrid finalization on background thread...")

      // Run heavy computation on background thread
      kotlinx.coroutines.withContext(Dispatchers.Default) {
        grid.keepLargestConnectedRegion()
        Log.d(TAG, "NavGrid finalized: ${grid.getWalkableCellCount()} walkable cells")
      }

      // Debug visualization is now lazy - only created when user enables debug grid
      // This avoids creating thousands of entities on room load
      Log.d(TAG, "NavGrid ready (debug visualization deferred until requested)")
    }

    // Mark environment as set up
    isEnvironmentSetup = true
    isRoomProcessing = false

    // Start room change detection
    startRoomChangeDetection()

    // Log comprehensive room summary
    Log.d(TAG, "=== ROOM PROCESSING COMPLETE ===")
    Log.d(TAG, "Room UUID: $roomUuid")
    Log.d(TAG, "NavGrid: ${navGrid?.gridWidth}x${navGrid?.gridHeight} cells, ${navGrid?.getWalkableCellCount()} walkable")
    Log.d(TAG, "NavGrid bounds: X[${navGrid?.minX}, ${navGrid?.maxX}] Z[${navGrid?.minZ}, ${navGrid?.maxZ}]")
    Log.d(TAG, "NavGrid floor Y: ${navGrid?.floorY}")
    Log.d(TAG, "Wall physics colliders: ${roomBoundsPhysicsEntities.size}")
    Log.d(TAG, "Room edge entities (visual): ${roomEdgeEntities.size}")
    Log.d(TAG, "Furniture physics boxes: ${furniturePhysicsBoxes.size}")
    Log.d(TAG, "Physics floor at Y: ${physicsFloorEntity?.tryGetComponent<Transform>()?.transform?.t?.y}")
    Log.d(TAG, "================================")
  }

  // Panel follow distance threshold (meters)
  private val PANEL_FOLLOW_DISTANCE = 2.0f

  /**
   * Start the panel follow job that moves the panel closer when user walks away.
   */
  private fun startPanelFollowJob() {
    panelFollowJob?.cancel()
    panelFollowJob = activityScope.launch {
      Log.d(TAG, "Panel follow job started (threshold: ${PANEL_FOLLOW_DISTANCE}m)")
      while (isActive) {
        delay(1000) // Check every 1 second
        checkPanelFollowDistance()
      }
    }
  }

  /**
   * Check if user is too far from panel and move it closer if needed.
   */
  private fun checkPanelFollowDistance() {
    val panel = panelEntity ?: return
    val headEntity = getHeadEntity() ?: return

    val headTransform = headEntity.tryGetComponent<Transform>()?.transform ?: return
    val panelTransform = panel.tryGetComponent<Transform>()?.transform ?: return

    val headPos = headTransform.t
    val panelPos = panelTransform.t

    // Calculate horizontal distance (ignore Y)
    val dx = headPos.x - panelPos.x
    val dz = headPos.z - panelPos.z
    val distance = kotlin.math.sqrt(dx * dx + dz * dz)

    if (distance > PANEL_FOLLOW_DISTANCE) {
      // Move panel to 1.5 meters in front of user
      val headRot = headTransform.q

      // Get forward direction from head rotation (negative Z is forward)
      val forward = headRot.times(Vector3(0f, 0f, -1f))
      // Flatten to horizontal
      val forwardFlat = Vector3(forward.x, 0f, forward.z)
      val forwardLen = kotlin.math.sqrt(forwardFlat.x * forwardFlat.x + forwardFlat.z * forwardFlat.z)

      if (forwardLen > 0.01f) {
        val normalizedForward = Vector3(forwardFlat.x / forwardLen, 0f, forwardFlat.z / forwardLen)

        // New panel position: 1.5m in front of head, at head height
        val newPanelPos = Vector3(
          headPos.x + normalizedForward.x * 1.5f,
          headPos.y,
          headPos.z + normalizedForward.z * 1.5f
        )

        // Calculate rotation to face the user
        val toUser = Vector3(headPos.x - newPanelPos.x, 0f, headPos.z - newPanelPos.z)
        val yaw = kotlin.math.atan2(toUser.x, toUser.z)
        val panelRot = Quaternion(0f, kotlin.math.sin(yaw / 2f).toFloat(), 0f, kotlin.math.cos(yaw / 2f).toFloat())

        panel.setComponent(Transform(Pose(newPanelPos, panelRot)))
        Log.d(TAG, "Panel moved closer: distance was ${distance}m, now at $newPanelPos")
      }
    }
  }

  /**
   * Start polling for room changes using MRUK's getCurrentRoom().
   * When room changes, plays a bark sound and rebuilds room data.
   */
  private fun startRoomChangeDetection() {
    roomChangeCheckJob?.cancel()
    roomChangeCheckJob = activityScope.launch {
      Log.d(TAG, "=== ROOM CHANGE DETECTION STARTED (MRUK getCurrentRoom) ===")
      Log.d(TAG, "Current processed room: ${currentProcessedRoomUuid?.take(8) ?: "none"}")
      Log.d(TAG, "isRoomMode: $isRoomMode")

      // Bark once to confirm detection started
      playBarkForRoomChange()

      var checkCount = 0
      while (isActive) {
        delay(2000) // Check every 2 seconds

        // Log every check to debug
        Log.d(TAG, "Room check #$checkCount: isRoomMode=$isRoomMode")

        if (!isRoomMode) {
          Log.d(TAG, "  -> Skipping (not in room mode)")
          checkCount++
          continue
        }

        val currentRoom = mrukFeature.getCurrentRoom()
        val currentUuid = currentRoom?.anchor?.uuid?.toString()

        Log.d(TAG, "  -> MRUK getCurrentRoom: ${currentUuid?.take(8) ?: "NULL"}")
        Log.d(TAG, "  -> Our processed room: ${currentProcessedRoomUuid?.take(8) ?: "NULL"}")

        // Update debug label (skip if within 10s of recenter to let that info stay visible)
        val timeSinceRecenter = System.currentTimeMillis() - lastRecenterTime
        if (timeSinceRecenter > 10000) {
          debugHeadPosLabel = "MRUK: ${currentUuid?.take(8) ?: "NULL"} | Ours: ${currentProcessedRoomUuid?.take(8) ?: "NULL"}"
        }

        if (currentUuid != null && currentUuid != currentProcessedRoomUuid) {
          Log.d(TAG, "=== ROOM CHANGE DETECTED (MRUK) ===")
          Log.d(TAG, "Old room: $currentProcessedRoomUuid")
          Log.d(TAG, "New room: $currentUuid")

          // Play bark sound to indicate room change
          playBarkForRoomChange()

          // Clear old room data and rebuild for new room
          rebuildForNewRoom(currentRoom, currentUuid)
        }
        checkCount++
      }
    }
  }

  /**
   * Play a bark sound when room change is detected.
   */
  private fun playBarkForRoomChange() {
    val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
    if (headPos != null) {
      bark1Player.play(headPos, 1.0f, false)
      Log.d(TAG, "BARK! Room change detected")
    }
  }

  /**
   * Show room picker panel in front of the player.
   */
  private fun showRoomPickerDialog() {
    val rooms = mrukFeature.rooms
    if (rooms.isEmpty()) {
      Log.w(TAG, "No rooms available to pick from")
      return
    }

    // Build room info list with furniture labels
    availableRooms = rooms.map { room ->
      val uuid = room.anchor.uuid.toString()
      val shortId = uuid.take(8)

      // Collect furniture labels from anchors (skip room bounds)
      val furnitureLabels = mutableSetOf<String>()
      val boundsLabels = setOf("WALL_FACE", "FLOOR", "CEILING", "INVISIBLE_WALL_FACE")

      for (anchorEntity in room.anchors) {
        val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>() ?: continue
        for (i in 0 until mrukAnchor.labelsCount) {
          val label = mrukAnchor.labels[i] ?: continue
          if (label !in boundsLabels) {
            // Convert SCREAMING_CASE to Title Case
            val readable = label.split("_").joinToString(" ") { word ->
              word.lowercase().replaceFirstChar { it.uppercase() }
            }
            furnitureLabels.add(readable)
          }
        }
      }

      val furnitureStr = if (furnitureLabels.isEmpty()) {
        "Empty room"
      } else {
        furnitureLabels.take(4).joinToString(", ") + if (furnitureLabels.size > 4) "..." else ""
      }

      RoomPickerInfo(
        uuid = uuid,
        shortId = shortId,
        furnitureList = furnitureStr,
        anchorCount = room.anchors.size
      )
    }

    showRoomPicker = true
    Log.d(TAG, "Showing room picker with ${availableRooms.size} rooms")

    // Spawn the room picker panel in front of the player
    spawnRoomPickerPanel()
  }

  /**
   * Spawn room picker panel directly in front of the player.
   */
  private fun spawnRoomPickerPanel() {
    // Destroy existing panel if any
    roomPickerPanelEntity?.destroy()
    roomPickerPanelEntity = null

    // Get player position and forward direction
    val viewerPose = scene.getViewerPose()
    val forward = viewerPose.forward()
    val panelDistance = 1.0f  // 1 meter in front

    // Position panel in front of player
    val panelPos = Vector3(
      viewerPose.t.x + forward.x * panelDistance,
      viewerPose.t.y,  // Same height as head
      viewerPose.t.z + forward.z * panelDistance
    )

    val panelId = roomPickerPanelId++

    // First register the panel dynamically
    registerPanel(
      PanelRegistration(panelId) {
        config {
          width = ROOM_PICKER_PANEL_WIDTH
          height = ROOM_PICKER_PANEL_HEIGHT
          layoutWidthInDp = ROOM_PICKER_PANEL_WIDTH * 1000  // Convert meters to dp
          themeResourceId = R.style.PanelAppThemeTransparent
        }
        composePanel {
          setContent {
            RoomPickerPanel(
              rooms = availableRooms,
              currentRoomUuid = currentProcessedRoomUuid,
              onRoomSelected = { roomInfo -> onRoomSelected(roomInfo) }
            )
          }
        }
      }
    )

    // Then create the panel entity in front of the player
    roomPickerPanelEntity = Entity.createPanelEntity(
      panelId,
      Transform(Pose(panelPos, Quaternion.lookRotation(forward * -1f))),
      Grabbable(true, GrabbableType.FACE)  // Allow grabbing and interaction
    )
    Log.d(TAG, "Spawned room picker panel at $panelPos with ID $panelId, forward=$forward")
    Log.d(TAG, "Room picker panel has ${availableRooms.size} rooms to display")
  }

  /**
   * User selected a room from the picker.
   */
  private fun onRoomSelected(roomInfo: RoomPickerInfo) {
    showRoomPicker = false
    Log.d(TAG, "User selected room: ${roomInfo.shortId} (${roomInfo.furnitureList})")

    // Destroy the picker panel
    roomPickerPanelEntity?.destroy()
    roomPickerPanelEntity = null

    // Find the MRUKRoom by UUID
    val selectedRoom = mrukFeature.rooms.find { it.anchor.uuid.toString() == roomInfo.uuid }
    if (selectedRoom == null) {
      Log.e(TAG, "Could not find room with UUID: ${roomInfo.uuid}")
      return
    }

    // Rebuild for the selected room
    activityScope.launch {
      rebuildForNewRoom(selectedRoom, roomInfo.uuid)
    }
  }

  /**
   * Clear old room's entities and rebuild for a new room.
   */
  private suspend fun rebuildForNewRoom(room: MRUKRoom, roomUuid: String) {
    isRoomProcessing = true

    // Clear all existing room data
    clearRoomBoundsEdges()
    navGrid?.clearDebugVisualization()
    navGrid = null
    petLocomotion.setNavGrid(null)
    furnitureQuads.clear()
    furnitureDebugSpheres.forEach { it.destroy() }
    furnitureDebugSpheres.clear()
    furniturePhysicsBoxes.forEach { it.destroy() }
    furniturePhysicsBoxes.clear()
    pendingWalls.clear()

    Log.d(TAG, "Cleared old room data, rebuilding for new room")

    // Reinitialize NavGrid for new room
    initializeNavGridForRoom(room)

    // Process new room
    processRoomAnchors(room, roomUuid)

    // Teleport pet to new room's walkable area
    teleportPetToNewRoom()
  }

  /**
   * Teleport the pet to a walkable position in the current room.
   * Called when room changes are detected.
   */
  private fun teleportPetToNewRoom() {
    val pet = currentPetEntity ?: return
    val grid = navGrid ?: return

    val walkablePos = grid.getRandomWalkablePoint()
    if (walkablePos == null) {
      Log.w(TAG, "Room change: no walkable position found for pet teleport")
      return
    }

    // Get current rotation to preserve it
    val currentTransform = pet.tryGetComponent<Transform>()?.transform
    val rotation = currentTransform?.q ?: Quaternion(1f, 0f, 0f, 0f)

    // Add pet model Y offset
    val petModelYOffset = 0.15f
    val newPos = Vector3(walkablePos.x, walkablePos.y + petModelYOffset, walkablePos.z)

    // Make sure pet is in world space (not parented)
    pet.setComponent(TransformParent(Entity.nullEntity()))
    pet.setComponent(Transform(Pose(newPos, rotation)))

    // Update wander area to new room
    petLocomotion.setWanderArea(newPos.x, newPos.z, 2.0f)

    Log.d(TAG, "Room change: teleported pet to new room at $newPos")
  }

  /**
   * Initialize NavGrid for a specific room based on its floor anchor.
   */
  private fun initializeNavGridForRoom(room: MRUKRoom) {
    // Find floor anchor to get room bounds
    for (anchor in room.anchors) {
      val mrukAnchor = anchor.tryGetComponent<MRUKAnchor>() ?: continue
      val labels = mutableListOf<String>()
      for (i in 0 until mrukAnchor.labelsCount) {
        mrukAnchor.labels[i]?.let { labels.add(it) }
      }

      if (labels.any { it == MRUKLabel.FLOOR.name }) {
        val transform = getAbsoluteTransform(anchor)  // Use world transform for multi-room support
        val planeComponent = anchor.tryGetComponent<MRUKPlane>() ?: continue

        val floorY = transform.t.y
        val worldPos = transform.t
        val worldRot = transform.q

        // Get floor plane local bounds
        val minLocal = planeComponent.min
        val maxLocal = planeComponent.max

        // Create 4 corners in local space (floor is XY plane, Z=0)
        val localCorners = listOf(
            Vector3(minLocal.x, minLocal.y, 0f),
            Vector3(maxLocal.x, minLocal.y, 0f),
            Vector3(maxLocal.x, maxLocal.y, 0f),
            Vector3(minLocal.x, maxLocal.y, 0f)
        )

        // Transform corners to world space using full rotation
        val worldCorners = localCorners.map { local ->
            val rotated = worldRot.times(local)
            Vector3(worldPos.x + rotated.x, worldPos.y + rotated.y, worldPos.z + rotated.z)
        }

        // Calculate axis-aligned bounding box from world corners
        val minX = worldCorners.minOf { it.x }
        val maxX = worldCorners.maxOf { it.x }
        val minZ = worldCorners.minOf { it.z }
        val maxZ = worldCorners.maxOf { it.z }

        val width = maxX - minX
        val height = maxZ - minZ

        Log.d(TAG, "=== NAVGRID AABB CALCULATION ===")
        Log.d(TAG, "Room UUID: ${room.anchor.uuid}")
        Log.d(TAG, "Floor world pos: (${worldPos.x}, ${worldPos.y}, ${worldPos.z})")
        Log.d(TAG, "Floor world rot: (${worldRot.x}, ${worldRot.y}, ${worldRot.z}, ${worldRot.w})")
        Log.d(TAG, "Local bounds: min=(${minLocal.x}, ${minLocal.y}) max=(${maxLocal.x}, ${maxLocal.y})")
        Log.d(TAG, "World corners:")
        worldCorners.forEachIndexed { i, c -> Log.d(TAG, "  Corner $i: (${c.x}, ${c.y}, ${c.z})") }
        Log.d(TAG, "AABB: X[$minX, $maxX] Z[$minZ, $maxZ]")
        Log.d(TAG, "NavGrid size: ${width}x${height} at floorY=$floorY")
        Log.d(TAG, "================================")

        navGrid = NavGrid(
            cellSize = 0.15f,
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
            floorY = floorY
        )
        petLocomotion.setNavGrid(navGrid)
        updatePhysicsFloorToRoomY(floorY)  // Match physics floor to actual room floor
        break
      }
    }
  }

  /**
   * Log all MRUK room and anchor data.
   */
  private fun logMrukRoomData() {
    val rooms = mrukFeature.rooms
    Log.d(TAG, "")
    Log.d(TAG, "---------------------------------------------------------------")
    Log.d(TAG, "MRUK DATA: ${rooms.size} room(s) found")
    Log.d(TAG, "---------------------------------------------------------------")

    rooms.forEachIndexed { roomIndex, room ->
      Log.d(TAG, "")
      Log.d(TAG, "+-------------------------------------------------------------+")
      Log.d(TAG, "� ROOM $roomIndex                                              �")
      Log.d(TAG, "+-------------------------------------------------------------�")
      Log.d(TAG, "� Room anchor UUID: ${room.anchor.uuid}")
      Log.d(TAG, "� Anchors count: ${room.anchors.size}")
      Log.d(TAG, "+-------------------------------------------------------------+")

      // Log each anchor
      room.anchors.forEachIndexed { anchorIndex, anchorEntity ->
        val mrukAnchor = anchorEntity.tryGetComponent<MRUKAnchor>()
        val transform = anchorEntity.tryGetComponent<Transform>()?.transform

        if (mrukAnchor != null) {
          Log.d(TAG, "")
          Log.d(TAG, "  +-- ANCHOR $anchorIndex --------------------------------------")
          Log.d(TAG, "  � UUID: ${mrukAnchor.uuid}")

          // Transform
          if (transform != null) {
            Log.d(TAG, "  � Position: (${String.format("%.3f", transform.t.x)}, ${String.format("%.3f", transform.t.y)}, ${String.format("%.3f", transform.t.z)})")
            Log.d(TAG, "  � Rotation: (${String.format("%.3f", transform.q.x)}, ${String.format("%.3f", transform.q.y)}, ${String.format("%.3f", transform.q.z)}, ${String.format("%.3f", transform.q.w)})")
          }

          // Try to get all component types on this anchor to see what's available
          Log.d(TAG, "  � Components:")
          Log.d(TAG, "  �   - Has MRUKAnchor: true")
          Log.d(TAG, "  �   - Has Transform: ${anchorEntity.hasComponent<Transform>()}")
          Log.d(TAG, "  �   - Has Box: ${anchorEntity.hasComponent<Box>()}")
          Log.d(TAG, "  �   - Has Physics: ${anchorEntity.hasComponent<Physics>()}")

          Log.d(TAG, "  +------------------------------------------------------------")
        } else {
          Log.d(TAG, "  [ANCHOR $anchorIndex] No MRUKAnchor component")
          if (transform != null) {
            Log.d(TAG, "    Position: (${transform.t.x}, ${transform.t.y}, ${transform.t.z})")
          }
        }
      }
    }

    Log.d(TAG, "")
    Log.d(TAG, "---------------------------------------------------------------")
    Log.d(TAG, "END MRUK DATA")
    Log.d(TAG, "---------------------------------------------------------------")
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
                    if (currentPetData != null) {
                      PetInfoPanel(
                          petData = currentPetData!!,
                          onClose = {
                            currentPet = null
                            currentPetData = null
                            petLocomotion.stopIdleWander() // Stop idle wander
                            petLocomotion.setPetEntity(null, null) // Clear locomotion
                            cleanupAccessories()
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
                      onMinimize = ::minimizeApp,
                      firebaseManager = firebaseManager,
                      isEnvironmentSetup = isEnvironmentSetup,
                      isRoomMode = isRoomMode,
                      isRoomProcessing = isRoomProcessing,
                      isDebugGridEnabled = isDebugGridEnabled,
                      onDebugGridToggle = ::toggleDebugGrid,
                      isRoomMeshVisible = isRoomMeshVisible,
                      onRoomMeshToggle = ::toggleRoomMesh,
                      isFurnitureOccluderVisible = isFurnitureOccluderVisible,
                      onFurnitureOccluderToggle = ::toggleFurnitureOccluder,
                      onRequestCameraPermission = ::requestCameraPermission,
                      onOpenBrowser = ::openBrowserPanel,
                      onStartQRScan = ::startQRScan,
                      onStopQRScan = ::stopQRScan,
                      isQRScanning = isQRScanning,
                      // Pet activity state
                      activityState = currentActivity.name,
                      // Current room label for debug
                      currentRoomLabel = currentRoomLabel,
                      debugHeadPosLabel = debugHeadPosLabel,
                      // NavGrid edit mode
                      isNavGridEditMode = isNavGridEditMode,
                      onNavGridEditModeToggle = ::toggleNavGridEditMode,
                      onRecalculateCulling = ::recalculateNavGridCulling,
                      // Accessories visibility
                      showAccessories = showAccessories,
                      onShowAccessoriesToggle = ::setAccessoriesVisible,
                      // Open pet page
                      onOpenPetPage = ::openPetPage,
                      // Room picker
                      onShowRoomPicker = ::showRoomPickerDialog
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
    pettingDetector.stop()
    // Cleanup any active hearts
    activeHearts.forEach { try { it.destroy() } catch (_: Exception) {} }
    activeHearts.clear()
    attentionResumeJob?.cancel()
    hideAttentionIndicator()
    roomChangeCheckJob?.cancel()
    roomChangeCheckJob = null
    panelFollowJob?.cancel()
    panelFollowJob = null
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
    // Clean up QR scanner
    qrScannerManager?.dispose()
    qrScannerManager = null
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
   * Minimize the app to show Quest home/system menu.
   */
  private fun minimizeApp() {
    Log.d(TAG, "Minimizing app to show Quest home...")
    val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
      addCategory(android.content.Intent.CATEGORY_HOME)
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(homeIntent)
  }

  /**
   * Toggle the NavGrid debug visualization visibility.
   * Creates visualization lazily on first enable to avoid lag during room processing.
   */
  private fun toggleDebugGrid(enabled: Boolean) {
    Log.d(TAG, "Toggle debug grid: $enabled")
    isDebugGridEnabled = enabled
    val grid = navGrid ?: return
    if (enabled) {
      // Create visualization lazily on first enable
      grid.createDebugVisualization(showBlocked = true)
      grid.showDebugVisualization()
    } else {
      grid.hideDebugVisualization()
      // Also disable edit mode when hiding grid
      if (isNavGridEditMode) {
        toggleNavGridEditMode(false)
      }
    }
  }

  /**
   * Toggle NavGrid edit mode - allows pointing at cells to block them.
   */
  private fun toggleNavGridEditMode(enabled: Boolean) {
    Log.d(TAG, "Toggle NavGrid edit mode: $enabled")
    isNavGridEditMode = enabled

    if (enabled) {
      val grid = navGrid ?: return
      val mruk = mrukFeature ?: return

      // Create the edit system if it doesn't exist
      if (navGridEditSystem == null) {
        navGridEditSystem = NavGridEditSystem(
          mrukFeature = mruk,
          getNavGrid = { navGrid },
          floorY = grid.floorY
        )
        systemManager.registerSystem(navGridEditSystem!!)
        Log.d(TAG, "NavGrid edit system registered")
      }
      navGridEditSystem?.isEnabled = true
    } else {
      navGridEditSystem?.isEnabled = false
    }
  }

  /**
   * Recalculate NavGrid culling - removes small disconnected walkable regions.
   * Also refreshes the debug visualization.
   */
  private fun recalculateNavGridCulling() {
    val grid = navGrid ?: run {
      Log.w(TAG, "Cannot recalculate culling - no NavGrid")
      return
    }

    Log.d(TAG, "Recalculating NavGrid culling...")
    val beforeCount = grid.getWalkableCellCount()

    // Run the culling algorithm
    grid.keepLargestConnectedRegion()

    val afterCount = grid.getWalkableCellCount()
    Log.d(TAG, "Culling complete: $beforeCount -> $afterCount walkable cells (removed ${beforeCount - afterCount})")

    // Refresh debug visualization if visible
    if (isDebugGridEnabled) {
      grid.clearDebugVisualization()
      grid.createDebugVisualization(showBlocked = true)
      grid.showDebugVisualization()
    }
  }

  /**
   * Toggle the room mesh visibility (walls, floor, ceiling edges).
   * Physics colliders remain active regardless of visibility - only visual meshes toggle.
   */
  private fun toggleRoomMesh(visible: Boolean) {
    Log.d(TAG, "Toggle room mesh visibility: $visible")
    isRoomMeshVisible = visible

    // Toggle visibility of room edge entities (walls, floor, ceiling)
    for (entity in roomEdgeEntities) {
      entity.setComponent(Visible(visible))
    }

    // Toggle visibility of outside mode room colliders (manual walls)
    for (entity in roomColliderEntities) {
      entity.setComponent(Visible(visible))
    }
    Log.d(TAG, "Toggled ${roomEdgeEntities.size + roomColliderEntities.size} room mesh entities visibility: $visible")
  }

  /**
   * Toggle the furniture occluder visibility.
   * These are the box meshes that occlude objects behind furniture.
   */
  private fun toggleFurnitureOccluder(visible: Boolean) {
    Log.d(TAG, "Toggle furniture occluder visibility: $visible")
    isFurnitureOccluderVisible = visible

    // Toggle visibility of furniture occluder boxes
    for (entity in furnitureDebugSpheres) {
      entity.setComponent(Visible(visible))
    }
    for (entity in furniturePhysicsBoxes) {
      entity.setComponent(Visible(visible))
    }
    Log.d(TAG, "Toggled ${furnitureDebugSpheres.size + furniturePhysicsBoxes.size} furniture occluder entities visibility: $visible")
  }

  /**
   * Called when clap is detected - pet turns to face player and pays attention.
   * Clap always (re)activates attention. Use raise hand gesture for sit command.
   * If already sitting, clap extends the sit duration.
   */
  private fun callPetAttention(silent: Boolean = false) {
    // Don't do anything if no pet is spawned
    if (currentPetEntity == null) {
      Log.d(TAG, "Clap detected but no pet spawned - ignoring")
      return
    }

    // Don't interrupt fetching
    if (currentActivity == AttentionActivity.FETCHING) {
      Log.d(TAG, "Clap ignored - pet is fetching")
      return
    }

    // If already sitting, extend the sit duration
    if (currentActivity == AttentionActivity.SITTING) {
      Log.d(TAG, "Clap while sitting - extending sit duration")
      petLocomotion.extendSit()
      return
    }

    // If already attentive, just reset the timeout (don't trigger sit - use raise hand for that)
    if (currentActivity == AttentionActivity.FACING_PLAYER) {
      Log.d(TAG, "Clap while attentive - resetting attention timeout")
      resetAttentionTimeout()
      return
    }

    Log.d(TAG, "Calling pet attention (silent=$silent)")

    // Play whistle sound at head position (unless silent)
    if (!silent) {
      val headPos = getHeadEntity()?.tryGetComponent<Transform>()?.transform?.t
      if (headPos != null) {
        whistlePlayer.play(headPos, 1.0f, false)
      }
    }

    // Set pet as attentive with FACING_PLAYER activity
    isPetAttentive = true
    currentActivity = AttentionActivity.FACING_PLAYER

    // Reset move command counter for new attention session
    moveCommandCount = 0
    Log.d(TAG, "Move command counter reset for new attention session")

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
   * Trigger sit command from raise hand gesture.
   * Only works if pet is attentive (FACING_PLAYER) or already sitting (extends sit).
   */
  private fun triggerSitFromGesture() {
    // Don't do anything if no pet is spawned
    if (currentPetEntity == null) {
      Log.d(TAG, "Raise hand detected but no pet spawned - ignoring")
      return
    }

    // If already sitting, extend the sit duration
    if (currentActivity == AttentionActivity.SITTING) {
      Log.d(TAG, "Raise hand while sitting - extending sit duration")
      petLocomotion.extendSit()
      return
    }

    // Only trigger sit if pet is attentive (facing player)
    if (currentActivity != AttentionActivity.FACING_PLAYER) {
      Log.d(TAG, "Raise hand ignored - pet not attentive (activity=$currentActivity)")
      return
    }

    Log.d(TAG, "Raise hand while attentive - commanding sit!")
    startPetSit()
  }

  /**
   * Start the sit command - pet sits and faces player.
   * Called from raise hand gesture when pet is attentive.
   */
  private fun startPetSit() {
    Log.d(TAG, "Starting sit command")

    // Play bark when sitting
    playRandomBark()

    // Update activity state
    currentActivity = AttentionActivity.SITTING
    attentionResumeJob?.cancel()  // Cancel any pending attention timeout
    showAttentionIndicator()  // Show indicator during sit (activity != IDLE)

    // Award 2% XP bonus for sitting
    currentPetData?.let { petData ->
      var newXp = petData.xp + SIT_XP_BONUS
      var newLevel = petData.level
      if (newXp >= 1f) {
        newXp = 0f
        newLevel += 1
        Log.d(TAG, "Level up from sit! New level: $newLevel")
        firebaseManager.updatePetLevel("demoUser", petData.firebaseKey, newLevel)
      }
      Log.d(TAG, "Sit XP bonus: +${(SIT_XP_BONUS * 100).toInt()}%, new total: ${(newXp * 100).toInt()}%")
      currentPetData = petData.copy(xp = newXp, level = newLevel)
      firebaseManager.updatePetXp("demoUser", petData.firebaseKey, newXp)
    }

    // Start sit in locomotion
    petLocomotion.startSit { getHeadEntity() }
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
   * Spawn a heart particle at position that floats up and shrinks out.
   * Uses pinkheart.glb model.
   */
  private fun spawnHeartParticle(position: Vector3) {
    try {
      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/pinkheart.glb".toUri()),
              Transform(Pose(position, Quaternion())),
              Scale(Vector3(0.03f, 0.03f, 0.03f)),
              Visible(true)
          )
      )

      activeHearts.add(entity)

      // Animate floating up and shrinking (no material updates to avoid flicker)
      activityScope.launch {
        val startY = position.y
        val duration = 1500L
        val startTime = System.currentTimeMillis()
        val floatHeight = 0.4f
        val wobbleAmount = 0.03f
        val startScale = 0.03f

        while (isActive) {
          val elapsed = System.currentTimeMillis() - startTime
          val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)

          if (t >= 1f) break

          val easedT = 1f - (1f - t) * (1f - t)
          val wobble = sin(t * 5f * PI.toFloat()) * wobbleAmount * (1f - t)
          val newY = startY + floatHeight * easedT
          val newPos = Vector3(position.x + wobble, newY, position.z)

          // Shrink as it rises
          val scale = startScale * (1f - easedT * 0.7f)

          entity.setComponent(Transform(Pose(newPos, Quaternion(0f, elapsed * 0.1f % 360f, 0f))))
          entity.setComponent(Scale(Vector3(scale, scale, scale)))

          delay(16)
        }

        try {
          activeHearts.remove(entity)
          entity.destroy()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to destroy heart: ${e.message}")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn heart particle: ${e.message}", e)
    }
  }

  /**
   * Award XP for petting the pet.
   */
  private fun awardPettingXp() {
    currentPetData?.let { petData ->
      var newXp = petData.xp + PettingDetector.XP_PER_PET
      var newLevel = petData.level

      if (newXp >= 1f) {
        newXp = 0f
        newLevel += 1
        Log.d(TAG, "Level up from petting! New level: $newLevel")
        firebaseManager.updatePetLevel("demoUser", petData.firebaseKey, newLevel)
      }

      firebaseManager.updatePetXp("demoUser", petData.firebaseKey, newXp)
      currentPetData = petData.copy(xp = newXp, level = newLevel)
      Log.d(TAG, "Petting XP awarded: ${(newXp * 100).toInt()}%")
    }
  }

  // Distance tracking for fetch debug
  private var distanceTrackingJob: Job? = null
  private var trackedBone: Entity? = null

  /**
   * Start tracking distance from pet to target bone (for debug UI)
   */
  private fun startDistanceTracking(bone: Entity) {
    stopDistanceTracking()
    trackedBone = bone
    distanceTrackingJob = activityScope.launch {
      while (isActive) {
        try {
          val pet = currentPetEntity
          val petPos = pet?.tryGetComponent<Transform>()?.transform?.t
          val bonePos = trackedBone?.tryGetComponent<Transform>()?.transform?.t

          if (petPos != null && bonePos != null) {
            val dx = bonePos.x - petPos.x
            val dz = bonePos.z - petPos.z
            debugDistanceToBone = kotlin.math.sqrt(dx * dx + dz * dz)
          } else {
            debugDistanceToBone = -1f
          }
        } catch (e: Exception) {
          debugDistanceToBone = -1f
        }
        delay(100)  // Update 10 times per second
      }
    }
  }

  /**
   * Stop distance tracking
   */
  private fun stopDistanceTracking() {
    distanceTrackingJob?.cancel()
    distanceTrackingJob = null
    trackedBone = null
  }

  /**
   * Reset the attention timeout - pet will lose attention after ATTENTION_TIMEOUT_MS.
   * Only applies when activity is FACING_PLAYER (not during FETCHING).
   */
  private fun resetAttentionTimeout() {
    attentionResumeJob?.cancel()

    // Start fading attention indicator
    showAttentionIndicator()

    attentionResumeJob = activityScope.launch {
      delay(ATTENTION_TIMEOUT_MS)

      // Don't timeout during fetching or other locked activities
      if (currentActivity == AttentionActivity.FETCHING) {
        Log.d(TAG, "Attention timeout skipped - pet is fetching")
        return@launch
      }

      Log.d(TAG, "Attention timeout - pet loses attention and resumes wandering")
      isPetAttentive = false
      currentActivity = AttentionActivity.IDLE
      stopXpGain()  // Stop XP accumulation when attention is lost
      hideAttentionIndicator()  // Remove indicator when attention is lost
      petLocomotion.stopFacingPlayer()
      petLocomotion.startIdleWander()
    }
  }

  /**
   * Show the attention indicator above the pet's head.
   * Shows whenever activity is NOT IDLE - follows the pet continuously.
   * Uses plumbob.glb model (like The Sims) with rotation animation.
   */
  private fun showAttentionIndicator() {
    // Already showing
    if (attentionIndicatorEntity != null) return

    val petEntity = currentPetEntity ?: return
    val petTransform = petEntity.tryGetComponent<Transform>()?.transform ?: return
    val indicatorPos = Vector3(
      petTransform.t.x,
      petTransform.t.y + INDICATOR_HEIGHT_OFFSET,
      petTransform.t.z
    )

    // Create plumbob indicator using GLB model with transparency
    attentionIndicatorEntity = Entity.create(
      listOf(
        Mesh("apk:///models/plumbob.glb".toUri()),
        Material().apply {
          baseColor = Color4(0.3f, 1f, 0.5f, 0.6f)  // Transparent green
          unlit = true
        },
        Transform(Pose(indicatorPos, Quaternion())),
        Scale(Vector3(0.03f, 0.03f, 0.03f))  // Much smaller
      )
    )

    Log.d(TAG, "Attention indicator shown (plumbob.glb)")

    // Follow pet continuously with rotation and subtle pulsing
    attentionIndicatorJob = activityScope.launch {
      var time = 0f
      while (isActive && attentionIndicatorEntity != null) {
        delay(30)  // Update ~33 times per second

        val indicator = attentionIndicatorEntity ?: break
        val currentPetTransform = currentPetEntity?.tryGetComponent<Transform>()?.transform

        if (currentPetTransform != null) {
          val newPos = Vector3(
            currentPetTransform.t.x,
            currentPetTransform.t.y + INDICATOR_HEIGHT_OFFSET,
            currentPetTransform.t.z
          )

          // Rotate slowly like Sims plumbob
          time += 0.05f
          val rotation = Quaternion(0f, kotlin.math.sin(time), 0f, kotlin.math.cos(time))

          indicator.setComponent(Transform(Pose(newPos, rotation)))

          // Subtle pulse between 0.9 and 1.1
          val pulseScale = 0.03f * (1f + 0.1f * kotlin.math.sin(time * 2f))
          indicator.setComponent(Scale(Vector3(pulseScale, pulseScale, pulseScale)))
        }
      }
    }
  }

  /**
   * Hide and remove the attention indicator.
   */
  private fun hideAttentionIndicator() {
    attentionIndicatorJob?.cancel()
    attentionIndicatorJob = null
    attentionIndicatorEntity?.destroy()
    attentionIndicatorEntity = null
    Log.d(TAG, "Attention indicator hidden")
  }

  private fun loadGLXF(): Job {
    return activityScope.launch {
      glXFManager.inflateGLXF(
          "apk:///scenes/Composition.glxf".toUri(),
          keyName = "example_key_name",
      )
    }
  }
  private var physicsFloorEntity: Entity? = null

  private fun createPhysicsFloor() {
    // Destroy old floor if exists
    physicsFloorEntity?.destroy()

    val floorY = floorHeight() - 0.02f
    physicsFloorEntity = Entity.create(
        listOf(
            Box(Vector3(20f, 0.1f, 20f)), // Larger, thicker floor collider
            Transform(Pose(Vector3(0f, floorY, 0f))),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = Vector3(20f, 0.1f, 20f)
            }
        )
    )
    Log.d(TAG, "Created physics floor at y=$floorY (20x20m)")
  }

  /**
   * Update physics floor to match actual room floor Y position.
   * Called after room scan completes.
   */
  private fun updatePhysicsFloorToRoomY(roomFloorY: Float) {
    physicsFloorEntity?.destroy()
    physicsFloorEntity = Entity.create(
        listOf(
            Box(Vector3(20f, 0.1f, 20f)),
            Transform(Pose(Vector3(0f, roomFloorY - 0.05f, 0f))),
            Physics().apply {
              state = PhysicsState.KINEMATIC
              shape = "box"
              dimensions = Vector3(20f, 0.1f, 20f)
            }
        )
    )
    Log.d(TAG, "Updated physics floor to room Y=${roomFloorY - 0.05f}")
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

    // Create furniture occluder material - subtle boxes that obscure objects behind
    // Uses fresnel effect to enhance edges, very low opacity to be unobtrusive
    furnitureOccluderMaterial = SceneMaterial.custom(
        "furnitureOccluder",
        arrayOf(
            SceneMaterialAttribute("occluderColor", SceneMaterialDataType.Vector4),
            SceneMaterialAttribute("occluderParams", SceneMaterialDataType.Vector4)
        )
    ).apply {
        setBlendMode(BlendMode.TRANSLUCENT)
        // Dark gray with 0.1 alpha - very subtle
        setAttribute("occluderColor", Vector4(0.2f, 0.2f, 0.2f, 0.1f))
        // x = edge boost (0.5), y = fresnel power (2.0), z = darken amount (0.3)
        setAttribute("occluderParams", Vector4(0.5f, 2.0f, 0.3f, 0f))
    }
    Log.d(TAG, "Furniture occluder material initialized (furnitureOccluder shader)")

    // Keep wallMaterial for backwards compatibility with manual wall creation
    wallMaterial = edgeBoxMaterial

    // Create AnchorProceduralMesh with physics-only furniture colliders (no visual mesh)
    // Visual debug is handled separately to avoid blocking UI raycasts
    procMeshSpawner = AnchorProceduralMesh(
        mrukFeature,
        mapOf(
            // Furniture: physics colliders only, no visual mesh (null material)
            MRUKLabel.TABLE to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.COUCH to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.BED to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.STORAGE to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.SCREEN to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.LAMP to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.PLANT to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.OTHER to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.WINDOW_FRAME to AnchorProceduralMeshConfig(null, true),
            MRUKLabel.DOOR_FRAME to AnchorProceduralMeshConfig(null, true),
        )
    )
    Log.d(TAG, "AnchorProceduralMesh initialized with physics-only colliders")

    // Register scene event listener to handle room loading events
    sceneEventListener = object : MRUKSceneEventListener {
        override fun onRoomAdded(room: MRUKRoom) {
            val roomUuid = room.anchor.uuid.toString()
            Log.d(TAG, "=== MRUK ROOM ADDED === UUID: ${roomUuid.take(8)}")
            // Bark twice for room added
            bark2Player.play(Vector3(0f, 1.5f, 0f), 1.0f, false)
        }

        override fun onRoomUpdated(room: MRUKRoom) {
            val roomUuid = room.anchor.uuid.toString()
            Log.d(TAG, "=== MRUK ROOM UPDATED === UUID: ${roomUuid.take(8)}, current: ${currentProcessedRoomUuid?.take(8)}")

            // Also check what getCurrentRoom returns right now
            val currentRoomNow = mrukFeature.getCurrentRoom()
            val currentRoomUuidNow = currentRoomNow?.anchor?.uuid?.toString()
            Log.d(TAG, "  getCurrentRoom() at this moment: ${currentRoomUuidNow?.take(8) ?: "NULL"}")

            // Log ALL rooms to see their states
            Log.d(TAG, "  === ALL ROOMS STATE ===")
            val allRooms = mrukFeature.rooms
            allRooms.forEachIndexed { idx, r ->
              val rUuid = r.anchor.uuid.toString().take(8)
              val isUpdatedRoom = r.anchor.uuid.toString() == roomUuid
              val isOurRoom = r.anchor.uuid.toString() == currentProcessedRoomUuid
              // Check room's anchor count - maybe the "active" room has more anchors loaded?
              val anchorCount = r.anchors.size
              Log.d(TAG, "    Room $idx: $rUuid anchors=$anchorCount ${if (isUpdatedRoom) "[UPDATED]" else ""} ${if (isOurRoom) "[OURS]" else ""}")
            }

            // Bark for room updated
            bark3Player.play(Vector3(0f, 1.5f, 0f), 1.0f, false)

            // Update debug label showing the updated room
            lastRecenterTime = System.currentTimeMillis()  // Use cooldown to keep this visible
            debugHeadPosLabel = "UPDATED: ${roomUuid.take(8)}\ngetCurrent: ${currentRoomUuidNow?.take(8) ?: "NULL"} rooms:${allRooms.size}"

            // If this is a different room than what we processed, rebuild!
            if (isRoomMode && roomUuid != currentProcessedRoomUuid) {
              Log.d(TAG, "=== ROOM CHANGED VIA onRoomUpdated - REBUILDING ===")
              Log.d(TAG, "  Old room: ${currentProcessedRoomUuid?.take(8)}")
              Log.d(TAG, "  New room: ${roomUuid.take(8)}")
              activityScope.launch {
                rebuildForNewRoom(room, roomUuid)
              }
            }
        }

        override fun onRoomRemoved(room: MRUKRoom) {
            Log.d(TAG, "=== MRUK ROOM REMOVED === UUID: ${room.anchor.uuid.toString().take(8)}")
            // Clean up edge entities when room is removed
            clearRoomBoundsEdges()
        }

        override fun onAnchorAdded(room: MRUKRoom, anchor: Entity) {
            // Only process anchors for the CURRENT room we're tracking
            val roomUuid = room.anchor.uuid.toString()
            if (currentProcessedRoomUuid != null && roomUuid != currentProcessedRoomUuid) {
                Log.d(TAG, "Ignoring anchor from different room: $roomUuid (current: $currentProcessedRoomUuid)")
                return
            }
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
      components.add(Visible(isRoomMeshVisible))
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

              // Check if trigger is held - don't throw if holding trigger or pinching (ButtonA)
              val controller = h.tryGetComponent<Controller>()
              val triggerMask = ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR or ButtonBits.ButtonA
              val isHoldingTrigger = controller != null && (controller.buttonState and triggerMask) != 0

              if (speed > 1f && !isHoldingTrigger) {
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

      // Trigger fetch after bone settles (if pet is spawned and attentive)
      triggerFetchWithDelay(entity)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn thrown bone: ${e.message}", e)
    }
  }

  /**
   * Trigger fetch for a bone after a delay to let physics settle.
   * Pet will go fetch the bone and bring it back.
   */
  private fun triggerFetchWithDelay(bone: Entity) {
    // Only trigger if pet exists
    if (currentPetEntity == null) {
      Log.d(TAG, "No pet spawned, skipping fetch trigger")
      return
    }

    // Already fetching something
    if (petLocomotion.isFetching()) {
      Log.d(TAG, "Pet already fetching, skipping new fetch trigger")
      return
    }

    // Throwing a bone automatically triggers attention and fetch
    // This is a command to the pet - no need to clap first!
    Log.d(TAG, "Bone thrown - auto-activating attention for fetch")

    // Stop any current activity (including sitting)
    petLocomotion.interruptSit()
    petLocomotion.stopIdleWander()
    petLocomotion.stopFacingPlayer()

    // Activate attention and set to fetching mode (locks attention during fetch)
    isPetAttentive = true
    currentActivity = AttentionActivity.FETCHING
    attentionResumeJob?.cancel()  // No timeout during fetch
    showAttentionIndicator()  // Show indicator during fetch (activity != IDLE)

    activityScope.launch {
      // Wait for physics to settle (bone to land/stop bouncing)
      delay(1500)

      // Verify bone still exists and is in our list
      if (bone !in thrownBones) {
        Log.d(TAG, "Bone no longer exists, skipping fetch")
        // Reset attention state if fetch didn't start
        currentActivity = AttentionActivity.IDLE
        isPetAttentive = false
        hideAttentionIndicator()
        petLocomotion.startIdleWander()
        return@launch
      }

      // Verify pet is still available
      if (currentPetEntity == null) {
        Log.d(TAG, "Pet no longer exists, skipping fetch")
        currentActivity = AttentionActivity.IDLE
        isPetAttentive = false
        hideAttentionIndicator()
        return@launch
      }

      Log.d(TAG, "Triggering fetch for bone id=${bone.id}")

      // Start fetch with callback to destroy bone when picked up
      petLocomotion.startFetch(bone) { boneToDestroy ->
        destroyThrownBone(boneToDestroy)
      }
    }
  }

  /**
   * Destroy a thrown bone and remove it from tracking.
   * Called when pet picks up the bone during fetch.
   */
  private fun destroyThrownBone(bone: Entity) {
    try {
      thrownBoneTimes.remove(bone.id)
      thrownBones.remove(bone)
      bone.destroy()
      Log.d(TAG, "Destroyed thrown bone id=${bone.id}, remaining=${thrownBones.size}")
    } catch (e: Exception) {
      Log.e(TAG, "Error destroying thrown bone: ${e.message}")
    }
  }

  /**
   * Spawn a bone that tweens from world position to pet's mouth.
   * The bone starts at boneWorldPos and animates to the mouth over ~300ms.
   */
  private fun spawnMouthBoneWithTween(petEntity: Entity, boneWorldPos: Vector3?): Entity? {
    try {
      // Mouth offset in pet's local space
      val mouthLocalOffset = Vector3(0f, 0f, 0.14f)  // 0 up, 14cm forward
      val mouthRotation = Quaternion(0f, 90f, 0f)        // Sideways in mouth

      // Get pet's current world transform
      val petTransform = petEntity.tryGetComponent<Transform>()?.transform
      if (petTransform == null) {
        Log.w(TAG, "Pet has no transform, spawning directly at mouth")
        return spawnMouthBoneDirectly(petEntity)
      }

      // Calculate mouth world position
      val mouthWorldPos = Vector3(
          petTransform.t.x + (petTransform.q * mouthLocalOffset).x,
          petTransform.t.y + mouthLocalOffset.y,
          petTransform.t.z + (petTransform.q * mouthLocalOffset).z
      )

      // Start position (bone's last world position, or mouth if unknown)
      val startPos = boneWorldPos ?: mouthWorldPos

      // Create bone at start position (NOT parented yet - world space for tween)
      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri()),
              Transform(Pose(startPos, Quaternion())),
              Scale(Vector3(0.3f, 0.3f, 0.3f)),
              Visible(true)
          )
      )

      Log.d(TAG, "Spawned tween bone at $startPos, tweening to mouth at $mouthWorldPos")

      // Animate bone from start to mouth, then parent it
      activityScope.launch {
        val tweenDuration = 300L  // 300ms tween
        val startTime = System.currentTimeMillis()

        while (isActive) {
          val elapsed = System.currentTimeMillis() - startTime
          val t = (elapsed.toFloat() / tweenDuration).coerceIn(0f, 1f)

          // Smooth easing
          val easedT = t * t * (3f - 2f * t)

          // Get current pet position for mouth target (pet may be moving)
          val currentPetTransform = petEntity.tryGetComponent<Transform>()?.transform
          val currentMouthWorld = if (currentPetTransform != null) {
            val rotatedOffset = currentPetTransform.q * mouthLocalOffset
            Vector3(
                currentPetTransform.t.x + rotatedOffset.x,
                currentPetTransform.t.y + mouthLocalOffset.y,
                currentPetTransform.t.z + rotatedOffset.z
            )
          } else mouthWorldPos

          // Interpolate position
          val tweenPos = Vector3(
              startPos.x + (currentMouthWorld.x - startPos.x) * easedT,
              startPos.y + (currentMouthWorld.y - startPos.y) * easedT,
              startPos.z + (currentMouthWorld.z - startPos.z) * easedT
          )

          // Interpolate rotation toward mouth rotation
          val currentPetRot = currentPetTransform?.q ?: Quaternion()
          val targetWorldRot = currentPetRot * mouthRotation
          val tweenRot = Quaternion().slerp(targetWorldRot, easedT)

          entity.setComponent(Transform(Pose(tweenPos, tweenRot)))

          if (t >= 1f) break
          delay(16)
        }

        // Tween complete - now parent to pet for carrying
        try {
          entity.setComponent(Transform(Pose(mouthLocalOffset, mouthRotation)))
          entity.setComponent(Scale(Vector3(0.3f, 0.3f, 0.3f)))
          entity.setComponent(TransformParent(petEntity))
          Log.d(TAG, "Bone tween complete, now parented to pet at offset $mouthLocalOffset")
        } catch (e: Exception) {
          Log.e(TAG, "Failed to parent bone after tween: ${e.message}")
        }
      }

      return entity
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn mouth bone with tween: ${e.message}", e)
      return null
    }
  }

  /**
   * Fallback: spawn bone directly at mouth (no tween)
   */
  private fun spawnMouthBoneDirectly(petEntity: Entity): Entity? {
    try {
      val mouthOffset = Pose(
          Vector3(0f, 0.05f, 0.12f),
          Quaternion(0f, 90f, 0f)
      )
      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri()),
              Transform(mouthOffset),
              Scale(Vector3(0.15f, 0.15f, 0.15f)),
              Visible(true),
              TransformParent(petEntity)
          )
      )
      Log.d(TAG, "Spawned mouth bone directly (no tween)")
      return entity
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn mouth bone directly: ${e.message}", e)
      return null
    }
  }

  /**
   * Spawn a dropped bone at position that can be picked up by the player.
   * This is called when pet completes fetch and drops the bone.
   */
  private fun spawnDroppedBone(position: Vector3) {
    try {
      val entity = Entity.create(
          listOf(
              Mesh("apk:///models/bonew.glb".toUri(), hittable = MeshCollision.LineTest),
              Transform(Pose(position, Quaternion())),
              Scale(Vector3(0.2f, 0.2f, 0.2f)),
              Visible(true),
              Hittable(MeshCollision.LineTest),
              Box(Vector3(0.04f, 0.03f, 0.12f)),
              Physics().apply {
                state = PhysicsState.DYNAMIC
                shape = "box"
                dimensions = Vector3(0.04f, 0.03f, 0.12f)
                restitution = 0.1f
                // No initial velocity - just drop
              }
          )
      )

      // Track as thrown bone so player can pick it up
      thrownBones.add(entity)
      thrownBoneTimes[entity.id] = System.currentTimeMillis()

      Log.d(TAG, "Spawned dropped bone at $position, id=${entity.id}, tracked=${thrownBones.size} bones")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to spawn dropped bone: ${e.message}", e)
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

              // === BONE BOUNDARY CHECKING ===

              // 1. Check if bone escaped room bounds (X/Z) - destroy it
              val grid = navGrid
              if (grid != null) {
                val margin = 0.3f  // 30cm margin before destroying
                val escaped = bonePos.x < grid.minX - margin ||
                              bonePos.x > grid.maxX + margin ||
                              bonePos.z < grid.minZ - margin ||
                              bonePos.z > grid.maxZ + margin

                if (escaped) {
                  Log.d(TAG, "Bone ${bone.id} escaped room bounds at (${bonePos.x}, ${bonePos.z}), destroying")
                  bonesToRemove.add(bone)
                  bone.destroy()
                  continue  // Skip to next bone
                }
              }

              // 2. Floor collision - teleport bones back above floor
              val actualFloorY = navGrid?.floorY ?: floorHeight()
              val minY = actualFloorY + 0.03f  // Bone center should be slightly above floor
              if (bonePos.y < minY) {
                Log.d(TAG, "Bone ${bone.id} below floor: y=${bonePos.y} < $minY, correcting")
                val correctedPos = Vector3(bonePos.x, minY, bonePos.z)
                bone.setComponent(Transform(Pose(correctedPos, boneTransform.q)))
                // Reset velocity to stop continued falling
                val physics = bone.tryGetComponent<Physics>()
                if (physics != null) {
                  physics.linearVelocity = Vector3(0f, 0f, 0f)
                  bone.setComponent(physics)
                }
              }

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

    // Get the anchor's world transform (absolute, not local) for multi-room support
    val anchorPose = getAbsoluteTransform(anchorEntity)
    if (anchorPose == Pose()) {
      Log.d(TAG, "Anchor has no valid Transform, skipping: $anchorLabels")
      return
    }

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

    // Create physics collider for walls and doors (windows stay open so you can throw bones out!)
    val needsWallCollider = anchorLabels.any {
      it == MRUKLabel.WALL_FACE.name ||
      it == MRUKLabel.DOOR_FRAME.name
      // WINDOW_FRAME excluded - bones can escape through windows
    }
    if (needsWallCollider) {
      createWallPhysicsCollider(anchorPose, width, height)
      Log.d(TAG, "Created wall collider for: $anchorLabels")
      // Also block wall footprint in NavGrid
      blockWallInNavGrid(anchorEntity, anchorPose, width)
    }

    // Extract floor polygon from FLOOR anchor
    val isFloor = anchorLabels.any { it == MRUKLabel.FLOOR.name }
    if (isFloor) {
      extractFloorPolygon(anchorPose, planeComponent)
    }
  }

  /**
   * Block a furniture anchor's footprint in the NavGrid.
   * Uses MRUKVolume bounds and getAbsoluteTransform for accurate world-space positioning.
   */
  private fun blockFurnitureInNavGrid(anchorEntity: Entity, anchorPose: Pose, labels: List<String>) {
    // Get the absolute world transform for this anchor entity
    val worldTransform = getAbsoluteTransform(anchorEntity)
    val worldPos = worldTransform.t
    val worldRot = worldTransform.q

    // Try to get volume/plane bounds for debug visualization
    val volumeComponent = anchorEntity.tryGetComponent<MRUKVolume>()
    val planeComponent = anchorEntity.tryGetComponent<MRUKPlane>()

    // Always create debug edges (even if NavGrid not ready yet)
    createFurnitureDebugEdges(worldPos, worldRot, volumeComponent, planeComponent, labels)

    // NavGrid blocking requires grid to exist
    val grid = navGrid
    if (grid == null) {
      Log.d(TAG, "NavGrid not ready, skipping blocking for: $labels (debug edges still created)")
      return
    }

    // Only block furniture that sits on the ground
    // Skip wall-mounted/floating items (> 1.5m above floor)
    val floorY = grid.floorY
    val heightAboveFloor = worldPos.y - floorY
    if (heightAboveFloor > 1.5f) {
      Log.d(TAG, "Skipping NavGrid blocking for wall-mounted furniture: $labels at Y=${worldPos.y} (floor=$floorY, height=${heightAboveFloor}m)")
      return
    }

    // volumeComponent and planeComponent already retrieved above

    val localCorners: List<Vector3>
    var furnitureTopHeight: Float  // Height of the furniture's top surface in world space

    if (volumeComponent != null) {
      // Use MRUKVolume - get the bottom face (floor footprint) corners
      // In anchor-local space: X = width, Y = depth, Z = height
      // Bottom face is at Z = min.z
      val min = volumeComponent.min
      val max = volumeComponent.max
      Log.d(TAG, "=== FURNITURE (Volume): $labels ===")
      Log.d(TAG, "  Volume min=(${"%.3f".format(min.x)}, ${"%.3f".format(min.y)}, ${"%.3f".format(min.z)})")
      Log.d(TAG, "  Volume max=(${"%.3f".format(max.x)}, ${"%.3f".format(max.y)}, ${"%.3f".format(max.z)})")

      // Calculate top surface height: anchor Y + max Z (top of volume in local space)
      furnitureTopHeight = worldPos.y + max.z
      Log.d(TAG, "  Top surface height: ${"%.3f".format(furnitureTopHeight)}m")

      // Bottom face corners (z = min.z for floor footprint)
      // X and Y define the horizontal footprint
      localCorners = listOf(
        Vector3(min.x, min.y, min.z),
        Vector3(max.x, min.y, min.z),
        Vector3(max.x, max.y, min.z),
        Vector3(min.x, max.y, min.z)
      )
    } else if (planeComponent != null) {
      // Fallback to MRUKPlane for 2D surfaces (horizontal surfaces like tabletops)
      val min = planeComponent.min
      val max = planeComponent.max
      Log.d(TAG, "=== FURNITURE (Plane): $labels ===")
      Log.d(TAG, "  Plane min=(${"%.3f".format(min.x)}, ${"%.3f".format(min.y)})")
      Log.d(TAG, "  Plane max=(${"%.3f".format(max.x)}, ${"%.3f".format(max.y)})")

      // For planes, the surface is at the anchor's Y position
      furnitureTopHeight = worldPos.y
      Log.d(TAG, "  Top surface height: ${"%.3f".format(furnitureTopHeight)}m")

      // Plane corners (X = width, Y = depth in plane's local 2D space, Z = 0)
      localCorners = listOf(
        Vector3(min.x, min.y, 0f),
        Vector3(max.x, min.y, 0f),
        Vector3(max.x, max.y, 0f),
        Vector3(min.x, max.y, 0f)
      )
    } else {
      // No volume or plane - use default size and assume ~0.5m height
      Log.d(TAG, "Furniture has no MRUKVolume/MRUKPlane, using default 0.5x0.5m: $labels")
      furnitureTopHeight = worldPos.y + 0.5f  // Assume 0.5m height for unknown furniture
      localCorners = listOf(
        Vector3(-0.25f, 0f, -0.25f),
        Vector3(+0.25f, 0f, -0.25f),
        Vector3(+0.25f, 0f, +0.25f),
        Vector3(-0.25f, 0f, +0.25f)
      )
    }

    // Transform local corners to world space using the absolute transform
    val worldCorners = localCorners.map { local ->
      // Rotate the local point by the world rotation, then add world position
      val rotated = worldRot.times(local)
      Pair(worldPos.x + rotated.x, worldPos.z + rotated.z)
    }

    // Store for debug
    furnitureQuads.add(FurnitureQuad(worldCorners, labels.firstOrNull() ?: "unknown"))
    Log.d(TAG, "=== FURNITURE QUAD: ${labels.firstOrNull()} ===")
    Log.d(TAG, "  World pos: (${"%.3f".format(worldPos.x)}, ${"%.3f".format(worldPos.y)}, ${"%.3f".format(worldPos.z)})")
    Log.d(TAG, "  Top height: ${"%.3f".format(furnitureTopHeight)}m")
    worldCorners.forEachIndexed { i, (x, z) ->
      Log.d(TAG, "  Corner $i: (${"%.3f".format(x)}, ${"%.3f".format(z)})")
    }

    // Block the footprint in NavGrid with furniture height (15cm padding)
    grid.blockPolygonWithHeight(worldCorners, furnitureTopHeight, padding = 0.15f)
    Log.d(TAG, "Blocked furniture polygon in NavGrid: $labels with ${worldCorners.size} corners at height ${"%.2f".format(furnitureTopHeight)}m")
    Log.d(TAG, "NavGrid now has ${grid.getWalkableCellCount()} walkable cells")
    // Note: Physics colliders are now created automatically by AnchorProceduralMesh

    // Create debug edge visualization (visual only, no physics/collision)
    createFurnitureDebugEdges(worldPos, worldRot, volumeComponent, planeComponent, labels)
  }

  /**
   * Create furniture occluder visualization.
   * Uses custom shader with subtle occlusion effect.
   * Creates a box mesh from the 8 corners (4 bottom cyan, 4 top yellow debug points).
   */
  private fun createFurnitureDebugEdges(
      worldPos: Vector3,
      worldRot: Quaternion,
      volumeComponent: MRUKVolume?,
      planeComponent: MRUKPlane?,
      labels: List<String>
  ) {
    // Calculate 8 corners using same transform as the original working sphere code
    val bottomLocalCorners: List<Vector3>
    val topLocalCorners: List<Vector3>

    if (volumeComponent != null) {
      val min = volumeComponent.min
      val max = volumeComponent.max
      bottomLocalCorners = listOf(
        Vector3(min.x, min.y, min.z),
        Vector3(max.x, min.y, min.z),
        Vector3(max.x, max.y, min.z),
        Vector3(min.x, max.y, min.z)
      )
      topLocalCorners = listOf(
        Vector3(min.x, min.y, max.z),
        Vector3(max.x, min.y, max.z),
        Vector3(max.x, max.y, max.z),
        Vector3(min.x, max.y, max.z)
      )
    } else if (planeComponent != null) {
      val min = planeComponent.min
      val max = planeComponent.max
      bottomLocalCorners = listOf(
        Vector3(min.x, min.y, 0f),
        Vector3(max.x, min.y, 0f),
        Vector3(max.x, max.y, 0f),
        Vector3(min.x, max.y, 0f)
      )
      topLocalCorners = listOf(
        Vector3(min.x, min.y, 0.02f),
        Vector3(max.x, min.y, 0.02f),
        Vector3(max.x, max.y, 0.02f),
        Vector3(min.x, max.y, 0.02f)
      )
    } else {
      bottomLocalCorners = listOf(
        Vector3(-0.25f, -0.25f, 0f),
        Vector3(0.25f, -0.25f, 0f),
        Vector3(0.25f, 0.25f, 0f),
        Vector3(-0.25f, 0.25f, 0f)
      )
      topLocalCorners = listOf(
        Vector3(-0.25f, -0.25f, 0.5f),
        Vector3(0.25f, -0.25f, 0.5f),
        Vector3(0.25f, 0.25f, 0.5f),
        Vector3(-0.25f, 0.25f, 0.5f)
      )
    }

    // Transform local corners to world space using full rotation (supports multi-room)
    fun localToWorld(local: Vector3): Vector3 {
      val rotated = worldRot.times(local)
      return Vector3(
        worldPos.x + rotated.x,
        worldPos.y + rotated.y,  // Use rotated.y for correct multi-room support
        worldPos.z + rotated.z
      )
    }

    val bottomWorld = bottomLocalCorners.map { localToWorld(it) }
    val topWorld = topLocalCorners.map { localToWorld(it) }

    // Calculate box center from world corners
    val allCorners = bottomWorld + topWorld
    val centerX = allCorners.map { it.x }.average().toFloat()
    val centerY = allCorners.map { it.y }.average().toFloat()
    val centerZ = allCorners.map { it.z }.average().toFloat()
    val worldCenter = Vector3(centerX, centerY, centerZ)

    // Use the original world rotation directly (not recomputed from corners)
    // This preserves full 3D rotation for multi-room support
    val boxRotation = worldRot

    // Get box dimensions from original local bounds (not from world corners)
    // This avoids floating point errors from recomputing distances
    val width: Float
    val depth: Float
    val height: Float
    if (volumeComponent != null) {
      val min = volumeComponent.min
      val max = volumeComponent.max
      width = max.x - min.x   // Local X = width
      depth = max.y - min.y   // Local Y = depth
      height = max.z - min.z  // Local Z = height
    } else if (planeComponent != null) {
      val min = planeComponent.min
      val max = planeComponent.max
      width = max.x - min.x
      depth = max.y - min.y
      height = 0.02f  // Thin plane
    } else {
      width = 0.5f
      depth = 0.5f
      height = 0.5f
    }

    try {
      // Create the occluder box mesh using SceneMesh.box with furnitureOccluderMaterial
      // Box is created in local space, then transformed by worldRot
      val halfX = width / 2f
      val halfY = depth / 2f   // Depth maps to mesh Y in local space
      val halfZ = height / 2f  // Height maps to mesh Z in local space
      val occluderMesh = SceneMesh.box(
        Vector3(-halfX, -halfY, -halfZ),
        Vector3(halfX, halfY, halfZ),
        furnitureOccluderMaterial
      )

      // Create entity with transform using original world rotation
      val occluderEntity = Entity.create(
        listOf(
          Transform(Pose(worldCenter, boxRotation)),
          Visible(isFurnitureOccluderVisible)
        )
      )

      // Create SceneObject and attach to entity via SceneObjectSystem
      val labelName = labels.firstOrNull() ?: "furniture"
      val sceneObject = SceneObject(scene, occluderMesh, "occluder_${labelName}_${System.currentTimeMillis()}", occluderEntity)
      systemManager.findSystem<SceneObjectSystem>().addSceneObject(
        occluderEntity,
        CompletableFuture<SceneObject>().apply { complete(sceneObject) }
      )

      furnitureDebugSpheres.add(occluderEntity)

      Log.d(TAG, "Created occluder box for $labelName: size=($width, $height, $depth) at ($centerX, $centerY, $centerZ)")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to create occluder box: ${e.message}")
    }
  }

  /**
   * Convert quaternion to Euler angles (pitch, yaw, roll) in degrees.
   */
  private fun quaternionToEuler(q: Quaternion): Vector3 {
    // Roll (x-axis rotation)
    val sinr_cosp = 2f * (q.w * q.x + q.y * q.z)
    val cosr_cosp = 1f - 2f * (q.x * q.x + q.y * q.y)
    val roll = kotlin.math.atan2(sinr_cosp, cosr_cosp)

    // Pitch (y-axis rotation)
    val sinp = 2f * (q.w * q.y - q.z * q.x)
    val pitch = if (kotlin.math.abs(sinp) >= 1f) {
      if (sinp > 0) kotlin.math.PI.toFloat() / 2f else -kotlin.math.PI.toFloat() / 2f
    } else {
      kotlin.math.asin(sinp)
    }

    // Yaw (z-axis rotation)
    val siny_cosp = 2f * (q.w * q.z + q.x * q.y)
    val cosy_cosp = 1f - 2f * (q.y * q.y + q.z * q.z)
    val yaw = kotlin.math.atan2(siny_cosp, cosy_cosp)

    // Convert to degrees
    val toDeg = 180f / kotlin.math.PI.toFloat()
    return Vector3(pitch * toDeg, yaw * toDeg, roll * toDeg)
  }

  /**
   * Extract yaw (Y-axis rotation) from quaternion in radians.
   * For MRUK furniture, this is the rotation around world Y (vertical axis).
   */
  private fun quaternionToYaw(q: Quaternion): Float {
    val siny_cosp = 2f * (q.w * q.y + q.x * q.z)
    val cosy_cosp = 1f - 2f * (q.x * q.x + q.y * q.y)
    return kotlin.math.atan2(siny_cosp, cosy_cosp)
  }

  /**
   * Block a wall's footprint in the NavGrid.
   * Walls are vertical planes, so we project their width onto the floor.
   */
  private fun blockWallInNavGrid(anchorEntity: Entity, anchorPose: Pose, width: Float) {
    // Get the absolute world transform
    val worldTransform = getAbsoluteTransform(anchorEntity)
    val worldPos = worldTransform.t
    val worldRot = worldTransform.q

    // If NavGrid doesn't exist yet, queue this wall for later processing
    val grid = navGrid
    if (grid == null) {
      pendingWalls.add(PendingWall(worldPos, worldRot, width))
      Log.d(TAG, "Queued wall for later blocking: width=$width at (${worldPos.x}, ${worldPos.z})")
      return
    }

    // Process this wall now
    processWallBlocking(grid, worldPos, worldRot, width)
  }

  /**
   * Process wall blocking - creates debug spheres and blocks NavGrid cells.
   */
  private fun processWallBlocking(grid: NavGrid, worldPos: Vector3, worldRot: Quaternion, width: Float) {

    // Wall is a vertical plane - get the two bottom corners
    // In local space, wall extends from -width/2 to +width/2 along X axis
    val halfWidth = width / 2f
    val wallThickness = 0.30f  // 30cm thick wall blocking (for visibility)

    // Local corners of wall footprint (a thin rectangle along the wall base)
    val localCorners = listOf(
      Vector3(-halfWidth, -wallThickness / 2f, 0f),
      Vector3(+halfWidth, -wallThickness / 2f, 0f),
      Vector3(+halfWidth, +wallThickness / 2f, 0f),
      Vector3(-halfWidth, +wallThickness / 2f, 0f)
    )

    // Transform to world space
    val worldCorners = localCorners.map { local ->
      val rotated = worldRot.times(local)
      Pair(worldPos.x + rotated.x, worldPos.z + rotated.z)
    }

    // Get the two endpoints of the wall (left and right ends along the wall's width)
    val leftEnd = Vector3(-halfWidth, 0f, 0f)
    val rightEnd = Vector3(+halfWidth, 0f, 0f)

    // Transform endpoints to world space
    val leftRotated = worldRot.times(leftEnd)
    val rightRotated = worldRot.times(rightEnd)
    val leftWorldX = worldPos.x + leftRotated.x
    val leftWorldZ = worldPos.z + leftRotated.z
    val rightWorldX = worldPos.x + rightRotated.x
    val rightWorldZ = worldPos.z + rightRotated.z

    // Block points every 10cm along the wall for denser coverage
    val pointSpacing = 0.10f  // 10cm spacing (denser than before)
    val numPoints = maxOf(2, (width / pointSpacing).toInt() + 1)
    val blockRadius = 0.12f  // 12cm blocking radius (24cm total diameter) - ensures overlap

    for (i in 0 until numPoints) {
      val t = if (numPoints > 1) i.toFloat() / (numPoints - 1) else 0.5f
      val pointX = leftWorldX + (rightWorldX - leftWorldX) * t
      val pointZ = leftWorldZ + (rightWorldZ - leftWorldZ) * t

      // Block a small square around this point in the NavGrid (no padding)
      val blockCorners = listOf(
        Pair(pointX - blockRadius, pointZ - blockRadius),
        Pair(pointX + blockRadius, pointZ - blockRadius),
        Pair(pointX + blockRadius, pointZ + blockRadius),
        Pair(pointX - blockRadius, pointZ + blockRadius)
      )
      grid.blockPolygon(blockCorners, padding = 0f)
    }

    Log.d(TAG, "Blocked wall with $numPoints points every 10cm, each blocking ${blockRadius*2}m area")
  }

  /**
   * Process any pending walls that were queued before NavGrid existed.
   */
  private fun processPendingWalls() {
    val grid = navGrid ?: return
    if (pendingWalls.isEmpty()) return

    Log.d(TAG, "Processing ${pendingWalls.size} pending walls")
    for (wall in pendingWalls) {
      processWallBlocking(grid, wall.worldPos, wall.worldRot, wall.width)
    }
    pendingWalls.clear()
    Log.d(TAG, "NavGrid after walls: ${grid.getWalkableCellCount()} walkable cells")
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
    updatePhysicsFloorToRoomY(floorY)  // Match physics floor to actual room floor
    Log.d(TAG, "NavGrid created: ${navGrid?.gridWidth}x${navGrid?.gridHeight} cells, ${navGrid?.getWalkableCellCount()} walkable")
  }

  /**
   * Creates a physics collider for a wall anchor from room scan.
   * The collider matches the wall's full dimensions.
   */
  private fun createWallPhysicsCollider(pose: Pose, width: Float, height: Float) {
    val wallThickness = 0.1f  // 10cm thick
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

      // Create edge entity with transform and visibility (SceneObject handles the mesh)
      val edgePose = Pose(worldPos, centerPose.q)

      val entity = Entity.create(
          listOf(
              Transform(edgePose),
              Visible(isRoomMeshVisible)
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

