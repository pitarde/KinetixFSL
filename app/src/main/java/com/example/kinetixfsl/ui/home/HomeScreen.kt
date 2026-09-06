package com.example.kinetixfsl.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.Avatar
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.game.ui.QuizGameRoot
import com.example.kinetixfsl.modules.ModulesScreen
import com.example.kinetixfsl.profile.ProfileScreen
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixWhite
import com.example.kinetixfsl.ui.theme.StatusBarLightIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onNavigateToCommunity: () -> Unit,
    /** Opens the Text-to-Sign search — the drawer's "Text to Gesture" item. */
    onNavigateToTextToSign: () -> Unit = {},
    onNavigateToSignList: (categoryId: String) -> Unit,
    onStartCommunity: () -> Unit = {},
    onDiscoverCommunities: () -> Unit = {},
    /** Opens a specific community — the drawer's Recently Visited rows. */
    onOpenCommunity: (String) -> Unit = {},
    /** Opens the Inbox (chat + notifications) — the drawer's own entry point. */
    onOpenInbox: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // The (offline) Profile screen's own avatar — a device-only file set via
    // Edit Profile in Settings (see LocalProfileStore), independent of the
    // Firebase account. The Dashboard top bar's profile shortcut mirrors this
    // exact picture, updated the instant Edit Profile saves a new one via
    // onAvatarChanged below — no separate source of truth to fall out of sync.
    var localAvatarFile by remember {
        mutableStateOf(com.example.kinetixfsl.profile.LocalProfileStore.avatarFile(context))
    }

    // Hoisted purely for the drawer's unread badge — the Inbox screen itself
    // gets its own instance when it's actually opened (a separate NavHost
    // destination, not an overlay over this one the way Community's is).
    val inboxViewModel = remember { com.example.kinetixfsl.community.inbox.InboxViewModel() }
    val inboxState by inboxViewModel.uiState.collectAsStateWithLifecycle()

    // rememberSaveable keeps the selected tab alive across navigation
    // (navigate to SignList → press back → still on Modules tab, not Home).
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.HOME) }

    // Back from any non-Home tab (Modules, Camera, Game, Profile) returns to
    // the Dashboard instead of exiting the app. If the drawer is open, back
    // closes it first. On the Home tab with the drawer closed, this handler is
    // disabled so the system default (exit) applies.
    BackHandler(enabled = drawerState.isOpen || selectedTab != HomeTab.HOME) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            selectedTab = HomeTab.HOME
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Icon-only to *open*; enabled once actually open so swiping it back
        // closed still works — see CommunityScreen's own copy of this.
        gesturesEnabled = drawerState.isOpen,
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
                        scope.launch {
                            drawerState.close()
                            onNavigateToTextToSign()
                        }
                    },
                    onCommunityClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToCommunity()
                        }
                    },
                    onStartCommunityClick = {
                        scope.launch {
                            drawerState.close()
                            onStartCommunity()
                        }
                    },
                    onDiscoverCommunitiesClick = {
                        scope.launch {
                            drawerState.close()
                            onDiscoverCommunities()
                        }
                    },
                    onAboutClick = {
                        scope.launch { drawerState.close() }
                    },
                    onRecentCommunityClick = { communityId ->
                        scope.launch {
                            drawerState.close()
                            onOpenCommunity(communityId)
                        }
                    },
                    onInboxClick = {
                        scope.launch {
                            drawerState.close()
                            onOpenInbox()
                        }
                    },
                    inboxUnreadCount = inboxState.totalUnread,
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
            localAvatarFile = localAvatarFile,
            onAvatarChanged = { localAvatarFile = it },
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
    localAvatarFile: java.io.File?,
    onAvatarChanged: (java.io.File?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Quiz Game runs an active level (tutorial/quiz/result) full-screen, with
    // its own X and progress bar — so while it's immersive we hide the app's top
    // bar and bottom nav. The flag is only ever consulted on the Game tab.
    var gameImmersive by rememberSaveable { mutableStateOf(false) }
    val hideChrome = selectedTab == HomeTab.GAME && gameImmersive
    // Profile reads as its own pushed screen — reached only via the icon on
    // Home's bar, not a bottom-nav destination — so the bottom nav hides
    // while it's showing, the same as it would for a real "new screen".
    val hideBottomNav = hideChrome || selectedTab == HomeTab.PROFILE
    // The settings gear now lives in Profile's own top bar rather than on the
    // screen itself — see HomeTopBar — so its open/closed state lives here,
    // a level above ProfileScreen, and is just passed down to it.
    var profileSettingsOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // HomeTopBar paints its own dark bar behind the status bar and
            // insets itself when it's shown — this only needs to reserve that
            // inset itself when the bar is hidden (immersive gameplay).
            .then(if (hideChrome) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (!hideChrome) {
            HomeTopBar(
                tab = selectedTab,
                onMenuClick = onMenuClick,
                onProfileClick = { onTabSelected(HomeTab.PROFILE) },
                onBackToDashboard = { onTabSelected(HomeTab.HOME) },
                onSettingsClick = { profileSettingsOpen = true },
                localAvatarFile = localAvatarFile,
            )
        }

        // The tab content fills everything below the top bar, and the bottom
        // nav pill floats as an overlay on top of it (aligned to the bottom)
        // rather than taking its own row of space — so the content runs the
        // full height behind the pill, and the pill reads as popped up in
        // front of it, not as a bar the page is squeezed above.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Home/Modules/Game switch instantly between each other (unchanged);
            // opening or leaving Profile specifically gets a slide+fade, so it
            // reads as a screen being pushed on and popped off, matching the
            // bottom nav hiding while it's up.
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    when (HomeTab.PROFILE) {
                        targetState -> {
                            // Opening Profile — slide in from the right + fade,
                            // matching the "pushed screen" treatment its bottom-
                            // nav-hiding already implies.
                            (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeIn(tween(280))) togetherWith
                                fadeOut(tween(150))
                        }
                        initialState -> {
                            // Leaving Profile — the reverse: slide out to the
                            // right + fade while the destination tab fades in.
                            fadeIn(tween(200)) togetherWith
                                (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 3 } +
                                    fadeOut(tween(280)))
                        }
                        else -> {
                            // Home/Modules/Game switching among themselves —
                            // unchanged, an instant swap.
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        }
                    }
                },
                label = "homeTabContent",
            ) { tab ->
                when (tab) {
                    HomeTab.HOME -> DashboardContent(onOpenModule = onNavigateToSignList)
                    HomeTab.MODULES -> ModulesScreen(
                        onCategoryClick = { category ->
                            onNavigateToSignList(category.id)
                        },
                    )
                    HomeTab.GAME -> QuizGameRoot(
                        onImmersiveChange = { gameImmersive = it },
                    )
                    HomeTab.PROFILE -> ProfileScreen(
                        onSignOut = onSignOut,
                        showSettings = profileSettingsOpen,
                        onDismissSettings = { profileSettingsOpen = false },
                        onAvatarChanged = onAvatarChanged,
                    )
                }
            }

            if (!hideBottomNav) {
                HomeBottomNav(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * The top bar. Home, Modules, and Game always get the same dark bar
 * treatment as the Community Home Feed's own top bar — KinetixNavy in light
 * mode, colorScheme.surface in dark, white (light) status bar icons. Profile
 * (only reached via the icon on Home's bar, not a bottom-nav tab of its own)
 * gets that same colored bar in light mode too — see the light-mode-only note
 * on its redesign — but keeps its old plain, uncolored bar in dark mode... with
 * one exception: in dark mode Profile's bar (like every other tab's) uses
 * colorScheme.surface, the same color its cards use — not
 * colorScheme.background, the plain page color — so it reads as a raised bar
 * rather than flush with the page.
 *
 * Home shows the hamburger on the left, "Dashboard" as its title (same size
 * as the Community Home Feed's "KinetixFSL Community"), and the Profile
 * shortcut on the right — the icon that used to be its own bottom-nav tab.
 * Modules and Game both get the hamburger too, opening the same drawer.
 * Profile gets a back arrow instead, returning to the Dashboard — it reads as
 * a pushed screen, not a peer destination, in both themes.
 */
@Composable
private fun HomeTopBar(
    tab: HomeTab,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onBackToDashboard: () -> Unit,
    onSettingsClick: () -> Unit,
    localAvatarFile: java.io.File? = null,
) {
    val dark = isSystemInDarkTheme()
    val coloredBar = tab != HomeTab.PROFILE || !dark

    StatusBarLightIcons(light = if (coloredBar) true else dark)

    // Dark mode always resolves to the card surface color regardless of tab —
    // Profile no longer needs its own "plain background" case there, since
    // that's exactly what colorScheme.surface already gives every other tab.
    val barBackground = when {
        dark -> MaterialTheme.colorScheme.surface
        coloredBar -> KinetixNavy
        else -> MaterialTheme.colorScheme.background
    }
    val barContentColor = when {
        dark -> MaterialTheme.colorScheme.onSurface
        coloredBar -> KinetixWhite
        else -> MaterialTheme.colorScheme.onBackground
    }

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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (tab) {
                HomeTab.HOME, HomeTab.MODULES, HomeTab.GAME -> {
                    Icon(
                        imageVector = HomeIcons.Menu,
                        contentDescription = "Open menu",
                        tint = barContentColor,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onMenuClick),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                HomeTab.PROFILE -> {
                    Icon(
                        imageVector = CommunityIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = barContentColor,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(onClick = onBackToDashboard),
                    )
                    Spacer(Modifier.width(12.dp))
                }
            }
            Text(
                text = if (tab == HomeTab.HOME) "Dashboard" else tab.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = barContentColor,
                modifier = Modifier.weight(1f),
            )
            if (tab == HomeTab.HOME) {
                // The same photo the (offline) Profile screen shows — set via
                // Edit Profile in Settings, see LocalProfileStore — falling
                // back to the plain icon until one is ever set.
                if (localAvatarFile == null) {
                    Icon(
                        imageVector = HomeIcons.Profile,
                        contentDescription = "Profile",
                        tint = barContentColor,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(onClick = onProfileClick),
                    )
                } else {
                    coil.compose.AsyncImage(
                        model = localAvatarFile,
                        contentDescription = "Profile",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onProfileClick),
                    )
                }
            }
            if (tab == HomeTab.PROFILE) {
                // Moved here from a gear icon on the screen itself — see
                // ProfileHeader.
                Icon(
                    imageVector = com.example.kinetixfsl.profile.ProfileIcons.Settings,
                    contentDescription = "Settings",
                    tint = barContentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onSettingsClick),
                )
            }
        }
    }
}

