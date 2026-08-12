package btcrenaud.corpse.listener

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction
import btcrenaud.corpse.entries.CorpseLootMode
import btcrenaud.corpse.entries.CorpseSettingsEntry
import btcrenaud.corpse.gui.CorpseGUI
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.corpse.utils.CorpseDiagnostics
import btcrenaud.corpse.utils.CorpseScheduler
import com.typewritermc.engine.paper.events.AsyncFakeEntityInteract
import com.typewritermc.engine.paper.utils.asMini
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
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
        AsyncFakeEntityInteract.getHandlerList().unregister(this)
        lastInteraction.clear()
        initialized = false
    }

    private fun settings(): CorpseSettingsEntry = CorpseManager.settings()

    /** Last accepted corpse interaction per player, used to collapse a burst into one gesture. */
    private val lastInteraction = ConcurrentHashMap<UUID, Long>()

    private const val INTERACTION_COOLDOWN_MS = 250L

    /**
     * Whether this packet is a new gesture rather than a repeat of the one just handled.
     *
     * Kept short enough that deliberate repeated clicks still register, long enough to swallow the
     * packet burst a single click produces.
     */
    private fun claimInteraction(playerId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastInteraction.put(playerId, now)
        return previous == null || now - previous >= INTERACTION_COOLDOWN_MS
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val cfg = settings()
        // Picked from the death world, so a definition can be scoped to the nether or an event map.
        val definition = CorpseManager.definitionFor(player.world.name)

        // Capture the loot before the server clears it, then take over the drops entirely.
        val allItems = buildList {
            addAll(player.inventory.contents.mapNotNull { it?.clone() })
            addAll(player.inventory.armorContents.mapNotNull { it?.clone() })
            addAll(player.inventory.extraContents.mapNotNull { it?.clone() })
        }
        val exp = event.droppedExp

        event.drops.clear()
        event.droppedExp = 0

        CorpseManager.spawnCorpse(player, player.location, allItems, exp, cfg, definition)
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

        // The client repeats the packet while the button is held, and a right-click can also yield
        // both actions at once. Without this, one gesture opened the menu several times over — or,
        // in direct mode, looted twice.
        if (!claimInteraction(player.uniqueId)) return

        val settings = settings()

        // Sneaking reaches the other mode, so a player can always get at both without a command.
        val swap = player.isSneaking && settings.sneakSwapsLootMode.get(player)
        val mode = when (settings.lootMode) {
            CorpseLootMode.GUI -> if (swap) CorpseLootMode.DIRECT else CorpseLootMode.GUI
            CorpseLootMode.DIRECT -> if (swap) CorpseLootMode.GUI else CorpseLootMode.DIRECT
            CorpseLootMode.DROP -> if (swap) CorpseLootMode.GUI else CorpseLootMode.DROP
        }

        // The event is async and looting mutates the world, so hop onto the corpse's region.
        CorpseScheduler.runAt(corpse.location) {
            when (mode) {
                CorpseLootMode.GUI -> CorpseGUI.openCorpseInventory(player, corpse)
                CorpseLootMode.DIRECT -> CorpseManager.lootCorpse(corpse, player, handToLooter = true)
                CorpseLootMode.DROP -> CorpseManager.lootCorpse(corpse, player, handToLooter = false)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!settings().notifyOnRespawn.get(player)) return

        val corpse = CorpseManager.getCorpseForPlayer(player.uniqueId) ?: return
        val loc = corpse.location
        val message = settings().respawnMessage.get(player)
            .replace("<x>", loc.blockX.toString())
            .replace("<y>", loc.blockY.toString())
            .replace("<z>", loc.blockZ.toString())
            .replace("<world>", loc.world?.name ?: "unknown")
        player.sendMessage(message.asMini())
    }
}
