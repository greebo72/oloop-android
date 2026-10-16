plugins {
    kotlin("jvm")
}

dependencies {
    // Plain YAML parsing; timestamps are kept as strings in Frontmatter.kt.
    implementation("org.yaml:snakeyaml:2.4")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
    // The Android app uses this module, so keep the class files at a level Android tooling reads.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
    // The oloop-spec submodule holds the conformance fixtures.
    systemProperty("oloop.spec", rootProject.file("spec").absolutePath)
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
