package io.github.devildare687.stealthmesh.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp

@Composable
fun InitializingScreen(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "mesh_boot")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_boot_rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StealthMeshMark(size = 92)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "StealthMesh",
                fontFamily = StealthMeshDisplayFont,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onBackground
            )

            Text(
                text = "Waking up your offline crew…",
                fontFamily = StealthMeshDisplayFont,
                fontSize = 16.sp,
                color = colors.onBackground.copy(alpha = 0.68f)
            )

            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .rotate(rotation),
                color = StealthMeshAccent,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .background(
                        color = colors.surfaceVariant.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StealthMeshAccessBadge(label = "BT")
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Linking nearby devices",
                        fontFamily = StealthMeshDisplayFont,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Give it a sec — radios are being dramatic.",
                        fontFamily = StealthMeshDisplayFont,
                        fontSize = 13.sp,
                        color = colors.onSurface.copy(alpha = 0.64f)
                    )
                }
            }
        }
    }
}

@Composable
fun InitializationErrorScreen(
    modifier: Modifier,
    errorMessage: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StealthMeshMark(size = 76)

            Text(
                text = "Mesh woke up grumpy.",
                fontFamily = StealthMeshDisplayFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.error,
                textAlign = TextAlign.Center
            )

            Text(
                text = errorMessage,
                fontFamily = StealthMeshDisplayFont,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onBackground.copy(alpha = 0.76f),
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
            ) {
                Text("Try the wake-up again", fontFamily = StealthMeshDisplayFont, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Open Android settings", fontFamily = StealthMeshDisplayFont)
            }
        }
    }
}
