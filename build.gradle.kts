plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.serialization") version "2.1.10" apply false
}

allprojects {
    group = "dev.nimbus"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Copy optional plugins + SDK to plugins/ in root for easy distribution
tasks.register("copyOptionalPlugins", Copy::class) {
    dependsOn(":nimbus-signs:shadowJar", ":nimbus-sdk:jar")
    from(project(":nimbus-signs").tasks.named("shadowJar").map { (it as Jar).archiveFile })
    from(project(":nimbus-sdk").tasks.named("jar").map { (it as Jar).archiveFile })
    into(layout.projectDirectory.dir("plugins"))
    rename { fileName ->
        when {
            fileName.startsWith("nimbus-signs") -> "nimbus-signs.jar"
            fileName.startsWith("nimbus-sdk") -> "nimbus-sdk.jar"
            else -> fileName
        }
    }
}

tasks.register("buildAll") {
    dependsOn(":nimbus-core:shadowJar", "copyOptionalPlugins")
    doLast {
        println("Build complete:")
        println("  Core:  nimbus-core/build/libs/nimbus-core-0.1.0-all.jar")
        println("  Signs: plugins/nimbus-signs.jar")
    }
}
