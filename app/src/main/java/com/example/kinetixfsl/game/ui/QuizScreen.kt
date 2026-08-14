package com.example.kinetixfsl.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.QuestionType

/**
 * The quiz phase: the five questions of a level, one at a time. Owns the
 * Check → feedback → Next cycle. Tapping Check locks the options and pops a
 * centered "Correct!/Try again" card (the "Correct_Notification" mockup); its
 * Next button advances.
 */
@Composable
fun QuizQuestionScreen(
    plan: LevelPlan,
    index: Int,
    onSubmitAnswer: (correct: Boolean) -> Unit,
    onMistake: (targetSignId: String, targetWord: String, chosenWord: String) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val question = plan.questions[index]
    val isLast = index == plan.questions.lastIndex

    val feedback = rememberQuizFeedback()

    // Per-question UI state; keyed on index so each question starts fresh.
    var complete by remember(index) { mutableStateOf(false) }
    var correct by remember(index) { mutableStateOf(false) }
    var revealed by remember(index) { mutableStateOf(false) }
    // What the feedback card shows. NOT keyed to index, so advancing to the next
    // question never momentarily flips it to a stale "wrong" state — this is what
    // kills the split-second "Not quite" flash on Next.
    var shownCorrect by remember { mutableStateOf(false) }
    // The label the user chose (for logging confusion pairs); null for
    // match/true-false where a sign-pair isn't meaningful.
    var chosenLabel by remember(index) { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            QuizTopBar(
                progress = (index + 1f) / plan.questions.size,
                onClose = onClose,
            )

            // The question area fills the space between the bar and the button.
            // Matching fills that space (its 2×2 grid stretches to fit); the other
            // question types scroll if they're taller than the screen.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                key(index) {
                    val onSelection: (Boolean, Boolean, String?) -> Unit = { c, cor, chosen ->
                        complete = c
                        correct = cor
                        chosenLabel = chosen
                    }
                    when (question.type) {
                        // These two fill the screen — their cards stretch to fit,
                        // so they never scroll.
                        QuestionType.MATCH ->
                            MatchQuestion(question, revealed, onSelection, Modifier.fillMaxSize())
                        QuestionType.VIDEO_FROM_WORD ->
                            VideoFromWordQuestion(question, revealed, onSelection, Modifier.fillMaxSize())
                        // The single-video questions scroll if taller than the screen.
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Spacer(Modifier.height(8.dp))
                            when (question.type) {
                                QuestionType.WORD_FROM_VIDEO ->
                                    WordFromVideoQuestion(question, revealed, onSelection)
                                QuestionType.TRUE_FALSE ->
                                    TrueFalseQuestion(question, revealed, onSelection)
                                else -> Unit
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (!revealed) {
                        shownCorrect = correct
                        revealed = true
                        onSubmitAnswer(correct)
                        // Log a confusion pair when a word-based answer is wrong.
                        val chosen = chosenLabel
                        if (!correct && chosen != null) {
                            onMistake(question.targetSignId, question.targetWord, chosen)
                        }
                        feedback.play(correct)
                    }
                },
                enabled = complete && !revealed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = "Check",
                    color = if (complete && !revealed) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Dim scrim + centered feedback card, animated in on Check.
        //
        // Exits are instant (ExitTransition.None): advancing to the next question
        // resets `correct` to false, so a fade-out would briefly render the card
        // in its "Not quite" state — the split-second flash. Hiding immediately
        // avoids composing that stale frame at all.
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
            )
        }
        AnimatedVisibility(
            visible = revealed,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = ExitTransition.None,
            modifier = Modifier.align(Alignment.Center),
        ) {
            FeedbackCard(correct = shownCorrect, isLast = isLast, onNext = onNext)
        }
    }
}

@Composable
private fun FeedbackCard(correct: Boolean, isLast: Boolean, onNext: () -> Unit) {
    val accent =
        if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (correct) QuizIcons.CheckCircle else QuizIcons.Close,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.height(26.dp).width(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (correct) "Correct!" else "Not quite",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text(
                text = if (isLast) "Finish" else "Next",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
