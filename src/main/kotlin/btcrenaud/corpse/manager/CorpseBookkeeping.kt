package btcrenaud.corpse.manager

import java.util.UUID

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

    /** The end of the log line written when a corpse could not be deleted from storage. */
    fun deleteFailureOutcome(marked: Boolean, marksAvailable: Boolean): String = when {
        marked -> "it is marked for purging at the next startup"
        !marksAvailable -> "no purge mark written (purge marks unavailable), it WILL come back after a restart"
        else -> "it WILL come back after a restart"
    }
}
