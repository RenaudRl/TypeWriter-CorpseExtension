package btcrenaud.corpse.persistence

import btcrenaud.corpse.utils.Failures

/** Runs a storage operation a limited number of times. */
object BoundedRetry {

    /**
     * Runs [operation] until it succeeds or [attempts] runs out.
     *
     * @param pauseMillis pause before attempt n+1 is [pauseMillis] * n, so a database that is briefly
     * unreachable gets a little longer each time. The storage thread waits; nothing else uses it.
     * @param onFailure called after every failed attempt with its number (from 1).
     * @return null on success, otherwise the failure of the last attempt.
     */
    fun run(
        attempts: Int,
        pauseMillis: Long,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        onFailure: (attempt: Int, failure: Throwable) -> Unit = { _, _ -> },
        operation: () -> Unit,
    ): Throwable? {
        var last: Throwable? = null
        for (attempt in 1..attempts.coerceAtLeast(1)) {
            try {
                operation()
                return null
            } catch (failure: Throwable) {
                if (Failures.isFatal(failure)) throw failure
                last = failure
                onFailure(attempt, failure)
                if (attempt < attempts) sleep(pauseMillis * attempt)
            }
        }
        return last
    }
}
