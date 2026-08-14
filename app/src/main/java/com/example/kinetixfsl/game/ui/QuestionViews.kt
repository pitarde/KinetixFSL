package com.example.kinetixfsl.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.game.data.QuizContent
import com.example.kinetixfsl.game.model.QuizQuestion

/**
 * The four question renderers. Each owns its own selection state and reports up
 * via [onSelectionChanged] `(isComplete, isCorrect)` on every interaction; the
 * host [QuizQuestionScreen] owns the Check/feedback flow and passes [revealed]
 * back down to switch the options into correct/wrong colours (input locks then).
 *
 * Sizes are standardised via the constants below so every question's video and
 * choices are consistent, matching the mockups: one large portrait video for the
 * single-video questions, a stacked pair for "pick the video", and a uniform 2×2
 * grid for matching.
 */

// Portrait video for the single-video questions (word-from-video, true/false).
private const val SINGLE_VIDEO_ASPECT = 0.82f
private val SINGLE_VIDEO_PAD = 32.dp

// Horizontal inset for the two stacked video options in "pick the sign".
private val PAIR_VIDEO_PAD = 44.dp

// Word/true-false choice buttons.
private val CHOICE_HEIGHT = 64.dp
private val CARD_SHAPE = RoundedCornerShape(24.dp)

