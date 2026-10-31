plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val allAbis = listOf("armeabi-v7a")

android {
    namespace = "io.github.henry753951.airplayremote"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    if (localProps.containsKey("storeFile")) {
        signingConfigs {
            create("release") {
                storeFile = file(localProps.getProperty("storeFile"))
                storePassword = localProps.getProperty("storePassword")
                keyAlias = localProps.getProperty("keyAlias")
                keyPassword = localProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = providers.gradleProperty("applicationIdOverride").getOrElse("io.github.henry753951.airplaytv")
        minSdk = 33
        targetSdk = 37
        versionCode = providers.gradleProperty("versionCodeOverride").map(String::toInt).getOrElse(15)
        versionName = "1.0.6"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += allAbis }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            ndk { abiFilters += allAbis }
        }
        // debuggable build with HWASan (arm64) + UBSan in native code
        create("sanitize") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake { arguments += "-DSANITIZE=ON" }
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        prefab = true
    }
}

tasks.register("applyUxplayPatches") {
    doLast {
        fun git(component: String, vararg args: String): Pair<Int, String> {
            val proc = ProcessBuilder("git", "-C", "$projectDir/src/main/cpp/third_party/$component", *args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            return Pair(proc.waitFor(), out)
        }
        listOf("UxPlay", "openssl-cmake").forEach { component ->
            val patches = file("src/main/cpp/patches/$component").listFiles { f -> f.extension == "patch" }!!.sorted()
            patches.forEach { patch ->
                // Also builds from source archives, where submodules have no Git metadata.
                if (git(component, "apply", "--reverse", "--check", "--unidiff-zero", patch.absolutePath).first != 0) {
                    val result = git(component, "apply", "--unidiff-zero", patch.absolutePath)
                    check(result.first == 0) { "Cannot apply ${patch.name}: ${result.second}" }
                }
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake")) dependsOn("applyUxplayPatches")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.prefs)
    implementation(libs.androidx.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.media3.transformer)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.bouncycastle)
    implementation(libs.oboe)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}

val checkRemoteLibrary = tasks.register("checkRemoteLibrary") {
    doLast {
        check(file("src/main/jniLibs/armeabi-v7a/libatv_android.so").isFile) {
            "Build the Remote receiver first: scripts/build-remote.sh"
        }
    }
}
tasks.named("preBuild") { dependsOn(checkRemoteLibrary) }

android {
    androidResources {
        localeFilters += listOf("en", "ru", "zh-rTW")
    }
    packaging {
        resources {
            excludes += setOf(
                "org/bouncycastle/pqc/**",
                "org/bouncycastle/x509/**",
                "META-INF/versions/**",
                "kotlin/**",
                "**/*.kotlin_builtins",
                "**/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
    }
}
