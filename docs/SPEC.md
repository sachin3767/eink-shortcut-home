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
| CORE-3 | P0 | Dock | 4–6 user-configurable shortcuts (defaults: Calendar, Tasks, Gmail, Keep). |
| CORE-4 | P0 | App drawer | Text-only A–Z list, paged, with search box; opened by swipe up. |
| CORE-5 | P1 | Hide apps | Apps can be hidden from the drawer. |
| CORE-6 | P1 | Gestures | Swipe up = drawer, swipe down = quick capture, double-tap = full refresh. Configurable. |
| CORE-7 | P2 | Layout backup | Export/import settings + layout as JSON. |
| CORE-8 | P1 | Timeline rail | Vertical 24-hour rail with a live current-time marker; toggleable in Settings; always renders on the side opposite the app list, swapping automatically if app alignment changes. *(Added — not in the original spec; built per the original MP01 launcher request.)* |

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
| MAIL-4 | P2 | Gmail API mode | Optional `gmail.readonly` integration for accurate counts/labels (restricted scope — personal/test-user use only). DECISION required. |

### 4.4 Google Keep
| ID | Pri | Requirement | Acceptance criteria |
|---|---|---|---|
| KEEP-1 | P0 | New note / checklist | One-tap launches Keep new-note (via `ACTION_SEND` to `com.google.android.keep` or Keep shortcuts). |
| KEEP-2 | P1 | Send-from-capture | Quick capture (CAP-1) can target Keep via share intent. |
| KEEP-3 | P2 | Pinned notes on home | **Keep has no consumer API.** Alternative: local scratchpad widget with "send to Keep". DECISION required. |

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
| EXT-1 | P1 | Weather | Current conditions via OpenWeatherMap, using a manual city-name entry and a user-supplied API key (no location permission). °C/°F unit toggle. Cached locally with a ~45 min refresh TTL. |
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

