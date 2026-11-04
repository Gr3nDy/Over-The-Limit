# Changelog

## 0.3.1

### Face Rig

- Added reliable Face Rig support for other players.
- Added automatic eye, pupil, brow, and eyelid detection from player skins.
- Added per-eye skin-derived colors and manual Lid pixel selection.
- Added support for 64×64 and legacy 64×32 skins.
- Added skin caching and retry behavior for late-loading skins.
- Improved dark-skin, hat-layer, and reference-style eyelid detection.
- Added blinking, glances, blush, glow, bow-aiming expressions, and sneaking behavior.

### Reliable Chat

- Added All, Team, Party, and private-message tabs.
- Added configurable Party Chat keybind, unbound by default.
- Pressing the vanilla Chat key opens the last selected channel.
- Pressing **Y** temporarily opens Team Chat and returns to the previous channel after sending or closing chat.
- Joining a game starts in All Chat without automatically sending `/t`.
- Added optional HUD channel tabs with unread counts and new-message indicators.
- Added Bottom Left, Bottom Right, Top Left, and Top Right tab positions.
- Selected channels no longer show unread counts while chat is closed.
- Preserved vanilla formatting, clicks, hover actions, completion, history, drafts, and PM handling.
- Added team speech bubbles, chat background opacity, history, drafts, and tab cycling settings.
- Improved HUD rendering so tabs remain visible after chat rendering.

### Compatibility and packaging

- Added Lunar/Ichor-compatible Minecraft singleton access to prevent startup `NoSuchMethodError` crashes.
- Renamed the main artifact to `OTL.jar`.
- Renamed the source artifact to `OTL-source.jar`.
- Updated the manual release-artifact workflow.
