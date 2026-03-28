package dev.nimbus.template

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink

class TemplateManager {

    private val logger = LoggerFactory.getLogger(TemplateManager::class.java)

    // Directories that should be symlinked instead of copied (large, read-only at runtime)
    private val symlinkDirs = setOf("libraries")

    fun prepareService(
        templateName: String,
        serviceName: String,
        templatesDir: Path,
        runningDir: Path
    ): Path {
        val sourceDir = templatesDir.resolve(templateName)
        val targetDir = runningDir.resolve(serviceName)

        require(sourceDir.exists() && sourceDir.isDirectory()) {
            "Template directory does not exist: $sourceDir"
        }

        logger.info("Preparing service '{}' from template '{}' -> {}", serviceName, templateName, targetDir)

        Files.createDirectories(targetDir)

        Files.walk(sourceDir).use { stream ->
            stream.forEach { source ->
                val relativePath = sourceDir.relativize(source)
                val destination = targetDir.resolve(relativePath)

                // Check if this is a top-level directory that should be symlinked
                val topLevelName = relativePath.getName(0).toString()
                if (topLevelName in symlinkDirs && source != sourceDir) {
                    // If we're at the top-level symlink dir itself, create the symlink
                    if (relativePath.nameCount == 1 && Files.isDirectory(source)) {
                        if (!destination.exists() && !destination.isSymbolicLink()) {
                            Files.createSymbolicLink(destination, source.toAbsolutePath())
                            logger.debug("Symlinked {} -> {}", destination, source.toAbsolutePath())
                        }
                    }
                    // Skip all children of symlinked directories (the symlink covers them)
                    return@forEach
                }

                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination)
                } else {
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        logger.info("Template '{}' copied to '{}'", templateName, targetDir)
        return targetDir
    }
}
