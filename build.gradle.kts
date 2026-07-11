import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("fabric-loom") version("1.10-SNAPSHOT")
    kotlin("jvm") version ("2.3.20")
}

group = property("maven_group")!!
version = property("mod_version")!!

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://api.modrinth.com/maven")
    maven("https://maven.fabricmc.net/")
    maven("https://maven.impactdev.net/repository/development/")
    // ModMenu
    maven("https://maven.terraformersmc.com/")
    // Cloth Config
    maven("https://maven.shedaniel.me/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Fabric Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Cobblemon
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // ModMenu
    modImplementation("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // Cloth Config
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")
    include("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to project.version))
        }
    }

    jar {
        from("LICENSE")
    }

    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}