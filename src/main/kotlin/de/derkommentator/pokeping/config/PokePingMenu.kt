package de.derkommentator.pokeping.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import de.derkommentator.pokeping.PokePing
import de.derkommentator.pokeping.PokePing.reloadConfig
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class PokePingMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent: Screen ->
            val localConfig = ConfigManager.config

            val builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("${PokePing.MOD_ID}.config.title"))

            val entryBuilder = builder.entryBuilder()
            val general = builder.getOrCreateCategory(Text.translatable("${PokePing.MOD_ID}.config.generalCategoryTitle"))

            general.addEntry(
                entryBuilder.startBooleanToggle(Text.translatable("${PokePing.MOD_ID}.config.modEnabled"), ConfigManager.config.notificationEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer { newIsModEnabled -> localConfig.notificationEnabled = newIsModEnabled}
                    .build()
            )

            general.addEntry(
                entryBuilder.startStrList(
                    Text.translatable("${PokePing.MOD_ID}.config.pokemonList"),
                    localConfig.species.toMutableList()
                )
                    .setDefaultValue(listOf())
                    //.setTooltip(Text.literal("Cobblemon Species Name (https://gitlab.com/cable-mc/cobblemon/-/tree/main/common/src/main/resources/data/cobblemon/species)"))
                    .setSaveConsumer { list -> localConfig.species = list.map { it.lowercase() }}
                    .build()
            )

            val biomeCategory = builder.getOrCreateCategory(Text.translatable("${PokePing.MOD_ID}.config.biomeCategoryTitle"))

            biomeCategory.addEntry(
                entryBuilder.startBooleanToggle(Text.translatable("${PokePing.MOD_ID}.config.enableBiomeSpawn"), ConfigManager.config.biomeSpawn.enabled)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("${PokePing.MOD_ID}.config.showBiomeSpawnTooltip"))
                    .setSaveConsumer { localConfig.biomeSpawn.enabled = it }
                    .build()
            )

            biomeCategory.addEntry(
                entryBuilder.startBooleanToggle(Text.translatable("${PokePing.MOD_ID}.config.showBiomeName"), ConfigManager.config.biomeSpawn.showBiomeName)
                    .setDefaultValue(false)
                    .setSaveConsumer { localConfig.biomeSpawn.showBiomeName = it }
                    .build()
            )

            biomeCategory.addEntry(
                entryBuilder.startEnumSelector(Text.translatable("${PokePing.MOD_ID}.config.biomeBucketMode"),
                    BiomeBucketMode::class.java, ConfigManager.config.biomeSpawn.bucketMode)
                    .setDefaultValue(BiomeBucketMode.ALL)
                    .setSaveConsumer { newMode -> localConfig.biomeSpawn.bucketMode = newMode}
                    .build()
            )

            biomeCategory.addEntry(
                entryBuilder.startBooleanToggle(Text.translatable("${PokePing.MOD_ID}.config.showBiomeOverlay"), ConfigManager.config.biomeSpawn.modelsEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer { localConfig.biomeSpawn.modelsEnabled = it }
                    .build()
            )

            biomeCategory.addEntry(
                entryBuilder.startEnumSelector(Text.translatable("${PokePing.MOD_ID}.config.biomeOverlayPosition"),
                    OverlayPosition::class.java, ConfigManager.config.biomeSpawn.overlayPosition)
                    .setDefaultValue(OverlayPosition.TOP_LEFT)
                    .setSaveConsumer { newPosition -> localConfig.biomeSpawn.overlayPosition = newPosition}
                    .build()
            )

            biomeCategory.addEntry(
                entryBuilder.startIntSlider(Text.translatable("${PokePing.MOD_ID}.config.maxPokemonDisplayed"), ConfigManager.config.biomeSpawn.maxPokemonDisplayed, 0, 6)
                    .setDefaultValue(4)
                    .setSaveConsumer { localConfig.biomeSpawn.maxPokemonDisplayed = it }
                    .build()
            )

            val discord = builder.getOrCreateCategory(Text.translatable("${PokePing.MOD_ID}.config.discordCategoryTitle"))

            discord.addEntry(
                entryBuilder.startBooleanToggle(Text.translatable("${PokePing.MOD_ID}.config.discordEnabled"), ConfigManager.config.discord.enabled)
                    .setDefaultValue(true)
                    .setSaveConsumer { newIsEnabled -> localConfig.discord.enabled = newIsEnabled}
                    .build()
            )

            discord.addEntry(
                entryBuilder.startEnumSelector(Text.translatable("${PokePing.MOD_ID}.config.discordNotificationMode"), DiscordMessageMode::class.java, ConfigManager.config.discord.messageMode)
                    .setDefaultValue(DiscordMessageMode.Embed)
                    .setSaveConsumer { newMode -> localConfig.discord.messageMode = newMode}
                    .build()
            )

            discord.addEntry(
                entryBuilder.startStrField(Text.translatable("${PokePing.MOD_ID}.config.discordWebhookUrl"), ConfigManager.config.discord.webhookUrl)
                    .setDefaultValue("")
                    .setSaveConsumer { newWebhookUrl -> localConfig.discord.webhookUrl = newWebhookUrl}
                    .build()
            )

            discord.addEntry(
                entryBuilder.startStrField(Text.translatable("${PokePing.MOD_ID}.config.discordWebhookUsername"), ConfigManager.config.discord.username)
                    .setDefaultValue("")
                    .setSaveConsumer { newUsername -> localConfig.discord.username = newUsername}
                    .build()
            )

            builder.setSavingRunnable {
                ConfigManager.saveLocalConfig(localConfig)
                reloadConfig()
            }

            builder.build()
        }
    }
}