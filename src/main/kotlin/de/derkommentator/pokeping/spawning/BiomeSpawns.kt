package de.derkommentator.pokeping.spawning

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.prioritizedAreaCalculators
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.spawner
import de.derkommentator.pokeping.config.BiomeBucketMode
import de.derkommentator.pokeping.config.ConfigManager
import de.derkommentator.pokeping.hud.HudOverlay
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2

object BiomeSpawns {
    private val logger = LoggerFactory.getLogger("PokePing")
    private var lastBiome = ""
    private val lastBiomeForPlayer = ConcurrentHashMap<UUID, String>()
    private var executor = Executors.newSingleThreadExecutor()
    private val ALL_BUCKETS = listOf(BiomeBucketMode.COMMON, BiomeBucketMode.UNCOMMON, BiomeBucketMode.RARE, BiomeBucketMode.ULTRARARE)
    private var allSpawns: List<CobblemonSpawnDataLoader.SpawnPoolData> = CobblemonSpawnDataLoader.spawnPools

    private val speciesCache: Map<String, Species> by lazy {
        PokemonSpecies.implemented.associateBy { it.name.lowercase() }
    }

    private var lastQueryTime : Long = 0
    private val lastQueryTimeMap = ConcurrentHashMap<UUID, Long>()
    private const val UPDATE_INTERVAL_MS = 5000L // 5 seconds

