package com.example.kinetixfsl.detection

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MainCamera"

// A confident-enough live reading turns the result green. Below this it stays
// neutral — the same 0.65 the practice screen confirms at.
private const val CONFIDENT = 0.65f

// Feed the dynamic classifier at 5 fps (training cadence); the overlay runs
// faster inside CameraPreview.
private const val FEED_MS = 200L

private val ConfidentGreen = Color(0xFF4CAF50)

/**
 * One selectable detection mode. [categoryId] picks the model via the same
 * factories the practice screen uses, so 0 (Numbers) and O (Letters) never
 * compete — the user chooses which world they're signing in.
 *
 * [available] = false means the category has no trained model yet; the screen
 * shows a "no model yet" notice instead of trying to load one.
 */
private data class CameraMode(
    val label: String,
    val categoryId: String,
    val isDynamic: Boolean,
    val available: Boolean = true,
)

// Order = left-to-right in the chip row. Flip `available` to true as each
// category's model is trained and copied into assets.
private val MODES = listOf(
    // Filipino alphabet — static handshapes (A-I, K-Y)...
    CameraMode("Letters", "alphabet", isDynamic = false),
    // ...and the motion letters J, Z, NG, Ñ (one-handed dynamic, fsl_dynamic).
    CameraMode("Motion Letters", "alphabet", isDynamic = true),
    CameraMode("Numbers", "numbers", isDynamic = false),
    CameraMode("Greetings", "greetings", isDynamic = true),
    CameraMode("School", "school", isDynamic = true),
    // Not trained yet — show a friendly notice instead of loading.
    CameraMode("Emergency", "emergency", isDynamic = true, available = false),
    CameraMode("Daily Needs", "dailyneeds", isDynamic = true, available = false),
    CameraMode("Social", "social", isDynamic = true, available = false),
)

private fun isTwoHanded(mode: CameraMode): Boolean =
    mode.isDynamic && DynamicSignClassifier.isTwoHanded(mode.categoryId)

/**
 * Free-detection camera — the "try any sign right now" capstone feature.
 *
 * Unlike the practice screen, there is no target sign and no pass/fail: the
 * user picks a mode, performs a sign, and sees the model's live top guess and
 * confidence. Modes map to the per-category models, which keeps look-alike
 * signs across categories (0/O, 2/V, ...) from colliding.
 *
 * ## Why the models are cached and the detector is single
 *
 * The camera analyzer captures its frame callback ONCE. Switching modes must
 * therefore never close a detector or classifier the callback might still be
 * mid-call on — doing so is a native (uncatchable) crash. So:
 *   - ONE [HandLandmarkHelper] (numHands = 2) serves every mode for the whole
 *     screen; it is only closed on dispose.
 *   - classifiers are built lazily and CACHED per category; a mode switch just
 *     changes which cached one the callback reads. Nothing is closed until the
 *     screen leaves composition.
 */
