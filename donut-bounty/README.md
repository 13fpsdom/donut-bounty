# DonutSMP Bounty Tracker (Fabric, client-side)

A client-side Fabric mod for **Minecraft Java Edition 1.21.1**. Pick any block, run a countdown, and rank the players who pay you while the bounty is running.

**Read-only by design.** The mod only reads incoming messages. It never sends chat messages, runs commands, makes payments, uses macros or bots, touches packets, or automates anything. Check DonutSMP's current rules yourself before using any client mod there; this mod does not know or enforce them.

---

## Status: what has and has not been verified

Read this before you rely on the mod.

| Part | Status |
|---|---|
| Payment parser, duplicate filter, leaderboard, CSV export, timer, config, saving/loading (everything except the Minecraft GUI code) | **Compiled with `javac` 21 and tested: 69 JUnit 4 tests pass.** Run directly with `javac` + JUnit, not through Gradle. I also confirmed the tests fail when I deliberately break the parser, the duplicate filter and the leaderboard totals. |
| Gradle / Fabric Loom build, producing the `.jar` | **Not run.** The environment this was written in could not reach Mojang's or Fabric's servers. |
| GUI, HUD, keybind and chat-listener code (`client/` package) | **Not compiled against real Minecraft 1.21.1.** It compiles against my own stand-ins for the Minecraft classes, which only proves it is internally consistent. Method names or signatures may differ in the real 1.21.1 API, so the first real build may report a few compile errors. |
| Behaviour in a real game, and on DonutSMP | **Not tested.** |
| The exact DonutSMP payment message | **Not verified.** See "Limitations". |

If the build prints compile errors, send them to me and I will fix them.

---

## Controls

* **H** (change it in *Options > Controls > Key Binds > DonutSMP Bounty Tracker*) opens the main screen.
* **Block list:** search by name or id (`oak plank`, `deepslate`, `minecraft:beacon`), click to select. Blocks that have no item form (water, fire, etc.) show no icon.
* **Duration:** `30` means 30 minutes. Also `45m`, `90s`, `1h30m`, `1:30:00`, `7d` (max 7 days).
* **Target amount (optional):** `5000`, `$250,000`, `2.5m`, `1.2b` (suffixes K, M, B, T).
* **Start / Pause / Resume / Stop.** Payments are counted **only while the bounty is running** (switchable). Paused or ended means ignored.
* **Leaderboard:** ranked by total confirmed payments to you. Shows username, total paid, number of payments and most recent payment. **Export CSV**, **Add manually**, and **Clear data** (asks for confirmation first).
* **HUD settings:** on/off, scale, X/Y position, six colours (`#RRGGBB` or `#AARRGGBB`) with a live preview.

The HUD appears at the top centre while a bounty is running, paused or just ended: block icon and name, countdown, raised / target with a progress bar, and a time-remaining bar.

---

## How a payment is counted (and how fakes are rejected)

A message counts only if **all** of these are true:

1. It arrives as a **system/game message** (where server plugins print notices), not as signed player chat. The player-chat channel is ignored unless you turn on `acceptChatChannel`, and even then only for messages with no sender profile.
2. You are connected to a server whose address contains one of `serverHosts` (default `donutsmp`). Switch off with `requireServerMatch` (for example to test in singleplayer).
3. The **entire** message matches a configured pattern. Extra text before or after (a rank tag, `Name:`, `<Name>`, a trailing sentence) makes it fail, so a player typing `Steve paid you $1m` in chat shows up as `[Rank] Name: Steve paid you $1m` and is rejected.
4. The message is a single line with no control characters, and is at most 256 characters.
5. The payer looks like a Minecraft username (`A-Z a-z 0-9 _`, 1-16 characters, optional leading `.` for Bedrock players).
6. The amount is a valid positive number no larger than `maxSingleAmountDollars`.
7. The payer is not you.
8. A bounty is running (unless `countOnlyWhileRunning` is off).
9. It is not a **duplicate**: an identical line within `dedupeWindowMillis` (default 1000 ms) of one already counted is dropped.

Legacy colour codes (`§a`) are stripped before matching.

### Supported message formats (configurable)

Patterns live in `config/donutbounty/config.json` under `payments.patterns`. Each has a `name`, a Java `regex` and `enabled`. The regex needs two named groups, `(?<player>...)` and `(?<amount>...)`, and is matched against the whole line.

| Name | Enabled | Matches | Source of the format |
|---|---|---|---|
| `paid_you` | yes | `Steve paid you $1,500`, `Alex paid you $2.5m`, `Bob paid you $3K.` | Based on how third-party DonutSMP mod pages describe the message (`<Player> paid you $<amount>`, e.g. `$2.5m`). **Not confirmed against the live server.** |
| `received_from_UNVERIFIED` | no | `You received $5 from Steve` | A guess, kept as an example template only. |

Amount text accepted: digits with optional thousands commas, optional decimals, optional `$`, optional suffix `K`/`M`/`B`/`T` (case-insensitive). Malformed numbers such as `1,2,3`, `0`, `-5`, `5x` are rejected.

Default regex for `paid_you`:

```
^(?<player>\.?[A-Za-z0-9_]{1,16}) paid you \$(?<amount>[0-9][0-9,]*(?:\.[0-9]+)?[KkMmBbTt]?)[.!]?$
```