// ── Type 1: pick the word for the shown video ────────────────────────────────
@Composable
fun WordFromVideoQuestion(
    question: QuizQuestion,
    revealed: Boolean,
    onSelectionChanged: (complete: Boolean, correct: Boolean, chosen: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        QuestionPrompt("What does this sign mean?")
        SignVideo(
            signId = question.targetSignId,
            word = question.targetWord,
            aspectRatio = SINGLE_VIDEO_ASPECT,
            modifier = Modifier.padding(horizontal = SINGLE_VIDEO_PAD),
        )
        Spacer(Modifier.height(24.dp))

        question.wordOptions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { option ->
                    OptionChip(
                        text = option,
                        state = optionState(revealed, selected == option, option == question.correctWord),
                        modifier = Modifier.weight(1f).height(CHOICE_HEIGHT),
                        enabled = !revealed,
                        onClick = {
                            selected = option
                            onSelectionChanged(true, option == question.correctWord, option)
                        },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Type 2: pick the correct video for the given word ────────────────────────
@Composable
fun VideoFromWordQuestion(
    question: QuizQuestion,
    revealed: Boolean,
    onSelectionChanged: (complete: Boolean, correct: Boolean, chosen: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    // Fills the screen: the two video options stretch to share the available
    // height, so this question fits without scrolling (the requested layout).
    Column(modifier = modifier.fillMaxSize()) {
        QuestionPrompt("Which one is the sign for ${question.targetWord}?")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = PAIR_VIDEO_PAD, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            question.signOptions.forEach { signId ->
                val state = optionState(revealed, selected == signId, signId == question.correctSignId)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(CARD_SHAPE)
                        .border(feedbackBorder(state), CARD_SHAPE)
                        .clickable(enabled = !revealed) {
                            selected = signId
                            onSelectionChanged(true, signId == question.correctSignId, QuizContent.word(signId))
                        },
                ) {
                    SignVideo(
                        signId = signId,
                        word = QuizContent.word(signId),
                        aspectRatio = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Type 3: match videos to words (uniform 2×2 grid) ─────────────────────────
@Composable
fun MatchQuestion(
    question: QuizQuestion,
    revealed: Boolean,
    onSelectionChanged: (complete: Boolean, correct: Boolean, chosen: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leftSigns = question.signOptions
    val rightWords = question.matchWords

    var selectedLeft by remember { mutableStateOf<Int?>(null) }
    var assignment by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    fun report() {
        val complete = assignment.size == leftSigns.size
        val correct = complete && assignment.all { (l, r) ->
            QuizContent.word(leftSigns[l]) == rightWords[r]
        }
        onSelectionChanged(complete, correct, null)
    }

    // fillMaxSize + weight rows/cells make the 2×2 grid stretch to fill the space
    // between the prompt and the Check button, so the cards run down the screen
    // instead of leaving a large empty gap below (the requested layout).
    Column(modifier = modifier.fillMaxSize()) {
        QuestionPrompt("Match the words")
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left: video prompts
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                leftSigns.forEachIndexed { i, signId ->
                    val correctPair = revealed && assignment[i]
                        ?.let { QuizContent.word(signId) == rightWords[it] } == true
                    MatchCell(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        borderColor = cellBorder(
                            revealed = revealed,
                            assigned = assignment.containsKey(i),
                            correct = correctPair,
                            selected = selectedLeft == i,
                            accent = assignment[i]?.let { pairColor(i) },
                        ),
                        onClick = if (revealed) null else ({ selectedLeft = i }),
                    ) {
                        SignVideo(
                            signId = signId,
                            word = QuizContent.word(signId),
                            aspectRatio = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // Right: word cards
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rightWords.forEachIndexed { j, word ->
                    val assignedLeft = assignment.entries.firstOrNull { it.value == j }?.key
                    val correctPair = revealed && assignedLeft
                        ?.let { QuizContent.word(leftSigns[it]) == word } == true
                    MatchCell(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        borderColor = cellBorder(
                            revealed = revealed,
                            assigned = assignedLeft != null,
                            correct = correctPair,
                            selected = false,
                            accent = assignedLeft?.let { pairColor(it) },
                        ),
                        onClick = if (revealed) null else ({
                            val l = selectedLeft
                            if (l != null) {
                                assignment = assignment.filterValues { it != j } + (l to j)
                                selectedLeft = null
                                report()
                            }
                        }),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One matching-grid cell: a rounded, optionally-bordered, tappable container. */
@Composable
private fun MatchCell(
    modifier: Modifier,
    borderColor: Color,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(3.dp, borderColor), RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        content()
    }
}

@Composable
private fun cellBorder(
    revealed: Boolean,
    assigned: Boolean,
    correct: Boolean,
    selected: Boolean,
    accent: Color?,
): Color = when {
    revealed && assigned -> if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    selected -> MaterialTheme.colorScheme.primary
    accent != null -> accent
    else -> MaterialTheme.colorScheme.outline
}

// ── Type 4: is the video the claimed word? (True / False) ────────────────────
@Composable
fun TrueFalseQuestion(
    question: QuizQuestion,
    revealed: Boolean,
    onSelectionChanged: (complete: Boolean, correct: Boolean, chosen: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        QuestionPrompt("Is this the sign for ${question.claimedWord}?")
        SignVideo(
            signId = question.targetSignId,
            word = question.targetWord,
            aspectRatio = SINGLE_VIDEO_ASPECT,
            modifier = Modifier.padding(horizontal = SINGLE_VIDEO_PAD),
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(true to "True", false to "False").forEach { (value, label) ->
                OptionChip(
                    text = label,
                    state = optionState(revealed, selected == value, value == question.claimedIsCorrect),
                    modifier = Modifier.weight(1f).height(CHOICE_HEIGHT),
                    enabled = !revealed,
                    onClick = {
                        selected = value
                        onSelectionChanged(true, value == question.claimedIsCorrect, null)
                    },
                )
            }
        }
    }
}

// ── shared bits ──────────────────────────────────────────────────────────────

@Composable
private fun QuestionPrompt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Visual state of a tappable option. */
enum class OptionVisual { NEUTRAL, SELECTED, CORRECT, WRONG }

private fun optionState(revealed: Boolean, isSelected: Boolean, isCorrect: Boolean): OptionVisual =
    when {
        revealed && isCorrect -> OptionVisual.CORRECT
        revealed && isSelected && !isCorrect -> OptionVisual.WRONG
        isSelected -> OptionVisual.SELECTED
        else -> OptionVisual.NEUTRAL
    }

@Composable
private fun feedbackBorder(state: OptionVisual): BorderStroke = when (state) {
    OptionVisual.CORRECT -> BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)
    OptionVisual.WRONG -> BorderStroke(3.dp, MaterialTheme.colorScheme.error)
    OptionVisual.SELECTED -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    OptionVisual.NEUTRAL -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
}

/** A rounded, tappable label chip used across the option-based questions. */
@Composable
fun OptionChip(
    text: String,
    state: OptionVisual,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = when (state) {
        OptionVisual.CORRECT -> MaterialTheme.colorScheme.tertiaryContainer
        OptionVisual.WRONG -> MaterialTheme.colorScheme.errorContainer
        OptionVisual.SELECTED -> MaterialTheme.colorScheme.primaryContainer
        OptionVisual.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(feedbackBorder(state), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** Distinct accent per matched pair, so connections read at a glance. */
@Composable
private fun pairColor(pairIndex: Int): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
    )
    return palette[pairIndex % palette.size]
}
