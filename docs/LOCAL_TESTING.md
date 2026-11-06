# Local multiplayer testing

A real 1.8.9 server beats faked packets for anything involving teams, scoreboard, scoreboard teams, join/leave and rendering. The setup is small and all public.

## Stack

| Piece | Where |
|---|---|
| `SportPaper.jar` (1.8.9 server) + `PGM.jar` | https://github.com/PGMDev/PGM/releases/tag/v0.15 |
| Maps | any folder from `OvercastCommunity/PublicMaps` (`map.xml` + `region/` + `map.png`) |
| Java | 8 (SportPaper 1.8.9 target). On this machine: `%TEMP%\opencode\jdk8\zulu8.98.0.107-ca-jdk8.0.512-win_x64` |

## Windows setup (~5 minutes)

```
C:\otl-server\
  SportPaper.jar
  start.bat
  plugins\PGM.jar
  local-maps\<gamemode>\<map>\   (copy map folders here)
```

`start.bat`:

```bat
java -Xmx2G -Dterminal.jline=false -jar SportPaper.jar nogui
PAUSE
```

First run creates the files; then set `eula.txt` → `eula=true` and `server.properties` → `online-mode=false`. Point PGM at the folder in `plugins/PGM/config.yml`:

```yaml
map:
  folders:
    - "local-maps"
```

Start it, wait for `Done`, connect to `localhost` from 1.8.9.

## Let the mod run there

The mod is scoped to `play.oc.tc` / `vanish.oc.tc`. Add a host to the allowlist in `config/overcast_util/settings.json`:

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

`.\gradlew.bat runClient -PotlSmoke --offline` — deterministic, covers the book hover and the card with fixtures. The remote lookup line prints `skipped` when offline; it never fails the run.
