package btcrenaud.corpse.persistence

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Corpses whose deletion from storage failed, kept in a small local file.
 *
 * A looted corpse that stays in the store comes back to life after the next restart, items and all.
 * When the delete cannot be done (database down, disk full) the id is written here instead, and the
 * startup removes those corpses before restoring anything. The file is local on purpose: the store
 * that just failed cannot be the place that remembers the failure.
 *
 * Calls come from the storage thread only (see [CorpseStorageQueue]).
 */
class PurgeMarks(private val file: File) {

    private val ids = linkedSetOf<UUID>()

    init {
        if (file.exists()) {
            file.readLines().forEach { line ->
                runCatching { UUID.fromString(line.trim()) }.getOrNull()?.let { ids += it }
            }
        }
    }

    companion object {
        /** The marks in [file], or null (after [onFailure]) when it cannot be read: the restore goes on without marks. */
        fun openOrNull(file: File, onFailure: (Throwable) -> Unit): PurgeMarks? =
            try {
                PurgeMarks(file)
            } catch (failure: Exception) {
                onFailure(failure)
                null
            }
    }

    fun all(): Set<UUID> = ids.toSet()

    fun contains(corpseId: UUID): Boolean = corpseId in ids

    /** Throws when the mark cannot be written. */
    fun add(corpseId: UUID) {
        if (ids.add(corpseId)) write()
    }

    fun remove(corpseId: UUID) {
        if (ids.remove(corpseId)) write()
    }

    private fun write() {
        file.parentFile?.mkdirs()
        val temporary = file.resolveSibling("${file.name}.tmp")
        temporary.writeText(ids.joinToString("\n"))
        try {
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
