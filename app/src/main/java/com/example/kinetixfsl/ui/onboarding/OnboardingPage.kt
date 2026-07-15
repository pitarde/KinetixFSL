package com.example.kinetixfsl.ui.onboarding

import androidx.annotation.DrawableRes
import com.example.kinetixfsl.R

/**
 * One slide of the onboarding carousel.
 *
 * @param image  the illustration at the top of the slide (a drawable, e.g. a PNG)
 * @param title  the large two-line heading
 * @param body   the supporting sentence under the title
 */
data class OnboardingPage(
    @param:DrawableRes val image: Int,
    val title: String,
    val body: String,
)

/**
 * The carousel content. This is the ONLY place to edit to change what the
 * onboarding says or shows — add, remove, or reorder entries and the dots,
 * swiping, and the final "Get Started" button all adjust automatically.
 *
 * Slides 2 and 3 currently reuse the slide-1 art as a placeholder. Drop in
 * `onboarding_2` / `onboarding_3` drawables and swap the `image =` lines when
 * your other two illustrations are ready.
 */
val onboardingPages = listOf(
    OnboardingPage(
        image = R.drawable.onboarding_1,
        title = "Start Your\nFSL Journey",
        body = "Explore lessons, practice signs and connect to the community.",
    ),
    OnboardingPage(
        image = R.drawable.onboarding_1, // TODO: replace with onboarding_2
        title = "Practice with\nYour Camera",
        body = "Sign in front of your camera and get instant feedback, fully offline.",
    ),
    OnboardingPage(
        image = R.drawable.onboarding_1, // TODO: replace with onboarding_3
        title = "Track Your\nProgress",
        body = "Follow your growth with lessons, quizzes and streaks that sync online.",
    ),
)