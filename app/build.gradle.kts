import java.util.Properties
import java.io.FileInputStream
import java.security.MessageDigest

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

// Release signing credentials — keystore.properties is gitignored (as is the
// .keystore itself); a fresh clone without it simply builds an unsigned
// release, which is the correct default for machines without the key.
val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        load(FileInputStream(keystoreFile))
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.noctra.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.noctra.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("SUPABASE_URL") ?: ""}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""}\""
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}


kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.flexbox)
    implementation(libs.androidx.swiperefresh)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.4.0")

    // Ktor
    implementation(libs.ktor.android)

    // Health Connect
    implementation(libs.health.connect.client)

    // Firebase
    implementation(libs.firebase.messaging)

    // Lottie
    implementation(libs.lottie)

    // MPAndroidChart
    implementation(libs.mpandroidchart)

    // WorkManager
    implementation(libs.workmanager)

    // Coroutines
    implementation(libs.coroutines.android)

    implementation(libs.kotlinx.serialization.json)
}

// ─── Release artifact ────────────────────────────────────────────────────────
// After every assembleRelease, produce the publishable pair next to the built
// APK:  Noctra-<version>.apk  and  Noctra-<version>.apk.sha256.
// The updater verifies its downloads against that .sha256 when the release
// carries it — so generating it here (instead of from memory, where it can be
// forgotten and silently disable verification for a whole release cycle) is
// the point of this block.
afterEvaluate {
    val releaseVersion: String = android.defaultConfig.versionName ?: "0"
    val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

    tasks.matching { it.name == "assembleRelease" }.configureEach {
        doLast {
            val apk = releaseApk.get().asFile
            check(apk.exists()) { "app-release.apk not found at $apk" }

            val artifact = File(apk.parentFile, "Noctra-$releaseVersion.apk")
            apk.copyTo(artifact, overwrite = true)

            val digest = MessageDigest.getInstance("SHA-256")
            artifact.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            File("$artifact.sha256").writeText("$hash  ${artifact.name}\n")

            logger.lifecycle("Release artifact: ${artifact.name} (${artifact.length() / (1024 * 1024)} MB)")
            logger.lifecycle("Checksum:         ${artifact.name}.sha256")
        }
    }
}