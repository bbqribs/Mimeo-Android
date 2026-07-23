import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.util.Arrays
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyReleaseSigningInputsTask : DefaultTask() {
    @get:Input
    abstract val keystorePropertiesPresent: Property<Boolean>

    @get:Internal
    abstract val keystorePropertiesPath: Property<String>

    @get:Input
    abstract val keystorePropertiesMalformed: Property<Boolean>

    @get:Input
    abstract val missingPropertyNames: ListProperty<String>

    @get:Internal
    abstract val releaseStoreFilePath: Property<String>

    @get:Input
    abstract val releaseStoreFilePresent: Property<Boolean>

    @get:Internal
    abstract val releaseNotesPath: Property<String>

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
        if (keystorePropertiesMalformed.get()) {
            throw GradleException(
                "Release signing properties are malformed or unreadable. " +
                    "Check keystore.properties without printing its values.",
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

        val signingProperties = Properties()
        try {
            File(keystorePropertiesPath.get()).inputStream().use(signingProperties::load)
        } catch (_: Exception) {
            throw GradleException(
                "Release signing properties are malformed or unreadable. " +
                    "Check keystore.properties without printing its values.",
            )
        }

        val storePassword = signingProperties.getProperty("storePassword").toCharArray()
        val keyPassword = signingProperties.getProperty("keyPassword").toCharArray()
        try {
            val keyStore = try {
                KeyStore.getInstance(File(storeFilePath), storePassword)
            } catch (_: Exception) {
                throw GradleException(
                    "Release signing keystore could not be opened with the configured storePassword.",
                )
            }
            val keyAlias = signingProperties.getProperty("keyAlias")
            if (!keyStore.containsAlias(keyAlias)) {
                throw GradleException("Release signing keyAlias was not found in the configured keystore.")
            }
            val signingKey = try {
                keyStore.getKey(keyAlias, keyPassword)
            } catch (_: Exception) {
                throw GradleException(
                    "Release signing key could not be opened with the configured keyPassword.",
                )
            }
            if (signingKey !is PrivateKey) {
                throw GradleException("Release signing keyAlias does not reference a private key.")
            }
            val signingCertificate = keyStore.getCertificate(keyAlias)
                ?: throw GradleException("Release signing keyAlias has no certificate.")
            val expectedFingerprint = Regex("""SHA-256:\s*`?([0-9a-fA-F]{64})`?""")
                .find(File(releaseNotesPath.get()).readText())
                ?.groupValues
                ?.get(1)
                ?: throw GradleException(
                    "Release signing certificate fingerprint is missing from RELEASE_NOTES.md.",
                )
            val actualFingerprint = MessageDigest.getInstance("SHA-256")
                .digest(signingCertificate.encoded)
                .joinToString("") { "%02x".format(it) }
            if (!actualFingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                throw GradleException(
                    "Release signing certificate does not match the fingerprint recorded in RELEASE_NOTES.md.",
                )
            }
        } finally {
            Arrays.fill(storePassword, '\u0000')
            Arrays.fill(keyPassword, '\u0000')
        }
    }
}

abstract class VerifyUnsignedReleaseConfigurationTask : DefaultTask() {
    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val expectedApplicationId: Property<String>

    @get:Input
    abstract val signingConfigName: Property<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val minifyEnabled: Property<Boolean>

    @get:Input
    abstract val productionMinifyEnabled: Property<Boolean>

    @TaskAction
    fun verify() {
        if (applicationId.get() != expectedApplicationId.get()) {
            throw GradleException("Unsigned release verification must use the isolated unsigned application ID.")
        }
        if (signingConfigName.get().isNotEmpty()) {
            throw GradleException("Unsigned release verification must not select any signing configuration.")
        }
        if (debuggable.get()) {
            throw GradleException("Unsigned release verification must not create a debuggable build.")
        }
        if (minifyEnabled.get() != productionMinifyEnabled.get()) {
            throw GradleException("Unsigned release verification must preserve the production shrinker setting.")
        }
        logger.lifecycle(
            "UNSIGNED RELEASE VERIFICATION: uses an isolated package, has no signing config, " +
                "and is non-production/non-publishable.",
        )
    }
}

abstract class VerifySignedProductionReleaseConfigurationTask : DefaultTask() {
    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val expectedApplicationId: Property<String>

    @get:Input
    abstract val signingConfigName: Property<String>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @TaskAction
    fun verify() {
        if (applicationId.get() != expectedApplicationId.get()) {
            throw GradleException("Signed production release must use the production application ID.")
        }
        if (signingConfigName.get() != "release") {
            throw GradleException("Signed production release must use the release signing configuration.")
        }
        if (debuggable.get()) {
            throw GradleException("Signed production release must not be debuggable.")
        }
        logger.lifecycle(
            "SIGNED PRODUCTION RELEASE VERIFICATION: operator inputs, credentials, alias, " +
                "certificate identity, package, and release variant are valid.",
        )
    }
}

val householdVersionCode = 11
val householdVersionName = "0.4.5"
val productionApplicationId = "com.mimeo.android"
val unsignedReleaseApplicationIdSuffix = ".unsigned"
val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
var releaseKeystorePropertiesMalformed = false
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.isFile) {
        try {
            releaseKeystorePropertiesFile.inputStream().use(::load)
        } catch (_: Exception) {
            releaseKeystorePropertiesMalformed = true
            clear()
        }
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
val releaseSigningReady = releaseKeystorePropertiesFile.isFile &&
    !releaseKeystorePropertiesMalformed &&
    missingReleaseSigningPropertyNames.isEmpty()
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
        applicationId = productionApplicationId
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
        create("unsignedRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = unsignedReleaseApplicationIdSuffix
            versionNameSuffix = "-unsigned"
            signingConfig = null
        }
    }

    sourceSets.getByName("unsignedRelease") {
        kotlin.directories += "src/release/java"
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
    testImplementation("androidx.work:work-testing:2.9.1")
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

val verifyReleaseSigningInputs = tasks.register<VerifyReleaseSigningInputsTask>("verifyReleaseSigningInputs") {
    keystorePropertiesPresent.set(releaseKeystorePropertiesFile.isFile)
    keystorePropertiesPath.set(releaseKeystorePropertiesPath)
    keystorePropertiesMalformed.set(releaseKeystorePropertiesMalformed)
    missingPropertyNames.set(missingReleaseSigningPropertyNames)
    releaseStoreFilePath.set(releaseStoreFileAbsolutePath)
    releaseStoreFilePresent.set(releaseStoreFileAbsolutePath.isNotBlank() && File(releaseStoreFileAbsolutePath).isFile)
    releaseNotesPath.set(rootProject.file("RELEASE_NOTES.md").absolutePath)
}

val releaseBuildType = android.buildTypes.getByName("release")
val unsignedReleaseBuildType = android.buildTypes.getByName("unsignedRelease")

val verifyUnsignedReleaseConfiguration = tasks.register<VerifyUnsignedReleaseConfigurationTask>(
    "verifyUnsignedReleaseConfiguration",
) {
    applicationId.set(productionApplicationId + unsignedReleaseBuildType.applicationIdSuffix.orEmpty())
    expectedApplicationId.set(productionApplicationId + unsignedReleaseApplicationIdSuffix)
    signingConfigName.set(unsignedReleaseBuildType.signingConfig?.name.orEmpty())
    debuggable.set(unsignedReleaseBuildType.isDebuggable)
    minifyEnabled.set(unsignedReleaseBuildType.isMinifyEnabled)
    productionMinifyEnabled.set(releaseBuildType.isMinifyEnabled)
}

tasks.register("verifyUnsignedRelease") {
    group = "verification"
    description = "Runs no-secrets release checks and packages a non-production unsigned APK."
    dependsOn(
        verifyUnsignedReleaseConfiguration,
        "testDebugUnitTest",
        "lintRelease",
        "assembleUnsignedRelease",
    )
    doLast {
        logger.lifecycle(
            "UNSIGNED RELEASE VERIFICATION PASSED: the APK is for CI build verification only; " +
                "it is not production-signed and must not be published, distributed, or installed.",
        )
    }
}

val verifySignedProductionRelease = tasks.register<VerifySignedProductionReleaseConfigurationTask>(
    "verifySignedProductionRelease",
) {
    group = "verification"
    description = "Fails closed unless operator-held production signing inputs and identity are valid."
    dependsOn(verifyReleaseSigningInputs)
    applicationId.set(productionApplicationId + releaseBuildType.applicationIdSuffix.orEmpty())
    expectedApplicationId.set(productionApplicationId)
    signingConfigName.set(releaseBuildType.signingConfig?.name.orEmpty())
    debuggable.set(releaseBuildType.isDebuggable)
}

val assembleSignedProductionRelease = tasks.register("assembleSignedProductionRelease") {
    group = "build"
    description = "Builds the production release only after signed-production verification passes."
    dependsOn("assembleRelease", verifySignedProductionRelease)
    doLast {
        logger.lifecycle("SIGNED PRODUCTION RELEASE ASSEMBLED: operator verification is still required before publication.")
    }
}

tasks.register<Copy>("copyVersionedReleaseApk") {
    dependsOn(assembleSignedProductionRelease)
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.buildDirectory.dir("household-distribution"))
    rename("app-release.apk", "mimeo-android-v$householdVersionName-vc$householdVersionCode-release.apk")
}

val signedReleaseArtifactTaskNames = setOf(
    "assembleRelease",
    "bundleRelease",
    "installRelease",
    "makeApkFromBundleForRelease",
    "packageRelease",
    "packageReleaseBundle",
    "packageReleaseUniversalApk",
    "signReleaseBundle",
    "validateSigningRelease",
    "zipApksForRelease",
)

tasks.configureEach {
    if (name in signedReleaseArtifactTaskNames) {
        dependsOn(verifyReleaseSigningInputs)
    }
}

// Re-install the debug APK after instrumented tests so the app stays on device.
tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy("installDebug")
}
