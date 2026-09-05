package com.example.kinetixfsl.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.kinetixfsl.account.AccountEraser
import com.example.kinetixfsl.ui.home.HomeIcons
import com.example.kinetixfsl.ui.theme.ThemePreference
import com.example.kinetixfsl.ui.theme.findActivity
import kotlinx.coroutines.launch

private enum class Stage { MENU, CHOICE, CONFIRM_RESET, CONFIRM_DELETE }

/**
 * The Settings pop-up, opened from the Profile gear.
 *
 *  - MENU: appearance (System / On / Off) + a "Delete account" entry.
 *  - CHOICE: the two destructive options.
 *  - CONFIRM_*: a final are-you-sure before running [AccountEraser].
 *
 * @param onSignOut routes to the login screen after a full account delete.
 * @param onEditProfile dismisses this menu and opens the Edit Profile sheet —
 *        see [EditProfileSheet].
 */
@Composable
fun AccountSettingsDialog(
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // LocalContext is wrapped by ForcedThemeResources, so resolve the real
    // Activity from the VIEW's context (unaffected by the override).
    val activity = androidx.compose.ui.platform.LocalView.current.context.findActivity()
    val scope = rememberCoroutineScope()
    val eraser = remember { AccountEraser() }

    var stage by remember { mutableStateOf(Stage.MENU) }
    var busy by remember { mutableStateOf(false) }

    fun runReset() {
        scope.launch {
            busy = true
            eraser.resetData(context)
            busy = false
            Toast.makeText(context, "Your data was reset.", Toast.LENGTH_LONG).show()
            onDismiss()
            // Recreate so every screen re-reads the now-empty stores.
            activity?.recreate()
        }
    }

    fun runDelete() {
        scope.launch {
            busy = true
            val outcome = eraser.deleteAccount(context)
            busy = false
            when (outcome) {
                AccountEraser.DeleteOutcome.DELETED -> {
                    // Appearance is a device setting that would otherwise stick
                    // on the deleted user's choice — reset it to "Use system" so
                    // the fresh login starts from the phone's own light/dark.
                    ThemePreference.set(ThemePreference.Mode.SYSTEM)
                    Toast.makeText(context, "Account deleted.", Toast.LENGTH_LONG).show()
                    onDismiss(); onSignOut()
                }
                AccountEraser.DeleteOutcome.DATA_WIPED_NEEDS_REAUTH -> {
                    ThemePreference.set(ThemePreference.Mode.SYSTEM)
                    Toast.makeText(
                        context,
                        "Your data was deleted. Sign in again to finish removing the account.",
                        Toast.LENGTH_LONG,
                    ).show()
                    onDismiss(); onSignOut()
                }
                AccountEraser.DeleteOutcome.REAUTH_CANCELLED -> {
                    // Nothing was touched — no toast needed, just return to the menu.
                    stage = Stage.MENU
                }
                AccountEraser.DeleteOutcome.FAILED -> {
                    Toast.makeText(context, "Couldn't delete the account. Try again.",
                        Toast.LENGTH_LONG).show()
                    stage = Stage.MENU
                }
            }
        }
    }

    when (stage) {
        Stage.MENU -> SettingsCard(
            onDeleteAccount = { stage = Stage.CHOICE },
            onEditProfile = onEditProfile,
            onDismiss = onDismiss,
        )
        Stage.CHOICE -> DeleteChoiceCard(
            onReset = { stage = Stage.CONFIRM_RESET },
            onDelete = { stage = Stage.CONFIRM_DELETE },
            onBack = { stage = Stage.MENU },
        )
        Stage.CONFIRM_RESET -> ConfirmDialog(
            title = "Reset your data?",
            message = "This deletes all your progress, quiz results, achievements, " +
                "posts, communities and notifications. Your account stays and you " +
                "start fresh. This can't be undone.",
            confirmLabel = "Reset",
            busy = busy,
            onConfirm = ::runReset,
            onCancel = { stage = Stage.MENU },
        )
        Stage.CONFIRM_DELETE -> ConfirmDialog(
            title = "Delete your account?",
            message = "This permanently deletes your account and all its data. " +
                "You may be asked to confirm your Google account first. " +
                "You'll be signed out and must register again to use the app. " +
                "This can't be undone.",
            confirmLabel = "Delete account",
            busy = busy,
            onConfirm = ::runDelete,
            onCancel = { stage = Stage.MENU },
        )
    }
}

// ── MENU: appearance + delete entry ─────────────────────────────────

@Composable
private fun SettingsCard(
    onDeleteAccount: () -> Unit,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        CardSurface {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // Name + photo, both stored only on this device — see
            // LocalProfileStore and EditProfileSheet.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditProfile)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = HomeIcons.Profile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Edit profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))

            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            ThemeOption("Use system setting", ThemePreference.Mode.SYSTEM)
            ThemeOption("On", ThemePreference.Mode.DARK)
            ThemeOption("Off", ThemePreference.Mode.LIGHT)

            Spacer(Modifier.height(18.dp))

            // Destructive entry.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDeleteAccount)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = com.example.kinetixfsl.community.CommunityIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Delete account",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, mode: ThemePreference.Mode) {
    val selected = ThemePreference.mode == mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { ThemePreference.set(mode) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = { ThemePreference.set(mode) })
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── CHOICE: the two destructive options ─────────────────────────────

@Composable
private fun DeleteChoiceCard(
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Dialog(onDismissRequest = onBack) {
        CardSurface {
            Text(
                text = "Delete account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose what to remove.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            ChoiceRow(
                title = "Reset my data",
                body = "Delete progress, quiz, achievements, posts, communities and " +
                    "notifications. Your account stays — start fresh.",
                onClick = onReset,
            )
            Spacer(Modifier.height(12.dp))
            ChoiceRow(
                title = "Delete my account",
                body = "Permanently delete the account and all its data. You'll be " +
                    "signed out and must register again.",
                destructive = true,
                onClick = onDelete,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    body: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Final confirmation ──────────────────────────────────────────────

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Working…")
                }
            } else {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
        },
    )
}

@Composable
private fun CardSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}
