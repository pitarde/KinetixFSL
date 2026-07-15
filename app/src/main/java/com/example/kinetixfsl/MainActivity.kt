package com.example.kinetixfsl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kinetixfsl.navigation.KinetixNavHost
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(). This is the platform splash — it only
        // covers the gap until our first Compose frame, then hands off to the
        // Compose SplashScreen, which is the one the user actually reads.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KinetixFSLTheme {
                KinetixNavHost()
            }
        }
    }
}