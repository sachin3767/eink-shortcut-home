# Architecture

How the launcher is put together, and the conventions feature work must follow so that
several people/agents can build features in parallel without editing the same lines.
*What* to build lives in [SPEC.md](SPEC.md); this file covers *where* and *how*.

Stack (decision D4): Kotlin, Jetpack Compose (Material 3, all animation off), Room, WorkManager,
DataStore Preferences, manual DI. `minSdk 29`, target device Minimal Phone MP01 (D3).

## Packages

```
com.mp01home.launcher
  MP01LauncherApp.kt     Application; creates AppContainer, exposes Context.appContainer
  AppContainer.kt        Manual DI: core singletons + one reserved block per feature
  MainActivity.kt        Theme from settings, NavHost (no transitions), EinkFlashOverlay
  backup/                JSON export/import of settings + layout via SAF (CORE-7)
  calendar/              CalendarContract repository + Room cache, pure CalendarLogic, actions,
                         free/busy strip, time blocking, leave-by worker, week model (CAL-*, PLAN-3/4)
  capture/               Capture planning, NaturalDateParser, CaptureExecutor (CAP-*, KEEP-2)
  data/apps/             Installed apps, AppLauncher (single launch path + DIS-1 hook), DockDefaults
  data/db/               Room AppDatabase + entities/DAOs (offline cache, EINK-7)
  data/settings/         LauncherSettings model, SettingsRepository (DataStore), SettingsCodec
  data/weather/          OpenWeatherMap client, cache, WeatherSyncable (EXT-1)
  digest/                Notification digest: policy, schedule, summary, repository (FOC-4)
  distraction/           DistractionGate interceptor, FrictionGateActivity, app limits,
                         UsageStats screen time (DIS-1..3)
  eink/                  Extras DataStore, sleep-screen agenda renderer (UX-5), vendor
                         detection/refresh hooks (UX-6), reading-app picker (EXT-3)
  focus/                 Top 3 (FOC-1), focus timer + notifier (FOC-2), DndController (FOC-3)
  habits/                Habit repository, streaks, habit settings (EXT-2)
  keep/                  Keep new note/list shortcuts and share (KEEP-1/2)
  mail/                  MailNotificationListener + NotificationHandler fan-out, Gmail
                         notification parser, mail summary and actions (MAIL-1..3)
  review/                Morning/evening review trigger + state (PLAN-1/2)
  sync/                  Syncable, SyncWorker, SyncScheduler, SyncStatusRepository, formatSyncedLabel
  tasks/                 GoogleTasksAuth (Sign-In, EncryptedSharedPreferences), REST client,
                         repository with offline op queue, mapping, home ViewModel (TASK-*)
  ui/components/         TimelineRail, dialogs, PlaceholderScreen (currently unused)
  ui/drawer/             Paged app drawer with search and letter index, DrawerPaging
  ui/home/               HomeScreen (frame, gestures, paging), HomeViewModel, HomeGestures
  ui/home/sections/      One file per home section slot (see below)
  ui/navigation/         Routes
  ui/review/, ui/focus/  Full-screen review screens (PLAN-1/2) and focus timer screen (FOC-2)
  ui/settings/           SettingsScreen, SettingsViewModel, SettingsComponents
  ui/settings/sections/  One settings slot per feature
  ui/theme/              Black/white theme, typography (≥ 18sp), EinkDimens, einkTappable
  util/                  MinuteTicker, Battery, EinkRefresh
```

Feature-private code lives in its feature package (`calendar/`, `tasks/`, `capture/`, `focus/`,
…), not in the shared `data/`, `ui/` and `util/` packages.

## Home screen and the section-slot convention

`ui/home/HomeScreen.kt` is owned by the foundation. It is a vertical, **non-scrolling**
layout (EINK-1):

```
[timeline rail] | HeaderSection        time · date · battery % · weather (foundation-owned)
                | page body (weight 1, clipped)
                |    page 0: Top3, Calendar, Tasks, Mail, Digest, Focus, Habits, Reading, ScreenTime
                |    page 1: WeekPage (only when HomeSection.WEEK_PAGE is enabled)
                | CaptureSection
                | DockSection         (foundation-owned)
                | page dots            (only with 2 pages)
```

