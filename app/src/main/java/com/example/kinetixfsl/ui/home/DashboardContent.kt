package com.example.kinetixfsl.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * The Dashboard content — everything BELOW the top-bar hamburger and ABOVE the
 * bottom nav. Rendered inside [HomeScreen], which owns the drawer, top bar, and
 * bottom nav; this composable only draws the greeting, streak card, and module list.
 *
 * All lesson-card clicks are no-ops for now — real navigation comes when the
 * modules feature is built.
 */
@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state = viewModel.uiState

    // Hardcoded for now (see DashboardData.kt). Wrapping in remember{} makes
    // the intent explicit: same reference on recomposition, ready to be replaced
    // with a StateFlow later.
    val streak = remember { SampleStreak }
    val modules = remember { SampleModules }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ---- Greeting ----
        Text(
            text = "Good morning,",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(20.dp))

        StreakCard(streak = streak)

        Spacer(Modifier.height(24.dp))

        Text(
            text = "CONTINUE LEARNING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        modules.forEach { module ->
            ModuleCard(module = module, onClick = { /* TODO(module): open lesson */ })
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The purple streak card. Big streak day count, subtle progress bar, and a
 * gold medal-star in the top-right.
 */
@Composable
private fun StreakCard(streak: StreakSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KinetixNavy)
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "Current Streak",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixWhite.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${streak.streakDays} days",
                style = MaterialTheme.typography.displayLarge,
                color = KinetixWhite,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(16.dp))

            // The pill-shaped progress track — mint fill on a lighter navy track.
            LinearProgressIndicator(
                progress = { streak.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = KinetixWhite,
                trackColor = KinetixWhite.copy(alpha = 0.25f),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Overall progress ~ ${(streak.overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixWhite.copy(alpha = 0.75f),
            )
        }

        // Gold medal star in the top-right corner.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(GoldStar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HomeIcons.StreakStar,
                contentDescription = null,
                tint = KinetixWhite,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// A warm gold, only used on the streak medal so far — kept local since it's not
// part of the general brand palette.
private val GoldStar = Color(0xFFF3B23A)

/** One row in the "Continue Learning" list. */
@Composable
private fun ModuleCard(
    module: ModuleProgress,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = module.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatusPill(status = module.status)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { module.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = module.xpLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatusPill(status: ModuleStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    KinetixFSLTheme {
        DashboardContent()
    }
}