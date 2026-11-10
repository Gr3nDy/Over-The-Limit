# Changelog

## v0.3.7 - 2026-11-11

### Changed
- License files reorganized into `license/`; the release jar now bundles `license/LICENSE`, `license/LICENSE-CC-BY-SA-4.0.txt` and `license/NOTICE.md` under `META-INF/`.
- The redundant `COMMUNITY_MAPS_LICENSE.txt` is no longer bundled in the jar.
- No gameplay or settings changes; at runtime this release is identical to v0.3.6.

## v0.3.6 - 2026-11-11

### Changed
- **Minimap meshes are downloaded on demand** instead of being bundled in the jar (jar is ~77 MB of previews + code; meshes fetch per current map with 3 silent retries).
- **Minimap zoom**: 1-5 snap levels with a hidden **Alt Zoom level** slider on its own `;` hotkey; the player marker scales with the zoom; border ON by default.
- **Chat background** is a snap slider under Reliable Chat (0-100%, default 25%), matching the tab background sliders.
- **Configuration renamed** to `config/Over The Limit`; the legacy `config/overcast_util` is auto-migrated. Face Rig preferences and painted face marks moved from `config/otl` into the same folder, auto-migrated.
- **Module reset buttons** reset only the module they belong to: Kill Feed no longer resets the chat tab HUD scale, Face Rig resets its face options too, Reliable Chat also restores the tab HUD position.

### Fixed
- **World renderer is isolated from the HUD**: GL state is hard-reset after every minimap draw (the head marker no longer leaks `GL_ALWAYS` depth compare; write-mask/depth-func restored unconditionally).
- Minimap: one malformed `minimap-index.json` entry no longer disables the whole minimap; corrupt/truncated downloads report `Map broken; try again later` instead of silent retry loops; mesh downloads tolerate map directories with spaces and non-ASCII names (path segments are URL-encoded).
- Minimap status text no longer overflows the panel (`Preview unavailable` trimmed with an ellipsis and centered).
- Modules screen: last-row cards are drawn at the position they are clickable at; the HUD editor no longer opens off a stale key press made behind a GUI; module scan survives an unreadable module package listing.
- Chat: party chat with a mixed-case `(Party)` prefix lands in Party instead of All; Reliable Chat sessions are capped at 24 hosts; Kill Confirm per-player stats are capped at 256 names.
- Face Rig: skin readback retry loop terminates after 3 failures instead of re-reading GL every frame; marks files with one corrupt line no longer wipe the whole file; face data now lives next to the rest of the config.
- Config migration can no longer strand a partial config: files are staged and moved atomically; the legacy folder is deleted only after every file is copied.
- Kill Feed: lava deaths are no longer labelled FALL damage; right-click no longer swallows the scroll-drag; a zero `kill_feed_size` no longer divides by zero.

### Removed
- 74 MB `Over The Limit.rar` binary artifact removed from the repository.

## v0.3.5 - 2026-11-09

### Added
- **Bow Optimizer** (module OFF by default): immediate local bow-release sound and reduced same-slot bow durability equip dips. Optimization, accuracy, hit sound and hit marker have independent controls under the module master switch; no changes to aiming, charge speed, damage or outgoing packets.
- **Bow Accuracy HUD**: server-confirmed player arrow hits divided by valid released shots, movable/resizable in the HUD editor, adjustable background opacity and 0–2 decimal places (default: 2). **Prefix** is ON by default; OFF hides `Bow Accuracy:` and shrinks the HUD background. Counts are session-only and reset on match endings/starts, map/world changes and reconnects.
- Optional **Bow Hit Sound** and **Hit Marker**, both OFF by default. The original hit tick uses vanilla audio; markers are white on hits and red on bow kills, with adjustable size from 50% to 200%.
- Expanded **Kill Confirm** streak tiers from RAMPAGE at 5 to THE LIMIT BREAKER at 100+, with exact streak counts. Three-second rapid chains now run through DOUBLE–NONA KILL, then MULTI KILL with exact counts at 10+.
- New Kill Confirm tags: **POINT BLANK** (reported bow distance ≤3 blocks), **EXTREME RANGE** (60+, replacing LONG SHOT), **DOMINATION**, **REDEMPTION**, **CLEAN SWEEP**, **DEMOLITION** and **BRAWLER**. The three-bonus-tag limit remains.
- Optional Kill Confirm victim heads and post-respawn total EXP/kill recap; Kill Feed player-head modes, feed filters and configurable assist outlines.
- Saved map favorites, a Favorites gallery filter and vote-book stars; book previews can show gamemode and server-provided vote tags. Optional team-colored global-chat bubbles alongside explicit team chat.

