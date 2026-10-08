package btcrenaud.corpse.manager

import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.ref
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.core.interaction.context
import btcrenaud.corpse.entries.*
import btcrenaud.corpse.entity.CorpseAccess
import btcrenaud.corpse.entity.CorpseAnimator
import btcrenaud.corpse.entity.CorpseDisplay
import btcrenaud.corpse.entity.CorpseEntity
import btcrenaud.corpse.entity.CorpseHitBoxCreator
import btcrenaud.corpse.entity.FallbackCorpseCreator
import btcrenaud.corpse.entity.SimpleCorpseEntity
import btcrenaud.corpse.persistence.CorpseRecord
import btcrenaud.corpse.persistence.CorpseRepository
import btcrenaud.corpse.persistence.CorpseStorageQueue
import btcrenaud.corpse.text.CorpseText
import btcrenaud.corpse.text.sendCorpseText
import btcrenaud.corpse.utils.CorpseDiagnostics
import btcrenaud.corpse.utils.CorpseScheduler
import com.typewritermc.engine.paper.entry.entity.EntityCreator
import com.typewritermc.engine.paper.entry.entity.SkinProperty
import com.typewritermc.engine.paper.entry.entity.toProperty
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.engine.paper.entry.entries.Var
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.logger
import com.typewritermc.engine.paper.utils.playSound
import com.typewritermc.engine.paper.utils.toPosition
import com.typewritermc.entity.entries.data.minecraft.GlowingEffectProperty
import com.typewritermc.entity.entries.data.minecraft.CustomNameProperty
import com.typewritermc.entity.entries.data.minecraft.PoseProperty
import com.typewritermc.entity.entries.data.minecraft.living.equipmentProperty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CorpseManager {

    /**
     * How long a reload or shutdown waits for queued storage writes. A garde-fou, not a tuning knob:
     * the queue holds a handful of small writes, so this only matters when the database is stuck.
     */
    private const val STORAGE_DRAIN_MILLIS = 10_000L

    private val activeCorpses = ConcurrentHashMap<UUID, CorpseEntity>()
    private var tickTask: CorpseScheduler.Task? = null
    private var storage: CorpseStorageQueue? = null

    @Volatile
    private var repository: CorpseRepository? = null
    private var initialized = false

    fun initialize() {
        if (initialized) return
        // Every tick, not every second: the display drives model animation through FakeEntity.tick(),
        // which the backends expect at tick rate.
        tickTask = CorpseScheduler.runTimer(1L, 1L, ::tickAll)
        initialized = true

        // The first task opens the store and restores what it holds; anything saved meanwhile is
        // queued behind it, so no write can reach a store that is not open yet.
        val queue = CorpseStorageQueue { logger.warning("[Corpse] Storage operation failed: ${it.message}") }
        storage = queue
        queue.submit {
            repository = CorpseRepository.create()
            restoreAll()
        }
    }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        // Only the rendering is torn down. The corpses themselves stay in storage so a restart
        // does not destroy the inventories they hold.
        activeCorpses.values.forEach { it.dispose() }
        activeCorpses.clear()

        // Wait for the writes already queued: a reload right after the last item was taken must
        // not lose that write and hand the item back after the restart.
        if (storage?.drain(STORAGE_DRAIN_MILLIS) == false) {
            logger.warning("[Corpse] Storage did not finish within ${STORAGE_DRAIN_MILLIS / 1000}s; recent changes may be lost")
        }
        storage = null
        repository?.shutdown()
        repository = null
        initialized = false
    }

    // ─── Configuration ─────────────────────────────────────────

    /**
     * The global settings entry, or built-in defaults when the project has none.
     *
     * Having no entry is a normal state. A resolution failure is not, and must not be quietly
     * turned into defaults: that used to switch `onlyOwnerCanLoot` off and let anyone loot
     * anything.
     */
    fun settings(): CorpseSettingsEntry =
        Query.find<CorpseSettingsEntry>().firstOrNull() ?: CorpseSettingsEntry()

    /**
     * The definition to use for a death in [worldName] (dimension key [dimensionKey]).
     *
     * Highest priority among the definitions that accept this world, so a nether corpse can differ
     * from an overworld one. The previous code took whichever definition came first in the project
     * and applied it everywhere.
     */
    fun definitionFor(worldName: String, dimensionKey: String? = null): CorpseDefinitionEntry =
        Query.find<CorpseDefinitionEntry>()
            .filter { it.appliesTo(worldName, dimensionKey) }
            .maxByOrNull { it.priority }
            ?: CorpseDefinitionEntry()

    // ─── Lifecycle ─────────────────────────────────────────────

    /**
     * Create the corpse of [player].
     *
     * Throws, with nothing registered, if the corpse cannot be built; the caller still owns the
     * items then. Once the corpse is registered nothing below can throw, so a failure can never
     * leave the items both in a corpse and back with the caller.
     */
    fun spawnCorpse(
        player: Player,
        location: Location,
        inventory: List<ItemStack>,
        exp: Int,
        settings: CorpseSettingsEntry,
        definition: CorpseDefinitionEntry,
    ) {
        // Refreshed here rather than at startup: the settings entry only exists once pages are loaded.
        CorpseDiagnostics.enabled = settings.debugInteractions

        val corpseId = UUID.randomUUID()
        val corpseLoc = prepareLocation(location, player, definition)

        val duration = definition.overrideDuration.get(player).takeIf { it > 0 }
            ?: settings.duration.get(player)

        val corpse = SimpleCorpseEntity(
            corpseId = corpseId,
            playerUUID = player.uniqueId,
            playerName = player.name,
            inventory = inventory,
            experience = exp,
            location = corpseLoc,
            access = CorpseAccess(
                onlyOwnerCanLoot = settings.onlyOwnerCanLoot.get(player),
                ownerProtectionSeconds = settings.ownerProtectionSeconds.get(player),
            ),
            definitionId = definition.id,
            expiresAt = if (duration > 0) System.currentTimeMillis() + duration * 1000L else 0L,
            display = createDisplay(player, corpseLoc, player.name, settings, definition),
        )

        activeCorpses[corpseId] = corpse

        // From here the corpse exists. Each step is isolated so that one failing cannot unwind the
        // spawn and make the caller give the items back a second time.
        guarded("persist") { persist(corpse) }
        guarded("audience refresh") { player.refreshCorpseAudience() }
        guarded("death effects") { spawnDeathEffects(corpseLoc, settings, player) }
        guarded("spawn event") { fireCorpseEvent<CorpseSpawnEventEntry>(player, corpse) }
    }

    private inline fun guarded(step: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            logger.warning("[Corpse] $step failed after the corpse was created: ${failure.stackTraceToString()}")
        }
    }

    /**
     * Empty the corpse for [looter].
     *
     * [handToLooter] moves the contents into their inventory and only drops the overflow; otherwise
     * everything lands on the ground at the corpse.
     */
    fun lootCorpse(corpse: CorpseEntity, looter: Player, handToLooter: Boolean = false) {
        val settings = settings()

        // The caller may hold a corpse that someone else emptied, or that expired, since the click
        // was queued: the object keeps its items after removal, so without this check the contents
        // would be handed out a second time.
        if (!corpse.canLoot(looter)) {
            looter.sendCorpseText(settings.cannotLootMessage.get(looter))
            return
        }

        // Claimed atomically before anything is handed out: of two clicks racing for the same
        // corpse, exactly one gets it, so the contents can never be given out twice.
        val claimed = activeCorpses.remove(corpse.corpseId)
        if (claimed == null) {
            looter.sendCorpseText(settings.corpseGoneMessage.get(looter))
            return
        }
        finishRemoval(claimed, CorpseExpireReason.LOOTED)

        val loc = claimed.location
        val items = claimed.inventory.filter { !it.type.isAir }
        val experience = claimed.experience

        val overflow = if (handToLooter) {
            val leftovers = items.flatMap { looter.inventory.addItem(it).values }
            if (leftovers.isNotEmpty()) {
                looter.sendCorpseText(settings.inventoryFullMessage.get(looter))
            }
            leftovers
        } else {
            items
        }

        // Dropping items and spawning orbs mutates the world, so it has to happen on the region
        // that owns the corpse location, not on the thread that called us.
        CorpseScheduler.runAt(loc) {
            val world = loc.world ?: return@runAt
            overflow.forEach { item -> dropReserved(world.dropItemNaturally(loc, item), looter, settings) }
            if (experience > 0) {
                if (handToLooter) looter.giveExp(experience)
                else world.spawn(loc, ExperienceOrb::class.java) { orb -> orb.experience = experience }
            }
            nearbyViewers(loc, settings.effectRange.get(looter)).forEach {
                it.playSound(settings.lootSound, null)
            }
        }

        fireCorpseEvent<CorpseLootEventEntry>(looter, corpse)
    }

    /**
     * Reserve a dropped item for the looter.
     *
     * Paper's owner flag makes the item pickable by that player alone. It stays visible to others —
     * a genuinely client-side drop would need per-viewer packet entities, which is not what this
     * does.
     */
    private fun dropReserved(item: Item, looter: Player, settings: CorpseSettingsEntry) {
        if (!settings.reserveDroppedItems.get(looter)) return
        item.owner = looter.uniqueId
        val seconds = settings.reserveDroppedItemsSeconds.get(looter)
        if (seconds > 0) {
            CorpseScheduler.runAtLater(item.location, seconds * 20L) {
                if (item.isValid) item.owner = null
            }
        }
    }

    fun removeCorpse(corpseId: UUID, reason: CorpseExpireReason) {
        val corpse = activeCorpses.remove(corpseId) ?: return
        finishRemoval(corpse, reason)
    }

    /** What follows a corpse leaving [activeCorpses]: its event, its rendering and its stored copy. */
    private fun finishRemoval(corpse: CorpseEntity, reason: CorpseExpireReason) {
        if (reason == CorpseExpireReason.EXPIRED) {
            fireCorpseEvent<CorpseExpireEventEntry>(null, corpse)
        }
        corpse.dispose()
        forget(corpse.corpseId)
        Bukkit.getPlayer(corpse.playerUUID)?.refreshCorpseAudience()
    }

    /** Corpses currently loaded, for facts and admin tooling. */
    fun activeCorpses(): List<CorpseEntity> = activeCorpses.values.toList()

    fun getCorpse(corpseId: UUID): CorpseEntity? = activeCorpses[corpseId]

    /**
     * The corpse [playerUUID] most recently left behind.
     *
     * A player can have several. The respawn message, the waypoint and the objective all speak of
     * "the" corpse, and the one that matters is the latest, not whichever the map lists first.
     */
    fun getCorpseForPlayer(playerUUID: UUID): CorpseEntity? =
        activeCorpses.values.filter { it.playerUUID == playerUUID }.maxByOrNull { it.createdAt }

    /**
     * Resolve the corpse a player interacted with from the entity id the client sent.
     *
     * This replaces the previous "nearest corpse within four blocks" guess, which fired on any
     * right-click near a corpse — including placing a block or opening a chest.
     */
    fun getCorpseByEntity(playerId: UUID, entityId: Int): CorpseEntity? =
        activeCorpses.values.firstOrNull { it.isSeenBy(playerId, entityId) }

    /** Entity ids every live corpse currently uses for [playerId]. Diagnostics only. */
    fun knownEntityIdsFor(playerId: UUID): List<Int> =
        activeCorpses.values.mapNotNull { it.entityIdFor(playerId) }

    // ─── Persistence ───────────────────────────────────────────

    /** Rebuild corpses saved by a previous run. Runs on the storage queue. */
    private fun restoreAll() {
        val records = repository?.loadAll().orEmpty()
        if (records.isEmpty()) return

        val now = System.currentTimeMillis()
        val settings = settings()
        var restored = 0

        records.forEach { record ->
            if (record.expiresAt in 1..now) {
                repository?.delete(record.corpseId)
                return@forEach
            }
            val location = record.toLocation() ?: return@forEach
            val world = location.world
            val definition = Query.find<CorpseDefinitionEntry>()
                .firstOrNull { it.id == record.definitionId }
                ?: definitionFor(world?.name.orEmpty(), world?.key()?.asString())

            activeCorpses[record.corpseId] = SimpleCorpseEntity(
                corpseId = record.corpseId,
                playerUUID = record.ownerUuid,
                playerName = record.ownerName,
                inventory = record.inventory,
                experience = record.experience,
                location = location,
                access = record.access,
                definitionId = record.definitionId,
                createdAt = record.createdAt,
                expiresAt = record.expiresAt,
                // No player to resolve against after a restart: only constant values can be read,
                // and the skin and armour, which were never stored, are the plain model's.
                display = createDisplay(null, location, record.ownerName, settings, definition),
            )
            restored++
        }

        if (restored > 0) logger.info("[Corpse] Restored $restored corpse(s) from storage")
    }

    /**
     * Read a [Var] for [player], or without a player when it is null.
     *
     * Without a player only constant values can be resolved; anything placeholder-driven falls
     * back to [fallback]. Restoring corpses at startup is the case: the owner may be offline.
     */
    private fun <T : Any> Var<T>.valueFor(player: Player?, fallback: T): T =
        if (player != null) get(player) else get(null as Player?) ?: fallback

    /**
     * Write the corpse's current state to storage.
     *
     * Called after every change to its contents, not only at spawn: looting one item at a time
     * from the GUI must not leave storage holding a copy of the items already taken, or a restart
     * would duplicate them.
     */
    fun persist(corpse: CorpseEntity) {
        val record = CorpseRecord.of(corpse) ?: return
        val queue = storage ?: return
        queue.submit { repository?.save(record) }
    }

    private fun forget(corpseId: UUID) {
        val queue = storage ?: return
        queue.submit { repository?.delete(corpseId) }
    }

    // ─── Internal ──────────────────────────────────────────────

    private fun tickAll() {
        if (activeCorpses.isEmpty()) return
        val now = System.currentTimeMillis()

        activeCorpses.values
            .filter { it.expiresAt in 1..now }
            .forEach { removeCorpse(it.corpseId, CorpseExpireReason.EXPIRED) }

        activeCorpses.values.forEach { corpse -> runCatching { corpse.tick() } }
    }

    /**
     * The renderer of a corpse.
     *
     * [player] is the dead player when the corpse is created, null when it is restored after a
     * restart; [ownerName] is the dead player's name either way.
     */
    private fun createDisplay(
        player: Player?,
        corpseLoc: Location,
        ownerName: String,
        settings: CorpseSettingsEntry,
        definition: CorpseDefinitionEntry,
    ): CorpseDisplay {
        val displayName = displayNameFor(player, ownerName, definition)

        val animation = definition.deathAnimationName.valueFor(player, "death")
            .takeIf { definition.playDeathAnimation.valueFor(player, true) && it.isNotBlank() }
        val animationSpeed = definition.deathAnimationSpeed.valueFor(player, 1.0)
        val animationLoops = definition.loopDeathAnimation.valueFor(player, true)

        return CorpseDisplay(
            creator = resolveCreator(definition, displayName),
            position = corpseLoc.toProperty(),
            initialProperties = buildProperties(player, definition, settings, displayName),
            onSpawned = { entity ->
                animation?.let { CorpseAnimator.play(entity, it, animationSpeed, animationLoops) }
            },
        )
    }

    private fun displayNameFor(player: Player?, ownerName: String, definition: CorpseDefinitionEntry): String =
        definition.displayName.valueFor(player, CorpseDefinitionEntry.DEFAULT_DISPLAY_NAME)
            .let { CorpseText.fill(it, mapOf("player" to ownerName)) }
            .parsePlaceholders(player)

    private fun resolveCreator(definition: CorpseDefinitionEntry, displayName: String): EntityCreator {
        val configured = runCatching { definition.definitionRef.get() }
            .onFailure { logger.warning("[Corpse] Failed to resolve entity definition: ${it.message}") }
            .getOrNull()
        val model = configured ?: FallbackCorpseCreator(displayName)

        // Always wrapped: a lying player has a hitbox of about a fifth of a block and model
        // backends have none at all, so without a box the corpse renders but cannot be clicked.
        return CorpseHitBoxCreator(
            delegate = model,
            width = definition.interactionWidth,
            height = definition.interactionHeight,
            verticalOffset = definition.interactionOffset,
        )
    }

    /** What every viewer's copy of the corpse is told. [player] is null for a restored corpse. */
    private fun buildProperties(
        player: Player?,
        definition: CorpseDefinitionEntry,
        settings: CorpseSettingsEntry,
        displayName: String,
    ): List<EntityProperty> = buildList {
        // The victim's skin and the armour they died in are only known at death; a restored corpse
        // has neither, because storage does not keep them.
        if (player != null) resolveSkin(player)?.let { add(it) }
        if (definition.showDisplayName.valueFor(player, true)) add(CustomNameProperty(displayName))
        if (player != null && settings.renderArmor.get(player)) runCatching { add(player.equipmentProperty()) }
        // Client-rendered outline. This replaces the old per-second particle ring, which the
        // server sent to everyone in the world regardless of distance.
        if (settings.glowEffect.valueFor(player, true)) {
            add(GlowingEffectProperty(glowing = true, color = settings.glowColor))
        }
        // Only the plain player model is posed. Model backends carry their own rest pose and would
        // fight a vanilla pose, so they are animated by name instead (see CorpseAnimator).
        if (definition.definitionRef.get() == null) {
            add(PoseProperty(definition.pose.pose))
        }
    }

    private fun prepareLocation(
        location: Location,
        player: Player,
        definition: CorpseDefinitionEntry,
    ): Location = location.clone().apply {
        x = blockX + 0.5
        y += definition.verticalOffset.get(player)
        z = blockZ + 0.5
        yaw = when (definition.rotationMode) {
            CorpseRotationMode.RANDOM -> (Math.random() * 360).toFloat()
            CorpseRotationMode.FACE_KILLER -> player.location.yaw + 180f
            CorpseRotationMode.FACE_FORWARD -> location.yaw
        }
    }

    private fun resolveSkin(player: Player): SkinProperty? = runCatching {
        player.playerProfile.properties
            .firstOrNull { it.name == "textures" }
            ?.let { SkinProperty(texture = it.value, signature = it.signature ?: "") }
    }.getOrNull()

    private fun spawnDeathEffects(loc: Location, settings: CorpseSettingsEntry, victim: Player) {
        val particles = settings.spawnParticles.get(victim)
        val primaryCount = settings.spawnParticleCount.get(victim)
        val secondaryCount = settings.spawnParticleSecondaryCount.get(victim)
        val range = settings.effectRange.get(victim)

        CorpseScheduler.runAt(loc) {
            val world = loc.world ?: return@runAt

            if (particles) {
                // A misconfigured particle must not take the whole spawn down: some Bukkit
                // particles need extra data and throw without it.
                if (primaryCount > 0) runCatching {
                    world.spawnParticle(
                        settings.spawnParticle, loc.clone().add(0.0, 1.0, 0.0),
                        primaryCount, 0.3, 0.5, 0.3, 0.1
                    )
                }
                if (secondaryCount > 0) runCatching {
                    world.spawnParticle(
                        settings.spawnParticleSecondary, loc.clone().add(0.0, 1.5, 0.0),
                        secondaryCount, 0.2, 0.3, 0.2, 0.02
                    )
                }
            }

            nearbyViewers(loc, range).forEach { it.playSound(settings.spawnSound, null) }
        }
    }

    /**
     * Players close enough to be told about something happening at [loc].
     *
     * Sounds are sent per player rather than through `World.playSound` so the server only talks to
     * clients that can actually hear it, and so the sound entry's own source setting is honoured.
     */
    private fun nearbyViewers(loc: Location, range: Double): List<Player> {
        val world = loc.world ?: return emptyList()
        val rangeSquared = range * range
        return world.players.filter { it.location.distanceSquared(loc) <= rangeSquared }
    }

    // ─── Events ────────────────────────────────────────────────

    /**
     * Fire every configured event entry of type [E] for this corpse.
     *
     * Goes through the engine's trigger pipeline, so the criteria and modifiers on the triggered
     * entries are evaluated. The old hand-rolled `ActionTrigger` path ignored both.
     */
    private inline fun <reified E : com.typewritermc.engine.paper.entry.entries.EventEntry> fireCorpseEvent(
        player: Player?,
        corpse: CorpseEntity,
    ) {
        val entries = Query.find<E>().toList()
        if (entries.isEmpty()) return

        val target = player ?: Bukkit.getPlayer(corpse.playerUUID) ?: return
        if (!target.isOnline) return

        entries.forEach { entry ->
            runCatching {
                entry.triggers.triggerEntriesFor(target, corpse.contextFor(entry))
            }.onFailure {
                logger.warning("[Corpse] Failed to fire ${E::class.simpleName} '${entry.id}': ${it.message}")
            }
        }
    }

    private fun CorpseEntity.contextFor(
        entry: com.typewritermc.engine.paper.entry.entries.EventEntry,
    ): InteractionContext = context {
        entry[CorpseContextKeys.OWNER_NAME] = playerName
        entry[CorpseContextKeys.OWNER_UUID] = playerUUID.toString()
        entry[CorpseContextKeys.EXPERIENCE] = experience
        entry[CorpseContextKeys.ITEM_COUNT] = inventory.count { !it.type.isAir }
        entry[CorpseContextKeys.CORPSE_POSITION] = location.toPosition()
    }

    /**
     * Audience filters cannot observe corpse changes on their own — gaining or losing a corpse is
     * not a Bukkit event — so re-evaluate them whenever a player's corpse set changes.
     */
    private fun Player.refreshCorpseAudience() {
        val player = this
        Query.find<CorpseOwnerAudienceEntry>().forEach { entry ->
            runCatching {
                entry.ref().findDisplay<CorpseOwnerAudienceFilter>()?.refreshOwner(player)
            }
        }
    }
}

enum class CorpseExpireReason {
    LOOTED,
    EXPIRED,
    MANUAL,
}
