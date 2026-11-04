# Over The Limit (OTL)

Client-side Forge 1.8.9 mod combining a skin-based Face Rig with Reliable Chat.

## Installation

1. Close Minecraft.
2. Place `OTL.jar` in `.minecraft/mods`.
3. Remove any older face-rig mod before launching.

Press **P** to open the OTL menu. Press **J** to open the Face Rig directly. Both
keys can be rebound in Minecraft Controls under **Over The Limit**.

## Face Rig

- Face Rig is **OFF by default**; Reliable Chat is **ON by default**.
- Eyes, pupils, brows, and eyelids use the player's own skin pixels.
- Blush is a light-pink bar mirrored below both eyes.
- Sneaking temporarily hides the entire face rig for that player.
- Glances and blinking default to **All**; the blink interval defaults to 3.6 seconds.
- Over-hat rendering, blush, and glowing eyes default to **None**.
- The editor supports Eye, Pupil, Brow, and Erase marks with undo, save, cancel, and reset.
- The preview shows the complete head, including hair and hats.

Face settings are stored in `config/otl/face.json`; painted marks are stored in
`config/otl/marks/`.

## Reliable Chat

Reliable Chat is designed for `play.oc.tc` and `vanish.oc.tc`.

- Detects lobby and game transitions and selects the appropriate chat tabs.
- Routes All, Team, Party, and private messages to their correct channels.
- Preserves vanilla formatting, clicks, hover actions, history, completion, and scale.
- Supports PM tabs, unread counts, drafts, optional history, and team speech bubbles.
- Does not guess recipients or duplicate unconfirmed private messages.

Settings are stored in `config/overcast_util/settings.json`. Settings remain editable
outside supported servers, but server-specific features activate only on supported
hosts.

## Upgrading from 0.2.x

- The mod ID is now `otl`; rebind **P** and **J** once if necessary.
- Face settings moved to `config/otl/` and old painted marks must be repainted.
- Reliable Chat settings remain in `config/overcast_util/settings.json`.

## License and source

OTL is licensed under the **GNU General Public License v3.0**; see `LICENSE`.
The Face Rig is an original implementation and contains no third-party face-mod code.
Corresponding source is supplied in `OTL-0.3.0-sources.jar`.

## Development

Use JDK 8. Run the unit tests with:

```text
gradlew.bat test --offline --no-daemon
```

Create a clean release build with:

```text
gradlew.bat clean build --offline --no-daemon
```

The optional `-PotlSmoke runClient` task runs local packet and UI integration checks.
Live server formats, command acceptance, and OptiFine/shader compatibility require
verification in a real client.
