package com.cybergarden.metapetz.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.model.DEFAULT_PETS
import com.cybergarden.metapetz.model.CUSTOM_PET
import com.cybergarden.metapetz.model.Pet
import com.cybergarden.metapetz.model.PetStats
import com.cybergarden.metapetz.services.FirebaseManager
import com.cybergarden.metapetz.services.ReplicateManager
import com.cybergarden.metapetz.ui.layouts.PetInfoLayout
import com.cybergarden.metapetz.ui.layouts.PetSelectionLayout
import com.cybergarden.metapetz.ui.layouts.PhotoCaptureLayout
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_WIDTH
import com.cybergarden.metapetz.ui.theme.PHOTO_MODAL_HEIGHT
import com.cybergarden.metapetz.ui.theme.PHOTO_MODAL_WIDTH
import com.cybergarden.metapetz.ui.theme.getPanelTheme
import com.meta.spatial.toolkit.PanelConstants
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.card.SecondaryCard
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialColor
import com.meta.spatial.uiset.theme.SpatialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
@Preview(
    widthDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_WIDTH).toInt(),
    heightDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_HEIGHT).toInt(),
)
fun OptionsPanelPreview() {
    OptionsPanel(onSelectPet = {}, onSpawnBone = {})
}

@Composable
fun OptionsPanel(
    onSelectPet: (String) -> Unit,
    onCreateCustomPet: ((String) -> Unit)? = null,
    replicateManager: ReplicateManager? = null,
    onCapturePhoto: ((callback: (Bitmap?) -> Unit) -> Unit)? = null,
    onSpawnBone: (() -> Unit)? = null
) {
    var showCustomPetScreen by remember { mutableStateOf(false) }

    SpatialTheme(colorScheme = getPanelTheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Spawn Bone button
            if (onSpawnBone != null) {
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Spawn Bone Toy",
                    onClick = {
                        Log.d("OptionsPanel", "Spawn Bone button pressed")
                        onSpawnBone()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showCustomPetScreen && replicateManager != null && onCapturePhoto != null) {
                // Show Custom Pet Creation Screen
                PhotoCaptureLayout(
                    replicateManager = replicateManager,
                    onCapturePhoto = onCapturePhoto,
                    onClose = { showCustomPetScreen = false },
                    onPetCreated = { glbUrl ->
                        onCreateCustomPet?.invoke(glbUrl)
                        showCustomPetScreen = false
                    }
                )
            } else {
                // Show Pet Selection Screen
                PetSelectionLayout(
                    pets = DEFAULT_PETS,
                    onSelectPet = { pet ->
                        onSelectPet(pet.name)
                    },
                    onCustomPetClick = if (replicateManager != null && onCapturePhoto != null) {
                        { showCustomPetScreen = true }
                    } else null
                )
            }
        }
    }
}

