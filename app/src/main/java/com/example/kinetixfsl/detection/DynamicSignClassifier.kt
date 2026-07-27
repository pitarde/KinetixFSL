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
class DynamicSignClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>

    private val buffer = ArrayList<FloatArray>(SEQUENCE_LENGTH)

    init {
        val model = loadModel(context, "fsl_dynamic.tflite")
        interpreter = Interpreter(model)
        labels = loadLabels(context, "fsl_dynamic_labels.txt")
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
        require(features.size == NUM_FEATURES) {
            "Expected $NUM_FEATURES features, got ${features.size}"
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

        // Build the (1, SEQUENCE_LENGTH, 63) input tensor.
        // Frames beyond buffer.size are left as zeros (padding).
        val input = Array(1) {
            Array(SEQUENCE_LENGTH) { i ->
                if (i < buffer.size) buffer[i]
                else ZERO_FRAME
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
        private const val NUM_FEATURES = 63

        /**
         * Minimum frames before the first classification attempt.
         * At 5 fps capture, 15 frames ≈ 3 seconds of motion —
         * enough for the model to see the core gesture.
         */
        const val MIN_FRAMES_FOR_EARLY = 15

        /** Reusable zero-filled frame for padding. */
        private val ZERO_FRAME = FloatArray(NUM_FEATURES)
    }
}
