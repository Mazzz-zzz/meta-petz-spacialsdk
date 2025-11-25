package com.cybergarden.metapetz

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.toolkit.PanelConstants
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.card.PrimaryCard
import com.meta.spatial.uiset.card.SecondaryCard
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialColor
import com.meta.spatial.uiset.theme.SpatialColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.lightSpatialColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

const val OPTIONS_PANEL_WIDTH = 0.85f
const val OPTIONS_PANEL_HEIGHT = 0.75f

data class PetStats(
    val hunger: Float = 1.0f,        // 0.0 to 1.0 (1.0 = full)
    val happiness: Float = 1.0f,     // 0.0 to 1.0 (1.0 = very happy)
    val health: Float = 1.0f,        // 0.0 to 1.0 (1.0 = healthy)
    val energy: Float = 1.0f,        // 0.0 to 1.0 (1.0 = energized)
    val level: Int = 1,              // Pet level
    val xp: Int = 0,                 // Experience points
    val xpToNextLevel: Int = 100     // XP needed for next level
)

data class Pet(
    val name: String,
    val emoji: String,
    val description: String,
    val trait: String = "Playful"
)

@Composable
fun getPanelTheme(): SpatialColorScheme =
    if (isSystemInDarkTheme()) darkSpatialColorScheme() else lightSpatialColorScheme()

@Composable
@Preview(
    widthDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_WIDTH).toInt(),
    heightDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_HEIGHT).toInt(),
)
fun OptionsPanelPreview() {
  OptionsPanel(onSelectPet = {})
}

@Composable
fun OptionsPanel(
    onSelectPet: (String) -> Unit,
    onCreateCustomPet: ((String) -> Unit)? = null,
    replicateManager: ReplicateManager? = null,
    onCapturePhoto: ((callback: (Bitmap?) -> Unit) -> Unit)? = null
) {
  var showCustomPetCreation by remember { mutableStateOf(false) }

  val pets: List<Pet> = listOf(
      Pet("Cat", "🐱", "A playful feline friend", "Curious"),
      Pet("Dog", "🐶", "A loyal canine companion", "Loyal"),
      Pet("Bunny", "🐰", "A cute hopping rabbit", "Gentle"),
      Pet("Bird", "🐦", "A chirping feathered friend", "Energetic"),
      Pet("Fish", "🐠", "A swimming aquatic pal", "Calm"),
      Pet("Hamster", "🐹", "A tiny furry buddy", "Playful"),
  )

  SpatialTheme(colorScheme = getPanelTheme()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (showCustomPetCreation && replicateManager != null && onCapturePhoto != null) {
        CustomPetCreationScreen(
            replicateManager = replicateManager,
            onCapturePhoto = onCapturePhoto,
            onBack = { showCustomPetCreation = false },
            onPetCreated = { imageUrl ->
              onCreateCustomPet?.invoke(imageUrl)
              showCustomPetCreation = false
            }
        )
      } else {
        // Show Pet Selection Screen
        PetSelectionScreen(
            pets = pets,
            onSelectPet = { pet ->
              onSelectPet(pet.name)
            },
            onCustomPetClick = if (replicateManager != null && onCapturePhoto != null) {
              { showCustomPetCreation = true }
            } else null
        )
      }
    }
  }
}

// Separate composable for the Pet Info Panel
@Composable
fun PetInfoPanel(
    petName: String,
    firebaseManager: FirebaseManager,
    onClose: () -> Unit
) {
  var petStats by remember { mutableStateOf(PetStats()) }
  var isLoading by remember { mutableStateOf(true) }
  var saveCounter by remember { mutableStateOf(0) }

  val pets: List<Pet> = listOf(
      Pet("Cat", "🐱", "A playful feline friend", "Curious"),
      Pet("Dog", "🐶", "A loyal canine companion", "Loyal"),
      Pet("Bunny", "🐰", "A cute hopping rabbit", "Gentle"),
      Pet("Bird", "🐦", "A chirping feathered friend", "Energetic"),
      Pet("Fish", "🐠", "A swimming aquatic pal", "Calm"),
      Pet("Hamster", "🐹", "A tiny furry buddy", "Playful"),
      Pet("Custom", "✨", "Your custom AI pet", "Unique"),
  )

  val pet = pets.find { it.name == petName } ?: return

  // Load saved stats from Firebase on pet selection
  LaunchedEffect(petName) {
    isLoading = true
    firebaseManager.loadPetStats(petName) { savedStats ->
      petStats = savedStats ?: PetStats()
      isLoading = false
    }
  }

  // Save stats to Firebase when they change (debounced)
  LaunchedEffect(saveCounter) {
    if (saveCounter > 0) {
      delay(1000) // Debounce: wait 1 second before saving
      firebaseManager.savePetStats(petName, petStats)
    }
  }

  // Simulate stat decay over time (Tamagotchi-style)
  LaunchedEffect(petName, isLoading) {
    if (isLoading) return@LaunchedEffect
    while (true) {
      delay(5000) // Every 5 seconds
      petStats = petStats.copy(
          hunger = max(0f, petStats.hunger - 0.05f),
          happiness = max(0f, petStats.happiness - 0.03f),
          energy = max(0f, petStats.energy - 0.04f)
      )
      saveCounter++ // Trigger save after decay
    }
  }

  SpatialTheme(colorScheme = getPanelTheme()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      PetInfoScreen(
          pet = pet,
          stats = petStats,
          onStatsUpdate = { newStats ->
            petStats = newStats
            saveCounter++ // Trigger save on care action
          },
          onBack = {
            // Save before closing
            firebaseManager.savePetStats(petName, petStats)
            onClose()
          }
      )
    }
  }
}

