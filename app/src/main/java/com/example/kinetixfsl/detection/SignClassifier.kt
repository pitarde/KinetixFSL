package com.example.kinetixfsl.detection

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads `fsl_alphabet.tflite` from assets and classifies a 63-dim
 * landmark feature vector into one of the trained FSL alphabet labels.
 *
 * Thread-safe: the [Interpreter] is called under a synchronized lock.
 * Create one instance and reuse it for the lifetime of the camera session.
 */
class SignClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val model = loadModel(context, "fsl_alphabet.tflite")
        interpreter = Interpreter(model)
        labels = loadLabels(context, "fsl_alphabet_labels.txt")
    }

    /**
     * Result of a single classification.
     *
     * @param label      Predicted letter (e.g. "A", "Ñ", "NG").
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

    private fun loadLabels(context: Context, filename: String): List<String> =
        context.assets.open(filename)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
}