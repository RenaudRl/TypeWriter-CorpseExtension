package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.corpse.text.DirectionArrows
import btcrenaud.corpse.text.corpseComponent
import com.typewritermc.engine.paper.entry.entries.*
import org.bukkit.entity.Player
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
    @Help("Action bar text. <distance> is the distance in blocks, <direction> a symbol pointing toward the corpse.")
    @Colored
    @Placeholder
    @Default("\"<red>☠ <white><distance>m <gray><direction>\"")
    val format: Var<String> = ConstVar("<red>☠ <white><distance>m <gray><direction>"),
    @Help(
        "The eight symbols standing for <direction>, clockwise from straight ahead: " +
            "ahead, ahead-right, right, behind-right, behind, behind-left, left, ahead-left. " +
            "Anything but exactly eight entries is ignored and the default arrows are used."
    )
    @Default("[\"↑\",\"↗\",\"→\",\"↘\",\"↓\",\"↙\",\"←\",\"↖\"]")
    val directionArrows: List<String> = DirectionArrows.DEFAULT,
    override val inverted: Boolean = false,
) : AudienceFilterEntry, Invertible {
    override suspend fun display(): AudienceFilter =
        CorpseWaypointFilter(ref(), showActionBar, format, DirectionArrows.usable(directionArrows))
}

class CorpseWaypointFilter(
    ref: Ref<out AudienceFilterEntry>,
    private val showActionBar: Var<Boolean>,
    private val format: Var<String>,
    private val arrows: List<String>,
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
            val angle = DirectionArrows.relativeAngle(dx, dz, player.location.yaw)

            player.sendActionBar(
                player.corpseComponent(
                    format.get(player),
                    values = mapOf("distance" to distance.toString()),
                    // The symbols are the admin's own text, so they may carry colours.
                    trusted = mapOf("direction" to arrows[DirectionArrows.indexFor(angle)]),
                )
            )
        }
    }
}
