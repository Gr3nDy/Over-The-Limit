# Changelog

## v0.7.2 - <PII type="DATE" id="20"/>

### Changed
- **Reliable Chat no longer takes over the client's chat** - OTL used to replace the game's chat object and swallow every incoming chat message before the client saw it. Other chat mods saw nothing, and the HUD's own drawing could leave a tinted GL colour behind for whatever was drawn next. The client's chat now receives every message untouched and draws the **All** tab itself, exactly as shipped. OTL only replaces the chat *draw* while a filtered tab is selected, and those tabs now use the same backdrop, fade and position as the All tab. Scrolling, page keys, link hover and clicking all act on whichever tab is on screen.
- **Channel tabs switch** - Reliable Chat's General page now opens with a **Channel tabs (All / Team / Party)** switch. Turning it off hides the strip both on the HUD and while typing, leaving the client's own chat, and the selected channel returns to All so nothing is left stranded on a hidden tab.

### Fixed
- **Kill feed cause on every language** - PGM writes death messages in each player's own language, so outside English the feed could only read the names and drew the plain skull for every kill. The translations PGM ships are now matched too, so the bow, explosion, melee, fall, fire, magic, cactus, anvil and drown icons come back in all 25 supported languages. German, French, Japanese, Thai and Chinese resolve every template, Korean 98% and Russian 97%, with no line left as a bare skull in either. English is untouched and still uses the hand-written templates.
- **Kill feed on translated clients** - German, Dutch, Swedish, Danish and Afrikaans glue a possessive onto the killer's name (`{1}s` renders as `Bobs`), so a line read as words found no killer and the death was dropped. A name is now matched as the longest known account name starting where the previous one ended, which also covers Japanese, where PGM writes both names with nothing between them. Affected 49 of 83 German killer lines and every Korean one.
- **Kill feed hover box** - a long translated death line could draw off the left or top of the screen. The box is now measured and clamped inside the screen on all four edges.

### Added
- **Team counts instead of heads** - a Team Bar switch replaces every team's row of heads with the number of players online, with that team's colour as a bar underneath. Each team keeps its own number, so it reads the same on a two-team map as on an eight-team one, and maps with eight teams count automatically because that many rows of heads no longer fit under the clock. Hovering a number lists everyone in that team. The team's score and objectives sit under its count, never beside the clock.
- **Update notice** - the first time you join a server in a session, OTL asks GitHub for the latest release and prints one chat line with a download link when yours is older. The check runs on its own thread, says nothing when it fails or is up to date, and can be turned off under PGM settings, CLIENT, "Update notice on join".

### Fixed
- **FFA objectives** - on a free-for-all map every player is their own team, so a control point or a flag had no side of its own and was dropped: Item Sumo showed the score with no point beside it. Those objectives now sit in the middle, under the clock and above the player grid.
- **Team Bar with heads hidden** - the team name and its objectives hung below an empty head row. With heads off there is no row to fill, so the name now sits level with the clock and everything under it moves up with it.

## v0.7.1 - Nov 20 2026

### Changed
- **Team Bar shared flags stay centred** - a flag both teams can capture is printed in one shared section, so it now sits under the clock in every state. Following the carrier made the row jump from the centre to a team's side each time the flag changed hands; the arrow still takes the carrier's team colour.

### Fixed
- **Delete profile confirmation** - clicking Yes deleted the profile but left the confirmation on screen. The menu was refreshed in place without being made the current screen again, so the dialog had nothing behind it and never went away.

## v0.7.0 - Nov 20 2026

