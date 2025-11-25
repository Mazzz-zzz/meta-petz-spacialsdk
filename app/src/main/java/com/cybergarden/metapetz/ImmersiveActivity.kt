package com.cybergarden.metapetz

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.TextView
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
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.LayoutXMLPanelRegistration
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.Animated
import com.meta.spatial.toolkit.PlaybackState
import com.meta.spatial.toolkit.PlaybackType
import com.meta.spatial.core.Query
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI

class ImmersiveActivity : AppSystemActivity() {
  private val activityScope = CoroutineScope(Dispatchers.Main)

  lateinit var textView: TextView
  lateinit var webView: WebView
  private var currentPet by mutableStateOf<String?>(null)
  private var currentPetEntity: Entity? = null
  private var pedestalEntity: Entity? = null
  private var spinningJob: Job? = null
  private var panelEntity: Entity? = null
  private var customPetImageUrl: String? = null

  // Firebase Manager for cloud persistence
  lateinit var firebaseManager: FirebaseManager
    private set

  // Replicate Manager for AI background removal
  lateinit var replicateManager: ReplicateManager
    private set

  // Photo Capture Manager for passthrough camera
  lateinit var photoCaptureManager: PhotoCaptureManager
    private set

  private var pendingCameraCallback: ((Bitmap?) -> Unit)? = null
  private var cameraPermissionGranted = false

  companion object {
    private const val TAG = "ImmersiveActivity"
    private const val CAMERA_PERMISSION_REQUEST = 1001
  }

