package com.example.kinetixfsl.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pages: List<OnboardingPage> = onboardingPages,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    // This screen never overrides the status bar itself, so without this it
    // just shows whatever the app-wide default was at launch and never
    // updates if the system's light/dark setting is toggled while the app is
    // open. Reading isSystemInDarkTheme() here directly makes it reactive,
    // the same way every other screen with its own override already is.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(
        light = androidx.compose.foundation.isSystemInDarkTheme(),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // App is edge-to-edge (enableEdgeToEdge), so reserve the status-bar
            // and navigation-bar areas here — otherwise "Skip" sits under the
            // status bar and the "Next" button under the on-screen nav buttons
            // when the phone uses gesture / 3-button navigation.
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: Skip, hidden on the last page so it doesn't compete with the CTA.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!isLastPage) {
                TextButton(onClick = onFinished) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Keep the row's height so the pager below doesn't jump on the last page.
                Spacer(Modifier.height(48.dp))
            }
        }

        // The swipeable content. weight(1f) lets it take all space above the footer.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { pageIndex ->
            OnboardingPageContent(page = pages[pageIndex])
        }

        // Dots
        PagerDots(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.padding(vertical = 24.dp),
        )

        // Footer button. "Next" advances the pager; the final page commits.
        KinetixPrimaryButton(
            text = if (isLastPage) "Get Started" else "Next",
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(page.image),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    KinetixFSLTheme {
        OnboardingScreen(onFinished = {})
    }
}