### Added
- **Team Bar hide vanilla scoreboard** - an option to hide the sidebar while the bar is up, so the clock and objectives are not drawn twice.
- **Non-English servers** - PGM translates its own chat per player, so an English-only matcher saw nothing on a German or Japanese client. Three language-free signals replace it: a death line is read from the two account names it carries (the victim first, the killer second) rather than the words between them; the end of a match is read from the wither sound PGM plays to every participant; and the welcome block's map name comes from its heading, which is wrapped in a rule of dashes rather than words, so the block ends on that rule instead of on the translated author line. A death with only one name in it - a fall, a starve - is deliberately not guessed at, because a join message has the same shape.
- **Menu header** - the module browser now opens with `MODS` and `SETTINGS` sections in the header, like the launcher's own strip. `SETTINGS` holds PGM's `CLIENT` tab and `SERVER` tab, so the profile rail no longer needs a separate PGM button.
- **Clickable player list** - middle-clicking while the player list is up frees the cursor and keeps the real list on screen, so clicking a name lands on the row you can see. The menu offers View Profile, Teleport to player, Add Friend and Message player; middle-click again closes it. The CLIENT tab of the `SETTINGS` page holds the single on/off switch for it.
- **Team Bar rounded heads** - an option to draw each head as a rounded tile instead of a square one.
- **Reliable Chat global channel** — the All tab sends plain chat, the same text vanilla would send, instead of prefixing `/g`. Team, Party and private tabs keep their command prefixes.
- **PGM settings page** — the `SETTINGS` header section opens PGM's own server settings (chat, death messages, picker, join, DMs, observers, sounds, vote book, stats, effects, time). CLIENT/SERVER tabs match the `/settings` menu; picking a value sends the same `/toggle` the server expects, so the server stays the source of truth. The server page is disabled until a PGM server is detected.
- **Redesigned interface** - every OTL menu now shares one dark surface (`#12141B`) and a blue accent (`#3B82F6`), green/red switches, blue slider fills, rounded panels with 1px outlines, filter chips, and module cards with an icon slot, OPTIONS button and a coloured ENABLED/DISABLED/LOCKED pill. The module grid drops to two columns when three would truncate names; the map gallery cards, map card, vote book cards and Face Rig panes use the same 1px `#2A2E38` frame with recessed thumbnails and a blue hover ring.
- **Vanilla boss health bar** — optionally hide the vanilla health bar while keeping its name visible.
- **Team Bar hide player heads** - optionally remove the heads and reclaim their space while keeping the clock and objectives; the team-colour line stays on its own.
- **Team Bar colour line** - hovering it names the team it belongs to, so the line is usable without heads.
- **Team Bar stacked text** — an optional score display with the current value over the target and a divider between them.
- **Custom Vote Book** — sponsored maps have a gold border; hover to see who sponsored them when the server supplies that information.
- **FFA Team Bar** — player heads are centred beneath the clock, with a horizontal player-colour line and the player's live numeric score beneath each head.

### Fixed
- **Command warnings stay in the tab that sent them** - a `⚠` line from the server now lands in the channel you typed in, not in All. A tab that turns plain text into `/t` or `/party chat` had not recorded itself as the speaker, so its warning fell through to All.
- **Your own messages no longer count as unread** - a rank or guild tag on either side of your name (`(Blue) Alex [VIP]: hi`, `[VIP] <Alex> hi`) hid the fact that the line was yours, so All showed an unread badge for it. Those lines also route to Team instead of All now.
- **Reliable Chat tab and channel stay together** - sending a message never moves the visible tab. A typed `/t` used to switch the strip to Team as a side effect, so the next message went to team while the strip still said All; the tab is now the only thing that decides a channel.
- **Player list stops the player** - middle-clicking the list while walking no longer lets the player run on under the cursor: the list releases the movement keys and holds them released until it closes.
- **Ranked names in the player list** - a rank or guild tag around a name no longer hides the player: the click resolves the account name inside the row instead of requiring the whole row to be a bare name, and a row like `0/75 Red` still cannot pass itself off as a player.
- **Player list rows under a client that subclasses the tab overlay** - the header is now read from the overlay's real class, so its height is counted and rows are no longer hit-tested one row high. Ordering comes from PGM's own `TabView` slot numbers when the server provides them, and a click within one row of a name opens that name instead of doing nothing.
- **Clickable player list on PGM** - PGM draws its tab list from fake entries whose visible text is the real name, and pads it with score rows. The click now resolves the name the row is actually showing, so View Profile and Teleport work in a match instead of opening nothing or the wrong player. Blank rows keep their place but never open a menu, and the hover highlight covers the whole name cell instead of a name-width sliver. Rows are ordered and named by the client's own tab overlay rather than a copy of its sort, and the popup is drawn after the list instead of underneath it.
- **Clickable player list exit** — leaving the list hands the mouse back: a list held open by the key closes when the key is let go, and a toggled one closes on the next press of the same key.
- **Team Bar on three- and four-team maps** — a team's objectives now find their own block of heads by team name instead of the order PGM printed them. The sidebar sorts teams by score while the bar sorts by the map's team order, so a shuffled sidebar used to drop goals under another team's row or pile them at the bottom. Compact markers are also grouped per team, so one team's icons never sit on the line that belongs to the block above it.
- **Team Bar reconnect** — the bar can reappear when a welcome returns to the same map after leaving a match.
- **Team Bar shared flags** — a carried flag sits on its carrier's side of the clock like any other objective, so its arrow hugs the time instead of drifting to the outer edge. Several flags on one team each repeat that, and compact mode keeps only their markers on that side.
- **Team Bar control points** — match ownership colours against live team score labels when available, including four-team KOTH maps such as Toca.
- **Team Bar** — selecting Teleport to player closes chat after sending the command.
- **Team Bar hide on player list** — also detects player-list render events for toggle-style clients, with the held key as a fallback.
- **Team Bar objective progress** — percentage-based objectives, including cores and control points, use the selected text or bar style; hover a bar for its name and exact progress.
- **Team Bar score bars** — right-side horizontal bars fill from right to left, mirroring left-side bars; both styles have rounded pixel corners and quarter markers.
- **Team Bar with empty teams** — reserve distinct team columns and ignore synthetic sidebar row teams, so a single populated team cannot absorb other teams' objectives.
- **Team Bar clock** — the time-limit countdown turns dark red at five seconds remaining.
- **Team Bar** — automatically hide only the duplicate `Time Remaining` boss bar while Team Bar is visible; other boss bars are unaffected.
- **Kill Feed** — temporarily disabled unreliable assist attribution; kill/death entries and Kill Confirm assist rewards remain available. Lava and fire use Minecraft's lava-bucket and fire textures.
- **Team Bar score display** — the optional horizontal and vertical bars also work for numeric team scores with an explicit target, such as `100/200`; hover a bar for the team name and exact score. Scores without a target remain text.
- **Team Bar respawning players** — recognize PGM's synthetic tab-list entries when checking whether a player is still online, so their head can remain faded during long respawn waits.
- **Team Bar shared flag objectives** — show PGM's shared flag state and numeric countdown in compact and named layouts, independently of control point tracking. A flag with no confirmed carrier stays centred beneath the clock.

