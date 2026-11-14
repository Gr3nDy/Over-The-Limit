# Over The Limit

![OVER THE LIMIT](docs/assets/otl-banner.svg)

![beta](https://img.shields.io/badge/branch-beta-orange) ![release](https://img.shields.io/badge/release-v0.3.9-blue) ![Minecraft](https://img.shields.io/badge/Minecraft-1.8.9-green)

**Over The Limit (OTL)** is a client-side Forge 1.8.9 mod for the Overcast Community server. Press **P** for the HUD editor and modules (rebind or unbind Mod Menu in Controls); Face Rig is available from Mods. [Download the release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.9) · [Wiki](docs/WIKI.md) · [What's new](CHANGELOG.md)

## Modules

| Module | What it does |
| --- | --- |
| **Face Rig** | Animates skin-based eyes and expressions; includes an in-game editor. |
| **Reliable Chat** | Adds channel/PM tabs, drafts, scrollable history and optional team speech bubbles. |
| **Map Preview** | Shows book/chat map previews, a searchable gallery and `/map` info cards. |
| **Custom Votebook** | Replaces the vote book with map cards, Supervote, match stats and a match link. |
| **Team Bar** | One row of heads per team at the top centre, stacked, with the match clock in the middle. |
| **Prefered Hand** | Visually chooses the first-person hand for items, blocks, tools or weapons. |
| **Kill Feed** | Displays deaths, causes and weapons with a configurable HUD and history. |
| **Kill Confirm** | Shows queued kill tags, streaks and immediate EXP feedback; OFF by default. |
| **Bow Optimizer** | Separately toggleable optimization, movable live accuracy HUD, optional hit sound and crosshair hit marker. Module OFF by default. |
| **Server Notices** | Separates server announcements from chat in a standalone HUD; OFF by default. |
| **Region Borders** | Wireframes of the current map's regions, plus the region name you are standing in; OFF by default. |
| **Height Limit Overlay** | Shows the current map's build height as an in-world overlay. |
| **Minimap** | Per-map terrain meshes downloaded on demand in a square, player-centered isometric viewport; OFF by default. |
| **Auto GG** | Says `gg` once per match, at the end only; OFF by default. |

## Download

Download `OTL.jar` from the [v0.3.9 release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.9) or [browse all releases](https://github.com/Gr3nDy/Over-The-Limit/releases).

Place `OTL.jar` in your Forge 1.8.9 client's `mods` folder manually.

The source branch is `beta`. It does not contain compiled JAR files.

## Build

Use JDK 8 and run:

```text
gradlew.bat clean build --offline --no-daemon
```

The verified, reobfuscated, smoke-free `OTL.jar` is created in `build/libs/`.
Copy it to your client manually. No build task installs anything into Lunar or another client.
Development and smoke jars stay in `build/dev-libs/`, so `runClient` cannot overwrite the manual-copy release.

Tests only: `gradlew.bat test --offline --no-daemon`.
In-game fixtures: `gradlew.bat runClient -PotlSmoke --offline --no-daemon`.
For a focused run, add `-PotlSmokeScope=teambar` (or `killconfirm`, `bow`, `hand`, or another module name); use the full run for shared-code changes.
Add `-PotlSmokeRemote` to require live lookups from **both** map repositories. Without it, remote checks are explicitly disabled, not reported as passed.

### Generate minimap models

The one-off Python converter needs `tools/requirements-minimap.txt` (no new Java/runtime dependency). Run `python tools/generate_minimap_models.py --self-test`, then `python tools/generate_minimap_models.py`. It downloads terrain to the local OpenCode temporary cache and writes `minimap.mesh.gz` alongside each map's assets, plus `assets/otl/maps/minimap-index.json`. Meshes are committed to the repo for on-demand download (they are not bundled in the jar). Completed models are reused on reruns; models and the index are atomically replaced. `build/minimap-generation.json` lists generated sizes, failures and missing terrain. Nether/End-only worlds are supported when there is one unambiguous terrain source. Large sparse maps use section-sized conversion rather than allocating their empty bounding volume. Wait for conversion to finish before making a release build; incomplete `.part` files are excluded from resources.

## License

The mod's source code and binaries are licensed under the **GNU General Public License v3.0**. See `LICENSE`.

Bundled map previews and the terrain meshes downloaded per map are derived from Overcast Community's PGM map repositories and are licensed under **CC BY-SA 4.0** by their respective map authors (see `LICENSE-CC-BY-SA-4.0.txt` and `NOTICE.md`, including `Maps/ATTRIBUTION.txt`). The mod is therefore distributed as a combined work: code (GPL-3.0) and map data (CC BY-SA 4.0).
