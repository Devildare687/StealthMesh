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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LocationCheckScreen(
    modifier: Modifier,
    status: LocationStatus,
    onEnableLocation: () -> Unit,
    onRetry: () -> Unit,
    isLoading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Box(modifier = modifier.padding(28.dp), contentAlignment = Alignment.Center) {
        when (status) {
            LocationStatus.DISABLED -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StealthMeshAccessBadge(label = "LOC")
                Text(
                    text = "Android has one weird Bluetooth rule.",
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Location services must be on for nearby Bluetooth discovery on this device. StealthMesh does not need your coordinates to chat locally.",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                if (isLoading) {
                    LocationLoadingIndicator()
                } else {
                    Button(
                        onClick = onEnableLocation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
                    ) {
                        Text("Open location controls", fontFamily = StealthMeshDisplayFont, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Check again", fontFamily = StealthMeshDisplayFont)
                    }
                }
            }

            LocationStatus.NOT_AVAILABLE -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StealthMeshAccessBadge(label = "LOC?")
                Text(
                    text = "Location services are unavailable.",
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Android is not exposing the location service StealthMesh needs for Bluetooth discovery.",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            LocationStatus.ENABLED -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StealthMeshAccessBadge(label = "LOC")
                LocationLoadingIndicator()
                Text(
                    text = "Checking Android's discovery gate…",
                    fontFamily = StealthMeshDisplayFont,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun LocationLoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "location_loading")
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
