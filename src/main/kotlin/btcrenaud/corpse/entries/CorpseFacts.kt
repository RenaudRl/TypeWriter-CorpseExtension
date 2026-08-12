package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import btcrenaud.corpse.manager.CorpseManager
import com.typewritermc.engine.paper.entry.entries.GroupEntry
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.facts.FactData
import org.bukkit.entity.Player

@Entry("has_corpse", "Whether the player has a corpse waiting", Colors.PURPLE, "mdi:skull")
/**
 * `1` while the player has at least one corpse in the world, `0` otherwise.
 *
 * ## How could this be used?
 * Gate a "recover your belongings" quest, or block a warp while the player still has loot to fetch.
 */
class HasCorpseFactEntry(
    override val id: String = "",
    override val name: String = "",
    @MultiLine
    @Help("Returns 1 when the player has an active corpse, 0 otherwise.")
    override val comment: String = "",
    @Help("If left empty, every player has its own group.")
    override val group: Ref<GroupEntry> = emptyRef(),
) : ReadableFactEntry {
    override fun readSinglePlayer(player: Player): FactData =
        FactData(if (CorpseManager.getCorpseForPlayer(player.uniqueId) != null) 1 else 0)
}

@Entry("corpse_count", "How many corpses the player has", Colors.PURPLE, "mdi:counter")
/**
 * The number of corpses this player currently has in the world.
 *
 * ## How could this be used?
 * Escalate a death penalty when a player keeps dying before recovering their previous corpse.
 */
class CorpseCountFactEntry(
    override val id: String = "",
    override val name: String = "",
    @MultiLine
    @Help("Returns the number of corpses the player currently has.")
    override val comment: String = "",
    @Help("If left empty, every player has its own group.")
    override val group: Ref<GroupEntry> = emptyRef(),
) : ReadableFactEntry {
    override fun readSinglePlayer(player: Player): FactData =
        FactData(CorpseManager.activeCorpses().count { it.playerUUID == player.uniqueId })
}
