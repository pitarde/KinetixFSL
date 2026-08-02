package com.example.kinetixfsl.community.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.model.Community

/**
 * A community summary card — avatar, name, member count, a two-line blurb, and
 * a Join/Joined toggle. Shared by the Discover screen and the per-category list
 * so both read identically.
 */
@Composable
internal fun CommunityCard(
    community: Community,
    isJoined: Boolean,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CommunityIcons.Profile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatCount(community.memberCount)} Members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            JoinPill(isJoined = isJoined, onJoin = onJoin, onLeave = onLeave)
        }

        if (community.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = community.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JoinPill(
    isJoined: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    if (isJoined) {
        Box(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onLeave)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Joined",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onJoin)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Join",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 999 -> "999", 1000 -> "1k", 1500 -> "1.5k", 12000 -> "12k". */
private fun formatCount(count: Long): String {
    if (count < 1000) return count.toString()
    val thousands = count / 1000.0
    return if (thousands % 1.0 == 0.0) {
        "${thousands.toInt()}k"
    } else {
        String.format("%.1fk", thousands)
    }
}
