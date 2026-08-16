package com.example.kinetixfsl.game.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.kinetixfsl.game.QuizScreen
import com.example.kinetixfsl.game.model.LevelStatus
import com.example.kinetixfsl.profile.AccentProgress
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import kotlin.math.PI
import kotlin.math.sin

/** Node + row geometry, shared by the connector Canvas and the node rows. */
private val NODE_SIZE = 78.dp
private val ROW_HEIGHT = 130.dp

/** How far nodes weave left/right of centre (as a fraction of the width). */
private const val PATH_AMPLITUDE = 0.26f

/** Vertical bias of a node inside its row; the caption sits below it. */
private const val NODE_V_BIAS = -0.32f

/** The x fraction (0..1) of the node in row [i] along the serpentine path. */
private fun nodeXFraction(i: Int): Float =
    0.5f + PATH_AMPLITUDE * sin(i * (PI / 2)).toFloat()

/**
 * The Quiz Game home, redesigned as a playable Duolingo-style adventure map: a
 * hero card with the game illustration and animated XP goal on top, then the
 * levels as raised, weaving "buttons" down a winding path — the current level
 * pulses with a START flag, cleared levels glow green, locked ones sit muted.
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
        val allDone = state.levels.isNotEmpty() && state.passedCount == state.levels.size
        AdventureHeader(state = state, allDone = allDone, onAction = onGoalAction)
        // Once every level is passed, the whole quiz game is done — celebrate it
        // so the learner clearly knows there's nothing left to clear.
        if (allDone) {
            Spacer(Modifier.height(16.dp))
            CompletedBanner()
        }
        Spacer(Modifier.height(24.dp))
        LevelPath(state = state, onLevelClick = onLevelClick)
        Spacer(Modifier.height(28.dp))
    }
}

// ── Hero header ─────────────────────────────────────────────────────────────
@Composable
private fun AdventureHeader(state: QuizScreen.Map, allDone: Boolean, onAction: () -> Unit) {
    val context = LocalContext.current
    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Animate the XP bar filling on entry so the goal feels alive.
    val targetFraction = (state.goalXp.toFloat() / state.xpGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    var startFill by remember { mutableStateOf(false) }
    LaunchedEffect(targetFraction) { startFill = true }
    val fill by animateFloatAsState(
        targetValue = if (startFill) targetFraction else 0f,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "goalFill",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, KinetixIndigo),
                ),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIGN QUEST",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Level ${state.goalLevel}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = state.goalTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
            // The games illustration (bundled SVG), rendered via Coil's SVG decoder.
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/games_icon.svg")
                    .build(),
                imageLoader = svgLoader,
                contentDescription = null,
                modifier = Modifier.size(150.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { fill },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            // Matches the Profile screen's progress accent so bars read the same
            // everywhere in the app.
            color = AccentProgress,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "🏆  ${state.goalXp} / ${state.xpGoal} XP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "✅  ${state.passedCount} / ${state.levels.size} levels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(18.dp))
        StartButton(
            label = when {
                allDone -> "REVIEW AGAIN"
                state.canResume -> "RESUME QUIZ"
                else -> "START LEVEL ${state.goalLevel}"
            },
            onClick = onAction,
        )
    }
}

/** The primary call-to-action — a raised pill that dips on press and gently breathes. */
@Composable
private fun StartButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val breathe = rememberInfiniteTransition(label = "startBreathe")
    val pulse by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "startPulse",
    )
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, label = "startPress")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pulse * press; scaleY = pulse * press }
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

/** Shown when every level is cleared — a celebratory "all done" ribbon. */
@Composable
private fun CompletedBanner() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.7f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "completedPop",
    )
    val shine = rememberInfiniteTransition(label = "completedShine")
    val glow by shine.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "completedGlow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(KinetixGreen, lerp(KinetixGreen, Color.Black, 0.18f)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🎉",
            fontSize = 30.sp,
            modifier = Modifier.graphicsLayer { scaleX = glow; scaleY = glow },
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "COMPLETED!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                text = "You've cleared every quiz level. Amazing work!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Icon(
            imageVector = QuizIcons.Trophy,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { scaleX = glow; scaleY = glow },
        )
    }
}

