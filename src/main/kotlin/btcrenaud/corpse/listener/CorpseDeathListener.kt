package btcrenaud.corpse.listener

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction
import btcrenaud.corpse.death.DeathLoot
import btcrenaud.corpse.entries.CorpseLootMode
import btcrenaud.corpse.entries.CorpseSettingsEntry
import btcrenaud.corpse.gui.CorpseGUI
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.corpse.text.sendCorpseText
import btcrenaud.corpse.utils.CorpseDiagnostics
import btcrenaud.corpse.utils.CorpseScheduler
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.events.AsyncFakeEntityInteract
import com.typewritermc.engine.paper.logger
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CorpseDeathListener : Listener {

    private var initialized = false

    fun initialize(plugin: Plugin) {
        if (initialized) return
        plugin.server.pluginManager.registerEvents(this, plugin)
        initialized = true
    }

    fun shutdown() {
        PlayerDeathEvent.getHandlerList().unregister(this)
        PlayerRespawnEvent.getHandlerList().unregister(this)
        PlayerQuitEvent.getHandlerList().unregister(this)
        AsyncFakeEntityInteract.getHandlerList().unregister(this)
        lastInteraction.clear()
        initialized = false
    }

    private fun settings(): CorpseSettingsEntry = CorpseManager.settings()

    /** Last accepted corpse interaction per player, used to collapse a burst into one gesture. */
    private val lastInteraction = ConcurrentHashMap<UUID, Long>()

    /** Whether this packet is a new gesture rather than a repeat of the one just handled. */
    private fun claimInteraction(playerId: UUID, cooldownMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastInteraction.put(playerId, now)
        return InteractionRules.isNewGesture(previous, now, cooldownMillis)
    }

    /**
     * Turns a death into a corpse.
     *
     * Runs last (MONITOR) so it sees what every other plugin decided, and skips a death another
     * plugin cancelled (a totem or revive plugin): such a player is not dead, and a corpse would
     * take their gear while they stand there alive.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val cfg = settings()

        // Outside the allowed worlds the death stays vanilla: items drop, orbs spawn, nothing else.
        val world = player.world
        if (!cfg.worldFilter().allows(world.name, world.key().asString())) return

        // The event's own drop list rather than the live inventory: it already reflects
        // keepInventory, Curse of Vanishing and items other plugins chose to keep for the player.
        val loot = DeathLoot.of<ItemStack>(
            drops = event.drops,
            droppedExperience = event.droppedExp,
            keepInventory = event.keepInventory,
            keepLevel = event.keepLevel,
            isEmpty = { it.type.isAir || it.amount <= 0 },
            copy = { it.clone() },
        ) ?: return
        if (loot.items.isEmpty() && loot.experience == 0) return

        // Picked from the death world, so a definition can be scoped to the nether or an event map.
        val definition = CorpseManager.definitionFor(world.name, world.key().asString())

        try {
            CorpseManager.spawnCorpse(player, player.location, loot.items, loot.experience, cfg, definition)
        } catch (failure: Exception) {
            // Nothing was registered, and the drops are untouched: the death falls back to vanilla
            // instead of losing the player's items.
            logger.warning("[Corpse] Could not create the corpse of ${player.name}; the items drop normally: ${failure.stackTraceToString()}")
            return
        }

        // The corpse owns the loot now. Cleared only after it exists, never before.
        event.drops.clear()
        if (!event.keepLevel) event.droppedExp = 0
    }

    /**
     * Corpses are packet entities, so an interaction arrives as a client packet that the engine
     * republishes as [AsyncFakeEntityInteract]. Matching on the entity id is what makes a click
     * land on *this* corpse and nothing else — the previous proximity guess fired on any
     * right-click within four blocks, including placing a block or opening a chest.
     */
    @EventHandler
    fun onCorpseInteract(event: AsyncFakeEntityInteract) {
        val player = event.player

        // Logged before the action filter so a click that never produces INTERACT is still visible.
        val corpse = CorpseManager.getCorpseByEntity(player.uniqueId, event.entityId)
        CorpseDiagnostics.interact(
            player,
            event.entityId,
            event.action.name,
            corpse != null,
            CorpseManager.knownEntityIdsFor(player.uniqueId),
        )

        // Both right-click actions are accepted: a corpse is an INTERACTION entity, and clicking one
        // yields INTERACT_AT alone — filtering for INTERACT rejected every click. ATTACK is not a
        // loot gesture, so it is the only action left out.
        if (event.action != InteractAction.INTERACT && event.action != InteractAction.INTERACT_AT) return
        if (corpse == null) return

        val settings = settings()

        // The client repeats the packet while the button is held, and a right-click can also yield
        // both actions at once. Without this, one gesture opened the menu several times over — or,
        // in direct mode, looted twice.
        if (!claimInteraction(player.uniqueId, settings.interactionCooldownMillis.get(player).toLong())) return

        // Sneaking reaches the other mode, so a player can always get at both without a command.
        val swap = player.isSneaking && settings.sneakSwapsLootMode.get(player)
        val mode = when (settings.lootMode) {
            CorpseLootMode.GUI -> if (swap) CorpseLootMode.DIRECT else CorpseLootMode.GUI
            CorpseLootMode.DIRECT -> if (swap) CorpseLootMode.GUI else CorpseLootMode.DIRECT
            CorpseLootMode.DROP -> if (swap) CorpseLootMode.GUI else CorpseLootMode.DROP
        }
        val reach = settings.interactionReach.get(player)

        // The event is async and looting mutates the world, so hop onto the corpse's region.
        CorpseScheduler.runAt(corpse.location) {
            if (!isInReach(player, corpse.location, reach)) return@runAt
            when (mode) {
                CorpseLootMode.GUI -> openGui(player, corpse, settings)
                CorpseLootMode.DIRECT -> CorpseManager.lootCorpse(corpse, player, handToLooter = true)
                CorpseLootMode.DROP -> CorpseManager.lootCorpse(corpse, player, handToLooter = false)
            }
        }
    }

    /**
     * The click packet of a packet-only entity is not range-checked by the server, so a modified
     * client could loot a corpse it merely sees from across the map.
     */
    private fun isInReach(player: Player, target: org.bukkit.Location, reach: Double): Boolean {
        val distanceSquared = if (player.world == target.world) player.location.distanceSquared(target) else null
        return InteractionRules.isWithinReach(distanceSquared, reach)
    }

    private fun openGui(
        player: Player,
        corpse: btcrenaud.corpse.entity.CorpseEntity,
        settings: CorpseSettingsEntry,
    ) {
        // Told before the menu opens, not after the first click on an item that would be refused.
        if (!corpse.canLoot(player)) {
            player.sendCorpseText(settings.cannotLootMessage.get(player))
            return
        }
        CorpseGUI.openCorpseInventory(player, corpse)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val settings = settings()
        if (!settings.notifyOnRespawn.get(player)) return

        val corpse = CorpseManager.getCorpseForPlayer(player.uniqueId) ?: return
        val loc = corpse.location
        player.sendCorpseText(
            settings.respawnMessage.get(player),
            mapOf(
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString(),
                "world" to (loc.world?.name ?: settings.unknownWorldLabel.get(player)),
            ),
        )
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        lastInteraction.remove(event.player.uniqueId)
        CorpseGUI.forget(event.player.uniqueId)
    }
}
