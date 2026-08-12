package btcrenaud.corpse.utils

import com.typewritermc.engine.paper.entry.entity.FakeEntity
import com.typewritermc.engine.paper.logger
import org.bukkit.entity.Player

/**
 * Opt-in tracing of the corpse interaction chain.
 *
 * A corpse is a packet entity, so a click that "does nothing" can fail at four different places and
 * all of them are silent: the entity never spawned, the client was sent a different entity id than
 * the one we track, the interact packet never reached us, or the id lookup missed. Guessing between
 * those costs a full death-and-test cycle each time, so the chain reports itself instead.
 *
 * Off unless `debugInteractions` is enabled on the corpse settings entry — it is one log line per
 * spawn and per click, which is far too noisy for a live server.
 */
object CorpseDiagnostics {

    @Volatile
    var enabled: Boolean = false

    fun spawned(player: Player, entity: FakeEntity) {
        if (!enabled) return
        logger.info(
            "[Corpse][debug] spawned for ${player.name}: entityId=${entity.entityId} " +
                "type=${entity::class.java.simpleName}"
        )
    }

    /** Called for every interact packet, before the corpse lookup, so a miss is visible. */
    fun interact(player: Player, entityId: Int, action: String, matched: Boolean, knownIds: List<Int>) {
        if (!enabled) return
        logger.info(
            "[Corpse][debug] interact from ${player.name}: entityId=$entityId action=$action " +
                "matched=$matched knownCorpseIds=$knownIds"
        )
    }
}