@Composable
fun PetSelectionScreen(
    pets: List<Pet>,
    onSelectPet: (Pet) -> Unit,
    onCustomPetClick: (() -> Unit)? = null
) {
  val scrollState = rememberScrollState()

  Column(
      modifier = Modifier.fillMaxSize()
          .verticalScroll(scrollState),
      verticalArrangement = Arrangement.Top
  ) {
    // Header
    Text(
        text = "Choose Your MetaPetz",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = 24.dp)
    )

    // Pet cards in 2 columns
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
      pets.chunked(2).forEach { rowPets ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
          rowPets.forEach { pet ->
            PrimaryCard(
                modifier = Modifier.weight(1f),
                onClick = { onSelectPet(pet) }
            ) {
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier.padding(16.dp)
              ) {
                Text(
                    text = pet.emoji,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = pet.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = pet.trait,
                    fontSize = 14.sp,
                    color = SpatialColor.white90,
                    modifier = Modifier.padding(top = 4.dp)
                )
              }
            }
          }
          // Fill empty space if odd number of pets
          if (rowPets.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      // Custom Pet Card
      if (onCustomPetClick != null) {
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCustomPetClick
        ) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth().padding(20.dp)
          ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create Custom Pet",
                tint = Color(0xFF9C27B0),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
              Text(
                  text = "Create Custom Pet",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
              )
              Text(
                  text = "Use your own photo with AI background removal",
                  fontSize = 12.sp,
                  color = SpatialColor.white90
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun CustomPetCreationScreen(
    replicateManager: ReplicateManager,
    onCapturePhoto: (callback: (Bitmap?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onPetCreated: (String) -> Unit
) {
  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isCapturing by remember { mutableStateOf(false) }
  var isProcessing by remember { mutableStateOf(false) }
  var isGenerating3D by remember { mutableStateOf(false) }
  var processedImageUrl by remember { mutableStateOf<String?>(null) }
  var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var glbModelUrl by remember { mutableStateOf<String?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  Column(
      modifier = Modifier.fillMaxSize()
          .verticalScroll(scrollState),
      verticalArrangement = Arrangement.Top
  ) {
    // Header with back button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
      SecondaryButton(
          label = "← Back",
          onClick = onBack
      )
      Text(
          text = "Custom Pet",
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
      )
      Spacer(modifier = Modifier.width(80.dp)) // Balance the layout
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Instructions
    SecondaryCard(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Create Your Own Pet",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "1. Point at something and take a photo\n" +
                   "2. AI will remove the background\n" +
                   "3. AI will generate a 3D model\n" +
                   "4. Your custom 3D pet appears in MR!",
            fontSize = 14.sp,
            color = SpatialColor.white90,
            lineHeight = 22.sp
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Camera capture area
    if (capturedBitmap == null) {
      // Show capture button when no photo taken yet
      SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
          // Camera icon placeholder
          Box(
              modifier = Modifier
                  .size(120.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0x33FFFFFF))
                  .border(2.dp, Color(0xFF9C27B0), RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center
          ) {
            if (isCapturing) {
              CircularProgressIndicator(color = Color(0xFF9C27B0))
            } else {
              Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = "Camera",
                  tint = Color(0xFF9C27B0),
                  modifier = Modifier.size(48.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          PrimaryButton(
              label = if (isCapturing) "Capturing..." else "Take Photo",
              expanded = true,
              onClick = {
                if (!isCapturing) {
                  isCapturing = true
                  errorMessage = null
                  statusMessage = "Capturing from passthrough camera..."
                  onCapturePhoto { bitmap ->
                    isCapturing = false
                    if (bitmap != null) {
                      capturedBitmap = bitmap
                      statusMessage = "Photo captured! Ready to process."
                    } else {
                      errorMessage = "Failed to capture photo. Please try again."
                      statusMessage = null
                    }
                  }
                }
              },
              leading = {
                if (isCapturing) {
                  CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      color = Color.White,
                      strokeWidth = 2.dp
                  )
                } else {
                  Icon(
                      imageVector = Icons.Filled.Add,
                      contentDescription = "Capture",
                      modifier = Modifier.size(20.dp)
                  )
                }
              }
          )
        }
      }
    } else {
      // Show captured photo preview
      SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
          Text(
              text = "Captured Photo",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
          )
          Spacer(modifier = Modifier.height(12.dp))

          Box(
              modifier = Modifier
                  .size(150.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0x33FFFFFF))
                  .border(2.dp, Color(0xFF9C27B0), RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center
          ) {
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured Photo",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Retake and Process buttons
          Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth()
          ) {
            SecondaryButton(
                label = "Retake",
                onClick = {
                  capturedBitmap = null
                  processedImageUrl = null
                  processedBitmap = null
                  glbModelUrl = null
                  errorMessage = null
                  statusMessage = null
                },
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                label = if (isProcessing) "Processing..." else "Remove BG",
                onClick = {
                  if (!isProcessing && capturedBitmap != null) {
                    isProcessing = true
                    errorMessage = null
                    statusMessage = "Removing background with AI..."
                    scope.launch {
                      try {
                        // Convert bitmap to data URL
                        val dataUrl = replicateManager.bitmapToDataUrl(capturedBitmap!!)
                        // Process with Replicate
                        val result = replicateManager.removeBackground(dataUrl)
                        if (result != null) {
                          processedImageUrl = result
                          processedBitmap = replicateManager.downloadImage(result)
                          statusMessage = "Background removed! Ready to use."
                        } else {
                          errorMessage = "Failed to remove background. Try again."
                          statusMessage = null
                        }
                      } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        statusMessage = null
                      }
                      isProcessing = false
                    }
                  }
                },
                modifier = Modifier.weight(1f),
                leading = {
                  if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                  }
                }
            )
          }
        }
      }
    }

    // Status Message
    statusMessage?.let { status ->
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          text = status,
          color = Color(0xFF4CAF50),
          fontSize = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
      )
    }

    // Error Message
    errorMessage?.let { error ->
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          text = error,
          color = Color(0xFFF44336),
          fontSize = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
      )
    }

    // Preview of processed image and 3D generation
    processedBitmap?.let { bitmap ->
      Spacer(modifier = Modifier.height(20.dp))

      SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
          Text(
              text = if (glbModelUrl != null) "3D Pet Ready!" else "Background Removed!",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
          )
          Spacer(modifier = Modifier.height(12.dp))

          Box(
              modifier = Modifier
                  .size(150.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0x33FFFFFF))
                  .border(2.dp, if (glbModelUrl != null) Color(0xFF2196F3) else Color(0xFF4CAF50), RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center
          ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Custom Pet Preview",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          if (glbModelUrl == null) {
            // Generate 3D button
            PrimaryButton(
                label = if (isGenerating3D) "Generating 3D..." else "Generate 3D Pet",
                expanded = true,
                onClick = {
                  if (!isGenerating3D && processedImageUrl != null) {
                    isGenerating3D = true
                    errorMessage = null
                    statusMessage = "Generating 3D model... This may take 1-2 minutes."
                    scope.launch {
                      try {
                        val glbUrl = replicateManager.generateModel3D(processedImageUrl!!)
                        if (glbUrl != null) {
                          glbModelUrl = glbUrl
                          statusMessage = "3D model generated! Ready to use."
                        } else {
                          errorMessage = "Failed to generate 3D model. Try again."
                          statusMessage = null
                        }
                      } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        statusMessage = null
                      }
                      isGenerating3D = false
                    }
                  }
                },
                leading = {
                  if (isGenerating3D) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                  } else {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = "Generate 3D",
                        modifier = Modifier.size(20.dp)
                    )
                  }
                }
            )
          } else {
            // Use this 3D pet button
            PrimaryButton(
                label = "Use This 3D Pet",
                expanded = true,
                onClick = { glbModelUrl?.let { onPetCreated(it) } },
                leading = {
                  Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = "Confirm",
                      modifier = Modifier.size(20.dp)
                  )
                }
            )
          }
        }
      }
    }
  }
}

