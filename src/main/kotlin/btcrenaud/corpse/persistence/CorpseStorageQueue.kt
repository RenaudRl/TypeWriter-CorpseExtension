package btcrenaud.corpse.persistence

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Runs every storage operation one after the other, in the order they were requested.
 *
 * Saves and deletes used to go through the server's async scheduler, which is a pool: two saves of
 * the same corpse could finish in either order, so an older snapshot could overwrite a newer one
 * (handing back an item already taken after a restart), a delete could be undone by a save that
 * ran after it, and two writes to the JSON file at once could corrupt it. One thread, one queue.
 *
 * It is also what lets a reload wait for the last writes: [drain] returns once everything queued
 * before it has run.
 */
class CorpseStorageQueue(
    threadName: String = "Corpse-Storage",
    private val onError: (Throwable) -> Unit,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, threadName).apply { isDaemon = true }
    }

    /** Queues [task]. Returns false when the queue no longer accepts work (after [drain]). */
    fun submit(task: () -> Unit): Boolean = try {
        executor.execute {
            try {
                task()
            } catch (failure: Throwable) {
                // One failed write must not stop the writes queued behind it.
                onError(failure)
            }
        }
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    /**
     * Stops accepting work and waits for what is queued to finish.
     *
     * @return false if [timeoutMillis] ran out first; the remaining tasks are then abandoned.
     */
    fun drain(timeoutMillis: Long): Boolean {
        executor.shutdown()
        val finished = try {
            executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) executor.shutdownNow()
        return finished
    }
}
