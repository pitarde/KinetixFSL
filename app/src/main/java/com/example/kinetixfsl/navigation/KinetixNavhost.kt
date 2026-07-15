package com.example.kinetixfsl.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.ui.login.LoginScreen
import com.example.kinetixfsl.ui.onboarding.OnboardingScreen
import com.example.kinetixfsl.ui.register.RegisterScreen
import com.example.kinetixfsl.ui.splash.SplashScreen

/** Every destination in the app. Add to this as we build each screen. */
object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

@Composable
fun KinetixNavHost(
    navController: NavHostController = rememberNavController(),
) {
    // One auth handle for routing decisions. Firebase persists the session across
    // app restarts, so this is already correct the moment the app launches.
    val authRepository = remember { AuthRepository() }

    NavHost(
        navController = navController,
        startDestination = Route.SPLASH,
    ) {
        composable(Route.SPLASH) {
            SplashScreen(
                onFinished = {
                    // Already signed in -> Home; otherwise -> onboarding/login.
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
                    // Login -> Register. No popUpTo, so back returns to Log in.
                    navController.navigate(Route.REGISTER)
                },
                onForgotPassword = {
                    // TODO: build password reset, then navigate here.
                },
            )
        }

        composable(Route.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    // Account created & signed in -> Home, clearing the auth stack.
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    // "Already have an account? Login" -> just go back to Log in.
                    navController.popBackStack()
                },
            )
        }

        composable(Route.HOME) {
            // Placeholder until we build the real home screen.
            // Temporary Sign Out so you can test the logged-in vs logged-out flow.
            HomePlaceholder(
                onSignOut = {
                    authRepository.signOut()
                    navController.navigate(Route.LOGIN) {
                        popUpTo(Route.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomePlaceholder(
    onSignOut: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSignOut) {
                Text("Sign out", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}