  // Pet model file paths in assets
  private val petModels = mapOf(
      "Cat" to "apk:///models/cat.glb",
      "Dog" to "apk:///models/dog.glb",
      "Bunny" to "apk:///models/bunny.glb",
      "Bird" to "apk:///models/bird.glb",
      "Fish" to "apk:///models/fish.glb",
      "Hamster" to "apk:///models/hamster.glb",
  )

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
            VRFeature(this),
            ComposeFeature()
        )
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      features.add(OVRMetricsFeature(this, OVRMetricsDataModel() { numberOfMeshes() }))
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

    // Initialize Firebase Manager
    firebaseManager = FirebaseManager(applicationContext)
    firebaseManager.updateLastActive()

    // Initialize Replicate Manager for AI background removal
    replicateManager = ReplicateManager(ReplicateManager.DEFAULT_API_TOKEN)

    // Initialize Photo Capture Manager for passthrough camera
    photoCaptureManager = PhotoCaptureManager(applicationContext)
    checkAndRequestCameraPermission()

    // Enable MR mode
    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    scene.enablePassthrough(true)

    loadGLXF()
  }

  override fun onSceneReady() {
    super.onSceneReady()

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
  }

  private fun checkAndRequestCameraPermission() {
    val permission = PhotoCaptureManager.PERMISSION
    if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
      cameraPermissionGranted = true
      initializeCamera()
    } else {
      ActivityCompat.requestPermissions(this, arrayOf(permission), CAMERA_PERMISSION_REQUEST)
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == CAMERA_PERMISSION_REQUEST) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        cameraPermissionGranted = true
        initializeCamera()
        // If there was a pending callback, execute it now
        pendingCameraCallback?.let { callback ->
          capturePhoto(callback)
          pendingCameraCallback = null
        }
      } else {
        Log.e(TAG, "Camera permission denied")
        pendingCameraCallback?.invoke(null)
        pendingCameraCallback = null
      }
    }
  }

  private fun initializeCamera() {
    if (cameraPermissionGranted) {
      val success = photoCaptureManager.initialize()
      Log.d(TAG, "Camera initialized: $success")
    }
  }

  fun capturePhoto(callback: (Bitmap?) -> Unit) {
    if (!cameraPermissionGranted) {
      pendingCameraCallback = callback
      checkAndRequestCameraPermission()
      return
    }
    photoCaptureManager.capturePhoto(callback)
  }

  fun selectCustomPet(glbUrl: String) {
    customPetImageUrl = glbUrl
    currentPet = "Custom"

    Log.d(TAG, "Loading custom 3D pet from: $glbUrl")

    // Cancel previous spinning animation
    spinningJob?.cancel()
    spinningJob = null

    // Remove previous pet and pedestal if they exist
    currentPetEntity?.destroy()
    currentPetEntity = null
    pedestalEntity?.destroy()
    pedestalEntity = null

    activityScope.launch {
      try {
        // Get the panel entity to attach to
        val panel = panelEntity ?: Entity.nullEntity()

        // Create glowing pedestal
        pedestalEntity = Entity.create(
            listOf(
                Mesh("apk:///models/pedestal_glowing.glb".toUri()),
                Transform(
                    Pose(
                        Vector3(0f, 0.0f, 0.2f),
                        Quaternion()
                    )
                ),
                Scale(Vector3(0.25f, 0.1f, 0.25f)),
                TransformParent(panel)
            )
        )

        // Initial rotation: no flip needed for Trellis-generated models
        val initialRotation = Quaternion()

        // Load the custom 3D model from the GLB URL
        // Meta Spatial SDK supports loading from network URLs via NetworkedAssetLoader
        currentPetEntity = Entity.create(
            listOf(
                Mesh(glbUrl.toUri()),
                Transform(
                    Pose(
                        Vector3(0f, 0.2f, 0.2f),
                        initialRotation
                    )
                ),
                Scale(Vector3(0.15f, 0.15f, 0.15f)), // Slightly smaller for custom models
                TransformParent(panel)
            )
        )

        Log.d(TAG, "Custom 3D pet entity created successfully")
        startSpinning()
      } catch (e: Exception) {
        Log.e(TAG, "Error creating custom pet: ${e.message}", e)
      }
    }
  }

  fun selectPet(petName: String) {
    currentPet = petName
    customPetImageUrl = null

    // Cancel previous spinning animation
    spinningJob?.cancel()
    spinningJob = null

    // Remove previous pet and pedestal if they exist
    currentPetEntity?.destroy()
    currentPetEntity = null
    pedestalEntity?.destroy()
    pedestalEntity = null

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

          // Create glowing pedestal to illuminate the pet
          pedestalEntity = Entity.create(
              listOf(
                  Mesh("apk:///models/pedestal_glowing.glb".toUri()),
                  Transform(
                      Pose(
                          // Position pedestal centered in front of panel
                          Vector3(0f, 0.0f, 0.2f),
                          Quaternion()
                      )
                  ),
                  Scale(Vector3(0.25f, 0.1f, 0.25f)), // Scaled to provide good base
                  TransformParent(panel)
              )
          )

          // Create pet entity with basic components
          currentPetEntity = Entity.create(
              listOf(
                  Mesh(modelPath.toUri()),
                  Transform(
                      Pose(
                          // Local position: centered directly in front of panel
                          Vector3(0f, 0.2f, 0.2f),
                          initialRotation
                      )
                  ),
                  Scale(Vector3(0.2f, 0.2f, 0.2f)),
                  TransformParent(panel),
                  // Play built-in GLB animations if they exist (track 0 = first animation)
                  Animated(
                      startTime = System.currentTimeMillis(),
                      playbackState = PlaybackState.PLAYING,
                      playbackType = PlaybackType.LOOP,
                      track = 0
                  )
              )
          )

          // Start spinning animation
          startSpinning()
        } catch (e: Exception) {
          // Error loading pet
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

          // Flip model upright (180° around X-axis) and combine with Y-axis spin
          // X-axis flip: 180 degrees = PI radians
          val xFlipRadians = PI.toFloat()

          // Quaternion for 180° rotation around X-axis (to flip upright)
          val qx = Quaternion(kotlin.math.sin(xFlipRadians / 2).toFloat(), 0f, 0f, kotlin.math.cos(xFlipRadians / 2).toFloat())

          // Quaternion for Y-axis rotation (spinning)
          val qy = Quaternion(0f, kotlin.math.sin(yRotRadians / 2), 0f, kotlin.math.cos(yRotRadians / 2))

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

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        // Registering Pet Info Panel (shows stats when pet is selected)
        ComposeViewPanelRegistration(
            R.id.ui_example,
            composeViewCreator = { _, context ->
              ComposeView(context).apply {
                setContent {
                  if (currentPet != null) {
                    PetInfoPanel(
                        petName = currentPet!!,
                        firebaseManager = firebaseManager,
                        onClose = {
                          currentPet = null
                          currentPetEntity?.destroy()
                          currentPetEntity = null
                          pedestalEntity?.destroy()
                          pedestalEntity = null
                        }
                    )
                  } else {
                    // Show welcome message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                      Text(
                          text = "Select a pet to get started!",
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
                      onCreateCustomPet = ::selectCustomPet,
                      replicateManager = replicateManager,
                      onCapturePhoto = ::capturePhoto
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
    photoCaptureManager.dispose()
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
}
