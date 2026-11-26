# MeeTube (fork)

> **Fork notice:** a fork of [MeeTube](https://github.com/yashoncode/meetube) by [yashoncode](https://github.com/yashoncode), under the same GPLv3 license. All credit for the original app goes to him. In-app updates in this fork come from this repository's own releases.

> [!IMPORTANT]
> Not affiliated with, endorsed by, or connected to YouTube or Google. Use it at your own discretion.

A YouTube video and music client in one app: **MeeTube** for videos and **MeeTune**, its music side, one tap away. No ads, a liquid-glass interface, synced lyrics, background playback and downloads.

## Features

- **MeeTune:** YouTube Music with synced lyrics, an equalizer, spatial sound, crossfade and Android Auto.
- **Player:** gestures, picture-in-picture, sleep timer, SponsorBlock and DeArrow.
- **Feeds:** Home, Shorts, Search, Subscriptions, channels, posts and live chat.
- **Downloads:** up to 4K, with lyrics for songs, playable offline.
- **Interface:** liquid glass or a flat YouTube mode, light and dark themes, full Arabic and RTL support.

## Download

Get the APK from [Releases](https://github.com/slman14422003-create/meetube/releases/latest). Android 7.0 or later.

| Your device | File |
|:--|:--|
| Most phones (64-bit) | `MeeTube_<version>_arm64-v8a.apk` |
| Older phones (32-bit) | `MeeTube_<version>_armeabi-v7a.apk` |
| Not sure | `MeeTube_<version>_universal.apk` |

## Updates

The app checks `releases/latest/download/meetube.json` of this repository (Settings → Check for updates).

To publish an update:

1. Open **Actions → Release → Run workflow**.
2. Type the version (for example `4.2.0`; the version code is worked out from it) and keep **Publish** ticked.
3. The workflow builds the APKs and uploads them with `meetube.json` as the latest release. Installed apps then offer the update.

Pushing a `vX.Y.Z` tag does the same, once `build.gradle` and the top of `CHANGELOG.md` match that version.

Every update must be signed with the same key, or phones refuse it. Add the secrets `SIGNING_KEY` (the `.jks`, base64), `KEY_STORE_PASSWORD`, `ALIAS` and `KEY_PASSWORD` under **Settings → Secrets and variables → Actions**. Without them the APKs are debug-signed.

## Build

```
./gradlew :mobile:assembleStmobileRelease
# APKs: mobile/build/outputs/renamed_apks/stmobileRelease/MeeTube_<version>_<abi>.apk
```

Clone with `--recurse-submodules`, or let the release workflow fetch `MediaServiceCore` and `SharedModules`.

## Credits

[MeeTube](https://github.com/yashoncode/meetube) by yashoncode, built on [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov and [NewTube](https://github.com/aleixrodriala/newtube) by aleixrodriala. Headphone correction uses [AutoEq](https://github.com/jaakkopasanen/AutoEq) profiles.

## License

GNU General Public License v3.0, see [LICENSE](LICENSE). Code from SmartTube and NewTube keeps its MIT notice; every other notice is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