The rail sits on the side opposite `appsAlignment`. A section is shown when its
`HomeSection` toggle is on (`LauncherSettings.enabledSections`, defaults in `HomeSection`).

Each feature section is a slot file in `ui/home/sections/` with a fixed signature:

```kotlin
@Composable
fun CalendarSection(host: HomeSectionHost, modifier: Modifier = Modifier)
```

Slots: `Top3Section`, `CalendarSection`, `TasksSection`, `MailSection`, `CaptureSection`,
`DigestSection`, `FocusSection`, `HabitSection`, `ReadingSection`, `ScreenTimeLine`, `WeekPage`.

Rules for a slot:

- Replace the body only; keep the signature, so HomeScreen never needs editing. Add your own
  ViewModel with `viewModel()` inside the slot, and extra files in your own package.
- Wrap content in `HomeSectionFrame(title, trailing = formatSyncedLabel(...)) { ... }`.
- Render from the local cache (Room/DataStore) only, never from the network (EINK-7).
- Never scroll. Show a few rows and link to a full screen with `host.navigate(route)`.
- Use `Modifier.einkTappable { }` for anything tappable, and typography from `MaterialTheme`.
- Update at most per minute or when data changes (EINK-4). Use `minuteTicker()`, not per-second
  timers.

`HomeSectionHost` gives slots `openDrawer()`, `openSettings()`, `navigate(route)`,
`requestFullRefresh()` and `captureRequests`. `captureRequests` is a counter bumped by the
swipe-down gesture; CaptureSection focuses its input when it changes:

```kotlin
LaunchedEffect(host.captureRequests) { if (host.captureRequests > 0) focusRequester.requestFocus() }
```

### Gestures

`HomeScreen` classifies drags with `classifySwipe(dx, dy, threshold)` (in `ui/home/HomeGestures.kt`),
plus double-tap and long-press. It dispatches each `Gesture` through
`LauncherSettings.gestures.actionFor(gesture)` to a `GestureAction`. The defaults:

| Gesture | Action |
|---|---|
| Swipe up | Drawer |
| Swipe down | Capture |
| Swipe left/right | Change page |
| Double-tap | Full refresh |
| Long-press | Settings |

### Full-screen routes

`ui/navigation/Routes.kt` defines HOME, APP_DRAWER, SETTINGS, MORNING_REVIEW, EVENING_REVIEW
and FOCUS. Every feature route has a real screen with the signature `(onBack: () -> Unit)`.
`ui/review/ReviewAutoTrigger` navigates to the morning or evening review once per day while home is
shown. The friction gate (`distraction/FrictionGateActivity`) is a separate activity using the
`Theme.MP01Home.NoAnim` theme, not a route.

To add a new route, add a constant and one `composable(...)` line in `MainActivity`. That is
the only shared edit.

## AppContainer (manual DI)

```kotlin
val c = context.appContainer          // Activity, Service, Worker, or application in a ViewModel
```

Core members, declared above the feature blocks:

| Member | Type |
|---|---|
| `appScope` | `CoroutineScope` |
| `settingsRepository` | `SettingsRepository` |
| `database` | `AppDatabase` (lazy) |
| `installedAppsRepository` | `InstalledAppsRepository` |
| `appLauncher` | `AppLauncher` |
| `einkRefresh` | `EinkRefreshController` |
| `syncStatusRepository` | `SyncStatusRepository` |
| `syncScheduler` | `SyncScheduler` |
| `weatherRepository`, `weatherCacheRepository`, `weatherSyncable` | weather |

Each feature has one reserved block, delimited by comments: `[calendar]`, `[tasks]`, `[mail]`,
`[capture]`, `[focus]`, `[planning]`, `[extras]`. Edit only your own block:

```kotlin
// ---- [calendar] CAL-* ----
val calendarRepository = CalendarRepository(app, database.cachedEventDao(), settingsRepository)
init { registerSyncable(calendarRepository) }
// ---- [end calendar] ----
```

