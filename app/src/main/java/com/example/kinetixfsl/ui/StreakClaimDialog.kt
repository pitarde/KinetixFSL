package com.example.kinetixfsl.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kinetixfsl.profile.AccentProgress
import com.example.kinetixfsl.progress.XpEngine
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * The daily-streak reward sheet. Shows the six streak days as claimable tiles:
 * a day is claimable once the login streak has reached it, already-claimed days
 * show a check, and days beyond the current streak stay locked. Claiming banks
 * [XpEngine.STREAK_MILESTONE_XP] and floats a "+XP" badge up as feedback.
 *
 * Missing a day resets the streak, so the higher days can't be reached (and
 * their XP can't be claimed) until it's rebuilt — by design, which is why
 * achievements matter for topping up to Level 20.
 */
@Composable
fun StreakClaimDialog(
    streakDays: Int,
    claimedDays: Set<Int>,
    onClaim: (day: Int) -> Int,
    onDismiss: () -> Unit,
) {
    val totalDays = XpEngine.STREAK_MAX_MILESTONES
    val dayXp = XpEngine.STREAK_MILESTONE_XP

    // Bumping this key restarts the floating "+XP" badge for each claim.
    var badgeTick by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "🔥 Daily Streak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$streakDays-day streak · tap a lit day to claim its XP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                // Two rows of three day tiles.
                for (rowStart in 1..totalDays step 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (day in rowStart until rowStart + 3) {
                            val state = when {
                                day in claimedDays -> DayState.CLAIMED
                                day <= streakDays -> DayState.CLAIMABLE
                                else -> DayState.LOCKED
                            }
                            DayTile(
                                day = day,
                                xp = dayXp,
                                state = state,
                                modifier = Modifier.weight(1f),
                                onClaim = {
                                    if (onClaim(day) > 0) badgeTick++
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Miss a day and the streak resets — you'll need to build " +
                        "it back up to reach the later rewards.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            // Floating "+500 XP" badge, replayed on every successful claim.
            if (badgeTick > 0) {
                key(badgeTick) {
                    FloatingXpBadge(
                        xp = dayXp,
                        visible = true,
                        modifier = Modifier.padding(top = 40.dp),
                        riseDistance = 90.dp,
                    )
                }
            }
        }
    }
}

private enum class DayState { CLAIMED, CLAIMABLE, LOCKED }

@Composable
private fun DayTile(
    day: Int,
    xp: Int,
    state: DayState,
    modifier: Modifier = Modifier,
    onClaim: () -> Unit,
) {
    // Claimable tiles gently pulse to invite the tap.
    val pulse = rememberInfiniteTransition(label = "dayPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (state == DayState.CLAIMABLE) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "dayPulseScale",
    )

    val bg = when (state) {
        DayState.CLAIMED -> KinetixGreen.copy(alpha = 0.18f)
        DayState.CLAIMABLE -> AccentProgress
        DayState.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (state) {
        DayState.CLAIMED -> KinetixGreen
        DayState.CLAIMABLE -> Color.White
        DayState.LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(
                if (state == DayState.CLAIMABLE)
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                else Modifier,
            )
            .clickable(enabled = state == DayState.CLAIMABLE, onClick = onClaim)
            .aspectRatio(0.82f)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Day $day",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (state) {
                DayState.CLAIMED -> "✓"
                DayState.LOCKED -> "🔒"
                DayState.CLAIMABLE -> "🔥"
            },
            fontSize = 22.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (state) {
                DayState.CLAIMED -> "Claimed"
                DayState.CLAIMABLE -> "CLAIM"
                DayState.LOCKED -> "+$xp"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}
