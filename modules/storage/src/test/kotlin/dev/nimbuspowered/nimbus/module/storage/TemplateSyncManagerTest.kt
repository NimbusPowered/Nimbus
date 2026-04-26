package dev.nimbuspowered.nimbus.module.storage

import dev.nimbuspowered.nimbus.module.storage.driver.StorageDriver
import dev.nimbuspowered.nimbus.module.storage.driver.StorageObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class TemplateSyncManagerTest {

    // -----------------------------------------------------------------------
    // Fake StorageDriver — in-memory map: key -> Pair(etag, content)
    // -----------------------------------------------------------------------
    private inner class FakeStorageDriver : StorageDriver {
        val store = mutableMapOf<String, Pair<String, ByteArray>>()

        override fun listObjects(prefix: String): List<StorageObject> =
            store.entries
                .filter { it.key.startsWith(prefix) }
                .map { (key, v) -> StorageObject(key, v.first, v.second.size.toLong()) }

        override fun putObject(key: String, file: Path): String {
            val bytes = file.toFile().readBytes()
            val etag = TemplateSyncManager.md5Hex(file)
            store[key] = etag to bytes
            return etag
        }

        override fun getObject(key: String, destination: Path) {
            val (_, bytes) = store[key] ?: error("Key not found: $key")
            destination.parent?.createDirectories()
            destination.writeBytes(bytes)
        }

        override fun headObject(key: String): String? = store[key]?.first

        override fun deleteObject(key: String) { store.remove(key) }

        override fun close() {}
    }

    private fun makeManager(driver: StorageDriver, templatesDir: Path): TemplateSyncManager {
        val config = StorageConfig(prefix = "templates")
        return TemplateSyncManager(driver, templatesDir, config)
    }

    // -----------------------------------------------------------------------
    // push tests
    // -----------------------------------------------------------------------

    @Test
    fun `push — uploads new file when no remote ETag exists`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val templateDir = templatesDir.resolve("Lobby").also { it.createDirectories() }
        templateDir.resolve("server.properties").writeText("level-name=world")

        val result = mgr.push("Lobby")

        assertTrue(result.success, "Expected no errors but got: ${result.errors}")
        assertEquals(1, result.uploaded)
        assertEquals(0, result.skipped)
        assertTrue(driver.store.containsKey("templates/Lobby/server.properties"))
    }

    @Test
    fun `push — skips unchanged file when local MD5 equals remote ETag`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val templateDir = templatesDir.resolve("Lobby").also { it.createDirectories() }
        val file = templateDir.resolve("server.properties").also { it.writeText("motd=Hello") }

        // Pre-populate remote with matching ETag
        val etag = TemplateSyncManager.md5Hex(file)
        driver.store["templates/Lobby/server.properties"] = etag to file.toFile().readBytes()

        val result = mgr.push("Lobby")

        assertTrue(result.success)
        assertEquals(0, result.uploaded)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `push — uploads changed file when local MD5 differs from remote ETag`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val templateDir = templatesDir.resolve("Lobby").also { it.createDirectories() }
        val file = templateDir.resolve("server.properties").also { it.writeText("motd=NewValue") }

        // Remote has a stale ETag
        driver.store["templates/Lobby/server.properties"] = "staleEtag000000000000000000000000" to ByteArray(0)

        val result = mgr.push("Lobby")

        assertTrue(result.success)
        assertEquals(1, result.uploaded)
        assertEquals(0, result.skipped)
        // Verify the stored ETag was updated
        assertEquals(TemplateSyncManager.md5Hex(file), driver.store["templates/Lobby/server.properties"]?.first)
    }

    @Test
    fun `push — returns error when local template directory does not exist`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val result = mgr.push("NonExistent")

        assertFalse(result.success)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().contains("not found"))
        assertEquals(0, result.uploaded)
    }

    @Test
    fun `push — uploads multiple files in nested directories`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val templateDir = templatesDir.resolve("BedWars").also { it.createDirectories() }
        templateDir.resolve("server.properties").writeText("level-name=bedwars")
        templateDir.resolve("plugins").createDirectories()
            .resolve("config.yml").writeText("arena: default")

        val result = mgr.push("BedWars")

        assertTrue(result.success)
        assertEquals(2, result.uploaded)
        assertEquals(0, result.skipped)
    }

    // -----------------------------------------------------------------------
    // pull tests
    // -----------------------------------------------------------------------

    @Test
    fun `pull — downloads file when remote ETag differs from local`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val remoteContent = "max-players=20".toByteArray()
        driver.store["templates/Lobby/server.properties"] = "abc123def456abc123def456abc12345" to remoteContent

        val result = mgr.pull("Lobby")

        assertTrue(result.success, "Expected no errors but got: ${result.errors}")
        assertEquals(1, result.downloaded)
        assertEquals(0, result.skipped)

        val localFile = templatesDir.resolve("Lobby/server.properties")
        assertTrue(localFile.toFile().exists())
        assertEquals("max-players=20", localFile.toFile().readText())
    }

    @Test
    fun `pull — skips file when remote ETag matches local MD5`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val templateDir = templatesDir.resolve("Lobby").also { it.createDirectories() }
        val localFile = templateDir.resolve("server.properties").also { it.writeText("max-players=20") }
        val localMd5 = TemplateSyncManager.md5Hex(localFile)

        driver.store["templates/Lobby/server.properties"] = localMd5 to localFile.toFile().readBytes()

        val result = mgr.pull("Lobby")

        assertTrue(result.success)
        assertEquals(0, result.downloaded)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `pull — returns error when no remote objects found`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val result = mgr.pull("Ghost")

        assertFalse(result.success)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().contains("No remote objects found"))
    }

    @Test
    fun `pull — creates parent directories for nested files`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val content = "arena: skyblock".toByteArray()
        driver.store["templates/BedWars/plugins/config.yml"] = "aabbccddeeff00112233445566778899" to content

        val result = mgr.pull("BedWars")

        assertTrue(result.success)
        assertEquals(1, result.downloaded)
        val nestedFile = templatesDir.resolve("BedWars/plugins/config.yml")
        assertTrue(nestedFile.toFile().exists())
    }

    // -----------------------------------------------------------------------
    // listRemote tests
    // -----------------------------------------------------------------------

    @Test
    fun `listRemote — extracts distinct template names from S3 key prefixes`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        driver.store["templates/Lobby/server.properties"] = "aaa" to ByteArray(0)
        driver.store["templates/Lobby/plugins/config.yml"] = "bbb" to ByteArray(0)
        driver.store["templates/BedWars/server.properties"] = "ccc" to ByteArray(0)
        driver.store["templates/SkyWars/server.properties"] = "ddd" to ByteArray(0)

        val names = mgr.listRemote()

        assertEquals(listOf("BedWars", "Lobby", "SkyWars"), names)
    }

    @Test
    fun `listRemote — returns empty list when bucket is empty`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        val names = mgr.listRemote()

        assertTrue(names.isEmpty())
    }

    // -----------------------------------------------------------------------
    // md5Hex tests
    // -----------------------------------------------------------------------

    @Test
    fun `md5Hex — is deterministic for the same file content`(@TempDir dir: Path) {
        val fileA = dir.resolve("a.txt").also { it.writeText("hello nimbus") }
        val fileB = dir.resolve("b.txt").also { it.writeText("hello nimbus") }

        assertEquals(TemplateSyncManager.md5Hex(fileA), TemplateSyncManager.md5Hex(fileB))
    }

    @Test
    fun `md5Hex — produces different hash for different content`(@TempDir dir: Path) {
        val fileA = dir.resolve("a.txt").also { it.writeText("content-alpha") }
        val fileB = dir.resolve("b.txt").also { it.writeText("content-beta") }

        assertNotEquals(TemplateSyncManager.md5Hex(fileA), TemplateSyncManager.md5Hex(fileB))
    }

    @Test
    fun `md5Hex — returns 32-character lowercase hex string`(@TempDir dir: Path) {
        val file = dir.resolve("test.txt").also { it.writeText("nimbus storage") }
        val hash = TemplateSyncManager.md5Hex(file)

        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    // -----------------------------------------------------------------------
    // listLocal tests
    // -----------------------------------------------------------------------

    @Test
    fun `listLocal — returns sorted directory names`(@TempDir templatesDir: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, templatesDir)

        templatesDir.resolve("Lobby").createDirectories()
        templatesDir.resolve("BedWars").createDirectories()
        templatesDir.resolve("SkyWars").createDirectories()

        val names = mgr.listLocal()

        assertEquals(listOf("BedWars", "Lobby", "SkyWars"), names)
    }

    @Test
    fun `listLocal — returns empty list when templates dir does not exist`(@TempDir parent: Path) = runTest {
        val driver = FakeStorageDriver()
        val mgr = makeManager(driver, parent.resolve("nonexistent"))

        val names = mgr.listLocal()

        assertTrue(names.isEmpty())
    }
}
