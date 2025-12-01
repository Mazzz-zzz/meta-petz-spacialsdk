package com.cybergarden.metapetz.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.spatial.uiset.theme.SpatialColor
import com.meta.spatial.uiset.theme.SpatialTheme

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

fun getStatColor(value: Float): Color {
    return when {
        value > 0.7f -> Color(0xFF4CAF50) // Green
        value > 0.4f -> Color(0xFFFFC107) // Amber
        else -> Color(0xFFF44336) // Red
    }
}
