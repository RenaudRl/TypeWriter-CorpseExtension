package btcrenaud.corpse.entity

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Where a player was, in plain values, when their own thread last looked. */
data class PlayerSpot(val worldId: UUID, val x: Double, val y: Double, val z: Double)

/** The in-range decision, free of any server object so it can be tested. */
object ViewerRules {

    /** Whether [spot] is in [worldId] and no farther than √[rangeSquared] from (x, y, z). */
    fun isWithin(spot: PlayerSpot?, worldId: UUID, x: Double, y: Double, z: Double, rangeSquared: Double): Boolean {
        if (spot == null || spot.worldId != worldId) return false
        val dx = spot.x - x
        val dy = spot.y - y
        val dz = spot.z - z
        return dx * dx + dy * dy + dz * dz <= rangeSquared
    }
}

/**
 * How long to leave a viewer alone after the corpse could not be spawned for them.
 *
 * The display retries every tick; a backend that throws at every spawn used to log a stack trace
 * 20 times a second per player. The delay doubles from [firstDelayMillis] up to [maxDelayMillis].
 * Technical pacing of a retry, not a gameplay value.
 */
class SpawnBackoff(
    private val firstDelayMillis: Long = 1_000L,
    private val maxDelayMillis: Long = 60_000L,
) {
    private class State(val failures: Int, val retryAt: Long)

    private val states = ConcurrentHashMap<UUID, State>()

    fun allowed(playerId: UUID, nowMillis: Long): Boolean {
        val state = states[playerId] ?: return true
        return nowMillis >= state.retryAt
    }

    /** Records a failure and returns how many in a row this viewer has had. */
    fun failed(playerId: UUID, nowMillis: Long): Int {
        val failures = (states[playerId]?.failures ?: 0) + 1
        val delay = (firstDelayMillis shl (failures - 1).coerceAtMost(20)).coerceAtMost(maxDelayMillis)
        states[playerId] = State(failures, nowMillis + delay)
        return failures
    }

    fun forget(playerId: UUID) {
        if (states.isNotEmpty()) states.remove(playerId)
    }
}
