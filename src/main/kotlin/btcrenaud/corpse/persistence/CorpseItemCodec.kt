package btcrenaud.corpse.persistence

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.Base64

/**
 * Encodes a corpse inventory to text.
 *
 * Uses Paper's own item serialization, which carries components and custom item data across
 * versions — the same approach the other BTC extensions use for stored inventories.
 *
 * Empty slots are kept as empty tokens: loot slots are addressed by index, so collapsing them
 * would shift every item after the first gap.
 */
object CorpseItemCodec {

    private const val SEPARATOR = ";"

    fun encode(items: List<ItemStack>): String = items.joinToString(SEPARATOR) { item ->
        if (item.type.isAir) "" else Base64.getEncoder().encodeToString(item.serializeAsBytes())
    }

    fun decode(raw: String): List<ItemStack> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).map { token ->
            if (token.isEmpty()) ItemStack(Material.AIR)
            else runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(token)) }
                .getOrElse { ItemStack(Material.AIR) }
        }
    }
}
