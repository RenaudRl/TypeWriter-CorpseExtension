plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

val omniGuiVersion = "0.15"

group = "btcrenaud"
version = "0.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external/")
    maven("https://jitpack.io")
    ivy {
        name = "omniGuiGitHubReleases"
        url = uri("https://github.com/RenaudRl/Typewriter-OmniGUIExtension/releases/download")
        patternLayout {
            artifact("[revision]/Typewriter-OmniGUIExtension-$omniGuiVersion.[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    implementation("com.typewritermc:engine-core:0.9.0")
    implementation("com.typewritermc:BasicExtension:0.9.0")
    // Entity properties (skin, equipment, pose, glow) and the player fallback entity.
    implementation("com.typewritermc:EntityExtension:0.9.0")
    // ObjectiveEntry for the corpse recovery objective.
    implementation("com.typewritermc:QuestExtension:0.9.0")

    // The loot menu is an OmniGUI menu like every other BTC menu, so it inherits shared chassis,
    // views, extended inventory and the engine's click handling instead of re-implementing them.
    compileOnly("btcrenaud:Typewriter-OmniGUIExtension:v$omniGuiVersion@jar")

    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.github.Tofaa2.EntityLib:api:2.4.11")

    // No compile-time dependency on the model backends: BetterModel, BTC Mob NPC and MythicMobs NPC
    // are reached reflectively through their shared `playAnimation` method, so any of them can be
    // absent at runtime.

    testImplementation(kotlin("test"))
}

typewriter {
    namespace = "btcrenaud"

    extension {
        name = "Corpse"
        shortDescription = "Player corpses on death, with multi-backend model support."
        description =
            "Spawns a corpse at the death location holding the player's inventory and experience. " +
            "Renders through EntityExtension, ModelEngine, BetterModel, BTC Mob NPC or MythicMobs NPC, " +
            "is rendered per viewer, survives restarts, and exposes events, facts and audiences."
        engineVersion = "0.9.0-beta-176"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()

        dependencies {
            dependency("typewritermc", "Basic")
            dependency("typewritermc", "Entity")
            dependency("typewritermc", "Quest")
            dependency(namespace = "renaud", name = "GuiAndDialogs")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