### Fixed / Changed
- Improved player-head caching and fallback behavior; Kill Confirm no longer flashes a skull while waiting for the victim name.
- Corrected Kill Confirm first-blood tracking, death/respawn state and rolling-row layout; improved death parsing and HUD sizing/settings behavior.
- Bounded map-preview texture caching, moved uncached map-name work off the render path and cached map-card text layout. Improved map XML handling and malformed-setting isolation.
- Fixed the Kill Feed slide-animation crash caused by an undersized OpenGL query buffer.
- **Height Limit Overlay is locked OFF**: the P menu shows **DISABLED**, toggle/settings controls are unavailable, and the HUD editor and runtime handlers cannot enable it.
- Added unit and focused Forge smoke coverage for combat tags, bow feedback/accuracy, Prefix persistence and bounds, and the disabled Height Limit menu.

### Notes
- Accuracy requires vanilla server player-hit notifications; mobs and servers omitting that signal are not counted. English PGM match messages are used for match resets.
- Bow sound suppression is conservative and can leave duplicate sounds. Live OC/Lunar compatibility and current server-rule approval have not been verified.

## v0.3.4 — 2026-11-08

- Kill and assist rewards now add to the same gold EXP total until death; assists still do not advance the kill streak or receive bonus tags.
- Kill Confirm's gold EXP now pulses outward more visibly and settles back with the `+` anchored on the crosshair. White rows align with EXP, and KILL/BOW KILL rows have a pixel skull prefix.
- Added **JUMPSHOT** for bow kills while off the ground. **AIRBORNE** now requires two blocks of clearance beneath the player; bow kills can earn both tags. **UNDERDOG** requires the victim to have at least 10 more observed kills than you.
- Added separate built-in kill and assist sounds behind one **OFF-by-default** Kill Confirm setting.
- Expanded Kill Confirm tests and focused Forge smoke coverage. Live Lunar/OCC behavior has not been verified.

## v0.3.3 — 2026-11-08

- Added **Kill Feed** with weapon/cause icons, colored outlines, independent background/outline opacity, 1–5 visible rows, adjustable lifetime, 1,000-message session history, hover originals and an optional slide animation (OFF by default).
- Added **Kill Confirm** (OFF by default): crosshair-centered `+N EXP`, immediate streak EXP, prioritized bonus tags, a three-line rolling white stack, and queued rows that wait for earlier kills. Assists award their own popup without changing streak or EXP. Eased pop/reveal/slide animations and a four-kill burst visual fixture are included.
- Added standalone **Server Notices** (OFF by default) with all/join-leave/exclude-join-leave modes; regular player messages stay in chat.
- Rebuilt the **HUD editor** with accurate previews and bounds, attached settings/enable controls, corner resizing and snapping; **P** opens the editor.
- Improved **Reliable Chat** with a draggable scrollbar, retained original chat components and separate typing/gameplay tab opacity; explicit team chat can display up to three stacked speech bubbles.
- Improved **Map Preview** with searchable CommunityMaps/PublicMaps galleries, on-demand PublicMaps assets, safe XML parsing, `/map` cards and book/chat hover previews. Added **Prefered Hand** settings for configurable first-person hand rendering.
- Added per-feature in-game smoke scopes. Unit tests and full Forge fixture smoke pass; live Lunar/OCC compatibility is not yet verified.

No client installation is performed by the build. The source branch does not track release jars.

## v0.3.2

### Modified
- **Team bubbles**: simplified to trigger only from server-delivered `(Team Name) Player: message` lines, including the local player's own messages. No join detection, team-color comparison, or scoreboard membership checks. Global, party, private, system and hub messages remain excluded. The 2-second per-sender cooldown remains.

### Fixed
- Team chat detection now uses explicit `(Team Name) Playername:` format exclusively.
- Join messages and name-color matching removed from bubble eligibility.
- Own team-chat messages now produce visible bubbles in third-person view.

## v0.3.1

### Added
- Map Preview module with book hover previews and searchable gallery.
- `/map` Forge client command with queued GUI opening.
- Gold map-name link previews in Reliable Chat and vanilla chat.
- Book hover previews with small/large toggles, no caption, tooltip-aligned.
- Prefered Hand module with Main Hand/Type settings and six type options.
- Team bubbles with observer default on matches, confirmed joins, hub exclusion, 2-second cooldown.
- PublicMaps (`main`) and CommunityMaps (`master`) catalog support.
- On-demand Public preview caching, not bundling all assets in jar.
- Secure JDK XML metadata parsing with default-variant selection and shared includes.
- Release build `build/libs/OTL.jar`; development/smoke jars in `build/dev-libs/`.

### Fixed
- Various bug fixes and stability improvements.
