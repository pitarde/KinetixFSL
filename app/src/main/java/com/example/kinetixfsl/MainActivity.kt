package com.example.kinetixfsl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.community.ShareLinks
import com.example.kinetixfsl.community.inbox.AccountNotifier
import com.example.kinetixfsl.community.inbox.PresenceRepository
import com.example.kinetixfsl.community.inbox.push.FcmTokenStore
import com.example.kinetixfsl.navigation.KinetixNavHost
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The post a shared link pointed at, or null on a normal launch. Held as
     * Compose state so a link arriving while the app is already open (see
     * [onNewIntent]) still routes the user to that post.
     */
    private var deepLinkPostId by mutableStateOf<String?>(null)

    /** The community a shared link pointed at, or null on a normal launch. */
    private var deepLinkCommunityId by mutableStateOf<String?>(null)

    /** The profile a shared link pointed at, or null on a normal launch. */
    private var deepLinkUserId by mutableStateOf<String?>(null)

    /** Whose account checks have already run this session. See [watchForSignIn]. */
    private var lastCheckedUid: String? = null

    /**
     * One server-side account-status check per uid per process. A non-null
     * result means an admin deleted / disabled / penalised this account while
     * the app was closed — [com.example.kinetixfsl.auth.AuthRepository
     * .enforceAccountStatusNow] has already torn the session down, so callers
     * must NOT recreate the profile, re-register the push token or re-sync.
     * Without this gate, `ensureUserProfile()` on the next launch quietly
     * rebuilt the `users/{uid}` document the wipe had just removed.
     */
    private val accountStatusChecks =
        java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String?>>()

    private fun accountStatusMessage(uid: String): kotlinx.coroutines.Deferred<String?> =
        accountStatusChecks.getOrPut(uid) {
            CoroutineScope(Dispatchers.IO).async {
                val msg = runCatching {
                    com.example.kinetixfsl.auth.AuthRepository().enforceAccountStatusNow()
                }.getOrNull()
                if (msg != null) com.example.kinetixfsl.auth.AccountStatusWatcher.raise(msg)
                msg
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(). This is the platform splash — it only
        // covers the gap until our first Compose frame, then hands off to the
        // Compose SplashScreen, which is the one the user actually reads.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load the saved light/dark choice before the first frame.
        com.example.kinetixfsl.ui.theme.ThemePreference.init(this)

        deepLinkPostId = ShareLinks.postIdFrom(intent?.data)
        deepLinkCommunityId = ShareLinks.communityIdFrom(intent?.data)
        deepLinkUserId = ShareLinks.profileIdFrom(intent?.data)

        requestNotificationPermission()
        watchForSignIn()
        // Live enforcement of admin disable/penalty/delete: kicks the learner
        // out the moment their accountStatus changes — and, on a cold start,
        // the moment the app sees a delete that landed while it was closed.
        com.example.kinetixfsl.auth.AccountStatusWatcher.start(applicationContext)

        setContent {
            // Reading ThemePreference.mode here re-themes the whole app the
            // instant Settings changes it — System follows the phone, On/Off
            // force dark/light.
            val dark = when (com.example.kinetixfsl.ui.theme.ThemePreference.mode) {
                com.example.kinetixfsl.ui.theme.ThemePreference.Mode.SYSTEM ->
                    androidx.compose.foundation.isSystemInDarkTheme()
                com.example.kinetixfsl.ui.theme.ThemePreference.Mode.LIGHT -> false
                com.example.kinetixfsl.ui.theme.ThemePreference.Mode.DARK -> true
            }
            KinetixFSLTheme(darkTheme = dark) {
                // Make -night drawables/resources follow the chosen theme, not
                // just the Compose colours (fixes forced light/dark leaving the
                // onboarding illustration on the phone's actual mode).
                com.example.kinetixfsl.ui.theme.ForcedThemeResources(darkTheme = dark) {
                    KinetixNavHost(
                        deepLinkPostId = deepLinkPostId,
                        deepLinkCommunityId = deepLinkCommunityId,
                        deepLinkUserId = deepLinkUserId,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Live presence for the messenger's green dot. Separate from the
        // Firestore stamp below: this one is a Realtime Database write with a
        // disconnect handler armed behind it, which is the only way the app can
        // be marked offline when the phone simply goes away.
        PresenceRepository.goOnline()

        // Everything below needs a signed-in user, which is why it runs here
        // rather than in the sign-in screens: this is the one place every route
        // into the app passes through, including a restored session.
        //
        // Each step gets its OWN runCatching. They were chained inside a single
        // one, which meant the first failure silently skipped the rest — and
        // `FcmTokenStore.register()` throws routinely (no Play Services, no
        // network, FCM not yet enabled on the project), so in practice
        // `AccountNotifier.check` almost never ran. That's what stopped the
        // very first device from being recorded, which in turn made the second
        // device look like the first and never raise a "new device" notice.
        CoroutineScope(Dispatchers.IO).launch {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance()
                .currentUser?.uid ?: return@launch
            // Bail before any profile-recreating write if an admin deleted /
            // disabled this account while the app was closed — otherwise
            // ensureUserProfile() rebuilds the users/{uid} doc the wipe removed.
            if (accountStatusMessage(uid).await() != null) return@launch

            // Stamp presence so other people's profile views show a current
            // "Active now / 5min / 3hr / 2d" state.
            runCatching {
                CommunityRepository().run {
                    ensureUserProfile()
                    touchLastActive()
                }
            }
            runCatching { FcmTokenStore.register() }
            runCatching { AccountNotifier.check(applicationContext) }
        }
    }

    override fun onPause() {
        super.onPause()
        // Backgrounding the app takes the dot down immediately, rather than
        // waiting for the socket to time out.
        PresenceRepository.goOffline()
    }

    /**
     * Runs the account checks the moment somebody signs in, rather than only on
     * the next `onResume`.
     *
     * Registering an account happens *while this Activity is already resumed* —
     * the user is on the sign-up screen inside it. So `onResume` had long since
     * run by the time the account existed, and the welcome notice waited for
     * whatever caused the next one: backgrounding the app, a phone call, a
     * screen rotation. That's the "sometimes it works, mostly it's late"
     * behaviour. An auth listener fires on the sign-in itself, which is the
     * event actually being waited for.
     *
     * Cheap to leave running: it also covers signing out and back in as a
     * different account within one session, which `onResume` alone never saw.
     */
    private fun watchForSignIn() {
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            // Guarded so re-entering the foreground doesn't re-run the whole
            // check for an account already handled this session. The checks are
            // individually idempotent anyway; this just avoids the round trips.
            if (uid == lastCheckedUid) return@addAuthStateListener
            lastCheckedUid = uid

            CoroutineScope(Dispatchers.IO).launch {
                // An admin delete / disable / penalty that landed while the app
                // was closed: the session is torn down here and the profile must
                // NOT be recreated below.
                if (accountStatusMessage(uid).await() != null) return@launch

                runCatching { CommunityRepository().ensureUserProfile() }
                runCatching { FcmTokenStore.register() }
                runCatching { AccountNotifier.check(applicationContext) }
                // If an admin wiped this account's data, clear the on-device
                // copy first so it doesn't just re-sync back. Must run before
                // the cloud restore below.
                runCatching {
                    com.example.kinetixfsl.progress.ProgressSync
                        .applyRemoteWipeIfNeeded(applicationContext)
                }
                // On a fresh install / new device, pull this account's progress
                // back from the cloud. No-op if local progress already exists,
                // so it never clobbers on-device data.
                runCatching {
                    com.example.kinetixfsl.progress.ProgressSync
                        .restoreFromCloudIfLocalEmpty(applicationContext)
                }
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS on Android 13+, where the manifest entry
     * alone grants nothing and every push would be dropped without a word.
     *
     * Deliberately not gated behind an explainer screen: this app only posts
     * notifications the user caused (a message, a reply, an upload finishing),
     * so the system dialog's own wording is enough, and one extra screen
     * between launch and the app is worse than the dialog.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        // Result ignored on purpose — a refusal simply means no banners, and
        // the in-app Inbox keeps working exactly as before.
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareLinks.postIdFrom(intent.data)?.let { deepLinkPostId = it }
        ShareLinks.communityIdFrom(intent.data)?.let { deepLinkCommunityId = it }
        ShareLinks.profileIdFrom(intent.data)?.let { deepLinkUserId = it }
    }
}
