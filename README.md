# Slate Launcher

A lightweight, original Android launcher in the style of Niagara: a minimal text list,
favorites on the home screen, an A-Z rail, gestures, badges and icon packs.
Native Kotlin, framework views only, zero third-party dependencies, no internet permission.

## Open and run
1. Install Android Studio (current stable).
2. File > Open > select this folder. Let Gradle sync (it downloads Gradle 8.7 and the Android SDK 34 pieces).
3. Plug in a phone (USB debugging on) or start an emulator, then press Run.
4. Press Home on the device and choose "Slate Launcher" > "Always".

## Smaller release APK
Build > Generate Signed Bundle / APK, or run `./gradlew assembleRelease` after `gradle wrapper`.
The release build uses R8 minify + resource shrinking. It is signed with the debug key so it installs
directly; swap in your own keystore before sharing it.

## Features
Home: clock and date, favorites list (bottom / center / top), notification badges.
All apps: swipe up or touch the A-Z rail. Search by name, word start, or contains; web-search fallback.
Long press an app: shortcuts, favorite / reorder, rename, hide, app info, uninstall.
Gestures: swipe up (apps), swipe down (notifications or search), swipe left / right (any app you pick),
double tap (lock screen), long press empty space (settings, wallpaper).
Look: dark / light / system, background tint, 6 accents, 7 fonts, 4 sizes, left / center / right alignment,
optional icons, ADW / Nova icon packs.
Extras: hidden apps (optionally searchable), launch on single match, keyboard on open, haptics.

## Optional permissions (all user-granted)
- Notification access: badge counts only. Counts stay in memory, nothing is stored or sent.
- Accessibility service "Slate gestures": only performs "lock screen" and "open notification panel".
  It does not read screen content.

## Why it is light
No AndroidX / Compose / image libraries. Icons are off by default; when on they load lazily into a 40-item cache.
The app list is one ListView with recycled rows. Nothing runs in the background except the two optional services.

## Not included (ideas for next stages)
Weather and media widgets, drag-to-reorder favorites (use Move up / Move down), backup / restore, per-app icon overrides.
