package com.cybergarden.metapetz.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.model.PetData
import com.cybergarden.metapetz.services.FirebaseManager
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_HEIGHT
import com.cybergarden.metapetz.ui.theme.OPTIONS_PANEL_WIDTH
import com.cybergarden.metapetz.ui.theme.getPanelTheme
import com.meta.spatial.toolkit.PanelConstants
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.ui.text.input.KeyboardType

@Composable
@Preview(
    widthDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_WIDTH).toInt(),
    heightDp = (PanelConstants.DEFAULT_DP_PER_METER * OPTIONS_PANEL_HEIGHT).toInt(),
)
fun OptionsPanelPreview() {
    OptionsPanel(onSelectPet = {}, onSpawnBone = {}, onSetupRoom = {}, onScanRoom = {}, onQuit = {})
}

@Composable
fun OptionsPanel(
    onSelectPet: (String) -> Unit,
    onSelectDemoPet: ((PetData) -> Unit)? = null,
    onSpawnBone: (() -> Unit)? = null,
    onSetupRoom: (() -> Unit)? = null,
    onScanRoom: (() -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
    firebaseManager: FirebaseManager? = null,
    isEnvironmentSetup: Boolean = false,
    isRoomMode: Boolean = false,  // true = room scan mode, false = outside mode
    isDebugGridEnabled: Boolean = false,
    onDebugGridToggle: ((Boolean) -> Unit)? = null,
    isRoomMeshVisible: Boolean = false,
    onRoomMeshToggle: ((Boolean) -> Unit)? = null,
    isFurnitureOccluderVisible: Boolean = true,
    onFurnitureOccluderToggle: ((Boolean) -> Unit)? = null,
    // Debug state values
    isPetAttentive: Boolean = false,
    hasBone: Boolean = false,
    // Fetch debug states
    fetchState: String = "IDLE",  // IDLE, MOVING_TO_BONE, PICKING_UP, RETURNING
    activityState: String = "NONE",  // NONE, FACING_PLAYER, FETCHING
    distanceToBone: Float = -1f,  // -1 = no target bone
    bonePickedUp: Boolean = false,
    returningBone: Boolean = false
) {
    var demoPet by remember { mutableStateOf<PetData?>(null) }
    var claimedPets by remember { mutableStateOf<List<PetData>>(emptyList()) }
    var isLoadingDemoPet by remember { mutableStateOf(true) }
    var isLoadingClaimedPets by remember { mutableStateOf(true) }
    var spawnCooldownRemaining by remember { mutableStateOf(0) }

    // Claim flow state
    var showClaimFlow by remember { mutableStateOf(false) }
    var showPetSelector by remember { mutableStateOf(false) }
    var petIdInput by remember { mutableStateOf("") }
    var claimError by remember { mutableStateOf<String?>(null) }
    var isClaiming by remember { mutableStateOf(false) }

    // Load demo pet and claimed pets on mount
    LaunchedEffect(firebaseManager) {
        if (firebaseManager != null) {
            // Load demo pet
            firebaseManager.getFirstPetFromUser("demoUser") { petData ->
                demoPet = petData
                isLoadingDemoPet = false
                Log.d("OptionsPanel", "Demo pet loaded: ${petData?.name}")
            }
            // Load all claimed pets
            firebaseManager.loadAllClaimedPets { pets ->
                claimedPets = pets
                isLoadingClaimedPets = false
                Log.d("OptionsPanel", "Claimed pets loaded: ${pets.size}")
            }
        } else {
            isLoadingDemoPet = false
            isLoadingClaimedPets = false
        }
    }

    // Cooldown timer - counts down every second
    LaunchedEffect(spawnCooldownRemaining) {
        if (spawnCooldownRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            spawnCooldownRemaining -= 1
        }
    }

    SpatialTheme(colorScheme = getPanelTheme()) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ========== ENVIRONMENT SECTION ==========
            Text(
                text = "Environment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Outside button (5x5 room) - highlighted when active
            if (onSetupRoom != null) {
                val isOutsideActive = isEnvironmentSetup && !isRoomMode
                if (isOutsideActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1B5E20))
                            .clickable {
                                Log.d("OptionsPanel", "Outside button pressed")
                                onSetupRoom()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Outside",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    SecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Outside",
                        onClick = {
                            Log.d("OptionsPanel", "Outside button pressed")
                            onSetupRoom()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Room Scan button (MRUK) - highlighted when active
            if (onScanRoom != null) {
                val isRoomScanActive = isEnvironmentSetup && isRoomMode
                if (isRoomScanActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1B5E20))
                            .clickable {
                                Log.d("OptionsPanel", "Room Scan button pressed")
                                onScanRoom()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Room Scan",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    SecondaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Room Scan",
                        onClick = {
                            Log.d("OptionsPanel", "Room Scan button pressed")
                            onScanRoom()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Room-mode specific toggle (furniture occluder for users)
            if (isEnvironmentSetup && isRoomMode && onFurnitureOccluderToggle != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Furniture Occluder visibility toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isFurnitureOccluderVisible,
                        onCheckedChange = { visible ->
                            Log.d("OptionsPanel", "Furniture occluder toggled: $visible")
                            onFurnitureOccluderToggle(visible)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF9C27B0),
                            uncheckedColor = Color.Gray
                        )
                    )
                    Text(
                        text = "Show Furniture",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== PETS SECTION ==========
            Text(
                text = "Pets",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isEnvironmentSetup) {
                val isOnCooldown = spawnCooldownRemaining > 0
                val petCount = claimedPets.size

                // Show pet selector (when 2+ pets)
                if (showPetSelector && petCount >= 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choose Pet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "X",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showPetSelector = false }
                                    .padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Pet list
                        claimedPets.forEach { pet ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable {
                                        if (!isOnCooldown) {
                                            spawnCooldownRemaining = 5
                                            showPetSelector = false
                                            onSelectDemoPet?.invoke(pet)
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = pet.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )
                                        Text(
                                            text = "Lv.${pet.level} | ${pet.shortId}",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = ">",
                                        fontSize = 18.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Add another pet button
                        SecondaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            label = "+ Add Pet",
                            onClick = {
                                showPetSelector = false
                                showClaimFlow = true
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                // Show claim flow dialog
                else if (showClaimFlow) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Enter Pet ID",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // ID input field
                        BasicTextField(
                            value = petIdInput,
                            onValueChange = {
                                petIdInput = it.uppercase().take(6)
                                claimError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        // Error message
                        if (claimError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = claimError!!,
                                fontSize = 12.sp,
                                color = Color.Red
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecondaryButton(
                                modifier = Modifier.weight(1f),
                                label = "Cancel",
                                onClick = {
                                    showClaimFlow = false
                                    petIdInput = ""
                                    claimError = null
                                }
                            )
                            PrimaryButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (isClaiming) Modifier.alpha(0.5f) else Modifier),
                                label = if (isClaiming) "..." else "Claim",
                                onClick = {
                                    if (isClaiming || petIdInput.length < 4) return@PrimaryButton
                                    if (firebaseManager == null) return@PrimaryButton

                                    isClaiming = true
                                    claimError = null

                                    firebaseManager.claimPet(petIdInput) { success, error, petData ->
                                        isClaiming = false
                                        if (success && petData != null) {
                                            // Refresh claimed pets list
                                            firebaseManager.loadAllClaimedPets { pets ->
                                                claimedPets = pets
                                            }
                                            showClaimFlow = false
                                            petIdInput = ""
                                            // Auto-spawn the claimed pet
                                            spawnCooldownRemaining = 5
                                            onSelectDemoPet?.invoke(petData)
                                        } else {
                                            claimError = error ?: "Failed to claim pet"
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    // Spawn Demo button - only show if user has no claimed pets
                    if (petCount == 0 && demoPet != null && onSelectDemoPet != null && firebaseManager != null) {
                        SecondaryButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isOnCooldown) Modifier.alpha(0.5f) else Modifier),
                            label = if (isOnCooldown) "Spawning... (${spawnCooldownRemaining}s)" else "Spawn Demo",
                            onClick = {
                                if (isOnCooldown) return@SecondaryButton
                                spawnCooldownRemaining = 5
                                firebaseManager.getFirstPetFromUser("demoUser") { freshPetData ->
                                    if (freshPetData != null) {
                                        demoPet = freshPetData
                                        onSelectDemoPet(freshPetData)
                                    } else {
                                        onSelectDemoPet(demoPet!!)
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Your Pet(s) button with + button
                    if (onSelectDemoPet != null && firebaseManager != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (isOnCooldown && petCount == 1) Modifier.alpha(0.5f) else Modifier),
                                label = when {
                                    isLoadingClaimedPets -> "Loading..."
                                    isOnCooldown && petCount == 1 -> "Spawning... (${spawnCooldownRemaining}s)"
                                    petCount == 0 -> "Your Pet"
                                    petCount == 1 -> "Your Pet (${claimedPets[0].name})"
                                    else -> "Your Pets (${petCount})"
                                },
                                onClick = {
                                    when {
                                        petCount == 0 -> {
                                            // No pets - show claim flow
                                            showClaimFlow = true
                                        }
                                        petCount == 1 -> {
                                            // One pet - spawn directly
                                            if (isOnCooldown) return@PrimaryButton
                                            spawnCooldownRemaining = 5
                                            // Refresh and spawn
                                            firebaseManager.loadAllClaimedPets { pets ->
                                                claimedPets = pets
                                                if (pets.isNotEmpty()) {
                                                    onSelectDemoPet(pets[0])
                                                }
                                            }
                                        }
                                        else -> {
                                            // Multiple pets - show selector
                                            showPetSelector = true
                                        }
                                    }
                                }
                            )
                            // Show + button when user has 1+ pets to add more
                            if (petCount >= 1) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF4CAF50))
                                        .clickable { showClaimFlow = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if ((isLoadingDemoPet || isLoadingClaimedPets) && firebaseManager != null && !showClaimFlow && !showPetSelector) {
                    Text(
                        text = "Loading...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Text(
                    text = "Select an environment first",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== ACTIONS SECTION ==========
            Text(
                text = "Actions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Spawn Bone button
            if (onSpawnBone != null && isEnvironmentSetup) {
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Spawn Bone",
                    onClick = {
                        Log.d("OptionsPanel", "Spawn Bone button pressed")
                        onSpawnBone()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Quit button
            if (onQuit != null) {
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Quit",
                    onClick = {
                        Log.d("OptionsPanel", "Quit button pressed")
                        onQuit()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== DEBUG SECTION ==========
            if (isEnvironmentSetup) {
                Text(
                    text = "Debug",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // NavGrid Debug toggle
                if (onDebugGridToggle != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isDebugGridEnabled,
                            onCheckedChange = { enabled ->
                                Log.d("OptionsPanel", "Debug grid toggled: $enabled")
                                onDebugGridToggle(enabled)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4CAF50),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Show NavGrid",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                // Room Mesh visibility toggle (debug)
                if (onRoomMeshToggle != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isRoomMeshVisible,
                            onCheckedChange = { visible ->
                                Log.d("OptionsPanel", "Room mesh toggled: $visible")
                                onRoomMeshToggle(visible)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF2196F3),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Show Room Mesh",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pet state debug values - Row 1: Attention & Activity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (isPetAttentive) Color(0xFF4CAF50) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Attn",
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                    Text(
                        text = "Act: $activityState",
                        fontSize = 12.sp,
                        color = when (activityState) {
                            "FETCHING" -> Color(0xFF2196F3)
                            "FACING_PLAYER" -> Color(0xFF4CAF50)
                            else -> Color.Gray
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Fetch State
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Fetch:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = fetchState,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (fetchState) {
                            "MOVING_TO_BONE" -> Color(0xFFFF9800)
                            "PICKING_UP" -> Color(0xFF9C27B0)
                            "RETURNING" -> Color(0xFF2196F3)
                            else -> Color.Gray
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 3: Distance to bone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dist to bone:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = if (distanceToBone >= 0) String.format("%.2fm", distanceToBone) else "N/A",
                        fontSize = 12.sp,
                        color = if (distanceToBone in 0f..0.2f) Color(0xFF4CAF50) else Color.DarkGray
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 4: Bone states
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (bonePickedUp) Color(0xFF9C27B0) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Picked",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (hasBone) Color(0xFFFF9800) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "InMouth",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    if (returningBone) Color(0xFF2196F3) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Return",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PetInfoPanel(
    petData: PetData,
    onClose: () -> Unit
) {
    Log.d("PetInfoPanel", "Rendering with: name=${petData.name}, desc=${petData.description}, level=${petData.level}")
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
            // Pet Name - Large and prominent
            Text(
                text = petData.name,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pet Description
            Text(
                text = if (petData.description.isNotEmpty()) petData.description else "No description",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Level display
            Text(
                text = "Level ${petData.level}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bones Fetched counter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\uD83E\uDDB4",  // Bone emoji
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${petData.bonesFetched} bones fetched",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Close button at bottom
            SecondaryButton(
                label = "Close",
                onClick = onClose
            )

            Spacer(modifier = Modifier.height(16.dp))

            // XP Bar - xp is stored as 0.0-1.0, displayed as percentage
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "XP: ${(petData.xp * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { petData.xp / petData.xpToNextLevel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}
