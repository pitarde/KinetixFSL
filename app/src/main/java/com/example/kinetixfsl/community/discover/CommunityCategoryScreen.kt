package com.example.kinetixfsl.community.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.CommunityIcons

/**
 * The communities filed under one category — reached by tapping a chip on the
 * Discover screen. Just the category's title and a list of Join-able cards.
 */
@Composable
fun CommunityCategoryScreen(
    category: String,
    onClose: () -> Unit,
    onOpenCommunity: (communityId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(category) { DiscoverCommunitiesViewModel(filterCategory = category) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val joinedIds by viewModel.joinedIds.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    // This screen's top bar paints dark behind the status bar too, same as
    // the Home Feed's — see StatusBarLightIcons for why this needs its own
    // override rather than relying on the app's light/dark theme default.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            // Off-white in light mode, matching the Home Feed — see
            // KinetixPageBackground — so the pure-white community cards
            // below read as cards. Dark mode keeps the normal background.
            .background(
                if (dark) MaterialTheme.colorScheme.background else com.example.kinetixfsl.ui.theme.KinetixPageBackground,
            ),
    ) {
        // ---- Top bar: back + category name — same treatment (color, size,
        // painting behind the status bar) as the Home Feed's own top bar. ----
        val barBackground = if (dark) MaterialTheme.colorScheme.surface else com.example.kinetixfsl.ui.theme.KinetixNavy
        val barContentColor = if (dark) MaterialTheme.colorScheme.onSurface else com.example.kinetixfsl.ui.theme.KinetixWhite
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
                        .size(26.dp)
                        .clickable(onClick = onClose),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = barContentColor,
                )
            }
        }

        when (val current = state) {
            is DiscoverState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DiscoverState.Error -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is DiscoverState.Success -> {
                if (current.communities.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No communities under \"$category\" yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.communities, key = { it.id }) { community ->
                            CommunityCard(
                                community = community,
                                isJoined = community.id in joinedIds,
                                onClick = { onOpenCommunity(community.id) },
                                onJoin = { viewModel.join(community) },
                                onLeave = { viewModel.leave(community.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
