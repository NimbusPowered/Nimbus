package dev.nimbus.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

// ── Permission Tables ───────────────────────────────────────

object PermissionGroups : IntIdTable("permission_groups") {
    val name = varchar("name", 64).uniqueIndex()
    val isDefault = bool("is_default").default(false)
    val prefix = varchar("prefix", 256).default("")
    val suffix = varchar("suffix", 256).default("")
    val priority = integer("priority").default(0)
}

object GroupPermissions : IntIdTable("group_permissions") {
    val groupId = reference("group_id", PermissionGroups)
    val permission = varchar("permission", 256)

    init {
        uniqueIndex(groupId, permission)
    }
}

object GroupParents : IntIdTable("group_parents") {
    val groupId = reference("group_id", PermissionGroups)
    val parentName = varchar("parent_name", 64)

    init {
        uniqueIndex(groupId, parentName)
    }
}

object Players : Table("players") {
    val uuid = varchar("uuid", 36)
    val name = varchar("name", 16)

    override val primaryKey = PrimaryKey(uuid)
}

object PlayerGroups : Table("player_groups") {
    val playerUuid = varchar("player_uuid", 36).references(Players.uuid)
    val groupName = varchar("group_name", 64)

    override val primaryKey = PrimaryKey(playerUuid, groupName)
}

// ── Metrics Tables ──────────────────────────────────────────

object ServiceEvents : Table("service_events") {
    val id = integer("id").autoIncrement()
    val timestamp = varchar("timestamp", 30)
    val eventType = varchar("event_type", 32)
    val serviceName = varchar("service_name", 128)
    val groupName = varchar("group_name", 64).nullable()
    val port = integer("port").nullable()
    val exitCode = integer("exit_code").nullable()
    val restartAttempt = integer("restart_attempt").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, timestamp)
    }
}

object ScalingEvents : Table("scaling_events") {
    val id = integer("id").autoIncrement()
    val timestamp = varchar("timestamp", 30)
    val eventType = varchar("event_type", 16)
    val groupName = varchar("group_name", 64)
    val serviceName = varchar("service_name", 128).nullable()
    val currentInstances = integer("current_instances").nullable()
    val targetInstances = integer("target_instances").nullable()
    val reason = varchar("reason", 512)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, timestamp)
    }
}

object PlayerSessions : Table("player_sessions") {
    val id = integer("id").autoIncrement()
    val playerName = varchar("player_name", 16)
    val serviceName = varchar("service_name", 128)
    val connectedAt = varchar("connected_at", 30)
    val disconnectedAt = varchar("disconnected_at", 30).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, playerName, connectedAt)
    }
}