Kotlin runs property initialisers and `init` blocks top to bottom. A block may therefore use
only what is declared above it. Keep constructors cheap: no I/O, because this runs in
`Application.onCreate`. `start()` is called once after construction. It:

- initialises the dock;
- binds the auto-refresh N;
- schedules the periodic sync;
- registers the screen-on trigger.

## Sync (EINK-6 / EINK-7)

```kotlin
interface Syncable {
    val syncKey: String                                  // stable id, e.g. "calendar"
    suspend fun sync(reason: SyncReason): SyncResult     // PERIODIC | SCREEN_ON | MANUAL
}
enum class SyncResult { SUCCESS, SKIPPED, RETRY, FAILURE }
```

- Register it with `registerSyncable(...)` in your AppContainer block.
- `SyncWorker` runs all syncables one after another. On SUCCESS it records the time in
  `SyncStatusRepository.markSuccess(syncKey)`. If any syncable returns RETRY, the whole run is
  retried, up to 3 attempts.
- Scheduling:
  - The periodic job runs every `syncIntervalMinutes` (15–30).
  - A one-off job runs on screen-on, at most every 5 minutes.
  - `syncScheduler.requestSync(SyncReason.MANUAL, force = true)` runs one now.
- To show freshness, use `syncStatusRepository.lastSuccessMillis(key)` together with
  `formatSyncedLabel(millis, timeFormat)`. It returns "synced 10:42", "synced 24 Sep 10:42" or
  "not synced".
- A syncable writes to Room/DataStore and the UI observes those flows. Syncables never push to
  the UI directly.

## Notifications (MAIL-1..3, FOC-4)

`mail/MailNotificationListener` is declared in the manifest and forwards every event to all
registered handlers:

```kotlin
interface NotificationHandler {
    fun onSnapshot(active: List<StatusBarNotification>) {}   // on (re)connect
    fun onPosted(sbn: StatusBarNotification) {}
    fun onRemoved(sbn: StatusBarNotification) {}
}
```

- Register a handler with `registerNotificationHandler(...)`.
- Handlers run on the listener's main thread, so hand work off to `appScope`.
- Access helpers: `MailNotificationListener.isAccessGranted(context)`,
  `accessSettingsIntent()` and `requestRebind(context)`.

## Launching apps (DIS-1)

All launches go through `appLauncher.launch(packageName)`. The friction gate installs
`appLauncher.interceptor = LaunchInterceptor { pkg -> /* show gate */ true }`, and after the pause
calls `launch(pkg, bypassInterceptor = true)`. For other intents, use
`appLauncher.startActivitySafely(intent)`.

## Settings

`LauncherSettings` (in `data/settings/SettingsModels.kt`) is one immutable snapshot of all user
preferences. It is exposed as `settingsRepository.settings: Flow<LauncherSettings>`, with
`suspend current()` for a one-off read. Groups:

- **Header and layout:** alignment, timeline, time, date, battery and weather.
- **Dock:** `dockApps` (at most `MAX_DOCK_APPS = 6`).
- **Sections:** `enabledSections`.
- **Display:** `bodyFontScale` (0.8–1.6), `density`, `fullRefreshEveryN` (0 = off).
- **Sync:** `syncIntervalMinutes` (15–30).
- **Gestures:** `gestures` (a `GestureMapping`).
- **App lists:** `hiddenApps`, `frictionApps`.
- **Sources:** `selectedCalendarIds` (empty = all), `selectedTaskListIds`.
- **Focus:** `focusMinutes`, `breakMinutes`, `focusDndEnabled`.
- **Digest:** `digestWhitelist`, `digestTimes`.
- **Reviews:** morning and evening review enabled flags and times.

Each field has a matching `suspend setX(...)` on `SettingsRepository`. Examples:

- `setSectionEnabled(section, enabled)`
- `setAppHidden(pkg, hidden)`
- `setDigestTimes(times)`

