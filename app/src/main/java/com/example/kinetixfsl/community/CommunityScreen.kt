package com.example.kinetixfsl.community


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.tabs.CommunityProfilePlaceholder
import com.example.kinetixfsl.community.tabs.NotificationsPlaceholder
import com.example.kinetixfsl.ui.home.KinetixDrawerContent
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(CommunityTab.HOME) }
    var commentPost: Post? by remember { mutableStateOf(null) }
    // Shared list state so the bottom nav can scroll it to top.
    val feedListState = rememberLazyListState()

    // The ViewModel — kept here so the bottom nav can call refresh().
    val feedViewModel = remember { CommunityFeedViewModel() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                KinetixDrawerContent(
                    onDashboardClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToDashboard()
                        }
                    },
                    onGestureToTextClick = { scope.launch { drawerState.close() } },
                    onTextToGestureClick = { scope.launch { drawerState.close() } },
                    onCommunityClick = {
                        selectedTab = CommunityTab.HOME
                        scope.launch { drawerState.close() }
                    },
                    onStartCommunityClick = { scope.launch { drawerState.close() } },
                    onDiscoverCommunitiesClick = { scope.launch { drawerState.close() } },
                    onAboutClick = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            CommunityScaffold(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onMenuClick = { scope.launch { drawerState.open() } },
                onSelectCommunity = { /* TODO */ },
                onCommentClick = { post -> commentPost = post },
                feedListState = feedListState,
                feedViewModel = feedViewModel,
                onScrollToTopAndRefresh = {
                    scope.launch {
                        feedListState.animateScrollToItem(0)
                        feedViewModel.refresh()
                    }
                },
            )

            val activeCommentPost = commentPost
            if (activeCommentPost != null) {
                val commentVm = remember(activeCommentPost.id) {
                    CommentViewModel(postId = activeCommentPost.id)
                }
                CommentScreen(
                    post = activeCommentPost,
                    viewModel = commentVm,
                    onClose = { commentPost = null },
                )
            }
        }
    }
}

@Composable
private fun CommunityScaffold(
    selectedTab: CommunityTab,
    onTabSelected: (CommunityTab) -> Unit,
    onMenuClick: () -> Unit,
    onSelectCommunity: () -> Unit,
    onCommentClick: (Post) -> Unit,
    feedListState: LazyListState,
    feedViewModel: CommunityFeedViewModel,
    onScrollToTopAndRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        CommunityTopBar(tab = selectedTab, onMenuClick = onMenuClick)

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                CommunityTab.HOME -> CommunityFeedContent(
                    viewModel = feedViewModel,
                    listState = feedListState,
                    onCommentClick = onCommentClick,
                )
                CommunityTab.PROFILE -> CommunityProfilePlaceholder()
                CommunityTab.CREATE -> CreatePostScreen(
                    onClose = { onTabSelected(CommunityTab.HOME) },
                    onSelectCommunity = { onSelectCommunity() },
                )
                CommunityTab.NOTIFICATIONS -> NotificationsPlaceholder()
            }
        }

        CommunityBottomNav(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                if (tab == CommunityTab.HOME && selectedTab == CommunityTab.HOME) {
                    onScrollToTopAndRefresh()
                } else {
                    onTabSelected(tab)
                }
            },
        )
    }
}

@Composable
private fun CommunityTopBar(
    tab: CommunityTab,
    onMenuClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (tab == CommunityTab.HOME) {
            Icon(
                imageVector = CommunityIcons.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
                    .clickable(onClick = onMenuClick),
            )
        } else {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
@Composable
private fun CommunityBottomNav(
    selectedTab: CommunityTab,
    onTabSelected: (CommunityTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityTab.entries.forEach { tab ->
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
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.size(28.dp),
        )
    }
}