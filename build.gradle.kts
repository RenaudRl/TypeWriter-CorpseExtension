plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

group = "btcrenaud"
version = "0.5"

base {
    archivesName.set("CorpseExtension")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external/")
    maven("https://jitpack.io")
}


dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("com.typewritermc:EntityExtension:0.9.0")
    implementation("com.typewritermc:QuestExtension:0.9.0")
    compileOnly(project(":Typewriter-OmniGUIExtension"))
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.github.Tofaa2.EntityLib:api:2.4.11")
    testImplementation(kotlin("test"))
}

typewriter {
    namespace = "btcrenaud"

    extension {
        name = "Corpse"
        shortDescription = "Corpse system for TypeWriter"
        description = "Corpse extension providing corpse management for TypeWriter, allowing NPC death handling and corpse interactions."
        engineVersion = "0.9.0-beta-177"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()

        dependencies {
            dependency("typewritermc", "Basic")
            dependency("typewritermc", "Quest")
        }
    }
}

kotlin {
    jvmToolchain(21)
}
