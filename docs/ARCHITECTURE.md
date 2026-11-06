# OTL — Architecture (MVP)

One Forge 1.8.9 mod, many modules, one server family (`play.oc.tc`, `vanish.oc.tc`).

## 1. Constraints that drive every decision

| Constraint | Consequence |
|---|---|
| Client is Lunar Client (obfuscated) or plain Forge | No reflection on MCP/SRG field names. Scan by **type/shape**, never by name. `MinecraftAccess` wraps the singleton lookup. |
| Server has no API, no plugin of ours, no chat backend | Gameplay uses client-visible state. Optional map lookups use GitHub GETs; no accounts. |
| Users install one JAR | Modules ship together with one entry point. Module preferences and face settings/marks are stored separately. |
| 1.8.9 + Java 8 | No streams-heavy code, no `java.time`, no newer Guava. |
| Feature must never break the game | Every module is a clearly gated `if (!enabled) return;`, and toggling is safe at any moment. |

## 2. Layering

```
              ┌──────────────────────────── UI ────────────────────────────┐
              │ UtilityScreen (module list) · per-module screens · HUD      │
              └──────────────────────────────▲──────────────────────────────┘
                                             │ reads flags
              ┌────────────────────────── Modules ───────────────────────────┐
              │ Face Rig (FaceOverlay, EyeDetector, SkinFace)                │
              │ Reliable Chat (ReliableChat, ChatRules, TeamBubbles)         │
              │ Map Preview (MapPreview, MapPreviewIndex, MapInfo card)
              └──────────────────────────────▲──────────────────────────────┘
                                             │ OtlModule contract
              ┌───────────────────────────── Core ───────────────────────────┐
              │ OtlModule.ALL registry · UtilitySettings (persistence)      │
              └──────────────────────────────▲──────────────────────────────┘
                                             │
              ┌─────────────────────────── Platform ─────────────────────────┐
              │ Otl (mod entry, keybinds, event bus)                         │
              │ MinecraftAccess · ServerScope · IRON resource reload         │
              └──────────────────────────────────────────────────────────────┘
```

Rule: dependencies point **upward only**. A module may use Core and Platform; Core never imports a module; Platform never imports a screen.

## 3. Module contract (`OtlModule`)

A module is 4 things: an id, a display name, an enabled flag, and an optional options screen.

```java
new OtlModule("reliable_chat", "Reliable Chat") {
    boolean enabled()           { return UtilitySettings.chat; }
    void setEnabled(boolean on) { UtilitySettings.chat = on; ReliableChat.applyPreferences(); }
    GuiScreen settings(GuiScreen back) { return new UtilityScreen(true); }
}
```

Guarantees the rest of the app relies on:

- `enabled()` is read **at the point of use**, not cached at startup → a toggle takes effect instantly.
- `setEnabled()` mutates memory only; the caller saves once. A half-applied config can't exist.
- No `onEnable`/`onDisable` lifecycle to get wrong. Modules that hold state (chat history) keep it and just stop rendering/sending.
- Toggling must not leak GL state or leave listeners registered — enforced by review, and by the smoke run.

Adding a module = one entry in `OtlModule.ALL` + (optionally) its own screen. The module list UI, the toggle wiring, and the JSON round-trip already exist.

## 4. Persistence ("database schema")

There is no database, and adding one would be wrong: this is a single-process desktop client whose entire persistent state is user preferences. A SQL store would duplicate ~20 booleans behind a driver, a migration tool and a corruption mode. The document below is the store; it is versioned so a real migration is possible later.

**Path:** `%APPDATA%/.minecraft/config/overcast_util/settings.json`
**Engine:** Gson, pretty-printed UTF-8. Save to a unique temporary file, then replace the previous file (atomic replacement where supported). The same writer protects face settings, painted marks, and remote map caches.
**Never stored:** chat messages, player names, drafts, server addresses, any image data.

