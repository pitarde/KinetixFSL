package com.example.kinetixfsl.detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kinetixfsl.modules.ModulesIcons
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixGreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "CameraPractice"

private const val CONFIRM_THRESHOLD = 0.65f
private const val CONFIRM_FRAMES = 4

// Anonymous, per-install id — lets admin analysis distinguish "one learner
// retried 20 times" from "20 different learners each got it in one try"
// without storing anything personally identifying.
private const val ANALYTICS_PREFS = "kinetix_analytics"
private const val KEY_DEVICE_ID = "device_id"

/** Length of one detection session — early-exit on success, this as fallback. */
private const val DETECTION_WINDOW_MS = 5_000L

// The overlay and the classifier run at DIFFERENT rates on purpose:
//
//  - OVERLAY_INTERVAL_MS drives how often MediaPipe runs and the red-dot
//    skeleton redraws. Fast (~12 fps) so the landmarks keep up with motion.
//  - CLASSIFIER_FEED_MS is how often a frame is pushed into the sign
//    classifier. It stays at 5 fps to match the training data
//    (CAPTURE_FPS = 5.0, 30-frame window). Feeding it faster would fill the
//    buffer in half the time and break the motion timing the model learned.
private const val OVERLAY_INTERVAL_MS = 80L
private const val CLASSIFIER_FEED_MS = 200L

// Feedback accents come from the KinetixFSL brand palette (ui/theme/Color.kt):
//   success  -> KinetixGreen   (the logo's "correct sign" green)
//   not-quite -> KinetixError  (the app's feedback colour)
// Everything else (progress, buttons, surfaces) reads from MaterialTheme so it
// tracks light/dark automatically.

/** Detection session state machine. */
private enum class Phase { DETECTING, SUCCESS, TIMEOUT }

/**
 * Camera Practice screen — supports both static and dynamic signs.
 *
 * ## Detection model: early-exit on success, timeout as fallback
 *
 * Each attempt is a [DETECTION_WINDOW_MS] session. The landmark buffer runs
 * continuously and predictions are evaluated in real time:
 *
 *  - **Instant success** — the moment the target sign is predicted at or
 *    above [CONFIRM_THRESHOLD] (dynamic) / for [CONFIRM_FRAMES] frames
 *    (static), the session ends immediately with a "Correct" result. It
 *    does not wait for the rest of the window.
 *  - **Forgiving** — before that, a wrong or low-confidence prediction never
 *    fails the attempt. The session keeps evaluating so the learner can
 *    adjust without restarting.
 *  - **Best-prediction tracking** — the highest-confidence prediction seen
 *    anywhere in the session is remembered, so a correct sign at second 2
 *    still counts even if the hands relax afterwards, and a timeout can show
 *    the closest thing the model saw instead of a blank failure.
 *  - **Timeout fallback** — if nothing crosses the threshold within the
 *    window, the session ends gracefully and shows that closest match.
 *
 * @param isDynamic   If true, uses DynamicSignClassifier (1D-CNN) instead of
 *                    SignClassifier (Dense).
 * @param categoryId  Module id from FslSignData ("alphabet", "numbers", ...).
 * @param onWatchDemo Optional — re-show the reference demo for this sign.
 *                    Null hides the "Watch demo" affordances.
 */
