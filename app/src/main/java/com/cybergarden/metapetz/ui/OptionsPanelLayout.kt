package com.cybergarden.metapetz.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isEnvironmentSetup: Boolean = false
) {
    var demoPet by remember { mutableStateOf<PetData?>(null) }
    var isLoadingDemoPet by remember { mutableStateOf(true) }

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
            // Environment section header
            Text(
                text = "Environment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Outside button (5x5 room)
            if (onSetupRoom != null) {
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = if (isEnvironmentSetup) "Outside" else "Outside",
                    onClick = {
                        Log.d("OptionsPanel", "Outside button pressed")
                        onSetupRoom()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Room Scan button (MRUK)
            if (onScanRoom != null) {
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Room Scan",
                    onClick = {
                        Log.d("OptionsPanel", "Room Scan button pressed")
                        onScanRoom()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Features section (disabled until environment is set up)
            if (isEnvironmentSetup) {
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

                // Demo Pet button (from Firebase "demoUser")
                if (demoPet != null && onSelectDemoPet != null && firebaseManager != null) {
                    PrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Demo Pet: ${demoPet!!.name}",
                        onClick = {
                            // Always fetch fresh data from Firebase when clicking
                            Log.d("OptionsPanel", "Fetching fresh demo pet data...")
                            firebaseManager.getFirstPetFromUser("demoUser") { freshPetData ->
                                if (freshPetData != null) {
                                    demoPet = freshPetData  // Update cached data too
                                    Log.d("OptionsPanel", "Fresh demo pet loaded: ${freshPetData.name}, xp: ${freshPetData.xp}, level: ${freshPetData.level}")
                                    onSelectDemoPet(freshPetData)
                                } else {
                                    Log.e("OptionsPanel", "Failed to fetch fresh pet data, using cached")
                                    onSelectDemoPet(demoPet!!)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (isLoadingDemoPet && firebaseManager != null) {
                    Text(
                        text = "Loading demo pet...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                // Show hint when environment not set up
                Text(
                    text = "Select an environment to begin",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Quit button
            if (onQuit != null) {
                Spacer(modifier = Modifier.weight(1f))
                SecondaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Quit App",
                    onClick = {
                        Log.d("OptionsPanel", "Quit button pressed")
                        onQuit()
                    }
                )
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
