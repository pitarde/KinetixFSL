package com.example.kinetixfsl.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.community.CommunityScreen
import com.example.kinetixfsl.community.SharedPostScreen
import com.example.kinetixfsl.modules.LearningRoomScreen
import com.example.kinetixfsl.modules.SignListScreen
import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.ui.forgotpassword.CheckEmailScreen
import com.example.kinetixfsl.ui.forgotpassword.ForgotPasswordScreen
import com.example.kinetixfsl.ui.home.HomeScreen
import com.example.kinetixfsl.ui.login.LoginScreen
import com.example.kinetixfsl.ui.onboarding.OnboardingScreen
import com.example.kinetixfsl.ui.register.RegisterScreen
import com.example.kinetixfsl.ui.splash.SplashScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing

import com.example.kinetixfsl.detection.CameraPracticeScreen

/** Every destination in the app. Add to this as we build each screen. */
object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"

    private const val CHECK_EMAIL_BASE = "check_email"
    const val CHECK_EMAIL_ARG = "email"
    const val CHECK_EMAIL_PATTERN = "$CHECK_EMAIL_BASE/{$CHECK_EMAIL_ARG}"
    fun checkEmail(email: String): String =
        "$CHECK_EMAIL_BASE/${URLEncoder.encode(email, StandardCharsets.UTF_8.name())}"

    const val HOME = "home"
    const val COMMUNITY = "community"

    // ── Shared post (opened from a link) ────────────────────
    private const val POST_BASE = "post"
    const val POST_ARG = "postId"
    const val POST_PATTERN = "$POST_BASE/{$POST_ARG}"
    fun post(postId: String): String = "$POST_BASE/$postId"

    // ── Modules routes ──────────────────────────────────────
    private const val SIGN_LIST_BASE = "sign_list"
    const val SIGN_LIST_ARG = "categoryId"
    const val SIGN_LIST_PATTERN = "$SIGN_LIST_BASE/{$SIGN_LIST_ARG}"
    fun signList(categoryId: String): String = "$SIGN_LIST_BASE/$categoryId"

    private const val LEARNING_ROOM_BASE = "learning_room"
    const val LEARNING_ROOM_SIGN_ARG = "signIndex"
    const val LEARNING_ROOM_PATTERN =
        "$LEARNING_ROOM_BASE/{$SIGN_LIST_ARG}/{$LEARNING_ROOM_SIGN_ARG}"
    fun learningRoom(categoryId: String, signIndex: Int): String =
        "$LEARNING_ROOM_BASE/$categoryId/$signIndex"

    // ── Practice route ──────────────────────────────────────
    private const val PRACTICE_BASE = "practice"
    const val PRACTICE_SIGN_ARG = "signIndex"
    const val PRACTICE_PATTERN =
        "$PRACTICE_BASE/{$SIGN_LIST_ARG}/{$PRACTICE_SIGN_ARG}"
    fun practice(categoryId: String, signIndex: Int): String =
        "$PRACTICE_BASE/$categoryId/$signIndex"
}

/** Animation duration in ms — kept consistent across all auth transitions. */
private const val ANIM_DURATION = 450

/** Shorter fade for the modules flow — feels snappier. */
private const val MODULES_FADE = 100
private const val MODULES_OUT_FADE = 250

