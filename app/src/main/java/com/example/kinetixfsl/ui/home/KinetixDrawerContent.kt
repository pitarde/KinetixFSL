package com.example.kinetixfsl.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.Avatar
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.RecentCommunitiesRepository
import com.example.kinetixfsl.community.RecentCommunity
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import kotlinx.coroutines.launch

/**
 * The side drawer content. All items are visual only — they take an [on Click]
 * so the parent can wire real destinations later, but for now the parent just
 * closes the drawer on any tap.
 *
 * Sections mirror the design: Dashboard is alone up top, then Community-related
 * items, then a lone About at the bottom.
 *
 * Every color here comes from MaterialTheme.colorScheme, not a fixed brand
 * constant — that's what lets this drawer follow the system's dark/light setting.
 */
@Composable
fun KinetixDrawerContent(
    onDashboardClick: () -> Unit,
    onGestureToTextClick: () -> Unit,
    onTextToGestureClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onStartCommunityClick: () -> Unit,
    onDiscoverCommunitiesClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opens a community from Recently Visited. Null hides the whole section —
     * which is what the Home drawer does, since it has no community overlay
     * stack to open one onto.
     */
    onRecentCommunityClick: ((String) -> Unit)? = null,
    /**
     * Opens the Inbox (chat + notifications). Null hides the row — the Inbox
     * lives behind an overlay stack only the Community screen owns, so a host
     * without one (today, the Home dashboard) simply doesn't offer it here.
     */
    onInboxClick: (() -> Unit)? = null,
    /** Badge on the Inbox row — unread messages plus unread notifications. */
    inboxUnreadCount: Int = 0,
) {
    // "See all" swaps the whole drawer for a dedicated Recently Visited view
    // (back arrow, per-row remove, Clear all), matching the design — then back
    // returns to the normal menu. State lives here so both views share it.
    var showAllRecents by rememberSaveable { mutableStateOf(false) }

    if (showAllRecents && onRecentCommunityClick != null) {
        RecentlyVisitedFullView(
            modifier = modifier,
            onBack = { showAllRecents = false },
            onCommunityClick = onRecentCommunityClick,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {

        Spacer(Modifier.height(24.dp))

        DrawerItem(HomeIcons.GridDashboard, "Dashboard", onDashboardClick)
        DrawerItem(HomeIcons.HandToText, "Gesture to Text", onGestureToTextClick)
        DrawerItem(HomeIcons.TextToHand, "Text to Gesture", onTextToGestureClick)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        DrawerItem(HomeIcons.Community, "Community", onCommunityClick)
        DrawerItem(HomeIcons.Plus, "Start a community", onStartCommunityClick)
        DrawerItem(HomeIcons.Search, "Discover communities", onDiscoverCommunitiesClick)
        // Inbox lives here now rather than as its own bottom-nav tab or top-bar
        // icon — one destination for both direct messages and notifications,
        // reached the same way Discover and Start a community are.
        if (onInboxClick != null) {
            DrawerItem(
                icon = CommunityIcons.Bell,
                label = "Chat/Notification",
                onClick = onInboxClick,
                badgeCount = inboxUnreadCount,
            )
        }

        if (onRecentCommunityClick != null) {
            RecentlyVisitedSection(
                onCommunityClick = onRecentCommunityClick,
                onSeeAll = { showAllRecents = true },
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        DrawerItem(icon = null, label = "About", onClick = onAboutClick)
    }
}

/**
 * Communities the user has visited, joined or created, newest first — the
 * drawer's history section, in the spirit of Reddit's "Recent".
 *
 * Renders nothing at all until there's something to show, so a new account
 * doesn't get an empty heading below the menu.
 *
 * Shows the [COLLAPSED_ROWS] most recent inline; "See all" hands off to the
 * full view via [onSeeAll]. Collapsed rows carry no remove button — a ✕ on
 * every row would make the drawer's main navigation read as a list of things
 * to dismiss rather than places to go. Removing and clearing live in the full
 * view.
 */
@Composable
private fun RecentlyVisitedSection(
    onCommunityClick: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val repository = remember { RecentCommunitiesRepository() }
    // Hold the flow across recompositions. Without this, `observe()` builds a
    // fresh flow every recomposition, so collectAsStateWithLifecycle keeps
    // re-subscribing — and each new subscription re-emits the stale marker copy
    // before its live listeners catch up, which read as a fast blink between the
    // old and new community picture right after an edit.
    val recentsFlow = remember(repository) { repository.observe() }
    val recents by recentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (recents.isEmpty()) return

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Recently Visited",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // Always offered whenever there's history: the full view is the only
        // place to remove a single community or Clear all, so it has to be
        // reachable even with just one or two here — not only once the list
        // spills past what's shown inline.
        Text(
            text = "See all",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }

    recents.take(COLLAPSED_ROWS).forEach { community ->
        RecentRow(
            community = community,
            onClick = { onCommunityClick(community.id) },
            onRemove = null,
        )
    }
}

/**
 * The dedicated Recently Visited view the drawer swaps to on "See all".
 *
 * Same 280dp drawer surface, so it reads as the drawer changing pages rather
 * than a new screen sliding in. Every row gets a remove ✕, and "Clear all"
 * empties the lot — the management actions that would clutter the collapsed
 * section in the main menu.
 */
@Composable
private fun RecentlyVisitedFullView(
    onBack: () -> Unit,
    onCommunityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { RecentCommunitiesRepository() }
    val scope = rememberCoroutineScope()
    // Remembered so recomposition doesn't rebuild the flow and force a
    // re-subscribe — see the note in RecentlyVisitedSection.
    val recentsFlow = remember(repository) { repository.observe() }
    val recents by recentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Phone back returns to the menu rather than closing the drawer, matching
    // the on-screen back arrow.
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(6.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Recently Visited",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (recents.isNotEmpty()) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { scope.launch { repository.clearAll() } }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (recents.isEmpty()) {
            // Clearing the last one empties the view; say so rather than leaving
            // a blank page under the header.
            Text(
                text = "Nothing here yet. Communities you visit show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        } else {
            recents.forEach { community ->
                RecentRow(
                    community = community,
                    onClick = { onCommunityClick(community.id) },
                    onRemove = { scope.launch { repository.remove(community.id) } },
                )
            }
        }
    }
}

/** One community row, shared by the collapsed section and the full view. */
@Composable
private fun RecentRow(
    community: RecentCommunity,
    onClick: () -> Unit,
    /** Null hides the remove ✕ — the collapsed section passes null. */
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrl = community.avatarUrl,
            name = community.name,
            size = 24.dp,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = community.name.ifBlank { "Community" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            Icon(
                imageVector = HomeIcons.Close,
                contentDescription = "Remove from recent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove)
                    .padding(6.dp),
            )
        }
    }
}

/** Rows shown before "See all" is tapped. */
private const val COLLAPSED_ROWS = 3

/** A single tappable row. Passing null [icon] indents the label — used for About. */
@Composable
private fun DrawerItem(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Unread count next to the label. Zero draws nothing — used by Inbox. */
    badgeCount: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (badgeCount > 0) {
            Spacer(Modifier.weight(1f))
            com.example.kinetixfsl.community.inbox.CountBadge(count = badgeCount)
        }
    }
}

@Preview(showBackground = true, name = "Drawer - Light")
@Composable
private fun KinetixDrawerContentPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        KinetixDrawerContent(
            onDashboardClick = {},
            onGestureToTextClick = {},
            onTextToGestureClick = {},
            onCommunityClick = {},
            onStartCommunityClick = {},
            onDiscoverCommunitiesClick = {},
            onAboutClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Drawer - Dark")
@Composable
private fun KinetixDrawerContentPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        KinetixDrawerContent(
            onDashboardClick = {},
            onGestureToTextClick = {},
            onTextToGestureClick = {},
            onCommunityClick = {},
            onStartCommunityClick = {},
            onDiscoverCommunitiesClick = {},
            onAboutClick = {},
        )
    }
}