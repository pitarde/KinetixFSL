package com.example.kinetixfsl.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

/**
 * Wraps MediaPipe [HandLandmarker] for on-device hand detection.
 *
 * Extracts 21 hand landmarks, normalizes them (wrist-relative + scale-invariant)
 * into a 63-dim [FloatArray] that can be fed directly to [SignClassifier].
 *
 * The normalization matches the Python `collect_landmarks.py` exactly:
 *   1. Subtract wrist position (landmark 0) from all landmarks
 *   2. Divide by distance from wrist to middle-finger MCP (landmark 9)
 */
class HandLandmarkHelper(
    context: Context,
    numHands: Int = 1,
) {

    private val handLandmarker: HandLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(numHands)
            .setMinHandDetectionConfidence(0.7f)
            .setMinHandPresenceConfidence(0.7f)
            .setMinTrackingConfidence(0.3f)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    /**
     * Detects hand landmarks in a camera frame and returns normalized features.
     *
     * @param bitmap  The camera frame (any size — MediaPipe handles resizing).
     * @param isFrontCamera  If true, mirrors the image horizontally so landmarks
     *                       match a natural (non-mirrored) hand orientation.
     * @return 63-dim normalized feature vector, or null if no hand was detected.
     */
    fun detectAndNormalize(bitmap: Bitmap, isFrontCamera: Boolean = true): FloatArray? {
        val processedBitmap = if (isFrontCamera) {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(processedBitmap).build()
        val result: HandLandmarkerResult = handLandmarker.detect(mpImage)

        if (result.landmarks().isEmpty()) return null

        return normalizeLandmarks(result)
    }

    /**
     * Detects hand landmarks and returns both the normalized feature vector
     * AND the raw landmark positions for drawing an overlay.
     *
     * @return Pair of (63-dim features, 21 raw (x, y, z) triples in 0..1 space),
     *         or null if no hand was detected.
     */
    fun detectAndNormalizeWithLandmarks(
        bitmap: Bitmap,
        isFrontCamera: Boolean = true,
    ): Pair<FloatArray, List<Triple<Float, Float, Float>>>? {
        val processedBitmap = if (isFrontCamera) {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(processedBitmap).build()
        val result: HandLandmarkerResult = handLandmarker.detect(mpImage)

        if (result.landmarks().isEmpty()) return null

        val features = normalizeLandmarks(result)

        // Raw landmarks in MediaPipe's normalized 0..1 coordinate space
        val rawLandmarks = result.landmarks()[0].map { lm ->
            Triple(lm.x(), lm.y(), lm.z())
        }

        return Pair(features, rawLandmarks)
    }

    /**
     * Two-hand variant used by word-sign modules (Greetings, Responses, ...).
     *
     * Returns a 126-dim vector: two 63-dim hand blocks laid end to end.
     * A hand that wasn't detected contributes 63 zeros, so one-handed and
     * two-handed signs share one tensor shape.
     *
     * ### Slot assignment
     * - one hand detected  -> slot 0, slot 1 zeroed
     * - two hands detected -> slot 0 is the hand with the smaller wrist x
     *   (leftmost in the already-mirrored image), slot 1 is the other
     *
     * Ordering is purely geometric on purpose. MediaPipe's handedness label
     * is unreliable here because the frame is mirrored before detection,
     * and a flipped label would scramble the two blocks.
     *
     * ### Per-hand encoding (63 floats)
     * Same wrist-relative, scale-normalized encoding as [normalizeLandmarks],
     * with one deliberate difference: slot 0 of each hand (which would
     * otherwise always be (0,0,0) after subtracting the wrist from itself)
     * carries the **raw wrist position** in MediaPipe's 0..1 image space.
     *
     * That single slot is what preserves the sign's trajectory. Without it
     * every frame is re-centred on its own wrist and a wave, a sweep and a
     * circle all collapse to "open palm held still" — which is exactly the
     * information these signs are made of.
     *
     * @return Pair of (126-dim features, raw landmarks per detected hand for
     *         the overlay), or null if no hand was found.
     */
    fun detectTwoHandsWithLandmarks(
        bitmap: Bitmap,
        isFrontCamera: Boolean = true,
    ): Pair<FloatArray, List<List<Triple<Float, Float, Float>>>>? {
        val processedBitmap = if (isFrontCamera) {
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(processedBitmap).build()
        val result: HandLandmarkerResult = handLandmarker.detect(mpImage)

        if (result.landmarks().isEmpty()) return null

        // Raw landmarks per hand, in MediaPipe's 0..1 space.
        val hands = result.landmarks().map { hand ->
            hand.map { lm -> Triple(lm.x(), lm.y(), lm.z()) }
        }

        // Deterministic slot order: leftmost wrist first.
        val ordered = if (hands.size >= 2) {
            hands.sortedBy { it[0].first }.take(2)
        } else {
            hands
        }

        val features = FloatArray(TWO_HAND_FEATURES) // zero-filled
        for ((slot, hand) in ordered.withIndex()) {
            encodeHand(hand, features, slot * SINGLE_HAND_FEATURES)
        }

        return Pair(features, ordered)
    }

    /**
     * Writes one hand's 63 floats into [out] starting at [offset].
     *
     * @see detectTwoHandsWithLandmarks for why index 0 holds the raw wrist.
     */
    private fun encodeHand(
        hand: List<Triple<Float, Float, Float>>,
        out: FloatArray,
        offset: Int,
    ) {
        val wristX = hand[0].first
        val wristY = hand[0].second
        val wristZ = hand[0].third

        val coords = Array(21) { i ->
            floatArrayOf(
                hand[i].first - wristX,
                hand[i].second - wristY,
                hand[i].third - wristZ,
            )
        }

        val mcp = coords[9]
        val handSize = sqrt(mcp[0] * mcp[0] + mcp[1] * mcp[1] + mcp[2] * mcp[2])
        if (handSize > 0f) {
            for (c in coords) {
                c[0] /= handSize
                c[1] /= handSize
                c[2] /= handSize
            }
        }

        // Index 0 would be (0,0,0) — reuse it for the raw wrist position
        // so the model can see where the hand is, not just its shape.
        coords[0][0] = wristX
        coords[0][1] = wristY
        coords[0][2] = wristZ

        for (i in 0 until 21) {
            out[offset + i * 3 + 0] = coords[i][0]
            out[offset + i * 3 + 1] = coords[i][1]
            out[offset + i * 3 + 2] = coords[i][2]
        }
    }

    /**
     * Normalizes raw MediaPipe landmarks into the 63-dim feature vector.
     *
     * Matches the Python training pipeline exactly:
     *   coords -= wrist
     *   coords /= ||coords[9]||   (distance to middle finger MCP)
     *   flatten to [x0, y0, z0, x1, y1, z1, ..., x20, y20, z20]
     */
    private fun normalizeLandmarks(result: HandLandmarkerResult): FloatArray {
        val landmarks = result.landmarks()[0] // first (only) hand

        // Raw coordinates: shape (21, 3)
        val coords = Array(21) { i ->
            floatArrayOf(
                landmarks[i].x(),
                landmarks[i].y(),
                landmarks[i].z(),
            )
        }

        // 1. Make relative to wrist (landmark 0)
        val wrist = coords[0].copyOf()
        for (i in coords.indices) {
            coords[i][0] -= wrist[0]
            coords[i][1] -= wrist[1]
            coords[i][2] -= wrist[2]
        }

        // 2. Scale by hand size (wrist → middle finger MCP, landmark 9)
        val mcp = coords[9]
        val handSize = sqrt(
            mcp[0] * mcp[0] + mcp[1] * mcp[1] + mcp[2] * mcp[2]
        )
        if (handSize > 0f) {
            for (i in coords.indices) {
                coords[i][0] /= handSize
                coords[i][1] /= handSize
                coords[i][2] /= handSize
            }
        }

        // 3. Flatten to 63-dim vector
        val features = FloatArray(63)
        for (i in coords.indices) {
            features[i * 3 + 0] = coords[i][0]
            features[i * 3 + 1] = coords[i][1]
            features[i * 3 + 2] = coords[i][2]
        }

        return features
    }

    fun close() {
        handLandmarker.close()
    }

    companion object {
        const val SINGLE_HAND_FEATURES = 63
        const val TWO_HAND_FEATURES = 126
    }
}
