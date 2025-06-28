plugins {
    kotlin("jvm") version "2.1.21"
}

group = "cb.core"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {

    // libs.versions.toml dependencies
    testImplementation(libs.junit)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Use Junit 5
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}

tasks.test {
    useJUnitPlatform()
}
