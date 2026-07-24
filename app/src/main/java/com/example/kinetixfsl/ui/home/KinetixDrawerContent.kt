package com.example.kinetixfsl.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
<<<<<<< HEAD
=======
import androidx.compose.foundation.layout.Box
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
<<<<<<< HEAD
=======
import androidx.compose.ui.graphics.Color
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
<<<<<<< HEAD
=======
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixWhite
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f

/**
 * The side drawer content. All items are visual only — they take an [on Click]
 * so the parent can wire real destinations later, but for now the parent just
 * closes the drawer on any tap.
 *
 * Sections mirror the design: Dashboard is alone up top, then Community-related
 * items, then a lone About at the bottom.
<<<<<<< HEAD
 *
 * Every color here comes from MaterialTheme.colorScheme, not a fixed brand
 * constant — that's what lets this drawer follow the system's dark/light setting.
=======
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
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
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
<<<<<<< HEAD
            .background(MaterialTheme.colorScheme.surface)
=======
            .background(KinetixWhite)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            .padding(vertical = 16.dp),
    ) {

        Spacer(Modifier.height(24.dp))

        DrawerItem(HomeIcons.GridDashboard, "Dashboard", onDashboardClick)
        DrawerItem(HomeIcons.HandToText, "Gesture to Text", onGestureToTextClick)
        DrawerItem(HomeIcons.TextToHand, "Text to Gesture", onTextToGestureClick)

        Spacer(Modifier.height(12.dp))
<<<<<<< HEAD
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
=======
        HorizontalDivider(color = KinetixOutline, modifier = Modifier.padding(horizontal = 20.dp))
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        Spacer(Modifier.height(12.dp))

        DrawerItem(HomeIcons.Community, "Community", onCommunityClick)
        DrawerItem(HomeIcons.Plus, "Start a community", onStartCommunityClick)
        DrawerItem(HomeIcons.Search, "Discover communities", onDiscoverCommunitiesClick)

        Spacer(Modifier.height(12.dp))
<<<<<<< HEAD
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
=======
        HorizontalDivider(color = KinetixOutline, modifier = Modifier.padding(horizontal = 20.dp))
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        Spacer(Modifier.height(12.dp))

        DrawerItem(icon = null, label = "About", onClick = onAboutClick)
    }
}

/** A single tappable row. Passing null [icon] indents the label — used for About. */
@Composable
private fun DrawerItem(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
<<<<<<< HEAD
                tint = MaterialTheme.colorScheme.onSurface,
=======
                tint = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
<<<<<<< HEAD
            color = MaterialTheme.colorScheme.onSurface,
=======
            color = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        )
    }
}

<<<<<<< HEAD
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
=======
@Preview(showBackground = true, backgroundColor = 0xFFEEEEEE)
@Composable
private fun KinetixDrawerContentPreview() {
    KinetixFSLTheme {
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
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