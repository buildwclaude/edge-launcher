# Edge

An Android launcher that drives the phone the way Ubuntu Touch (Lomiri) does: no Back, Home or Recents buttons, everything from the screen edges. Built for the Nothing Phone (3a); needs no root.

| Edge | Gesture |
|---|---|
| Left | Slide out the dock of pinned apps. Running apps have a pip; the orange one is in front. Keep dragging far right to go Home. |
| Right, short | Switch to the previous app. |
| Right, long (past 40% of width) | Spread of running apps. Tap a card to open, flick up to close. |
| Bottom | App drawer with instant search. |
| Top | Left half: notifications. Right half: quick settings. |
| Home screen | Double-tap to lock, swipe up for the drawer, long-press for settings and wallpaper. |

## Install

1. On the phone, open <https://github.com/buildwclaude/edge-launcher/releases/tag/latest> (signed in to GitHub) and download `edge-debug.apk`.
2. Open it and allow your browser or file manager to install apps.
3. Open **Edge Settings** from your current launcher; setup starts automatically.
4. The accessibility switch will be greyed out ("Restricted setting") because the app was sideloaded: tap **App info** in setup, then ⋮ → **Allow restricted settings**, go back and switch it on.
5. Grant usage access, set battery to Unrestricted, set Edge as the default home app.
6. Phone settings: switch to gesture navigation and lower the Back sensitivity.

Or with adb: `adb install -r edge-debug.apk`. Installing with adb doesn't trigger the restricted-settings block.

Every push to `main` builds a new APK in GitHub Actions and replaces the `latest` release. All builds share one debug key (`keystore/`), so updates install over the old version and keep your settings.

## Known limitations

- **No live app thumbnails** in the switcher: Android only gives screenshots of other apps to the system launcher. Cards show the app's icon on its colour instead.
- **Nothing OS's status bar, shade and Settings app can't be restyled** without root. The top edge opens the stock shade (Lomiri-style custom panel is a planned v2).
- **System gestures win at the very edges.** With gesture navigation, a swipe starting in the system's home-bar zone or Back zone goes to Android first. Edge's side strips cover only the upper two-thirds by default and ask Android to exclude them from Back (Android honours up to 200 dp). Tune strip sizes in settings; "Show strips" tints them while you adjust.
- **Aggressive battery management** can kill the accessibility service. Set Edge's battery use to Unrestricted, and if it still stops, lock it in the old launcher's recents. If it dies, turn the service off and on again in Accessibility.
- **"Close app" in the spread** removes the card and asks Android to stop the app's background process. It can't force-stop the foreground app.
- **Full-screen detection** is based on whether the status bar is hidden; swipe the system bars in first when in a video or game, then use the edges.
- The recent-apps list is built from what Edge sees while its service runs, seeded from usage access after a restart.

## Build

```
./gradlew assembleDebug
```
JDK 17 and Android SDK 35. The APK lands in `app/build/outputs/apk/debug/`.

Ubuntu font © Canonical Ltd., under the Ubuntu Font Licence 1.0 (`licenses/UFL.txt`).
