# MobilePulse

**Real-time Android system monitor and optimizer** — tracks CPU, RAM, battery, and running apps live; enforces automation rules; provides AI-powered diagnostics; and includes an embedded shell terminal. Works on stock Android, Shizuku, and rooted devices.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Min SDK](https://img.shields.io/badge/minSdk-24-orange)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Features at a glance

| | |
|---|---|
| **Live monitoring** | CPU %, per-core bars, RAM, battery, temperature — updated every 2 seconds |
| **Enforcement tiers** | Standard (no root) → Shizuku (ADB-level) → Root — same app, different power |
| **AI assistant** | Claude or DeepSeek chat; errors show an "Ask AI to fix this" button |
| **Terminal** | Embedded shell with command suggestions, tier-aware (root / adb / restricted) |
| **Automation rules** | Trigger actions when metrics cross thresholds; whitelist protects critical apps |
| **Hourly auto-clean** | WorkManager job runs when you're idle; freezes monitoring tick while cleaning |
| **Activity log** | Every action timestamped in Room DB; ERROR entries show AI fix or full details |
| **Boot Boost** | Blocks known battery-drainers from auto-starting (Shizuku / Root only) |
| **Themes** | Forest · Light · Dark · System — persisted across launches |

---

## Screenshots

> Dashboard · Apps · RAM Monitor · AI Chat · Terminal · Logs · Settings

*(screenshots coming soon)*

---

## Enforcement Tiers

The tier you choose in Settings controls what every feature in the app is allowed to do.

| Tier | Requirement | What it unlocks |
|------|-------------|-----------------|
| **Standard** | Nothing — works out of the box | Soft process trimming, own-app cache clear, notifications |
| **Shizuku** | [Shizuku app](https://shizuku.rikka.app/) running | `am force-stop`, `pm trim-caches`, full per-app cache clear, ADB shell |
| **Root** | Magisk or any SU provider | Everything above + filesystem access, system cache wipe, root shell |

### Setting up Shizuku (no root required)

1. Install **Shizuku** from the Play Store.
2. Enable **Developer Options** (tap Build Number 7 times in About Phone).
3. In Shizuku, start the service via **Wireless Debugging** or USB ADB:
   ```
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
4. Open MobilePulse → Settings → Enforcement Tier → tap **Shizuku** → grant the prompt.

---

## Screens

### Dashboard

The home screen. Live metrics from the background service, refreshed every 2 seconds.

- **Status card** — active tier badge; Usage Access warning with one-tap Fix button
- **Alert banners** — animated cards when CPU / RAM / battery cross your thresholds; overheating warning above 40 °C
- **Gauges** — animated arc gauges for CPU %, RAM %, Battery %; tap to drill into the Apps screen
- **CPU Cores** — per-core usage bars
- **Memory Details** — total / used / free in MB
- **Battery Details** — level, charging state, temperature
- **RAM Optimizer** — one-tap deep clean, shows MB freed
- **Boot Boost** — restrict auto-start apps at boot

---

### Apps

All running processes sorted by CPU or RAM usage. Tap any app to force-stop it (respects tier).

---

### RAM Monitor

Dedicated per-process memory screen using `ps -A -o RSS,NAME`.

- **Donut chart** — top 7 processes by RSS; tap centre to run a deep clean
- **Per-app cards** — RAM bar, percentage of total, individual force-stop with confirmation
- Reach it from Dashboard → *Per-App RAM Breakdown →*

---

### Automation Rules

Rules are evaluated on every service tick against live metrics.

**Fields:** Name · Metric (CPU / RAM / Battery / Temperature) · Operator · Threshold · Action · Response Type (Notify Only / Semi-Auto / Full Auto)

**Actions by tier:**

| Action | Standard | Shizuku | Root |
|--------|----------|---------|------|
| Notify | ✓ | ✓ | ✓ |
| Reduce Priority | `killBackgroundProcesses` | `am kill` | `kill -19` |
| Stop App | `killBackgroundProcesses` | `am force-stop` | `am force-stop` |
| Clear Cache | Own cache only | `pm clear --cache-only` | `rm -rf /data/data/<pkg>/cache/*` |

Whitelisted apps are always skipped. All executions are written to the Activity Log.

---

### Lists (Whitelist)

Apps added here are never killed or restricted by any automated action. Use for home automation, medical, or navigation apps that must stay alive.

---

### Activity Log

Every action MobilePulse takes is logged here with a timestamp and type tag (INFO / ACTION / AUTOMATION / SUCCESS / WARNING / ERROR).

- **With AI configured** — ERROR and WARNING entries show an **Ask AI to fix this** button that opens the AI chat with the error pre-filled.
- **Without AI** — a **View full info** toggle expands detailed info inline and reminds you where to add an API key.
- Logcat tab with live filter and per-line tap-to-detail (also has Ask AI for error lines).
- Export as JSON or CSV via the share button.

---

### AI Assistant

Built-in chat backed by **Claude** (Anthropic) or **DeepSeek**, selectable in Settings.

- Ask anything about your device's performance
- Errors in the Optimizer, Boot Boost, and Activity Log show an **"Ask AI to fix this"** button that pre-fills the question
- Session history kept in memory; trash icon clears it
- Requires an API key (Settings → AI Assistant)

| Provider | Model | Endpoint |
|----------|-------|----------|
| Claude | `claude-haiku-4-5-20251001` | `api.anthropic.com/v1/messages` |
| DeepSeek | `deepseek-chat` | `api.deepseek.com/chat/completions` |

Get a Claude key at [console.anthropic.com](https://console.anthropic.com) · DeepSeek key at [platform.deepseek.com](https://platform.deepseek.com)

---

### Terminal

A real shell embedded in the app. Tier determines what shell you get:

| Tier | Shell | Capability |
|------|-------|------------|
| Root | `su` via libsu | Full root shell — anything `su` can do |
| Shizuku | `sh` via Shizuku IPC | ADB-level — `am`, `pm`, `dumpsys`, `settings` |
| Standard | `sh` (app UID) | Restricted — `ls`, `getprop`, `date`, `env` |

**Command suggestions** appear as horizontal chips above the input bar and filter as you type. Type `help` or `/help` to print all available commands to the terminal output.

- One-shot execution only (no interactive programs like `vi` or live `top`)
- Access via Settings → Developer Tools → Shell Terminal

---

### Settings

| Section | Setting | Description |
|---------|---------|-------------|
| Appearance | Theme | Forest / Light / Dark / System |
| Enforcement | Tier | Standard / Shizuku / Root — tests availability on tap |
| Thresholds | CPU Alert | 10–95% |
| | RAM Alert | 10–95% |
| | Battery Low | 5–50% |
| General | Notifications | Enable/disable alert notifications |
| | Automation Engine | Enable/disable background rule evaluation |
| | Hourly Auto-Clean | Scheduled deep clean when idle for 5+ min |
| Storage | Clear App Cache | Clears MobilePulse's own cache; shows MB freed |
| AI Assistant | Provider | Claude or DeepSeek |
| | Claude API Key | Stored in DataStore (never leaves the device) |
| | DeepSeek API Key | Stored in DataStore |
| Developer Tools | Shell Terminal | Opens the terminal screen |

---

## Background Service

`MonitoringService` is a foreground service that:

1. Starts on launch and restarts on reboot via `BootReceiver`
2. Polls every **2 seconds** using `ActivityManager`, `/proc/stat`, `BatteryManager`, and `StatFs`
3. Publishes metrics as a static `StateFlow<DashboardMetrics?>` — ViewModels observe without binding to the service
4. Runs `RuleEngine` on every tick
5. Pauses to a 30-second tick while `AutoCleanWorker` is running to avoid interference

---

## Hourly Auto-Clean

`AutoCleanWorker` (WorkManager `PeriodicWorkRequest`, 1-hour interval):

1. Checks `scheduledCleanEnabled` — exits immediately if toggle is off
2. Reads `lastForegroundMs` written by `MainActivity.onStop()` — skips if the user was active within the last 5 minutes
3. Sets `MonitoringService.isCleanRunning = true` (backs off monitoring to 30 s)
4. Runs a deep clean appropriate for the current tier
5. Shows a result notification (RAM freed / cache freed)
6. Clears the flag in a `finally` block so monitoring always resumes

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│  UI  (Jetpack Compose)                          │
│  Screens ─► ViewModels ─► StateFlow ─► recompose│
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  Domain                                         │
│  RuleEngine · OptimizerManager · RogueRamEngine │
│  BootOptimizer · AiRepository                   │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  Data                                           │
│  Room DB  (rules, log, whitelist, boot)         │
│  DataStore (settings, theme, API keys)          │
│  MonitoringService StateFlow (live metrics)     │
│  OkHttp (Claude / DeepSeek APIs)                │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│  Enforcement                                    │
│  EnforcementManager → Standard / Shizuku / Root │
│  ShizukuService IPC · libsu Shell               │
└─────────────────────────────────────────────────┘
```

**Pattern:** MVVM + Repository. Every screen has a `@HiltViewModel`. All state is `StateFlow`. No shared mutable state between ViewModels.

**DI:** Hilt throughout. `AppModule` provides `OkHttpClient` and `Json`. `DatabaseModule` provides Room DAOs.

---

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.1.0 | Language |
| Jetpack Compose BOM | 2024.10.01 | UI framework |
| Material 3 | (BOM) | Design system |
| Hilt | 2.54 | Dependency injection |
| Room | 2.6.1 | Local database |
| DataStore Preferences | 1.1.1 | Settings + API keys |
| Navigation Compose | 2.8.3 | Screen navigation |
| WorkManager | 2.9.1 | Scheduled background tasks |
| Shizuku API | 13.1.5 | ADB-level privilege without root |
| libsu | 6.0.0 | Root shell |
| OkHttp | 4.12.0 | AI API HTTP client |
| kotlinx.serialization | 1.6.3 | JSON parsing |
| Coroutines | 1.8.1 | Async / concurrency |

---

## Build

**Requirements:** Android Studio Hedgehog+, JDK 17, `compileSdk 35`, `minSdk 24`

```bash
git clone https://github.com/shetty0003/MobilePulse.git
cd MobilePulse
./gradlew assembleDebug
```

AIDL must be enabled (`buildFeatures { aidl = true }`) — it's already configured in `app/build.gradle.kts`.

---

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | AI API calls (Claude / DeepSeek) |
| `FOREGROUND_SERVICE` | MonitoringService |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service type |
| `PACKAGE_USAGE_STATS` | Per-app CPU/RAM (user must grant in system Settings) |
| `RECEIVE_BOOT_COMPLETED` | Auto-restart service after reboot |
| `POST_NOTIFICATIONS` | Alert notifications (Android 13+) |
| `KILL_BACKGROUND_PROCESSES` | Standard-tier process management |

`PACKAGE_USAGE_STATS` is the only permission requiring manual user action. The Dashboard shows a **FIX** button that opens the correct system page when it's missing.

---

## License

MIT
