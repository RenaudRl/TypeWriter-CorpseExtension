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
import btcrenaud.corpse.entity.ViewerRules
import btcrenaud.corpse.persistence.BoundedRetry
import btcrenaud.corpse.persistence.CorpseRecord
import btcrenaud.corpse.persistence.CorpseRepository
import btcrenaud.corpse.persistence.CorpseStorageQueue
import btcrenaud.corpse.persistence.PurgeMarks
import btcrenaud.corpse.text.CorpseText
import btcrenaud.corpse.text.sendCorpseText
import btcrenaud.corpse.utils.CorpseDiagnostics
import btcrenaud.corpse.utils.CorpseScheduler
import btcrenaud.corpse.utils.Failures
import btcrenaud.corpse.utils.PlayerPositions
import btcrenaud.corpse.world.CorpseSpot
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
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.playSound
import com.typewritermc.engine.paper.utils.toPosition
import com.typewritermc.entity.entries.data.minecraft.GlowingEffectProperty
import com.typewritermc.entity.entries.data.minecraft.CustomNameProperty
import com.typewritermc.entity.entries.data.minecraft.PoseProperty
import com.typewritermc.entity.entries.data.minecraft.living.equipmentProperty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
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

    /** Where the ids of corpses whose deletion failed are kept (see [PurgeMarks]). */
    private const val PURGE_MARKS_FILE = "corpse/purge-marks.txt"

    /**
     * Attempts for one storage write, and the pause step between them (see [BoundedRetry]). Technical
     * limits of a retry, not a tuning knob: three tries over about a second and a half ride out a
     * dropped connection without holding the storage thread for long.
     */
    private const val STORAGE_ATTEMPTS = 3
    private const val STORAGE_PAUSE_MILLIS = 500L

    /** Pause before an expired corpse whose contents could not be dropped is tried again. */
    private const val EXPIRY_RETRY_MILLIS = 10_000L

    private val activeCorpses = ConcurrentHashMap<UUID, CorpseEntity>()

    /**
     * Corpses being emptied right now. A corpse stays registered, and stored, until its contents
     * have reached their new owner; this set is what keeps a second click, the expiry and a
     * command from emptying it a second time in the meantime.
     */
    private val claims: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val expiryRetryAt = ConcurrentHashMap<UUID, Long>()
    private val tickFailing: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var tickTask: CorpseScheduler.Task? = null
    private var storage: CorpseStorageQueue? = null

    @Volatile
    private var repository: CorpseRepository? = null

    /** Touched on the storage thread only. */
    private var purgeMarks: PurgeMarks? = null

    @Volatile
    private var closing = false
    private var initialized = false

    fun initialize() {
        if (initialized) return
        closing = false
        // Every tick, not every second: the display drives model animation through FakeEntity.tick(),
        // which the backends expect at tick rate.
        tickTask = CorpseScheduler.runTimer(1L, 1L, ::tickAll)
        initialized = true

        // The first task opens the store and restores what it holds; anything saved meanwhile is
        // queued behind it, so no write can reach a store that is not open yet.
        val queue = CorpseStorageQueue { logger.warning("[Corpse] Storage operation failed: ${it.stackTraceToString()}") }
        storage = queue
        queue.submit {
            repository = CorpseRepository.create()
            purgeMarks = PurgeMarks(plugin.dataFolder.resolve(PURGE_MARKS_FILE))
            applyPurgeMarks()
            restoreAll()
        }
    }

    fun shutdown() {
        tickTask?.cancel()
        tickTask = null
        // Tells a restore still running on the storage thread to stop, so it cannot register a
        // corpse after the clear below.
        closing = true

        // Wait for the writes already queued, and for a restore in progress, before touching the
        // corpses: a reload right after the last item was taken must not lose that write and hand
        // the item back after the restart, and a restore that outlived the clear leaked its displays.
        if (storage?.drain(STORAGE_DRAIN_MILLIS) == false) {
            logger.warning("[Corpse] Storage did not finish within ${STORAGE_DRAIN_MILLIS / 1000}s; recent changes may be lost")
        }
        storage = null

        // Only the rendering is torn down. The corpses themselves stay in storage so a restart
        // does not destroy the inventories they hold.
        activeCorpses.values.forEach { it.dispose() }
        activeCorpses.clear()
        claims.clear()
        expiryRetryAt.clear()
        tickFailing.clear()

        repository?.shutdown()
        repository = null
        purgeMarks = null
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
     * items then. [onRegistered] runs the moment the corpse exists and before anything else can
     * fail, so the caller can give up its own copy of the items at exactly that point; every later
     * step is isolated, so a failure can never leave the items both in a corpse and with the caller.
     */
    fun spawnCorpse(
        player: Player,
        location: Location,
        inventory: List<ItemStack>,
        exp: Int,
        settings: CorpseSettingsEntry,
        definition: CorpseDefinitionEntry,
        onRegistered: () -> Unit = {},
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

        // From here the corpse owns the items. Each step is isolated so that one failing cannot
        // unwind the spawn and make the caller give the items back a second time.
        guarded("hand-over") { onRegistered() }
        guarded("persist") { persist(corpse) }
        guarded("audience refresh") { player.refreshCorpseAudience() }
        guarded("death effects") { spawnDeathEffects(corpseLoc, settings, player) }
        guarded("spawn event") { fireCorpseEvent<CorpseSpawnEventEntry>(player, corpse) }
    }

    private inline fun guarded(step: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            // An Error counts too: a backend class missing at runtime must not unwind the spawn.
            if (Failures.isFatal(failure)) throw failure
            logger.warning("[Corpse] $step failed after the corpse was created: ${failure.stackTraceToString()}")
        }
    }

    /** What a drop on the ground needs from the looter's settings, read on the looter's own thread. */
    private class DropPlan(val reserveFor: UUID?, val reserveSeconds: Int)

    /**
     * Empty the corpse for [looter]. Call it on the looter's own thread: it reads their settings,
     * changes their inventory and experience, and sends them text. Only what must touch the world
     * (dropping items, spawning an orb) hops to the region owning the corpse.
     *
     * [handToLooter] moves the contents into their inventory and only drops the overflow; otherwise
     * everything lands on the ground at the corpse.
     *
     * The corpse is not removed, and its stored copy is not deleted, until everything has reached
     * its new place. A hand-over that fails half way puts back what did not arrive and keeps the
     * corpse, instead of having deleted it first and lost the rest.
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

        val loc = corpse.location
        if (loc.world == null || !plugin.isEnabled) {
            logger.warning("[Corpse] Corpse ${corpse.corpseId} cannot be emptied now (its world is not loaded, or the plugin is disabled); nothing was removed")
            looter.sendCorpseText(settings.corpseGoneMessage.get(looter))
            return
        }

        // Claimed atomically before anything is handed out: of two clicks racing for the same
        // corpse, exactly one gets it, so the contents can never be given out twice.
        val claimed = activeCorpses[corpse.corpseId]
        if (claimed == null || !claims.add(corpse.corpseId)) {
            looter.sendCorpseText(settings.corpseGoneMessage.get(looter))
            return
        }

        // Emptied in one step under the corpse's own lock, the lock a single-slot take also uses.
        val taken = claimed.takeAll()
        val experience = claimed.takeExperience()
        val pending = LinkedHashMap(taken)
        val overflow = linkedMapOf<Int, ItemStack>()
        var orbExperience = 0
        try {
            if (handToLooter) {
                taken.forEach { (slot, item) ->
                    val leftover = looter.inventory.addItem(item.clone()).values.firstOrNull()
                    pending.remove(slot)
                    if (leftover != null) overflow[slot] = leftover
                }
                if (overflow.isNotEmpty()) looter.sendCorpseText(settings.inventoryFullMessage.get(looter))
                if (experience > 0) looter.giveExp(experience)
            } else {
                pending.clear()
                overflow.putAll(taken)
                orbExperience = experience
            }

            val plan = DropPlan(
                reserveFor = looter.uniqueId.takeIf { settings.reserveDroppedItems.get(looter) },
                reserveSeconds = settings.reserveDroppedItemsSeconds.get(looter),
            )
            val range = settings.effectRange.get(looter)
            groundDrop(claimed, overflow, orbExperience, plan) {
                completeRemoval(claimed, CorpseExpireReason.LOOTED, taken.size, experience)
                playNearby(loc, range, settings.lootSound)
                fireCorpseEvent<CorpseLootEventEntry>(looter, claimed, taken.size, experience)
            }
        } catch (failure: Throwable) {
            if (Failures.isFatal(failure)) throw failure
            logger.warning("[Corpse] Emptying corpse ${claimed.corpseId} for ${looter.name} failed, keeping what did not arrive: ${failure.stackTraceToString()}")
            pending.putAll(overflow)
            putBack(claimed, pending, orbExperience)
        }
    }

    /**
     * Drop [items] (by their slot in the corpse) and spawn an orb for [orbExperience] at the corpse.
     * Each drop is tried on its own, so one failing does not take the others with it. [onDelivered]
     * runs once everything is on the ground; otherwise what failed is put back and the corpse stays.
     */
    private fun groundDrop(
        corpse: CorpseEntity,
        items: Map<Int, ItemStack>,
        orbExperience: Int,
        plan: DropPlan?,
        onDelivered: () -> Unit,
    ) {
        // What follows a delivery must not be mistaken for a failed delivery: it only logs.
        val delivered = {
            try {
                onDelivered()
            } catch (failure: Throwable) {
                if (Failures.isFatal(failure)) throw failure
                logger.warning("[Corpse] Finishing corpse ${corpse.corpseId} failed after its contents were delivered: ${failure.stackTraceToString()}")
            }
        }
        if (items.isEmpty() && orbExperience <= 0) {
            delivered()
            return
        }
        val loc = corpse.location
        // The world is mutated on the region that owns the corpse, not on the thread that called us.
        val scheduled = CorpseScheduler.runAt(loc) {
            val failedItems = linkedMapOf<Int, ItemStack>()
            var orbFailed = false
            val world = loc.world
            if (world == null) {
                failedItems.putAll(items)
                orbFailed = orbExperience > 0
            } else {
                items.forEach { (slot, item) ->
                    try {
                        val dropped = world.dropItemNaturally(loc, item)
                        if (plan != null) reserve(dropped, plan)
                    } catch (failure: Throwable) {
                        if (Failures.isFatal(failure)) throw failure
                        failedItems[slot] = item
                        logger.warning("[Corpse] Could not drop slot $slot of corpse ${corpse.corpseId} ($item): ${failure.stackTraceToString()}")
                    }
                }
                if (orbExperience > 0) {
                    try {
                        world.spawn(loc, ExperienceOrb::class.java) { orb -> orb.experience = orbExperience }
                    } catch (failure: Throwable) {
                        if (Failures.isFatal(failure)) throw failure
                        orbFailed = true
                        logger.warning("[Corpse] Could not spawn the experience orb of corpse ${corpse.corpseId}: ${failure.stackTraceToString()}")
                    }
                }
            }
            if (failedItems.isEmpty() && !orbFailed) delivered()
            else putBack(corpse, failedItems, if (orbFailed) orbExperience else 0)
        }
        // Not scheduled (the plugin is going down): nothing was dropped, so nothing is lost either.
        if (!scheduled) putBack(corpse, items, orbExperience)
    }

    /** Give the corpse back what a failed hand-over did not deliver, and keep it. */
    private fun putBack(corpse: CorpseEntity, items: Map<Int, ItemStack>, experience: Int) {
        items.forEach { (slot, item) ->
            if (!corpse.restoreItem(slot, item)) {
                logger.severe("[Corpse] Slot $slot of corpse ${corpse.corpseId} could not take its item back, it is lost: $item")
            }
        }
        corpse.restoreExperience(experience)
        expiryRetryAt[corpse.corpseId] = System.currentTimeMillis() + EXPIRY_RETRY_MILLIS
        // Storage still holds the full corpse; bring it in line with what the looter did receive.
        persist(corpse)
        claims.remove(corpse.corpseId)
        logger.warning("[Corpse] Corpse ${corpse.corpseId} kept: ${items.size} item(s) and $experience experience could not be handed over")
    }

    /**
     * Reserve a dropped item for the looter.
     *
     * Paper's owner flag makes the item pickable by that player alone. It stays visible to others —
     * a genuinely client-side drop would need per-viewer packet entities, which is not what this
     * does.
     */
    private fun reserve(item: Item, plan: DropPlan) {
        val reserveFor = plan.reserveFor ?: return
        try {
            item.owner = reserveFor
            if (plan.reserveSeconds > 0) {
                CorpseScheduler.runAtLater(item.location, plan.reserveSeconds * 20L) {
                    if (item.isValid) item.owner = null
                }
            }
        } catch (failure: Throwable) {
            if (Failures.isFatal(failure)) throw failure
            logger.warning("[Corpse] Could not reserve a dropped item for its looter: ${failure.stackTraceToString()}")
        }
    }

    /**
     * Take a corpse that has run out of time off the world. With `dropOnExpire` its contents are
     * dropped at the corpse first, and the corpse stays until they are on the ground.
     */
    private fun expire(corpse: CorpseEntity) {
        // A corpse whose world is not loaded waits for it: there is nowhere to drop the contents.
        if (corpse.location.world == null) return
        if (activeCorpses[corpse.corpseId] == null || !claims.add(corpse.corpseId)) return

        val taken = corpse.takeAll()
        val experience = corpse.takeExperience()
        try {
            groundDrop(corpse, taken, experience, plan = null) {
                completeRemoval(corpse, CorpseExpireReason.EXPIRED, taken.size, experience)
            }
        } catch (failure: Throwable) {
            if (Failures.isFatal(failure)) throw failure
            logger.warning("[Corpse] Expiring corpse ${corpse.corpseId} failed, keeping its contents: ${failure.stackTraceToString()}")
            putBack(corpse, taken, experience)
        }
    }

    /** Admin removal, or expiry without `dropOnExpire`: the corpse goes with what it holds. */
    fun removeCorpse(corpseId: UUID, reason: CorpseExpireReason) {
        if (corpseId in claims) return
        val corpse = activeCorpses.remove(corpseId) ?: return
        finishRemoval(corpse, reason, null, null)
    }

    /** The end of an emptied corpse: leaves the registry, then the world and the store. */
    private fun completeRemoval(corpse: CorpseEntity, reason: CorpseExpireReason, itemCount: Int, experience: Int) {
        try {
            activeCorpses.remove(corpse.corpseId)
            finishRemoval(corpse, reason, itemCount, experience)
        } finally {
            claims.remove(corpse.corpseId)
            expiryRetryAt.remove(corpse.corpseId)
        }
    }

    /** What follows a corpse leaving [activeCorpses]: its event, its rendering and its stored copy. */
    private fun finishRemoval(corpse: CorpseEntity, reason: CorpseExpireReason, itemCount: Int?, experience: Int?) {
        if (reason == CorpseExpireReason.EXPIRED) {
            guarded("expire event") { fireCorpseEvent<CorpseExpireEventEntry>(null, corpse, itemCount, experience) }
        }
        guarded("dispose") { corpse.dispose() }
        forget(corpse.corpseId)
        guarded("audience refresh") { refreshAudienceOf(corpse.playerUUID) }
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
            // A reload during the restore: the shutdown is waiting for this task, so stop early.
            if (closing) return@forEach
            // One bad record (a missing model backend, a corrupt item) must not stop the others from
            // coming back. It stays in storage untouched, so it can be recovered once fixed.
            try {
                if (restore(record, now, settings)) restored++
            } catch (failure: Throwable) {
                if (Failures.isFatal(failure)) throw failure
                logger.warning("[Corpse] Could not restore corpse ${record.corpseId} of ${record.ownerName}: ${failure.stackTraceToString()}")
            }
        }

        if (restored > 0) logger.info("[Corpse] Restored $restored corpse(s) from storage")
    }

    /** Brings one stored corpse back. Returns false when it was expired or its world is gone. */
    private fun restore(record: CorpseRecord, now: Long, settings: CorpseSettingsEntry): Boolean {
        // Its deletion failed last time and is still pending: bringing it back would hand out a
        // corpse somebody already emptied.
        if (purgeMarks?.contains(record.corpseId) == true) return false
        // With dropOnExpire an expired corpse is restored like any other and the tick drops its
        // contents; only a server that destroys expired corpses deletes it here.
        if (record.expiresAt in 1..now && !settings.dropOnExpire) {
            writeDelete(record.corpseId)
            return false
        }
        val location = record.toLocation() ?: return false
        val world = location.world
        val definition = Query.find<CorpseDefinitionEntry>()
            .firstOrNull { it.id == record.definitionId }
            ?: definitionFor(world?.name.orEmpty(), world?.key()?.asString())

        val corpse = SimpleCorpseEntity(
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
            unreadableItems = record.unreadableItems,
        )
        // An entry already there (the same corpse restored twice) must not be left rendering
        // behind the one that replaces it.
        activeCorpses.put(record.corpseId, corpse)?.dispose()
        return true
    }

    /** Removes the corpses whose deletion failed in an earlier run. Runs on the storage queue. */
    private fun applyPurgeMarks() {
        val marks = purgeMarks ?: return
        marks.all().forEach { corpseId ->
            if (closing) return
            if (writeDelete(corpseId, alreadyMarked = true)) {
                runCatching { marks.remove(corpseId) }
                    .onFailure { logger.warning("[Corpse] Purged corpse $corpseId but could not clear its mark: ${it.message}") }
            }
        }
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
        // Checked when it runs: a save queued just before the corpse was emptied must not write it
        // back after its deletion.
        queue.submit { if (activeCorpses.containsKey(record.corpseId)) writeSave(record) }
    }

    private fun forget(corpseId: UUID) {
        val queue = storage ?: return
        queue.submit { writeDelete(corpseId) }
    }

    /** Storage thread only. Retries, then says loudly what could not be saved. */
    private fun writeSave(record: CorpseRecord) {
        val store = repository ?: return
        val failure = BoundedRetry.run(
            STORAGE_ATTEMPTS, STORAGE_PAUSE_MILLIS,
            onFailure = { attempt, cause ->
                logger.warning("[Corpse] Saving corpse ${record.corpseId} of ${record.ownerName} failed (attempt $attempt of $STORAGE_ATTEMPTS): ${cause.message}")
            },
        ) { store.save(record) }
        if (failure != null) {
            logger.severe(
                "[Corpse] Could not save corpse ${record.corpseId} of ${record.ownerName} (${record.inventory.count { !it.type.isAir }} item(s)) " +
                    "after $STORAGE_ATTEMPTS attempts. The stored copy is out of date: items already taken from it " +
                    "come back if the server restarts before the next successful save. ${failure.stackTraceToString()}"
            )
        }
    }

    /**
     * Storage thread only. Retries; when the corpse still cannot be deleted its id is written to
     * the purge marks, which the next startup applies before restoring anything.
     *
     * @return whether the corpse is gone from storage.
     */
    private fun writeDelete(corpseId: UUID, alreadyMarked: Boolean = false): Boolean {
        val store = repository ?: return false
        val failure = BoundedRetry.run(
            STORAGE_ATTEMPTS, STORAGE_PAUSE_MILLIS,
            onFailure = { attempt, cause ->
                logger.warning("[Corpse] Deleting corpse $corpseId failed (attempt $attempt of $STORAGE_ATTEMPTS): ${cause.message}")
            },
        ) { store.delete(corpseId) }
        if (failure == null) return true

        val marked = alreadyMarked || runCatching { purgeMarks?.add(corpseId) }
            .onFailure { logger.severe("[Corpse] Could not record the purge mark of corpse $corpseId: ${it.stackTraceToString()}") }
            .isSuccess
        logger.severe(
            "[Corpse] Could not delete corpse $corpseId from storage after $STORAGE_ATTEMPTS attempts; " +
                (if (marked) "it is marked for purging at the next startup" else "it WILL come back after a restart") +
                ". ${failure.stackTraceToString()}"
        )
        return false
    }

    // ─── Internal ──────────────────────────────────────────────

    private fun tickAll() {
        if (activeCorpses.isEmpty()) return
        val now = System.currentTimeMillis()

        val expired = activeCorpses.values.filter {
            it.expiresAt in 1..now && (expiryRetryAt[it.corpseId] ?: 0L) <= now
        }
        if (expired.isNotEmpty()) {
            val dropContents = settings().dropOnExpire
            expired.forEach { corpse ->
                if (dropContents) expire(corpse) else removeCorpse(corpse.corpseId, CorpseExpireReason.EXPIRED)
            }
        }

        activeCorpses.values.forEach { corpse ->
            try {
                corpse.tick()
                if (tickFailing.isNotEmpty()) tickFailing.remove(corpse.corpseId)
            } catch (failure: Throwable) {
                if (Failures.isFatal(failure)) throw failure
                // Once per run of failures: it would otherwise repeat every tick.
                if (tickFailing.add(corpse.corpseId)) {
                    logger.warning("[Corpse] Ticking corpse ${corpse.corpseId} failed (further failures are not repeated): ${failure.stackTraceToString()}")
                }
            }
        }
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
            worldId = corpseLoc.world.uid,
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
        // A death in the void or above the build limit leaves a body nobody can reach: lift it to
        // the nearest standable block of the column, or bound it to the world (see CorpseSpot).
        y = reachableY(location) + definition.verticalOffset.get(player)
        z = blockZ + 0.5
        yaw = when (definition.rotationMode) {
            CorpseRotationMode.RANDOM -> (Math.random() * 360).toFloat()
            CorpseRotationMode.FACE_KILLER -> player.location.yaw + 180f
            CorpseRotationMode.FACE_FORWARD -> location.yaw
        }
    }

    /** Runs on the region owning the death location (the death event), the only place its blocks may be read. */
    private fun reachableY(death: Location): Double {
        val world = death.world ?: return death.y
        val x = death.blockX
        val z = death.blockZ
        return CorpseSpot.resolveY(death.y, world.minHeight, world.maxHeight) { y -> canLieAt(world, x, y, z) }
    }

    /** A body can lie at [y] when the block under it is solid and there is room for it above. */
    private fun canLieAt(world: World, x: Int, y: Int, z: Int): Boolean {
        if (y - 1 < world.minHeight || y + 1 >= world.maxHeight) return false
        if (!world.getBlockAt(x, y - 1, z).type.isSolid) return false
        val at = world.getBlockAt(x, y, z)
        return at.isPassable && at.type != Material.LAVA && world.getBlockAt(x, y + 1, z).isPassable
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
        // Read here, on the victim's thread, like every other Var: the region task below has no player.
        val primaryHeight = settings.spawnParticleHeight.get(victim)
        val primarySpreadH = settings.spawnParticleSpreadHorizontal.get(victim)
        val primarySpreadV = settings.spawnParticleSpreadVertical.get(victim)
        val primarySpeed = settings.spawnParticleSpeed.get(victim)
        val secondaryHeight = settings.spawnParticleSecondaryHeight.get(victim)
        val secondarySpreadH = settings.spawnParticleSecondarySpreadHorizontal.get(victim)
        val secondarySpreadV = settings.spawnParticleSecondarySpreadVertical.get(victim)
        val secondarySpeed = settings.spawnParticleSecondarySpeed.get(victim)

        CorpseScheduler.runAt(loc) {
            val world = loc.world ?: return@runAt

            if (particles) {
                // A misconfigured particle must not take the whole spawn down: some Bukkit
                // particles need extra data and throw without it.
                if (primaryCount > 0) runCatching {
                    world.spawnParticle(
                        settings.spawnParticle, loc.clone().add(0.0, primaryHeight, 0.0),
                        primaryCount, primarySpreadH, primarySpreadV, primarySpreadH, primarySpeed
                    )
                }
                if (secondaryCount > 0) runCatching {
                    world.spawnParticle(
                        settings.spawnParticleSecondary, loc.clone().add(0.0, secondaryHeight, 0.0),
                        secondaryCount, secondarySpreadH, secondarySpreadV, secondarySpreadH, secondarySpeed
                    )
                }
            }
        }
        playNearby(loc, range, settings.spawnSound)
    }

    /**
     * Play [sound] to the players close enough to [loc].
     *
     * Sounds are sent per player rather than through `World.playSound` so the server only talks to
     * clients that can actually hear it, and so the sound entry's own source setting is honoured.
     * Who is close comes from [PlayerPositions], never from reading players' locations here: the
     * caller may be on a region that does not own them. The sound itself is played on the
     * player's own thread.
     */
    private fun playNearby(loc: Location, range: Double, sound: com.typewritermc.engine.paper.utils.Sound) {
        val world = loc.world ?: return
        val worldId = world.uid
        val rangeSquared = range * range
        PlayerPositions.forEach { playerId, spot ->
            if (!ViewerRules.isWithin(spot, worldId, loc.x, loc.y, loc.z, rangeSquared)) return@forEach
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            CorpseScheduler.runForEntity(player) { player.playSound(sound, null) }
        }
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
        itemCount: Int? = null,
        experience: Int? = null,
    ) {
        val entries = Query.find<E>().toList()
        if (entries.isEmpty()) return

        val target = player ?: Bukkit.getPlayer(corpse.playerUUID) ?: return
        // The count and the experience a loot or expiry event reports are those the corpse held
        // before it was emptied, which the caller captured; read here they would be zero.
        val items = itemCount ?: corpse.inventory.count { !it.type.isAir }
        val xp = experience ?: corpse.experience

        // Triggering acts on the player, so it runs on the thread that owns them.
        CorpseScheduler.runForEntity(target) {
            if (!target.isOnline) return@runForEntity
            entries.forEach { entry ->
                runCatching {
                    entry.triggers.triggerEntriesFor(target, corpse.contextFor(entry, items, xp))
                }.onFailure {
                    logger.warning("[Corpse] Failed to fire ${E::class.simpleName} '${entry.id}': ${it.message}")
                }
            }
        }
    }

    private fun CorpseEntity.contextFor(
        entry: com.typewritermc.engine.paper.entry.entries.EventEntry,
        itemCount: Int,
        experience: Int,
    ): InteractionContext = context {
        entry[CorpseContextKeys.OWNER_NAME] = playerName
        entry[CorpseContextKeys.OWNER_UUID] = playerUUID.toString()
        entry[CorpseContextKeys.EXPERIENCE] = experience
        entry[CorpseContextKeys.ITEM_COUNT] = itemCount
        entry[CorpseContextKeys.CORPSE_POSITION] = location.toPosition()
    }

    /** [refreshCorpseAudience] for the player with this id, on their own thread, if they are online. */
    private fun refreshAudienceOf(playerId: UUID) {
        val player = Bukkit.getPlayer(playerId) ?: return
        CorpseScheduler.runForEntity(player) { player.refreshCorpseAudience() }
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
