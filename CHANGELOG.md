# Changelog

## 0.8 - 2026-11-11

**Update Corpse and the Dungeon extension together** (Dungeon 1.4.0 and Corpse 0.8). With an older
Corpse, the corpses of an orphaned run world are not brought out and are lost with the world.
Needs GUI (OmniGUI) 0.21 or later.

- **World evacuation**: a world about to be removed (a dungeon run) can have its corpses brought
  out first, through the new `CorpseApi`. Online owner: the contents are handed over, with the new
  `evacuatedMessage`; if they cannot take it all, the body is moved instead. Offline owner: the
  body is moved to a fallback location. Corpses stored for a world that is not loaded are moved
  too, and a warning at startup lists the dormant ones. A store that cannot be asked is told apart
  from "no corpse", and a corpse whose move fails is put back whole.
- New `corpse_settings` fields, all with a default: `dropOnExpire`, eight particle offsets
  (`spawnParticleHeight`, `spawnParticleSpreadHorizontal`, `spawnParticleSpreadVertical`,
  `spawnParticleSpeed`, and the four `spawnParticleSecondary...` equivalents) and `evacuatedMessage`.
  A third-party extension that builds `corpse_settings` in code must be recompiled.
- **`dropOnExpire`** (on by default): an expired corpse now drops what it holds on the ground, and
  its experience as an orb, instead of deleting it. Turn it off to keep the 0.7 behaviour.
- **Fix**: no corpse when `keepInventory` is on and the drops are empty.
- **Fix**: storage errors are retried and reported, unreadable items are kept, a corpse is emptied
  under its own lock, and an unavailable store is no longer taken for "no corpse".
- **Fix**: Folia thread ownership for looting and for the viewers; the contents are handed over
  before the corpse is deleted; reload drains the pending work; void deaths and expiry are handled.
- **Fix**: experience nobody claimed is given back, a late hand-over after shutdown is refused, and
  the purge marks are hardened.
- **Build**: the extension builds on its own (`./gradlew build`, Gradle wrapper included). OmniGUI
  comes from its GitHub release (0.21) instead of the multi-extension workspace.

## 0.7 - 2026-10-16

- Built against GUI (OmniGUI) 0.20. Needs GUI 0.20 or later: the GUI slot class gained a field in
  0.20, so the previous build (made against 0.15) no longer links with it. No change to entries or
  behaviour.
- The build no longer declares an unused GUI release repository.

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
