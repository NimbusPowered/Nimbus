plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    compileOnly(project(":nimbus-core"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    compileOnly("io.ktor:ktor-server-core:3.1.1")
    compileOnly("io.ktor:ktor-server-content-negotiation:3.1.1")
    compileOnly("io.ktor:ktor-serialization-kotlinx-json:3.1.1")
    compileOnly("org.jetbrains.exposed:exposed-core:0.57.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:0.57.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    // AWS SDK v2 — bundled into module JAR (not in core shadowJar)
    implementation("software.amazon.awssdk:s3:2.26.1")
    implementation("software.amazon.awssdk:apache-client:2.26.1")

    testImplementation(project(":nimbus-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

tasks.jar {
    from(configurations.runtimeClasspath.get()
        .filter { f -> f.name.contains("awssdk") || f.name.contains("apache-") || f.name.contains("httpclient") || f.name.contains("httpcore") || f.name.contains("jackson") }
        .map { f -> if (f.isDirectory) f else zipTree(f) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(21)
}
