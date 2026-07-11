package de.derkommentator.pokeping.config

import com.google.gson.GsonBuilder
import java.io.File

enum class BiomeBucketMode(val value: String, val bucketPercentage: Float) {
    COMMON("common", 94.4f),
    UNCOMMON("uncommon", 5f),
    RARE("rare", 0.5f),
    ULTRARARE("ultra-rare", 0.1f),
    ALL("all", 0f)
}

enum class OverlayPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

enum class DiscordMessageMode(name: String) {
    Text("Text"),
    Embed("Embed"),
}

data class DiscordConfig(
    var enabled: Boolean = false,
    var webhookUrl: String = "",
    var username: String = "PokePing",
    var messageMode: DiscordMessageMode = DiscordMessageMode.Embed
)

data class PokePingConfig(
    var notificationEnabled: Boolean = true,
    var species: List<String> = listOf(),
    val announceOnce: Boolean = true,
    var discord: DiscordConfig = DiscordConfig(),
    var biomeSpawn: PokePingBiomeSpawns = PokePingBiomeSpawns()
)

data class PokePingBiomeSpawns(
    var enabled: Boolean = false,
    var showBiomeName: Boolean = false,
    var modelsEnabled: Boolean = true,
    var bucketMode: BiomeBucketMode = BiomeBucketMode.ALL,
    var overlayPosition: OverlayPosition = OverlayPosition.TOP_LEFT,
    var maxPokemonDisplayed: Int = 6
)

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config/pokeping.json")

    var config: PokePingConfig = PokePingConfig()

    fun load() {
        if (configFile.exists()) {
            config = gson.fromJson(configFile.readText(), PokePingConfig::class.java)
        } else {
            save()
        }
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(config))
//        println(config.toString())
    }

    fun saveLocalConfig(config: PokePingConfig) {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(config))
//        println(config.toString())
    }
}