@Composable
fun PetInfoScreen(
    pet: Pet,
    stats: PetStats,
    onStatsUpdate: (PetStats) -> Unit,
    onBack: () -> Unit
) {
  val scrollState = rememberScrollState()

  Column(
      modifier = Modifier.fillMaxSize()
          .verticalScroll(scrollState),
      verticalArrangement = Arrangement.Top
  ) {
    // Header with back button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
      SecondaryButton(
          label = "← Back",
          onClick = onBack
      )
      Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "Level ${stats.level}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SpatialColor.white90
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = "${stats.xp}/${stats.xpToNextLevel} XP",
              fontSize = 12.sp,
              color = SpatialColor.white90
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Pet display with name
    SecondaryCard(
        modifier = Modifier.fillMaxWidth()
    ) {
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().padding(20.dp)
      ) {
        Text(
            text = pet.emoji,
            fontSize = 64.sp
        )
        Text(
            text = pet.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = getMoodText(stats),
            fontSize = 16.sp,
            color = getMoodColor(stats),
            modifier = Modifier.padding(top = 4.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Stats Display
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
      StatBar("Hunger", stats.hunger, Icons.Filled.ShoppingCart, getStatColor(stats.hunger))
      StatBar("Happiness", stats.happiness, Icons.Filled.Face, getStatColor(stats.happiness))
      StatBar("Health", stats.health, Icons.Filled.Favorite, getStatColor(stats.health))
      StatBar("Energy", stats.energy, Icons.Filled.Star, getStatColor(stats.energy))
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Care Actions
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth()
      ) {
        CareActionButton(
            label = "Feed",
            icon = Icons.Filled.ShoppingCart,
            modifier = Modifier.weight(1f),
            onClick = {
              onStatsUpdate(stats.copy(
                  hunger = (stats.hunger + 0.3f).coerceIn(0f, 1f),
                  xp = stats.xp + 10
              ))
            }
        )
        CareActionButton(
            label = "Play",
            icon = Icons.Filled.Refresh,
            modifier = Modifier.weight(1f),
            onClick = {
              onStatsUpdate(stats.copy(
                  happiness = (stats.happiness + 0.3f).coerceIn(0f, 1f),
                  energy = (stats.energy - 0.1f).coerceIn(0f, 1f),
                  xp = stats.xp + 15
              ))
            }
        )
      }
      Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth()
      ) {
        CareActionButton(
            label = "Clean",
            icon = Icons.Filled.Check,
            modifier = Modifier.weight(1f),
            onClick = {
              onStatsUpdate(stats.copy(
                  health = (stats.health + 0.2f).coerceIn(0f, 1f),
                  xp = stats.xp + 10
              ))
            }
        )
        CareActionButton(
            label = "Rest",
            icon = Icons.Filled.Place,
            modifier = Modifier.weight(1f),
            onClick = {
              onStatsUpdate(stats.copy(
                  energy = (stats.energy + 0.4f).coerceIn(0f, 1f),
                  xp = stats.xp + 5
              ))
            }
        )
      }
    }
  }
}

