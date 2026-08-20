package com.bitchat.android.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionExplanationScreen(
    modifier: Modifier,
    permissionCategories: List<PermissionCategory>,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .padding(bottom = 92.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            StealthMeshHeader(
                eyebrow = "Local setup / 01",
                title = "A few Android handshakes, then we disappear off-grid.",
                subtitle = "No account circus. Just the access Android needs to find people nearby and keep the local mesh alive."
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "LOCAL BY DESIGN",
                        fontFamily = StealthMeshCodeFont,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = "Nearby chats stay on the mesh. No central server is needed for local messaging.",
                        fontFamily = StealthMeshDisplayFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = colors.onSurface.copy(alpha = 0.78f)
                    )
                }
            }

            Text(
                text = "WHAT ANDROID WANTS",
                fontFamily = StealthMeshCodeFont,
                fontSize = 11.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground.copy(alpha = 0.54f),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )

            permissionCategories.forEach { category ->
                StealthMeshInfoPanel(
                    badge = permissionBadge(category.type),
                    title = permissionTitle(category.type),
                    body = category.description
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = colors.background,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(
                    text = "Cool, set it up",
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
    }
}

private fun permissionBadge(type: PermissionType): String = when (type) {
    PermissionType.NEARBY_DEVICES -> "BT"
    PermissionType.PRECISE_LOCATION -> "LOC"
    PermissionType.BACKGROUND_LOCATION -> "BG"
    PermissionType.MICROPHONE -> "MIC"
    PermissionType.NOTIFICATIONS -> "PING"
    PermissionType.WIFI_AWARE -> "WIFI"
    PermissionType.BATTERY_OPTIMIZATION -> "BAT"
    PermissionType.OTHER -> "SYS"
}

private fun permissionTitle(type: PermissionType): String = when (type) {
    PermissionType.NEARBY_DEVICES -> "Nearby links"
    PermissionType.PRECISE_LOCATION -> "Android location gate"
    PermissionType.BACKGROUND_LOCATION -> "Background discovery"
    PermissionType.MICROPHONE -> "Microphone"
    PermissionType.NOTIFICATIONS -> "Heads-ups"
    PermissionType.WIFI_AWARE -> "Nearby Wi-Fi"
    PermissionType.BATTERY_OPTIMIZATION -> "Stay awake"
    PermissionType.OTHER -> "System access"
}
