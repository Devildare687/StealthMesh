package io.github.devildare687.stealthmesh.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devildare687.stealthmesh.R

internal val StealthMeshDisplayFont = FontFamily.SansSerif
internal val StealthMeshCodeFont = FontFamily.Monospace
internal val StealthMeshAccent = Color(0xFF8B7CFF)
internal val StealthMeshAccentSoft = Color(0x228B7CFF)

@Composable
internal fun StealthMeshMark(
    modifier: Modifier = Modifier,
    size: Int = 44
) {
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape((size / 3).dp),
        color = Color.Black
    ) {
        Icon(
            painter = painterResource(R.drawable.stealthmesh_mark),
            contentDescription = "StealthMesh",
            tint = Color.White,
            modifier = Modifier.padding((size / 7).dp)
        )
    }
}

@Composable
internal fun StealthMeshHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StealthMeshMark(size = 42)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    color = StealthMeshAccent,
                    fontFamily = StealthMeshCodeFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "StealthMesh",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = StealthMeshDisplayFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = StealthMeshDisplayFont,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
            fontFamily = StealthMeshDisplayFont,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
internal fun StealthMeshAccessBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = StealthMeshAccentSoft,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = StealthMeshAccent,
            fontFamily = StealthMeshCodeFont,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
internal fun StealthMeshInfoPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (badge != null) {
                StealthMeshAccessBadge(label = badge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = StealthMeshDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontFamily = StealthMeshDisplayFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
