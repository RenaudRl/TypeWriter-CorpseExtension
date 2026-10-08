# Changelog

## 0.6 - 2026-10-08

(0.4 and 0.5 were not announced here.)

- **World filter**: `allowedWorlds` and `excludedWorlds` on `corpse_settings`. Empty allow list =
  every world, as before. `*` and `?` wildcards (`dungeon_*`), exact names (`dgbuild`) and dimension
  keys (`minecraft:the_nether`) are understood; an exclusion wins. Outside the allowed worlds the
  death is vanilla.
- **`filteredWorldDeath`** (`VANILLA_DEATH_SCREEN` by default, or `INSTANT_RESPAWN`): in a world the
  filter turns down, respawn the player on the next tick instead of showing the death screen. The
  body seen with `VANILLA_DEATH_SCREEN` is the Minecraft client's own death pose, not a Corpse
  entity. No effect when both lists are empty.
- **Every text is configurable**: all messages, GUI labels and materials, the waypoint format and
  its eight direction arrows, the admin command replies, the default corpse name, and the recovery
  objective text. Placeholders (`<player>`, `<x>`, ...) are filled before MiniMessage, and what a
  player controls (a name) is escaped so it can never become a clickable tag.
- New settings: `interactionReach` (6 blocks, the server did not check the distance of a click on a
  packet entity), `interactionCooldownMillis`, `unknownWorldLabel`.
- **Fix**: a death cancelled by another plugin (totem, revive) no longer takes the player's gear.
- **Fix**: `keepInventory` and `keepLevel` are respected, and items other plugins keep for the
  player (Curse of Vanishing, soulbound) are no longer duplicated into the corpse.
- **Fix**: if the corpse cannot be created, the items drop normally instead of being lost.
- **Fix**: taking an item with a full inventory dropped the whole stack again, duplicating the part
  that had fitted.
- **Fix**: two clicks racing for the same corpse could hand its contents out twice.
- **Fix**: a restart no longer brings back items already taken; the last writes are awaited on
  reload, and they run in order on a single thread.
- **Fix**: an unreadable or half-written storage file no longer wipes every stored corpse; the
  file is written through a temporary file and an unreadable one is kept aside.
- **Fix**: restored corpses keep their pose, glow, name and animation (the owner's skin and armour
  are not stored and stay the plain model's).
- **Fix**: the respawn message, waypoint and objective use the latest corpse; the loot menu warns
  before opening instead of after the first click; the per-player menu state is released on quit.
- **Fix**: loot clicks read their settings and the player's state on the region thread, not in the
  async packet event; the items leave the death drops the moment the corpse exists, so a late
  failure can no longer leave them both in the corpse and on the ground; one bad stored corpse no
  longer stops the others from being restored.
- A definition is now also chosen with the dimension key; the `/corpses list` header and lines are
  configurable. New optional `outOfReachMessage` (empty by default: no message).

## 0.3 - 2026-08-29

- Rebuilt against OmniGUI (GuiAndDialogs) v0.14.

## 0.1 — 2026-08-12

First release.

- Corpses are rendered once per viewer, like the engine's own entity displays, so everyone nearby
  sees them, with model animation and nameplates ticking on the BetterModel, BTC Mob NPC and
  MythicMobs NPC backends.
- The loot menu is a GUI Extension menu rather than a bare inventory with its own click listener,
  so it inherits the engine's click routing, menu history and extended-inventory projection.
  **The GUI Extension is therefore required.**
- Runs on Folia: all scheduling goes through a Folia-aware scheduler, and item drops, experience
  orbs and particles run on the region owning the corpse.
- The inventory a corpse holds survives a restart, a crash or a reload. Corpses are stored in
  MySQL when available, otherwise in the plugin folder, and restored on startup.
- `onlyOwnerCanLoot` is enforced, and looting is matched against the corpse's own entity id rather
  than any right-click within four blocks.
- A corpse definition is picked per death by world and priority, and its lifetime is resolved once,
  on that corpse.
- The `on_corpse_spawn`, `on_corpse_loot` and `on_corpse_expire` events go through the engine's
  trigger pipeline and publish the corpse's owner, loot and position as context.
- `displayName`, `showDisplayName`, `dropOnInteract`, `renderArmor`, `playDeathAnimation` and
  `deathAnimationName` are all honoured, and most settings are dynamic values, so they can be
  driven by placeholders and facts.
- A client-rendered glow outline with a configurable colour, and corpse sounds sent per nearby
  player instead of world-wide.
- `has_corpse` and `corpse_count` facts, the `corpse_owner_audience` audience, `corpse_waypoint`
  showing distance and direction to the player's own corpse, `corpse_recovery_objective`, and
  `corpse_admin_command` with `list` and `clear` subcommands.
- A loot protection window, after which a corpse opens to everyone.
