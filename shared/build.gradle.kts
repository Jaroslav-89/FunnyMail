import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom3)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.compilations.getByName("main") {
            cinterops {
                val commonCrypto by creating {
                    defFile(project.file("src/nativeInterop/cinterop/commonCrypto.def"))
                }
            }
        }

        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.jaroapps.funnymail.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.core.ktx)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.google.id)
            implementation(libs.onnxruntime.android)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.room3.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.jaroapps.funnymail.resources"
}

private const val dexiNedModelUrl =
    "https://huggingface.co/opencv/edge_detection_dexined/resolve/main/edge_detection_dexined_2024sep.onnx"
private const val dexiNedModelSha256 =
    "a50d01dc8481549c7dedb9eb3e0123b810a016520df75e4669a504609982cdd0"
private const val dexiNedModelRelativePath =
    "models/edge_detection_dexined_2024sep.onnx"

val ensureDexiNedModel by tasks.registering {
    val modelFile = rootProject.file(dexiNedModelRelativePath)
    outputs.file(modelFile)
    // Validate the checksum on every build; re-download only if absent or corrupted.
    outputs.upToDateWhen { false }

    doLast {
        fun sha256(file: File): String {
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

        if (modelFile.isFile && modelFile.length() > 40_000_000L &&
            sha256(modelFile).equals(dexiNedModelSha256, ignoreCase = true)
        ) {
            logger.info("Verified bundled DexiNed model: ${modelFile.absolutePath}")
            return@doLast
        }

        modelFile.parentFile.mkdirs()
        val temporary = File(modelFile.parentFile, "${modelFile.name}.download")
        temporary.delete()
        val connection = (URL(dexiNedModelUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FunnyMail-PhotoSketch/1.0")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw GradleException("DexiNed model download failed: HTTP $responseCode")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            }
            if (!temporary.isFile || temporary.length() <= 40_000_000L) {
                throw GradleException("Downloaded DexiNed model is too small or incomplete")
            }
            if (!sha256(temporary).equals(dexiNedModelSha256, ignoreCase = true)) {
                throw GradleException("Downloaded DexiNed model SHA-256 does not match")
            }
            if (modelFile.exists() && !modelFile.delete()) {
                throw GradleException("Cannot replace invalid DexiNed model")
            }
            if (!temporary.renameTo(modelFile)) {
                throw GradleException("Cannot move verified DexiNed model into Android assets")
            }
            logger.lifecycle("DexiNed model verified and ready to be bundled.")
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }
}

// The model lives once in the repository's root models/ directory, shared with the iOS bundle.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory("../models")
    }
}

// Ensure Android asset merging happens only after the bundled model is present and verified.
tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Assets"))) {
        dependsOn(ensureDexiNedModel)
    }
}
