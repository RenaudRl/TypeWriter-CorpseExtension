package btcrenaud.corpse.utils

import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.logging.Level

/**
 * Scheduling for both Paper and Folia.
 *
 * Corpses are spawned from a death event, ticked from a repeating task and drop their loot into
 * the world. On Folia each of those touches a different thread: the global region owns the tick,
 * the region owning the corpse location owns anything that spawns items or particles there.
 * [Bukkit.getScheduler] throws on Folia, so every scheduled corpse task goes through here.
 */
object CorpseScheduler {

    /**
     * Paper exposes the Folia scheduler *methods* without the threaded-regions implementation, so
     * the API surface cannot be used to detect Folia. The regionised server class can.
     */
    private val folia: Boolean by lazy {
        runCatching { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess
    }

    val isFolia: Boolean get() = folia

    interface Task {
        fun cancel()
    }

    private class FoliaTask(
        private val task: io.papermc.paper.threadedregions.scheduler.ScheduledTask,
    ) : Task {
        override fun cancel() {
            runCatching { task.cancel() }
        }
    }

    private class PaperTask(private val task: org.bukkit.scheduler.BukkitTask) : Task {
        override fun cancel() {
            runCatching { task.cancel() }
        }
    }

    private fun guarded(what: String, task: () -> Unit) {
        runCatching(task).onFailure {
            plugin.logger.log(Level.SEVERE, "[Corpse] $what failed", it)
        }
    }

    /** Repeating task on the global region. Must not touch entities or blocks directly on Folia. */
    fun runTimer(delayTicks: Long, periodTicks: Long, task: () -> Unit): Task? {
        if (!plugin.isEnabled) return null
        return if (folia) {
            FoliaTask(
                Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, { _ -> guarded("timer", task) }, delayTicks, periodTicks)
            )
        } else {
            PaperTask(
                Bukkit.getScheduler()
                    .runTaskTimer(plugin, Runnable { guarded("timer", task) }, delayTicks, periodTicks)
            )
        }
    }

    /**
     * Run on the region owning [location]. Required for dropping items, spawning experience orbs
     * and spawning particles, all of which mutate the world at a specific place.
     */
    fun runAt(location: Location, task: () -> Unit) {
        if (!plugin.isEnabled) return
        if (folia) {
            Bukkit.getRegionScheduler().execute(plugin, location) { guarded("region task", task) }
        } else if (Bukkit.isPrimaryThread()) {
            guarded("region task", task)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { guarded("region task", task) })
        }
    }

    /** Run on the region owning [location] after [delayTicks]. */
    fun runAtLater(location: Location, delayTicks: Long, task: () -> Unit) {
        if (!plugin.isEnabled) return
        if (folia) {
            Bukkit.getRegionScheduler()
                .runDelayed(plugin, location, { _ -> guarded("delayed region task", task) }, delayTicks)
        } else {
            Bukkit.getScheduler()
                .runTaskLater(plugin, Runnable { guarded("delayed region task", task) }, delayTicks)
        }
    }

    /** Off-thread work: database reads and writes. */
    fun runAsync(task: () -> Unit) {
        if (!plugin.isEnabled) return
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin) { _ -> guarded("async task", task) }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { guarded("async task", task) })
        }
    }
}
