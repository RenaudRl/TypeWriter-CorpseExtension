package btcrenaud.corpse.gui

import btcrenaud.corpse.entity.CorpseEntity
import btcrenaud.corpse.manager.CorpseManager
import btcrenaud.gui.GuiType
import btcrenaud.gui.InventorySize
import btcrenaud.gui.api.GuiSlot
import btcrenaud.gui.api.InteractionType
import btcrenaud.gui.api.MenuDefinition
import btcrenaud.gui.api.SimpleLayout
import btcrenaud.gui.services.MenuSessionService
import com.typewritermc.engine.paper.entry.entries.get
import com.typewritermc.engine.paper.utils.asMini
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val LOOT_ROWS = 5
private const val PAGE_SIZE = LOOT_ROWS * 9
private const val CONTROL_ROW = 5
private const val PREV_X = 0
private const val NEXT_X = 1
private const val LOOT_ALL_X = 4
private const val CLOSE_X = 8

/**
 * Corpse loot menu, rendered by the GUI engine.
 *
 * This used to drive a bare Bukkit inventory with its own click listener, because the GUI engine
 * was a private BTC component. It is public now, so the menu goes through it like every other
 * menu: the engine owns rendering, click routing, history and the extended-inventory projection,
 * and this file only describes what the screen contains.
 */
object CorpseGUI {

    /**
     * Page each viewer is on.
     *
     * A player may carry far more than one screen of stacks once an inventory extension is in
     * play, so the corpse is paged rather than silently truncated to what fits.
     */
    private val openPages = ConcurrentHashMap<UUID, Int>()

    /** Kept for symmetry with the initializer; the engine owns event registration now. */
    fun initialize(plugin: Plugin) {
        // Nothing to register: MenuSessionService already listens for clicks and closes.
    }

    fun shutdown() {
        openPages.clear()
    }

    fun openCorpseInventory(player: Player, corpse: CorpseEntity, page: Int = 0) {
        val settings = CorpseManager.settings()
        val items = corpse.inventory
        val pages = ((items.size - 1) / PAGE_SIZE + 1).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        openPages[player.uniqueId] = current

        val rawTitle = settings.guiTitle.get(player).replace("<player>", corpse.playerName)
        val slots = mutableListOf<GuiSlot>()

        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            val meta = itemMeta ?: return@apply
            meta.displayName(" ".asMini())
            itemMeta = meta
        }

        val offset = current * PAGE_SIZE
        val pageItems = items.drop(offset).take(PAGE_SIZE)

        for (index in 0 until PAGE_SIZE) {
            val x = index % 9
            val y = index / 9
            val item = pageItems.getOrNull(index)
            if (item == null || item.type.isAir) {
                slots.add(GuiSlot(x = x, y = y, item = filler.clone(), allowPickup = false, isGhost = true))
                continue
            }
            val absoluteSlot = offset + index
            slots.add(
                GuiSlot(
                    x = x,
                    y = y,
                    item = item.clone(),
                    // The stack is handed over by the take handler, never dragged out of the
                    // window: the corpse must stay the single source of truth for its contents.
                    allowPickup = false,
                    onClick = { clicker, _ -> takeItem(clicker, corpse, absoluteSlot) },
                )
            )
        }

        // Control row: every position not carrying a control keeps the decorative background.
        val controls = mutableMapOf<Int, GuiSlot>()

        controls[LOOT_ALL_X] = GuiSlot(
            x = LOOT_ALL_X,
            y = CONTROL_ROW,
            item = ItemStack(Material.CHEST).apply {
                val meta = itemMeta ?: return@apply
                meta.displayName(settings.guiLootAllLabel.get(player).asMini())
                val lore = buildList {
                    if (corpse.experience > 0) {
                        add(
                            settings.guiExperienceLabel.get(player)
                                .replace("<experience>", corpse.experience.toString()).asMini()
                        )
                    }
                    if (pages > 1) {
                        add(
                            settings.guiPageLabel.get(player)
                                .replace("<page>", (current + 1).toString())
                                .replace("<pages>", pages.toString()).asMini()
                        )
                    }
                }
                if (lore.isNotEmpty()) meta.lore(lore)
                itemMeta = meta
            },
            allowPickup = false,
            onClick = { clicker, _ ->
                val live = CorpseManager.getCorpse(corpse.corpseId)
                if (live == null) {
                    clicker.sendMessage(settings.corpseGoneMessage.get(clicker).asMini())
                    clicker.closeInventory()
                } else if (!live.canLoot(clicker)) {
                    clicker.sendMessage(settings.cannotLootMessage.get(clicker).asMini())
                } else {
                    CorpseManager.lootCorpse(live, clicker, handToLooter = true)
                    clicker.closeInventory()
                }
            },
        )

