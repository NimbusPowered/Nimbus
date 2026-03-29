package dev.nimbus.console.commands

import dev.nimbus.console.Command
import dev.nimbus.console.ConsoleFormatter
import dev.nimbus.service.ServiceManager
import dev.nimbus.service.ServiceRegistry

class ShutdownCommand(
    private val serviceManager: ServiceManager,
    private val registry: ServiceRegistry
) : Command {

    override val name = "shutdown"
    override val description = "Gracefully shut down all services and exit"
    override val usage = "shutdown"

    override suspend fun execute(args: List<String>) {
        val services = registry.getAll()
        if (services.isEmpty()) {
            println(ConsoleFormatter.info("No services running. Shutting down..."))
            return
        }

        println(ConsoleFormatter.warn("Initiating graceful shutdown..."))
        println(ConsoleFormatter.hint("Stopping ${services.size} service(s)..."))

        try {
            serviceManager.stopAll()
            println("${ConsoleFormatter.success("All services stopped.")} ${ConsoleFormatter.hint("Goodbye.")}")
        } catch (e: Exception) {
            println(ConsoleFormatter.error("Error during shutdown: ${e.message}"))
            println(ConsoleFormatter.warn("Forcing exit."))
        }
    }
}