@Composable
fun StatBar(
    label: String,
    value: Float,
    icon: ImageVector,
    color: Color
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
    Column(modifier = Modifier.weight(1f)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = SpatialColor.white90,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${(value * 100).toInt()}%",
            fontSize = 14.sp,
            color = SpatialColor.white90
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      LinearProgressIndicator(
          progress = { value },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(SpatialTheme.shapes.small),
          color = color,
          trackColor = SpatialColor.white20
      )
    }
  }
}

@Composable
fun CareActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
  PrimaryButton(
      label = label,
      expanded = true,
      onClick = onClick,
      modifier = modifier,
      leading = {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
      }
  )
}

fun getMoodText(stats: PetStats): String {
  val avgStat = (stats.hunger + stats.happiness + stats.health + stats.energy) / 4
  return when {
    avgStat > 0.8f -> "Feeling Great! 😊"
    avgStat > 0.6f -> "Doing Well 🙂"
    avgStat > 0.4f -> "Needs Attention 😐"
    avgStat > 0.2f -> "Not Happy 😟"
    else -> "Critical! 😢"
  }
}

fun getMoodColor(stats: PetStats): Color {
  val avgStat = (stats.hunger + stats.happiness + stats.health + stats.energy) / 4
  return when {
    avgStat > 0.8f -> Color(0xFF4CAF50) // Green
    avgStat > 0.6f -> Color(0xFF8BC34A) // Light Green
    avgStat > 0.4f -> Color(0xFFFFC107) // Amber
    avgStat > 0.2f -> Color(0xFFFF9800) // Orange
    else -> Color(0xFFF44336) // Red
  }
}

fun getStatColor(value: Float): Color {
  return when {
    value > 0.7f -> Color(0xFF4CAF50) // Green
    value > 0.4f -> Color(0xFFFFC107) // Amber
    else -> Color(0xFFF44336) // Red
  }
}
