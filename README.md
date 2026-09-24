# MP01 Home

A minimal, text-based Android home launcher built for the **Minimal Phone MP01** (Minimal Company) and its eInk display.

## Features

- **Up to 8 apps**, listed top to bottom as plain text, aligned left or right (Settings).
- **Timeline rail**: a vertical 24-hour rail with a marker for the current time. Toggle it on/off in Settings; when shown, it always renders on the side opposite the app list (it swaps automatically if you change app alignment).
- **Time**: optional, 12-hour or 24-hour format.
- **Date**: optional, switchable between ISO / US / EU / long formats.
- **Weather**: optional, via [OpenWeatherMap](https://openweathermap.org/api)'s current-weather endpoint (manual city entry + your own free API key; no location permission needed).
- Apps are added only from the **app drawer** (swipe up from Home) by long-pressing an app and choosing "Add to Home" — empty slots are never shown. Long-press an app already on Home to remove it. Long-press empty space on Home to open Settings.
- Registers as a real Android **HOME launcher** and can request the default-launcher role from Settings.
- No animations, pure black-on-white (or white-on-black) theme, minute-granularity clock updates — tuned to avoid eInk ghosting from frequent partial refreshes.

## Project layout

Single-module Android app, Kotlin + Jetpack Compose, `minSdk 29`.

```
app/src/main/java/com/mp01home/launcher/
  MainActivity.kt, MP01LauncherApp.kt      Entry point + Compose NavHost
  ui/home/       Home screen (app list + timeline rail + header)
  ui/drawer/     App drawer (swipe up from Home)
  ui/settings/   Settings screen
  ui/components/ TimelineRail, HeaderInfo, dialogs
  ui/theme/      Pure black/white eInk theme, no ripple/animation
  data/settings/ DataStore-backed settings repository + models
  data/apps/     PackageManager-backed installed-apps repository
  data/weather/  OpenWeatherMap client + local cache
  util/          Minute-granularity clock ticker
```

## Building

Requires the Android SDK (not just a JDK) — install Android Studio, or the command-line SDK tools, and point `ANDROID_HOME`/`local.properties` at it. Then:

```
./gradlew assembleDebug
```

Install on a device/emulator:

```
./gradlew installDebug
```

To make it the default home screen, open the app and long-press empty space to reach **Settings → Launcher → Set as Default Launcher**, or set it manually via Android's own Settings → Apps → Default apps → Home app.

## Weather setup

Get a free API key at [openweathermap.org/api](https://openweathermap.org/api), then enter it along with your city name under **Settings → Weather**.

## Roadmap

See [docs/SPEC.md](docs/SPEC.md) for the feature spec and build status tracker.
