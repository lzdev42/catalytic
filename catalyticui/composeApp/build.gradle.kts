import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvmToolchain(21) // Use local Java 21 to match ProGuard 7.7.0 support

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.filekit.dialogs.compose)
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("com.squareup.okio:okio:3.16.4")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // gRPC API
            implementation(project(":grpc-api"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(project(":grpc-api"))
            // Compose Desktop UI 测试
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "io.github.lzdev42.catalyticui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.lzdev42.catalyticui"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "io.github.lzdev42.catalyticui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Catalytic" // User requested specific App Name
            packageVersion = "1.0.0"
            
            macOS {
                bundleID = "io.github.lzdev42.catalyticui"
                // Include Runtime binaries (Service/Engine) from local bundle
                val serviceBundle = project.file("service_bundle")
                appResourcesRootDir.set(serviceBundle)
                println("Configuring macOS resources from: ${serviceBundle.absolutePath}")
            }
        }
        
        buildTypes.release.proguard {
            version.set("7.8.2")
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }

}


tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// gRPC 连接测试任务
tasks.register<JavaExec>("testGrpcConnection") {
    group = "verification"
    description = "测试与 Host 的 gRPC 连接"
    mainClass.set("io.github.lzdev42.catalyticui.GrpcConnectionTestKt")
    classpath = files(
        tasks.named("jvmJar"),
        configurations.named("jvmRuntimeClasspath")
    )
    dependsOn("jvmJar")
}