// ── The winding path ────────────────────────────────────────────────────────
@Composable
private fun LevelPath(state: QuizScreen.Map, onLevelClick: (Int) -> Unit) {
    val reachedColor = MaterialTheme.colorScheme.tertiary
    val lockedColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxWidth()) {
        // Connectors behind the nodes, weaving between consecutive node centres.
        // Node centre y matches the node's vertical bias inside its row.
        Canvas(modifier = Modifier.matchParentSize()) {
            val n = state.levels.size
            if (n == 0) return@Canvas
            val rowH = size.height / n
            val yFactor = 0.5f + NODE_V_BIAS * 0.5f
            fun centre(i: Int) = Offset(
                x = size.width * nodeXFraction(i),
                y = rowH * (i + yFactor),
            )
            for (i in 1 until n) {
                val reached = state.levels[i].status != LevelStatus.LOCKED
                drawLine(
                    color = if (reached) reachedColor else lockedColor,
                    start = centre(i - 1),
                    end = centre(i),
                    strokeWidth = (if (reached) 12.dp else 8.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Column {
            state.levels.forEach { card ->
                LevelNodeRow(
                    card = card,
                    isCurrent = card.level == state.goalLevel &&
                        card.status != LevelStatus.PASSED,
                    onClick = { onLevelClick(card.level) },
                )
            }
        }
    }
}

@Composable
private fun LevelNodeRow(
    card: com.example.kinetixfsl.game.LevelCard,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        // Levels are sequential (1..N), so the path index is level - 1 — this
        // matches the connector Canvas, which walks the list by the same index.
        val bias = 2f * nodeXFraction(card.level - 1) - 1f
        // Node.
        Box(modifier = Modifier.align(BiasAlignment(bias, NODE_V_BIAS))) {
            LevelNode(status = card.status, level = card.level, isCurrent = isCurrent, onClick = onClick)
        }
        // Caption below the node.
        Box(modifier = Modifier.align(BiasAlignment(bias, 0.72f))) {
            LevelCaption(card = card)
        }
    }
}

@Composable
private fun LevelNode(
    status: LevelStatus,
    level: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val topColor = when (status) {
        LevelStatus.PASSED -> KinetixGreen
        LevelStatus.UNLOCKED -> MaterialTheme.colorScheme.primary
        LevelStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val baseColor = lerp(topColor, Color.Black, 0.22f)
    val iconTint = when (status) {
        LevelStatus.LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> Color.White
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, label = "nodePress")

    // The current level bobs gently so the eye lands on "play next".
    val bob = rememberInfiniteTransition(label = "bob")
    val bobScale by bob.animateFloat(
        initialValue = 1f,
        targetValue = if (isCurrent) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "bobScale",
    )
    val scale = press * bobScale

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Floating START flag above the current node.
        if (isCurrent) {
            StartFlag()
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(width = NODE_SIZE, height = NODE_SIZE + 8.dp),
        ) {
            // 3D base (darker, offset down).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(NODE_SIZE)
                    .clip(CircleShape)
                    .background(baseColor),
            )
            // Raised top face.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(NODE_SIZE)
                    .clip(CircleShape)
                    .background(topColor)
                    .then(
                        if (isCurrent) Modifier.border(4.dp, KinetixGreen, CircleShape)
                        else Modifier,
                    )
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                when (status) {
                    LevelStatus.PASSED -> Icon(
                        imageVector = QuizIcons.CheckCircle,
                        contentDescription = "Passed",
                        tint = iconTint,
                        modifier = Modifier.size(34.dp),
                    )
                    LevelStatus.UNLOCKED -> Text(
                        text = "$level",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = iconTint,
                    )
                    LevelStatus.LOCKED -> Icon(
                        imageVector = QuizIcons.Lock,
                        contentDescription = "Locked",
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

/** The small "START" flag that bounces above the current level. */
@Composable
private fun StartFlag() {
    val transition = rememberInfiniteTransition(label = "flag")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "flagOffset",
    )
    Box(
        modifier = Modifier
            .graphicsLayer { translationY = offset }
            .clip(RoundedCornerShape(10.dp))
            .background(KinetixGreen)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "START",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}

@Composable
private fun LevelCaption(card: com.example.kinetixfsl.game.LevelCard) {
    val locked = card.status == LevelStatus.LOCKED
    val titleColor =
        if (locked) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(150.dp),
    ) {
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = when (card.status) {
                LevelStatus.PASSED -> "+${card.xpEarned} XP earned"
                LevelStatus.LOCKED -> "🔒 Locked"
                else -> "Up to ${card.xpReward} XP"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (card.status) {
                LevelStatus.PASSED -> KinetixGreen
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}
