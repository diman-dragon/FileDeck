import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appVersion: String = providers.gradleProperty("app.version").get()
val versionParts = appVersion.split(".").map { it.toInt() }
val computedVersionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

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
        minSdk = 30
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
            pickFirsts += listOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
            excludes += listOf("META-INF/versions/**", "META-INF/*.kotlin_module")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.zip4j)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.junrar)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
