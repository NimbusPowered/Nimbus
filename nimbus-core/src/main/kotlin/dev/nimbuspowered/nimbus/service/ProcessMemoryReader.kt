package dev.nimbuspowered.nimbus.service

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.useLines

/**
 * Reads live process memory usage via OS-level APIs. Used to populate the
 * memory fields on [Service] for servers that don't have the Nimbus SDK
 * deployed (modded backends, dedicated services) and therefore never send
 * health reports.
 *
 * Currently Linux-only via `/proc/<pid>/status`. On other operating systems
 * the methods return `null` and callers fall back to the configured memory.
 */
object ProcessMemoryReader {

    /** Resident set size of the process in MB, or `null` if unavailable. */
    fun readRssMb(pid: Long): Long? {
        if (pid <= 0) return null
        val statusFile = Paths.get("/proc/$pid/status")
        if (!statusFile.exists()) return null
        return try {
            var rssKb: Long? = null
            statusFile.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("VmRSS:")) {
                        rssKb = line.substringAfter("VmRSS:")
                            .trim()
                            .removeSuffix("kB")
                            .trim()
                            .toLongOrNull()
                        break
                    }
                }
            }
            rssKb?.let { it / 1024 }
        } catch (_: Exception) {
            null
        }
    }
}
