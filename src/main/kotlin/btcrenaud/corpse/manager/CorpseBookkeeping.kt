package btcrenaud.corpse.manager

import java.util.UUID

/** What bringing a stored corpse back came to: back, destroyed on purpose because it expired, or not restored (it stays in storage). */
internal enum class RestoreOutcome { BROUGHT_BACK, DESTROYED_ON_EXPIRY, NOT_RESTORED }

/** Decisions of [CorpseManager] that need no Bukkit, kept here so they can be tested on their own. */
internal object CorpseBookkeeping {

    /**
     * The experience a failed hand-over must give back to the corpse. Experience already handed to
     * the looter is theirs: restoring it would create it twice.
     */
    fun experienceToPutBack(taken: Int, alreadyGivenToLooter: Boolean): Int =
        if (alreadyGivenToLooter) 0 else taken

    /**
     * Whether a task queued before a shutdown now runs after it. Such a task must leave the
     * registry and the claims alone: they belong to the run that followed (a reload restores the
     * same corpse id), and the storage it would delete from is gone.
     */
    fun isStale(queuedInEpoch: Int, currentEpoch: Int, closing: Boolean, pluginEnabled: Boolean): Boolean =
        queuedInEpoch != currentEpoch || closing || !pluginEnabled

    /** One SEVERE line per unreadable item: the raw text is the only copy left once the corpse is deleted. */
    fun unreadableReport(corpseId: UUID, unreadable: Map<Int, String>): List<String> =
        unreadable.map { (slot, raw) ->
            "[Corpse] Corpse $corpseId is being deleted with an unreadable item in slot $slot, its stored text was: $raw"
        }

    /** The corpses of [corpses] that lie in the world called [worldName], by the world name [worldOf] reads. */
    fun <T> inWorld(corpses: Collection<T>, worldName: String, worldOf: (T) -> String?): List<T> =
        corpses.filter { worldOf(it) == worldName }

    /**
     * Hands [items] over one by one: [give] returns what did not fit (null when everything went in). Each item leaves [pending]
     * as soon as it was offered and its leftover, if any, lands in [surplus] (the part the caller drops at the owner's feet),
     * so a [give] that fails half way leaves in [pending] exactly what was not offered, and nothing is in neither map.
     */
    fun <T> handOver(items: Map<Int, T>, pending: MutableMap<Int, T>, surplus: MutableMap<Int, T>, give: (T) -> T?) {
        items.forEach { (slot, item) ->
            val leftover = give(item)
            pending.remove(slot)
            if (leftover != null) surplus[slot] = leftover
        }
    }

    /** The WARN written for a stored corpse whose world is not loaded: its owner and what they lose sight of. */
    fun dormantReport(corpseId: UUID, ownerName: String, itemCount: Int, worldUid: UUID): String =
        "[Corpse] Corpse $corpseId of $ownerName ($itemCount item(s)) is not restored: its world $worldUid is not loaded. " +
            "It stays in storage, but nobody can reach it until that world is loaded again; if that world is deleted (a dungeon run world " +
            "is, with its folder) the items are lost with it."

    /**
     * Whether a relocation is over after its stored copy was restored: a corpse brought back is, and so is one the settings destroy at
     * its expiry (nothing to keep, not a failure to report). Anything else leaves the items only in the record at the destination,
     * which the old corpse must take back.
     */
    fun relocationSettled(outcome: RestoreOutcome): Boolean = outcome != RestoreOutcome.NOT_RESTORED

    /** The end of the log line written when a corpse could not be deleted from storage. */
    fun deleteFailureOutcome(marked: Boolean, marksAvailable: Boolean): String = when {
        marked -> "it is marked for purging at the next startup"
        !marksAvailable -> "no purge mark written (purge marks unavailable), it WILL come back after a restart"
        else -> "it WILL come back after a restart"
    }
}
