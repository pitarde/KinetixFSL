package com.example.kinetixfsl.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.ui.home.HomeIcons
import java.io.File

/**
 * The "Edit profile" sheet — opened as a slide-up sheet (see SlideUpScreen,
 * same as Create/Edit Post and the community profile's own edit sheet) from
 * the Settings menu. Name and photo are both stored entirely on this device
 * (see [LocalProfileStore]) — there's no account to sync to; this only
 * changes what the Profile screen shows on this phone.
 *
 * A newly picked photo is only actually written to disk once Save is
 * tapped — see [pendingPhotoUri] — so dismissing without saving never
 * touches the previously saved copy.
 */
@Composable
internal fun EditProfileSheet(
    currentName: String,
    currentAvatar: File?,
    onSaved: (name: String, avatar: File?) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    // A photo just picked from the gallery, not yet written to disk.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) pendingPhotoUri = uri
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) LocalProfileStore.setName(context, trimmed)
        val savedAvatar = pendingPhotoUri?.let { LocalProfileStore.saveAvatar(context, it) }
            ?: currentAvatar
        onSaved(trimmed.ifBlank { currentName }, savedAvatar)
        dismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Edit profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(96.dp)) {
                val photo = pendingPhotoUri ?: currentAvatar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .then(
                            if (photo == null) {
                                Modifier.background(
                                    Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF6C5CE7))),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { pickPhoto.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                // Small camera badge, bottom-right of the circle.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { pickPhoto.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = HomeIcons.Camera,
                        contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the photo to change it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = "NAME",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))
        val canSave = name.isNotBlank()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                .clickable(enabled = canSave) { save() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
