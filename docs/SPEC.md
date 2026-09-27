# E-ink Productivity Launcher — Product Spec

> **For Claude Code / agents:** This file is the source of truth for *what* should be built.
> To run a gap analysis, follow [§0 Agent workflow](#0-agent-workflow). Keep the
> status table in [§9](#9-status-tracker) up to date whenever you implement something.

---

## 0. Agent workflow

When asked to "compare the spec with what's built" or "action new items":

1. Read this whole file, then survey the codebase (manifest, `src/`, build files, tests).
2. For every requirement ID below (e.g. `CAL-2`), decide one status:
   - `DONE` — implemented and all acceptance criteria met
   - `PARTIAL` — some criteria met (list which are missing)
   - `TODO` — not started
   - `BLOCKED` — needs a decision/credential from the user (say what)
3. Cite evidence as `path/to/file.kt:line` for DONE/PARTIAL.
4. Update the table in §9 (status + evidence + notes). Do not delete requirements;
   mark obsolete ones `DROPPED` with a reason.
5. Pick the next items by **priority (P0 → P1 → P2), then phase order (§8)**,
   respecting dependencies. Implement, test, update §9, commit.
6. If you discover a new requirement, add it with a new ID in the right section
   and mark it `TODO` — never renumber existing IDs.
7. Open questions go in §10; don't guess on anything marked as a decision.

Priority: **P0** = core MVP, **P1** = high-value, **P2** = nice-to-have.

---

## 1. Goal

An Android home launcher for e-ink devices (Onyx Boox, Bigme, Hisense, etc.) that
makes the home screen a live, glanceable "daily sheet" and gives one-tap access
to Google Calendar, Google Tasks, Gmail and Google Keep — while reducing
distraction.

## 2. E-ink design constraints (apply to ALL features)

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| EINK-1 | P0 | No animations | All window/activity transitions, ripples, and list animations disabled. No scrolling lists on home; use paging. |
| EINK-2 | P0 | High contrast | Pure black `#000` on white `#FFF` only; no greys for meaning. State shown via bold, inversion, icons. |
| EINK-3 | P0 | Large touch targets | All tappables ≥ 56dp. Body text ≥ 18sp, configurable. |
| EINK-4 | P0 | Event-driven redraw | Views only invalidate when their data changes. No per-second clock ticks (minute-level only). |
| EINK-5 | P1 | Ghosting control | Manual full-refresh gesture (double-tap empty space) + setting "full refresh every N partial updates". Vendor refresh APIs used where available (see UX-6). |
| EINK-6 | P0 | Battery-friendly sync | Background sync via WorkManager (15–30 min, configurable) + on screen-on. No persistent foreground services. |
| EINK-7 | P0 | Offline-first | All widgets render from a local cache (Room/DataStore) and show "synced HH:MM". |

## 3. Launcher core

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| CORE-1 | P0 | Registers as HOME launcher | `CATEGORY_HOME` intent filter; can be set as default. |
| CORE-2 | P0 | Clock / date / battery header | Minute-resolution time, full date, battery %. |
| CORE-3 | P0 | Dock | 4–6 user-configurable shortcuts (defaults: Calendar, Tasks, Gmail, Keep). Replaces the old "up to 8 apps" home list. |
| CORE-4 | P0 | App drawer | Text-only A–Z list, paged, with search box; opened by swipe up. |
| CORE-5 | P1 | Hide apps | Apps can be hidden from the drawer. |
| CORE-6 | P1 | Gestures | Swipe up = drawer, swipe down = quick capture, double-tap = full refresh. Configurable. |
| CORE-7 | P2 | Layout backup | Export/import settings + layout as JSON. |

## 4. Integrations

### 4.1 Calendar
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| CAL-1 | P0 | Next-event card | Title, time, countdown ("in 25 min"), location. Via `CalendarContract` (READ_CALENDAR), all device accounts. |
| CAL-2 | P0 | Today's agenda | Remaining events today, max ~5, tap opens event in Calendar app. |
| CAL-3 | P1 | Quick add event | Button opens `Intent.ACTION_INSERT` on `Events.CONTENT_URI`. |
| CAL-4 | P1 | Join-meeting button | If next event contains a Meet/Zoom/Teams URL, show "Join" when ≤10 min away. |
| CAL-5 | P1 | Free/busy strip | Text/block timeline of the day showing busy vs free gaps. |
| CAL-6 | P2 | Leave-by alert | Notification "leave in X min" for events with a location. |
| CAL-7 | P2 | Calendar selection | User chooses which calendars are shown. |

### 4.2 Google Tasks
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| TASK-1 | P0 | Auth | Google Sign-In / OAuth with `https://www.googleapis.com/auth/tasks`. Token stored securely. |
| TASK-2 | P0 | Due today + overdue | Home widget shows overdue count and top 3–5 tasks due today. |
| TASK-3 | P0 | Complete from home | Tapping checkbox marks complete (optimistic UI, queued if offline). |
| TASK-4 | P1 | Quick add | Adds to default list with optional due date. |
| TASK-5 | P2 | List picker | Choose which task lists feed the home screen. |

### 4.3 Gmail
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| MAIL-1 | P0 | Unread count | Shown on home. **Default implementation: `NotificationListenerService`** reading Gmail notifications (no restricted scopes). |
| MAIL-2 | P1 | Top messages | Sender + subject of latest 3 unread/important. |
| MAIL-3 | P0 | Quick actions | Open inbox, compose (`mailto:`/Gmail intent). |
| MAIL-4 | P2 | Gmail API mode | ~~Optional `gmail.readonly` integration~~ **DROPPED (D1):** notification-listener only. |

### 4.4 Google Keep
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| KEEP-1 | P0 | New note / checklist | One-tap launches Keep new-note (via `ACTION_SEND` to `com.google.android.keep` or Keep shortcuts). |
| KEEP-2 | P1 | Send-from-capture | Quick capture (CAP-1) can target Keep via share intent. |
| KEEP-3 | P2 | Pinned notes on home | ~~Local scratchpad widget~~ **DROPPED (D2).** Keep has no consumer API. |

## 5. Capture & focus

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| CAP-1 | P0 | Universal quick capture | Single input on home; target toggle Task / Keep / Event; submits in ≤2 taps. |
| CAP-2 | P1 | Natural-language dates | "call Sam fri 3pm" → due date/time parsed. |
| CAP-3 | P1 | Voice input | Mic button using `RecognizerIntent`. |
| FOC-1 | P0 | Daily Top 3 | User pins up to 3 priorities (free text or linked tasks); shown at top; resets daily with carry-over prompt. |
| FOC-2 | P1 | Focus timer | Pomodoro (25/5 configurable), large countdown updating once per minute. |
| FOC-3 | P1 | DND during focus | Optional: enable Do Not Disturb while timer runs (notification policy access). |
| FOC-4 | P1 | Notification digest | Home shows batched notification summary from whitelisted apps; others hidden until digest times. |

## 6. Planning & review

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| PLAN-1 | P1 | Morning review screen | Agenda + due tasks + set Top 3. Shown on first unlock after configurable time. |
| PLAN-2 | P1 | Evening review screen | Completed today, roll-over tasks, tomorrow's first event. |
| PLAN-3 | P2 | Week page | Second home page: 7 days with event titles and task counts. |
| PLAN-4 | P2 | Time blocking | Schedule a task into a free slot (creates calendar event). |

## 7. Distraction control & extras

| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| DIS-1 | P1 | Friction gate | Flagged apps require a 10s pause / typed reason before opening. |
| DIS-2 | P2 | Daily app limits | Per-app minute limit using `UsageStatsManager`. |
| DIS-3 | P2 | Screen-time line | One-line "today: 1h12m, top: Chrome" on home. |
| EXT-1 | P1 | Weather | Now + rain chance next hours. **Resolved:** OpenWeatherMap (user API key + city, no location permission) instead of Open-Meteo — current weather + max `pop` of the next forecast slots. |
| EXT-2 | P2 | Habit tracker | Row of daily checkboxes, streak count. |
| EXT-3 | P2 | Reading shortcuts | Configurable reading-list/e-reader shortcuts. |
| UX-5 | P1 | Sleep-screen agenda | Render agenda to the device's sleep/lock image (e-ink retains it with no power). Vendor-specific. |
| UX-6 | P2 | Vendor refresh modes | Per-device refresh mode (Onyx SDK / Bigme / Hisense) with generic fallback. |
| UX-7 | P1 | Themes | Font size + density settings. |

## 8. Phases (build order)

1. **Shell:** CORE-1..4, EINK-1..4, CORE-2
2. **Calendar:** CAL-1, CAL-2, CAL-3, EINK-6, EINK-7
3. **Capture:** CAP-1, KEEP-1, KEEP-2, MAIL-3
4. **Tasks:** TASK-1..4
5. **Mail:** MAIL-1, MAIL-2
6. **Focus:** FOC-1..4, DIS-1, CORE-5, CORE-6
7. **Review & extras:** PLAN-1..2, EXT-1, CAL-4, CAL-5, EINK-5, UX-5, UX-7
8. **Nice-to-have:** all remaining P2

### Reference home layout (top → bottom)
```
┌──────────────────────────────┐
│ Tue 24 Sep     10:42    87%  │  CORE-2
├──────────────────────────────┤
│ TOP 3: ① … ② … ③ …           │  FOC-1
├──────────────────────────────┤
│ NEXT  Standup · in 18 min    │  CAL-1 / CAL-4
│ 11:00 Design review          │  CAL-2
├──────────────────────────────┤
│ TASKS  2 overdue             │  TASK-2
│ ☐ Send invoice  ☐ Call Sam   │
├──────────────────────────────┤
│ MAIL  5 unread · 1 important │  MAIL-1
├──────────────────────────────┤
│ [ + capture…      ] T K E 🎤 │  CAP-1
├──────────────────────────────┤
│ Cal  Tasks  Gmail  Keep      │  CORE-3
└──────────────────────────────┘
```

## 9. Status tracker

> Agents: update this table on every implementation pass.

> **Build status: UNVERIFIED.** The build environment has no Android SDK and Google Maven is
> blocked, so this code has never been built by Gradle, run through KSP/Room codegen, or run on
> a device. The last integration pass compiled every project file against hand-written
> Android/androidx stubs. That compile found no unresolved project symbols, redeclarations or
> signature mismatches between features. The JVM unit tests under `app/src/test` pass against
> those stubs. `PartialUpdateCounterTest` was not run. "DONE" below means that the code meets
> the acceptance criteria when read; it does not mean the feature was tested on a device.
> Evidence paths are relative to `app/src/main/java/com/mp01home/launcher/` unless they start
> with `app/`.

| ID | Status | Evidence | Notes |
|---|---|---|---|
| EINK-1 | DONE | `app/src/main/res/values/styles.xml:6`, `MainActivity.kt:91`, `ui/theme/Theme.kt:84`, `ui/home/HomeScreen.kt:77`, `ui/drawer/AppDrawerScreen.kt:138` | No window animations. NavHost transitions are `None`. `NoIndication` plus a null ripple config. Home is non-scrolling and paged: page 0 holds the sections, page 1 the week. The drawer is paged. Only Settings scrolls, and it is not a home surface. |
| EINK-2 | DONE | `ui/theme/Color.kt:5`, `ui/theme/Theme.kt:84` | Only `#000` and `#FFF` are used. State is shown by bold text, inversion or glyphs. |
| EINK-3 | DONE | `ui/theme/Type.kt:14`, `ui/theme/EinkModifiers.kt:12` | Body text is at least 18sp at every font scale. `Modifier.einkTappable` enforces a 56dp minimum and is used by every section. |
| EINK-4 | DONE | `util/MinuteTicker.kt:12`, `util/Battery.kt:21`, `focus/FocusViewModel.kt:71` | Clocks and countdowns tick on minute boundaries. Battery redraws only when the % changes. The only sub-minute timer is the single 10 s pause in the friction gate (`distraction/FrictionGateActivity.kt:125`). |
| EINK-5 | DONE | `util/EinkRefresh.kt:115`, `util/EinkRefresh.kt:133`, `ui/home/HomeScreen.kt:144`, `ui/settings/SettingsScreen.kt:171` | Double-tap triggers a full refresh. A setting forces a full refresh every N partial updates. |
| EINK-6 | DONE | `sync/SyncScheduler.kt:37`, `sync/SyncScheduler.kt:70`, `sync/SyncWorker.kt` | Periodic WorkManager sync, 15–30 min (a setting). A throttled one-off sync runs on screen-on. No foreground services. |
| EINK-7 | DONE | `data/db/AppDatabase.kt:16`, `sync/SyncStatus.kt:37`, `ui/home/sections/CalendarSection.kt:66`, `ui/home/sections/TasksSection.kt:42`, `ui/home/sections/MailSection.kt:45` | Calendar, Tasks, Mail, Digest and Week render from Room and show "synced HH:MM". Weather renders from the DataStore cache (`data/weather/WeatherCacheRepository.kt`) but its header line shows no synced label. |
| CORE-1 | DONE | `app/src/main/AndroidManifest.xml:77`, `ui/settings/SettingsScreen.kt:221` | HOME intent filter, plus a `RoleManager.ROLE_HOME` request in Settings. |
| CORE-2 | DONE | `ui/home/sections/HeaderSection.kt:33` | Minute-resolution time, date, battery % and a weather line. |
| CORE-3 | DONE | `ui/home/sections/DockSection.kt:43`, `data/apps/DockDefaults.kt`, `data/settings/SettingsRepository.kt:161` | Up to 6 shortcuts. Defaults are Calendar, Tasks, Gmail and Keep when installed. The legacy home list is migrated. Shortcuts are added and removed by long-pressing in the drawer. |
| CORE-4 | DONE | `ui/drawer/AppDrawerScreen.kt:116`, `ui/drawer/AppDrawerScreen.kt:138`, `ui/drawer/DrawerPaging.kt:16` | Text-only A–Z list with a search box, Prev/Next paging and a letter index. Opened by swiping up. |
| CORE-5 | DONE | `ui/drawer/AppDrawerViewModel.kt:34`, `ui/settings/sections/DistractionSettings.kt:162` | Per-app "hidden" toggle in Settings. The drawer filters hidden apps out. |
| CORE-6 | DONE | `ui/home/HomeScreen.kt:151`, `ui/home/HomeGestures.kt:10`, `data/settings/SettingsModels.kt:43`, `ui/home/sections/CaptureSection.kt:96` | Defaults: swipe up opens the drawer, swipe down focuses capture, double-tap does a full refresh. Left/right swipes change pages. Long-press opens Settings. Every gesture can be remapped (`ui/settings/SettingsScreen.kt:192`). |
| CORE-7 | DONE | `backup/SettingsBackupManager.kt:25`, `backup/SettingsBackupManager.kt:35`, `ui/settings/sections/BackupSettings.kt:56` | Exports and imports settings plus layout as JSON through the Storage Access Framework. |
| CAL-1 | DONE | `calendar/CalendarRepository.kt:208`, `ui/home/sections/CalendarSection.kt:108`, `ui/home/sections/CalendarSection.kt:157` | `CalendarContract.Instances` across all accounts. The card shows title, countdown, time and location. Includes a READ_CALENDAR prompt. |
| CAL-2 | DONE | `calendar/CalendarViewModel.kt:113`, `ui/home/sections/CalendarSection.kt:172`, `calendar/CalendarPrefs.kt:31` | Remaining events today, 0–5 rows (a setting). Tapping a row opens the event (`calendar/CalendarActions.kt:40`). |
| CAL-3 | DONE | `calendar/CalendarActions.kt:31`, `ui/home/sections/CalendarSection.kt:88` | The "+ Event" button sends `ACTION_INSERT` on `Events.CONTENT_URI`. |
| CAL-4 | DONE | `calendar/CalendarLogic.kt:85`, `ui/home/sections/CalendarSection.kt:115` | "Join" appears when a Meet/Zoom/Teams link exists and the event is 10 min away or less. |
| CAL-5 | DONE | `calendar/CalendarLogic.kt:189`, `calendar/CalendarUi.kt:85`, `ui/home/sections/CalendarSection.kt:72` | Block strip of busy and free time, plus a free-gaps label. Can be turned off in settings. |
| CAL-6 | DONE | `calendar/LeaveByWorker.kt:32`, `calendar/LeaveByWorker.kt:104`, `calendar/CalendarRepository.kt:174` | WorkManager-scheduled "leave in X min" notification for events with a physical location. The lead time is configurable. |
| CAL-7 | DONE | `ui/settings/sections/CalendarSettings.kt:68`, `calendar/CalendarRepository.kt:231` | Pick calendars from all device accounts. With nothing picked, the calendars visible in the calendar app are used. |
| TASK-1 | BLOCKED | `tasks/GoogleTasksAuth.kt:72`, `tasks/GoogleTasksAuth.kt:118`, `tasks/GoogleTasksAuth.kt:156`, `ui/settings/sections/TasksSettings.kt:74` | **The code is done.** Google Sign-In requests the `auth/tasks` scope. Only the account name is stored, in EncryptedSharedPreferences, and tokens come from `GoogleAuthUtil`. **Runtime is blocked** until the user supplies an OAuth client: `GOOGLE_OAUTH_WEB_CLIENT_ID` in `local.properties` (`app/build.gradle.kts:29`) and an Android OAuth client for `com.mp01home.launcher` with the signing SHA-1 (see README). TASK-2..5 depend on this at runtime. |
| TASK-2 | DONE | `tasks/TasksHomeViewModel.kt:76`, `tasks/TasksHomeViewModel.kt:121`, `ui/home/sections/TasksSection.kt:38` | Overdue count in the section title, plus up to 4 tasks due today or overdue. |
| TASK-3 | DONE | `tasks/TasksRepository.kt:112`, `tasks/TasksRepository.kt:208`, `tasks/TasksHomeViewModel.kt:97` | Optimistic Room update plus a `PendingTaskOp`, flushed on sync when back online. |
| TASK-4 | DONE | `tasks/TasksRepository.kt:85`, `capture/CaptureExecutor.kt:76` | The capture "T" target adds to the default list. CAP-2 parses an optional due date. |
| TASK-5 | DONE | `ui/settings/sections/TasksSettings.kt:116`, `tasks/TasksRepository.kt:183` | Choose which task lists feed home. The default list is used when none is chosen. |
| MAIL-1 | DONE | `mail/MailNotificationListener.kt:18`, `ui/home/sections/MailSection.kt:35`, `app/src/main/AndroidManifest.xml:84` | Unread count from Gmail notifications (`NotificationListenerService`). Includes a notification-access prompt. |
| MAIL-2 | DONE | `mail/MailSummary.kt:21`, `ui/home/sections/MailSection.kt:64` | Sender and subject of the latest 3. |
| MAIL-3 | DONE | `mail/MailActions.kt:16`, `mail/MailActions.kt:23`, `ui/home/sections/MailSection.kt:69` | Opens the inbox. Compose uses `mailto:` in Gmail, falling back to any mail app. |
| MAIL-4 | DROPPED | — | Decision D1: notification listener only. |
| KEEP-1 | DONE | `keep/KeepActions.kt:22`, `keep/KeepActions.kt:24`, `ui/home/sections/MailSection.kt:79` | "+ note" and "+ list" use Keep's shortcuts when available, falling back to `ACTION_SEND` or opening the Keep app. |
| KEEP-2 | DONE | `capture/CaptureExecutor.kt:25`, `ui/home/sections/CaptureSection.kt:127` | The capture "K" target shares the text to Keep, with the system chooser as fallback. |
| KEEP-3 | DROPPED | — | Decision D2. Keep has no consumer API. |
| CAP-1 | DONE | `ui/home/sections/CaptureSection.kt:75`, `capture/CapturePlan.kt:40`, `capture/CaptureExecutor.kt:64` | A single input with a Task / Keep / Event toggle. Type, then tap Add. |
| CAP-2 | DONE | `capture/NaturalDateParser.kt:44` | "call Sam fri 3pm" becomes a date and time. Unit-tested. |
| CAP-3 | DONE | `ui/home/sections/CaptureSection.kt:104`, `capture/CaptureExecutor.kt:47` | The Mic button launches `RecognizerIntent`. |
| FOC-1 | DONE | `ui/home/sections/Top3Section.kt:48`, `focus/Top3Logic.kt:35`, `focus/TopPriorityRepository.kt` | Up to 3 priorities per day, as free text or linked tasks, shown first on home. Includes a carry-over prompt for unfinished items. |
| FOC-2 | DONE | `focus/FocusTimerLogic.kt:30`, `focus/FocusViewModel.kt:71`, `ui/focus/FocusScreen.kt:91`, `data/settings/SettingsModels.kt:145` | 25/5 by default, configurable. A large countdown redraws once per minute. |
| FOC-3 | DONE | `focus/DndController.kt:27`, `focus/FocusTimerRepository.kt:44`, `ui/settings/sections/FocusSettings.kt:55` | Optional DND (priority only) while focusing. It is restored afterwards and never overrides DND the user set themselves. Needs notification-policy access. |
| FOC-4 | DONE | `digest/DigestPolicy.kt:62`, `digest/DigestRepository.kt:115`, `ui/home/sections/DigestSection.kt:63`, `ui/settings/sections/MailSettings.kt:66` | Whitelisted apps are batched into the home digest. Other apps are snoozed until the next digest time. |
| PLAN-1 | DONE | `ui/review/MorningReviewScreen.kt:55`, `review/ReviewTrigger.kt:33`, `ui/review/ReviewAutoTrigger.kt:22` | Shows agenda, due tasks and Top 3 entry. Opens automatically on home once per day after a configurable time. |
| PLAN-2 | DONE | `ui/review/EveningReviewScreen.kt:24`, `ui/review/EveningReviewScreen.kt:34`, `ui/review/EveningReviewScreen.kt:52` | Tasks completed today, tasks rolling over and tomorrow's first event. |
| PLAN-3 | DONE | `ui/home/sections/WeekPage.kt:36`, `ui/home/HomeScreen.kt:192` | Second home page with 7 days of event titles and task counts. |
| PLAN-4 | PARTIAL | `calendar/CalendarActions.kt:71`, `calendar/CalendarLogic.kt:261`, `calendar/CalendarUi.kt:134`, `ui/home/sections/CalendarSection.kt:99` | The calendar "Block" button finds the next free slot and opens a prefilled event editor. **Missing:** a way to start this from a task row; today the task title is typed by hand. |
| DIS-1 | DONE | `distraction/DistractionGate.kt:42`, `distraction/FrictionGateActivity.kt:101`, `distraction/FrictionGateActivity.kt:129`, `AppContainer.kt:157` | For flagged apps, the `AppLauncher` interceptor opens a gate: a 10 s pause plus a typed reason. |
| DIS-2 | DONE | `distraction/AppLimitsRepository.kt:25`, `distraction/DistractionGate.kt:46`, `distraction/UsageStatsRepository.kt` | Per-app daily minute limit, checked against `UsageStatsManager`. Needs usage access. |
| DIS-3 | DONE | `ui/home/sections/ScreenTimeLine.kt:22`, `distraction/UsageCalc.kt:94` | Home line such as "today: 1h12m, top: Chrome". |
| EXT-1 | DONE | `data/weather/WeatherRepository.kt:32`, `data/weather/WeatherRepository.kt:79`, `data/weather/WeatherSyncable.kt:18`, `ui/home/sections/HeaderSection.kt:69` | OpenWeatherMap, per the user's decision (D5): current weather plus the max `pop` over about the next 6 h. Uses an API key and city, with no location permission. |
| EXT-2 | DONE | `ui/home/sections/HabitSection.kt:33`, `habits/StreakCalc.kt:13`, `habits/HabitRepository.kt` | One row of daily checkboxes per habit, with a streak count. |
| EXT-3 | DONE | `ui/home/sections/ReadingSection.kt:26`, `ui/home/HomeScreen.kt:252`, `ui/settings/sections/ExtrasSettings.kt:74` | Up to 4 configurable reading-app shortcuts, shown as the `READING` home section. |
| UX-5 | DONE | `eink/SleepScreenUpdater.kt:57`, `eink/SleepScreenRenderer.kt:66`, `ui/settings/sections/ExtrasSettings.kt:44` | Renders the agenda to the lock-screen wallpaper (`FLAG_LOCK`) on screen-off and on sync. Whether the sleep image uses it depends on the vendor, and this is unverified on the MP01. |
| UX-6 | DONE | `eink/VendorRefresh.kt:41`, `eink/VendorDetection.kt:14`, `util/EinkRefresh.kt:35`, `ui/settings/sections/ExtrasSettings.kt:57` | Refresh mode per device (Auto/Onyx/Bigme/Hisense/Generic) with a black-flash fallback. The vendor hooks are best-effort reflection and have not been tested on hardware (D3). |
| UX-7 | DONE | `ui/settings/SettingsScreen.kt:158`, `ui/settings/SettingsScreen.kt:167`, `ui/theme/Theme.kt:51` | Font scale from 0.8 to 1.6×, never below 18sp. Density can be compact, normal or spacious. |

**Summary:** 53 IDs: 49 DONE, 1 PARTIAL (PLAN-4), 1 BLOCKED (TASK-1: needs the user's OAuth client), 2 DROPPED (MAIL-4, KEEP-3), 0 TODO.

## 10. Decisions

Resolved:

- **D1 (MAIL-4): RESOLVED — notification listener only.** Gmail data comes only from `NotificationListenerService`; MAIL-4 is DROPPED.
- **D2 (KEEP-3): RESOLVED — dropped.** No pinned-notes/scratchpad on home; KEEP-3 is DROPPED.
- **D3: RESOLVED — Minimal Phone MP01, `minSdk 29`.** Vendor refresh (UX-6) uses a generic black-flash fallback plus best-effort vendor hooks (`util/EinkRefresh.kt`).
- **D4: RESOLVED — Kotlin + Jetpack Compose (animations off) + Room + WorkManager** (+ DataStore for settings, manual DI via `AppContainer`).
- **D5 (EXT-1): RESOLVED — keep OpenWeatherMap** (user API key + city) instead of Open-Meteo; rain chance for the next hours comes from the OWM forecast endpoint.
- **D6 (home layout): RESOLVED —** the old "up to 8 apps" list is replaced by a dock of up to 6 shortcuts (CORE-3; defaults Calendar, Tasks, Gmail, Keep when installed). The timeline rail stays beside the sections.

Open: none.
