package dev.nimbus.permissions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PermissionManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var permDir: Path
    private lateinit var manager: PermissionManager

    @BeforeEach
    fun setUp() {
        permDir = tempDir.resolve("permissions")
        manager = PermissionManager(permDir)
    }

    // ── init / ensureDefaultGroup ───────────────────────────────

    @Test
    fun `init creates Default group if none exists`() {
        manager.init()

        val defaultGroup = manager.getDefaultGroup()
        assertNotNull(defaultGroup)
        assertEquals("Default", defaultGroup!!.name)
        assertTrue(defaultGroup.default)
    }

    @Test
    fun `init does not create Default group if groups already exist`() {
        // Pre-create a group TOML file before init
        permDir.toFile().mkdirs()
        val groupFile = permDir.resolve("admin.toml")
        groupFile.toFile().writeText("""
            [group]
            name = "Admin"
            default = false
            prefix = ""
            suffix = ""
            priority = 100

            [group.permissions]
            list = []

            [group.inheritance]
            parents = []
        """.trimIndent())

        manager.init()

        // Should only have the Admin group, no auto-created Default
        assertEquals(1, manager.getAllGroups().size)
        assertEquals("Admin", manager.getAllGroups().first().name)
    }

    // ── createGroup ────────────────────────────────────────────

    @Test
    fun `createGroup adds new group`() {
        manager.init()

        val group = manager.createGroup("VIP")
        assertEquals("VIP", group.name)
        assertFalse(group.default)
        assertNotNull(manager.getGroup("VIP"))
    }

    @Test
    fun `createGroup with duplicate name throws`() {
        manager.init()
        manager.createGroup("Moderator")

        assertThrows<IllegalArgumentException> {
            manager.createGroup("Moderator")
        }
    }

    @Test
    fun `createGroup duplicate is case-insensitive`() {
        manager.init()
        manager.createGroup("Admin")

        assertThrows<IllegalArgumentException> {
            manager.createGroup("admin")
        }
    }

    // ── deleteGroup ────────────────────────────────────────────

    @Test
    fun `deleteGroup removes it`() {
        manager.init()
        manager.createGroup("TempGroup")
        assertNotNull(manager.getGroup("TempGroup"))

        manager.deleteGroup("TempGroup")
        assertNull(manager.getGroup("TempGroup"))
    }

    @Test
    fun `deleteGroup throws for unknown group`() {
        manager.init()
        assertThrows<IllegalArgumentException> {
            manager.deleteGroup("NonExistent")
        }
    }

    @Test
    fun `deleteGroup removes group from players`() {
        manager.init()
        manager.createGroup("VIP")
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.setPlayerGroup(uuid, "Steve", "VIP")

        manager.deleteGroup("VIP")

        val player = manager.getPlayer(uuid)
        assertNotNull(player)
        assertFalse(player!!.groups.any { it.equals("VIP", ignoreCase = true) })
    }

    // ── getGroup ───────────────────────────────────────────────

    @Test
    fun `getGroup returns correct group`() {
        manager.init()
        manager.createGroup("Admin")

        val group = manager.getGroup("Admin")
        assertNotNull(group)
        assertEquals("Admin", group!!.name)
    }

    @Test
    fun `getGroup is case-insensitive`() {
        manager.init()
        manager.createGroup("Admin")

        assertNotNull(manager.getGroup("admin"))
        assertNotNull(manager.getGroup("ADMIN"))
    }

    @Test
    fun `getGroup returns null for unknown`() {
        manager.init()
        assertNull(manager.getGroup("NonExistent"))
    }

    // ── getAllGroups ────────────────────────────────────────────

    @Test
    fun `getAllGroups returns all groups`() {
        manager.init()
        manager.createGroup("Admin")
        manager.createGroup("VIP")

        val groups = manager.getAllGroups()
        // Default + Admin + VIP
        assertEquals(3, groups.size)
        assertTrue(groups.any { it.name == "Default" })
        assertTrue(groups.any { it.name == "Admin" })
        assertTrue(groups.any { it.name == "VIP" })
    }

    // ── addPermission / removePermission ───────────────────────

    @Test
    fun `addPermission modifies group permissions`() {
        manager.init()
        manager.createGroup("Admin")

        manager.addPermission("Admin", "nimbus.cloud.list")
        manager.addPermission("Admin", "nimbus.cloud.start")

        val group = manager.getGroup("Admin")!!
        assertTrue("nimbus.cloud.list" in group.permissions)
        assertTrue("nimbus.cloud.start" in group.permissions)
    }

    @Test
    fun `addPermission does not duplicate`() {
        manager.init()
        manager.createGroup("Admin")

        manager.addPermission("Admin", "nimbus.cloud.list")
        manager.addPermission("Admin", "nimbus.cloud.list")

        val group = manager.getGroup("Admin")!!
        assertEquals(1, group.permissions.count { it == "nimbus.cloud.list" })
    }

    @Test
    fun `removePermission removes from group`() {
        manager.init()
        manager.createGroup("Admin")
        manager.addPermission("Admin", "nimbus.cloud.list")

        manager.removePermission("Admin", "nimbus.cloud.list")

        val group = manager.getGroup("Admin")!!
        assertFalse("nimbus.cloud.list" in group.permissions)
    }

    @Test
    fun `addPermission throws for unknown group`() {
        manager.init()
        assertThrows<IllegalArgumentException> {
            manager.addPermission("NoGroup", "some.perm")
        }
    }

    // ── setDefault ─────────────────────────────────────────────

    @Test
    fun `setDefault marks group as default and clears previous`() {
        manager.init()
        // Default group already exists from init
        manager.createGroup("NewDefault")

        manager.setDefault("NewDefault", true)

        val newDefault = manager.getGroup("NewDefault")!!
        assertTrue(newDefault.default)

        // The old Default group should no longer be default
        val oldDefault = manager.getGroup("Default")!!
        assertFalse(oldDefault.default)
    }

    @Test
    fun `getDefaultGroup returns the default group`() {
        manager.init()

        val defaultGroup = manager.getDefaultGroup()
        assertNotNull(defaultGroup)
        assertTrue(defaultGroup!!.default)
    }

    // ── addParent / removeParent ───────────────────────────────

    @Test
    fun `addParent manages inheritance chain`() {
        manager.init()
        manager.createGroup("Admin")
        manager.createGroup("Moderator")

        manager.addParent("Admin", "Moderator")

        val admin = manager.getGroup("Admin")!!
        assertTrue(admin.parents.any { it.equals("Moderator", ignoreCase = true) })
    }

    @Test
    fun `addParent does not duplicate`() {
        manager.init()
        manager.createGroup("Admin")
        manager.createGroup("Moderator")

        manager.addParent("Admin", "Moderator")
        manager.addParent("Admin", "Moderator")

        val admin = manager.getGroup("Admin")!!
        assertEquals(1, admin.parents.count { it.equals("Moderator", ignoreCase = true) })
    }

    @Test
    fun `addParent throws for unknown parent`() {
        manager.init()
        manager.createGroup("Admin")

        assertThrows<IllegalArgumentException> {
            manager.addParent("Admin", "NonExistent")
        }
    }

    @Test
    fun `removeParent removes from inheritance`() {
        manager.init()
        manager.createGroup("Admin")
        manager.createGroup("Moderator")
        manager.addParent("Admin", "Moderator")

        manager.removeParent("Admin", "Moderator")

        val admin = manager.getGroup("Admin")!!
        assertFalse(admin.parents.any { it.equals("Moderator", ignoreCase = true) })
    }

    // ── Permission Resolution ──────────────────────────────────

    @Test
    fun `getEffectivePermissions with no parents returns own permissions`() {
        manager.init()
        manager.createGroup("Admin")
        manager.addPermission("Admin", "nimbus.admin")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        val perms = manager.getEffectivePermissions(uuid)
        assertTrue("nimbus.admin" in perms)
    }

    @Test
    fun `getEffectivePermissions with parent merges parent permissions`() {
        manager.init()
        manager.createGroup("Moderator")
        manager.addPermission("Moderator", "nimbus.moderate")

        manager.createGroup("Admin")
        manager.addPermission("Admin", "nimbus.admin")
        manager.addParent("Admin", "Moderator")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        val perms = manager.getEffectivePermissions(uuid)
        assertTrue("nimbus.admin" in perms)
        assertTrue("nimbus.moderate" in perms)
    }

    @Test
    fun `getEffectivePermissions with grandparent merges all`() {
        manager.init()

        // C (grandparent)
        manager.createGroup("GroupC")
        manager.addPermission("GroupC", "perm.c")

        // B (parent of A, child of C)
        manager.createGroup("GroupB")
        manager.addPermission("GroupB", "perm.b")
        manager.addParent("GroupB", "GroupC")

        // A (child of B)
        manager.createGroup("GroupA")
        manager.addPermission("GroupA", "perm.a")
        manager.addParent("GroupA", "GroupB")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "GroupA")

        val perms = manager.getEffectivePermissions(uuid)
        assertTrue("perm.a" in perms)
        assertTrue("perm.b" in perms)
        assertTrue("perm.c" in perms)
    }

    @Test
    fun `negated permission overrides positive from parent`() {
        manager.init()

        manager.createGroup("Parent")
        manager.addPermission("Parent", "some.perm")

        manager.createGroup("Child")
        manager.addPermission("Child", "-some.perm")
        manager.addParent("Child", "Parent")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Child")

        val perms = manager.getEffectivePermissions(uuid)
        assertFalse("some.perm" in perms)
    }

    @Test
    fun `getEffectivePermissions includes default group permissions`() {
        manager.init()
        manager.addPermission("Default", "basic.perm")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")

        val perms = manager.getEffectivePermissions(uuid)
        assertTrue("basic.perm" in perms)
    }

    // ── matchesPermission ──────────────────────────────────────

    @Test
    fun `matchesPermission exact match`() {
        val effective = setOf("nimbus.cloud.list")
        assertTrue(PermissionManager.matchesPermission(effective, "nimbus.cloud.list"))
    }

    @Test
    fun `matchesPermission wildcard matches child`() {
        val effective = setOf("nimbus.cloud.*")
        assertTrue(PermissionManager.matchesPermission(effective, "nimbus.cloud.list"))
        assertTrue(PermissionManager.matchesPermission(effective, "nimbus.cloud.start"))
    }

    @Test
    fun `matchesPermission global wildcard`() {
        val effective = setOf("*")
        assertTrue(PermissionManager.matchesPermission(effective, "anything.at.all"))
    }

    @Test
    fun `matchesPermission no match`() {
        val effective = setOf("nimbus.cloud.list")
        assertFalse(PermissionManager.matchesPermission(effective, "nimbus.cloud.start"))
    }

    @Test
    fun `matchesPermission wildcard does not match different prefix`() {
        val effective = setOf("nimbus.cloud.*")
        assertFalse(PermissionManager.matchesPermission(effective, "nimbus.admin.list"))
    }

    @Test
    fun `matchesPermission empty set returns false`() {
        val effective = emptySet<String>()
        assertFalse(PermissionManager.matchesPermission(effective, "some.perm"))
    }

    // ── hasPermission ──────────────────────────────────────────

    @Test
    fun `hasPermission resolves through inheritance`() {
        manager.init()

        manager.createGroup("Base")
        manager.addPermission("Base", "nimbus.cloud.*")

        manager.createGroup("Admin")
        manager.addParent("Admin", "Base")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        assertTrue(manager.hasPermission(uuid, "nimbus.cloud.list"))
        assertTrue(manager.hasPermission(uuid, "nimbus.cloud.start"))
    }

    @Test
    fun `hasPermission returns false for missing permission`() {
        manager.init()
        manager.createGroup("Basic")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Basic")

        assertFalse(manager.hasPermission(uuid, "nimbus.admin.secret"))
    }

    // ── Player Management ──────────────────────────────────────

    @Test
    fun `registerPlayer creates entry`() {
        manager.init()
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        val created = manager.registerPlayer(uuid, "Steve")
        assertTrue(created)

        val player = manager.getPlayer(uuid)
        assertNotNull(player)
        assertEquals("Steve", player!!.name)
    }

    @Test
    fun `registerPlayer returns false if already up to date`() {
        manager.init()
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        manager.registerPlayer(uuid, "Steve")
        val updated = manager.registerPlayer(uuid, "Steve")
        assertFalse(updated)
    }

    @Test
    fun `registerPlayer updates name if changed`() {
        manager.init()
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        manager.registerPlayer(uuid, "Steve")
        val updated = manager.registerPlayer(uuid, "Steve_New")
        assertTrue(updated)
        assertEquals("Steve_New", manager.getPlayer(uuid)!!.name)
    }

    @Test
    fun `setPlayerGroup assigns group`() {
        manager.init()
        manager.createGroup("VIP")
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        manager.setPlayerGroup(uuid, "Steve", "VIP")

        val player = manager.getPlayer(uuid)!!
        assertTrue(player.groups.any { it.equals("VIP", ignoreCase = true) })
    }

    @Test
    fun `setPlayerGroup throws for unknown group`() {
        manager.init()
        assertThrows<IllegalArgumentException> {
            manager.setPlayerGroup("some-uuid", "Steve", "NonExistent")
        }
    }

    @Test
    fun `removePlayerGroup removes assignment`() {
        manager.init()
        manager.createGroup("VIP")
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.setPlayerGroup(uuid, "Steve", "VIP")

        manager.removePlayerGroup(uuid, "VIP")

        val player = manager.getPlayer(uuid)!!
        assertFalse(player.groups.any { it.equals("VIP", ignoreCase = true) })
    }

    @Test
    fun `removePlayerGroup throws for unknown player`() {
        manager.init()
        assertThrows<IllegalArgumentException> {
            manager.removePlayerGroup("unknown-uuid", "Default")
        }
    }

    @Test
    fun `getPlayerByName finds player`() {
        manager.init()
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")

        val result = manager.getPlayerByName("Steve")
        assertNotNull(result)
        assertEquals(uuid, result!!.first)
        assertEquals("Steve", result.second.name)
    }

    @Test
    fun `getPlayerByName is case-insensitive`() {
        manager.init()
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")

        assertNotNull(manager.getPlayerByName("steve"))
        assertNotNull(manager.getPlayerByName("STEVE"))
    }

    @Test
    fun `getPlayerByName returns null for unknown`() {
        manager.init()
        assertNull(manager.getPlayerByName("Nobody"))
    }

    // ── getPlayerDisplay ───────────────────────────────────────

    @Test
    fun `getPlayerDisplay returns highest priority group display`() {
        manager.init()

        manager.createGroup("VIP")
        manager.updateGroupDisplay("VIP", prefix = "[VIP] ", suffix = null, priority = 10)

        manager.createGroup("Admin")
        manager.updateGroupDisplay("Admin", prefix = "[Admin] ", suffix = " (admin)", priority = 100)

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.setPlayerGroup(uuid, "Steve", "VIP")
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        val display = manager.getPlayerDisplay(uuid)
        assertEquals("[Admin] ", display.prefix)
        assertEquals(" (admin)", display.suffix)
        assertEquals("Admin", display.groupName)
        assertEquals(100, display.priority)
    }

    @Test
    fun `getPlayerDisplay falls back to default group`() {
        manager.init()
        manager.updateGroupDisplay("Default", prefix = "[Member] ", suffix = null, priority = 0)

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")

        val display = manager.getPlayerDisplay(uuid)
        assertEquals("[Member] ", display.prefix)
        assertEquals("Default", display.groupName)
    }

    // ── Persistence ────────────────────────────────────────────

    @Test
    fun `reload reads from TOML files`() {
        manager.init()
        manager.createGroup("Admin")
        manager.addPermission("Admin", "nimbus.admin")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.registerPlayer(uuid, "Steve")
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        // Create a fresh manager pointing to the same directory
        val manager2 = PermissionManager(permDir)
        manager2.init()

        assertNotNull(manager2.getGroup("Admin"))
        assertTrue("nimbus.admin" in manager2.getGroup("Admin")!!.permissions)
        assertNotNull(manager2.getPlayer(uuid))
        assertTrue(manager2.getPlayer(uuid)!!.groups.any { it.equals("Admin", ignoreCase = true) })
    }

    @Test
    fun `changes persist after save and reload cycle`() {
        manager.init()
        manager.createGroup("Moderator")
        manager.addPermission("Moderator", "nimbus.moderate")
        manager.addPermission("Moderator", "nimbus.chat.mute")

        manager.createGroup("Admin")
        manager.addPermission("Admin", "nimbus.admin")
        manager.addParent("Admin", "Moderator")

        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        manager.setPlayerGroup(uuid, "Steve", "Admin")

        // Reload from disk
        manager.reload()

        // Verify everything survived
        val admin = manager.getGroup("Admin")!!
        assertTrue("nimbus.admin" in admin.permissions)
        assertTrue(admin.parents.any { it.equals("Moderator", ignoreCase = true) })

        val mod = manager.getGroup("Moderator")!!
        assertTrue("nimbus.moderate" in mod.permissions)
        assertTrue("nimbus.chat.mute" in mod.permissions)

        val player = manager.getPlayer(uuid)!!
        assertTrue(player.groups.any { it.equals("Admin", ignoreCase = true) })

        // Effective permissions should still resolve through inheritance
        val perms = manager.getEffectivePermissions(uuid)
        assertTrue("nimbus.admin" in perms)
        assertTrue("nimbus.moderate" in perms)
    }

    @Test
    fun `deleteGroup removes TOML file from disk`() {
        manager.init()
        manager.createGroup("TempGroup")

        val file = permDir.resolve("tempgroup.toml")
        assertTrue(file.toFile().exists())

        manager.deleteGroup("TempGroup")
        assertFalse(file.toFile().exists())
    }
}