To adapt it: open **Add manually**, which lists the recent payment-related messages the mod actually saw and what it decided about each. Compare with your pattern, edit `config.json`, then press **Reload config file** in HUD settings. Invalid patterns are reported on screen, never silently used.

---

## Limitations (please read)

* **The real DonutSMP message may differ from the default pattern.** If the server prints something else, nothing is counted until you adjust the pattern. Use **Add manually** meanwhile.
* **The server might not expose payments reliably to the client.** If the notice is not sent as a system message, or is sent with extra formatting, or not at all for some payment types, the mod cannot see it. The mod has no access to the server's records and does not try to get any. Nothing here is cryptographically verified: a message is "genuine" only in the sense that it arrived through the system channel in exactly the expected form.
* Money is stored as whole cents, so totals do not drift. If the server rounds large `K/M/B` display amounts (for example showing `$2.5m` for `$2,499,871`), the mod can only count what is displayed.
* Duplicate protection can wrongly drop two genuinely separate, identical payments that arrive within the 1 s window. Set `dedupeWindowMillis` to `0` to disable it.
* The mod only works while you are logged in; payments received while offline are not seen.
* Only Minecraft **1.21.1** is targeted. Other versions need code changes, not just a version bump.

### Manual confirmation

If something was not detected, open Leaderboard > **Add manually**, enter the username and amount. Manual entries are trusted (so double-check them), are saved like any other, and count towards the HUD total if a bounty is running or paused.

---

## Build

You need **JDK 21** (for example Temurin 21) and an internet connection (Gradle downloads Minecraft, Fabric and libraries).

**Windows (PowerShell or cmd), in this folder:**

```
gradlew.bat build
```

**macOS / Linux:**

```
chmod +x gradlew
./gradlew build
```

The jar to install is:

```
build/libs/donut-bounty-1.0.0.jar
```

(`build/libs/donut-bounty-1.0.0-sources.jar` is only the source code; do not put it in `mods`.)

Run just the tests:

```
./gradlew test          # Windows: gradlew.bat test
```

Test reports appear in `build/reports/tests/test/index.html`.

No local JDK or Gradle? Put this folder in a GitHub repository, open the **Actions** tab, run the **build** workflow, and download the `donut-bounty-jar` artifact.

## Install

1. Install the [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1.
2. Put **Fabric API** (version `0.105.0+1.21.1` or any newer 1.21.1 build) and `donut-bounty-1.0.0.jar` into your `.minecraft/mods` folder.
3. Launch the game and press **H**.

If Gradle cannot find `fabric-api:0.105.0+1.21.1`, look up the current 1.21.1 build on Modrinth or fabricmc.net and put it in `gradle.properties` as `fabric_version`.

## Files the mod writes

All in `.minecraft/config/donutbounty/`:

| File | Contents |
|---|---|
| `config.json` | HUD, payment patterns, switches, last-used inputs |
| `leaderboard.tsv` | Saved leaderboard (survives restarts; written atomically) |
| `exports/leaderboard-YYYYMMDD-HHMMSS.csv` | CSV exports: `rank,username,total_paid,payments,last_payment_utc,last_payment_amount` |

If `config.json` or `leaderboard.tsv` is unreadable, the mod keeps a copy (`*.broken-<time>` / `*.corrupt-<time>`) and starts fresh instead of overwriting it.

### Config reference

| Key | Default | Meaning |
|---|---|---|
| `hud.enabled` | `true` | Show the HUD |
| `hud.offsetX` / `hud.offsetY` | `0` / `6` | Shift from top centre, in GUI pixels |
| `hud.scale` | `1.0` | 0.5 to 3.0 |
| `hud.background/border/text/accent/warning/track` | see file | `#RRGGBB` or `#AARRGGBB` |
| `payments.patterns` | see above | Message formats |
| `payments.countOnlyWhileRunning` | `true` | Ignore payments unless the bounty is running |
| `payments.requireServerMatch` | `true` | Only listen on servers whose address contains a `serverHosts` entry |
| `payments.serverHosts` | `["donutsmp"]` | Address fragments to match |
| `payments.dedupeWindowMillis` | `1000` | Duplicate window; `0` disables (restart needed) |
| `payments.maxSingleAmountDollars` | `1000000000000` | Reject implausibly large amounts (restart needed) |
| `payments.acceptChatChannel` | `false` | Also listen on the player-chat channel (unsigned, no-sender messages only) |

## Troubleshooting

* **Nothing is counted:** Is a bounty *running* (HUD shows a timer)? Are you on a server matching `serverHosts`? Open **Add manually** and read the "recent messages" list: it shows what the mod saw and why it ignored it.
* **Messages appear in chat but not in that list:** the server probably delivers them through the chat channel; try `"acceptChatChannel": true`.
* **Keybind does nothing:** another mod may use H; rebind it in Controls.

## Source layout

```
src/main/java/com/example/donutbounty/
  core/      Minecraft-free logic (parser, duplicate filter, leaderboard, store, CSV, timer, money, colours)
  ModConfig, BountyService      config + orchestration (also Minecraft-free)
  client/    Fabric entrypoint, HUD, screens, block catalogue (needs Minecraft to compile)
src/test/java/...               69 JUnit 4 tests for the Minecraft-free code
```
