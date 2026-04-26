package dev.nimbuspowered.nimbus.module.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class StorageConfigManagerTest {

    @Test
    fun `creates default config file on first init when file does not exist`(@TempDir dir: Path) {
        val mgr = StorageConfigManager(dir)
        mgr.init()

        val configFile = dir.resolve("storage.toml")
        assertTrue(configFile.exists(), "storage.toml should have been created")
        val text = configFile.readText()
        assertTrue(text.contains("enabled = false"))
        assertTrue(text.contains("prefix = \"templates\""))
        assertTrue(text.contains("region = \"us-east-1\""))
    }

    @Test
    fun `init is idempotent — does not overwrite existing config`(@TempDir dir: Path) {
        val mgr = StorageConfigManager(dir)
        mgr.init()

        // Modify the file
        val configFile = dir.resolve("storage.toml")
        configFile.writeText(
            """
            enabled = true
            bucket = "my-bucket"
            region = "eu-west-1"
            prefix = "templates"
            """.trimIndent() + "\n"
        )

        // Init again — should NOT overwrite
        mgr.init()
        val text = configFile.readText()
        assertTrue(text.contains("my-bucket"), "Existing config should not be overwritten by init()")
    }

    @Test
    fun `loads config correctly from TOML file with all fields set`(@TempDir dir: Path) {
        dir.resolve("storage.toml").writeText(
            """
            enabled = true
            endpoint = "https://123.r2.cloudflarestorage.com"
            region = "auto"
            bucket = "nimbus-templates"
            prefix = "mc/templates"
            access_key = "AKID12345"
            secret_key = "s3cr3t"
            path_style = true
            auto_sync_on_start = true
            """.trimIndent() + "\n"
        )

        val mgr = StorageConfigManager(dir)
        mgr.init()
        val config = mgr.config

        assertTrue(config.enabled)
        assertEquals("https://123.r2.cloudflarestorage.com", config.endpoint)
        assertEquals("auto", config.region)
        assertEquals("nimbus-templates", config.bucket)
        assertEquals("mc/templates", config.prefix)
        assertEquals("AKID12345", config.accessKey)
        assertEquals("s3cr3t", config.secretKey)
        assertTrue(config.pathStyle)
        assertTrue(config.autoSyncOnStart)
    }

    @Test
    fun `returns defaults on malformed TOML without throwing`(@TempDir dir: Path) {
        dir.resolve("storage.toml").writeText(
            """
            this is not valid toml !!!
            ====broken====
            """.trimIndent() + "\n"
        )

        val mgr = StorageConfigManager(dir)
        // Should not throw
        assertDoesNotThrow { mgr.init() }

        // Defaults: enabled = false, bucket = ""
        assertFalse(mgr.config.enabled)
        assertEquals("", mgr.config.bucket)
    }

    @Test
    fun `reload picks up changes written after init`(@TempDir dir: Path) {
        val mgr = StorageConfigManager(dir)
        mgr.init()

        assertFalse(mgr.config.enabled)

        dir.resolve("storage.toml").writeText(
            """
            enabled = true
            bucket = "updated-bucket"
            region = "us-east-1"
            prefix = "templates"
            """.trimIndent() + "\n"
        )

        mgr.reload()

        assertTrue(mgr.config.enabled)
        assertEquals("updated-bucket", mgr.config.bucket)
    }

    @Test
    fun `default config values match StorageConfig defaults`(@TempDir dir: Path) {
        val mgr = StorageConfigManager(dir)
        mgr.init()

        val config = mgr.config
        assertEquals("us-east-1", config.region)
        assertEquals("templates", config.prefix)
        assertEquals("", config.endpoint)
        assertEquals("", config.accessKey)
        assertEquals("", config.secretKey)
        assertFalse(config.pathStyle)
        assertFalse(config.autoSyncOnStart)
    }
}
