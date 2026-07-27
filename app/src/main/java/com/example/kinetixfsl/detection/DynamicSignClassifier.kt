package com.example.kinetixfsl.detection

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Classifies dynamic FSL signs (J, Z, Ñ, NG) using a sequence of
 * landmark frames fed to an LSTM model.
 *
 * Buffers [SEQUENCE_LENGTH] frames and classifies the full motion.
 * If the model predicts "NONE", the result is treated as a non-match.
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

    val isReady: Boolean get() = buffer.size >= SEQUENCE_LENGTH
    val frameCount: Int get() = buffer.size
    val progress: Float get() = buffer.size.toFloat() / SEQUENCE_LENGTH

    fun addFrame(features: FloatArray) {
        require(features.size == NUM_FEATURES) {
            "Expected $NUM_FEATURES features, got ${features.size}"
        }
        if (buffer.size < SEQUENCE_LENGTH) {
            buffer.add(features)
        }
    }

    /**
     * Classifies the buffered sequence.
     * Returns the top prediction. If "NONE" is predicted, the label
     * is returned as "NONE" — the caller should treat this as a non-match.
     */
    fun classify(): Result {
        check(isReady) { "Buffer not full. Need $SEQUENCE_LENGTH frames, have ${buffer.size}" }

        val input = Array(1) { Array(SEQUENCE_LENGTH) { i -> buffer[i] } }
        val output = Array(1) { FloatArray(labels.size) }

        synchronized(this) {
            interpreter.run(input, output)
        }

        val probs = output[0]
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
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

    private fun loadLabels(context: Context, filename: String): List<String> =
        context.assets.open(filename)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

    companion object {
        private const val SEQUENCE_LENGTH = 30  // must match training
        private const val NUM_FEATURES = 63
    }
}