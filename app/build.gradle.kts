import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------- версия
// Единственный источник правды — app.version в gradle.properties.
val appVersion: String = providers.gradleProperty("app.version").get()
val versionParts = appVersion.split(".").map { it.toInt() }
val computedVersionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

// ---------------------------------------------------------------- подпись
// 1) signing/ в репозитории — для debug, CI и установки «для себя» (одинаковый ключ на всех машинах,
//    поэтому новая сборка ставится ПОВЕРХ старой, без удаления);
// 2) keystore.properties (если создан) — личный ключ для Google Play.
fun loadProps(file: File): Properties = Properties().apply {
    if (file.exists()) file.inputStream().use { load(it) }
}

val sideloadProps = loadProps(rootProject.file("signing/signing.properties"))
val storeProps = loadProps(rootProject.file("keystore.properties"))
val hasSideloadKey = rootProject.file("signing/filedeck-sideload.jks").exists() && !sideloadProps.isEmpty
val hasStoreKey = rootProject.file("keystore.properties").exists()

if (!hasSideloadKey) {
    throw GradleException(
        "Нет ключа подписи signing/ (filedeck-sideload.jks и signing.properties). " +
            "Без него APK подпишется случайным ключом и не обновится поверх установленной версии."
    )
}

android {
    namespace = "dev.filedeck.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.filedeck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = appVersion
    }

    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file(sideloadProps.getProperty("storeFile"))
            storeType = sideloadProps.getProperty("storeType")
            storePassword = sideloadProps.getProperty("storePassword")
            keyAlias = sideloadProps.getProperty("keyAlias")
            keyPassword = sideloadProps.getProperty("keyPassword")
        }
        if (hasStoreKey) {
            create("store") {
                storeFile = rootProject.file(storeProps.getProperty("storeFile"))
                storePassword = storeProps.getProperty("storePassword")
                keyAlias = storeProps.getProperty("keyAlias")
                keyPassword = storeProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasStoreKey) "store" else "sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        language { enableSplit = false }
    }

    packaging {
        resources {
            // библиотеки архивации приносят одинаковые служебные файлы — берём первый, чтобы сборка не падала
            pickFirsts += listOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
            excludes += listOf("META-INF/versions/**", "META-INF/*.kotlin_module")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Миниатюры изображений
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")

    // Архиватор: ZIP (пароль AES-256), 7z / TAR / GZ / BZ2 / XZ, RAR (распаковка)
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.junrar:junrar:7.5.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
