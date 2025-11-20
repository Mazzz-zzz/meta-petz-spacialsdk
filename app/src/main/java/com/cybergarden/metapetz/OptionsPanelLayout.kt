package com.cybergarden.metapetz

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.toolkit.PanelConstants
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.lightSpatialColorScheme

const val OPTIONS_PANEL_WIDTH = 0.85f
const val OPTIONS_PANEL_HEIGHT = 0.75f

data class Pet(
    val name: String,
    val emoji: String,
    val description: String
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
  var isMenuVisible by remember { mutableStateOf(true) }

  val pets: List<Pet> = listOf(
      Pet("Cat", "🐱", "A playful feline friend"),
      Pet("Dog", "🐶", "A loyal canine companion"),
      Pet("Bunny", "🐰", "A cute hopping rabbit"),
      Pet("Bird", "🐦", "A chirping feathered friend"),
      Pet("Fish", "🐠", "A swimming aquatic pal"),
      Pet("Hamster", "🐹", "A tiny furry buddy"),
  )

  SpatialTheme(colorScheme = getPanelTheme()) {
    if (isMenuVisible) {
      Column(
          modifier =
              Modifier.fillMaxSize()
                  .clip(SpatialTheme.shapes.large)
                  .background(brush = LocalColorScheme.current.panel)
                  .padding(36.dp),
          verticalArrangement = Arrangement.Top,
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Header with title and close button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
              text = "Choose Your Pet",
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              modifier = Modifier.weight(1f)
          )
          SecondaryButton(
              label = "Close",
              onClick = { isMenuVisible = false }
          )
        }

        // Pet selection buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
          pets.forEach { pet ->
            PrimaryButton(
                label = "${pet.emoji} ${pet.name}",
                expanded = true,
                onClick = {
                  onSelectPet(pet.name)
                  isMenuVisible = false
                },
            )
          }
        }
      }
    } else {
      // Show a button to reopen the menu
      Box(
          modifier = Modifier.fillMaxSize()
              .clip(SpatialTheme.shapes.large)
              .background(brush = LocalColorScheme.current.panel)
              .padding(36.dp),
          contentAlignment = Alignment.Center
      ) {
        SecondaryButton(
            label = "Open Menu",
            onClick = { isMenuVisible = true }
        )
      }
    }
  }
}
