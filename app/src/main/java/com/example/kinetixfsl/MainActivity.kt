package com.example.kinetixfsl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.community.ShareLinks
import com.example.kinetixfsl.navigation.KinetixNavHost
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The post a shared link pointed at, or null on a normal launch. Held as
     * Compose state so a link arriving while the app is already open (see
     * [onNewIntent]) still routes the user to that post.
     */
    private var deepLinkPostId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(). This is the platform splash — it only
        // covers the gap until our first Compose frame, then hands off to the
        // Compose SplashScreen, which is the one the user actually reads.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        deepLinkPostId = ShareLinks.postIdFrom(intent?.data)

        setContent {
            KinetixFSLTheme {
                KinetixNavHost(deepLinkPostId = deepLinkPostId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Stamp presence so other people's profile views show a current
        // "Active now / 5min / 3hr / 2d" state. Best-effort and fire-and-forget.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                CommunityRepository().run {
                    ensureUserProfile()
                    touchLastActive()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareLinks.postIdFrom(intent.data)?.let { deepLinkPostId = it }
    }
}
