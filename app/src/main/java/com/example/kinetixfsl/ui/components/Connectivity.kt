package com.example.kinetixfsl.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the device currently has a usable internet connection, as a Compose
 * [State] that flips live when the network comes or goes.
 *
 * This backs the offline state: a screen that failed to load shows "No Internet
 * Connection" only when this is false, a generic error otherwise — and, because
 * it's live, a screen can auto-retry the moment the connection returns rather
 * than making the user tap Retry themselves.
 *
 * It reports transport availability, not reachability — a captive-portal wifi
 * reads as "online" here. That's the same limit every client-side check has;
 * the failed request itself is the real backstop, and this just decides how to
 * word the failure and when to auto-retry.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = isCurrentlyOnline(context), context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

        if (manager == null) {
            value = true // No way to tell — assume online rather than block.
            return@produceState
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                value = true
            }

            // Fired when the last usable network goes away.
            override fun onLost(network: Network) {
                value = isCurrentlyOnline(context)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

        // A snapshot up front, in case the state changed between the initial
        // read and registering the callback.
        value = isCurrentlyOnline(context)
        runCatching { manager.registerDefaultNetworkCallback(callback) }

        awaitDispose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
}

/** One-shot connectivity read — the seed value and the [onLost] recheck. */
private fun isCurrentlyOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return true
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
