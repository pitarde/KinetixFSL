package com.example.kinetixfsl.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.game.QuizScreen
import com.example.kinetixfsl.game.model.LevelStatus

/** Node geometry, shared by the connector Canvas and the node rows so they line up. */
private val NODE_SIZE = 84.dp
private val ROW_HEIGHT = 128.dp
private const val LEFT_X = 0.25f   // node centre x within a row (left column)
private const val RIGHT_X = 0.75f  // …and the right column

/** A flat-top hexagon for cleared levels (mockup's green badge). */
private val Hexagon = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.25f, 0f)
    lineTo(w * 0.75f, 0f)
    lineTo(w, h * 0.5f)
    lineTo(w * 0.75f, h)
    lineTo(w * 0.25f, h)
    lineTo(0f, h * 0.5f)
    close()
}

/**
 * The Quiz Game home as a winding roadmap (per the requested layout): a
 * "CURRENT GOAL" card on top, then the ten levels as alternating hexagon/circle
 * nodes joined by connector lines — cleared levels green, locked ones muted.
 */
@Composable
fun LevelMapScreen(
    state: QuizScreen.Map,
    onLevelClick: (Int) -> Unit,
    onGoalAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        GoalCard(state = state, onAction = onGoalAction)
        Spacer(Modifier.height(20.dp))
        LevelPath(state = state, onLevelClick = onLevelClick)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Current-goal card ───────────────────────────────────────────────────────
@Composable
private fun GoalCard(state: QuizScreen.Map, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(20.dp),
    ) {
        Text(
            text = "CURRENT GOAL",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "🎯  Level ${state.goalLevel}: ${state.goalTitle}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { state.goalXp.toFloat() / state.xpGoal.coerceAtLeast(1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${state.goalXp} / ${state.xpGoal} XP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.tertiary)
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (state.canResume) "RESUME QUIZ" else "START LEVEL ${state.goalLevel}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

// ── The winding path ────────────────────────────────────────────────────────
@Composable
private fun LevelPath(state: QuizScreen.Map, onLevelClick: (Int) -> Unit) {
    val reachedColor = MaterialTheme.colorScheme.tertiary
    val lockedColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxWidth()) {
        // Connectors, drawn behind the nodes. A gap is "reached" (green) when the
        // deeper level of the two is unlocked or already passed.
        Canvas(modifier = Modifier.matchParentSize()) {
            val rowH = size.height / state.levels.size
            fun centre(i: Int) = Offset(
                x = size.width * if (i % 2 == 0) LEFT_X else RIGHT_X,
                y = rowH * (i + 0.5f),
            )
            for (i in 1 until state.levels.size) {
                val reached = state.levels[i].status != LevelStatus.LOCKED
                drawLine(
                    color = if (reached) reachedColor else lockedColor,
                    start = centre(i - 1),
                    end = centre(i),
                    strokeWidth = (if (reached) 11.dp else 7.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Column {
            state.levels.forEachIndexed { index, card ->
                LevelNodeRow(
                    level = card.level,
                    title = card.title,
                    status = card.status,
                    onLeft = index % 2 == 0,
                    xpLabel = if (card.level == state.goalLevel && state.goalXp > 0) {
                        "${state.goalXp} / ${state.xpGoal} XP"
                    } else null,
                    onClick = { onLevelClick(card.level) },
                )
            }
        }
    }
}

@Composable
private fun LevelNodeRow(
    level: Int,
    title: String,
    status: LevelStatus,
    onLeft: Boolean,
    xpLabel: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onLeft) {
            NodeSlot(level, status, onClick, Modifier.weight(1f))
            LabelSlot(level, title, status, xpLabel, Alignment.CenterStart, Modifier.weight(1f))
        } else {
            LabelSlot(level, title, status, xpLabel, Alignment.CenterEnd, Modifier.weight(1f))
            NodeSlot(level, status, onClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NodeSlot(level: Int, status: LevelStatus, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (status) {
            LevelStatus.PASSED -> HexNode(onClick)
            LevelStatus.UNLOCKED -> CurrentNode(level, onClick)
            LevelStatus.LOCKED -> LockedNode()
        }
    }
}

@Composable
private fun HexNode(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(NODE_SIZE)
            .clip(Hexagon)
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = QuizIcons.CheckCircle,
            contentDescription = "Passed",
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun CurrentNode(level: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(NODE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = QuizIcons.Controller,
            contentDescription = "Play level $level",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun LockedNode() {
    Box(
        modifier = Modifier
            .size(NODE_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = QuizIcons.Lock,
            contentDescription = "Locked",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun LabelSlot(
    level: Int,
    title: String,
    status: LevelStatus,
    xpLabel: String?,
    alignment: Alignment,
    modifier: Modifier,
) {
    val locked = status == LevelStatus.LOCKED
    val primaryText =
        if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Box(modifier = modifier.padding(horizontal = 8.dp), contentAlignment = alignment) {
        Column {
            Text(
                text = "Level $level:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = primaryText,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryText.copy(alpha = if (locked) 0.8f else 1f),
            )
            if (xpLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = xpLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
