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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BatteryOptimizationScreen(
    modifier: Modifier,
    status: BatteryOptimizationStatus,
    onDisableBatteryOptimization: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        BatteryOptimizationPreferenceManager.init(context)
    }

    Box(modifier = modifier.padding(22.dp)) {
        when (status) {
            BatteryOptimizationStatus.ENABLED -> BatteryOptimizationEnabledContent(
                onDisableBatteryOptimization = onDisableBatteryOptimization,
                onRetry = onRetry,
                onSkip = onSkip,
                colorScheme = colors,
                isLoading = isLoading
            )
            BatteryOptimizationStatus.DISABLED -> BatteryOptimizationCheckingContent(colors)
            BatteryOptimizationStatus.NOT_SUPPORTED -> BatteryOptimizationNotSupportedContent(
                onRetry = onRetry,
                colorScheme = colors
            )
        }
    }
}

@Composable
private fun BatteryOptimizationEnabledContent(
    onDisableBatteryOptimization: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    colorScheme: ColorScheme,
    isLoading: Boolean
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StealthMeshHeader(
                eyebrow = "Local setup / 03",
                title = "Android is trying to put the mesh to sleep.",
                subtitle = "You can let StealthMesh dodge battery restrictions for steadier background links — or skip it and keep moving."
            )

            Spacer(modifier = Modifier.height(6.dp))

            StealthMeshInfoPanel(
                badge = "BAT",
                title = "Why bother?",
                body = "Fewer dropped background links, smoother reconnects, and a better shot at receiving nearby messages while the app is not front and center."
            )

            StealthMeshInfoPanel(
                badge = "OPT",
                title = "Still your call",
                body = "This is recommended, not mandatory. StealthMesh keeps working if you skip it; Android may just pause background activity more aggressively."
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onDisableBatteryOptimization,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Let the mesh stay awake",
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
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Check again", fontFamily = StealthMeshDisplayFont)
                }

                TextButton(
                    onClick = {
                        BatteryOptimizationPreferenceManager.setSkipped(context, true)
                        onSkip()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Skip it", fontFamily = StealthMeshDisplayFont, color = StealthMeshAccent)
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCheckingContent(colorScheme: ColorScheme) {
    val infiniteTransition = rememberInfiniteTransition(label = "battery_check")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "battery_rotation"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StealthMeshMark(size = 72)
        Spacer(modifier = Modifier.height(28.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp).rotate(rotation),
            color = StealthMeshAccent,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Checking the battery leash…",
            fontFamily = StealthMeshDisplayFont,
            color = colorScheme.onBackground.copy(alpha = 0.74f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BatteryOptimizationNotSupportedContent(
    onRetry: () -> Unit,
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StealthMeshMark(size = 72)
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "No battery babysitting needed here.",
            fontFamily = StealthMeshDisplayFont,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "This device does not require the extra battery step.",
            fontFamily = StealthMeshDisplayFont,
            color = colorScheme.onBackground.copy(alpha = 0.66f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StealthMeshAccent)
        ) {
            Text("Continue", fontFamily = StealthMeshDisplayFont)
        }
    }
}
