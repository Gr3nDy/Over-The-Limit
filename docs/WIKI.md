# Over The Limit — Wiki

**Over The Limit (OTL)** is a client-side Forge 1.8.9 mod for the [Overcast Community](https://overcastcommunity.com/) PGM-style server family (`play.oc.tc`, `vanish.oc.tc`).

**Current release:** [v0.3.6](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.6) · **Source:** [github.com/Gr3nDy/Over-The-Limit](https://github.com/Gr3nDy/Over-The-Limit) (branch `beta`)

## Installing

1. Download `OTL.jar` from the [latest release](https://github.com/Gr3nDy/Over-The-Limit/releases/latest).
2. Place it in your Forge 1.8.9 client's `mods` folder.
3. Restart the client. No build task installs anything into Lunar or any other launcher.

## Using it

Press **P** to open the HUD editor. From the editor you can toggle, resize, and reposition every HUD module, per-module settings live under its *Settings* button. Press **Esc** to close.

## Modules

| Module | What it does | Default |
| --- | --- | --- |
| Face Rig | Animates skin-based eyes and expressions; includes an in-game editor | OFF |
| Reliable Chat | Channel/PM tabs, drafts, scrollable history, optional team speech bubbles | ON |
| Map Preview | Book/chat map previews, searchable map gallery, `/map` info cards | ON |
| Prefered Hand | Configurable first-person hand rendering | OFF |
| Kill Feed | Deaths, causes and weapons with a configurable HUD and 1,000-message history | ON |
| Kill Confirm | Queued kill tags, streaks, EXP recap after death | OFF |
| Bow Optimizer | Predicted release sound, same-slot durability fix, live accuracy HUD, hit marker | OFF |
| Server Notices | Server announcements in a standalone HUD | OFF |
| Height Limit Overlay | Shows the map's block placement limit as an in-world overlay | OFF |
| Minimap | Downloads per-map terrain meshes on demand; square, player-centered isometric viewport | OFF |

## Building from source

Requires JDK 8.

```text
gradlew.bat clean build --offline --no-daemon
```

The verified, reobfuscated `OTL.jar` is created in `build/libs/`. Tests only: `gradlew.bat test --offline --no-daemon`. In-game fixtures: `gradlew.bat runClient -PotlSmoke --offline --no-daemon` (scope with `-PotlSmokeScope=<name>`, e.g. `bow`, `minimap`, `killconfirm`).

## Repository map

- `src/main/java/otl/client/` — the mod (modules, HUD, screens, settings)
- `src/main/resources/assets/otl/maps/` — bundled map previews (`map.xml`, `map.png`) and the `minimap-index.json` catalog
- `Maps/` — OvercastCommunity PGM map meshes (downloaded on demand per map)
- `docs/` — [Architecture](ARCHITECTURE.md) · [Local testing](LOCAL_TESTING.md)
- `tools/generate_minimap_models.py` — the mesh generator (not a runtime dependency)
- `license/` — LICENSE, CC BY-SA-4.0, NOTICE, ATTRIBUTION

## Licensing

- **Source code and binaries**: GNU General Public License v3.0 (`license/LICENSE`)
- **Map data** (previews bundled in the jar; meshes downloaded on demand from the public repos): Creative Commons Attribution-ShareAlike 4.0 International, by the respective map authors (`license/LICENSE-CC-BY-SA-4.0.txt`, `license/NOTICE.md`, `license/ATTRIBUTION.txt`)
- Game content © Mojang Studios. Not affiliated with or endorsed by Mojang or the Overcast Community.

## Problems or questions?

Open a [GitHub issue](https://github.com/Gr3nDy/Over-The-Limit/issues). Include your client (Lunar/Forge), which module is involved, and what you expected versus what happened.