@Composable
fun MainCameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── Permission ──
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── Mode ──
    var mode by remember { mutableStateOf(MODES.first()) }

    // ── ML: one detector for the whole screen, classifiers cached by id ──
    var landmarkHelper by remember { mutableStateOf<HandLandmarkHelper?>(null) }
    val staticCache = remember { ConcurrentHashMap<String, SignClassifier>() }
    val dynamicCache = remember { ConcurrentHashMap<String, DynamicSignClassifier>() }
    var modelError by remember { mutableStateOf<String?>(null) }

    // ── Live reading ──
    var detectedLabel by remember { mutableStateOf("") }
    var confidence by remember { mutableFloatStateOf(0f) }
    var hint by remember { mutableStateOf("Show your hand") }
    var landmarkPoints by remember {
        mutableStateOf<List<List<Triple<Float, Float, Float>>>?>(null)
    }
    val lastFeed = remember { AtomicLong(0L) }

    // Detector: created once, numHands = 2 (works for one- and two-hand modes).
    LaunchedEffect(Unit) {
        try {
            landmarkHelper = HandLandmarkHelper(context, numHands = 2)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HandLandmarkHelper", e)
            modelError = "Camera model failed to load."
        }
    }

    // Load (and cache) the selected mode's classifier; never close here.
    LaunchedEffect(mode) {
        detectedLabel = ""; confidence = 0f
        hint = "Show your hand"
        landmarkPoints = null
        lastFeed.set(0L)

        if (!mode.available) {
            modelError = "There is no model yet.\n\"${mode.label}\" is coming soon."
            return@LaunchedEffect
        }
        modelError = null
        try {
            if (mode.isDynamic) {
                val dc = dynamicCache.getOrPut(mode.categoryId) {
                    DynamicSignClassifier.forCategory(context, mode.categoryId)
                }
                dc.reset()
            } else {
                staticCache.getOrPut(mode.categoryId) {
                    SignClassifier.forCategory(context, mode.categoryId)
                }
            }
            Log.d(TAG, "Ready: ${mode.label} (${mode.categoryId})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model for ${mode.categoryId}", e)
            modelError = "There is no model yet.\n\"${mode.label}\" could not load."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            landmarkHelper?.close()
            staticCache.values.forEach { runCatching { it.close() } }
            dynamicCache.values.forEach { runCatching { it.close() } }
            staticCache.clear(); dynamicCache.clear()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        // ── Mode chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MODES.forEach { m ->
                ModeChip(
                    label = m.label,
                    selected = m == mode,
                    available = m.available,
                    onClick = { if (m != mode) mode = m },
                )
            }
        }

        // ── Camera ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    onFrame = { bitmap, rotationDegrees ->
                        val lh = landmarkHelper
                        val m = mode
                        if (lh == null || !m.available) {
                            bitmap.recycle()
                            return@CameraPreview
                        }
                        try {
                            val tsMs = SystemClock.uptimeMillis()
                            val rotated = rotateBitmap(bitmap, rotationDegrees)
                            val twoHanded = isTwoHanded(m)

                            val features: FloatArray?
                            val raw: List<List<Triple<Float, Float, Float>>>?
                            when {
                                twoHanded -> {
                                    val r = lh.detectTwoHandsWithLandmarks(rotated, tsMs)
                                    features = r?.first; raw = r?.second
                                }
                                m.isDynamic -> {
                                    val r = lh.detectDynamicHandWithLandmarks(rotated, tsMs)
                                    features = r?.first; raw = r?.second?.let { listOf(it) }
                                }
                                else -> {
                                    val r = lh.detectAndNormalizeWithLandmarks(rotated, tsMs)
                                    features = r?.first; raw = r?.second?.let { listOf(it) }
                                }
                            }
                            if (rotated !== bitmap) rotated.recycle()
                            bitmap.recycle()

                            landmarkPoints = raw

                            if (features == null) {
                                hint = "Show your hand"
                                detectedLabel = ""; confidence = 0f
                                return@CameraPreview
                            }

                            if (m.isDynamic) {
                                val dc = dynamicCache[m.categoryId] ?: return@CameraPreview
                                if (features.size != dc.numFeatures) return@CameraPreview
                                val feed = tsMs - lastFeed.get() >= FEED_MS
                                if (feed) {
                                    lastFeed.set(tsMs)
                                    dc.addFrame(features)
                                    if (dc.canClassify) {
                                        val r = dc.classify()
                                        detectedLabel = r.label
                                        confidence = r.confidence
                                    }
                                    hint = "Keep performing the sign"
                                }
                            } else {
                                val sc = staticCache[m.categoryId] ?: return@CameraPreview
                                if (features.size != 63) return@CameraPreview
                                val r = sc.classify(features)
                                detectedLabel = r.label
                                confidence = r.confidence
                                hint = "Hold the handshape steady"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Frame error", e)
                        }
                    },
                )

                HandLandmarkOverlay(hands = landmarkPoints, mirrorX = false)
                LiveBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                )
            }

            modelError?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        // ── Result ──
        val confident = detectedLabel.isNotEmpty() && confidence >= CONFIDENT
        val accent = if (confident) ConfidentGreen else MaterialTheme.colorScheme.onSurfaceVariant

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "Detected — ${mode.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (detectedLabel.isEmpty()) hint else detectedLabel,
                style = MaterialTheme.typography.displaySmall,
                color = if (detectedLabel.isEmpty())
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                else accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Confidence",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { confidence.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick a mode above and then sign.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            // Dim the not-yet-trained modes, but keep them tappable so the
            // user can see the "coming soon" notice.
            .alpha(if (available || selected) 1f else 0.45f)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live")
    val dot by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(dot)
                .clip(CircleShape)
                .background(Color(0xFFE53935)),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Live",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
