package btcrenaud.corpse.text

import kotlin.math.atan2

/**
 * The symbol that points a player toward a corpse.
 *
 * The symbols are an admin's choice (a server may prefer letters or its own font glyphs), so this
 * only decides which of the eight to show.
 */
object DirectionArrows {

    /** Number of symbols: one per 45 degrees, clockwise from straight ahead. */
    const val COUNT = 8

    /** Clockwise from straight ahead. Also the field's default in the editor. */
    val DEFAULT: List<String> = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")

    /**
     * Angle from the player's facing to the corpse, in `[0, 360)` degrees, clockwise.
     *
     * Minecraft's yaw 0 faces +Z and grows clockwise seen from above, so a corpse straight ahead
     * has a relative angle of 0 whatever way the player looks.
     *
     * @param dx corpse x minus player x
     * @param dz corpse z minus player z
     * @param yaw the player's yaw in degrees
     */
    fun relativeAngle(dx: Double, dz: Double, yaw: Float): Double {
        val bearing = Math.toDegrees(atan2(-dx, dz))
        return ((bearing - yaw) % 360 + 360) % 360
    }

    /** Index into the eight symbols for an angle from [relativeAngle]. */
    fun indexFor(relativeAngle: Double): Int = ((relativeAngle + 22.5) / 45).toInt() % COUNT

    /**
     * The configured symbols when there are exactly [COUNT] of them, otherwise [DEFAULT].
     *
     * A list of the wrong length cannot be mapped onto the eight directions; falling back keeps the
     * waypoint readable instead of failing on every tick.
     */
    fun usable(configured: List<String>): List<String> =
        if (configured.size == COUNT) configured else DEFAULT
}
