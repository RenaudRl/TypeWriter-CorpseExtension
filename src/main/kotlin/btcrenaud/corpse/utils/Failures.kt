package btcrenaud.corpse.utils

/** Which failures an isolated step may swallow after logging them. */
object Failures {

    /**
     * Whether [failure] must not be swallowed: the JVM is going down, or the thread is being stopped.
     *
     * Everything else is recoverable for a step that merely follows a successful action — including
     * a [LinkageError] such as a `NoClassDefFoundError` from a model backend that is not installed,
     * which is an [Error] but only breaks that one step.
     */
    @Suppress("DEPRECATION", "removal")
    fun isFatal(failure: Throwable): Boolean =
        failure is VirtualMachineError || failure is ThreadDeath || failure is InterruptedException
}