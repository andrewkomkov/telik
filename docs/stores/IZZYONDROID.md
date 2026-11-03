# IzzyOnDroid request

IzzyOnDroid takes requests as issues in the Codeberg repository `IzzyOnDroid/repo`. The requester needs a Codeberg account.
The repository reads the APK from the GitHub releases, so no build recipe is necessary.

## Steps

1. Sign in to Codeberg.
2. Open a new issue in `IzzyOnDroid/repo` with the title below.
3. Paste the body below.

## Title

```
Telik (dev.telik.tv)
```

## Body

```
Name: Telik
Package: dev.telik.tv
Source: https://github.com/andrewkomkov/telik
License: GPL-3.0-only
APK: https://github.com/andrewkomkov/telik/releases (asset telik-*-armv7.apk, signed with the release key of the project)
Size: about 11 MB
Device: Android TV 13 or newer (minSdk 33), armeabi-v7a

Telik makes an Android TV work with the iPhone: the Apple TV Remote in the Control Center, the iPhone keyboard,
AirPlay mirroring and audio, and a TV tile in Apple Home. It can also be the TV Home screen.

Fastlane metadata: fastlane/metadata/android (en-US, ru), with TV screenshots.
Privacy: no analytics, no ads, no account. Policy: https://andrewkomkov.github.io/telik/privacy.html

Notes for the review:
- No proprietary libraries and no Google Play services.
- An accessibility service presses the TV buttons for the iPhone remote and types text from the iPhone keyboard.
- Protected video (DRM) does not play over AirPlay. Telik does not bypass DRM.
- The app is not affiliated with Apple or Google.
```
