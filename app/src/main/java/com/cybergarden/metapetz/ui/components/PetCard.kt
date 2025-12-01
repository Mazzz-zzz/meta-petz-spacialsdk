package com.cybergarden.metapetz.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.model.Pet
import com.meta.spatial.uiset.card.PrimaryCard
import com.meta.spatial.uiset.theme.SpatialColor

@Composable
fun PetCard(
    pet: Pet,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PrimaryCard(
        modifier = modifier,
        onClick = onClick
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

@Composable
fun CustomPetCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PrimaryCard(
        modifier = modifier,
        onClick = onClick
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
