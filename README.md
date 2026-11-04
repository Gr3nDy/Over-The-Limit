# Overcast Community Server Utilities

Client-side Forge 1.8.9 utilities for the Overcast Community server.

This project currently contains two mods:

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

The compiled files are created in `build/libs/`.

## License

Licensed under the **GNU General Public License v3.0**. See `LICENSE`.
