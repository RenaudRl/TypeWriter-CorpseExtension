package btcrenaud.corpse.manager

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** The orchestration of an evacuation, without Bukkit, so its counting, its fallback and its timeout are tested on their own. */
internal object EvacuationFlow {

    /**
     * Brings [corpses] out one by one and completes, whatever happens, with how many are out: at the latest after [timeoutMillis].
     *
     * [hand] hands a corpse over to its owner and answers null when the owner is not there; a corpse that is not handed over (no
     * owner, [hand] false, failed or throwing) goes through [move] instead, so nothing stays behind for a reason the other way
     * could have settled. A corpse counts when its last step answered true in time. [report] gets the number that are not out and
     * the total, once, when some are not.
     */
    fun <T> run(
        corpses: List<T>,
        hand: (T) -> CompletableFuture<Boolean>?,
        move: (T) -> CompletableFuture<Boolean>,
        timeoutMillis: Long,
        report: (notOut: Int, total: Int) -> Unit,
    ): CompletableFuture<Int> {
        if (corpses.isEmpty()) return CompletableFuture.completedFuture(0)
        val outcomes = corpses.map { corpse ->
            val handed = try {
                hand(corpse)
            } catch (_: Exception) {
                null
            }
            if (handed == null) safeMove(corpse, move)
            else handed.handle { ok, failure -> if (failure == null && ok == true) CompletableFuture.completedFuture(true) else safeMove(corpse, move) }
                .thenCompose { it }
        }
        return CompletableFuture.allOf(*outcomes.toTypedArray())
            .completeOnTimeout(null, timeoutMillis, TimeUnit.MILLISECONDS)
            .handle { _, _ ->
                val out = outcomes.count { it.isDone && !it.isCompletedExceptionally && it.getNow(false) }
                if (out < corpses.size) report(corpses.size - out, corpses.size)
                out
            }
    }

    private fun <T> safeMove(corpse: T, move: (T) -> CompletableFuture<Boolean>): CompletableFuture<Boolean> = try {
        move(corpse)
    } catch (_: Exception) {
        CompletableFuture.completedFuture(false)
    }
}
