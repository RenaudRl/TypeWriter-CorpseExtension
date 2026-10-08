plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

val omniGuiVersion = "0.15"

group = "btcrenaud"
version = "0.6"

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
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("com.typewritermc:BasicExtension:0.9.0")
    implementation("com.typewritermc:EntityExtension:0.9.0")
    implementation("com.typewritermc:QuestExtension:0.9.0")
    compileOnly(project(":Typewriter-OmniGUIExtension"))
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.github.Tofaa2.EntityLib:api:2.4.11")
    // Pure-logic tests only: paper-api is here for MiniMessage, no server is started.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.test {
    useJUnitPlatform()
}