## v0.6.2 - 2026-11-17

### Known issues
- Score display has been reported not to work in some situations; the cause is not yet confirmed.

### Added
- **Team Bar header above the clock** (default None) — a three-way choice of None, map name or gamemode, drawn bold in the server's own colour. Hovering it shows the other one plus the map's author. The map name and author come from the server's welcome block and its `Playing <map> by <author>` line; the gamemode is the sidebar title, which PGM renders as the map's mode.

### Fixed
- **Team Bar clock colour** — the live/count-up clock is gold instead of green, matching the pre-match clock. A time limit stays yellow and turns red in its last 25 seconds.
- **Team Bar objectives on 3+ team maps** — each team's block is now followed by that team's own objectives, so on a four-team bar a score can never sit under another team's row. Teams with nobody online still print theirs below the stack.

## v0.6.1 - 2026-11-17

### Added
- **Team Bar head actions** — with chat open, click a player's head for View Profile (`https://oc.tc/PlayerName`, after a link-confirmation warning), Teleport to player (`/tp PlayerName`), Add Friend (`/friend add PlayerName`), or Message player, which opens that player's private chat tab. Menus follow the head, stay on screen, close when chat closes, and leave teleport/friend permissions to the server.
- **Team Bar symbolic scores** — read PGM's circle, square, heart and pipe score displays as live numeric team scores, including Tom Brady-style Touchdown maps. Match each row's team-coloured arrow to its actual team; unknown/ambiguous teams are not guessed. Works with control point tracking disabled too.
- **Two-row compact objectives** — one to three markers keep their existing layout; four or more form two balanced horizontal rows aligned vertically with the player's heads and clock.
- **Team Bar: Control point tracking** (ON by default) — neutral control points and their capture progress are centred below the clock. Captured points appear below their controlling team's side and stay there during enemy capture attempts until PGM changes the point label's owner colour. Supports multiple points, retains live text colours/icons, and can be disabled per profile.
- **Score display option** — a symbolic score can show as text (`2/5`), a horizontal bar, or a vertical bar. A bar fills with the team's colour toward the confirmed target and shows the number on hover; maps with no confirmed target keep the `2 pts` text.

