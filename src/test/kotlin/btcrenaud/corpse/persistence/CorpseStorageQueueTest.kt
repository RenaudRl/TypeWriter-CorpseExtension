package btcrenaud.corpse.persistence

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorpseStorageQueueTest {

    @Test
    fun `tasks run in submission order on one thread`() {
        val queue = CorpseStorageQueue("test-storage") { }
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val threads = Collections.synchronizedSet(mutableSetOf<Thread>())
        repeat(200) { i -> queue.submit { order += i; threads += Thread.currentThread() } }
        assertTrue(queue.drain(5_000))
        assertEquals((0 until 200).toList(), order)
        assertEquals(1, threads.size)
    }

    @Test
    fun `a failing task does not stop the ones behind it`() {
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val queue = CorpseStorageQueue("test-storage") { errors += it }
        var ran = false
        queue.submit { error("boom") }
        queue.submit { ran = true }
        assertTrue(queue.drain(5_000))
        assertTrue(ran)
        assertEquals(1, errors.size)
    }

    @Test
    fun `drain waits for queued work and then refuses new work`() {
        val queue = CorpseStorageQueue("test-storage") { }
        val started = CountDownLatch(1)
        var finished = false
        queue.submit { started.countDown(); Thread.sleep(150); finished = true }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(queue.drain(5_000))
        assertTrue(finished)
        assertFalse(queue.submit { })
    }

    @Test
    fun `drain reports a timeout when the work is stuck`() {
        val queue = CorpseStorageQueue("test-storage") { }
        val started = CountDownLatch(1)
        queue.submit { started.countDown(); try { Thread.sleep(10_000) } catch (_: InterruptedException) { } }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertFalse(queue.drain(100))
    }
}