package com.example.kinetixfsl.community.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.kinetixfsl.community.model.CommunityCategories
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixPageBackground
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * "Discover communities" — reached from the side drawer.
 *
 * The top half lets the user explore by topic: tapping a category chip opens a
 * list of the communities filed under it. Below that, "Recommended for you"
 * surfaces communities directly, each with a Join button.
 */
@Composable
fun DiscoverCommunitiesScreen(
    onClose: () -> Unit,
    onOpenCommunity: (communityId: String) -> Unit,
    onOpenCategory: (category: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverCommunitiesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val joinedIds by viewModel.joinedIds.collectAsStateWithLifecycle()

    // This screen's top bar is always dark (KinetixNavy in light mode,
    // colorScheme.surface in dark) — see DiscoverTopBar — so it always wants
    // light (white) status bar icons, same as the Home Feed's own bar.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)

    // Offline vs error wording, plus auto-retry when the connection returns.
    val isOnline by com.example.kinetixfsl.ui.components.rememberIsOnline()
    androidx.compose.runtime.LaunchedEffect(isOnline, state) {
        if (isOnline && state is DiscoverState.Error) viewModel.retry()
    }

    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            // Off-white in light mode, matching the Home Feed — see
            // KinetixPageBackground — so the pure-white community cards
            // below read as cards. Dark mode keeps the normal theme
            // background.
            .background(
                if (isSystemInDarkTheme()) {
                    MaterialTheme.colorScheme.background
                } else {
                    KinetixPageBackground
                },
            ),
    ) {
        DiscoverTopBar(onClose = onClose)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                // Inset the list's bottom edge past the system navigation bar.
                // Done as a modifier (the same way the other screens do it)
                // rather than via contentPadding — the computed WindowInsets
                // value was resolving to zero here, so the last card kept
                // sitting under the nav buttons.
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ---- Header ----
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Discover communities",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Explore communities by topic",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

        // ---- Category chips ----
        item {
            CategoryChips(
                onCategoryClick = onOpenCategory,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ---- Recommended for you ----
        item {
            Text(
                text = "Recommended for you",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
            )
        }

        when (val current = state) {
            is DiscoverState.Loading -> item(key = "discover-skeleton") {
                com.example.kinetixfsl.ui.components.CommunityListSkeleton()
            }
            is DiscoverState.Error -> item(key = "discover-error") {
                if (isOnline) {
                    com.example.kinetixfsl.ui.components.ErrorState(
                        onRetry = { viewModel.retry() },
                        message = current.message,
                    )
                } else {
                    com.example.kinetixfsl.ui.components.OfflineState(
                        onRetry = { viewModel.retry() },
                    )
                }
            }
            is DiscoverState.Success -> {
                if (current.communities.isEmpty()) {
                    item {
                        Text(
                            text = "No communities yet. Be the first to start one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                } else {
                    items(current.communities, key = { it.id }) { community ->
                        CommunityCard(
                            community = community,
                            isJoined = community.id in joinedIds,
                            onClick = { onOpenCommunity(community.id) },
                            onJoin = { viewModel.join(community) },
                            onLeave = { viewModel.leave(community.id) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommunityCategories.ALL.forEach { category ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    // Pure white in light mode, matching every other card on
                    // this screen. Dark mode used colorScheme.surface, which
                    // is exactly the top bar's own color — see
                    // KinetixDarkSurfaceVariant, the token meant for cards
                    // sitting on the dark page background, for a chip that
                    // actually stands out from both the page and the bar.
                    .background(
                        if (isSystemInDarkTheme()) {
                            com.example.kinetixfsl.ui.theme.KinetixDarkSurfaceVariant
                        } else {
                            KinetixWhite
                        },
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable { onCategoryClick(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A slim top bar matching the Home Feed's own: KinetixNavy in light mode,
 * the normal theme surface in dark mode — just the back arrow, since the
 * screen's actual title reads better as a large heading in the scrolling
 * content below than crammed into a 56dp bar.
 */
@Composable
private fun DiscoverTopBar(onClose: () -> Unit) {
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
        }
    }
}