### Fixed
- **Symbolic score targets** — show current/target when unfilled scoreboard slots confirm a target, retain that confirmed target through the final point, and clear it on a new match. Without a confirmed target, show points beside the normal clock instead of inventing a first-to score; supports zero-point time-based rows too.
- **Pre-match Team Bar clock** — gold while waiting for the match and during its start countdown; live-match timer colours are unchanged.
- **Hide arrow particles only worked on other players' arrows** — three separate gaps: the critical-hit flag was cleared at tick *end* (a tick after your own bow's sparkle had already been rendered), the particle filter was installed once and lost when Minecraft rebuilds its effect renderer on a world change, and server-broadcast sparkle packets were never filtered. The flag now clears at tick start, the filter reinstalls with each renderer, and matching crit-sparkle packets are dropped as well.
- **Owned control-point layout** — site names fill horizontally beneath their team's heads before wrapping onto another row; neutral-point height is unchanged.
- **Neutral point progress ordering** — the percentage leans toward the side of the team capturing the point (percentage first for a left-column team, name first for a right-column team), so the live progress sits on that team's own side of the clock.
- **Team Bar scoreboard text colours** — preserve colour changes inside score fractions, progress and objective names in both modes, including PGM's white current score, dark-grey slash and grey target score.
- **Custom Votebook missing player skins** — use player-list profiles for stats portraits when players are outside world-render distance. Keep previews while the GUI is open, allow pending skins/slim models to update, and match full account names instead of partial names. Loaded players still supply their available equipment.
- **Team Bar objective layout** — compact markers stay beside the clock; named objectives stay below their team name, mirrored as `Name [icon]` on the left and `[icon] Name` on the right.
- **Missing icons and cut-off names** — both modes keep the scoreboard's live state icon and colour. Non-compact names are kept in full and wrap when necessary instead of being shortened or dropped.
- **Part-destroyed monuments were dropped entirely** — PGM's touch glyph (`✳`) was not a recognised state, so a DTM monument someone had started breaking disappeared from the strip until it was finished. That state now shows like PGM's ✔/✕, and a destroyable's precise breaks (`55% 11/20`) stay with the state instead of being read as part of the monument's name.
- **Objective team grouping** — always follow the live scoreboard's team blocks, including multiple objectives and opposing-colour wool names. Objective names, colours and map XML no longer override the scoreboard grouping. Rows outside any team block are not guessed onto the left.
- **Objectives disappearing on PGM** — normalize formatting prefixes in live team display names before matching scoreboard headers, keeping every objective in the matching team block.
- **Normal Team Bar clock** — green instead of white; countdown and warning colours are unchanged.

## v0.6.0 - 2026-11-16

### Added
- **Team Bar objectives** (ON by default) - PGM's own sidebar objectives sit beside the clock, one line per sidebar row: Capture the Wool wools as coloured squares that change with their state (hollow untouched, half picked up, filled placed, each in the wool's colour), destroyables and other goals as their label and progress, and team scores as `12 Red` / `3/10 Blue`. Each team's objectives sit on that team's own side, to the left or the right of the time, in the same order the sidebar prints them. Everything is read from the sidebar PGM already draws, so a mode with no goals simply has no strip, and a line is capped at a third of the room beside the clock so it can never push the bar off screen. The clock stays on the exact centre.
- **Compact objectives** (OFF by default) - drop the objective names and keep only the markers, one line per side. Open chat and hover a marker to see which objective it is.
### Fixed
- **Objectives order** - a goal now reads `[icon] Name`, the same order PGM's own sidebar prints, so the icons always end up nearest the clock instead of trailing the name.
- **Objectives on the wrong side** - a team's goals printed above its name in the sidebar were left with no team and every one of them landed on the left side of the clock; each row now takes the nearest team name in either direction.

## v0.5.0 - 2026-11-16

### Added
- **Touchdown player card**: a short celebration card after each touchdown, with the scorer's and the confirmed passer's half-body portraits wearing their own armor, the team, the estimated pass distance and the score. Five animation styles, movable in the HUD editor. It only appears on confirmed PGM touchdown events and never invents a score or a pass distance.
- **Team Bar head names**: open chat and move the pointer over a head - it grows slightly and the player's name appears, clamped to the screen.
- **Team Bar: hide while the player list is open** (OFF by default) - the bar disappears only while you hold the player-list key, then returns.
- **Team Bar dead-head opacity** - dead players fade instead of vanishing, and now stay through long respawns; 25-100%, default 50%.
- **Minimap fades out unavailable maps** (OFF by default) - a map with no minimap no longer leaves an empty frame behind, and the minimap fades back in on the next map that has one.
- **Arrow trails** - a flying arrow draws the same fading contrail as the football, so a shot is easier to follow.
- **Arrow sparkles off** - the Bow Optimizer can hide the vanilla critical-hit sparkle an arrow leaves behind.
- **Football trail off** - Touchdown can hide PGM's redstone particle trail around a football you can see.

