package com.example.kinetixfsl.detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kinetixfsl.modules.ModulesIcons
import java.util.concurrent.Executors

private const val TAG = "CameraPractice"

private const val CONFIRM_THRESHOLD = 0.65f
private const val CONFIRM_FRAMES = 4
private const val FRAME_INTERVAL_MS = 200L

/**
 * Camera Practice screen — supports both static and dynamic signs.
 *
 * Static signs: classified per frame (Dense model).
 * Dynamic signs: early classification — starts attempting after 15 frames,
 *   confirms as soon as the motion is recognized with high confidence.
 *
 * @param isDynamic If true, uses DynamicSignClassifier (1D-CNN) instead of
 *                  SignClassifier (Dense).
 */
@Composable
fun CameraPracticeScreen(
    targetLabel: String,
    displayName: String,
    isDynamic: Boolean,
    onBack: () -> Unit,
    onProceed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

    // ── Detection state ─────────────────────────────────────
    var isConfirmed by remember { mutableStateOf(false) }
    var confidence by remember { mutableFloatStateOf(0f) }
    var detectedLabel by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var consecutiveHits by remember { mutableIntStateOf(0) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finishTime by remember { mutableLongStateOf(0L) }

    // Dynamic-specific state
    var dynamicProgress by remember { mutableFloatStateOf(0f) }
    var dynamicStatus by remember { mutableStateOf("Waiting for hand...") }

    // Landmark overlay state — raw MediaPipe normalized coords (0..1)
    var landmarkPoints by remember { mutableStateOf<List<Triple<Float, Float, Float>>?>(null) }

    // ── ML helpers ──────────────────────────────────────────
    var staticClassifier by remember { mutableStateOf<SignClassifier?>(null) }
    var dynamicClassifier by remember { mutableStateOf<DynamicSignClassifier?>(null) }
    var landmarkHelper by remember { mutableStateOf<HandLandmarkHelper?>(null) }

    LaunchedEffect(Unit) {
        try {
            landmarkHelper = HandLandmarkHelper(context)
            if (isDynamic) {
                dynamicClassifier = DynamicSignClassifier(context)
                Log.d(TAG, "Loaded DYNAMIC classifier for: $targetLabel")
            } else {
                staticClassifier = SignClassifier(context)
                Log.d(TAG, "Loaded STATIC classifier for: $targetLabel")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML models", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            staticClassifier?.close()
            dynamicClassifier?.close()
            landmarkHelper?.close()
        }
    }

    // ── UI ───────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PracticeTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ── Camera area ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (hasCameraPermission && !isConfirmed) {
                    CameraPreview(
                        onFrame = { bitmap, rotationDegrees ->
                            if (isConfirmed) return@CameraPreview

                            val lh = landmarkHelper ?: return@CameraPreview
                            val rotated = rotateBitmap(bitmap, rotationDegrees)
                            val result = lh.detectAndNormalizeWithLandmarks(rotated)
                            if (rotated !== bitmap) rotated.recycle()
                            bitmap.recycle()

                            val features = result?.first
                            val rawLandmarks = result?.second

                            // Update landmark overlay (even if null — clears old dots)
                            landmarkPoints = rawLandmarks

                            if (features != null) {
                                if (isDynamic) {
                                    // ── Dynamic: buffer + early classify ──
                                    val dc = dynamicClassifier ?: return@CameraPreview
                                    dc.addFrame(features)
                                    dynamicProgress = dc.progress

                                    if (dc.canClassify &&
                                        (dc.frameCount % 3 == 0 || dc.isFull)
                                    ) {
                                        // Classify every 3rd frame to avoid lag.
                                        // Always classify on the final frame.
                                        val classResult = dc.classify()
                                        attempts++
                                        detectedLabel = classResult.label
                                        confidence = classResult.confidence

                                        dynamicStatus =
                                            "Analyzing... ${dc.frameCount}/${DynamicSignClassifier.SEQUENCE_LENGTH}"

                                        Log.d(TAG, "DYNAMIC Target=$targetLabel | " +
                                                "Predicted=${classResult.label} | " +
                                                "Confidence=${(classResult.confidence * 100).toInt()}% | " +
                                                "Frames=${dc.frameCount}")

                                        if (classResult.label == targetLabel &&
                                            classResult.confidence >= CONFIRM_THRESHOLD
                                        ) {
                                            isConfirmed = true
                                            finishTime = System.currentTimeMillis()
                                        } else if (dc.isFull) {
                                            // Buffer full without a match — reset
                                            dc.reset()
                                            dynamicProgress = 0f
                                            dynamicStatus = "Not matched — try again"
                                        }
                                    } else {
                                        dynamicStatus =
                                            "Recording motion... ${dc.frameCount}/${DynamicSignClassifier.MIN_FRAMES_FOR_EARLY}"
                                    }
                                } else {
                                    // ── Static: classify per frame ──
                                    val sc = staticClassifier ?: return@CameraPreview
                                    val classResult = sc.classify(features)
                                    attempts++
                                    detectedLabel = classResult.label
                                    confidence = classResult.confidence

                                    Log.d(TAG, "STATIC Target=$targetLabel | " +
                                            "Predicted=${classResult.label} | " +
                                            "Confidence=${(classResult.confidence * 100).toInt()}% | " +
                                            "Hits=$consecutiveHits")

                                    if (classResult.label == targetLabel &&
                                        classResult.confidence >= CONFIRM_THRESHOLD
                                    ) {
                                        consecutiveHits++
                                        if (consecutiveHits >= CONFIRM_FRAMES) {
                                            isConfirmed = true
                                            finishTime = System.currentTimeMillis()
                                        }
                                    } else {
                                        consecutiveHits = 0
                                    }
                                }
                            } else {
                                // No hand detected
                                landmarkPoints = null
                                if (isDynamic) {
                                    dynamicStatus = "No hand detected — show your hand"
                                }
                                confidence = 0f
                                detectedLabel = ""
                                consecutiveHits = 0
                            }
                        },
                    )

                    // ── Hand landmark overlay ───────────────
                    // landmarks come from the already-flipped bitmap, so their
                    // x coords already match the mirrored preview — no extra flip.
                    HandLandmarkOverlay(
                        landmarks = landmarkPoints,
                        mirrorX = false,
                    )
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isConfirmed) Color(0xFF4CAF50)
                            else Color(0xFF2E7D32)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = if (isConfirmed) "Confirmed" else "Detecting...",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Debug overlay — what the model sees
                if (!isConfirmed && detectedLabel.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Seeing: $detectedLabel (${(confidence * 100).toInt()}%)",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Confirmed overlay
                if (isConfirmed) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Sign Confirmed",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Confidence bar ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E7D32))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Confidence",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // ── Dynamic: motion progress bar ────────────────
            if (isDynamic && !isConfirmed) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = dynamicStatus,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { dynamicProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (!isConfirmed) {
                // ── Sign prompt ─────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Show the sign for",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── How to use ──────────────────────────────
                Text(
                    text = "How to use:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                val tips = if (isDynamic) {
                    listOf(
                        "Ensure good lighting and clear view of your hands.",
                        "Perform the sign movement slowly and clearly.",
                        "Keep your hands within the camera frame throughout the motion.",
                        "The sign will be confirmed as soon as it's recognized — no need to wait for the full bar.",
                    )
                } else {
                    listOf(
                        "Ensure good lighting and clear view of your hands.",
                        "Perform sign slow and clear.",
                        "Keep your hands within the camera frame.",
                    )
                }
                tips.forEachIndexed { index, tip ->
                    Text(
                        text = "${index + 1}. $tip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            } else {
                // ── Detection log ───────────────────────────
                val elapsed = ((finishTime - startTime) / 1000).toInt()
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
                        LogLine("Attempts", "$attempts")
                        LogLine("Time finished", "${elapsed}sec")
                        LogLine("Accurate percentage", "${(confidence * 100).toInt()}%")
                        LogLine("Confidence", "${(confidence * 100).toInt()}%")
                        LogLine("Type", if (isDynamic) "Dynamic (1D-CNN)" else "Static (Dense)")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Proceed button ──────────────────────────────────
        if (isConfirmed && onProceed != null) {
            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Proceed to the next letter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Hand Landmark Overlay ──────────────────────────────────────

/**
 * MediaPipe hand connections — pairs of landmark indices that should
 * be connected by lines, matching the skeleton drawn in Python's
 * mp_draw.draw_landmarks().
 */
private val HAND_CONNECTIONS = listOf(
    // Thumb
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    // Index finger
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    // Middle finger
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    // Ring finger
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    // Pinky
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    // Palm
    5 to 9, 9 to 13, 13 to 17,
)

/**
 * Draws the 21-point hand skeleton on top of the camera preview.
 *
 * @param landmarks  List of 21 (x, y, z) triples in MediaPipe's 0..1
 *                   normalized coordinate space. Null = no hand detected.
 * @param isFrontCamera  If true, x is mirrored (1 - x) so the overlay
 *                       matches the mirrored preview the user sees.
 */
@Composable
private fun HandLandmarkOverlay(
    landmarks: List<Triple<Float, Float, Float>>?,
    mirrorX: Boolean,
) {
    if (landmarks == null) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        fun toScreen(lm: Triple<Float, Float, Float>): Offset {
            val x = if (mirrorX) (1f - lm.first) * w else lm.first * w
            val y = lm.second * h
            return Offset(x, y)
        }

        // Draw connections (white lines)
        for ((a, b) in HAND_CONNECTIONS) {
            val pA = toScreen(landmarks[a])
            val pB = toScreen(landmarks[b])
            drawLine(
                color = Color.White,
                start = pA,
                end = pB,
                strokeWidth = 3f,
            )
        }

        // Draw landmark dots (red circles)
        for (lm in landmarks) {
            val p = toScreen(lm)
            drawCircle(
                color = Color.Red,
                radius = 6f,
                center = p,
            )
        }
    }
}

// ── Shared composables ──────────────────────────────────────────

@Composable
private fun PracticeTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ModulesIcons.ArrowBack,
            contentDescription = "Go back",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onBack),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = "Learning Room",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
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

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
private fun CameraPreview(
    onFrame: (Bitmap, Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                var lastProcessedTime = 0L

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastProcessedTime < FRAME_INTERVAL_MS) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastProcessedTime = now

                    val bitmap = imageProxyToBitmap(imageProxy)
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close()

                    if (bitmap != null) {
                        onFrame(bitmap, rotation)
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