```jsonc
{
  "schema_version": 1,          // int, written by current builds, unknown keys ignored

  "face_rig": true,             // bool, module: Face Rig
  "other_faces": true,          // bool, render rigs for other players too

  "reliable_chat": true,        // bool, module: Reliable Chat
  "unread_counts": true,        // bool, per-view badges
  "show_tab_chat": true,        // bool, HUD tab strip
  "tab_chat_position": 0,       // 0..3  bottom-left, bottom-right, top-left, top-right
  "chat_background_opacity": 0, // 0..100 percent
  "remember_drafts": false,     // bool, session draft retention
  "unlimited_history": false,   // bool, otherwise 100 messages per view
  "shift_tab": true,            // bool, shift+tab cycles views

  "team_bubbles": false,        // bool, speech bubbles over teammates
  "bubble_characters": 80,      // 20..200
  "bubble_seconds": 4,          // 1..15

  "dev_hosts": [],              // extra hosts where modules also run (local test server)

  "map_preview": true,          // bool, module: Map Preview (book hover + gallery)
  "preview_on_command": true,   // bool, independently intercept /map when Map Preview is enabled

  "selected_channels": {        // view id -> channel key, validated against ChatRules on load
    "ALL": "all",
    "TEAM": "team",
    "PARTY": "party"
  }
}
```

Invariants enforced on load, not trusted from disk: every int is clamped to its range, every `selected_channels` value must resolve through `ChatRules.channel(...)` or it is dropped, and a malformed file is logged and replaced by defaults rather than aborting startup.

`schema_version` is written but not yet branched on. When a breaking change lands: `if (v < 2) migrate(j);`. Nothing else is needed.

If a hosted backend ever exists (leaderboards, cross-device settings), that is when a real DB appears — Postgres, one `player_settings(player_uuid, json, updated_at)` row plus per-feature tables. Not before.

## 5. UI architecture

**Server scoping.** `ServerScope.host()` normalizes the address and returns a non-empty string only for approved hosts. Every gameplay effect is gated on `ServerScope.active()`; screens stay open off-server so users can configure while in a lobby. `dev_hosts` in the settings allows a local server for testing without changing code (`docs/LOCAL_TESTING.md`).

**Screen tree**

```
Escape ─┬─ FaceScreen            (J)   face rig sliders/toggles
        └─ UtilityScreen         (P)
              ├─ module row  → toggle, persisted immediately
              ├─ Settings    → module.settings(this)
              │     ├─ FaceScreen        (back returns here)
              │     ├─ UtilityScreen(true): General │ Team bubbles │ Appearance
              │     └─ MapPreviewSettings → MapGalleryScreen
              └─ Done
```

Module rows are generated from `OtlModule.ALL`, ids `20+i` (toggle) and `40+i` (settings). Four rows fit above the Done button; beyond that, add paging. The map gallery calculates its grid and page capacity from the current screen size; drawing and hit-testing share that grid.

**HUD / render layers** (registered once in `Otl.init`, gated inside):

- chat views + tab strip: `ReliableChat` (render + tick + input events)
- speech bubbles: `TeamBubbles`
- face rig: `FaceOverlay`, one per `RenderPlayer`

Input is centralized in `Otl`: `J` menu, `P` modules, `Y` team chat, vanilla `T` all chat, configurable party key. Keybinds are Forge `KeyBinding`s so users can rebind them.

**Lunar safety pattern.** When the vanilla field name is not trustworthy (`GuiScreenBook` pages on Lunar), find the field by **type**: walk `GuiScreen` fields, take the `List` whose first element is an `IChatComponent`. This survives obfuscation because runtime types can't be renamed.

**`/map` map card.** A Forge client command handles `/map <name>` from normal chat, Reliable Chat and clickable commands. It queues the card for the next client tick because vanilla chat closes itself after sending; the keyboard hook is only a fallback. Commands still reach the server: its repository/path response corrects aliases and ambiguous names while the requested card remains open. Bare `/map` uses that response to identify the current map. Disabled/off-server commands are forwarded unchanged. Closing/navigating away cancels late GUI updates. Gallery clicks pass the exact repository/path, not a second ambiguous name search.

- Resolution order: bundled `map.xml` → PublicMaps (`main`) and CommunityMaps (`master`), with exact remote matches before partial matches.
- A bounded single worker performs remote work. Downloads live under `config/overcast_util/map_cache/<repo>/<branch>/<map>/`, with separate 6h indexes. Closing a screen cannot let a late response replace another screen.
- Downloads have byte limits, preview dimensions are bounded, and cached paths reject traversal. No query text or player data is sent.
- Shared square textures preserve the full image with padding. They are reused across books, cards, galleries and reconnects instead of losing references to textures still owned by Minecraft.
- Metadata scrolls beside the square image; footer controls remain outside the scroll area.
- Metadata is parsed with Java's XML reader with external entities/DTDs disabled. Only the default variant is shown; active shared includes and constants supply objectives, teams and player limits. Shared include files are cached from PublicMaps, and unavailable fields are labelled explicitly.
- The gallery merges bundled previews with both six-hour repository indexes, keeping repository identity even for maps with the same folder name. Remote images load on demand; maps without bundled images are no longer omitted. Repository outages are shown in the gallery header.

