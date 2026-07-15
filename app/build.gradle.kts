import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyReleaseSigningInputsTask : DefaultTask() {
    @get:Input
    abstract val keystorePropertiesPresent: Property<Boolean>

    @get:Input
    abstract val keystorePropertiesPath: Property<String>

    @get:Input
    abstract val missingPropertyNames: ListProperty<String>

    @get:Input
    abstract val releaseStoreFilePath: Property<String>

    @get:Input
    abstract val releaseStoreFilePresent: Property<Boolean>

    @TaskAction
    fun verify() {
        if (!keystorePropertiesPresent.get() || !File(keystorePropertiesPath.get()).isFile) {
            throw GradleException(
                "Release signing requires keystore.properties in the repo root. " +
                    "Copy keystore.properties.example, keep the real file untracked, " +
                    "and point storeFile at the operator-held release keystore. " +
                    "Debug builds do not require signing secrets.",
            )
        }
        val missingProperties = missingPropertyNames.get()
        if (missingProperties.isNotEmpty()) {
            throw GradleException(
                "Release signing properties are incomplete. Missing keys in keystore.properties: " +
                    missingProperties.joinToString(", "),
            )
        }
        val storeFilePath = releaseStoreFilePath.get()
        if (storeFilePath.isBlank() || !releaseStoreFilePresent.get() || !File(storeFilePath).isFile) {
            throw GradleException(
                "Release signing keystore file was not found. Check the storeFile value in keystore.properties.",
            )
        }
    }
}

val householdVersionCode = 9
val householdVersionName = "0.4.3"
val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.isFile) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningPropertyNames = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
)
val missingReleaseSigningPropertyNames = releaseSigningPropertyNames.filter {
    releaseKeystoreProperties.getProperty(it).isNullOrBlank()
}
val releaseSigningReady = releaseKeystorePropertiesFile.isFile && missingReleaseSigningPropertyNames.isEmpty()
val releaseKeystorePropertiesPath = releaseKeystorePropertiesFile.absolutePath
val releaseStoreFileAbsolutePath = releaseKeystoreProperties
    .getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it).absolutePath }
    .orEmpty()

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.mimeo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mimeo.android"
        minSdk = 26
        targetSdk = 35
        versionCode = householdVersionCode
        versionName = householdVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.media:media:1.7.0")
    ksp("androidx.room:room-compiler:2.8.4")

    // On-device translation for the reader's selection toolbar. Models are
    // downloaded once per language pair (~30 MB) and cached for offline use.
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register<Copy>("copyVersionedReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.buildDirectory.dir("household-distribution"))
    rename("app-release.apk", "mimeo-android-v$householdVersionName-vc$householdVersionCode-release.apk")
}

tasks.register<VerifyReleaseSigningInputsTask>("verifyReleaseSigningInputs") {
    keystorePropertiesPresent.set(releaseKeystorePropertiesFile.isFile)
    keystorePropertiesPath.set(releaseKeystorePropertiesPath)
    missingPropertyNames.set(missingReleaseSigningPropertyNames)
    releaseStoreFilePath.set(releaseStoreFileAbsolutePath)
    releaseStoreFilePresent.set(releaseStoreFileAbsolutePath.isNotBlank() && File(releaseStoreFileAbsolutePath).isFile)
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn("verifyReleaseSigningInputs")
    }
}

// Re-install the debug APK after instrumented tests so the app stays on device.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy("installDebug")
}
