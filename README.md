# Over The Limit

![OVER THE LIMIT](docs/assets/otl-banner.svg)

**Over The Limit (OTL)** is a client-side Forge 1.8.9 mod for the Overcast Community server. Press **P** for the HUD editor and modules (rebind or unbind Mod Menu in Controls); Face Rig is available from Mods. [Download the release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.5) · [What's new](CHANGELOG.md)

## Modules

| Module | What it does |
| --- | --- |
| **Face Rig** | Animates skin-based eyes and expressions; includes an in-game editor. |
| **Reliable Chat** | Adds channel/PM tabs, drafts, scrollable history and optional team speech bubbles. |
| **Map Preview** | Shows book/chat map previews, a searchable gallery and `/map` info cards. |
| **Prefered Hand** | Visually chooses the first-person hand for items, blocks, tools or weapons. |
| **Kill Feed** | Displays deaths, causes and weapons with a configurable HUD and history. |
| **Kill Confirm** | Shows queued kill tags, streaks and immediate EXP feedback; OFF by default. |
| **Bow Optimizer** | Separately toggleable optimization, movable live accuracy HUD, optional hit sound and crosshair hit marker. Module OFF by default. |
| **Server Notices** | Separates server announcements from chat in a standalone HUD; OFF by default. |
| **Height Limit Overlay** | Temporarily unavailable. Shown as DISABLED in the module menu; cannot be enabled. |
| **Minimap** | Bundled 3D terrain in a square, player-centered isometric viewport; OFF by default. |

Team speech bubbles are suppressed on announced FFA and other teamless maps, even when players share a chat color. Bubbles stay hidden while an announced map's metadata is unavailable; team maps restore them automatically.

Minimap keeps terrain at a fixed scale of two GUI pixels per projected block: moving pans the terrain below your centered location marker; resizing only changes the square viewport, never fits or shrinks the map. Move/resize it in the HUD editor and adjust background opacity in its settings. Only your position is shown—no other players, radar or exploration fog. Both the current-map `Playing … by …` notice and the centered join/rotation title followed by `Created by …` select a model, even if the module was OFF when the announcement arrived. World changes clear the old map immediately. Arbitrary `/map` XML lookups and player quotes cannot switch the current minimap. Missing or invalid models show `Preview unavailable` rather than an old map.

The meshes use actual repository terrain, simplified block colors and cube approximations for partial blocks. They are static: placed/broken blocks, dynamic objectives and server-generated map variants are not reflected. XML world variants with distinct announced names select their corresponding terrain. Ambiguous same-name worlds within a repository (for example, server-version-specific Runic Altar worlds) show Preview unavailable instead of guessing. Cross-repository duplicate names prefer CommunityMaps. Model redistribution permissions and live-server minimap rules have not been verified.

Bow Optimizer changes local feedback only, not charge speed, damage or packets. Sound matching is conservative: nearby players or a late/moved server sound can leave an audible duplicate rather than risk muting another player.

Inside Bow Optimizer settings, Optimization and Live Bow Accuracy can be toggled separately. Accuracy is server-confirmed player arrow hits divided by valid local bow shots; mobs and servers that omit the vanilla arrow-hit signal are not counted. The HUD can be moved/resized in the HUD editor, with adjustable background opacity and 0–2 decimal places (default: 2). Counts are session-only and reset on map/world changes, recognized English PGM match endings/starts, or reconnecting.

The Bow Accuracy tab also has a Prefix toggle, ON by default. Turn it OFF to show only the percentage instead of `Bow Accuracy: 33.33%`.

Bow Accuracy's Mode cycles through Always (default), On Hit and Holding Bow. On Hit shows the HUD for two seconds after a server-confirmed player arrow hit, then fades the text and background out over half a second; another hit refreshes it. Holding Bow shows it while a bow is equipped and keeps it for one second after switching items, fading over the final half-second. The HUD editor preview stays visible. Animate Percentage is ON by default and smoothly counts up or down to the real accuracy over 0.4 seconds; turn it OFF for instant updates. Display modes do not change the underlying counts. Saved On Hit preferences are preserved.

Fade In and Fade Out sliders adjust the conditional accuracy HUD's transitions: 0–100% means 0–1 second. Fade In defaults to 0% (instant), Fade Out to 50% (half a second). On Hit keeps a two-second hold before fading; Holding Bow keeps a half-second grace before fading. Always mode and the editor preview remain fully visible.

Bow Hit Sound, Bow-Kill Sound and Hit Marker are OFF by default. The original COD-inspired hit tick uses vanilla audio; Bow-Kill Sound plays a separate vanilla chime on your own server-confirmed bow kills. Both sounds have independent toggles. The crosshair marker is white on a confirmed bow hit and red on a confirmed bow kill, with a size slider from 50% to 200%. Accuracy, sounds and marker remain usable with Optimization OFF; the module master switch still controls all of them. The packet observer reads hit/title notifications and forwards every original packet unchanged.

Kill Confirm has named streak tiers from RAMPAGE (5 kills) through THE LIMIT BREAKER (100+), with the exact streak count below. Rapid chains advance through NONA KILL, then MULTI KILL with an exact count at 10+. POINT BLANK requires a reported bow distance of 3 blocks or less; EXTREME RANGE replaces LONG SHOT at 60+. DOMINATION starts after five kills against one opponent and resets when that opponent kills you.

Streak tags and their exact-count row appear only at 5, 10, 15, 20, 25, 30, then every 10 kills (40, 50, 60, and so on). They do not repeat between milestones. The former 7-kill DOMINATING tier is removed; DOMINATION remains a separate opponent-specific bonus. Streak counting, EXP, rapid-chain tags and other kill bonuses are unchanged.

REDEMPTION replaces REVENGE when you kill someone who killed you five times since you last killed them. CLEAN SWEEP needs five different opponents in one rapid chain, BRAWLER needs three melee kills in that chain, and DEMOLITION requires a credited explosion kill. The three-bonus-tag limit still applies.

HOT HAND requires three bow kills in one rapid chain. MIXED ARSENAL requires both a melee kill and a bow kill in that chain, in either order. The chain permits at most three seconds between kills and resets on death or a longer gap. Assists and other players' kills do not advance these counters; both tags share the existing three-bonus-tag limit.

Kill Confirm's optional Total EXP, Kills & Assists recap preserves the previous life's totals on death. Its three rows read `EXP 72`, `KILLS EXP 40 (10 KILLS)` and `ASSISTS EXP 32 (20 ASSISTS)`; the first is the sum of kill and assist EXP. Counts and each EXP subtotal come from the server's explicit reward messages, not guesses from death lines. It appears on a normal dead-to-alive transition, or a PGM-style teleport of more than four blocks in one client tick away from the death position while alive and not in spectator mode. Ordinary movement or a teleport without a preceding death does not trigger it. Same-world teleport respawns are supported; world changes still reset session state. Servers with closer respawn points or custom spectator transitions may need explicit respawn signals instead.

The recap is hidden when the previous life had no kills and no assists. Otherwise it shows total EXP and only the categories earned: kill-only lives omit ASSISTS EXP, assist-only lives omit KILLS EXP, and lives with both show all three rows.

## Download

Download `OTL.jar` from the [v0.3.5 release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.5) or [browse all releases](https://github.com/Gr3nDy/Over-The-Limit/releases).

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
For a focused run, add `-PotlSmokeScope=killconfirm` (or `bow`, `hand`, or another module name); use the full run for shared-code changes.
Add `-PotlSmokeRemote` to require live lookups from **both** map repositories. Without it, remote checks are explicitly disabled, not reported as passed.

### Generate bundled minimap models

The one-off Python converter needs `tools/requirements-minimap.txt` (no new Java/runtime dependency). Run `python tools/generate_minimap_models.py --self-test`, then `python tools/generate_minimap_models.py`. It downloads terrain to the local OpenCode temporary cache and writes `minimap.mesh.gz` alongside each map's assets, plus `assets/otl/maps/minimap-index.json`. Completed models are reused on reruns; models and the index are atomically replaced. `build/minimap-generation.json` lists generated sizes, failures and missing terrain. Nether/End-only worlds are supported when there is one unambiguous terrain source. Large sparse maps use section-sized conversion rather than allocating their empty bounding volume. Wait for conversion to finish before making a release build; incomplete `.part` files are excluded from resources.

## License

The mod's source code and binaries are licensed under the **GNU General Public License v3.0**. See `LICENSE`.

Bundled map previews and the terrain meshes downloaded per map are derived from Overcast Community's PGM map repositories and are licensed under **CC BY-SA 4.0** by their respective map authors (see `LICENSE-CC-BY-SA-4.0.txt` and `NOTICE.md`, including `Maps/ATTRIBUTION.txt`). The mod is therefore distributed as a combined work: code (GPL-3.0) and map data (CC BY-SA 4.0).