/**
 * The bottom nav — Home, Modules, Game — as a floating rounded pill rather
 * than a full-width bar. It's positioned as an overlay on top of the tab
 * content (see [HomeScaffold]) rather than in its own row of layout space, so
 * the page runs the full height behind it and the pill reads as popped up in
 * front of the screen — a strong shadow reinforces the separation. A tab's
 * icon simply switches to the primary color when selected — no filled capsule
 * behind it. Camera was removed entirely, and Profile lives on the Dashboard's
 * own top bar instead — see [HomeTab.BOTTOM_NAV_TABS].
 */
@Composable
private fun HomeBottomNav(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            // colorScheme.surface *is* pure white in light mode (see Theme.kt)
            // — the same color every card (Achievements, post cards) renders
            // on, so this reads as pure white too. Dark mode gets its own
            // theme surface. No tonalElevation: Surface tints the color by a
            // primary-tinted overlay proportional to it, which would leave
            // this a faint off-white instead of the flat white every card uses.
            color = MaterialTheme.colorScheme.surface,
            // A pronounced shadow so the pill visibly lifts off the page
            // behind it, matching the "popped up in front" look.
            shadowElevation = 16.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTab.BOTTOM_NAV_TABS.forEach { tab ->
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
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        // A short underline marks which tab is active — invisible (but still
        // reserving its space, so the icon above doesn't shift) on the rest.
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
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