@Composable
fun CameraPracticeScreen(
    targetLabel: String,
    displayName: String,
    isDynamic: Boolean,
    categoryId: String,
    onBack: () -> Unit,
    onProceed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onWatchDemo: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // Device back mirrors the top-bar back: return to the Learning Room
    // (onBack is wired to do exactly that), from detecting or result view.
    BackHandler { onBack() }

    // ── Permission ──────────────────────────────────────────
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
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Detection session state ─────────────────────────────
    // Bumping sessionId restarts a fresh attempt (Replay / Try again).
    var sessionId by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.DETECTING) }

    // Live (current-frame) prediction.
    var confidence by remember { mutableFloatStateOf(0f) }
    var detectedLabel by remember { mutableStateOf("") }

    // Best prediction seen anywhere in the session (any label).
    var bestLabel by remember { mutableStateOf("") }
    var bestConfidence by remember { mutableFloatStateOf(0f) }

    var attempts by remember { mutableIntStateOf(0) }
    var consecutiveHits by remember { mutableIntStateOf(0) }
    var sessionStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var matchTimeMs by remember { mutableLongStateOf(0L) }

    // Time-based progress across the 5 s window (0f..1f).
    var timeProgress by remember { mutableFloatStateOf(0f) }
    var handStatus by remember { mutableStateOf("Keep your hands in the frame") }

    // Last time (uptimeMillis) a frame was pushed into the classifier. The
    // overlay runs faster than this; the classifier is gated to 5 fps to
    // match the training window. Held in an AtomicLong because it is read
    // and written from the camera analyzer thread, not the composition.
    val lastClassifierFeed = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    // ── Admin-analysis fields (Detection log) ────────────────
    // "Sign accuracy" is deliberately NOT the same as confidence: confidence
    // is the model's certainty about a single frame, while sign accuracy is
    // the fraction of the WHOLE attempt that actually looked like the target
    // sign — a user who flickers into the right shape for one lucky frame
    // reads very differently on this metric than one who held it cleanly.
    var targetMatchFrames by remember { mutableIntStateOf(0) }
    val seenLabels = remember { mutableSetOf<String>() }
    var distinctLabelCount by remember { mutableIntStateOf(0) }
    var retryCount by remember { mutableIntStateOf(0) }
    var confidenceAtMatch by remember { mutableFloatStateOf(0f) }
    var framesAtOutcome by remember { mutableIntStateOf(0) }
    var resultTimestampMs by remember { mutableLongStateOf(0L) }

    // Stable per-install id (not personally identifying) so repeated
    // attempts by the same learner can be grouped in admin analysis.
    val deviceSessionId = remember {
        val prefs = context.getSharedPreferences(ANALYTICS_PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
    }

    // Landmark overlay state — raw MediaPipe normalized coords (0..1).
    var landmarkPoints by remember {
        mutableStateOf<List<List<Triple<Float, Float, Float>>>?>(null)
    }

    // Word-sign modules track both hands; dynamic letters stay one-handed.
    val twoHanded = isDynamic && DynamicSignClassifier.isTwoHanded(categoryId)

    // ── ML helpers ──────────────────────────────────────────
    var staticClassifier by remember { mutableStateOf<SignClassifier?>(null) }
    var dynamicClassifier by remember { mutableStateOf<DynamicSignClassifier?>(null) }
    var landmarkHelper by remember { mutableStateOf<HandLandmarkHelper?>(null) }

    var modelError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isDynamic, categoryId) {
        try {
            landmarkHelper = HandLandmarkHelper(
                context,
                numHands = if (twoHanded) 2 else 1,
            )
            if (isDynamic) {
                dynamicClassifier = DynamicSignClassifier.forCategory(context, categoryId)
                Log.d(TAG, "Loaded DYNAMIC classifier for: $targetLabel " +
                        "($categoryId, ${if (twoHanded) "2-hand" else "1-hand"})")
            } else {
                staticClassifier = SignClassifier.forCategory(context, categoryId)
                Log.d(TAG, "Loaded STATIC classifier for: $targetLabel ($categoryId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML models for category '$categoryId'", e)
            modelError = "Model failed to load for \"$categoryId\".\n" +
                    (e.message?.take(220) ?: e::class.java.simpleName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            staticClassifier?.close()
            dynamicClassifier?.close()
            landmarkHelper?.close()
        }
    }

    // ── Session driver: resets trackers, ticks progress, times out ──
    // Re-keyed on sessionId so Replay / Try again start a clean window.
    LaunchedEffect(sessionId) {
        // sessionId 0 is the first attempt; anything after is a retry.
        if (sessionId > 0) retryCount++

        // Reset for a fresh attempt.
        dynamicClassifier?.reset()
        confidence = 0f
        detectedLabel = ""
        bestLabel = ""
        bestConfidence = 0f
        attempts = 0
        consecutiveHits = 0
        targetMatchFrames = 0
        seenLabels.clear()
        distinctLabelCount = 0
        confidenceAtMatch = 0f
        framesAtOutcome = 0
        timeProgress = 0f
        handStatus = "Keep your hands in the frame"
        sessionStart = System.currentTimeMillis()
        phase = Phase.DETECTING

        // Tick the time-based progress bar until success or timeout.
        while (phase == Phase.DETECTING) {
            val elapsed = System.currentTimeMillis() - sessionStart
            timeProgress = (elapsed.toFloat() / DETECTION_WINDOW_MS).coerceIn(0f, 1f)
            if (elapsed >= DETECTION_WINDOW_MS) {
                framesAtOutcome = dynamicClassifier?.frameCount ?: attempts
                resultTimestampMs = System.currentTimeMillis()
                phase = Phase.TIMEOUT
                break
            }
            delay(50)
        }
    }

    val detecting = phase == Phase.DETECTING

    // ── UI ───────────────────────────────────────────────────
    // Detecting: a top bar, a large rounded camera card, then the prompt,
    // progress bar and Watch-demo button stacked below it. Result: the same
    // top bar over a scrolling result layout.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PracticeTopBar(
            title = "Practice: $displayName",
            onBack = onBack,
        )

        if (detecting) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) {
                // ── Camera card ─────────────────────────────
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
                                if (phase != Phase.DETECTING) {
                                    bitmap.recycle()
                                    return@CameraPreview
                                }

                                val lh = landmarkHelper
                                if (lh == null) {
                                    bitmap.recycle()
                                    return@CameraPreview
                                }

                                try {
                                    // Monotonic timestamp for VIDEO-mode tracking.
                                    val tsMs = SystemClock.uptimeMillis()
                                    val rotated = rotateBitmap(bitmap, rotationDegrees)

                                    val features: FloatArray?
                                    val rawLandmarks: List<List<Triple<Float, Float, Float>>>?
                                    if (twoHanded) {
                                        val r = lh.detectTwoHandsWithLandmarks(rotated, tsMs)
                                        features = r?.first
                                        rawLandmarks = r?.second
                                    } else {
                                        // Dynamic letters (J, Z, NG, Ñ) need the
                                        // wrist-preserving encoding to capture motion;
                                        // static letters stay wrist-zeroed.
                                        val r = if (isDynamic)
                                            lh.detectDynamicHandWithLandmarks(rotated, tsMs)
                                        else
                                            lh.detectAndNormalizeWithLandmarks(rotated, tsMs)
                                        features = r?.first
                                        rawLandmarks = r?.second?.let { listOf(it) }
                                    }

                                    if (rotated !== bitmap) rotated.recycle()
                                    bitmap.recycle()

                                    // Overlay redraws every frame (fast path).
                                    landmarkPoints = rawLandmarks

                                    // Feed the classifier only at 5 fps (training cadence).
                                    val feedClassifier =
                                        tsMs - lastClassifierFeed.get() >= CLASSIFIER_FEED_MS
                                    if (feedClassifier) lastClassifierFeed.set(tsMs)

                                    if (features == null) {
                                        // No hand: clear overlay + status every frame.
                                        landmarkPoints = null
                                        handStatus = "No hand detected — show your hand"
                                        confidence = 0f
                                        detectedLabel = ""
                                        consecutiveHits = 0
                                    } else if (feedClassifier) {
                                        if (isDynamic) {
                                            val dc = dynamicClassifier ?: return@CameraPreview
                                            dc.addFrame(features)

                                            if (dc.canClassify &&
                                                (dc.frameCount % 3 == 0 || dc.isFull)
                                            ) {
                                                val r = dc.classify()
                                                attempts++
                                                detectedLabel = r.label
                                                confidence = r.confidence
                                                handStatus = "Detecting your sign..."

                                                // Track best prediction of the session.
                                                if (r.confidence > bestConfidence) {
                                                    bestConfidence = r.confidence
                                                    bestLabel = r.label
                                                }

                                                // Sign-accuracy bookkeeping: how much of the
                                                // WHOLE attempt matched the target, and how
                                                // many different labels the model flip-flopped
                                                // between (a confusion signal).
                                                if (r.label == targetLabel) targetMatchFrames++
                                                seenLabels.add(r.label)
                                                distinctLabelCount = seenLabels.size

                                                if (r.label == targetLabel &&
                                                    r.confidence >= CONFIRM_THRESHOLD
                                                ) {
                                                    matchTimeMs =
                                                        System.currentTimeMillis() - sessionStart
                                                    confidenceAtMatch = r.confidence
                                                    framesAtOutcome = dc.frameCount
                                                    resultTimestampMs = System.currentTimeMillis()
                                                    phase = Phase.SUCCESS
                                                }
                                            } else {
                                                handStatus = "Detecting your sign..."
                                            }
                                        } else {
                                            val sc = staticClassifier ?: return@CameraPreview
                                            val r = sc.classify(features)
                                            attempts++
                                            detectedLabel = r.label
                                            confidence = r.confidence
                                            handStatus = "Detecting your sign..."

                                            if (r.confidence > bestConfidence) {
                                                bestConfidence = r.confidence
                                                bestLabel = r.label
                                            }

                                            if (r.label == targetLabel) targetMatchFrames++
                                            seenLabels.add(r.label)
                                            distinctLabelCount = seenLabels.size

                                            if (r.label == targetLabel &&
                                                r.confidence >= CONFIRM_THRESHOLD
                                            ) {
                                                consecutiveHits++
                                                if (consecutiveHits >= CONFIRM_FRAMES) {
                                                    matchTimeMs =
                                                        System.currentTimeMillis() - sessionStart
                                                    confidenceAtMatch = r.confidence
                                                    framesAtOutcome = attempts
                                                    resultTimestampMs = System.currentTimeMillis()
                                                    phase = Phase.SUCCESS
                                                }
                                            } else {
                                                consecutiveHits = 0
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Frame processing error", e)
                                }
                            },
                        )

                        HandLandmarkOverlay(hands = landmarkPoints, mirrorX = false)

                        // Centered dashed hand-position guide.
                        DashedFrameGuide()

                        // Recording pill (pulsing dot), top-left inside the card.
                        RecordingPill(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                        )

                        // Live "seeing" chip, top-right inside the card.
                        if (detectedLabel.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Seeing: $detectedLabel (${(confidence * 100).toInt()}%)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // Model failed to load (e.g. asset not bundled yet).
                    modelError?.let { message ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Prompt ──────────────────────────────────
                Text(
                    text = "Perform the sign for \"$displayName\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = handStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(14.dp))

                // ── Time-based detection progress bar (5 s) ──
                LinearProgressIndicator(
                    progress = { timeProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Detecting, up to ${DETECTION_WINDOW_MS / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(16.dp))

                // ── Watch demo again (filled brand pill) ────
                if (onWatchDemo != null) {
                    Button(
                        onClick = onWatchDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Watch demo again",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        } else {
            // ── Result view (SUCCESS or TIMEOUT) ────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                ResultCard(
                    success = phase == Phase.SUCCESS,
                    displayName = displayName,
                    matchTimeMs = matchTimeMs,
                    confidence = if (phase == Phase.SUCCESS) confidence else bestConfidence,
                    closestMatch = bestLabel,
                    categoryId = categoryId,
                    onProceed = onProceed,
                    onRetry = { sessionId++ },
                    onWatchDemo = onWatchDemo,
                )

                Spacer(Modifier.height(20.dp))

                // ── Detection log — built for admin analysis, not just  ──
                // ── on-screen debugging. Every field here is meant to  ──
                // ── survive being pulled into a spreadsheet/dashboard   ──
                // ── later: which sign, on which device, how well.       ──
                val signAccuracy = if (attempts > 0)
                    targetMatchFrames.toFloat() / attempts else 0f
                val modelVersion = if (isDynamic)
                    dynamicClassifier?.modelAssetName
                else
                    staticClassifier?.modelAssetName
                val timestampLabel = remember(resultTimestampMs) {
                    if (resultTimestampMs == 0L) "—"
                    else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(resultTimestampMs))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "Detection log for $displayName:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))

                        LogLine("Target sign", targetLabel)
                        LogLine("Category", categoryId)
                        LogLine("Timestamp", timestampLabel)
                        LogLine("Outcome",
                            if (phase == Phase.SUCCESS) "Correct" else "Timed out")
                        if (phase == Phase.SUCCESS) {
                            LogLine("Matched in",
                                String.format("%.1fs", matchTimeMs / 1000f))
                            LogLine("Confidence at match",
                                "${(confidenceAtMatch * 100).toInt()}%")
                        }
                        LogLine("Best prediction",
                            if (bestLabel.isEmpty()) "—"
                            else "$bestLabel (${(bestConfidence * 100).toInt()}%)")

                        // Sign accuracy: fraction of the WHOLE attempt (not just the
                        // matching instant) that read as the target sign — see the
                        // KDoc on CameraPracticeScreen for why this differs from
                        // "Confidence at match".
                        LogLine("Sign accuracy",
                            "${(signAccuracy * 100).toInt()}% " +
                                    "($targetMatchFrames/$attempts frames)")
                        LogLine("Frames classified", "$attempts")
                        LogLine("Distinct predictions seen", "$distinctLabelCount")
                        LogLine("Retry count", "$retryCount")
                        if (isDynamic) {
                            LogLine("Frames at outcome",
                                "$framesAtOutcome/${DynamicSignClassifier.SEQUENCE_LENGTH}")
                        }
                        LogLine("Model version", modelVersion ?: "—")
                        LogLine("Session ID", deviceSessionId.take(8))
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Result card ────────────────────────────────────────────────

@Composable
private fun ResultCard(
    success: Boolean,
    displayName: String,
    matchTimeMs: Long,
    confidence: Float,
    closestMatch: String,
    categoryId: String,
    onProceed: (() -> Unit)?,
    onRetry: () -> Unit,
    onWatchDemo: (() -> Unit)?,
) {
    val accent = if (success) KinetixGreen else KinetixError

    Spacer(Modifier.height(20.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
    ) {
        Column {
            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ModulesIcons.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (success) "Correct" else "Not quite",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (success)
                    "Matched in ${String.format("%.1f", matchTimeMs / 1000f)}s"
                else if (closestMatch.isNotEmpty())
                    "Closest match: $closestMatch"
                else
                    "No confident match this time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(16.dp))

            // Confidence bar
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
                trackColor = MaterialTheme.colorScheme.surface,
            )

            if (!success) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Tip: keep your hands inside the frame and hold the " +
                            "full motion until it's recognized.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(18.dp))

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (success && onProceed != null) {
                    Button(
                        onClick = onProceed,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = when (categoryId) {
                                "alphabet" -> "Next letter"
                                "numbers" -> "Next number"
                                else -> "Next word"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = if (success) "Replay" else "Try again",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!success && onWatchDemo != null) {
                    OutlinedButton(
                        onClick = onWatchDemo,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Watch demo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Dashed frame guide ─────────────────────────────────────────

/**
 * A large dashed rounded rectangle in the upper-centre of the camera area,
 * showing learners where to place their hands before the sign is detected.
 */
@Composable
private fun DashedFrameGuide() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // A softly portrait rounded box, centred in the camera card.
        val boxW = size.width * 0.5f
        val boxH = boxW * 1.25f
        val left = (size.width - boxW) / 2f
        val top = (size.height - boxH) / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.7f),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(28f, 28f),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), 0f),
            ),
        )
    }
}

// ── Recording pill ─────────────────────────────────────────────

/**
 * Small translucent pill with a pulsing red dot, indicating the camera is
 * actively monitoring. Subtle and non-distracting.
 */
@Composable
private fun RecordingPill(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "recording")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
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
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(KinetixError),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Recording",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Hand Landmark Overlay ──────────────────────────────────────

private val HAND_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    5 to 9, 9 to 13, 13 to 17,
)

/**
 * Draws the 21-point hand skeleton on top of the camera preview.
 *
 * @param hands    One inner list of 21 (x, y, z) triples per detected hand,
 *                 in MediaPipe's 0..1 normalized space. Null = no hand.
 * @param mirrorX  If true, x is mirrored (1 - x) to match a mirrored preview.
 */
@Composable
internal fun HandLandmarkOverlay(
    hands: List<List<Triple<Float, Float, Float>>>?,
    mirrorX: Boolean,
) {
    if (hands.isNullOrEmpty()) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        fun toScreen(lm: Triple<Float, Float, Float>): Offset {
            val x = if (mirrorX) (1f - lm.first) * w else lm.first * w
            val y = lm.second * h
            return Offset(x, y)
        }

        for (landmarks in hands) {
            if (landmarks.size < 21) continue

            for ((a, b) in HAND_CONNECTIONS) {
                drawLine(
                    color = Color.White,
                    start = toScreen(landmarks[a]),
                    end = toScreen(landmarks[b]),
                    strokeWidth = 3f,
                )
            }

            for (lm in landmarks) {
                drawCircle(color = Color.Red, radius = 6f, center = toScreen(lm))
            }
        }
    }
}

// ── Shared composables ──────────────────────────────────────────

@Composable
private fun PracticeTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    overCamera: Boolean = false,
) {
    // Over the camera, use white so the bar stays legible on any scene;
    // on the result view fall back to the theme's foreground colour.
    val tint = if (overCamera) Color.White else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ModulesIcons.ArrowBack,
            contentDescription = "Go back",
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onBack),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = tint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

internal fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
internal fun CameraPreview(
    onFrame: (Bitmap, Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    // The camera binds to the host lifecycle, which for a Home TAB outlives
    // this composable. So we MUST stop it ourselves on dispose — otherwise the
    // analyzer keeps firing onFrame after the caller has closed its detector,
    // which is a native (uncatchable) crash. `active` also gates the callback
    // so no frame sneaks through during teardown.
    val active = remember { AtomicBoolean(true) }
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            runCatching { providerRef.get()?.unbindAll() }
            runCatching { executor.shutdown() }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                // The composable may have been disposed before the provider
                // finished loading — don't bind a camera nobody is showing.
                if (!active.get()) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                providerRef.set(cameraProvider)

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                var lastProcessedTime = 0L

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (!active.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastProcessedTime < OVERLAY_INTERVAL_MS) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastProcessedTime = now

                    val bitmap = imageProxyToBitmap(imageProxy)
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close()

                    if (bitmap != null && active.get()) {
                        onFrame(bitmap, rotation)
                    } else {
                        bitmap?.recycle()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val pixelStride = imageProxy.planes[0].pixelStride
        val rowStride = imageProxy.planes[0].rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width

        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888,
        )
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e(TAG, "Bitmap conversion failed", e)
        null
    }
}