### Changed
- **New factory defaults**: Team Bar 10 heads at 150%, Kill Feed 96% with deaths hidden from chat, Kill Confirm without its background panel, Bow Optimizer on with the hit marker and the accuracy HUD off, Face Rig on, team bubbles on, unlimited chat history, and Auto GG on with a 5-second random delay. HUD defaults moved: the touchdown card to the right edge, region text to the right, bow accuracy to the left, and the Team Bar slightly below the top edge. Existing settings are untouched.
- **Bigger held football**: drawn at 0.85 of the item space instead of 0.7.
- **Removed the Height Limit Overlay and Server Notices modules**, along with their settings.

### Fixed
- **Player card lost the passer's armor** when that player was not loaded at the instant of the throw; the card now uses equipment it saw in the last ten seconds and shows no armor rather than guessing.
- **Winning touchdown showed no score**: PGM omits its usual score line on the winning point, so the card now reads the final score from PGM's symbolic sidebar, viewer's team first.
- **Normal snowballs** are no longer drawn at the football's scale; only actual Touchdown footballs change.

## v0.4.0 - 2026-11-15

### Added
- **Ball trail**: the thrown Football draws a fading white contrail behind it - one thin line, its newest end brightest, about a second and a half of flight - so a long pass is easy to follow across the map. A line strip drawn straight in the world, not particles. Toggle it under Touchdown → Ball trail.

### Fixed
- **The flag mostly stayed a banner**: PGM's touchdown flag is not a banner block - it is a floating banner **item** spinning at the post. Only banner blocks were converted, so on most maps the flag never turned into the football. The floating flag entity now becomes the ball, keeping the flag's own hover and spin, while a banner worn on the carrier's head stays a flag so you can always see who has it. No refresh is needed - the swap is live, per frame, and switches the moment PGM announces the map.

### Changed
- **Works on any PGM server**: the mod only switched on for a handful of known addresses (`play.oc.tc` and friends), so on any other server - a private test server included - every module stayed inert even though the server was running PGM. The mod now recognises PGM from its own chat lines (`The match has started!`, `Time Remaining: 5:00`, `Playing <map> by <author>`, `has ended the match.`) and switches on for that server, then forgets it again when you leave or join somewhere else. A server that is not PGM is still left alone.
- **Football looks like a football**: the ball is dark maroon leather instead of flat brown, with a white stripe near each tip and seven laces down the middle, and it is drawn at 0.7 of the item space instead of 1.25 - it was bigger than the block-sized slot it sat in, so it filled your hand and your inventory slot.

## v0.3.9 - 2026-11-14

### Changed
- **Team Bar layout**: teams are listed in a fixed order by team name, so a team keeps its column for the whole match and the bar never reshuffles when players join or leave. Left rows are flush against the clock and grow leftward, right rows are flush against the clock and grow rightward, so no empty space sits next to the clock. Ten heads fill a row and a bigger team wraps onto the rows below at full size instead of shrinking its heads.
- **Team Bar clock** is locked to one source for the whole match, so the boss bar, sidebar and tab footer can no longer fight over the display and make the number jump.

