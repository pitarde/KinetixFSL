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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.modules.ModulesScreen
import com.example.kinetixfsl.ui.home.tabs.CameraTabPlaceholder
import com.example.kinetixfsl.ui.home.tabs.GameTabPlaceholder
import com.example.kinetixfsl.ui.home.tabs.ProfileTabPlaceholder
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToSignList: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // rememberSaveable keeps the selected tab alive across navigation
    // (navigate to SignList → press back → still on Modules tab, not Home).
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.HOME) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                KinetixDrawerContent(
                    onDashboardClick = {
                        selectedTab = HomeTab.HOME
                        scope.launch { drawerState.close() }
                    },
                    onGestureToTextClick = {
                        scope.launch { drawerState.close() }
                    },
                    onTextToGestureClick = {
                        scope.launch { drawerState.close() }
                    },
                    onCommunityClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToCommunity()
                        }
                    },
                    onStartCommunityClick = {
                        scope.launch { drawerState.close() }
                    },
                    onDiscoverCommunitiesClick = {
                        scope.launch { drawerState.close() }
                    },
                    onAboutClick = {
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
            onNavigateToSignList = onNavigateToSignList,
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
    onNavigateToSignList: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        HomeTopBar(
            tab = selectedTab,
            onMenuClick = onMenuClick,
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                HomeTab.HOME -> DashboardContent()
                HomeTab.MODULES -> ModulesScreen(
                    onCategoryClick = { category ->
                        onNavigateToSignList(category.id)
                    },
                )
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
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
                    .clickable(onClick = onMenuClick),
            )
        } else {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The five-icon bottom nav. Selected icon takes the theme's primary, rest are muted. */
@Composable
private fun HomeBottomNav(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
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

@Preview(showBackground = true, showSystemUi = true, name = "Home - Light")
@Composable
private fun HomeScreenPreviewLight() {
    KinetixFSLTheme(darkTheme = false) {
        HomeScreen(onSignOut = {}, onNavigateToCommunity = {}, onNavigateToSignList = {})
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home - Dark")
@Composable
private fun HomeScreenPreviewDark() {
    KinetixFSLTheme(darkTheme = true) {
        HomeScreen(onSignOut = {}, onNavigateToCommunity = {}, onNavigateToSignList = {})
    }
}