Setters clamp their values. Ordered lists are stored comma-joined, and the encoding lives in
`SettingsCodec`, which is unit-tested. The legacy `home_apps` key is migrated into the dock once
by `ensureDockInitialized`.

Feature-private state belongs in your own DataStore file or Room table, not in `LauncherSettings`.
Examples are OAuth tokens, sync cursors and timer state. If you need a new user-facing setting,
add the field, its key and its setter next to the related group.

**Settings UI.** `SettingsScreen` renders the general sections and then calls one slot per
feature from `ui/settings/sections/`:

`CalendarSettings`, `TasksSettings`, `MailSettings`, `FocusSettings`, `ReviewSettings`,
`DistractionSettings`, `BackupSettings`, `ExtrasSettings`.

Each slot has the signature `(viewModel: SettingsViewModel)`. Build it from the shared rows in
`SettingsComponents.kt`:

- `SettingsSectionTitle`
- `ToggleRow`
- `CycleRow`
- `StepperRow`
- `ClickableLabel`
- `SettingsNote`

Write through `viewModel.update { setX(...) }`.

## Local database (Room, `data/db`)

`AppDatabase` is version 1 with `exportSchema = false` and `fallbackToDestructiveMigration()`.
It is only a cache, so everything in it can be rebuilt by a sync. Every DAO returns `Flow`s for
the UI.

| Entity | DAO | Used by |
|---|---|---|
| `CachedEvent` (per instance) | `cachedEventDao()`: `observeBetween`, `observeNext`, `replaceWindow` | CAL-* |
| `CachedTask`, `PendingTaskOp` | `cachedTaskDao()`, `pendingTaskOpDao()` (offline queue) | TASK-* |
| `MailNotification` | `mailNotificationDao()` | MAIL-1/2 |
| `DigestNotification` | `digestNotificationDao()` | FOC-4 |
| `TopPriority` (unique date+slot) | `topPriorityDao()` | FOC-1, PLAN-1 |
| `Habit`, `HabitEntry` | `habitDao()` | EXT-2 |

When you add or change an entity, bump `version`. The destructive fallback then rebuilds the
cache.

## Theme and e-ink rules

- `MP01HomeTheme(fontScale, density)` is applied in `MainActivity` from the settings. It
  provides:
  - black/white colours;
  - monospace typography, with body/title/labelLarge at 18sp or more (`einkTypography`);
  - no ripple or indication;
  - `EinkTheme.dimens`, which contains `gutter`, `sectionGap`, `rowPadding` and
    `minTouchTarget = 56dp`.
- Use `Modifier.einkTappable(onLongClick = …) { onClick }` for every tap target. It sets a 56dp
  minimum and has no ripple.
- Full refresh:
  - `einkRefresh.requestFullRefresh()`: vendor hook if available, otherwise a 300 ms black
    flash via `EinkFlashOverlay`.
  - Every frame the window draws counts as a partial update. After `fullRefreshEveryN` of them
    a full refresh happens automatically.
  - Vendor hooks: `VendorRefreshHooks` (an Onyx reflection hook is included). `eink/VendorRefresh`
    swaps in Onyx, Bigme or Hisense hooks based on the Settings vendor mode, which is Auto by
    default.
- Battery: `batteryPercentFlow(context)` emits only when the percentage changes.

## Tests

JVM unit tests live in `app/src/test/`, one per pure-logic unit. They cover:

- **Settings and backup:** settings codec, backup codec.
- **Home:** synced label, gestures, dock layout, weather line, drawer paging, partial-update counter.
- **Calendar:** calendar logic.
- **Capture:** capture plan, natural-date parser.
- **Mail and Keep:** Gmail parser, mail summary, Keep shortcut matcher.
- **Digest:** digest policy, schedule, summary.
- **Focus and review:** focus timer, Top 3, review trigger.
- **Extras:** usage calc, sleep-screen content, vendor detection, streaks.
- **Tasks:** tasks mapping.

Run them with `./gradlew testDebugUnitTest`. Keep new logic in pure functions where possible so
it can be tested the same way.
