# Corpse Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20BTC--CORE-blue)

**Corpse Extension** spawns a corpse where a player died, holding their inventory and experience
until someone loots it or it expires. Built for **TypeWriter**, engineered for **BTC Studio**
infrastructure.

---

## Key Features

### Rendering
- **Per-viewer rendering**: every player nearby sees the corpse, each with their own copy of the
  model, using the engine's `entity.show-range`.
- **Five model backends**: EntityExtension, ModelEngine, BetterModel, BTC Mob NPC and MythicMobs NPC.
- **Vanilla fallback**: with no model configured, the corpse is a plain player entity in the dying
  pose, wearing the victim's skin and armour.
- **Client-side glow**: a configurable outline drawn by the client, not server-sent particles.

### Loot
- **Full inventory and experience**: captured on death, so the vanilla drops are taken over entirely.
- **Survives restarts**: stored in MySQL when available, otherwise in the plugin folder.
- **Access control**: owner-only looting, or a timed protection window after which the corpse opens
  to everyone.
- **Loot menu on the GUI engine**: take items one at a time instead of dropping everything at once.
  The menu is a GUI Extension menu, so it inherits the engine's click routing, menu history and
  extended-inventory projection rather than running its own inventory listener.

### Integration
- **Events**: `on_corpse_spawn`, `on_corpse_loot`, `on_corpse_expire`, with the corpse's owner, loot
  and position published as context.
- **Facts and audiences**: `has_corpse`, `corpse_count`, `corpse_owner_audience`, `corpse_waypoint`.
- **Quests**: `corpse_recovery_objective`, active until the player recovers their corpse.
- **Admin**: `corpse_admin_command` with `list` and `clear`.

### Performance
- **Folia-safe**: world changes run on the region that owns the corpse.
- **Dynamic values**: most settings can be driven by placeholders and facts.

---

## Requirements

| Requirement | Needed for |
| :--- | :--- |
| Basic Extension | Base entries |
| Entity Extension | Model rendering and the player fallback |
| Quest Extension | The recovery objective |
| GUI Extension | The loot menu |
| MySQL Extension | Cross-restart storage (optional — falls back to a local file) |

---

## Documentation
Full documentation available at [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/corpse/).
