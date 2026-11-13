# Local multiplayer testing

A real 1.8.9 server beats faked packets for anything involving teams, scoreboard, scoreboard teams, join/leave and rendering. The setup is small and all public.

## Stack

| Piece | Where |
|---|---|
| `SportPaper.jar` (1.8.9 server) + `PGM.jar` | https://dl.pgm.fyi/download/pgm and `/sportpaper` (latest: PGM `0.16-SNAPSHOT`) |
| Maps | `OvercastCommunity/PublicMaps` (`main`) and `CommunityMaps` (`master`), copied wholesale |
| Java | 25 for PGM 0.16 (it is compiled for class 69). On this machine: `%LOCALAPPDATA%\jdks\jdk-25.0.5+8` |

## Windows setup (~5 minutes)

```
C:\otl-server\
  SportPaper.jar
  start.bat
  plugins\PGM.jar
  refresh-pool.ps1
  public-maps\      community-maps\      default-maps\      local-maps\
```

`start.bat`:

```bat
set JAVA_HOME=%LOCALAPPDATA%\jdks\jdk-25.0.5+8
java -Xmx3G -Dterminal.jline=false -jar SportPaper.jar nogui
PAUSE
```

First run creates the files; then set `eula.txt` → `eula=true`. For offline local testing only, set `server-ip=127.0.0.1` before setting `online-mode=false` in `server.properties`; do not expose an offline-mode server to other machines. Point PGM at every map folder in `plugins/PGM/config.yml` (recursive, so gamemode subfolders are enough):

```yaml
map:
  folders:
    - "public-maps"
    - "community-maps"
    - "default-maps"
    - "local-maps"
  experiments:
    payload: true
```

Then rebuild the pool so it only names maps PGM actually loaded — a pool entry for a rejected map stops the pool from enabling:

```
powershell -ExecutionPolicy Bypass -File refresh-pool.ps1
```

The script skips maps PGM rejected in the last boot, maps declaring `min-server-version="1.21"`, and decodes UTF-8 names and XML entities. Start it, wait for `Done`, connect to `localhost` from 1.8.9.

Keep `empty-server-suspend: P1D` in `sportpaper.yml`: with the 1-minute default the listener closes while you edit code, and the next client connect fails with no error.

## Let the mod run there

The mod is scoped to `play.oc.tc`, `vanish.oc.tc`, and `172.65.223.142`. Add a test host to the allowlist in `config/Over The Limit/settings.json`:

```json
"dev_hosts": ["localhost", "127.0.0.1"]
```

No UI for this on purpose — it is a test-only escape hatch, and the modules screen shows which host is active.

## What a localhost server actually proves

- scope gating on a non-oc.tc host (the `dev_hosts` path itself)
- chat routing `/t`, `/g`, `/party`, tab strip, unread counts
- team detection from the scoreboard, speech bubbles
- face rig rendering with second client or a bot
- join → leave → rejoin cleanup (sessions cleared, no leaked GL state)
- `/map <name>` end-to-end through the real chat screen: PGM has no map book, so this exercises the interception and the GitHub path for real

## What it cannot prove

- **OCC's vote book / hover preview.** That book comes from OCC's own plugin, which is not in the public PGM release. The smoke harness feeds a real `GuiScreenBook` fixture instead.
- Card fields that only exist server-side (tags, pools).

## Regression run

Left Hand checks toggle and reload its preference, capture right/left sword and empty-hand frames (`otl-hand-*.png`), verify off-server/disabled behavior, and assert projection/front-face restoration after an intentional rendering exception. These are plain Forge checks; Lunar's custom renderer still requires manual verification.

`.\gradlew.bat runClient -PotlSmoke --offline` — deterministic fixtures for chat, book hover, gallery pagination/search/click/back, square previews, separate command-toggle persistence and scope, and PM reopening/retention.

Add `-PotlSmokeRemote` to require live PublicMaps (`main`) and CommunityMaps (`master`) lookups. An enabled remote check fails on errors or timeout. Without the flag, remote checks are explicitly DISABLED.

Add `-PotlSmokeServer` to also connect the development client to `localhost:25565`, check the separate command toggle and map card on the live connection, and save `run/screenshots/otl-localhost-map.png`. This does not change the server configuration or install a jar in any client.

Command checks send Enter through both Reliable Chat and vanilla chat, then check the screen on a later tick. They do not call an open-card helper. The local-server check also exercises bare `/map` and its server response. Remote checks require both gallery catalogs and verify Infection: Cataclysm's default Infection objective, shared teams and 40-player capacity rather than its inactive OITQ variant.

Use `-PotlSmokeServerOnly` instead of `-PotlSmokeServer` for direct localhost checks without loading/unloading the integrated fixture world. With `-PotlSmokeRemote`, this checks the exact `/map 100┈Rooms` input and captures a Public-map vote-book fixture hover in `run/screenshots/otl-public-book-hover.png`. Integrated fixtures are explicitly skipped in this mode; run them separately for full coverage.

`-PotlSmokeScope=live` joins the running match on `localhost:25565`, dumps the real sidebar, boss bar, scoreboard teams and team-bar sides twice, saves `run/screenshots/otl-team-bar-live.png`, then asserts that the boss-bar clock PGM sends (`Time Remaining: m:ss`) is the one Team Bar shows, and leaves. It needs the local server up; run `-PotlSmokeScope=teambar` for the fixture version.

The direct checks also hover a gold `/map` link through real chat hit testing in both Reliable Chat and vanilla chat, saving `otl-gold-chat-hover.png` and `otl-gold-vanilla-hover.png`. Unit tests reject plain gold text, non-map commands, bare `/map`, and non-gold links, and cover inherited styles and Unicode separators.

After smoke testing, run `.\gradlew.bat clean build --offline --no-daemon`. Only `build/libs/OTL.jar` is the verified manual-copy release; never install the jar in `build/dev-libs/`. Build tasks do not copy into any client mods folder.
