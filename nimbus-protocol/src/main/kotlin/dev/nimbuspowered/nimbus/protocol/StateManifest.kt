package dev.nimbuspowered.nimbus.protocol

import kotlinx.serialization.Serializable

/**
 * Manifest of a service's on-disk state. Paths are relative to the service root
 * (e.g. `services/state/Lobby-1/`), always use `/` as separator regardless of OS,
 * and never contain `..` components.
 *
 * Used by the agent-to-controller state sync protocol:
 *   - Agent GETs controller's manifest before pulling state (to figure out deltas)
 *   - Agent POSTs its own manifest when pushing state back
 *   - Controller validates agent-pushed files match their manifest hashes
 */
@Serializable
data class StateManifest(
    /** Map from relative path → file entry. */
    val files: Map<String, StateFileEntry> = emptyMap()
)

@Serializable
data class StateFileEntry(
    /** Lowercase hex SHA-256 of the file's bytes. */
    val sha256: String,
    /** File size in bytes. */
    val size: Long
)
