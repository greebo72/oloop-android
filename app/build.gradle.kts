import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release signing key. It is never in the repository. The values come from the environment
// (the release workflow) or from keystore.properties in the project root (a local release build,
// the file is ignored by Git):
//
//   storeFile=/path/to/oloop-release.jks
//   storePassword=...
//   keyAlias=oloop
//   keyPassword=...        (the same as storePassword for a PKCS12 keystore)
//
// Without all four values the release build is not signed, and the debug build is unaffected.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("OLOOP_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("OLOOP_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("OLOOP_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("OLOOP_KEY_PASSWORD", "keyPassword")
val canSignRelease = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "io.github.greebo72.oloop.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.greebo72.oloop.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 500
        versionName = "0.5.0"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Renders the Markdown description in a TextView.
    implementation("io.noties.markwon:core:4.6.2")
}
