# Overcast Community Server Utilities

![Over The Limit: crosshair, EXP and queued kill confirmations](docs/assets/otl-banner.svg)

**Over The Limit (OTL) v0.3.3** is a client-side Forge 1.8.9 mod for the Overcast Community server. Press **P** for the HUD editor and modules; press **J** for Face Rig. [Download the release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.3) · [What's new](CHANGELOG.md)

## Modules

| Module | What it does |
| --- | --- |
| **Face Rig** | Animates skin-based eyes and expressions; includes an in-game editor. |
| **Reliable Chat** | Adds channel/PM tabs, drafts, scrollable history and optional team speech bubbles. |
| **Map Preview** | Shows book/chat map previews, a searchable gallery and `/map` info cards. |
| **Prefered Hand** | Visually chooses the first-person hand for items, blocks, tools or weapons. |
| **Kill Feed** | Displays deaths, causes and weapons with a configurable HUD and history. |
| **Kill Confirm** | Shows queued kill tags, streaks and immediate EXP feedback; OFF by default. |
| **Server Notices** | Separates server announcements from chat in a standalone HUD; OFF by default. |

### Details

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
- Drag the scrollbar beside open chat, click its track, or use its up/down buttons to browse without a mouse wheel. Each channel keeps its own scroll position.
- Original vanilla chat components, click/hover actions, and selected-channel chat buffers are retained for client chat extensions such as Lunar's copy feature. OTL adds no clipboard shortcut or copy popup. Lunar's native copy hook still needs live verification.
- **Reliable Chat → Settings → Appearance** has separate **Tabs - typing** and **Tabs - not typing** background opacity sliders (0–100%, default 100%), independent of chat-message backgrounds. Typing affects chat-input tabs including PMs/overflow; not typing affects gameplay tabs and HUD previews. A solid white underline always marks the selected channel, even at 0% opacity; labels remain visible. Existing tab-opacity preferences carry over to both sliders. Reset chat options restores both to 100%.
- Server command feedback starting with `⚠` goes to the channel the slash command was sent from, even if you switch tabs before its reply. The command context expires after eight seconds and resets on server/world changes; quoted player messages are not treated as feedback.
- Speech bubbles use only server-delivered **`(Team Name) Player: message`** chat, including your own messages (visible in third person). No join detection, team-color comparison, or scoreboard membership is required. Global, party, private, system and hub messages never become bubbles. Each nearby player can have three messages: new messages appear below, older ones slide upward over 200 ms, and the oldest is dropped when a fourth arrives. Text limit and lifetime remain configurable; the former two-second cooldown is removed.

### Prefered Hand

- Toggle **Prefered Hand** in the modules menu (**P → Mods**), then open **Settings**. Defaults: module OFF, **Main Hand: Right**, **Type: ALL**; active only on approved servers.
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

### Kill Feed

- Enabled by default on approved servers, at the top right. Recognizes English [PGM death messages](https://github.com/PGMDev/PGM/blob/dev/util/src/main/i18n/templates/death.properties) and displays killer → weapon/cause → victim, retaining name colors. Both names are bold when you kill or die; unrelated players' kills keep normal names. Your kills have a gold border, and your deaths have a red border. Self-kills/unassisted deaths show a skull before the victim without a colored border.
- Uses Minecraft item sprites for recognized weapons, including wooden swords and bows, with distinct fall/void icons rather than text labels and distances formatted as `(+50 Blocks)`. Environmental deaths have no invented killer; custom/unknown weapon names use a generic cause icon.
- Lava knockbacks retain the fist/weapon followed by a separate lava icon; flames use a distinct fire icon. Self/unassisted deaths show skull → name → known cause (lava, fire, fall, void, etc.), without a colored outline.
- Context icons use lava, flame, cracked-ground impact, and a void hole—not generic down arrows. The HUD editor uses the real Kill Feed renderer with stable sample entries and measured content bounds.
- **Settings → Appearance** has a size slider (75% by default, 50–150%) and independent **Background opacity** (black fill) and **Outline opacity** (colored stroke) sliders/reset buttons. Neither affects names/icons. Existing combined-opacity preferences migrate to both controls. **Colors** has kill/death outline swatches and optional hex entry. Only the hovered row highlights; original-message tooltips remain at normal readable size.
- **General → Slide animation** optionally animates new entries over 200 ms; OFF by default.
- Show 1–5 entries (default 5) and choose a 1–30 second display lifetime (default 10); older entries fade during their final second. Open chat to hover for the original formatted message, scroll through history, or drag the scrollbar without fading. Up to 1,000 entries are kept in memory; world changes clear them, and nothing is written to disk.
- **P → Mods → Kill Feed → Settings → Hide deaths in chat** is OFF by default. When enabled, only recognized deaths are hidden; ordinary chat and unrecognized/localized messages are untouched. Disabling the module also stops chat suppression.
- Use the module menu's next-page arrow on smaller screens to reach all modules.

### Server Notices

- Standalone module, **OFF by default**, independent of Reliable Chat. Open **P → Mods → Server Notices → Settings**.
- Modes: **All notices**, **Join/leave only**, or **Except join/leave**. Observer/global/team/party/private player messages stay in chat, including decorated names and plain observer `Name: message` formats. Ambiguous player-like lines remain in normal chat rather than being moved.
- Selected notices go **only** to the notice feed, not normal chat. When disabled or excluded by the selected mode, messages follow normal chat routing. Action-bar messages are untouched.
- Own panel/border-opacity setting and a **Size** slider (50–150%, default 100%) with **Reset size**. At 100% text follows Minecraft's GUI scale; custom sizing applies in both gameplay and HUD preview. A 10-second display and up to 100 session-only original message components; world changes clear the feed and no chat content is serialized.

### Kill Confirm

- Separate module, **OFF by default**: enable under **P → Mods → Kill Confirm**. Works independently of Kill Feed and Reliable Chat on approved servers.
- Explicit `+5 raindrops | Kill` / `+3 raindrops | Kill Assist` server messages trigger an animated score label, without a hit marker. The large gold **`+N EXP`** runs total is the anchor and starts on the crosshair; the smaller white stack sits to its right and reveals **KILL** or **BOW KILL** first, then up to three bonus tags, then the streak. Other reward amounts are supported. Player chat containing those words does not count; original reward messages keep their normal chat/Server Notices routing.
- Bonus tags, highest priority first: **SHUTDOWN** (victim was on a 5+ streak), **REVENGE** (victim killed you last), **FLAWLESS** (melee duel where the victim had 18+ HP on your first hit and your health never dropped until they died), **LONG SHOT** (bow kill from 30+ blocks), **DOUBLE KILL / TRIPLE KILL / MULTI KILL** (kills within 3 seconds), **LAST BREATH** (kill at 1 heart or less) or **SURVIVOR** (2 hearts or less), **UNDERDOG** (victim had more kills than you), **FIRST BLOOD** (your first kill since joining), **COMEBACK** (kill after 3 deaths in a row), **AIRBORNE**, **SNEAK**. At most three tags show per kill; any others are dropped by priority.
- Kills, deaths and streaks are counted client-side from the same death messages the Kill Feed reads, starting when you join; assists are only known for you. Nothing is sent anywhere. Streaks read **N KILL STREAK** from 2, then **RAMPAGE** at 5, **UNSTOPPABLE** at 10, **GODLIKE** at 15 and **LEGENDARY** at 20+.
- The white stack scrolls upward with at most **3 lines visible**; each new line slides in from the left and the oldest one drops off. A kill that lands while rows from an earlier kill are still waiting queues its **KILL** line below them, so rows always appear in kill order; the gold EXP total updates immediately. EXP accumulates with the kill streak and resets on death. Assists still display their own EXP popup, but never show tags or streak lines, increase the streak or EXP total, or reset it. The next kill can show the retained streak again. Local death (recognized death message or actual player death), disabling the module, and world/server changes reset it. Feedback fades about 3 seconds after the last queued row appears; a new reward keeps the popup alive until its rows have shown. Streaks and counts are session-only, not saved.
- **Hide rewards in chat** is OFF by default. When enabled, recognized kill/assist raindrop rewards are hidden from chat/Server Notices while Kill Confirm still displays the popup. Player chat, unrelated rewards, and messages received while Kill Confirm is OFF keep their normal routing.
- Defaults to a small **75%** size with the gold EXP's **+** sign centred on the crosshair and **0% background opacity**. **Kill Confirm → Settings** provides a 50–150% size slider, background opacity, Reset size, and Reset position. Saved custom sizes/positions are preserved. Drag, resize, and snap its preview in the HUD editor; its gear opens the same settings. No sound effects.

## HUD positioning

Press **P** to open the HUD editor. The central **Mods** button opens the module list. Drag the gameplay chat tabs, Kill Feed, or Server Notices panel; drag any corner handle to resize (50–150%). Visible frames enclose the previews with attached gear/disable controls. Center cross guides and temporary alignment guides help snap content to screen edges/center and other widgets, including adjacent four-pixel gaps. Drag away to release snapping, or hold **Shift** to bypass it. The old alignment/reset toolbar is removed. Positions and sizes are saved; positions stay screen-relative and clamped when GUI scale/resolution changes. Editor frames/guides do not appear during gameplay or change background/outline opacity.

Each panel has an attached settings gear and an **x** to disable it. Disabled panels remain as editor placeholders with a **+** to re-enable them. Kill Confirm also supports the same positioning/resizing/snapping. These placements affect screen-space HUDs, not first-person items or world-space face rigs/speech bubbles; chat-input tabs stay attached to the input field.

The editor previews actual chat tabs and Server Notices, as well as Kill Feed, instead of empty rectangles. Notices use sample messages when their history is empty and reflect their mode/opacity settings.

## Download

Download `OTL.jar` from the [v0.3.3 release](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.3) or [browse all releases](https://github.com/Gr3nDy/Over-The-Limit/releases).

Place `OTL.jar` in your Forge 1.8.9 client's `mods` folder manually. This build has not been verified in a live Lunar/OCC session.

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
For one feature, add `-PotlSmokeScope=killconfirm` (or `chat`, `face`, `menu`, `maps`, `hand`, `bubbles`, `killfeed`, `notices`, `tabs`). Use the full run when shared code changes. The Kill Confirm run captures `run/screenshots/otl-kill-confirm-burst-queued.png` and `run/screenshots/otl-kill-confirm-burst-fourth.png` for a four-kill queue.
Add `-PotlSmokeRemote` to require live lookups from **both** map repositories. Without it, remote checks are explicitly disabled, not reported as passed.

## License

Licensed under the **GNU General Public License v3.0**. See `LICENSE`.
