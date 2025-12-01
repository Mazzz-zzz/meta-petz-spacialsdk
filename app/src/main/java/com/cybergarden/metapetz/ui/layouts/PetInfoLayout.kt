package com.cybergarden.metapetz.ui.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.model.Pet
import com.cybergarden.metapetz.model.PetStats
import com.cybergarden.metapetz.ui.components.CareActionButton
import com.cybergarden.metapetz.ui.components.StatBar
import com.cybergarden.metapetz.ui.components.getStatColor
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.card.SecondaryCard
import com.meta.spatial.uiset.theme.SpatialColor

@Composable
fun PetInfoLayout(
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
                label = "\u2190 Back",
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

fun getMoodText(stats: PetStats): String {
    val avgStat = (stats.hunger + stats.happiness + stats.health + stats.energy) / 4
    return when {
        avgStat > 0.8f -> "Feeling Great! \uD83D\uDE0A"
        avgStat > 0.6f -> "Doing Well \uD83D\uDE42"
        avgStat > 0.4f -> "Needs Attention \uD83D\uDE10"
        avgStat > 0.2f -> "Not Happy \uD83D\uDE1F"
        else -> "Critical! \uD83D\uDE22"
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
