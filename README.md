# MobilePulse

Android system monitor and optimizer. Tracks CPU, RAM, battery, and running apps in real time; enforces user-defined automation rules; and provides root/Shizuku-accelerated optimization — all from a single foreground service that ticks every 5 seconds.

---

## Table of Contents

1. [Overview](#overview)
2. [Enforcement Tiers](#enforcement-tiers)
3. [Screens](#screens)
   - [Dashboard](#dashboard)
   - [Apps](#apps)
   - [RAM Monitor](#ram-monitor)
   - [Automation Rules](#automation-rules)
   - [Lists (Whitelist)](#lists-whitelist)
   - [Activity Log](#activity-log)
   - [AI Assistant](#ai-assistant)
   - [Terminal](#terminal)
   - [Settings](#settings)
4. [Background Service](#background-service)
5. [Automation Engine](#automation-engine)
6. [Optimizer](#optimizer)
7. [Boot Boost](#boot-boost)
8. [Themes](#themes)
9. [Architecture](#architecture)
10. [Tech Stack](#tech-stack)
11. [Build Requirements](#build-requirements)
12. [Permissions](#permissions)

---

## Overview

MobilePulse runs a persistent foreground service that polls system metrics every 5 seconds and exposes them as a `StateFlow` to the UI. All optimization actions are gated behind an **enforcement tier** — the same app works on stock Android, Shizuku-enabled devices, and fully rooted devices, adjusting what it can do accordingly.

---

## Enforcement Tiers

The tier controls what MobilePulse is allowed to do when killing processes, clearing caches, or applying boot restrictions. You set it once in Settings and the entire app — dashboard, optimizer, automation rules, RAM monitor, and terminal — all operate within that boundary.

| Tier | Requirement | Capabilities |
|------|-------------|--------------|
| **Standard** | None | Soft process trimming via `ActivityManager`, own-app cache only, notifications |
| **Shizuku** | Shizuku app running with ADB permission | `am force-stop`, `pm trim-caches`, full per-app cache clear, ADB shell commands |
| **Root** | Magisk (or any SU provider) | Everything above + direct filesystem access, `am kill-all`, system-level cache wipe, root shell |

### Setting up Shizuku
1. Install the **Shizuku** app from the Play Store.
2. Enable **Developer Options** on your device (tap Build Number 7 times).
3. Inside Shizuku, start the service via **Wireless Debugging** or connect via USB ADB (`adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`).
4. Open MobilePulse → Settings → Enforcement Tier → tap **Shizuku**.
5. Grant the permission prompt that appears.

---

## Screens

### Dashboard

The home screen. Shows live metrics updated every 5 seconds from the background service.

- **Status card** — active tier, Usage Access permission status with one-tap Fix button
- **Alert banners** — animated cards that appear when CPU, RAM, or battery cross your configured thresholds; also shows overheating warning above 40 °C
- **Gauge cards** — animated arc gauges for CPU %, RAM %, and Battery %; tapping CPU or RAM navigates to the Apps screen filtered by that metric
- **CPU Cores** — per-core usage bars
- **Memory Details** — total / used / free in MB
- **Battery Details** — level, charging status, temperature
- **Optimizer** — inline RAM optimizer and Boot Boost cards (see [Optimizer](#optimizer))

---

### Apps

Lists all running processes with their CPU and RAM usage, sorted by the selected metric. Supports filtering by CPU-heavy or RAM-heavy processes. Tap any app to force-stop it (tier-dependent).

---

### RAM Monitor

Dedicated per-process memory screen. Uses `ps -A -o RSS,NAME` to read resident set size for every process (requires Shizuku or Root for full results).

- **Animated donut chart** — shows the top 7 processes by RSS; tap to trigger a **deep clean** (spins while running, shows MB freed when done)
- **Deep clean** kills background processes (`am kill-all` on Shizuku/Root, `killBackgroundProcesses` on Standard) and trims caches (`pm trim-caches` on Shizuku, `rm -rf /data/data/*/cache/*` on Root)
- **Per-app cards** — RAM bar, percentage of total, individual force-stop button with confirmation dialog
- Access via Dashboard → "Per-App RAM Breakdown →"

---

### Automation Rules

Create rules that trigger actions automatically when a metric crosses a threshold.

**Rule fields:**
- **Name** — display label
- **Metric** — CPU, RAM, Battery, or Temperature
- **Operator** — greater than, less than, ≥, ≤
- **Threshold** — numeric value (% for CPU/RAM/Battery, °C for Temp)
- **Action** — Notify, Stop App, Clear Cache, or Reduce Priority
- **Response Type** — Notify Only, Semi-Auto (notify + ask), Full Auto (execute silently)

Rules are evaluated against live metrics on every service tick. Whitelisted apps are always skipped. All executions are written to the Activity Log.

**App picker** — rules targeting "Stop App" or "Clear Cache" let you pick which apps are in scope from your installed package list.

---

### Lists (Whitelist)

Apps added here are never killed or restricted by automation rules or the optimizer. Use this for apps that must keep running (home automation, medical, navigation).

---

### Activity Log

Timestamped list of every action MobilePulse has taken: optimization runs, rule triggers, boot restrictions applied, cache clears. Filterable. Persisted in Room.

---

### AI Assistant

Built-in chat interface backed by **Claude** (Anthropic) or **DeepSeek**, selectable in Settings.

- Type any question about your device's performance
- When the Optimizer or Boot Boost encounters an error, an **"Ask AI to fix this"** button appears inline — tapping it opens the assistant with the error pre-filled as the first message
- Conversation history is kept for the session; tap the trash icon to clear it
- Requires an API key (see Settings → AI Assistant)

**Claude** — `claude-haiku-4-5-20251001`, via `api.anthropic.com/v1/messages`  
**DeepSeek** — `deepseek-chat`, via `api.deepseek.com/chat/completions`

---

### Terminal

A real shell terminal inside the app. What it can do depends on your tier:

| Tier | Shell | Example commands |
|------|-------|-----------------|
| Root | `su` via libsu | `dumpsys meminfo`, `cat /proc/cpuinfo`, `reboot`, `svc wifi disable` |
| Shizuku | `sh` via Shizuku service (ADB-level) | `am force-stop <pkg>`, `pm clear <pkg>`, `dumpsys battery` |
| Standard | `sh` (app user only) | `ls`, `getprop`, `date`, `env` |

- Green-on-black monospace UI
- Input commands on the bottom bar; output scrolls up
- Clear screen button in the top bar
- One-shot execution only — no interactive programs (`vi`, live `top`, etc.)
- Access via Settings → Developer Tools → Shell Terminal

---

### Settings

| Section | Option | Description |
|---------|--------|-------------|
| **Appearance** | App Theme | Forest (default), Light, System, Dark |
| **Enforcement Tier** | Standard / Shizuku / Root | Sets the permission level for all operations; tests availability on tap |
| **Alert Thresholds** | CPU Alert | Triggers dashboard banner and notifications (10–95%) |
| | RAM Alert | (10–95%) |
| | Battery Low | (5–50%) |
| **General** | Notifications | Enable/disable threshold alert notifications |
| | Automation Engine | Enable/disable the background rule evaluator |
| **Storage** | Clear App Cache | Deletes MobilePulse's own cache files; shows MB freed in a snackbar |
| **AI Assistant** | Provider | Claude or DeepSeek |
| | Claude API Key | `sk-ant-...` from console.anthropic.com |
| | DeepSeek API Key | `sk-...` from platform.deepseek.com |
| | Open AI Assistant | Direct link to the chat screen |
| **Developer Tools** | Shell Terminal | Direct link to the terminal screen |
| **About** | Version | App version (1.0.0) |
| | Active Tier | Current enforcement tier shown in color |

---

## Background Service

`MonitoringService` is a foreground service that:

1. Starts on app launch and survives in the background
2. Polls system metrics every **5 seconds** using `ActivityManager`, `/proc/stat`, `BatteryManager`, and `StatFs`
3. Publishes metrics via a static `StateFlow<DashboardMetrics?>` so all ViewModels can observe without binding to the service
4. Runs the **RuleEngine** on every tick to evaluate automation rules
5. Updates the persistent notification (summary refresh every 5 minutes to avoid Android rate-limiting)
6. Respects the `refreshIntervalSeconds` setting (configurable 1–60 s, default 2 s for the UI polling rate)

The service starts automatically on device reboot via `BootReceiver` if the app was running before shutdown.

---

## Automation Engine

`RuleEngine` is called on every service tick. Evaluation flow:

```
metrics tick
  └─ settings.automationEnabled? → no → skip
  └─ fetch active rules from Room
      └─ for each rule:
           evaluate metric against threshold and operator
           └─ triggered?
                check exemption list (whitelist) → skip if exempt
                └─ execute action via EnforcementManager
                     log result to ActivityLog
                     send notification if notifyOnTrigger
```

**Actions by tier:**

| Action | Standard | Shizuku | Root |
|--------|----------|---------|------|
| NOTIFY | ✓ | ✓ | ✓ |
| REDUCE_PRIORITY | `ActivityManager.killBackgroundProcesses` | `am kill` | `kill -19` |
| STOP_APP | `ActivityManager.killBackgroundProcesses` | `am force-stop` | `am force-stop` |
| CLEAR_CACHE | Own cache only | `pm clear --cache-only` | `rm -rf /data/data/<pkg>/cache/*` |

---

## Optimizer

The **RAM Optimizer** card on the Dashboard runs a full optimization pass on demand:

**Root:**
1. `am kill-all` (kills all cached background processes)
2. Per-package cache wipe via `rm -rf /data/data/*/cache/*`

**Shizuku:**
1. `am kill-all`
2. `pm trim-caches 999999999` (trims all app caches system-wide)

**Standard:**
1. `ActivityManager.killBackgroundProcesses` for all non-whitelisted packages
2. `System.gc()`
3. Own-app cache delete

Results (apps killed, MB freed, errors) are shown inline and written to the Activity Log.

---

## Boot Boost

Prevents selected apps from auto-starting on reboot by revoking `RECEIVE_BOOT_COMPLETED` and setting background restriction flags. Available on Shizuku and Root tiers only.

- Scans for known battery-draining packages (social media, news, ad networks)
- Skips anything on the whitelist
- Changes persist until you tap **Reset** in the Boot Boost card
- Result is logged with a count of restricted apps

---

## Themes

| Theme | Description |
|-------|-------------|
| **Forest** (default) | Deep green background (`#071A0E`), sage green primary (`#52B788`), warm cream text (`#F2EDD0`) |
| **Light** | Warm cream background (`#F5F0E4`), forest green primary (`#2D7A50`), dark green text |
| **Dark** | Navy background (`#0A0E1A`), blue primary (`#4F8EF7`) |
| **System** | Forest when system is dark; Light when system is light |

Theme preference is persisted in a separate DataStore (`mp_theme`) and applied at the root composable via `MobilePulseTheme`.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  UI Layer  (Jetpack Compose)                        │
│  Screens → ViewModels → StateFlow → recompose       │
└───────────────────────┬─────────────────────────────┘
                        │ collect / call
┌───────────────────────▼─────────────────────────────┐
│  Domain / Use-case Layer                            │
│  RuleEngine · OptimizerManager · RogueRamEngine     │
│  BootOptimizer · AiRepository                       │
└───────────────────────┬─────────────────────────────┘
                        │ read / write
┌───────────────────────▼─────────────────────────────┐
│  Data Layer                                         │
│  Room DB  (rules, log, whitelist, boot restrictions)│
│  DataStore (settings, theme, AI keys)               │
│  MonitoringService StateFlow (live metrics)         │
│  OkHttp (Anthropic / DeepSeek API)                  │
└───────────────────────┬─────────────────────────────┘
                        │ platform APIs
┌───────────────────────▼─────────────────────────────┐
│  Enforcement Layer                                  │
│  EnforcementManager → Standard / Shizuku / Root     │
│  ShizukuService (IPC to Shizuku user service)       │
│  libsu Shell (root commands)                        │
└─────────────────────────────────────────────────────┘
```

**Pattern:** MVVM + Repository. Every screen has a `@HiltViewModel`; all state is `StateFlow`; no shared mutable state between ViewModels.

**Dependency Injection:** Hilt throughout. `AppModule` provides `OkHttpClient`, `Json`, and coroutine dispatchers. `DatabaseModule` provides Room DAOs. `RepositoryModule` provides repository singletons.

---

## Tech Stack

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.1.0 | Language |
| Jetpack Compose BOM | 2024.10.01 | UI |
| Material3 | (BOM) | Design system |
| Hilt | 2.54 | Dependency injection |
| Room | 2.6.1 | Local database |
| DataStore Preferences | 1.1.1 | Settings / theme / API keys |
| Navigation Compose | 2.8.3 | In-app navigation |
| WorkManager | 2.9.1 | Scheduled background tasks |
| Shizuku API | 13.1.5 | ADB-level privilege without root |
| libsu | 6.0.0 | Root shell via Magisk |
| OkHttp | 4.12.0 | HTTP client for AI API calls |
| kotlinx.serialization | 1.6.3 | JSON (de)serialization |
| Coil | 2.7.0 | Image loading |
| Coroutines | 1.8.1 | Async / background work |

---

## Build Requirements

- **Android Studio** Hedgehog or newer
- **JDK 17**
- **compileSdk 35**, **minSdk 24** (Android 7.0+)
- **AIDL** enabled (`buildFeatures { aidl = true }`) — required for the Shizuku IPC interface

```bash
# Clone and open in Android Studio, then:
./gradlew assembleDebug
```

KSP is used for Room and Hilt annotation processing (no kapt).

---

## Permissions

| Permission | Required for |
|------------|-------------|
| `FOREGROUND_SERVICE` | MonitoringService |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ foreground service type |
| `PACKAGE_USAGE_STATS` | Reading per-app CPU/RAM usage (user must grant manually in Settings) |
| `RECEIVE_BOOT_COMPLETED` | Auto-start service on reboot |
| `POST_NOTIFICATIONS` | Alert notifications (Android 13+) |
| `REQUEST_INSTALL_PACKAGES` | (declared, not actively used) |

Usage Access (`PACKAGE_USAGE_STATS`) is the only permission that requires the user to navigate to a system settings page. The Dashboard Status Card shows a **FIX** button that opens the correct settings page directly when this permission is missing.