        // Paging arrows only exist when there is somewhere to go.
        if (current > 0) {
            controls[PREV_X] = pageButton(settings.guiPreviousPageLabel.get(player)) { clicker ->
                reopen(clicker, corpse, current - 1)
            }.copy(x = PREV_X, y = CONTROL_ROW)
        }
        if (current < pages - 1) {
            controls[NEXT_X] = pageButton(settings.guiNextPageLabel.get(player)) { clicker ->
                reopen(clicker, corpse, current + 1)
            }.copy(x = NEXT_X, y = CONTROL_ROW)
        }

        controls[CLOSE_X] = GuiSlot(
            x = CLOSE_X,
            y = CONTROL_ROW,
            item = ItemStack(Material.BARRIER).apply {
                val meta = itemMeta ?: return@apply
                meta.displayName(settings.guiCloseLabel.get(player).asMini())
                itemMeta = meta
            },
            allowPickup = false,
            onClick = { clicker, _ -> clicker.closeInventory() },
        )

        for (x in 0 until 9) {
            slots.add(
                controls[x] ?: GuiSlot(
                    x = x,
                    y = CONTROL_ROW,
                    item = filler.clone(),
                    allowPickup = false,
                    isGhost = true,
                )
            )
        }

        val definition = MenuDefinition(
            id = "corpse:${corpse.corpseId}",
            type = GuiType.CUSTOM,
            title = MiniMessage.miniMessage().deserialize(rawTitle),
            rawTitle = rawTitle,
            size = InventorySize.SIZE_54,
            layout = SimpleLayout(slots, id = "corpse_loot"),
        )

        // Only the first page opens a new screen: paging within one corpse must not stack up
        // entries the back button would then have to walk through one by one.
        MenuSessionService.register(player, definition, pushHistory = current == 0)
    }

    private fun pageButton(label: String, action: (Player) -> Unit): GuiSlot = GuiSlot(
        x = 0,
        y = CONTROL_ROW,
        item = ItemStack(Material.ARROW).apply {
            val meta = itemMeta ?: return@apply
            meta.displayName(label.asMini())
            itemMeta = meta
        },
        allowPickup = false,
        onClick = { clicker, _ -> action(clicker) },
    )

    /** Re-opens the menu on another page, checking the corpse is still there. */
    private fun reopen(player: Player, corpse: CorpseEntity, page: Int) {
        val settings = CorpseManager.settings()
        val live = CorpseManager.getCorpse(corpse.corpseId)
        if (live == null) {
            player.sendMessage(settings.corpseGoneMessage.get(player).asMini())
            player.closeInventory()
            return
        }
        openCorpseInventory(player, live, page)
    }

    private fun takeItem(player: Player, corpse: CorpseEntity, slot: Int) {
        val settings = CorpseManager.settings()
        val live = CorpseManager.getCorpse(corpse.corpseId) ?: run {
            player.sendMessage(settings.corpseGoneMessage.get(player).asMini())
            player.closeInventory()
            return
        }
        if (!live.canLoot(player)) {
            player.sendMessage(settings.cannotLootMessage.get(player).asMini())
            return
        }

        val item = live.removeItem(slot) ?: return

        val leftover = player.inventory.addItem(item.clone())
        if (leftover.isNotEmpty()) {
            // The item left the corpse either way, so it must land somewhere rather than vanish.
            player.sendMessage(settings.inventoryFullMessage.get(player).asMini())
            player.world.dropItemNaturally(player.location, item.clone())
        }

        // The slot is empty in memory now; storage has to agree or a restart would hand it back.
        CorpseManager.persist(live)

        if (live.isEmpty()) {
            CorpseManager.lootCorpse(live, player, handToLooter = true)
            player.closeInventory()
        } else {
            openCorpseInventory(player, live, openPages[player.uniqueId] ?: 0)
        }
    }
}
