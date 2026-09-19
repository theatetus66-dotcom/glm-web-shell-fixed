package com.glmwebshell.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class HealthPillColor { OK, DEGRADED, FAILED, UNKNOWN }

@Composable
fun HealthPill(
    label: String,
    state: HealthPillColor,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        HealthPillColor.OK -> Color(0xFF2E7D32)
        HealthPillColor.DEGRADED -> Color(0xFFF57C00)
        HealthPillColor.FAILED -> Color(0xFFC62828)
        HealthPillColor.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
