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

Bow Optimizer changes local feedback only, not charge speed, damage or packets. Sound matching is conservative: nearby players or a late/moved server sound can leave an audible duplicate rather than risk muting another player.

Inside Bow Optimizer settings, Optimization and Live Bow Accuracy can be toggled separately. Accuracy is server-confirmed player arrow hits divided by valid local bow shots; mobs and servers that omit the vanilla arrow-hit signal are not counted. The HUD can be moved/resized in the HUD editor, with adjustable background opacity and 0–2 decimal places (default: 2). Counts are session-only and reset on map/world changes, recognized English PGM match endings/starts, or reconnecting.

The Bow Accuracy tab also has a Prefix toggle, ON by default. Turn it OFF to show only the percentage instead of `Bow Accuracy: 33.33%`.

Bow Hit Sound and Hit Marker are OFF by default. The original COD-inspired hit tick uses vanilla audio. The crosshair marker is white on a confirmed bow hit and red on a confirmed bow kill, with a size slider from 50% to 200%. Accuracy, hit sound and marker remain usable with Optimization OFF; the module master switch still controls all of them. The packet observer reads hit/title notifications and forwards every original packet unchanged.

Kill Confirm has named streak tiers from RAMPAGE (5 kills) through THE LIMIT BREAKER (100+), with the exact streak count below. Rapid chains advance through NONA KILL, then MULTI KILL with an exact count at 10+. POINT BLANK requires a reported bow distance of 3 blocks or less; EXTREME RANGE replaces LONG SHOT at 60+. DOMINATION starts after five kills against one opponent and resets when that opponent kills you.

REDEMPTION replaces REVENGE when you kill someone who killed you five times since you last killed them. CLEAN SWEEP needs five different opponents in one rapid chain, BRAWLER needs three melee kills in that chain, and DEMOLITION requires a credited explosion kill. The three-bonus-tag limit still applies.

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

## License

Licensed under the **GNU General Public License v3.0**. See `LICENSE`.