## 6. File structure

```
otl/                         (repo root: Over The Limit)
├─ build.gradle              ForgeGradle 2.1, MC 1.8.9-11.15.1, mappings stable_22
├─ docs/ARCHITECTURE.md      this file
└─ src/
   ├─ main/java/otl/client/
   │  ├─ Otl.java            mod entry: keybinds, event bus, render layers, resets
   │  ├─ OtlModule.java      ★ module registry + contract
   │  ├─ UtilitySettings.java  JSON persistence, clamping, defaults
   │  ├─ UtilityScreen.java    module list + Reliable Chat settings
   │  ├─ MinecraftAccess.java  Lunar-safe Minecraft singleton
   │  ├─ ServerScope.java      approved-host gate
   │  ├─ FaceScreen / FaceConfig / FacePose / FaceUi / FaceDoll / FaceMarks / EyeDetector / SkinFace
   │  ├─ FaceOverlay.java      RenderPlayer layer
   │  ├─ ReliableChat / ReliableChatScreen / ChatRules / ChatAppearance / ChatWarnings / ChatWelcomeGate
   │  ├─ TeamBubbles / TeamBubbleRules
   │  ├─ MapPreview / MapPreviewSettings / MapGalleryScreen / MapPreviewIndex (generated)
   │  ├─ MapInfo / MapRemote / MapInfoScreen   (/map card + public repo fallback)
   │  ├─ PlayerSignals / CompactScreen / NeutralButton
   │  └─ (ui kit: CompactScreen, NeutralButton shared by every screen)
   ├─ main/resources/          mcmod.info, assets (fonts, map data)
   └─ otlSmoke/java/...        automated in-client smoke harness (`-PotlSmoke`)
```

## 7. Adding a module (checklist)

1. `OtlModule.ALL` → one entry: id, name, `enabled`/`setEnabled` over the settings field.
2. `UtilitySettings` → field + one load line + one save line (clamped).
3. Gate the feature at its use-site on `ServerScope.active() && <flag>`.
4. Register any listeners/layers in `Otl.init` (unconditional register, conditional body).
5. Optional: `settings(back)` screen using `CompactScreen`/`NeutralButton`.
6. Smoke: toggle on/off, rejoin a world, confirm no stray listeners or GL state.

## 8. Build and deploy

```
gradlew.bat clean build  # tests + reobfuscation + verified build/libs/OTL.jar for manual copying
gradlew.bat releaseJar   # tests + reobfuscation + verification, without a clean
gradlew.bat runClient -PotlSmoke   # in-client smoke harness
```

**No automatic deployment.** `jar` and `runClient` use `build/dev-libs/`. `releaseJar` depends on unit tests and `reobfJar`, rejects development names and any `OtlSmoke` classes, then writes only `build/libs/OTL.jar`. Users copy that verified release themselves. Running `runClient` later cannot overwrite it. A release requested with `-PotlSmoke` fails verification.

`getSkinMap()` stays unrenamed on purpose: it is a Forge-added method (`RenderManager` in forgeSrc declares it), so it resolves at runtime. Only vanilla members need SRG names.

## 9. Known debt and the next scale step

- Module list is a static `Arrays.asList` — compile-time only, no dynamic loading. Fine while everything ships in one JAR; a service loader only pays off when modules become separate artifacts.
- Public-repo lookup uses unauthenticated GitHub API (60 requests/hour) and slug matching; a miss offers the gallery. A repository outage is logged, and the other repository is still searched.
- 4 visible module rows, no scrolling.
- No per-server or per-profile config sets.
- Map preview assets: 880 PNG previews + XML (1,661 files, ~84 MB, ~97% of the JAR) belong in a separate optional resource pack if size becomes a complaint. `MapPreviewIndex` is generated from that folder and must be regenerated whenever assets change.
