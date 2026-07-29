package com.example.kinetixfsl.detection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Classifies dynamic FSL signs (J, Z, Ñ, NG) using a sequence of
 * landmark frames fed to a 1D-CNN model.
 *
 * ## Early classification
 *
 * Instead of waiting for all [SEQUENCE_LENGTH] frames, the classifier
 * starts attempting classification once [MIN_FRAMES_FOR_EARLY] frames
 * have been buffered. Remaining slots are zero-padded (same as the
 * training script's `pad_or_truncate`). Each new frame triggers a
 * fresh attempt, so the user gets confirmed as soon as the motion is
 * recognizable — typically well before the full 30 frames.
 *
 * If the buffer fills completely without a confident match the caller
 * should reset and let the user try again.
 */
class DynamicSignClassifier(
    context: Context,
    modelAsset: String = LETTERS_MODEL,
    labelsAsset: String = LETTERS_LABELS,
    /**
     * Floats per frame. 63 for the one-handed letter model (J, Z, Ñ, NG),
     * 126 for the two-handed word-sign models. Must match the second
     * dimension the .tflite was trained with, or `interpreter.run` throws.
     */
    val numFeatures: Int = SINGLE_HAND_FEATURES,
) {

    private val interpreter: Interpreter
    private val labels: List<String>

    private val buffer = ArrayList<FloatArray>(SEQUENCE_LENGTH)
    private val zeroFrame = FloatArray(numFeatures)

    init {
        val model = loadModel(context, modelAsset)
        interpreter = Interpreter(model)
        labels = loadLabels(context, labelsAsset)
    }

    data class Result(
        val label: String,
        val confidence: Float,
    )

    /** True once enough frames are buffered to start classifying. */
    val canClassify: Boolean get() = buffer.size >= MIN_FRAMES_FOR_EARLY

    /** True when the buffer is completely full. */
    val isFull: Boolean get() = buffer.size >= SEQUENCE_LENGTH

    val frameCount: Int get() = buffer.size
    val progress: Float get() = buffer.size.toFloat() / SEQUENCE_LENGTH

    /**
     * Adds a frame. Keeps accepting frames until the buffer is full.
     */
    fun addFrame(features: FloatArray) {
        require(features.size == numFeatures) {
            "Expected $numFeatures features, got ${features.size}"
        }
        if (buffer.size < SEQUENCE_LENGTH) {
            buffer.add(features)
        }
    }

    /**
     * Classifies the current buffer contents.
     *
     * If fewer than [SEQUENCE_LENGTH] frames are present, the remaining
     * slots are zero-padded — this mirrors the training augmentation
     * where short recordings were padded the same way.
     *
     * Can be called as soon as [canClassify] is true, and again after
     * each subsequent [addFrame].
     */
    fun classify(): Result {
        check(canClassify) {
            "Need at least $MIN_FRAMES_FOR_EARLY frames, have ${buffer.size}"
        }

        // Build the (1, SEQUENCE_LENGTH, numFeatures) input tensor.
        // Frames beyond buffer.size are left as zeros (padding).
        val input = Array(1) {
            Array(SEQUENCE_LENGTH) { i ->
                if (i < buffer.size) buffer[i]
                else zeroFrame
            }
        }
        val output = Array(1) { FloatArray(labels.size) }

        synchronized(this) {
            interpreter.run(input, output)
        }

        val probs = output[0]
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0

        Log.d(TAG, "classify() frames=${buffer.size}/$SEQUENCE_LENGTH " +
                "-> ${labels[maxIdx]} ${(probs[maxIdx] * 100).toInt()}%")

        return Result(
            label = labels[maxIdx],
            confidence = probs[maxIdx],
        )
    }

    fun reset() {
        buffer.clear()
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModel(context: Context, filename: String): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )
    }

    /**
     * Reads one label per line.
     *
     * Explicit UTF-8 is required: the label list contains "Ñ", and the
     * platform default charset is not guaranteed to decode it.
     * `trim()` is required because the file may be saved with Windows
     * CRLF endings — an untrimmed "Z\r" never equals the target "Z",
     * so every match silently fails.
     */
    private fun loadLabels(context: Context, filename: String): List<String> =
        context.assets.open(filename)
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        private const val TAG = "DynamicClassifier"

        const val SEQUENCE_LENGTH = 30   // must match training

        const val SINGLE_HAND_FEATURES = 63
        const val TWO_HAND_FEATURES = 126

        /**
         * Minimum frames before the first classification attempt.
         * At 5 fps capture, 15 frames ≈ 3 seconds of motion —
         * enough for the model to see the core gesture.
         */
        const val MIN_FRAMES_FOR_EARLY = 15

        // One-handed dynamic letters: J, Z, Ñ, NG (+ NONE).
        const val LETTERS_MODEL = "fsl_dynamic.tflite"
        const val LETTERS_LABELS = "fsl_dynamic_labels.txt"

        // Two-handed word signs, one model per module.
        const val GREETINGS_MODEL = "fsl_greetings.tflite"
        const val GREETINGS_LABELS = "fsl_greetings_labels.txt"

        // Basic Responses: Oo, Hindi, Hintay, Sige.
        const val RESPONSES_MODEL = "fsl_responses.tflite"
        const val RESPONSES_LABELS = "fsl_responses_labels.txt"

        private data class Spec(
            val model: String,
            val labels: String,
            val features: Int,
        )

        /**
         * Single source of truth for which model a module uses.
         *
         * [isTwoHanded] and [forCategory] must never disagree: the screen
         * asks the first which extractor to run, and the second builds the
         * classifier that consumes it. If one said 126 and the other built
         * a 63-dim model, `addFrame` would throw on the very first frame,
         * inside the camera analyzer callback.
         *
         * Add a branch here as each word-sign module gets its model.
         * Modules without one yet fall back to the one-handed letters
         * model, whose labels (J, Z, Ñ, NG) simply never match a word
         * sign — the screen shows low confidence instead of crashing.
         */
        private fun specFor(categoryId: String): Spec = when (categoryId) {
            "greetings" -> Spec(GREETINGS_MODEL, GREETINGS_LABELS, TWO_HAND_FEATURES)
            "responses" -> Spec(RESPONSES_MODEL, RESPONSES_LABELS, TWO_HAND_FEATURES)
            else -> Spec(LETTERS_MODEL, LETTERS_LABELS, SINGLE_HAND_FEATURES)
        }

        /** True when [categoryId] uses the two-handed (126-dim) encoding. */
        fun isTwoHanded(categoryId: String): Boolean =
            specFor(categoryId).features == TWO_HAND_FEATURES

        /** Builds the dynamic classifier for a module id. */
        fun forCategory(context: Context, categoryId: String): DynamicSignClassifier =
            specFor(categoryId).let {
                DynamicSignClassifier(context, it.model, it.labels, it.features)
            }
    }
}
