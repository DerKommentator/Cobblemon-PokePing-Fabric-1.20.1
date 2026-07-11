package de.derkommentator.pokeping.hud

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.systems.RenderSystem
import de.derkommentator.pokeping.PokePing
import de.derkommentator.pokeping.config.ConfigManager
import de.derkommentator.pokeping.config.OverlayPosition
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.CopyOnWriteArrayList

object HudOverlay : HudRenderCallback {
    private val df = DecimalFormat().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        roundingMode = RoundingMode.CEILING
        isGroupingUsed = false
    }
    private val displayEntries = CopyOnWriteArrayList<PokemonDisplayEntry>()
    private val logger = LoggerFactory.getLogger("PokePing")
    @Volatile private var lastBiome: String = ""
    private const val ENTRY_DISTANCE: Int = 20

    data class PokemonDisplayEntry(
        val resourceIdentifier: Identifier,
        val aspects: Set<String>,
        val name: String,
        val translatedName: String,
        val spawnChance: Float
    )

    fun updateDisplay(newEntries: List<PokemonDisplayEntry>, biome: String) {
        val maxEntries = ConfigManager.config.biomeSpawn.maxPokemonDisplayed
        val limited = newEntries.take(maxEntries)

        displayEntries.clear()
        displayEntries.addAll(limited)

        lastBiome = biome
    }

    override fun onHudRender(context: DrawContext, tickCounter: RenderTickCounter) {
        val mc = MinecraftClient.getInstance() ?: return

        if (!ConfigManager.config.biomeSpawn.enabled || mc.player == null) return

        val startX: Int
        var startY: Int
        val width = mc.window.scaledWidth
        val height = mc.window.scaledHeight

        when (ConfigManager.config.biomeSpawn.overlayPosition) {
            OverlayPosition.TOP_LEFT -> {
                startX = 10; startY = 10
            }
            OverlayPosition.TOP_RIGHT -> {
                startX = width - 150; startY = 10
            }
            OverlayPosition.BOTTOM_LEFT -> {
                startX = 10; startY = height - (displayEntries.size * ENTRY_DISTANCE) - 10
            }
            OverlayPosition.BOTTOM_RIGHT -> {
                startX = width - 150; startY = height - (displayEntries.size * ENTRY_DISTANCE) - 10
            }
        }

        if (ConfigManager.config.biomeSpawn.showBiomeName) {
            context.drawText(
                mc.textRenderer,
                Text.translatable("${PokePing.MOD_ID}.hud.biomeTitle", lastBiome),
                startX,
                startY,
                0xFFFFFF,
                true
            )
        } else {
            startY -= 10
        }

        var y = startY
        for (entry in displayEntries) {
            try {
                drawPokemonEntry(context, entry, tickCounter.getTickDelta(true), startX, y)
            } catch (e: Exception) {
                logger.error("Failed to render ${entry.name}", e)
            }
            y += ENTRY_DISTANCE
        }
    }

    private fun drawPokemonEntry(context: DrawContext, entry: PokemonDisplayEntry, tickDelta: Float, x: Int, y: Int) {
        val client = MinecraftClient.getInstance() ?: return

        if (ConfigManager.config.biomeSpawn.modelsEnabled) {
            val rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(10f, 340f, 0F))
            val matrixStack = context.matrices

            RenderSystem.enableBlend()

            matrixStack.push()
            matrixStack.translate(x.toDouble() + 18.0, y.toDouble() + 18.0, 0.0)
            val state = FloatingState()
            try {
                drawProfilePokemon(
                    species = entry.resourceIdentifier,
                    matrixStack = matrixStack,
                    rotation = rotation,
                    state = state,
                    scale = 12f,
                    partialTicks = tickDelta
                )
            } catch (e: Exception) {
                logger.warn("Pokemon ${entry.name} could not be rendered: ${e.message}")
            }

            matrixStack.pop()
        }

        context.drawText(
            client.textRenderer,
            Text.translatable(
                "${PokePing.MOD_ID}.hud.pokemonSpawnProbability",
                entry.translatedName,
                df.format((entry.spawnChance))
            ),
            if (ConfigManager.config.biomeSpawn.modelsEnabled) (x + 40) else x,
            y + 30,
            0xFFFFFF,
            true
        )

        RenderSystem.disableBlend()
    }
}