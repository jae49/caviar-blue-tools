plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "cb.core"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {

    // libs.versions.toml dependencies
    testImplementation(libs.junit)
    implementation(libs.coroutines)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Use Junit 5
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
    useJUnitPlatform()
    group = "verification"
    description = "Runs slow integration and performance tests"
    
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