### Fixed
- **Touchdown flags sometimes stayed banners**: an older map lookup could overwrite the current map's detection. New map announcements now invalidate earlier lookups, and an unknown map can also be identified by a Touchdown gamemode in the sidebar.
- **Touchdown crashed when you took the flag**: a banner *item* (the flag on a carrier's head, or any banner in a slot) has no world, and the banner renderer called `getBlockType()` on it, which threw a `NullPointerException` every frame. Each throw also skipped the item renderer's matrix pop, so the GL matrix stack filled up and the client died with `1283: Stack overflow`. Banner items now go straight to the vanilla renderer.
- **Touchdown flags that were still banners**: PGM places a flag from a block face, so on some posts it is a wall banner rather than a standing one, and only standing banners were converted. Both kinds are now replaced.
- **Touchdown stayed on after its map ended**: once a Football had been seen, every later map kept drawing footballs, because nothing but a world reload cleared the flag. The Football signal is now re-read per match and a map we can name always overrides it, so a leftover Football in the inventory cannot make the next map a Touchdown map.
- **Touchdown only worked on some maps**: the map name was read only when the server's message began with `Playing`, but PGM wraps that line in a strikethrough rule and sends it inside the join welcome block, so on a real server it was never recognised and the flags stayed flags. The line is now found anywhere in the message, so every PGM server is detected.
- **Team Bar clock stays centred** when no teams exist yet or you are watching from the observer team. The bar was placed using the HUD editor's preview width (which invents placeholder heads), so a clock-only bar landed up to ~90px off centre. The clock also no longer disappears when no team is on the scoreboard.
- **Team Bar clock colours**: a time limit turns **red** in its last 25 seconds as well as at the count-in, and stays **yellow** above that.
- **Team Bar heads no longer flicker to Steve**: a head is only re-sampled when the player's skin actually changes, and the fallback skin is never cached.
- **Votebook `(You: 12)`**: PGM's value inside the tag now moves to the `(You)` line instead of being glued onto the end of your name as `: 12)`.
- **Votebook name colours**: a name that changes colour part-way (rank tag, then the name) now keeps every colour code instead of being drawn in one colour.
- **Reliable Chat scrollbar** is a slim rounded pill instead of a boxed bar with `^`/`v` arrows, and can be turned off under Reliable Chat → Appearance.
- Reliable Chat no longer leaves the history scrolled up: closing the chat returns it to the newest message, and the floating `New messages` indicator is gone.
- **Settings-screen scrollbar** can now be clicked and dragged; before it was drawn but ignored the mouse, so long pages could only be scrolled with the wheel.
- **Team Bar clock showed the wrong time**: the source lock trusted whichever clock line it saw first, even one that never counts. PGM's sidebar can carry the map's configured limit while the live time sits elsewhere, so the bar sat on a number that was simply not the match time. It now follows the source whose value actually moves, re-syncs within a couple of seconds of joining a match in progress, and keeps its lock while that source is still counting so two live sources cannot make the number jump.
- **Team Bar clock showed no time limit at all**: PGM sends its time limit as a **chat** message - `Time Remaining: 5:00`, once a minute - and puts it in no boss bar, sidebar or tab footer. Chat was not one of the bar's sources, so on those servers it never saw a clock and counted up from the kickoff, which is why a timed match looked untimed and the number sometimes jumped to a plain count. The chat line is now read like any other source, so the limit shows and keeps counting down between announcements. Sudden death (`Sudden Death Ends In: 4:00`) counts down too.
- **Team Bar in FFA**: FFA gives every player their own one-member team (and their own colour), which the bar treated as a team per player - a row block and a colour line each. Those sides are now recognised as FFA: the players are pooled into shared rows and no team-colour line is drawn, since the colour belongs to the player, not a side. The pool is dealt out alternately - left, right, left - so the two columns hold the same number of heads instead of ten on one side and two on the other.
- **Team Bar in FFA with a party**: a party keeps its players on one team, so not every side was a single and the whole FFA match fell back to team-map drawing - team-colour line included. FFA is now recognised from the match shape (mostly one-player sides) rather than from every side being one player.
- **Team Bar drew the same head on both sides**: a team packet that moves a player leaves a moment where two teams list them, and the dead-player memory put a second head for them on the old row. A player is now drawn on exactly one row.
- **Team Bar in the lobby**: the hub has a clock and scoreboard teams of its own, so the bar was drawn there too, with heads that mean nothing outside a match. It now waits for PGM's `Playing <map>` line and disappears again when that match ends.
- **Team Bar clock counted the wrong way on a time-limit map**: Overcast sends the live clock as a bare `Time: 4:32` with no "remaining" in it, so the bar guessed and counted **up** from the map's limit. The direction now comes from the label or from watching the number move, so a time limit counts down and a no-limit match counts up.
- **Team Bar clock kept running when its source went away**: PGM swapping the boss bar's text, or clearing the tab footer, used to leave the bar frozen or hand it to a line that never moves (the map's `Time Limit`). The clock now counts on from its last value and only switches to a source that is actually ticking.
- **Server timer follows the observer option**: with **Show in observer** off, the boss-bar clock is hidden too, so the server's own timer is not left on screen as the only clock.
- **Team Bar Size slider looked dead**: the bar was shrunk back to fit the screen whenever a row of ten came out too wide, so a bigger Size could draw *smaller* heads than a smaller one. Head size now comes only from the Size option, and a row that will not fit wraps onto another row instead - one to ten players a row always get exactly the size you picked.
### Added
- **Touchdown** (ON by default, Touchdown maps only): the snowball becomes a 3D American football - in the hotbar and inventory, in your hand, and while it is in the air, spinning about its long axis so the white laces flash. Every flag banner becomes the same football, floating above the post and bobbing as it turns. It works on any PGM server: the map name comes from PGM's own `Playing <map>` line, and on a server whose maps we do not have, the Football in your inventory or PGM's own "threw the Football" message turns it on. Nothing changes on any other gamemode.- **Auto GG options page**: **Say gg in observer** (on by default), a **Delay** slider from 0 to 5000 ms, and **Randomise delay**, which picks any wait up to that value so every client on the server does not say gg on the same tick. A new match cancels a gg that is still waiting.
- **Your settings can no longer be lost on the first run after updating**: the old `config/overcast_util` folder was deleted before the copy was moved into place, so a failed move left no complete copy. The copy is moved first now, and only then is the old folder removed.
- **Team Bar heads draw in one pass** instead of 64 rectangles per head, and the dead/grey state is cached for a quarter second. A full match no longer spends thousands of draw calls per frame on the bar.
- **Map previews no longer keep every decoded image** for the session; the cache is capped and evicts the oldest.
- **Map lookups no longer wait for the gallery**: a catalog refresh used to hold a lock across two GitHub downloads, so `/map` and the minimap could freeze for up to half a minute behind it.
- **Heads refresh after a resource-pack change** (`F3+T`), instead of keeping the old pack's faces until you rejoin.
- The HUD editor no longer lists the disabled Server Notices element.

