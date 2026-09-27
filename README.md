# MP01 Home

A minimal, text-based Android home launcher built for the **Minimal Phone MP01** (Minimal Company) and its eInk display.

## Features

A non-scrolling, paged "daily sheet" home made of sections. Each section can be switched on or off in **Settings → Home sections**. Per-feature status is in [docs/SPEC.md §9](docs/SPEC.md#9-status-tracker).

- **Header:** time (12/24-hour), date, battery % and weather. Each part is optional.
- **Top 3:** up to three daily priorities, as free text or linked to a task. The next day offers to carry over unfinished items.
- **Calendar:** reads all device accounts through `CalendarContract`. Shows:
  - the next event with a countdown and location;
  - today's agenda, one tap to open an event;
  - a "Join" button for Meet/Zoom/Teams links 10 minutes or less before start;
  - a free/busy strip;
  - "+ Event" and "Block" (book the next free slot).

  Optional extras: a leave-by notification and a choice of which calendars to show.
- **Google Tasks:**
  - overdue count plus the tasks due today;
  - tick a task to complete it (optimistic, queued while offline);
  - choose which task lists feed home;
  - quick add from capture.
- **Gmail:** unread count and the latest 3 senders and subjects, read from Gmail notifications (no Gmail API). Buttons open the inbox or compose a mail.
- **Keep:** "+ note" / "+ list" shortcuts. Quick capture can also send text to Keep.
- **Quick capture:** one input with a Task / Keep / Event target toggle. It understands natural dates ("call Sam fri 3pm") and has a voice mic.
- **Focus:**
  - a Pomodoro timer (default 25/5), whose large countdown redraws once per minute;
  - optional Do Not Disturb while it runs;
  - a notification digest that holds non-whitelisted apps until set times.
- **Reviews:** a morning review (agenda, due tasks, set Top 3) and an evening review (done today, rolling over, tomorrow's first event). Each opens automatically once a day.
- **Week page:** a second home page with 7 days of events and task counts.
- **Distraction control:**
  - hide apps from the drawer;
  - a friction gate (10 s pause plus a typed reason) for flagged apps;
  - daily per-app minute limits;
  - a screen-time line on home.
- **Extras:**
  - habit tracker with streaks;
  - reading-app shortcuts;
  - an agenda drawn onto the lock-screen wallpaper for the e-ink sleep screen;
  - JSON backup and restore of settings and layout.
- **Dock:** up to 6 text shortcuts at the bottom. The defaults are Calendar, Tasks, Gmail and Keep, when installed. Add apps by long-pressing them in the **app drawer** (swipe up; paged A–Z list with search). Long-press a dock entry to remove it.
- **Timeline rail:** a vertical 24-hour rail with a marker for the current time.
- **Weather:** optional, via [OpenWeatherMap](https://openweathermap.org/api). Uses a manual city and your own free API key, with no location permission. Shows current conditions plus the rain chance for the next hours.
- **Gestures** (all configurable):
  - swipe up opens the drawer;
  - swipe down focuses quick capture;
  - double-tap does a full e-ink refresh;
  - long-press opens Settings;
  - swipe left/right changes page.
- **E-ink care:**
  - no animations or ripples, pure black on white;
  - minute-level updates only;
  - text size and spacing settings;
  - a full refresh every N screen updates;
  - vendor refresh modes (Onyx / Bigme / Hisense / generic);
  - background sync every 15–30 minutes and on screen-on, with a local cache and "synced HH:MM" labels.
- Registers as a real Android **HOME launcher** and can ask for the default-launcher role from Settings.

## Project layout

Single-module Android app, Kotlin + Jetpack Compose, `minSdk 29`.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for packages, the home section-slot
convention, `AppContainer`, sync and settings.

## Building

Requires the Android SDK (not just a JDK) — install Android Studio, or the command-line SDK tools, and point `ANDROID_HOME`/`local.properties` at it. Then:

```
./gradlew assembleDebug
```

Install on a device/emulator:

```
./gradlew installDebug
```

To make it the default home screen, open the app and long-press empty space to reach **Settings → Launcher → Set as default launcher**, or set it manually via Android's own Settings → Apps → Default apps → Home app.

> **Build status:** this code has not been built with Gradle or tested on a device yet. The
> environment it was written in had no Android SDK. Expect to fix small compile issues on the
> first `./gradlew assembleDebug`.

## Google sign-in (Tasks)

Google Tasks uses Google Sign-In with the `https://www.googleapis.com/auth/tasks` scope. Only the account name is stored, in EncryptedSharedPreferences. Tokens come from Google Play services. One-time setup in the [Google Cloud console](https://console.cloud.google.com/):

1. Create or pick a project, then enable the **Google Tasks API** under *APIs & Services → Library*.
2. Configure the **OAuth consent screen** and add your Google account as a test user while the app is in testing.
3. Create an OAuth client of type **Web application**. Put its client id in `local.properties`, which git ignores. The build exposes it as `BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID`:

   ```
   GOOGLE_OAUTH_WEB_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
   ```

4. Create an OAuth client of type **Android**:
   - package name `com.mp01home.launcher`;
   - the **SHA-1** of the key that signs your build.

   For debug builds, get the SHA-1 with `./gradlew signingReport` or `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`.
5. Rebuild, then sign in under **Settings → Google Tasks**.

Without these steps, the Tasks section shows a setup hint and the rest of the launcher still works.

## Permissions and special access

Grant each permission from the matching Settings section; each shows a button when access is missing.

| Access | Needed for | Where |
|---|---|---|
| Calendar (`READ_CALENDAR`) | Calendar section, week page, reviews, sleep-screen agenda | Prompt in the Calendar section or **Settings → Calendar** |
| Notification access | Gmail unread/top messages, notification digest | **Settings → Mail & notifications** (Android: *Special app access → Notification access*) |
| Usage access | Daily app limits, screen-time line | **Settings → Distraction** (Android: *Special app access → Usage access*) |
| Do Not Disturb access | DND during focus | **Settings → Focus timer** (Android: *Special app access → Do Not Disturb access*) |
| Notifications (`POST_NOTIFICATIONS`, Android 13+) | Leave-by alerts, focus timer end notice | Asked when enabling leave-by alerts in **Settings → Calendar** |
| Set wallpaper | Sleep-screen agenda (lock-screen image) | Granted at install |

## Weather setup

Get a free API key at [openweathermap.org/api](https://openweathermap.org/api), then enter it along with your city name under **Settings → Weather**.

## Roadmap

See [docs/SPEC.md](docs/SPEC.md) for the feature spec and build status tracker.
