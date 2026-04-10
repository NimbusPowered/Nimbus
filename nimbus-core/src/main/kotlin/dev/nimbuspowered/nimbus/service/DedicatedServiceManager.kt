package dev.nimbuspowered.nimbus.service

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlOutputConfig
import dev.nimbuspowered.nimbus.config.DedicatedServiceConfig
import dev.nimbuspowered.nimbus.config.ServerSoftware
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText

class DedicatedServiceManager(
    private val dedicatedConfigDir: Path,
    private val servicesBaseDir: Path
) {

    private val logger = LoggerFactory.getLogger(DedicatedServiceManager::class.java)
    private val configs = ConcurrentHashMap<String, DedicatedServiceConfig>()
    private val toml = Toml(outputConfig = TomlOutputConfig())

    /** Returns the directory where a dedicated service's files live. */
    fun getServiceDirectory(name: String): Path = servicesBaseDir.resolve(name).toAbsolutePath()

    /** Creates the service directory if it does not exist. Returns the absolute path. */
    fun ensureServiceDirectory(name: String, software: ServerSoftware? = null): Path {
        val dir = getServiceDirectory(name)
        if (!dir.exists()) {
            dir.createDirectories()
            logger.info("Created dedicated service directory '{}'", dir)
        }
        // Auto-accept EULA for game servers (never for Velocity proxies)
        if (software != null && software != ServerSoftware.VELOCITY) {
            val eulaFile = dir.resolve("eula.txt")
            if (!eulaFile.exists()) {
                eulaFile.writeText("eula=true\n")
                logger.info("Wrote eula.txt to '{}'", dir)
            }
        }
        return dir
    }

    fun loadConfigs(loaded: List<DedicatedServiceConfig>) {
        configs.clear()
        for (config in loaded) {
            val name = config.dedicated.name
            configs[name] = config
            logger.info("Loaded dedicated service '{}'", name)
        }
        logger.info("Loaded {} dedicated service(s)", configs.size)
    }

    fun reloadConfigs(loaded: List<DedicatedServiceConfig>) {
        val incoming = loaded.associateBy { it.dedicated.name }

        val removed = configs.keys - incoming.keys
        for (name in removed) {
            logger.warn("Dedicated service '{}' was removed from configuration — still tracked until restart", name)
        }

        for ((name, config) in incoming) {
            if (configs.containsKey(name)) {
                configs[name] = config
                logger.info("Reloaded dedicated service '{}'", name)
            } else {
                configs[name] = config
                logger.info("Added new dedicated service '{}'", name)
            }
        }
    }

    fun getConfig(name: String): DedicatedServiceConfig? = configs[name]

    fun getAllConfigs(): List<DedicatedServiceConfig> = configs.values.toList()

    fun addConfig(config: DedicatedServiceConfig) {
        configs[config.dedicated.name] = config
    }

    fun removeConfig(name: String): Boolean {
        return configs.remove(name) != null
    }

    fun writeTOML(config: DedicatedServiceConfig) {
        val file = dedicatedConfigDir.resolve("${config.dedicated.name}.toml")
        val content = toml.encodeToString(config)
        file.writeText(content)
        logger.info("Wrote dedicated config to {}", file)
    }

    fun deleteTOML(name: String) {
        val file = dedicatedConfigDir.resolve("$name.toml")
        file.deleteIfExists()
        logger.info("Deleted dedicated config {}", file)
    }
}
