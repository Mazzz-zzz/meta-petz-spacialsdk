package com.cybergarden.metapetz.ui.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.model.Pet
import com.cybergarden.metapetz.ui.components.CustomPetCard
import com.cybergarden.metapetz.ui.components.PetCard

@Composable
fun PetSelectionLayout(
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
                        PetCard(
                            pet = pet,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectPet(pet) }
                        )
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
                CustomPetCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCustomPetClick
                )
            }
        }
    }
}
