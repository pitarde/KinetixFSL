package com.example.kinetixfsl.modules

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.modules.model.SignCategory
import com.example.kinetixfsl.modules.model.SignEntry
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * Displays all signs within a category as a vertical learning path.
 *
 * Matches the Figma "Lessons_Alphabet" screen: category header with
 * illustration, progress bar, and a list of signs with vertical
 * connector lines and completion status.
 *
 * @param category  The category to display.
 * @param onBack    Called when the back arrow is tapped.
 * @param onSignClick Called when a sign row is tapped. Receives the
 *                    index of the sign within this category.
 */
@Composable
fun SignListScreen(
    category: SignCategory,
    onBack: () -> Unit,
    onSignClick: (signIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: Replace with real Room-backed progress when we build the DB.
    // For now, no signs are completed — progress is 0%.
    val completedCount = 0
    val progress = if (category.signCount > 0) {
        completedCount.toFloat() / category.signCount
    } else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ── Top bar with back arrow ─────────────────────────────
        TopBarWithBack(onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            // ── Category header ─────────────────────────────────
            item {
                CategoryHeader(
                    title = category.title,
                )
            }

            // ── Progress section ────────────────────────────────
            item {
                ProgressSection(
                    categoryTitle = category.title,
                    progress = progress,
                    completedCount = completedCount,
                    totalCount = category.signCount,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Sign list with connector line ───────────────────
            itemsIndexed(
                items = category.signs,
                key = { _, sign -> sign.id },
            ) { index, sign ->
                val isCompleted = index < completedCount
                val isLast = index == category.signs.lastIndex

                SignListItem(
                    sign = sign,
                    displayPrefix = getDisplayPrefix(category.id),
                    isCompleted = isCompleted,
                    showConnector = !isLast,
                    onClick = { onSignClick(index) },
                )
            }

            // Bottom padding
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────

@Composable
private fun TopBarWithBack(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = ModulesIcons.ArrowBack,
            contentDescription = "Go back",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
                .clickable(onClick = onBack),
        )
    }
}

// ── Category header ─────────────────────────────────────────────

@Composable
private fun CategoryHeader(
    title: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Illustration placeholder — will be replaced with actual
        // category illustrations (e.g. ABC blocks for alphabet,
        // number blocks for numbers, etc.)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(3).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Category title pill badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ── Progress section ────────────────────────────────────────────

@Composable
private fun ProgressSection(
    categoryTitle: String,
    progress: Float,
    completedCount: Int,
    totalCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val percentage = (progress * 100).toInt()
        Text(
            text = "$categoryTitle Progress $percentage%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
    }
}

// ── Individual sign item ────────────────────────────────────────

@Composable
private fun SignListItem(
    sign: SignEntry,
    displayPrefix: String,
    isCompleted: Boolean,
    showConnector: Boolean,
    onClick: () -> Unit,
) {
    val xp = if (isCompleted) 20 else 30

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Left: connector line ────────────────────────────
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(72.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .offset(y = 22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) KinetixGreen
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
            // Connector line below the dot
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .offset(y = 34.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }

        // ── Right: sign row card ────────────────────────────
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sign name
            Text(
                text = "$displayPrefix ${sign.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            // XP badge
            Text(
                text = "${xp}xp",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )

            // Status icon
            Icon(
                imageVector = if (isCompleted) {
                    ModulesIcons.CheckCircle
                } else {
                    ModulesIcons.PlayCircle
                },
                contentDescription = if (isCompleted) "Completed" else "Start",
                tint = if (isCompleted) {
                    KinetixGreen
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Returns the display prefix for sign names within a category.
 * "Letter" for alphabet, "Number" for numbers, empty for word categories.
 */
private fun getDisplayPrefix(categoryId: String): String = when (categoryId) {
    "alphabet" -> "Letter"
    "numbers" -> "Number"
    else -> ""
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "SignList – Light")
@Composable
private fun SignListPreviewLight() {
    val cat = remember { FslSignData.findCategory("alphabet")!! }
    KinetixFSLTheme(darkTheme = false) {
        SignListScreen(category = cat, onBack = {}, onSignClick = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "SignList – Dark")
@Composable
private fun SignListPreviewDark() {
    val cat = remember { FslSignData.findCategory("alphabet")!! }
    KinetixFSLTheme(darkTheme = true) {
        SignListScreen(category = cat, onBack = {}, onSignClick = {})
    }
}