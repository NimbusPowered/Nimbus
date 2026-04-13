rootProject.name = "nimbus"

// Core
include("nimbus-core")
include("nimbus-protocol")
include("nimbus-agent")
include("nimbus-cli")

// Plugins (Minecraft server/proxy plugins)
include("nimbus-sdk")
project(":nimbus-sdk").projectDir = file("plugins/sdk")

include("nimbus-bridge")
project(":nimbus-bridge").projectDir = file("plugins/bridge")

include("nimbus-display")
project(":nimbus-display").projectDir = file("plugins/display")

include("nimbus-perms")
project(":nimbus-perms").projectDir = file("plugins/perms")

// Controller Modules
include("nimbus-module-api")
project(":nimbus-module-api").projectDir = file("modules/api")

include("nimbus-module-perms")
project(":nimbus-module-perms").projectDir = file("modules/perms")

include("nimbus-module-display")
project(":nimbus-module-display").projectDir = file("modules/display")

include("nimbus-module-scaling")
project(":nimbus-module-scaling").projectDir = file("modules/scaling")

include("nimbus-module-players")
project(":nimbus-module-players").projectDir = file("modules/players")