@Composable
fun PetInfoPanel(
    petName: String,
    firebaseManager: FirebaseManager,
    onClose: () -> Unit
) {
    var petStats by remember { mutableStateOf(PetStats()) }
    var isLoading by remember { mutableStateOf(true) }
    var saveCounter by remember { mutableStateOf(0) }

    val allPets = DEFAULT_PETS + CUSTOM_PET
    val pet = allPets.find { it.name == petName } ?: return

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
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PetInfoLayout(
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

/**
 * Photo Capture Modal - A standalone modal for creating custom pets
 * Uses pinch gesture to capture photos - simpler one-handed flow
 */
@Composable
fun PhotoCaptureModal(
    replicateManager: ReplicateManager,
    onCapturePhoto: (callback: (Bitmap?) -> Unit) -> Unit,
    onClose: () -> Unit,
    onPetCreated: (String) -> Unit,
    onRegisterPinchCallback: (((() -> Unit)?) -> Unit)? = null
) {
    SpatialTheme(colorScheme = getPanelTheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ViewfinderContent(
                replicateManager = replicateManager,
                onCapturePhoto = onCapturePhoto,
                onClose = onClose,
                onPetCreated = onPetCreated,
                onRegisterPinchCallback = onRegisterPinchCallback
            )
        }
    }
}

/**
 * Viewfinder-style UI for photo capture with pinch gesture
 */
@Composable
fun ViewfinderContent(
    replicateManager: ReplicateManager,
    onCapturePhoto: (callback: (Bitmap?) -> Unit) -> Unit,
    onClose: () -> Unit,
    onPetCreated: (String) -> Unit,
    onRegisterPinchCallback: (((() -> Unit)?) -> Unit)? = null
) {
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGenerating3D by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableStateOf(0) }
    var processedImageUrl by remember { mutableStateOf<String?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var glbModelUrl by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Point at your subject") }

    val scope = rememberCoroutineScope()

    // Register pinch callback for photo capture
    LaunchedEffect(Unit) {
        onRegisterPinchCallback?.invoke {
            if (!isCapturing && capturedBitmap == null) {
                isCapturing = true
                statusMessage = "Capturing..."
                onCapturePhoto { bitmap ->
                    isCapturing = false
                    if (bitmap != null) {
                        capturedBitmap = bitmap
                        statusMessage = "Photo captured! Processing..."
                        // Auto-start background removal
                        GlobalScope.launch(Dispatchers.Main) {
                            try {
                                val dataUrl = replicateManager.bitmapToDataUrl(bitmap)
                                isProcessing = true
                                val result = replicateManager.removeBackground(dataUrl)
                                if (result != null) {
                                    processedImageUrl = result
                                    processedBitmap = replicateManager.downloadImage(result)
                                    statusMessage = "Generating 3D model..."
                                    // Auto-start 3D generation
                                    isGenerating3D = true
                                    val glbUrl = replicateManager.generateModel3D(result) { progress ->
                                        generationProgress = progress
                                        statusMessage = "Creating 3D pet... $progress%"
                                    }
                                    if (glbUrl != null) {
                                        glbModelUrl = glbUrl
                                        statusMessage = "3D pet ready! Pinch to use it"
                                    } else {
                                        statusMessage = "Failed to generate 3D. Pinch to retry."
                                    }
                                    isGenerating3D = false
                                }
                                isProcessing = false
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                                isProcessing = false
                                isGenerating3D = false
                            }
                        }
                    } else {
                        statusMessage = "Capture failed. Try again."
                    }
                }
            } else if (glbModelUrl != null) {
                // Pinch again to use the 3D pet
                GlobalScope.launch(Dispatchers.Main) {
                    statusMessage = "Loading pet..."
                    val cachedUrl = replicateManager.downloadAndCacheGlb(glbModelUrl!!)
                    onPetCreated(cachedUrl)
                    onClose()
                }
            }
        }
    }

    // Cleanup callback on dispose
    DisposableEffect(Unit) {
        onDispose {
            onRegisterPinchCallback?.invoke(null)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Custom Pet Camera",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Viewfinder area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x22FFFFFF))
                .border(3.dp, Color(0xFF9C27B0), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (capturedBitmap != null) {
                // Show captured/processed image
                val displayBitmap = processedBitmap ?: capturedBitmap
                Image(
                    bitmap = displayBitmap!!.asImageBitmap(),
                    contentDescription = "Captured",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
                // Overlay progress indicator
                if (isProcessing || isGenerating3D) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0x88000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF9C27B0))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isGenerating3D) "$generationProgress%" else "Processing...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                // Empty viewfinder
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Camera",
                        tint = Color(0xFF9C27B0).copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    if (isCapturing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(color = Color(0xFF9C27B0))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status message
        Text(
            text = statusMessage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Gesture hint
        SecondaryCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\uD83D\uDC4C",
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (glbModelUrl != null) "Pinch to use pet" else "Pinch to capture",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Use your right hand",
                        fontSize = 12.sp,
                        color = SpatialColor.white90
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Close button
        SecondaryButton(
            label = "Close (or \uD83D\uDD90 palm up)",
            onClick = onClose
        )
    }
}
