package btcrenaud.corpse.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.ContextKeys
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.EventEntry

/**
 * Corpse lifecycle events.
 *
 * These replace the old single `corpse_action` entry, which was an [com.typewritermc.engine.paper.entry.entries.ActionEntry]
 * with an empty `execute()` fired through a hand-rolled `ActionTrigger`. That path bypassed the
 * engine's trigger pipeline entirely, so the `criteria` and `modifiers` configured on the entry
 * were silently ignored. Going through real events means the engine evaluates them.
 */

@Entry("on_corpse_spawn", "When a corpse spawns", Colors.YELLOW, "mdi:skull-outline")
@ContextKeys(CorpseContextKeys::class)
/**
 * Fires when a player dies and their corpse appears.
 *
 * ## How could this be used?
 * Announce the death, start a "recover your things" quest, or record where the player fell.
 */
class CorpseSpawnEventEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
) : EventEntry

@Entry("on_corpse_loot", "When a corpse is looted", Colors.YELLOW, "mdi:treasure-chest")
@ContextKeys(CorpseContextKeys::class)
/**
 * Fires when a corpse is emptied, by its owner or by someone else.
 *
 * ## How could this be used?
 * Complete a recovery objective, or reward a player who returns loot to its owner.
 */
class CorpseLootEventEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
) : EventEntry

@Entry("on_corpse_expire", "When a corpse expires unlooted", Colors.YELLOW, "mdi:timer-sand-empty")
@ContextKeys(CorpseContextKeys::class)
/**
 * Fires when a corpse reaches the end of its lifetime without being looted.
 *
 * ## How could this be used?
 * Tell the owner their belongings are gone, or apply a death penalty.
 */
class CorpseExpireEventEntry(
    override val id: String = "",
    override val name: String = "",
    override val triggers: List<Ref<TriggerableEntry>> = emptyList(),
) : EventEntry
