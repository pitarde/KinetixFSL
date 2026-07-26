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

/** Confidence threshold to confirm a sign. */
private const val CONFIRM_THRESHOLD = 0.65f

/** How many consecutive high-confidence frames needed to confirm. */
private const val CONFIRM_FRAMES = 4

/** Minimum milliseconds between processing frames (~5 fps). */
private const val FRAME_INTERVAL_MS = 200L

/**
 * Camera Practice screen — live sign detection matching the Figma.
 *
 * Two states:
 * 1. **Detecting**: camera preview + "Show the sign for Letter X" prompt
 * 2. **Confirmed**: "Sign Confirmed" + detection log + "Proceed" button
 */
@Composable
fun CameraPracticeScreen(
    targetLabel: String,
    displayName: String,
    onBack: () -> Unit,
    onProceed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ── Permission handling ─────────────────────────────────
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

    // ── ML helpers (created once, cleaned up on dispose) ────
    var classifier by remember { mutableStateOf<SignClassifier?>(null) }
    var landmarkHelper by remember { mutableStateOf<HandLandmarkHelper?>(null) }

    LaunchedEffect(Unit) {
        try {
            classifier = SignClassifier(context)
            landmarkHelper = HandLandmarkHelper(context)
            Log.d(TAG, "ML models loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ML models", e)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            classifier?.close()
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
        // Top bar
        PracticeTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ── Camera / result area ────────────────────────
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
                            val cl = classifier ?: return@CameraPreview

                            // Apply rotation from CameraX so landmarks are upright
                            val rotated = rotateBitmap(bitmap, rotationDegrees)

                            val features = lh.detectAndNormalize(rotated)

                            // Recycle bitmaps to prevent OOM
                            if (rotated !== bitmap) rotated.recycle()
                            bitmap.recycle()

                            if (features != null) {
                                val result = cl.classify(features)
                                attempts++
                                detectedLabel = result.label
                                confidence = result.confidence

                                // Debug: see what the model is predicting
                                Log.d(TAG, "Target=$targetLabel | " +
                                        "Predicted=${result.label} | " +
                                        "Confidence=${(result.confidence * 100).toInt()}% | " +
                                        "Hits=$consecutiveHits")

                                if (result.label == targetLabel &&
                                    result.confidence >= CONFIRM_THRESHOLD
                                ) {
                                    consecutiveHits++
                                    if (consecutiveHits >= CONFIRM_FRAMES) {
                                        isConfirmed = true
                                        finishTime = System.currentTimeMillis()
                                    }
                                } else {
                                    consecutiveHits = 0
                                }
                            } else {
                                confidence = 0f
                                detectedLabel = ""
                                consecutiveHits = 0
                            }
                        },
                    )
                }

                // "Detecting..." or "Sign Confirmed" badge
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

                // Show what the model currently sees (debug info)
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

                // Confirmed overlay text
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

            Spacer(Modifier.height(20.dp))

            if (!isConfirmed) {
                // ── "Show the sign for..." prompt ───────────
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

                val tips = listOf(
                    "Ensure good lighting and clear view of your hands.",
                    "Perform sign slow and clear.",
                    "Keep your hands within the camera frame.",
                )
                tips.forEachIndexed { index, tip ->
                    Text(
                        text = "${index + 1}. $tip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            } else {
                // ── Detection log (shown after confirmation) ─
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
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Bottom button (only when confirmed) ─────────────
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

// ── Top bar ─────────────────────────────────────────────────────

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

// ── Detection log line ──────────────────────────────────────────

@Composable
private fun LogLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

// ── Bitmap rotation ─────────────────────────────────────────────

/**
 * Rotates a bitmap by the given degrees. CameraX reports rotation
 * needed to make the image upright — we must apply it before
 * passing to MediaPipe, otherwise landmarks are extracted from
 * a sideways image and won't match the training data.
 */
private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// ── CameraX preview with throttled frame analysis ───────────────

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

                // Throttled: only process a frame every FRAME_INTERVAL_MS
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
                        // bitmap is recycled inside onFrame after use
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

/**
 * Converts an [ImageProxy] with RGBA_8888 format to a [Bitmap].
 */
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