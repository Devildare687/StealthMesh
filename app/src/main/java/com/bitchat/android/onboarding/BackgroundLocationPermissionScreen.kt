package com.bitchat.android.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackgroundLocationPermissionScreen(
    modifier: Modifier,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.padding(22.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StealthMeshHeader(
                    eyebrow = "Local setup / 02",
                    title = "Want the mesh to keep lurking in the background?",
                    subtitle = "Optional. Android asks for background location so Bluetooth discovery can keep doing its thing when StealthMesh is not on screen."
                )

                Spacer(modifier = Modifier.height(6.dp))

                StealthMeshInfoPanel(
                    badge = "BG",
                    title = "Stay discoverable",
                    body = "Useful if you want nearby links to reconnect while multitasking or after interruptions."
                )

                StealthMeshInfoPanel(
                    badge = "NO GPS",
                    title = "Your coordinates are not the product",
                    body = "StealthMesh does not store or share your precise location. Android uses this permission gate for background Bluetooth scanning."
                )

                Text(
                    text = "If Android opens Settings, choose “Allow all the time.”",
                    fontFamily = StealthMeshDisplayFont,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.onBackground.copy(alpha = 0.62f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
                ) {
                    Text(
                        text = "Keep me discoverable",
                        fontFamily = StealthMeshDisplayFont,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Check again", fontFamily = StealthMeshDisplayFont)
                    }

                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Maybe later", fontFamily = StealthMeshDisplayFont, color = StealthMeshAccent)
                    }
                }
            }
        }
    }
}
