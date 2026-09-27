import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // NOTE (AGP 9+): Kotlin Android support is built into AGP; the
    // 'org.jetbrains.kotlin.android' plugin must NOT be applied anymore.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// KGP 2.x style (kotlinOptions inside android{} is deprecated).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Release signing inputs, resolved once at configuration time (file scope —
// NOT inside android{}: the Android DSL receiver shadows plain `java.*`
// references and breaks script compilation).
// keystore.properties is generated in CI from GitHub Secrets and never
// committed; local builds without the file still work (release APK is then
// left unsigned instead of failing the build).
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    FileInputStream(keystorePropsFile).use { stream -> keystoreProps.load(stream) }
}
// Optional overrides from CI: -PversionNameOverride=1.2.3 (tag v1.2.3
// stripped of the leading 'v'), -PversionCodeOverride=5.
val versionNameOverride = (findProperty("versionNameOverride") as String?)
    ?.takeIf { it.isNotBlank() }
val versionCodeOverride = (findProperty("versionCodeOverride") as String?)
    ?.toIntOrNull()

android {
    namespace = "id.web.izs.sqlclient"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.web.izs.sqlclient"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOverride ?: 1
        versionName = versionNameOverride ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Signed in CI (keystore.properties present). Locally without the
            // file the config has no credentials — Gradle leaves the APK
            // unsigned instead of failing the build.
            if (keystoreProps.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // MariaDB JDBC Driver - 2.4.4 is the latest version compatible with Android's java.sql and regex engine
    implementation("org.mariadb.jdbc:mariadb-java-client:2.4.4")

    // SSH Tunnel (maintained fork of jcraft/jsch)
    implementation("com.github.mwiede:jsch:2.28.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
