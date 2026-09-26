# Edge

An Android launcher that drives the phone the way Ubuntu Touch (Lomiri) does: no Back, Home or Recents buttons, everything from the screen edges. Built for the Nothing Phone (3a); needs no root.

| Edge | Gesture |
|---|---|
| Left | Lomiri launcher panel. Pips mark running apps (left) and the app in front (right). Keep dragging to pull out the app drawer. Orange button: tap for the drawer, long-press for home. |
| Right, short | The current app tilts away and the previous one slides in; release to switch. |
| Right, long (past 40% of width) | The Lomiri spread of running apps, with live previews. Tap a card to open, flick up to close. |
| Bottom | App drawer with instant search. |
| Top | Lomiri indicator panel: pull down under the indicator you want (notifications, network, Bluetooth, sound, battery & brightness, date). Slide sideways while pulling to switch. |
| Home screen | Clock and a screen-time infographic ring. Double-tap to lock, swipe up for the drawer, long-press for settings and wallpaper. |

## Install

1. On the phone, open <https://github.com/buildwclaude/edge-launcher/releases/tag/latest> (signed in to GitHub) and download `edge.apk`.
2. Open it and allow your browser or file manager to install apps.
3. Open **Edge Settings** from your current launcher; setup starts automatically.
4. The accessibility switch will be greyed out ("Restricted setting") because the app was sideloaded: tap **App info** in setup, then ⋮ → **Allow restricted settings**, go back and switch it on.
5. Grant usage access, set battery to Unrestricted, set Edge as the default home app.
6. Phone settings: switch to gesture navigation and lower the Back sensitivity.

Or with adb: `adb install -r edge.apk`. Installing with adb doesn't trigger the restricted-settings block.

Every push to `main` builds an optimised (R8, non-debuggable) APK in GitHub Actions and replaces the `latest` release. All builds share one signing key (`keystore/`), so updates install over the old version and keep your settings.

## Known limitations

- **Switcher previews** are screenshots Edge takes through the accessibility service (Android 11+) when an app settles and when you touch an edge. Apps that block screenshots (banking, DRM video) show black cards; apps not seen since Edge started show their icon.
- **Nothing OS's own status bar and Settings app can't be restyled** without root. The indicator panel is drawn by Edge; Wi-Fi, mobile data, Bluetooth and flight mode can only be opened (Android doesn't let apps switch them), while volume, silent mode, Do Not Disturb, brightness, rotation lock and the flashlight work in place.
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
