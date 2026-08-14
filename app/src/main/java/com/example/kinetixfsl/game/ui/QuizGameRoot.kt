package com.example.kinetixfsl.game.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.game.QuizGameViewModel
import com.example.kinetixfsl.game.QuizScreen

/**
 * Entry point for the Quiz Game — rendered by the Home screen's Game tab.
 *
 * Owns the [QuizGameViewModel] and dispatches to the level map, tutorial, quiz,
 * and result screens. Reports [onImmersiveChange] whenever an active level
 * attempt is on screen so the host can hide its top bar and bottom nav (the
 * mockups show those flows full-screen with only an X and a progress bar).
 */
@Composable
fun QuizGameRoot(
    onImmersiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel = remember { QuizGameViewModel(context.applicationContext) }
    val screen by viewModel.screen.collectAsStateWithLifecycle()

    val immersive = screen is QuizScreen.Tutorial ||
        screen is QuizScreen.Quiz ||
        screen is QuizScreen.Result
    LaunchedEffect(immersive) { onImmersiveChange(immersive) }

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = screen) {
            is QuizScreen.Loading -> Unit

            is QuizScreen.Map -> {
                LevelMapScreen(
                    state = s,
                    onLevelClick = viewModel::startLevel,
                    onGoalAction = viewModel::onGoalAction,
                )
                s.resumeSession?.let {
                    ResumeDialog(
                        onContinue = viewModel::resumeLast,
                        onDismiss = viewModel::dismissResumePrompt,
                    )
                }
            }

            is QuizScreen.Tutorial -> TutorialScreen(
                plan = s.plan,
                index = s.index,
                onNext = viewModel::tutorialNext,
                onPrev = viewModel::tutorialPrev,
                onClose = viewModel::exitToMap,
                modifier = Modifier.navigationBarsPadding(),
            )

            is QuizScreen.Quiz -> QuizQuestionScreen(
                plan = s.plan,
                index = s.index,
                onSubmitAnswer = viewModel::submitAnswer,
                onMistake = viewModel::recordMistake,
                onNext = viewModel::quizNext,
                onClose = viewModel::exitToMap,
                modifier = Modifier.navigationBarsPadding(),
            )

            is QuizScreen.Result -> ResultScreen(
                plan = s.plan,
                correctCount = s.correctCount,
                passed = s.passed,
                elapsedSeconds = s.elapsedSeconds,
                onRetry = viewModel::retryLevel,
                onProceed = viewModel::proceedToNext,
                onHome = viewModel::finishResult,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

/** The resume popup shown when the game opens on an unfinished attempt (§6). */
@Composable
private fun ResumeDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Continue last session?") },
        text = { Text("You have an unfinished level. Pick up where you left off?") },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Yes", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
