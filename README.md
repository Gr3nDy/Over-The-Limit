# Over The Limit (OTL)

Client-side Forge 1.8.9 mod combining a skin-based Face Rig with Reliable Chat.

## Get the JAR

For a published beta binary, open the repository's **Releases** page:

<https://github.com/Gr3nDy/Over-The-Limit/releases>

Download `OTL.jar` from the latest beta release and place it in your
`.minecraft/mods` folder. The `beta` branch is the source branch; it does not
contain the binary JAR itself.

To build the JAR yourself from `beta`, install JDK 8, clone the repository, and run:

```text
git clone -b beta https://github.com/Gr3nDy/Over-The-Limit.git
cd Over-The-Limit
gradlew.bat clean build --offline --no-daemon
```

The result is `build/libs/OTL-0.3.0.jar`. Rename it to `OTL.jar` if desired.

## Installation

1. Close Minecraft.
2. Place `OTL.jar` in `.minecraft/mods`.

Press **P** to open the OTL menu. Press **J** to open the Face Rig directly. Both
keys can be rebound in Minecraft Controls under **Over The Limit**.

## Mods

### Face Rig

- Face Rig is **OFF by default**; Reliable Chat is **ON by default**.
- Eyes, pupils, brows, and eyelids use the player's own skin pixels.
- Blush is a light-pink bar mirrored below both eyes.
- Sneaking temporarily hides the entire face rig for that player.
- Glances and blinking default to **All**; the blink interval defaults to 3.6 seconds.
- Over-hat rendering, blush, and glowing eyes default to **None**.
- The editor supports Eye, Pupil, Brow, Lid, and Erase marks with undo, save, cancel, and reset.
- A manual Lid mark selects the exact skin pixel used for that eyelid; automatic sampling remains the fallback.
- The preview shows the complete head, including hair and hats.

Face settings are stored in `config/otl/face.json`; painted marks are stored in
`config/otl/marks/`.

### Reliable Chat

Reliable Chat is designed for `play.oc.tc`.

- Detects lobby and game transitions and selects the appropriate chat tabs.
- Routes All, Team, Party, and private messages to their correct channels.
- Preserves vanilla formatting, clicks, hover actions, history, completion, and scale.
- Supports PM tabs, unread counts, drafts, optional history, and team speech bubbles.
- Does not guess recipients or duplicate unconfirmed private messages.

Settings are stored in `config/overcast_util/settings.json`. Settings remain editable
outside supported servers, but server-specific features activate only on supported
hosts.

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