| ID | Status | Evidence | Notes |
|---|---|---|---|
| EINK-1 | DONE | `app/src/main/res/values/styles.xml:6`; `app/src/main/java/com/mp01home/launcher/ui/theme/Theme.kt:28-46` | Window animations disabled; ripple/press indication replaced with a no-op; Home's app list is a plain `Column`, not a scrolling list |
| EINK-2 | DONE | `app/src/main/java/com/mp01home/launcher/ui/theme/Color.kt`; `.../ui/theme/Theme.kt:13-20` | Pure black/white color scheme only, no greys |
| EINK-3 | PARTIAL | `app/src/main/java/com/mp01home/launcher/ui/theme/Type.kt` | Body text is 22sp (meets ≥18sp default); no user-configurable text size; row touch-target height not explicitly verified ≥56dp |
| EINK-4 | DONE | `app/src/main/java/com/mp01home/launcher/util/MinuteTicker.kt` | Ticks only on minute boundaries, never per-second |
| EINK-5 | TODO | | No double-tap full-refresh gesture, no "refresh every N updates" setting, no vendor refresh API |
| EINK-6 | PARTIAL | `.../ui/home/HomeViewModel.kt` (`maybeRefreshWeather`); `.../data/weather/WeatherRepository.kt:21` (`CACHE_TTL_MILLIS`) | Lazy TTL-gated (45 min) refresh while Home is on screen, not a scheduled WorkManager job and no explicit screen-on trigger; no persistent foreground service (meets that half of the criterion) |
| EINK-7 | PARTIAL | `.../data/weather/WeatherCacheRepository.kt` | Weather is cached in DataStore and Home renders from cache before a refresh completes; no "synced HH:MM" label shown; no other widgets exist to assess |
| CORE-1 | DONE | `app/src/main/AndroidManifest.xml:25-30`; `.../ui/settings/SettingsScreen.kt:153-155` | HOME/DEFAULT intent filter + `RoleManager.ROLE_HOME` request |
| CORE-2 | PARTIAL | `.../ui/components/HeaderInfo.kt` | Minute-resolution time + full date shown; no battery % |
| CORE-3 | PARTIAL | `.../data/settings/SettingsModels.kt:38` (`MAX_HOME_APPS = 8`) | Built as an 8-slot, no-default, any-app list, not a 4–6 item dock defaulting to Calendar/Tasks/Gmail/Keep — revisit once those integrations exist |
| CORE-4 | PARTIAL | `.../ui/drawer/AppDrawerScreen.kt:46` (`LazyColumn`); `.../ui/home/HomeScreen.kt:58` (swipe-up) | Text-only A–Z list, opened by swipe-up; scrolling rather than paged, and no search box — near-term follow-up |
| CORE-5 | TODO | | Drawer always lists every launchable app; no hide option |
| CORE-6 | PARTIAL | `.../ui/home/HomeScreen.kt:55,58` | Swipe-up → drawer and long-press → remove/settings work; no swipe-down capture, no double-tap refresh, nothing is user-configurable |
| CORE-7 | TODO | | No export/import of settings/layout |
| CORE-8 | DONE | `.../ui/components/TimelineRail.kt`; `.../ui/home/HomeScreen.kt` | Not in the original spec — added per the original MP01 launcher request (see requirement row above) |
| CAL-1 | TODO | | |
| CAL-2 | TODO | | |
| CAL-3 | TODO | | |
| CAL-4 | TODO | | |
| CAL-5 | TODO | | |
| CAL-6 | TODO | | |
| CAL-7 | TODO | | No Calendar integration exists anywhere in the app |
| TASK-1 | TODO | | |
| TASK-2 | TODO | | |
| TASK-3 | TODO | | |
| TASK-4 | TODO | | |
| TASK-5 | TODO | | No Google Tasks integration exists |
| MAIL-1 | TODO | | |
| MAIL-2 | TODO | | |
| MAIL-3 | TODO | | |
| MAIL-4 | TODO | | No Gmail integration exists |
| KEEP-1 | TODO | | |
| KEEP-2 | TODO | | |
| KEEP-3 | TODO | | No Keep integration exists |
| CAP-1 | TODO | | |
| CAP-2 | TODO | | |
| CAP-3 | TODO | | No quick-capture input exists anywhere |
| FOC-1 | TODO | | |
| FOC-2 | TODO | | |
| FOC-3 | TODO | | |
| FOC-4 | TODO | | No Top-3, focus timer, DND, or notification digest exists |
| PLAN-1 | TODO | | |
| PLAN-2 | TODO | | |
| PLAN-3 | TODO | | |
| PLAN-4 | TODO | | No morning/evening review, week page, or time blocking exists |
| DIS-1 | TODO | | |
| DIS-2 | TODO | | |
| DIS-3 | TODO | | No friction gate, app limits, or screen-time line exists |
| EXT-1 | DONE | `.../data/weather/WeatherRepository.kt:20`; `.../ui/settings/SettingsScreen.kt` (city/API-key fields) | Requirement text (above) was rewritten to match a deliberate earlier decision: OpenWeatherMap current-conditions, manual city entry, user-supplied API key — not Open-Meteo/GPS/forecast |
| EXT-2 | TODO | | No habit tracker |
| EXT-3 | TODO | | No reading-list shortcuts |
| UX-5 | TODO | | No sleep-screen agenda rendering |
| UX-6 | TODO | | No vendor-specific refresh-mode handling |
| UX-7 | TODO | | No font-size/density settings (the alignment/timeline toggles we built are a different axis, not this requirement) |

(Split grouped rows into individual IDs as soon as any one of them changes status.)

## 10. Open decisions

- **D1 (MAIL-4):** Use Gmail API with restricted scope, or stay notification-listener only?
- **D2 (KEEP-3):** Local scratchpad vs. dropping pinned-notes-on-home?
- **D3:** Target devices / min SDK (affects UX-5, UX-6).
- **D4:** Tech stack — default assumption Kotlin + Jetpack Compose (animations off) + Room + WorkManager.
