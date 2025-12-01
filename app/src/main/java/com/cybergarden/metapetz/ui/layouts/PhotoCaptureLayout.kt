package com.cybergarden.metapetz.ui.layouts

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.services.ReplicateManager
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.card.SecondaryCard
import com.meta.spatial.uiset.theme.SpatialColor
import kotlinx.coroutines.launch

@Composable
fun PhotoCaptureLayout(
    replicateManager: ReplicateManager,
    onCapturePhoto: (callback: (Bitmap?) -> Unit) -> Unit,
    onClose: () -> Unit,
    onPetCreated: (String) -> Unit
) {
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGenerating3D by remember { mutableStateOf(false) }
    var generationProgress by remember { mutableStateOf(0) }
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
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                label = "\u2715 Close",
                onClick = onClose
            )
            Text(
                text = "Create Custom Pet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.width(80.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions
        SecondaryCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Create Your Own Pet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Center your subject in view\n" +
                            "2. Take a photo (uses left eye camera)\n" +
                            "3. AI removes background & creates 3D model\n" +
                            "4. Your custom 3D pet appears in MR!",
                    fontSize = 14.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tip: Subject should be centered - capture uses left eye view",
                    fontSize = 12.sp,
                    color = Color(0xFFB8860B),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Camera capture area
        if (capturedBitmap == null) {
            CaptureSection(
                isCapturing = isCapturing,
                onCapture = {
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
                }
            )
        } else {
            CapturedPhotoSection(
                capturedBitmap = capturedBitmap!!,
                isProcessing = isProcessing,
                onRetake = {
                    capturedBitmap = null
                    processedImageUrl = null
                    processedBitmap = null
                    glbModelUrl = null
                    errorMessage = null
                    statusMessage = null
                },
                onProcess = {
                    if (!isProcessing && capturedBitmap != null) {
                        isProcessing = true
                        errorMessage = null
                        statusMessage = "Removing background with AI..."
                        scope.launch {
                            try {
                                val dataUrl = replicateManager.bitmapToDataUrl(capturedBitmap!!)
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
                }
            )
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
            ProcessedImageSection(
                bitmap = bitmap,
                glbModelUrl = glbModelUrl,
                isGenerating3D = isGenerating3D,
                generationProgress = generationProgress,
                onGenerate3D = {
                    if (!isGenerating3D && processedImageUrl != null) {
                        isGenerating3D = true
                        generationProgress = 0
                        errorMessage = null
                        statusMessage = "Generating 3D model... This may take 1-2 minutes."
                        scope.launch {
                            try {
                                val glbUrl = replicateManager.generateModel3D(processedImageUrl!!) { progress ->
                                    generationProgress = progress
                                    statusMessage = "Generating 3D model... $progress%"
                                }
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
                            generationProgress = 0
                        }
                    }
                },
                onUsePet = { url ->
                    scope.launch {
                        val cachedUrl = replicateManager.downloadAndCacheGlb(url)
                        onPetCreated(cachedUrl)
                    }
                }
            )
        }
    }
}

@Composable
private fun CaptureSection(
    isCapturing: Boolean,
    onCapture: () -> Unit
) {
    SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
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
                onClick = onCapture,
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
}

@Composable
private fun CapturedPhotoSection(
    capturedBitmap: Bitmap,
    isProcessing: Boolean,
    onRetake: () -> Unit,
    onProcess: () -> Unit
) {
    SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Captured Photo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
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
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = "Captured Photo",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SecondaryButton(
                    label = "Retake",
                    onClick = onRetake,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    label = if (isProcessing) "Processing..." else "Remove BG",
                    onClick = onProcess,
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

@Composable
private fun ProcessedImageSection(
    bitmap: Bitmap,
    glbModelUrl: String?,
    isGenerating3D: Boolean,
    generationProgress: Int,
    onGenerate3D: () -> Unit,
    onUsePet: (String) -> Unit
) {
    var isCaching by remember { mutableStateOf(false) }

    SecondaryCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (glbModelUrl != null) "3D Pet Ready!" else "Background Removed!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FFFFFF))
                    .border(
                        2.dp,
                        if (glbModelUrl != null) Color(0xFF2196F3) else Color(0xFF4CAF50),
                        RoundedCornerShape(12.dp)
                    ),
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
                if (isGenerating3D) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { generationProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF2196F3),
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                PrimaryButton(
                    label = if (isGenerating3D) "Generating... $generationProgress%" else "Generate 3D Pet",
                    expanded = true,
                    onClick = onGenerate3D,
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
                PrimaryButton(
                    label = if (isCaching) "Loading..." else "Use This 3D Pet",
                    expanded = true,
                    onClick = {
                        if (!isCaching) {
                            isCaching = true
                            onUsePet(glbModelUrl)
                        }
                    },
                    leading = {
                        if (isCaching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Confirm",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}
