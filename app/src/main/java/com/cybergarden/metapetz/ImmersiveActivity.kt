package com.cybergarden.metapetz

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
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
  private var currentPet: String? = null
  private var currentPetEntity: Entity? = null
  private var pedestalEntity: Entity? = null
  private var spinningJob: Job? = null
  private var panelEntity: Entity? = null

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
            ComposeFeature(),
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

  fun selectPet(petName: String) {
    currentPet = petName
    textView.text = "Loading your $petName..."
    textView.visibility = View.VISIBLE
    webView.visibility = View.GONE

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
                          // Position pedestal at base, centered and in front of panel
                          Vector3(0f, -0.15f, 0.2f),
                          Quaternion()
                      )
                  ),
                  Scale(Vector3(0.25f, 0.1f, 0.25f)), // Scaled to provide good base
                  TransformParent(panel)
              )
          )

          // Create pet entity positioned on top of pedestal
          currentPetEntity = Entity.create(
              listOf(
                  Mesh(modelPath.toUri()),
                  Transform(
                      Pose(
                          // Local position: centered, slightly above pedestal, in front of panel
                          Vector3(0f, 0.05f, 0.2f),
                          initialRotation
                      )
                  ),
                  Scale(Vector3(0.2f, 0.2f, 0.2f)),
                  TransformParent(panel)
              )
          )

          textView.text = "Meet your new pet $petName! ${petName.lowercase()} is ready to play!"

          // Start spinning animation
          startSpinning()
        } catch (e: Exception) {
          textView.text = "Error loading $petName: ${e.message}\nPath: $modelPath"
        }
      }
    } else {
      textView.text = "Pet model not found for $petName"
    }
  }

  private fun startSpinning() {
    val entity = currentPetEntity ?: return

    spinningJob = activityScope.launch {
      var angle = 0f
      val rotationSpeed = 0.5f // Degrees per frame (slow rotation)

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

          // Update entity transform with local position (relative to panel)
          entity.setComponent(
              Transform(
                  Pose(
                      Vector3(0f, 0.05f, 0.2f), // Local position: on pedestal
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
        // Registering light-weight Views panel
        LayoutXMLPanelRegistration(
            R.id.ui_example,
            layoutIdCreator = { _ -> R.layout.ui_example },
            settingsCreator = { _ -> UIPanelSettings() },
            panelSetupWithRootView = { rootView, _, _ ->
              webView =
                  rootView.findViewById<WebView>(R.id.web_view) ?: return@LayoutXMLPanelRegistration
              textView =
                  rootView.findViewById<TextView>(R.id.text_view)
                      ?: return@LayoutXMLPanelRegistration
              val webSettings = webView.settings
              @SuppressLint("SetJavaScriptEnabled")
              webSettings.javaScriptEnabled = true
              webSettings.mediaPlaybackRequiresUserGesture = false
            },
        ),
        // Registering a Compose panel for pet selection
        ComposeViewPanelRegistration(
            R.id.options_panel,
            composeViewCreator = { _, context ->
              ComposeView(context).apply {
                setContent {
                  OptionsPanel(onSelectPet = ::selectPet)
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
