package dev.kryonix.nimbus.console.commands

import dev.kryonix.nimbus.config.NimbusConfig
import dev.kryonix.nimbus.config.ServerSoftware
import dev.kryonix.nimbus.console.Command
import dev.kryonix.nimbus.console.ConsoleFormatter
import dev.kryonix.nimbus.console.ConsoleFormatter.BOLD
import dev.kryonix.nimbus.console.ConsoleFormatter.CYAN
import dev.kryonix.nimbus.console.ConsoleFormatter.DIM
import dev.kryonix.nimbus.console.ConsoleFormatter.GREEN
import dev.kryonix.nimbus.console.ConsoleFormatter.RED
import dev.kryonix.nimbus.console.ConsoleFormatter.RESET
import dev.kryonix.nimbus.console.ConsoleFormatter.YELLOW
import dev.kryonix.nimbus.console.InteractivePicker
import dev.kryonix.nimbus.console.LiveSearchPicker
import dev.kryonix.nimbus.group.GroupManager
import dev.kryonix.nimbus.service.ServiceRegistry
import dev.kryonix.nimbus.template.PluginSearchClient
import dev.kryonix.nimbus.template.SoftwareResolver
import org.jline.terminal.Terminal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

class PluginsCommand(
    private val config: NimbusConfig,
    private val registry: ServiceRegistry,
    private val groupManager: GroupManager,
    private val softwareResolver: SoftwareResolver,
    private val terminal: Terminal
) : Command {

    override val name = "plugins"
    override val description = "Manage server plugins"
    override val usage = "plugins [list|install|remove|check|search] [plugin] [target]"

    private val templatesDir: Path get() = Path.of(config.paths.templates)
    private val staticDir: Path get() = Path.of(config.paths.services).resolve("static")

    /** All known plugin types that can be installed. */
    enum class PluginType(
        val id: String,
        val displayName: String,
        val fileName: String,
        val embedded: Boolean,
        val proxyOnly: Boolean = false,
        val backendOnly: Boolean = false
    ) {
        SDK("sdk", "Nimbus SDK", "nimbus-sdk.jar", true, backendOnly = true),
        VIA_VERSION("viaversion", "ViaVersion", "ViaVersion-*.jar", false, backendOnly = true),
        VIA_BACKWARDS("viabackwards", "ViaBackwards", "ViaBackwards-*.jar", false, backendOnly = true),
        VIA_REWIND("viarewind", "ViaRewind", "ViaRewind-*.jar", false, backendOnly = true),
        GEYSER("geyser", "Geyser", "Geyser-*.jar", false),
        FLOODGATE("floodgate", "Floodgate", "Floodgate-*.jar", false);

        companion object {
            fun fromId(id: String): PluginType? = entries.find { it.id.equals(id, ignoreCase = true) }
            val ALL_IDS: List<String> = entries.map { it.id }
        }
    }

    override suspend fun execute(args: List<String>) {
        val sub = args.firstOrNull()?.lowercase() ?: "list"
        when (sub) {
            "list", "ls" -> list(args.getOrNull(1))
            "install", "add" -> install(args.getOrNull(1), args.getOrNull(2))
            "remove", "rm" -> remove(args.getOrNull(1), args.getOrNull(2))
            "check" -> check(args.getOrNull(1))
            "search", "find" -> search(args.drop(1))
            else -> {
                println(ConsoleFormatter.error("Unknown subcommand: $sub"))
                println("${DIM}Usage: $usage$RESET")
            }
        }
    }

    // ── List ───────────────────────────────────────────────────

    private fun list(target: String?) {
        if (target == null) {
            listOverview()
            return
        }

        val dir = resolvePluginsDir(target)
        if (dir == null) {
            println(ConsoleFormatter.error("Unknown group or service: $target"))
            return
        }

        val jars = listJars(dir)
        println("${BOLD}Plugins in $CYAN$target$RESET${BOLD}:$RESET")
        println()
        if (jars.isEmpty()) {
            println("  ${DIM}No plugins installed.$RESET")
        } else {
            for (jar in jars) {
                val sizeMb = String.format("%.1f", jar.fileSize() / 1024.0 / 1024.0)
                val matched = PluginType.entries.find { matchesPlugin(jar, it) }
                val tag = if (matched != null) " ${DIM}(${matched.displayName})$RESET" else ""
                println("  ${GREEN}●$RESET ${jar.name}  ${DIM}${sizeMb} MB$RESET$tag")
            }
        }
    }

    private fun listOverview() {
        println("${BOLD}Available plugins:$RESET")
        println()

        val globalDir = templatesDir.resolve("global").resolve("plugins")
        val globalProxyDir = templatesDir.resolve("global_proxy").resolve("plugins")

        for (plugin in PluginType.entries) {
            val inGlobal = globalDir.exists() && findPluginJar(globalDir, plugin) != null
            val inGlobalProxy = globalProxyDir.exists() && findPluginJar(globalProxyDir, plugin) != null
            val installed = inGlobal || inGlobalProxy

            val status = if (installed) "${GREEN}●$RESET" else "${DIM}○$RESET"
            val location = when {
                inGlobal && inGlobalProxy -> "${DIM}(global + global_proxy)$RESET"
                inGlobal -> "${DIM}(global)$RESET"
                inGlobalProxy -> "${DIM}(global_proxy)$RESET"
                else -> ""
            }
            val source = if (plugin.embedded) "${DIM}embedded$RESET" else "${DIM}download$RESET"

            println("  $status ${CYAN}${plugin.id}$RESET  ${plugin.displayName}  $source  $location")
        }

        println()
        println("  ${DIM}Install with: ${CYAN}plugins install <plugin> <target>$RESET")
    }

    // ── Install ────────────────────────────────────────────────

    private suspend fun install(pluginArg: String?, targetArg: String?) {
        val plugin = if (pluginArg != null) {
            PluginType.fromId(pluginArg) ?: run {
                println(ConsoleFormatter.error("Unknown plugin: $pluginArg"))
                println("  ${DIM}Available: ${PluginType.ALL_IDS.joinToString(", ")}$RESET")
                return
            }
        } else {
            // Interactive picker
            val options = PluginType.entries.map { p ->
                InteractivePicker.Option(p.id, p.displayName, if (p.embedded) "embedded" else "download")
            }
            val idx = InteractivePicker.pickOne(terminal, options)
            if (idx == InteractivePicker.BACK) {
                println("${DIM}Cancelled.$RESET")
                return
            }
            PluginType.entries[idx]
        }

        val target = targetArg ?: run {
            println(ConsoleFormatter.error("Missing target. Usage: plugins install ${plugin.id} <group|global>"))
            return
        }

        val pluginsDir = resolvePluginsDir(target)
        if (pluginsDir == null) {
            println(ConsoleFormatter.error("Unknown target: $target"))
            return
        }

        if (!pluginsDir.exists()) pluginsDir.createDirectories()

        // Check if already installed
        if (findPluginJar(pluginsDir, plugin) != null) {
            println("${DIM}${plugin.displayName} is already installed in $target.$RESET")
            return
        }

        val success = if (plugin.embedded) {
            installEmbedded(plugin, pluginsDir)
        } else {
            installDownload(plugin, pluginsDir.parent) // parent = template dir (Via/Geyser expect template root)
        }

        if (success) {
            println(ConsoleFormatter.successLine("Installed ${CYAN}${plugin.displayName}$RESET in $CYAN$target$RESET"))
        } else {
            println(ConsoleFormatter.errorLine("Failed to install ${plugin.displayName}"))
        }
    }

    private fun installEmbedded(plugin: PluginType, pluginsDir: Path): Boolean {
        val resourcePath = "plugins/${plugin.fileName}"
        val resource = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return false

        val targetFile = pluginsDir.resolve(plugin.fileName)
        resource.use { input ->
            Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    private suspend fun installDownload(plugin: PluginType, templateDir: Path): Boolean {
        return when (plugin) {
            PluginType.VIA_VERSION -> softwareResolver.downloadViaPlugin(
                SoftwareResolver.ViaPlugin.VIA_VERSION, templateDir
            )
            PluginType.VIA_BACKWARDS -> softwareResolver.downloadViaPlugin(
                SoftwareResolver.ViaPlugin.VIA_BACKWARDS, templateDir
            )
            PluginType.VIA_REWIND -> softwareResolver.downloadViaPlugin(
                SoftwareResolver.ViaPlugin.VIA_REWIND, templateDir
            )
            PluginType.GEYSER -> softwareResolver.ensureGeyserPlugin(templateDir)
            PluginType.FLOODGATE -> softwareResolver.ensureFloodgatePlugin(templateDir, "spigot")
            else -> false
        }
    }

    // ── Remove ─────────────────────────────────────────────────

    private fun remove(pluginArg: String?, targetArg: String?) {
        if (pluginArg == null || targetArg == null) {
            println(ConsoleFormatter.error("Usage: plugins remove <plugin> <target>"))
            return
        }

        val plugin = PluginType.fromId(pluginArg) ?: run {
            println(ConsoleFormatter.error("Unknown plugin: $pluginArg"))
            return
        }

        val pluginsDir = resolvePluginsDir(targetArg)
        if (pluginsDir == null) {
            println(ConsoleFormatter.error("Unknown target: $targetArg"))
            return
        }

        val jar = findPluginJar(pluginsDir, plugin)
        if (jar == null) {
            println("${DIM}${plugin.displayName} is not installed in $targetArg.$RESET")
            return
        }

        print("${YELLOW}Remove ${jar.name} from $targetArg? [y/N]$RESET ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm != "y" && confirm != "yes") {
            println("${DIM}Cancelled.$RESET")
            return
        }

        Files.deleteIfExists(jar)
        println(ConsoleFormatter.successLine("Removed ${CYAN}${plugin.displayName}$RESET from $CYAN$targetArg$RESET"))
    }

    // ── Check ──────────────────────────────────────────────────

    private fun check(groupArg: String?) {
        val dirsToCheck = mutableListOf<Pair<String, Path>>()

        if (groupArg != null) {
            val dir = resolvePluginsDir(groupArg)
            if (dir == null) {
                println(ConsoleFormatter.error("Unknown group: $groupArg"))
                return
            }
            dirsToCheck.add(groupArg to dir)
        } else {
            // Check global dirs + all group templates
            val globalPlugins = templatesDir.resolve("global").resolve("plugins")
            val globalProxyPlugins = templatesDir.resolve("global_proxy").resolve("plugins")
            if (globalPlugins.exists()) dirsToCheck.add("global" to globalPlugins)
            if (globalProxyPlugins.exists()) dirsToCheck.add("global_proxy" to globalProxyPlugins)

            for (group in groupManager.getAllGroups()) {
                val templateName = group.config.group.template.ifEmpty { group.name }
                val dir = templatesDir.resolve(templateName).resolve("plugins")
                if (dir.exists()) dirsToCheck.add(group.name to dir)
            }
        }

        if (dirsToCheck.isEmpty()) {
            println("${DIM}No plugin directories found.$RESET")
            return
        }

        println("${BOLD}Plugin version check:$RESET")
        println()

        val embeddedPlugins = PluginType.entries.filter { it.embedded }
        var foundAny = false

        for ((label, dir) in dirsToCheck) {
            for (plugin in embeddedPlugins) {
                val jar = findPluginJar(dir, plugin) ?: continue
                foundAny = true

                val resourcePath = "plugins/${plugin.fileName}"
                val resource = javaClass.classLoader.getResourceAsStream(resourcePath)
                if (resource == null) continue

                val embeddedSize = resource.use { it.readBytes().size.toLong() }
                val installedSize = jar.fileSize()

                val status = if (embeddedSize == installedSize) {
                    "${GREEN}up-to-date$RESET"
                } else {
                    "${YELLOW}update available$RESET"
                }

                println("  $CYAN$label$RESET / ${plugin.displayName}: $status")
            }
        }

        if (!foundAny) {
            println("  ${DIM}No Nimbus plugins found in templates.$RESET")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Resolves a target name to its plugins/ directory.
     * Accepts: "global", "global_proxy", a group name, or a static service name.
     */
    private fun resolvePluginsDir(target: String): Path? {
        val lower = target.lowercase()

        if (lower == "global") return templatesDir.resolve("global").resolve("plugins")
        if (lower == "global_proxy") return templatesDir.resolve("global_proxy").resolve("plugins")

        // Check group templates
        val group = groupManager.getGroup(target)
        if (group != null) {
            val templateName = group.config.group.template.ifEmpty { group.name }
            return templatesDir.resolve(templateName).resolve("plugins")
        }

        // Check static services
        val staticServiceDir = staticDir.resolve(target)
        if (staticServiceDir.exists()) {
            return staticServiceDir.resolve("plugins")
        }

        return null
    }

    /** Lists all JAR files in a directory. */
    private fun listJars(dir: Path): List<Path> {
        if (!dir.exists()) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.name.endsWith(".jar") }.sorted().toList()
        }
    }

    /** Finds a plugin JAR in a directory by matching against the plugin's file pattern. */
    private fun findPluginJar(pluginsDir: Path, plugin: PluginType): Path? {
        if (!pluginsDir.exists()) return null
        return Files.list(pluginsDir).use { stream ->
            stream.filter { matchesPlugin(it, plugin) }.findFirst().orElse(null)
        }
    }

    /** Checks if a file path matches a plugin type (exact name or glob pattern). */
    private fun matchesPlugin(file: Path, plugin: PluginType): Boolean {
        val name = file.name
        if (!name.endsWith(".jar")) return false
        val pattern = plugin.fileName
        return if (pattern.contains("*")) {
            val prefix = pattern.substringBefore("*")
            name.startsWith(prefix, ignoreCase = true)
        } else {
            name.equals(pattern, ignoreCase = true)
        }
    }

    // ── Search (Hangar + Modrinth) ───────────────────────────

    private suspend fun search(args: List<String>) {
        // Determine target: first arg or interactive picker
        val target = args.firstOrNull() ?: run {
            val options = mutableListOf(
                InteractivePicker.Option("global", "global", "all backend servers")
            )
            for (group in groupManager.getAllGroups()) {
                if (group.config.group.software != ServerSoftware.VELOCITY) {
                    val ver = group.config.group.version
                    options.add(InteractivePicker.Option(group.name, group.name, ver))
                }
            }
            println("${BOLD}Install target:$RESET")
            val idx = InteractivePicker.pickOne(terminal, options)
            if (idx == InteractivePicker.BACK) {
                println("${DIM}Cancelled.$RESET")
                return
            }
            options[idx].id
        }

        val pluginsDir = resolvePluginsDir(target)
        if (pluginsDir == null) {
            println(ConsoleFormatter.error("Unknown target: $target"))
            return
        }

        // Determine MC version from target
        val mcVersion = resolveMcVersion(target)
        if (mcVersion == null) {
            println(ConsoleFormatter.error("Could not determine Minecraft version for '$target'"))
            return
        }

        val searchClient = PluginSearchClient(softwareResolver.client)
        val initialQuery = if (args.size > 1) args.drop(1).joinToString(" ") else ""

        // Live search
        val selected = LiveSearchPicker.liveSearch(
            terminal = terminal,
            title = "Search plugins ${DIM}($mcVersion)${RESET}",
            initialQuery = initialQuery,
            search = { query -> searchClient.search(query, mcVersion) },
            render = { result ->
                val tag = when (result.source) {
                    PluginSearchClient.PluginSource.HANGAR -> "H"
                    PluginSearchClient.PluginSource.MODRINTH -> "M"
                }
                LiveSearchPicker.SearchLine(
                    sourceTag = tag,
                    name = result.name,
                    author = result.author,
                    downloads = formatDownloads(result.downloads),
                    description = result.description
                )
            }
        )

        if (selected == null) {
            println("${DIM}Cancelled.$RESET")
            return
        }

        // Fetch version details + dependencies
        println("${DIM}Fetching version info for ${selected.name}...$RESET")
        val version = searchClient.fetchVersion(selected, mcVersion)
        if (version == null) {
            println(ConsoleFormatter.error("No compatible version found for ${selected.name} on $mcVersion"))
            return
        }

        // Show details
        println()
        println("  ${BOLD}${selected.name}${RESET} ${DIM}v${version.versionName}${RESET}")
        println("  ${DIM}by ${selected.author} · ${formatDownloads(selected.downloads)} downloads${RESET}")
        if (version.fileSize > 0) {
            println("  ${DIM}File: ${version.fileName} (${PluginSearchClient.formatSize(version.fileSize)} MB)${RESET}")
        }

        val requiredDeps = version.dependencies.filter { it.required }
        if (requiredDeps.isNotEmpty()) {
            println("  ${YELLOW}Dependencies:${RESET}")
            for (dep in requiredDeps) {
                println("    ${DIM}●${RESET} ${dep.name} ${DIM}(required)${RESET}")
            }
        }

        println()
        print("  ${YELLOW}Install to $CYAN$target$YELLOW? [Y/n]$RESET ")
        val confirm = readlnOrNull()?.trim()?.lowercase()
        if (confirm == "n" || confirm == "no") {
            println("${DIM}Cancelled.$RESET")
            return
        }

        // Download main plugin
        if (!pluginsDir.exists()) pluginsDir.createDirectories()
        val file = searchClient.download(version, pluginsDir)
        if (file == null) {
            println(ConsoleFormatter.errorLine("Failed to download ${selected.name}"))
            return
        }
        println(ConsoleFormatter.successLine("Installed ${CYAN}${selected.name}${RESET} ${DIM}(${version.fileName})${RESET}"))

        // Auto-install required dependencies
        for (dep in requiredDeps) {
            print("  ${DIM}Installing dependency: ${dep.name}...$RESET")
            val depFile = searchClient.resolveAndDownloadDependency(dep, mcVersion, pluginsDir)
            if (depFile != null) {
                println("\r${ConsoleFormatter.successLine("  Dependency ${CYAN}${dep.name}${RESET} installed")}")
            } else {
                println("\r${ConsoleFormatter.warnLine("  Could not auto-install ${dep.name} — install manually")}")
            }
        }
    }

    /**
     * Resolves the Minecraft version for a target.
     * For groups: reads from group config.
     * For "global": uses the most common version across backend groups.
     */
    private fun resolveMcVersion(target: String): String? {
        val lower = target.lowercase()

        // Direct group lookup
        val group = groupManager.getGroup(target)
        if (group != null) return group.config.group.version

        // For global targets, find the most common version across backend groups
        if (lower == "global" || lower == "global_proxy") {
            return groupManager.getAllGroups()
                .filter { it.config.group.software != ServerSoftware.VELOCITY }
                .groupBy { it.config.group.version }
                .maxByOrNull { it.value.size }
                ?.key
        }

        return null
    }

    private fun formatDownloads(count: Long): String = when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }

    companion object {
        /** All available target names for tab completion. */
        fun targetCandidates(groupManager: GroupManager, registry: ServiceRegistry): List<String> {
            val targets = mutableListOf("global", "global_proxy")
            targets.addAll(groupManager.getAllGroups().map { it.name })
            targets.addAll(registry.getAll().filter { it.isStatic }.map { it.name })
            return targets
        }
    }
}
