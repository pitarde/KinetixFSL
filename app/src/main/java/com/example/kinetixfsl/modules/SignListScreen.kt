package com.example.kinetixfsl.modules

import androidx.compose.foundation.Image
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kinetixfsl.R
import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.modules.model.SignCategory
import com.example.kinetixfsl.modules.model.SignEntry
import com.example.kinetixfsl.progress.XpEngine
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
    /** Sign ids the user has learned (Camera Practice success). */
    completedSignIds: Set<String> = emptySet(),
) {
    val completedCount = category.signs.count { it.id in completedSignIds }
    val progress = if (category.signCount > 0) {
        completedCount.toFloat() / category.signCount
    } else 0f

    // The exact XP each sign awards — the category's 400-XP pool split evenly per
    // item (remainder on the last), the SAME value Camera Practice awards on a
    // correct sign. Displaying this here keeps the label and the reward in sync.
    val perSignXp = remember(category.id) { XpEngine.perItemXp(category.signCount) }

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
                    categoryId = category.id,
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
                val isCompleted = sign.id in completedSignIds
                val isLast = index == category.signs.lastIndex

                SignListItem(
                    sign = sign,
                    displayPrefix = getDisplayPrefix(category.id),
                    isCompleted = isCompleted,
                    showConnector = !isLast,
                    xpReward = perSignXp.getOrElse(index) { 0 },
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
    categoryId: String,
    title: String,
) {
    val iconRes = categoryIconRes(categoryId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        // Illustration card with a title banner pinned to its bottom edge,
        // matching the category-detail mockup.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Illustration takes all the space above the banner so it reads
            // large and fills the card, with a little breathing room.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                } else {
                    Text(
                        text = title.take(3).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Title banner across the bottom of the card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
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
            text = "Progress $percentage%  ·  $completedCount / $totalCount done",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(6.dp))

        // The bar animates up from empty on entry and glides whenever a new sign
        // is learned, so progress feels like it's moving rather than jumping.
        var start by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { start = true }
        val animatedProgress by animateFloatAsState(
            targetValue = if (start) progress else 0f,
            animationSpec = tween(900, easing = FastOutSlowInEasing),
            label = "moduleProgress",
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
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
    xpReward: Int,
    onClick: () -> Unit,
) {
    // The XP this sign awards — the exact value Camera Practice grants on a
    // correct sign, so the label a learner sees before doing it matches what
    // they earn. It's the same whether or not the sign is already completed.
    val xp = xpReward

    // Tap feedback: the card dips slightly while pressed.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "signPress")

    // Gentle entry: each row fades and slides in from the left the first time
    // it appears, giving the learning path a lively "unrolling" feel.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(340),
        label = "signEnterAlpha",
    )
    val enterShift by animateFloatAsState(
        targetValue = if (appeared) 0f else 48f,
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        label = "signEnterShift",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enterAlpha; translationX = enterShift },
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
        // Done signs read as filled purple cards with a green check; not-yet-done
        // signs stay light with a play icon (matching the mockup).
        val cardColor = if (isCompleted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
        val nameColor = if (isCompleted) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onBackground
        val xpColor = if (isCompleted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$displayPrefix ${sign.name}",
                style = MaterialTheme.typography.titleMedium,
                color = nameColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${xp}xp",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = xpColor,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (isCompleted) {
                // Filled green check disc.
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KinetixGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Icon(
                    imageVector = ModulesIcons.PlayCircle,
                    contentDescription = "Start",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
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