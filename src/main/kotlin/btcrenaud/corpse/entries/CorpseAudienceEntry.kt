package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Entry
import btcrenaud.corpse.manager.CorpseManager
import com.typewritermc.engine.paper.entry.entries.*
import org.bukkit.entity.Player

@Entry("corpse_owner_audience", "Players who have a corpse waiting", Colors.GREEN, "mdi:skull")
/**
 * Filters an audience down to the players who currently have at least one corpse in the world.
 *
 * ## How could this be used?
 * Show a recovery hint, a waypoint or a boss bar only to players who still have belongings to
 * collect, and drop them from it the moment they do.
 */
class CorpseOwnerAudienceEntry(
    override val id: String = "",
    override val name: String = "",
    override val children: List<Ref<AudienceEntry>> = emptyList(),
    override val inverted: Boolean = false,
) : AudienceFilterEntry, Invertible {
    override suspend fun display(): AudienceFilter = CorpseOwnerAudienceFilter(ref())
}

class CorpseOwnerAudienceFilter(
    ref: Ref<out AudienceFilterEntry>,
) : AudienceFilter(ref) {
    override fun filter(player: Player): Boolean =
        CorpseManager.getCorpseForPlayer(player.uniqueId) != null

    /**
     * Corpse creation and removal are not Bukkit events, so the filter cannot react to them on its
     * own. [CorpseManager] calls this whenever a player's corpse set changes.
     */
    fun refreshOwner(player: Player) = player.refresh()
}
