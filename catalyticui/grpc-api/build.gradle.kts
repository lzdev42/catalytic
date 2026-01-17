plugins {
    kotlin("jvm")
    alias(libs.plugins.wire)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.wire.runtime)
    api(libs.wire.grpc.client)
}

wire {
    kotlin {
        rpcCallStyle = "suspending"
        rpcRole = "client"
    }
}
