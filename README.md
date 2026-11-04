# Over The Limit (OTL) — Forge 1.8.9

**Version 0.3.0.** Fully close Minecraft, remove the previous face-rig JAR from `mods`, and install **OTL.jar**. Do not install both. P should show **Over The Limit 0.3.0**.

Party membership errors such as **You are not in a party** appear in both All and Party, even without a recent Party send. They never switch the selected tab.

Gameplay features work only on `play.oc.tc` and `vanish.oc.tc` (ports allowed). Settings remain editable elsewhere.

Licensed under the **GNU General Public License v3.0** — see `LICENSE`. The face rig in 0.3.0 is an original implementation written for OTL; it does not include code from any third-party face mod.

The corresponding GPL source is supplied as `OTL-0.3.0-sources.jar` with the release.

## Upgrading from 0.2.x (one-time changes)

- The mod ID changed to `otl`, so keybinds reset once: rebind **P** and **J** in Minecraft Controls (category **Over The Limit**).
- Face settings now live in `config/otl/face.json` and painted marks in `config/otl/marks/`; old face settings do not carry over and painted faces must be repainted.
- Reliable Chat settings are unaffected (`config/overcast_util/settings.json`).

## Controls and Face Rig

- **P** opens OTL: Face Rig and Reliable Chat. **J** opens Face Rig. Both keys can be rebound or unbound in Minecraft Controls.
- The P menu has independent module ON/OFF toggles and Settings buttons. **Face Rig defaults OFF**, **Reliable Chat defaults ON**. Choices persist. Face Rig OFF disables both own and other-player face rendering, while its settings and preview remain usable.
- One settings page, no Advanced tab, with Fullscreen at the top right. Scroll for remaining controls on small screens.
- **FaceRig** master toggle (default OFF) and **Other Player's FaceRig** (default ON) are independently controlled.
- **Over the hat layer**, **Blush**, **Glowing Eyes**: Only me / All / None; default **None**. Blush is a short light-pink cheek bar below each eye, mirrored outward on the outer skin layer.
- **Glances**, **Blinking**: Only me / All / None; default **All**.
- **World lighting**: Natural / Fullbright. **Reset** restores face defaults.
- Blink interval defaults to 3.6 seconds (adjustable 1.5–8 seconds); click increases, Shift-click decreases. It is disabled when Blinking is None.
- Fixed full-head preview includes hair/hat and automatically blinks at the configured interval when Blinking applies to you. It does not follow the mouse.
- Eyes, pupils and brows replay the player's own face-skin pixels, so the eye colour follows the skin rather than a fixed white. Glances move the skin's own pupil cells; faces with only Eye marks get an automatic pupil from the darkest skin column. Eyelids use nearby face skin, not hair.
- Sneaking hides the face rig for that player while the sneak key is held.
- No expression wheel or reaction settings. The only combat expressions are bow aiming (the character's left eye closes and the brow focuses) and shooting (brief eye widen); they have no settings.
- **Edit face** opens the inline skin grid and preview. Erase/Eye/Pupil/Brow tools paint rig marks, right-click erases, Undo reverses edits. Save persists; Cancel restores the previous marks. Reset edits restores the automatically detected face and is separate from Reset settings; both require confirmation. Faces without convincing eyes can be painted by hand.
- Settings save automatically. Settings Reset keeps painted face edits. Painted marks are plain text files under `config/otl/marks/`, one `cell:kind` line per texel.

## Reliable Chat

- Detects lobby chat from **Overcast Community** plus **view our /servers or click the beacon**, in one message or separate messages within the same world. Lobby mode shows only All and sends plain text unchanged.
- **Welcome to Overcast Community** or **Created by <map author>** identifies a game server. On entering game mode, OTL selects Team and sends `/t` once. Ordinary map changes preserve full tabs and do not repeat that command. A new connection starts with full tabs pending lobby detection; it does not send `/t` until a game marker arrives.
- Clicking tabs and Shift+Tab only change the view. Normal text in All sends `/g <text>`, Team sends `/t <text>`, Party sends `/party chat <text>`, and a PM tab sends `/msg <name> <text>`.
- Explicit slash commands pass through unchanged. `/msg` and `/r` confirmations use the server's **To** recipient, appearing once in that PM and in All without selecting the conversation. No speculative delivered-message copies.
- PM prefixes require a colon after From/To or a clear separator after the name. Bare prose such as **to your monument!** stays in All and cannot create a PM tab. Unrecognized formats also remain in All.
- All keeps every original message. Formatting, clicks/hover actions, vanilla input/history/completion/scale and text opacity are preserved.
- Three visible PM tabs plus overflow. Incoming PMs never steal focus. Close preserves session history; Clear deletes the conversation/history/draft/unread count. Bulk clearing includes closed conversations and keeps the latest open conversation if one exists.
- Default history: 100 messages/100 wrapped lines per view. Unlimited history and remembered drafts are optional and default OFF. Histories, names and drafts are memory-only; Minecraft's own logging is unchanged.
- Unread counts are optional, exclude own messages, and clear on viewing. Overflow aggregates hidden PM unread counts. Other-channel arrivals do not refresh message ages or scroll position.
- Message backgrounds default to transparent; Appearance offers independent background opacity. Shift+Tab cycling defaults ON; normal Tab remains autocomplete.
- Relevant party/private-send failures are copied to the applicable conversation when the target is known. Failed `/r` without a server-confirmed recipient stays in All rather than guessing.
- Team speech bubbles default OFF; when enabled they learn the team from `You Joined <team>`, show matching-team messages only, and are depth-tested. Defaults: 80 characters, two lines, four seconds. Team/bubbles clear on world/server changes.

## Development and verification

Use JDK 8 and `gradlew.bat clean build --offline --no-daemon`. Unit tests run with `gradlew.bat test --offline --no-daemon`; they cover the pure face logic (eye detection, pose math, scope rules) and chat routing. `-PotlSmoke runClient` runs local packet/UI integration fixtures; always clean-build without the smoke property before distribution.

Local tests do not connect to oc.tc. Actual server message formats, command acceptance and OptiFine/shader compatibility still require live-client verification.
