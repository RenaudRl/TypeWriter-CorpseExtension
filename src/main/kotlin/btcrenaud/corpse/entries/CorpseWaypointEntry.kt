package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import btcrenaud.corpse.manager.CorpseManager
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.utils.asMini
import org.bukkit.entity.Player
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Entry("corpse_waypoint", "Points players back to their corpse", Colors.GREEN, "mdi:navigation")
/**
 * Shows the distance and direction to the player's own corpse on the action bar.
 *
 * Only players who actually have a corpse are in this audience, so it disappears on its own once
 * they recover it.
 *
 * ## How could this be used?
 * Give players a fair chance to find where they died without a map marker system.
 */
class CorpseWaypointEntry(
    override val id: String = "",
    override val name: String = "",
    override val children: List<Ref<AudienceEntry>> = emptyList(),
    @Help("Show the distance and direction on the action bar.")
    @Default("true")
    val showActionBar: Var<Boolean> = ConstVar(true),
    @Help("Action bar text. <distance> is the distance in blocks, <direction> an arrow toward the corpse.")
    val format: Var<String> = ConstVar("<red>☠ <white><distance>m <gray><direction>"),
    override val inverted: Boolean = false,
) : AudienceFilterEntry, Invertible {
    override suspend fun display(): AudienceFilter = CorpseWaypointFilter(ref(), showActionBar, format)
}

class CorpseWaypointFilter(
    ref: Ref<out AudienceFilterEntry>,
    private val showActionBar: Var<Boolean>,
    private val format: Var<String>,
) : AudienceFilter(ref), TickableDisplay {

    override fun filter(player: Player): Boolean {
        val corpse = CorpseManager.getCorpseForPlayer(player.uniqueId) ?: return false
        return corpse.location.world == player.world
    }

    override fun tick() {
        // Re-evaluating every tick is what makes the waypoint appear on death and vanish on
        // recovery, since neither is a Bukkit event the filter could listen to.
        consideredPlayers.forEach { it.refresh() }

        players.forEach { player ->
            if (!showActionBar.get(player)) return@forEach
            val corpse = CorpseManager.getCorpseForPlayer(player.uniqueId) ?: return@forEach
            val target = corpse.location
            if (target.world != player.world) return@forEach

            val dx = target.x - player.location.x
            val dz = target.z - player.location.z
            val distance = sqrt(dx * dx + dz * dz).roundToInt()

            val text = format.get(player)
                .replace("<distance>", distance.toString())
                .replace("<direction>", arrowTowards(player, dx, dz))

            player.sendActionBar(text.asMini())
        }
    }

    /** An arrow in the player's own frame of reference, so it points where they are looking. */
    private fun arrowTowards(player: Player, dx: Double, dz: Double): String {
        val bearing = Math.toDegrees(atan2(-dx, dz))
        val relative = ((bearing - player.location.yaw) % 360 + 360) % 360
        return ARROWS[((relative + 22.5) / 45).toInt() % ARROWS.size]
    }

    companion object {
        /** Clockwise from straight ahead, in 45° steps. */
        private val ARROWS = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
    }
}
