package io.github.devildare687.stealthmesh.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BluetoothCheckScreen(
    modifier: Modifier,
    status: BluetoothStatus,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        when (status) {
            BluetoothStatus.DISABLED -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StealthMeshAccessBadge(label = "BT")
                Text(
                    text = "Bluetooth is taking the day off.",
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "StealthMesh needs Bluetooth to spot nearby people and build local links.",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                if (isLoading) {
                    BluetoothLoadingIndicator()
                } else {
                    Button(
                        onClick = onEnableBluetooth,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
                    ) {
                        Text("Wake Bluetooth up", fontFamily = StealthMeshDisplayFont, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("Not now", fontFamily = StealthMeshDisplayFont, color = StealthMeshAccent)
                    }
                }
            }

            BluetoothStatus.NOT_SUPPORTED -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StealthMeshAccessBadge(label = "NO BT")
                Text(
                    text = "No Bluetooth radio found.",
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "This device cannot join the nearby mesh without Bluetooth support.",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onSkip) {
                    Text("Continue anyway", fontFamily = StealthMeshDisplayFont, color = StealthMeshAccent)
                }
            }

            BluetoothStatus.ENABLED -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StealthMeshAccessBadge(label = "BT")
                BluetoothLoadingIndicator()
                Text(
                    text = "Checking the radio link…",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BluetoothLoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "bluetooth_loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    CircularProgressIndicator(
        modifier = Modifier.size(48.dp).rotate(rotation),
        color = StealthMeshAccent,
        strokeWidth = 3.dp
    )
}
