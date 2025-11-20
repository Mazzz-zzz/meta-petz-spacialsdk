package com.cybergarden.metapetz

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
fun OptionsPanel(onSelectPet: (String) -> Unit) {
  var selectedPet by remember { mutableStateOf<Pet?>(null) }
  var petStats by remember { mutableStateOf(PetStats()) }

  val pets: List<Pet> = listOf(
      Pet("Cat", "🐱", "A playful feline friend", "Curious"),
      Pet("Dog", "🐶", "A loyal canine companion", "Loyal"),
      Pet("Bunny", "🐰", "A cute hopping rabbit", "Gentle"),
      Pet("Bird", "🐦", "A chirping feathered friend", "Energetic"),
      Pet("Fish", "🐠", "A swimming aquatic pal", "Calm"),
      Pet("Hamster", "🐹", "A tiny furry buddy", "Playful"),
  )

  // Simulate stat decay over time (Tamagotchi-style)
  LaunchedEffect(selectedPet) {
    if (selectedPet != null) {
      while (true) {
        delay(5000) // Every 5 seconds
        petStats = petStats.copy(
            hunger = max(0f, petStats.hunger - 0.05f),
            happiness = max(0f, petStats.happiness - 0.03f),
            energy = max(0f, petStats.energy - 0.04f)
        )
      }
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
      if (selectedPet == null) {
        // Pet Selection Screen
        PetSelectionScreen(
            pets = pets,
            onSelectPet = { pet ->
              selectedPet = pet
              petStats = PetStats() // Reset stats for new pet
              onSelectPet(pet.name)
            }
        )
      } else {
        // Pet Info & Care Screen
        PetInfoScreen(
            pet = selectedPet!!,
            stats = petStats,
            onStatsUpdate = { newStats -> petStats = newStats },
            onBack = { selectedPet = null }
        )
      }
    }
  }
}

@Composable
fun PetSelectionScreen(
    pets: List<Pet>,
    onSelectPet: (Pet) -> Unit
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
