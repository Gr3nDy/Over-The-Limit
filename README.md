# Overcast Community Server Utilities

Client-side Forge 1.8.9 utilities for the Overcast Community server.

This project contains three client-side modules:

## Mods

### Face Rig

A skin-based face animation and editing mod.

- Uses the player's own skin pixels for eyes, pupils, brows, and eyelids.
- Includes blinking, glances, blush, glowing eyes, and bow-aiming expressions.
- Sneaking hides the face rig.
- Includes an editor with Eye, Pupil, Brow, Lid, and Erase tools.
- Supports 64×64 and legacy 64×32 skins.

### Reliable Chat

Improved chat utilities designed for `play.oc.tc`.

- Detects lobby and game transitions.
- Supports All, Team, Party, and private-message tabs.
- Preserves vanilla formatting, clicks, hover actions, history, and completion.
- Includes unread counts, drafts, optional history, and team speech bubbles.
- Speech bubbles use only server-delivered **`(Team Name) Player: message`** chat, including your own messages (visible in third person). No join detection, team-color comparison, or scoreboard membership is required. Global, party, private, system and hub messages never become bubbles. A fixed two-second per-sender cooldown ignores spam without extending the bubble; character limit and display duration remain configurable in Reliable Chat settings.

### Prefered Hand

- Toggle **Prefered Hand** in the modules menu (P), then open **Settings**. Defaults: module OFF, **Main Hand: Right**, **Type: ALL**; active only on approved servers.
- Main Hand switches Right/Left. Type cycles **Items Only**, **Blocks Only**, **Tools Only**, **Tools + Blocks Only**, **Weapons**, and **ALL**. Selected types use Main Hand; all others use the opposite hand. For example, Right + Blocks Only puts blocks on the right and other items on the left; Left reverses that. Items means all non-block items; weapons are swords/bows; tools include axes, pickaxes, shovels, hoes, shears, fishing rods, and flint and steel.
- Mirrors the first-person item/empty hand while retaining the original renderer's equip/use animations. World rendering, HUD, third-person views and overlays are unchanged.
- Two-handed maps keep their normal orientation so map text stays readable. This is visual only, not an offhand inventory slot.

### Map Preview

- Square, uncropped book previews and a searchable, paged gallery of **both** CommunityMaps and PublicMaps.
- Hover gold map-name links in open chat to see their image, in Reliable Chat or vanilla chat. Uses the link's `/map <name>` target; unrelated gold text is ignored. Enabled by Map Preview, independently of Preview on command.
- Hover images default to Small (80 GUI pixels); switch to Large (170) in Map Preview settings. Images have no name caption and align beneath the normal text tooltip, or above it near the bottom edge.
- Map cards with scrollable metadata and exact gallery selection.
- Separate **Preview on command** switch for `/map <name>` and current-map `/map`. Commands use Forge's command path and open the card after chat closes.
- Bundled maps first, then `OvercastCommunity/PublicMaps` (`main`) and `OvercastCommunity/CommunityMaps` (`master`).
- Public catalog entries and previews are fetched/cached on demand; they are not all bundled in the jar. Search `PublicMaps` or `CommunityMaps` to filter by repository.
- Metadata uses the default XML variant, resolves shared PublicMaps includes/constants, and labels unavailable/server-defined fields instead of guessing.

## Download

Download the latest beta from:

<https://github.com/Gr3nDy/Over-The-Limit/releases>

Place `OTL.jar` in `.minecraft/mods`.

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
Add `-PotlSmokeRemote` to require live lookups from **both** map repositories. Without it, remote checks are explicitly disabled, not reported as passed.

## License

Licensed under the **GNU General Public License v3.0**. See `LICENSE`.
