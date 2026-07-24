package com.example.kinetixfsl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.community.CommunityScreen
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

/** Every destination in the app. Add to this as we build each screen. */
object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"

    // Check-email carries the address it was sent to as a URL-encoded path arg.
    private const val CHECK_EMAIL_BASE = "check_email"
    const val CHECK_EMAIL_ARG = "email"
    const val CHECK_EMAIL_PATTERN = "$CHECK_EMAIL_BASE/{$CHECK_EMAIL_ARG}"
    fun checkEmail(email: String): String =
        "$CHECK_EMAIL_BASE/${URLEncoder.encode(email, StandardCharsets.UTF_8.name())}"

    const val HOME = "home"
    const val COMMUNITY = "community"
}

@Composable
fun KinetixNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val authRepository = remember { AuthRepository() }

    NavHost(
        navController = navController,
        startDestination = Route.SPLASH,
    ) {
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

        composable(Route.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.LOGIN) { inclusive = true }
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

        composable(Route.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                },
            )
        }

        composable(Route.FORGOT_PASSWORD) {
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

        composable(
            route = Route.CHECK_EMAIL_PATTERN,
            arguments = listOf(navArgument(Route.CHECK_EMAIL_ARG) { type = NavType.StringType }),
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

        composable(Route.HOME) {
            HomeScreen(
                onSignOut = {
                    authRepository.signOut()
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.HOME) { inclusive = true }
                    }
                },
                onNavigateToCommunity = {
                    // Home -> Community. No popUpTo -- back returns to Home.
                    navController.navigate(Route.COMMUNITY)
                },
            )
        }

        composable(Route.COMMUNITY) {
            CommunityScreen(
                onNavigateToDashboard = {
                    // Drawer "Dashboard" from Community: go home. If Home is still
                    // on the stack we just pop back; if not, we navigate fresh.
                    val popped = navController.popBackStack(Route.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Route.HOME) {
                            popUpTo(Route.COMMUNITY) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}