# Changelog

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
