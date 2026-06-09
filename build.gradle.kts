plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "cb.core"
version = "1.1.5"

repositories {
    mavenCentral()
}

// Build and run on JDK 25 for both Kotlin and Java compilation tasks.
kotlin {
    jvmToolchain(25)
}

dependencies {

    // libs.versions.toml dependencies
    implementation(libs.coroutines)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // JUnit (BOM aligns jupiter + platform launcher versions)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.platform.launcher)

    // SQLite JDBC driver for settings system tests
    testImplementation(libs.sqlite)

}

tasks.test {
    useJUnitPlatform {
        excludeTags("slow")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Create a separate test task for slow/performance tests
tasks.register<Test>("slowTests") {
    group = "verification"
    description = "Runs slow integration and performance tests"

    // Wire up the test classes and runtime classpath. Without this a registered
    // Test task has no inputs and reports NO-SOURCE (silently running nothing).
    val testSourceSet = sourceSets.test.get()
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath

    // Include only tests marked with @Tag("slow")
    useJUnitPlatform {
        includeTags("slow")
    }

    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Configure Dokka for HTML documentation
tasks.dokkaHtml {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    
    dokkaSourceSets {
        configureEach {
            includeNonPublic.set(false)
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            
            // Link to JDK documentation
            jdkVersion.set(17)
            
            // Source links
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(uri("https://github.com/your-org/caviar-blue-tools/tree/main/src/main/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

// Configure Dokka for Javadoc (for Maven compatibility)
tasks.dokkaJavadoc {
    outputDirectory.set(layout.buildDirectory.dir("dokka/javadoc"))
}