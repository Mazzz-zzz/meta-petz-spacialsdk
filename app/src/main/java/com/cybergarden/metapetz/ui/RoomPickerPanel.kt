package com.cybergarden.metapetz.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybergarden.metapetz.ui.theme.getPanelTheme
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme

/**
 * Data class for room picker information
 */
data class RoomPickerInfo(
    val uuid: String,
    val shortId: String,
    val furnitureList: String,
    val anchorCount: Int
)

/**
 * Room Picker Panel - Modal popup for selecting which room the user is in.
 * Spawns in front of the player and requires a selection before dismissing.
 */
@Composable
fun RoomPickerPanel(
    rooms: List<RoomPickerInfo>,
    currentRoomUuid: String?,
    onRoomSelected: (RoomPickerInfo) -> Unit
) {
    SpatialTheme(colorScheme = getPanelTheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Your Room",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Which room are you currently in?",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Room list
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                rooms.forEach { roomInfo ->
                    val isCurrentRoom = roomInfo.uuid == currentRoomUuid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCurrentRoom) Color(0xFF4CAF50).copy(alpha = 0.3f)
                                else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable {
                                Log.d("RoomPickerPanel", "Room selected: ${roomInfo.shortId}")
                                onRoomSelected(roomInfo)
                            }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Room ${roomInfo.shortId}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (isCurrentRoom) {
                                    Text(
                                        text = "Current",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = roomInfo.furnitureList,
                                fontSize = 14.sp,
                                color = Color.LightGray
                            )
                            Text(
                                text = "${roomInfo.anchorCount} objects detected",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
