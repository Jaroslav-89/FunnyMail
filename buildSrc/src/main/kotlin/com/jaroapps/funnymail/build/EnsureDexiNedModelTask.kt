package com.jaroapps.funnymail.build

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Ensures the ONNX model exists locally before Android/iOS resource packaging.
 *
 * This is a dedicated task type (rather than a doLast closure in a build script) so its action
 * does not capture the Gradle script instance and remains compatible with configuration cache.
 */
abstract class EnsureDexiNedModelTask : DefaultTask() {
    @get:Input
    abstract val modelUrl: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    // Deliberately not declared as an output: this task must verify the checksum every build.
    // With no declared outputs Gradle runs the task action each time; valid model files are reused.
    @get:Internal
    abstract val modelFile: RegularFileProperty

    @TaskAction
    fun ensureModel() {
        val target = modelFile.get().asFile
        val expected = expectedSha256.get()

        if (isVerifiedModel(target, expected)) {
            logger.info("Verified bundled DexiNed model: ${target.absolutePath}")
            return
        }

        val parent = target.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            throw GradleException("Cannot create DexiNed model directory: ${parent.absolutePath}")
        }

        val temporary = File(parent, "${target.name}.download")
        temporary.delete()
        val connection = (URL(modelUrl.get()).openConnection() as? HttpURLConnection)
            ?: throw GradleException("DexiNed model URL must use HTTP or HTTPS")

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 180_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "FunnyMail-PhotoSketch/1.0")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw GradleException("DexiNed model download failed: HTTP $responseCode")
            }

            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            }

            if (!isVerifiedModel(temporary, expected)) {
                throw GradleException(
                    "Downloaded DexiNed model is incomplete or SHA-256 does not match"
                )
            }

            if (target.exists() && !target.delete()) {
                throw GradleException("Cannot replace invalid DexiNed model: ${target.absolutePath}")
            }
            if (!temporary.renameTo(target)) {
                throw GradleException("Cannot move verified DexiNed model into place")
            }

            logger.lifecycle("DexiNed model verified and ready to be bundled.")
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun isVerifiedModel(file: File, expected: String): Boolean {
        if (!file.isFile || file.length() <= MIN_MODEL_BYTES) return false
        return sha256(file).equals(expected, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val MIN_MODEL_BYTES = 40_000_000L
    }
}