@Composable
fun KinetixNavHost(
    navController: NavHostController = rememberNavController(),
    /**
     * Post id from a shared link the app was opened with, or null for a normal
     * launch. Changes when a new link arrives while the app is already running.
     */
    deepLinkPostId: String? = null,
) {
    val authRepository = remember { AuthRepository() }

    /**
     * A link that arrived before the user was signed in. Held here and opened
     * as soon as login or registration succeeds.
     */
    var pendingPostId by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * Drops the user on the linked post with the community feed underneath, so
     * back (or the X) lands on the feed instead of closing the app.
     */
    fun openSharedPost(postId: String) {
        navController.navigate(Route.HOME) {
            popUpTo(Route.SPLASH) { inclusive = true }
        }
        navController.navigate(Route.COMMUNITY)
        navController.navigate(Route.post(postId))
    }

    LaunchedEffect(deepLinkPostId) {
        val id = deepLinkPostId ?: return@LaunchedEffect
        if (authRepository.isSignedIn) {
            openSharedPost(id)
        } else {
            // Sit on it until the user gets through login.
            pendingPostId = id
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.SPLASH,
    ) {
        // ---- Splash: no animation (it fades on its own) ----
        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    val destination = if (authRepository.isSignedIn) {
                        Route.HOME
                    } else {
                        Route.ONBOARDING
                    }
                    navController.navigate(destination) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // ---- Onboarding: fade in/out ----
        composable(
            route = Route.ONBOARDING,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ---- Login: slides in from left, slides out to left ----
        composable(
            route = Route.LOGIN,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
        ) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                    // If they got here by opening a shared link, take them
                    // to that post now that they're signed in.
                    pendingPostId?.let { postId ->
                        pendingPostId = null
                        navController.navigate(Route.COMMUNITY)
                        navController.navigate(Route.post(postId))
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Route.REGISTER)
                },
                onForgotPassword = {
                    navController.navigate(Route.FORGOT_PASSWORD)
                },
            )
        }

        // ---- Register: slides in from right (going forward from Login) ----
        composable(
            route = Route.REGISTER,
            enterTransition = {
                slideInHorizontally(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) { it }
            },
            exitTransition = {
                slideOutHorizontally(tween(ANIM_DURATION, easing = FastOutSlowInEasing)) { -it }
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
        ) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                    pendingPostId?.let { postId ->
                        pendingPostId = null
                        navController.navigate(Route.COMMUNITY)
                        navController.navigate(Route.post(postId))
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                },
            )
        }

        // ---- Forgot Password: slides in from right ----
        composable(
            route = Route.FORGOT_PASSWORD,
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
        ) {
            ForgotPasswordScreen(
                onLinkSent = { email ->
                    navController.navigate(Route.checkEmail(email)) {
                        popUpTo(Route.FORGOT_PASSWORD) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // ---- Check Email: slides in from right ----
        composable(
            route = Route.CHECK_EMAIL_PATTERN,
            arguments = listOf(navArgument(Route.CHECK_EMAIL_ARG) { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(ANIM_DURATION))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(ANIM_DURATION))
            },
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString(Route.CHECK_EMAIL_ARG).orEmpty()
            val email = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())

            CheckEmailScreen(
                email = email,
                onBackToLogin = {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        // ---- Home: fade in (coming from login success) ----
        composable(
            route = Route.HOME,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { fadeIn(tween(ANIM_DURATION)) },
        ) {
            HomeScreen(
                onSignOut = {
                    authRepository.signOut()
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.HOME) { inclusive = true }
                    }
                },
                onNavigateToCommunity = {
                    navController.navigate(Route.COMMUNITY)
                },
                onNavigateToSignList = { categoryId ->
                    navController.navigate(Route.signList(categoryId))
                },
            )
        }

        // ---- Community: quick fade (drawer provides the visual motion) ----
        composable(
            route = Route.COMMUNITY,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            CommunityScreen(
                onNavigateToDashboard = {
                    val popped = navController.popBackStack(Route.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Route.HOME) {
                            popUpTo(Route.COMMUNITY) { inclusive = true }
                        }
                    }
                },
            )
        }

        // ---- Shared post: opened from a link, backed by the community feed ----
        composable(
            route = Route.POST_PATTERN,
            arguments = listOf(navArgument(Route.POST_ARG) { type = NavType.StringType }),
            // No `deepLinks` here on purpose: NavController would auto-handle
            // the launch intent and drop this destination straight on top of
            // splash, so back would exit the app. openSharedPost() below builds
            // the stack we actually want instead.
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString(Route.POST_ARG).orEmpty()

            SharedPostScreen(
                postId = postId,
                onClose = {
                    // Back and the X both land on the community feed, which is
                    // already sitting underneath this destination.
                    val popped = navController.popBackStack(Route.COMMUNITY, inclusive = false)
                    if (!popped) {
                        navController.navigate(Route.COMMUNITY) {
                            popUpTo(Route.POST_PATTERN) { inclusive = true }
                        }
                    }
                },
            )
        }

        // ---- Sign List: fade in/out ----
        composable(
            route = Route.SIGN_LIST_PATTERN,
            arguments = listOf(
                navArgument(Route.SIGN_LIST_ARG) { type = NavType.StringType },
            ),
            enterTransition = { fadeIn(tween(MODULES_FADE)) },
            exitTransition = { fadeOut(tween(MODULES_OUT_FADE)) },
            popEnterTransition = { fadeIn(tween(MODULES_FADE)) },
            popExitTransition = { fadeOut(tween(MODULES_OUT_FADE)) },
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString(Route.SIGN_LIST_ARG).orEmpty()
            val category = remember(categoryId) { FslSignData.findCategory(categoryId) }

            if (category != null) {
                SignListScreen(
                    category = category,
                    onBack = { navController.popBackStack() },
                    onSignClick = { signIndex: Int ->
                        navController.navigate(Route.learningRoom(categoryId, signIndex))
                    },
                )
            }
        }

        // ---- Learning Room: fade in/out ----
        composable(
            route = Route.LEARNING_ROOM_PATTERN,
            arguments = listOf(
                navArgument(Route.SIGN_LIST_ARG) { type = NavType.StringType },
                navArgument(Route.LEARNING_ROOM_SIGN_ARG) { type = NavType.IntType },
            ),
            enterTransition = { fadeIn(tween(MODULES_FADE)) },
            exitTransition = { fadeOut(tween(MODULES_OUT_FADE)) },
            popEnterTransition = { fadeIn(tween(MODULES_FADE)) },
            popExitTransition = { fadeOut(tween(MODULES_OUT_FADE)) },
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString(Route.SIGN_LIST_ARG).orEmpty()
            val signIndex = backStackEntry.arguments?.getInt(Route.LEARNING_ROOM_SIGN_ARG) ?: 0
            val category = remember(categoryId) { FslSignData.findCategory(categoryId) }

            if (category != null) {
                val isLastSign = signIndex >= category.signCount - 1

                LearningRoomScreen(
                    category = category,
                    signIndex = signIndex,
                    onBack = { navController.popBackStack() },
                    onNext = if (isLastSign) {
                        null
                    } else {
                        {
                            // Fade to next sign (replaces current)
                            navController.navigate(
                                Route.learningRoom(categoryId, signIndex + 1)
                            ) {
                                popUpTo(
                                    Route.learningRoom(categoryId, signIndex)
                                ) { inclusive = true }
                            }
                        }
                    },
                    onPractice = {
                        navController.navigate(Route.practice(categoryId, signIndex))
                    },
                )
            }


        }

        // ---- Camera Practice: real-time sign detection ----
        composable(
            route = Route.PRACTICE_PATTERN,
            arguments = listOf(
                navArgument(Route.SIGN_LIST_ARG) { type = NavType.StringType },
                navArgument(Route.PRACTICE_SIGN_ARG) { type = NavType.IntType },
            ),
            enterTransition = { fadeIn(tween(MODULES_FADE)) },
            exitTransition = { fadeOut(tween(MODULES_FADE)) },
            popEnterTransition = { fadeIn(tween(MODULES_FADE)) },
            popExitTransition = { fadeOut(tween(MODULES_FADE)) },
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString(Route.SIGN_LIST_ARG).orEmpty()
            val signIndex = backStackEntry.arguments?.getInt(Route.PRACTICE_SIGN_ARG) ?: 0
            val category = remember(categoryId) { FslSignData.findCategory(categoryId) }

            if (category != null) {
                val sign = category.signs.getOrNull(signIndex)
                val displayPrefix = when (categoryId) {
                    "alphabet" -> "Letter"
                    "numbers" -> "Number"
                    else -> ""
                }
                val isLastSign = signIndex >= category.signCount - 1

                if (sign != null) {
                    CameraPracticeScreen(
                        targetLabel = sign.name,
                        displayName = "$displayPrefix ${sign.name}".trim(),
                        isDynamic = sign.isDynamic,
                        categoryId = categoryId,
                        onBack = { navController.popBackStack() },
                        onProceed = if (isLastSign) null else {
                            {
                                // Go to the Learning Room for the next sign
                                navController.navigate(
                                    Route.learningRoom(categoryId, signIndex + 1)
                                ) {
                                    // Pop back to the sign list so the back stack is clean
                                    popUpTo(Route.signList(categoryId))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}