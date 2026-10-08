package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import btcrenaud.corpse.manager.CorpseManager
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.entries.*
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.quest.entries.ObjectiveEntry
import com.typewritermc.quest.entries.QuestEntry
import org.bukkit.entity.Player
import java.util.Optional

@Entry("corpse_recovery_objective", "Recover your corpse", Colors.ORANGE, "mdi:skull-crossbones")
/**
 * An objective that stays active for as long as the player has a corpse waiting for them.
 *
 * It completes on its own when the corpse is looted or expires, so it needs no separate
 * completion trigger.
 *
 * ## How could this be used?
 * Add a "get your gear back" step to a hardcore quest line.
 */
class CorpseRecoveryObjectiveEntry(
    override val id: String = "",
    override val name: String = "",
    override val quest: Ref<QuestEntry> = emptyRef(),
    override val criteria: List<Criteria> = emptyList(),
    @Help("The text shown to the player.")
    @Colored
    @Placeholder
    @Default("\"<red>Recover your belongings\"")
    override val display: Var<String> = ConstVar("<red>Recover your belongings"),
    override val children: List<Ref<AudienceEntry>> = emptyList(),
    override val priorityOverride: Optional<Int> = Optional.empty(),
) : ObjectiveEntry {
    override suspend fun display(): AudienceFilter = CorpseRecoveryObjectiveFilter(ref(), criteria)
}

class CorpseRecoveryObjectiveFilter(
    ref: Ref<out AudienceFilterEntry>,
    private val criteria: List<Criteria>,
) : AudienceFilter(ref), TickableDisplay {

    override fun filter(player: Player): Boolean =
        criteria.matches(player) && CorpseManager.getCorpseForPlayer(player.uniqueId) != null

    override fun tick() {
        // Gaining and losing a corpse are not Bukkit events, so the objective has to re-check
        // rather than wait to be told.
        consideredPlayers.forEach { it.refresh() }
    }
}
