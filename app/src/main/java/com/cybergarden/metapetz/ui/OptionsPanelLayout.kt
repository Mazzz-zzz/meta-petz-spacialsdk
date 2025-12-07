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
    var isLoadingDemoPet by remember { mutableStateOf(true) }
    var spawnCooldownRemaining by remember { mutableStateOf(0) }  // Seconds remaining in cooldown

    // Load first pet from "demoUser" user on mount
    LaunchedEffect(firebaseManager) {
        if (firebaseManager != null) {
            firebaseManager.getFirstPetFromUser("demoUser") { petData ->
                demoPet = petData
                isLoadingDemoPet = false
                Log.d("OptionsPanel", "Demo pet loaded: ${petData?.name}")
            }
        } else {
            isLoadingDemoPet = false
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
                // Spawn Dog button
                if (demoPet != null && onSelectDemoPet != null && firebaseManager != null) {
                    val isOnCooldown = spawnCooldownRemaining > 0
                    PrimaryButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isOnCooldown) Modifier.alpha(0.5f) else Modifier),
                        label = if (isOnCooldown) "Spawning... (${spawnCooldownRemaining}s)" else "Spawn Dog",
                        onClick = {
                            if (isOnCooldown) return@PrimaryButton

                            spawnCooldownRemaining = 5

                            Log.d("OptionsPanel", "Fetching fresh demo pet data...")
                            firebaseManager.getFirstPetFromUser("demoUser") { freshPetData ->
                                if (freshPetData != null) {
                                    demoPet = freshPetData
                                    Log.d("OptionsPanel", "Fresh demo pet loaded: ${freshPetData.name}")
                                    onSelectDemoPet(freshPetData)
                                } else {
                                    Log.e("OptionsPanel", "Failed to fetch fresh pet data, using cached")
                                    onSelectDemoPet(demoPet!!)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (isLoadingDemoPet && firebaseManager != null) {
                    Text(
                        text = "Loading...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
