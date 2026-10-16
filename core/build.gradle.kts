plugins {
    kotlin("jvm") version "2.4.10"
}

repositories {
    mavenCentral()
}

dependencies {
    // Plain YAML parsing; timestamps are kept as strings in Frontmatter.kt.
    implementation("org.yaml:snakeyaml:2.4")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
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
