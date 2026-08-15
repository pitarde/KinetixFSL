package com.example.kinetixfsl.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kinetixfsl.progress.AchievementView
import com.example.kinetixfsl.progress.XpEngine

// Warm gold for unlocked achievement medallions — matches the badge grids.
private val AchievementGold = Color(0xFFF4A62A)

/**
 * Tapping any achievement badge opens this: a medallion, the achievement's name,
 * whether it's unlocked yet, the exact requirement to earn it ("How to earn it"),
 * and its XP value. Shared by the Profile and Dashboard badge grids so the "how
 * do I get this?" answer is identical wherever a badge is tapped.
 */
@Composable
fun AchievementInfoDialog(
    view: AchievementView,
    onDismiss: () -> Unit,
) {
    val ach = view.achievement
    val unlocked = view.unlocked

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Medallion — gold + emoji when earned, muted padlock while locked.
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) AchievementGold
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (unlocked) ach.emoji else "🔒", fontSize = 42.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = ach.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))
            // Status pill.
            val statusBg = if (unlocked) AchievementGold.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceVariant
            val statusFg = if (unlocked) AchievementGold
            else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = if (unlocked) "✓ Unlocked" else "Locked",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusFg,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "HOW TO EARN IT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = ach.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))
            // XP reward row.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "🏆", fontSize = 18.sp)
                Text(
                    text = "+${XpEngine.ACHIEVEMENT_XP} XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
