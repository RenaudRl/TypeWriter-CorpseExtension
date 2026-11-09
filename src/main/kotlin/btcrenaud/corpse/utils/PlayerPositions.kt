package btcrenaud.corpse.utils

import btcrenaud.corpse.entity.PlayerSpot
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Where each online player is, kept up to date by one task per player on that player's own thread.
 *
 * On Folia a player's location may only be read from the region that owns them, and the corpse tick
 * runs on the global region. Reading `player.location` for every player there raced with the
 * regions moving them. This holds plain values instead: each player's task writes their spot, and
 * any thread reads it.
 */
object PlayerPositions {

    /**
     * Ticks between two samples. Decides how late a corpse appears or disappears for a player who
     * walks into or out of range: a quarter of a second. Technical pacing, not a gameplay value.
     */
    private const val SAMPLE_PERIOD_TICKS = 5L

    private val spots = ConcurrentHashMap<UUID, PlayerSpot>()
    private val tasks = ConcurrentHashMap<UUID, CorpseScheduler.Task>()

    /** Starts sampling [player]. Safe from any thread; a player already tracked is left as is. */
    fun track(player: Player) {
        val id = player.uniqueId
        if (tasks.containsKey(id)) return
        val task = CorpseScheduler.runForEntityTimer(player, SAMPLE_PERIOD_TICKS) {
            if (player.isOnline) sample(player) else untrack(id)
        } ?: return
        if (tasks.putIfAbsent(id, task) != null) task.cancel()
    }

    fun untrack(playerId: UUID) {
        tasks.remove(playerId)?.cancel()
        spots.remove(playerId)
    }

    fun untrackAll() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        spots.clear()
    }

    fun spot(playerId: UUID): PlayerSpot? = spots[playerId]

    fun forEach(action: (UUID, PlayerSpot) -> Unit) = spots.forEach { (id, spot) -> action(id, spot) }

    // Only ever called by the player's own timer, which runs on the thread that owns them.
    private fun sample(player: Player) {
        val location = player.location
        val world = location.world ?: return
        spots[player.uniqueId] = PlayerSpot(world.uid, location.x, location.y, location.z)
    }
}
