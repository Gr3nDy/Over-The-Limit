# Over The Limit — Wiki

**Over The Limit (OTL)** is a client-side Forge 1.8.9 mod for the [Overcast Community](https://overcastcommunity.com/) PGM-style server family (`play.oc.tc`, `vanish.oc.tc`, `spectrum.oc.tc`).

**Current release:** [v0.3.9](https://github.com/Gr3nDy/Over-The-Limit/releases/tag/v0.3.9) · **Source:** [github.com/Gr3nDy/Over-The-Limit](https://github.com/Gr3nDy/Over-The-Limit) (branch `beta`)

**New in v0.3.9:** Team Bar layout and clock fixes (fixed team order, ten heads per row then wrap, a centred clock that no longer vanishes while observing, no skin flicker, FFA pooled with no colour line), votebook `(You: 12)` and multi-colour names, and a slimmer toggleable chat scrollbar plus a draggable settings scrollbar. Full list in the [changelog](../CHANGELOG.md).

## Installing

1. Download `OTL.jar` from the [latest release](https://github.com/Gr3nDy/Over-The-Limit/releases/latest).
2. Place it in your Forge 1.8.9 client's `mods` folder.
3. Restart the client. No build task installs anything into Lunar or any other launcher.

## Using it

Press **P** to open the HUD editor. From the editor you can toggle, resize, and reposition every HUD module; per-module settings live under each *Settings* button. Press **Esc** to close.<br>
Every module also has a **Reset** button in its settings screen — now inside the module body, top right — that restores that module's defaults. Settings live in `config/Over The Limit/settings.json` and `profiles/<name>.json` (per-profile); the `config/Over The Limit/profiles/` folder holds your profiles. Every module option, including the Team Bar ones, is stored per profile, and a new profile starts from factory defaults.

## Modules

### Face Rig — OFF by default

Animates skin-based faces for yourself and other players using server-confirmed overlay data.

- **Face Rig** master switch in Mods.
- **Face settings**: blush, glowing eyes, glances, blinking, hat — each with *Only me / All / None* modes.
- **Editor**: in-game editor opened from Mods. Paint eye marks per skin, or automatic detection runs from the skin pixels.
- **World lighting** can be toggled from *natural* to *fullbright* (night lighting changes).
- Painted marks are stored per skin and persist across relogs; automatic detection is re-done per texture.

### Reliable Chat — ON by default

A tabbed chat with channel history, drafts, and optional team speech bubbles.

**Basic options (General tab):**
- **Reliable Chat** master switch (when off, the mod stays out of chat completely).
- **Unread counts** — unread message counts on channel tabs.
- **Remember drafts** — drafts persist per channel; **Unlimited history** — no 100-line cap; **Shift+Tab cycling** — cycle channels with Shift+Tab.
- **Show Tab Chat**, and **Tab position** (Bottom Left / Bottom Right / Top Left / Top Right) — where the channel strip renders.

**Team bubbles:**
- **Team speech bubbles** (OFF by default) — renders a floating speech bubble above a player's head in world when they send a team-chat message.
- **Mode** — *Team Chat only* (only explicit team-chat lines bubble) or *Team & Team Chat* (own-team global chat also bubbles when it matches your team color).
- **Text limit** (20–200 characters) and **Duration** (1–15 seconds).
- Bubbles are suppressed on announced FFA and other teamless maps, stay hidden while an announced map's metadata is unknown, team maps restore them automatically, and world changes clear old bubbles.
- Only messages in `§`-anchored `(Any Team)`/`[…]`/`(Team)` prefixes are recognised; quotes and map lookups never create bubbles.

**Appearance:**
- **Tabs – not typing** and **Tabs – typing** — separate opacity sliders for idle and focused states, plus a reset to 25%.
- **Chat scrollbar** — the slim pill you drag to browse history; turn it off to hide it entirely.
- Chat lines themselves are drawn without a background box, and closing the chat returns the history to the newest message.

### Map Preview — ON by default

Shows previews of map names announced in chat, `/map` commands, and vote books.

- **Gold map links** — hover a gold map name in chat to see the preview image.
- **Vote books** — face-hover any map name on an open written book to preview it (favorites get a star; the gallery can filter Favorites only).
- **`/map` command** with an in-game card: map name, game mode, objective, teams, dimensions, build height limit, contributors.
- **Gallery** — browse every bundled map, search by name, open a map's info card.
- **Large Map Preview** toggle — bigger previews in chat (also in HUD editor).
- **Preview on command** — `/map <name>` in chat opens the card (default: on).
- **Favorites** — star maps; they live in your favorites filter everywhere.
- Map previews are also usable in the HUD editor's *Map Preview* preview.

### Team Bar — ON by default

One row of player heads per team at the top centre, stacked, with the match clock in the middle.

- **Team Bar** master switch; heads only appear during a real match, never in the hub. During the match-start count-in the clock shows alone.
- **Layout** — teams are listed in a fixed order by team name, so a team keeps its column for the whole match and the bar never reshuffles when somebody joins or leaves. Rows alternate left and right: the left column is flush against the clock and grows leftward, the right column is flush against the clock and grows rightward, so no empty space ever sits next to the clock.
- **Ten heads per row** — a bigger team wraps onto the rows below at full size instead of shrinking its heads.
- **Your own head** always leads its team, at the inner end of a left block and at the outer end of a right block.
- **Any number of teams** — rows keep stacking downward. Ten heads fill a row and the rest wrap onto the rows below, so heads stay full size.
- **Clock** - read live from PGM (boss bar, then sidebar, then tab footer) and ticked locally between server updates. **Yellow** for a time limit, **red** in its last 25 seconds, **red** for the match-start countdown, **white** for a count-up. It freezes at match end and holds that final time until the map changes, then resets to `0:00`. The clock sits on the screen centre at all times, including before teams are assigned and while you are watching from the observer team.
- **Dead players** fade to grey and clear on respawn; a head never disappears on death. Players who leave, go to the observers or change team are moved or removed.
- **Team colour line** (ON by default) — a thin vertical bar in the team's own colour beside each row, on the bar's outer edge. It is never drawn in FFA.
- **FFA** — FFA puts every player on their own one-member team, each with its own colour, so there are no sides: the bar pools all players into the same rows (your own head first) and left/right only split the stack to keep it balanced.
- **Hide bossbar** — hides PGM's boss-bar clock while the Team Bar shows it.
- **Show in observer** (ON by default) — keep the bar while watching.
- **Players shown** (1–75, default 5) and **Size** (50–150%).

### Custom Votebook — ON by default

Replaces the written vote book with a compact panel.

- Map cards with previews and vote counts, and a **Supervote** button that unlocks once you have voted.
- **Match Stats** row — one column per category, each with the leading player's head, name and value, plus `and N more...` for ties.
- Your own row shows **`(You)`** on its own last line, under the value, in every column where you appear. If PGM sends `(You: 12)` with your own value, that line reads `(You: 12)` and your name stays clean.
- Names keep their full chat styling, so a rank tag and a differently coloured name both show in their own colours.
- **Match link** to the match page, and map **favorites** with a star on the card.

### Prefered Hand — OFF by default

Visually chooses which hand your items render in first-person.

- **Main Hand** — Right or Left.
- **Type** — categories of item to force to your chosen hand: *Weapons/Cards*, *All items*, *Tools only* or *Everything*.
- When enabled, wraps `ItemRenderer` so your preferred slot renders as if you were right/left-handed.

### Kill Feed — ON by default

A configurable death feed, independent of chat.

- **Player heads**: *Only me / Off / All / Team* — whose head shows on kill rows.
- **Show entries**: *All / Only Me / Only My Team*.
- **Hide deaths in chat** — kill lines disappear from chat so they only appear in the feed.
- **Outline / Background opacity**, **Row size** 50–150%, **Outline** color, **visible rows** (1–5), **fade duration** (1–30 seconds), and an optional **slide-in animation** (default OFF).
- **Kill history** — hover the feed to reveal the raw original chat line that produced it.
- KillFeed never disappears at world changes; history is session-only.

### Kill Confirm — OFF by default

Shows a focused, richer HUD for your own kills, streak milestones and chapterenders.

**Toggles:**
- **Kill Confirmation** master switch.
- **Sound** for confirmations, **victim heads** in streaks, **Hide kill confirm** (hide the EXP recap), **Total EXP** (host-confirmedEXP only).

**Feedback:**
- A queued row appears for each of your kills, with EXP reward, when the queue resolves.
- **Streak milestones** — at 5/10/15/20/25/30 (then every 10): **RAMPAGE** (5+), **BEYOND THE LIMIT** (30+) and **THE LIMIT BREAKER** (100+), each with the exact streak count. Rapid chains advance through NONA KILL and MULTI KILL (10+).
- **Rapid-chain tags** — within three seconds of each other, tags like CLEAN SWEEP (five different opponents), BRAWLER (three melee kills), DEMOLITION (explosion kill), HOT HAND (three bow kills), MIXED ARSENAL (melee + bow in any order). Three bonus tags per chain maximum.
- **REDEMPTION** — you kill a player who killed you five times since you last killed them.
- **DOMINATION** — five kills against one opponent in a rapid chain.
- **Long-range**: LONG SHOT at 30+ blocks; **POINT BLANK** (≤3); **EXTREME RANGE** replaces LONG SHOT at 60+.
- **EXP recap on death** — a three-line total of EXP gained that life (Kills EXP / Assists EXP / total EXP). Only appears when your previous life earned something.

**Interactions:**
- Kill Feed can stay on independently of Kill Confirmation.

### Bow Optimizer — OFF by default

Toned-down bow optimization: local feedback, none of the vanilla numeris.

**Everything here is local-feedback only — no changes to charge speed, damage or outgoing packets.**

**Independent toggles under the module switch:**
- **Optimization** — replaces the same-slot durability equip dip with a local continuous render.
- **Live Bow Accuracy** — server-confirmed arrow hits divided by valid local shots.
  - **Prefix** — shown as `Bow Accuracy: N%` (ON by default; OFF to show only `N%`).
  - **Mode** — *Always* (default), *On Hit* (two-second flash after a confirmed hit), *Holding Bow* (shown while a bow is equipped + one-second grace after switching).
  - **Fade In / Fade Out** sliders (0–100% means 0–1s), **Animate Percentage** (smooth count-up, default ON), **Background opacity** (0–100%, default 25%), **Accuracy size** (50–150%), **Decimal places** (0–2, default 2).
- **Bow Hit Sound** (OFF) — replaces the vanilla hit sound with a quieter tick (COD-inspired).
- **Bow-Kill Sound** (OFF) — low chime on your server-confirmed bow kills.
- **Hit Marker** (OFF) — a white/red crosshair marker on confirmed hits/kills; size adjustable 50–200%.
- Accuracy resets on map/world changes, reconnects, and recognized PGM match endings/starts.

### Server Notices — OFF by default

Separates server announcements from player chat into their own overlay.

- **Server Notices** master switch (locked OFF in this release).
- **Mode** — *All*, *Joins & leaves only*, *Non-joins only*.
- **Panel/border opacity** slider, **Size** slider.
- **10-second display window**; up to 100 buffered lines; player/team/observer chat never routed here.

### Region Borders — OFF by default

Wireframes of the current map's regions from its `map.xml`, plus the region name you are standing in.

- **Region Borders** master switch, **Region text** toggle, **Prefix**, **Background** opacity.

### Height Limit Overlay — OFF by default

Overlay-only build-height marker for PGM maps where all build must stop at a fixed Y.

- **Height Limit Overlay** master switch (locked OFF in this release).
- Reads the build limit from the announced PGM map metadata (`<maxbuildheight>`).
- **Opacity**, **Size**, **Indicator seconds** sliders, **Top-only** toggle.

### Auto GG — OFF by default

- **Auto GG** master switch. Says `gg` once per match, when the server announces the end.
- Never fires on map start: a result line left over from the previous match is ignored until this client sees the match begin.
- The `gg` is sent once per match only, so a double "Game over!" cannot repeat it.

### Minimap — OFF by default

An isometric terrain viewport that downloads each map's mesh on demand.

- **Minimap** master switch.
- **Zoom level** (1–5) and **Alt Zoom level** sliders, snapped. Press `;` in world to swap between them.
- **Player marker size** 50–150% slider.
- **Border** (ON by default) — square cyber-border around the panel.
- **Coordinates**, **Cardinals** (N/E/S/W), **Static rotation**, **Large background** opacity.
- **Static/Follow mode** — static always centres on the map's bounds vs. follow the player.
- **Background opacity** 0–100%.
- **Current map** — set from the server's `Playing … by …` notice, or the centered `Created by …` title. Arbitrary `/map` lookups and player quotes never switch the current minimap.
- **Missing maps** — a map without a local mesh shows `Preview unavailable` (with a `/map` hint), not a stale map.
- World changes clear the old map immediately.
- Meshes downloaded per map from the configured mesh repositories on first play and cached under `config/Over The Limit/Maps/`.

## Configuration

OTL's data lives entirely in `config/Over The Limit/`:

- `settings.json` — global module settings
- `profiles/Default.json` — per-profile settings (create more profiles from Mods)
- `face.json` — Face Rig preferences, including painted marks
- `marks/<name>.txt` — painted face marks per skin
- `profiles/` — all saved profiles
- `Maps/` — downloaded terrain meshes, cached
- `map_cache/` — map preview cache

Every module option is per profile; a profile that is missing a newer option falls back to its factory default instead of inheriting the previous profile. Settings removed in a release (such as the old `chat_background_opacity`) are ignored and dropped on the next save, so an existing profile keeps loading without errors.

Legacy `config/overcast_util` and `config/otl` folders are migrated automatically on first launch; `config/overcast_util` is left as a rollback copy.

## Troubleshooting

- **Logs**: `logs/latest.log` inside your client directory — search for `OTL:` for mod-specific lines.
- **Smoke tests**: `gradlew.bat runClient -PotlSmoke --offline` runs the built-in test fixtures.
- **Minimap says "Preview unavailable"** for your current map: wait a few seconds after joining (the mesh downloads the first time a map is played); dead maps can 404 once — join again to retry.
- **Chat looks wrong after a config reset**: hit `P → Mods → Reliable Chat → Settings → Reset chat options`.

## Licensing

- **Source code and binaries**: GNU General Public License v3.0 (`license/LICENSE`)
- **Map data** (previews bundled in the jar; meshes downloaded on demand): Creative Commons Attribution-ShareAlike 4.0 International, by the respective map authors (`license/LICENSE-CC-BY-SA-4.0.txt`, `license/NOTICE.md`, `license/ATTRIBUTION.txt`)
- Game content © Mojang Studios. Not affiliated with or endorsed by Mojang or the Overcast Community.

## Problems or questions?

Open a [GitHub issue](https://github.com/Gr3nDy/Over-The-Limit/issues). Include your client (Lunar/Forge), which module is involved, and what you expected versus what happened.
