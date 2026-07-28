package com.example.kinetixfsl.detection

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads a static-sign TFLite model from assets and classifies a 63-dim
 * landmark feature vector into one of its trained labels.
 *
 * Each learning module ships its own model rather than sharing one big
 * classifier — FSL number handshapes collide with several letters (2/V,
 * 5/open-B), and a per-module model means the practice screen can only ever
 * predict a sign that belongs to the module the learner is actually in.
 *
 * Use the [forCategory] factory to get the right model for a module id.
 *
 * Thread-safe: the [Interpreter] is called under a synchronized lock.
 * Create one instance and reuse it for the lifetime of the camera session.
 */
class SignClassifier(
    context: Context,
    modelAsset: String = ALPHABET_MODEL,
    labelsAsset: String = ALPHABET_LABELS,
) {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = loadModel(context, modelAsset)
        interpreter = Interpreter(model)
        labels = loadLabels(context, labelsAsset)
    }

    /**
     * Result of a single classification.
     *
     * @param label      Predicted sign (e.g. "A", "NG", "7", "NONE").
     * @param confidence Softmax probability, 0.0–1.0.
     */
    data class Result(
        val label: String,
        val confidence: Float,
    )

    /**
     * Classifies a normalized 63-dim feature vector.
     *
     * @param features Exactly 63 floats: 21 landmarks × (x, y, z),
     *                 wrist-relative and scale-normalized — same
     *                 preprocessing as the Python training script.
     * @return The top prediction with its confidence.
     */
    fun classify(features: FloatArray): Result {
        require(features.size == 63) {
            "Expected 63 features, got ${features.size}"
        }

        val input = arrayOf(features)
        val output = Array(1) { FloatArray(labels.size) }

        synchronized(this) {
            interpreter.run(input, output)
        }

        val probabilities = output[0]
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        return Result(
            label = labels[maxIdx],
            confidence = probabilities[maxIdx],
        )
    }

    fun close() {
        interpreter.close()
    }

    // ── Asset loaders ───────────────────────────────────────

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
     * Explicit UTF-8 and `trim()` guard against non-ASCII labels and
     * Windows CRLF line endings, either of which would make a label
     * fail to compare equal to its target string.
     */
    private fun loadLabels(context: Context, filename: String): List<String> =
        context.assets.open(filename)
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        const val ALPHABET_MODEL = "fsl_alphabet.tflite"
        const val ALPHABET_LABELS = "fsl_alphabet_labels.txt"

        const val NUMBERS_MODEL = "fsl_numbers.tflite"
        const val NUMBERS_LABELS = "fsl_numbers_labels.txt"

        /**
         * Builds the classifier for a module id from [FslSignData].
         *
         * Unknown categories fall back to the alphabet model — the word-sign
         * categories are all dynamic and never reach this code path today,
         * but a future static word module would need its own entry here.
         */
        fun forCategory(context: Context, categoryId: String): SignClassifier =
            when (categoryId) {
                "numbers" -> SignClassifier(context, NUMBERS_MODEL, NUMBERS_LABELS)
                else -> SignClassifier(context, ALPHABET_MODEL, ALPHABET_LABELS)
            }
    }
}