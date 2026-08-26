import com.android.SdkConstants
import com.android.build.api.dsl.ApplicationExtension
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.model.Password
import com.android.tools.build.bundletool.model.SigningConfiguration
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional as Opt
import kotlin.apply
import org.gradle.kotlin.dsl.register

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

configure<ApplicationExtension> {
    dependenciesInfo {
        // Disables dependency metadata when building APKs and Bundles.
        includeInApk = false
        includeInBundle = false
    }

    compileSdk = 37

    defaultConfig {
        applicationId = "org.unifiedpush.distributor.sunup"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "1.3.2"

        // buildConfigField("String", "DEFAULT_API_URL", "\"http://10.0.2.2:8088\"")
        buildConfigField("String", "DEFAULT_API_URL", "\"https://push.services.mozilla.com\"")
        buildConfigField("Boolean", "URGENCY", "false")
        buildConfigField("Boolean", "SUPPORT_MIGRATIONS", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    buildTypes {
        getByName("release") {
            resValue("string", "app_name", "Sunup")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            resValue("string", "app_name", "Sunup-dbg")
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    namespace = "org.unifiedpush.distributor.sunup"
}

dependencies {
    implementation(libs.unifiedpush.distributor)
    implementation(libs.unifiedpush.distributor.base)
    implementation(libs.unifiedpush.distributor.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.appcompat)
    implementation(libs.okhttp)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.ui.tooling.preview.android)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
}

tasks.register<RunBundletoolTask>("reproduceUniversal") {
    group = "build"
    description = "Generate universal .apks from .aab with bundletool"
    dependsOn("bundleRelease")
    aabFile = project.rootDir.resolve("app/build/outputs/bundle/release/app-release.aab")
    universalApks = project.rootDir.resolve("universal.apks")
    signature.set(RunBundletoolTask.Signature.UnsignedOrDebug)
}

tasks.register<RunBundletoolTask>("bundletoolBuildApks") {
    group = "build"
    description = "Generate default and universal .apks from .aab with bundletool"

    val aabPath = System.getenv("AAB") ?: error("AAB not set")
    aabFile = project.rootDir.resolve(aabPath)
    universalApks = project.rootDir.resolve("universal.apks")
    defaultApks = project.rootDir.resolve("app.apks")

    val ks = System.getenv("KS") ?: error("KS not set")
    val ksPass = System.getenv("KS_PASS") ?: error("KS_PASS not set")
    val keyAlias = System.getenv("KEY_ALIAS") ?: error("KEY_ALIAS not set")

    signature.set(RunBundletoolTask.Signature.Signed(ks, ksPass, keyAlias))
}

abstract class RunBundletoolTask : DefaultTask() {
    abstract class Signature {
        object UnsignedOrDebug : Signature()
        data class Signed(
            val ks: String,
            val pass: String,
            val alias: String
        ) : Signature()
    }

    @get:InputFile
    abstract val aabFile: RegularFileProperty

    /**
     * Output for build with mode=default
     */
    @get:Optional
    @get:OutputFile
    abstract val defaultApks: RegularFileProperty

    /**
     * Output for build with mode=universal
     */
    @get:Optional
    @get:OutputFile
    abstract val universalApks: RegularFileProperty

    @get:Internal
    abstract val signature: Property<Signature>

    private fun aapt2Path(): Path {
        val path = if (project.hasProperty("android.aapt2FromMavenOverride")) {
            project.property("android.aapt2FromMavenOverride") as String
        } else {
            val buildToolsVersion = SdkConstants.CURRENT_BUILD_TOOLS_VERSION
            val buildToolsDir = Paths.get(
                project.androidComponents.sdkComponents.sdkDirectory.get().toString(),
                SdkConstants.FD_BUILD_TOOLS,
                buildToolsVersion
            )
            BuildToolInfo.fromStandardDirectoryLayout(
                Revision.parseRevision(buildToolsVersion),
                buildToolsDir
            ).getPath(BuildToolInfo.PathId.AAPT2)
        }
        return project.rootDir.resolve(path).toPath()
    }

    @Suppress("NewApi")
    @TaskAction
    fun generateApks() {
        val aapt2Path = aapt2Path()
        val aabPath = aabFile.get().asFile.toPath()

        println("➡ Compiling $aabPath")
        println("➡ Using $aapt2Path")

        val aapt2Command = Aapt2Command.createFromExecutablePath(aapt2Path)
        val signature = signature.get()
        val signingConfig = if (signature is Signature.Signed) {
            SigningConfiguration.extractFromKeystore(
                Path.of(signature.ks),
                signature.alias,
                Opt.of(
                    Password.createFromStringValue("pass:${signature.pass}")
                ),
                Opt.empty<Password>()
            )
        } else {
            null
        }
        if (universalApks.isPresent) {
            val outputFile = universalApks.get().asFile.toPath()

            BuildApksCommand.builder()
                .setBundlePath(aabFile.get().asFile.toPath())
                .setOutputFile(outputFile)
                .setAapt2Command(aapt2Command)
                .setApkBuildMode(BuildApksCommand.ApkBuildMode.UNIVERSAL)
                .setOverwriteOutput(true)
                .apply {
                    signingConfig?.let {
                        setSigningConfiguration(it)
                    }
                }
                .build()
                .execute()

            println("✅ $outputFile generated")
        }
        if (defaultApks.isPresent) {
            val outputFile = defaultApks.get().asFile.toPath()

            BuildApksCommand.builder()
                .setBundlePath(aabFile.get().asFile.toPath())
                .setOutputFile(outputFile)
                .setAapt2Command(aapt2Command)
                .setApkBuildMode(BuildApksCommand.ApkBuildMode.DEFAULT)
                .setOverwriteOutput(true)
                .apply {
                    signingConfig?.let {
                        setSigningConfiguration(it)
                    }
                }
                .build()
                .execute()

            println("✅ $outputFile generated")
        }
    }
}
