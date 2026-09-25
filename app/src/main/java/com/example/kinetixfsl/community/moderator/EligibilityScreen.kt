package com.example.kinetixfsl.community.moderator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixPageBackground
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * "Become a Moderator" eligibility — reached from the side drawer and from
 * the bottom of Discover Communities. Shows four YouTube-Studio-style
 * progress rows and a primary button that unlocks once every threshold is
 * met.
 */
@Composable
fun EligibilityScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EligibilityViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)

    BackHandler(onBack = onClose)

    val isApprovedModerator = (state as? EligibilityUiState.Ready)?.applicationStatus == "approved"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.background
                } else {
                    KinetixPageBackground
                },
            ),
    ) {
        EligibilityTopBar(
            onClose = onClose,
            title = if (isApprovedModerator) "Your Analytics" else "Eligibility",
        )

        when (val current = state) {
            is EligibilityUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is EligibilityUiState.Ready -> if (isApprovedModerator) {
                // Once approved, eligibility itself is moot — show the
                // moderator their own content analytics instead, the same way
                // this screen showed their progress toward the badge before.
                ModeratorAnalyticsContent(viewModel = viewModel())
            } else {
                EligibilityContent(
                    state = current,
                    onApply = viewModel::apply,
                )
            }
        }
    }
}

@Composable
private fun EligibilityContent(
    state: EligibilityUiState.Ready,
    onApply: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 16.dp,
            ),
        ) {
            item {
                Text(
                    text = "Become a Moderator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                ExplainerCard()
                Spacer(Modifier.height(16.dp))
                InfoCard(text = state.asOfDate)
                Spacer(Modifier.height(20.dp))
            }

            items(state.requirements, key = { it.label }) { requirement ->
                RequirementRow(requirement)
                Spacer(Modifier.height(18.dp))
            }

            if (state.applicationStatus == "rejected" && !state.rejectionNote.isNullOrBlank()) {
                item {
                    InfoCard(
                        text = "Your last application was declined: ${state.rejectionNote}",
                        emphasize = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (!state.submitError.isNullOrBlank()) {
                item {
                    Text(
                        text = state.submitError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        BottomActionBar(state = state, onApply = onApply)
    }
}

/** Short explainer of what a moderator is and what the badge means. */
@Composable
private fun ExplainerCard() {
    Card {
        Text(
            text = "Moderators are trusted educators and SPED professionals whose " +
                "posts are automatically marked Validated, no review queue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InfoCard(text: String, emphasize: Boolean = false) {
    val tint = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!emphasize) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(1.5.dp, tint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "i",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
            )
        }
    }
}

/**
 * The pure-white-in-light / dark-surface-variant-in-dark rounded card every
 * section here sits in. Internal (not private) — [ModeratorAnalyticsScreen]
 * reuses it for the same visual language.
 */
@Composable
internal fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSystemInDarkTheme()) {
                    com.example.kinetixfsl.ui.theme.KinetixDarkSurfaceVariant
                } else {
                    KinetixWhite
                },
            )
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun RequirementRow(requirement: RequirementProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                text = requirement.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${requirement.current} / ${requirement.target}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (requirement.met) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { requirement.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun BottomActionBar(
    state: EligibilityUiState.Ready,
    onApply: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.surface
                } else {
                    KinetixWhite
                },
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        // "approved" never reaches here — EligibilityScreen routes an approved
        // moderator straight to ModeratorAnalyticsContent instead of this button.
        val label = when (state.applicationStatus) {
            "pending" -> "Pending Review"
            "rejected" -> "Reapply"
            else -> "Apply as Moderator"
        }
        val enabled = state.allMet &&
            state.applicationStatus != "pending" &&
            !state.isSubmitting

        Button(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Slim dark top bar, matching Discover Communities' own [DiscoverTopBar]. */
@Composable
private fun EligibilityTopBar(onClose: () -> Unit, title: String) {
    val dark = isSystemInDarkTheme()
    val barBackground = if (dark) MaterialTheme.colorScheme.surface else KinetixNavy
    val barContentColor = if (dark) MaterialTheme.colorScheme.onSurface else KinetixWhite

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.ArrowBack,
                contentDescription = "Back",
                tint = barContentColor,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = barContentColor,
            )
        }
    }
}