    fun register() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newSingleThreadExecutor()
        }
    }

    fun biomeHasTag(world: World, pos: BlockPos, tagString: String): Boolean {
        val raw = tagString.removePrefix("#")
        val id = Identifier.of(raw.lowercase(Locale.ROOT))
        val tagKey = TagKey.of(RegistryKeys.BIOME, id)

        // world.getBiome(pos) liefert ein RegistryEntry<Biome> / Holder<Biome> mit isIn(...)
        val biomeEntry = world.getBiome(pos)
        return biomeEntry.isIn(tagKey)
    }

    fun biomeMatchesAnyTag(world: World, pos: BlockPos, tagStrings: List<String>): Boolean {
        val biomeEntry = world.getBiome(pos)
        return tagStrings.any { rawTag ->
            val id = Identifier.of(rawTag.removePrefix("#").lowercase(Locale.ROOT))
            val tagKey = TagKey.of(RegistryKeys.BIOME, id)
            biomeEntry.isIn(tagKey)
        }
    }

    fun onDedicatedServerTick(client: MinecraftClient) {
        val player = client.player ?: return
        val world = player.world ?: return

        val currentBiome = world.getBiome(player.blockPos).key.get().value.toString()
        val currentTime = System.currentTimeMillis()

        if (currentBiome == lastBiome && (currentTime - lastQueryTime < UPDATE_INTERVAL_MS)) return

        lastBiome = currentBiome
        lastQueryTime = currentTime

        // HUD asynchron updaten
        executor.execute {
            try {
//                val possible = allSpawns.flatMap { pool ->
//                    pool.entries.filter { biomeId ->
//                        biomeId.biomes.any { b -> b == currentBiome || b.endsWith(currentBiome.substringAfterLast(":")) }
//                    }.map { it to pool.bucket }
//                }

                if (allSpawns.isEmpty()) {
                    HudOverlay.updateDisplay(emptyList(), currentBiome)
                    return@execute
                }

                val displayEntries = allSpawns.flatMap { group ->
                    val bucketWeight = BiomeBucketMode.entries.find { it.value == group.bucket }?.bucketPercentage ?: 0f

                    group.entries
                        .filter { spawn ->
                            val biomeMatch = biomeMatchesAnyTag(world, player.blockPos, spawn.biomes)
                            //logger.info("Tag: $biomeMatch ${spawn.biomes} ${spawn.name} $currentBiome")
                            biomeMatch
                        }
                        .map { spawn ->
                            val displayName = spawn.name.replaceFirstChar { it.uppercase() }

                            HudOverlay.PokemonDisplayEntry(
                                resourceIdentifier = Identifier("cobblemon", spawn.name),
                                aspects = emptySet(),
                                name = displayName,
                                spawnChance = (spawn.weight / 100f) * bucketWeight,
                                translatedName = displayName
                            )
                        }
                }.distinctBy { it.name }
                .sortedByDescending { it.spawnChance }

                HudOverlay.updateDisplay(displayEntries, currentBiome)
                logger.info("HUD updated for biome $currentBiome with ${displayEntries.size} Pokémon")

            } catch (e: Exception) {
                logger.error("Failed to update Client Biome", e)
            }
        }
    }

    fun reloadAllSpawns() {
        allSpawns = CobblemonSpawnDataLoader.spawnPools
            .map { pool ->
                pool.copy(
                    entries = pool.entries.filter { spawn ->
                        ConfigManager.config.species.contains(spawn.name)
                    }
                )
            }
            .filter { it.entries.isNotEmpty() }
    }

    fun onSingleplayerTick(player: ServerPlayerEntity, biomeBucketMode: BiomeBucketMode) {
        val uuid = player.uuid
        val world = player.world as? ServerWorld ?: return

        val biomeKey = world.getBiome(player.blockPos).key.get().value.toString()
        val currentTime = System.currentTimeMillis()

        // prevent spamming (only update if biome changes or 5 seconds have passed)
        if (biomeKey == lastBiomeForPlayer[uuid] &&
            (currentTime - (lastQueryTimeMap[uuid] ?: 0L)) < UPDATE_INTERVAL_MS
        ) return

        lastBiomeForPlayer[uuid] = biomeKey
        lastQueryTimeMap[uuid] = currentTime

        executor.execute {
            try {
                val selectedBuckets = if (biomeBucketMode == BiomeBucketMode.ALL) ALL_BUCKETS else listOf(biomeBucketMode)
                val allProbabilities = mutableMapOf<SpawnDetail, Float>()

                val spawner = player.spawner

                for (biomeBucket in selectedBuckets) {
                    val bucket = SpawnBucket(biomeBucket.value, biomeBucket.bucketPercentage)
                    val cause = SpawnCause(spawner, player)

                    val slice = Cobblemon.spawningZoneGenerator.generate(
                        spawner = spawner,
                        input = SpawningZoneInput(
                            cause = cause,
                            world = world,
                            baseX = MathHelper.ceil(player.x - Cobblemon.config.spawningZoneDiameter / 2F),
                            baseY = MathHelper.ceil(player.y - Cobblemon.config.spawningZoneHeight / 2F),
                            baseZ = MathHelper.ceil(player.z - Cobblemon.config.spawningZoneDiameter / 2F),
                            length = Cobblemon.config.spawningZoneDiameter,
                            height = Cobblemon.config.spawningZoneHeight,
                            width = Cobblemon.config.spawningZoneDiameter
                        )
                    )

                    val contexts = Cobblemon.areaSpawnablePositionResolver.resolve(spawner, prioritizedAreaCalculators, slice)
                    val spawnProbabilities = spawner.selector.getProbabilities(spawner, bucket, contexts).toMutableMap()

                    for ((spawnEntry, innerProbability) in spawnProbabilities) {
                        val totalProbability = (innerProbability / 100) * biomeBucket.bucketPercentage

//                        allProbabilities[spawnEntry] = (allProbabilities[spawnEntry] ?: 0f) + totalProbability
                        allProbabilities[spawnEntry] = innerProbability
                    }
                }

                val filtered = allProbabilities.filter { (spawnEntry, _) ->
                    val internalName = spawnEntry.id.substringBefore("-").lowercase()
                    val translatedName = spawnEntry.getName().string.trim().replace("\\s".toRegex(), "").lowercase()

                    ConfigManager.config.species.any { species ->
                        val speciesLower = species.trim().replace("\\s".toRegex(), "").lowercase()
                        internalName == speciesLower || translatedName == speciesLower
                    }
                }

                if (filtered.isEmpty()) {
                    HudOverlay.updateDisplay(emptyList(), biomeKey)
                    return@execute
                }

                val deduped = filtered.entries
                    .groupBy { entry ->
                        entry.key.id.substringBefore("-").trim().replace("\\s+".toRegex(), "").lowercase()
                    }
                    .mapValues { (_, entries) -> entries.minBy { it.value } }
                    .values
                    .associate { it.key to it.value }

                val maxDisplay = ConfigManager.config.biomeSpawn.maxPokemonDisplayed
                val sorted = deduped.entries
                    .sortedByDescending { it.value }
                    .take(maxDisplay)

                val entries = sorted.mapNotNull { (spawnEntry, probability) ->
                    val pokemonId = spawnEntry.id.substringBefore("-").lowercase()
                    val species = speciesCache[pokemonId]
                    if (species == null) {
                        logger.warn("Species '${pokemonId}' not found in cache.")
                        null
                    } else {
                        HudOverlay.PokemonDisplayEntry(
                            resourceIdentifier = species.resourceIdentifier,
                            aspects = species.standardForm.aspects.toMutableSet(),
                            name = species.name,
                            translatedName = species.translatedName.string,
                            spawnChance = probability
                        )
                    }
                }

                HudOverlay.updateDisplay(entries, biomeKey)

            } catch (e: Exception) {
                logger.error("Failed to load biome spawns for ${player.name.string}", e)
            }
        }
    }

    fun shutdown() {
        if (!executor.isShutdown) {
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}