## v0.3.8 - 2026-11-14

### Added
- **Team Bar** (ON by default): one row of player heads per team at the top centre, stacked, with the match clock locked to the exact middle.
  - Left-column teams read left-to-right, right-column teams right-to-left, so every row hugs the bar's outer edge; your own team always takes the left column and your own head always leads it.
  - The clock reads PGM's live time (boss bar, then sidebar, then tab footer), ticks locally between server updates, turns **yellow** for a time limit and **white** for a count-up, shows the match-start countdown in **red**, freezes at match end and resumes next match. A map change resets it to `0:00`.
  - Dead players' heads fade to grey and clear on respawn; a head never disappears on death (a player who drops out of the team for the respawn delay is held for 10 seconds).
  - Players who leave, move to the observers or change team are moved or removed instead of being left behind.
  - Heads only appear in a real match, never in the hub.
  - Options: **Hide bossbar**, **Show in observer**, **Team colour line**, **Players shown** (1–75, default 5) and **Size** (50–150%).
- **Team colour line**: a thin vertical bar in the team's own PGM colour beside each team row, on the bar's outer edge, one per team. Toggle in Team Bar settings.
- **Custom Votebook**: replaces the written book with a compact panel — map cards with previews and vote counts, Supervote, a Match Stats row of player heads, a match link and map favorites.
- **spectrum.oc.tc** is recognised as an Overcast host.

### Changed
- **Team Bar stacks any number of teams** instead of two: rows alternate left/right, grow downward, and the heads shrink until the whole stack fits the screen. Stacked rows are separated by a small gap so two teams never read as one block.
- **Settings screens**: the reset button now sits in the module body (top right) instead of the screen header, and every screen fits one compact 470×272 panel at every GUI scale.

### Fixed
- **`(You)` in the votebook** now appears on its own last line under `Most X: N` / `and N more...`, and is drawn for your own row in every column.
- Votebook stat rows keep your whole chat-styled name (rank symbol and its own colours) instead of flattening or clipping it.
- PGM's own `(You)` tag inside a stats name is stripped instead of being cut mid-word, and no longer stops the row being recognised as yours.
- **Chat HUD crash on team join**: team data is snapshotted before use and the overlay draw is guarded, so a team packet arriving mid-frame can no longer take the game down.
- Auto GG says `gg` once per match, at the end only, never on map start.

### Removed
- **Chat background** slider under Reliable Chat. Chat lines no longer draw a background box; the tab opacity sliders are unchanged.

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
