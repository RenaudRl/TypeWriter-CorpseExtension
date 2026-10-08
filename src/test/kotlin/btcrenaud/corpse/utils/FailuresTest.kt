package btcrenaud.corpse.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailuresTest {

    @Test
    fun `a missing optional backend class is recoverable`() {
        assertFalse(Failures.isFatal(NoClassDefFoundError("com/example/Backend")))
        assertFalse(Failures.isFatal(LinkageError()))
    }

    @Test
    fun `ordinary exceptions are recoverable`() {
        assertFalse(Failures.isFatal(IllegalStateException()))
        assertFalse(Failures.isFatal(RuntimeException()))
    }

    @Test
    fun `a dying JVM or an interrupted thread is never swallowed`() {
        assertTrue(Failures.isFatal(OutOfMemoryError()))
        assertTrue(Failures.isFatal(StackOverflowError()))
        assertTrue(Failures.isFatal(InterruptedException()))
    }
}