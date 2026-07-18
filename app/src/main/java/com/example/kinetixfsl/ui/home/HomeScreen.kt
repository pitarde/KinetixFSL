package com.example.kinetixfsl.ui.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.ui.home.tabs.CameraTabPlaceholder
import com.example.kinetixfsl.ui.home.tabs.GameTabPlaceholder
import com.example.kinetixfsl.ui.home.tabs.ModulesTabPlaceholder
import com.example.kinetixfsl.ui.home.tabs.ProfileTabPlaceholder
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixSurface
import com.example.kinetixfsl.ui.theme.KinetixWhite
import kotlinx.coroutines.launch

/**
 * The Home shell. Wraps everything the user sees after login: side drawer,
 * top bar with hamburger, the current tab's content, and the bottom nav bar.
 *
 * The drawer only opens from the Home tab (it's the only tab with the hamburger).
 * Other tabs get a simple centered title bar instead. This matches how most apps
 * feel: the drawer belongs to Home, not to Camera / Game / etc.
 */
@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(HomeTab.HOME) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = KinetixWhite,
            ) {
                KinetixDrawerContent(
                    // All drawer clicks close the drawer for now — real destinations
                    // hook in later, one feature at a time.
                    onDashboardClick = {
                        selectedTab = HomeTab.HOME
                        scope.launch { drawerState.close() }
                    },
                    onGestureToTextClick = { /* TODO(gesture-to-text) */
                        scope.launch { drawerState.close() }
                    },
                    onTextToGestureClick = { /* TODO(text-to-gesture) */
                        scope.launch { drawerState.close() }
                    },
                    onCommunityClick = { /* TODO(community) */
                        scope.launch { drawerState.close() }
                    },
                    onStartCommunityClick = { /* TODO(community-create) */
                        scope.launch { drawerState.close() }
                    },
                    onDiscoverCommunitiesClick = { /* TODO(community-discover) */
                        scope.launch { drawerState.close() }
                    },
                    onAboutClick = { /* TODO(about) */
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        HomeScaffold(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onMenuClick = { scope.launch { drawerState.open() } },
            onSignOut = onSignOut,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeScaffold(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onMenuClick: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KinetixSurface)
            .statusBarsPadding(),
    ) {
        HomeTopBar(
            tab = selectedTab,
            onMenuClick = onMenuClick,
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                HomeTab.HOME -> DashboardContent()
                HomeTab.MODULES -> ModulesTabPlaceholder()
                HomeTab.CAMERA -> CameraTabPlaceholder()
                HomeTab.GAME -> GameTabPlaceholder()
                HomeTab.PROFILE -> ProfileTabPlaceholder(onSignOut = onSignOut)
            }
        }

        HomeBottomNav(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )
    }
}

/**
 * The top bar. Only the Home tab shows the hamburger — every other tab shows a
 * centered title so the user always knows where they are.
 */
@Composable
private fun HomeTopBar(
    tab: HomeTab,
    onMenuClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (tab == HomeTab.HOME) {
            Icon(
                imageVector = HomeIcons.Menu,
                contentDescription = "Open menu",
                tint = KinetixInk,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
                    .clickable(onClick = onMenuClick),
            )
        } else {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleLarge,
                color = KinetixInk,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The five-icon bottom nav. Selected icon takes the brand indigo, rest are muted. */
@Composable
private fun HomeBottomNav(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KinetixWhite),
    ) {
        HorizontalDivider(color = KinetixOutline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTab.entries.forEach { tab ->
                NavItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) KinetixIndigo else KinetixMuted
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    KinetixFSLTheme {
        HomeScreen(onSignOut = {})
    }
}