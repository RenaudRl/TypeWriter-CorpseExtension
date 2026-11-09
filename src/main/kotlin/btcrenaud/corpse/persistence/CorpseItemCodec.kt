package btcrenaud.corpse.persistence

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.Base64

/** A stored item that could not be read back: its raw text is kept so it is not lost. */
data class UnreadableItem(val token: String, val cause: String)

/**
 * What [CorpseItemCodec] read.
 *
 * @property items one entry per slot; an unreadable slot holds the empty value.
 * @property unreadable slot index to the raw text of the item that could not be read.
 */
data class DecodedItems<T>(val items: List<T>, val unreadable: Map<Int, UnreadableItem>)

/**
 * Encodes a corpse inventory to text.
 *
 * Uses Paper's own item serialization, which carries components and custom item data across
 * versions — the same approach the other BTC extensions use for stored inventories.
 *
 * Empty slots are kept as empty tokens: loot slots are addressed by index, so collapsing them
 * would shift every item after the first gap.
 *
 * An item that cannot be read (a plugin that defined it is gone, a format the server no longer
 * understands) is reported and its raw text is written back as it was by the next save, so a fixed
 * server can still read it. It used to be replaced by air without a word, and the next save erased it.
 */
object CorpseItemCodec {

    private const val SEPARATOR = ";"

    fun encode(items: List<ItemStack>, unreadable: Map<Int, String> = emptyMap()): String =
        encodeTokens(items, { it.type.isAir }, { it.serializeAsBytes() }, unreadable)

    fun decode(raw: String): DecodedItems<ItemStack> =
        decodeTokens(raw, { ItemStack(Material.AIR) }, { ItemStack.deserializeBytes(it) })

    fun <T> encodeTokens(
        items: List<T>,
        isEmpty: (T) -> Boolean,
        write: (T) -> ByteArray,
        unreadable: Map<Int, String> = emptyMap(),
    ): String = items.withIndex().joinToString(SEPARATOR) { (index, item) ->
        if (isEmpty(item)) unreadable[index].orEmpty() else Base64.getEncoder().encodeToString(write(item))
    }

    fun <T> decodeTokens(raw: String, empty: () -> T, read: (ByteArray) -> T): DecodedItems<T> {
        if (raw.isBlank()) return DecodedItems(emptyList(), emptyMap())
        val unreadable = linkedMapOf<Int, UnreadableItem>()
        val items = raw.split(SEPARATOR).mapIndexed { index, token ->
            if (token.isEmpty()) {
                empty()
            } else {
                try {
                    read(Base64.getDecoder().decode(token))
                } catch (failure: Exception) {
                    unreadable[index] = UnreadableItem(token, "${failure::class.simpleName}: ${failure.message}")
                    empty()
                }
            }
        }
        return DecodedItems(items, unreadable)
    }
}
