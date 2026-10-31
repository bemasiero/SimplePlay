# SimplePlay

A very simple IPTV player for Android TV.

1. **Add your playlist** – paste an M3U URL (and optionally an XMLTV EPG URL).
2. **Pick channels** – tick the ones you want (search box helps with big lists).
3. **Watch** – the app opens straight into the last channel you watched.

## Remote control

| Key | Action |
| --- | --- |
| ▲ / ▼ (or CH+ / CH−) | Next / previous channel |
| OK | Show now/next info · press again for the channel list |
| ◀ | Channel list (with what's on now) |
| ☰ Menu | Settings (or the *Settings* button at the bottom of the channel list) |
| Back | Hide overlay · exit |

## EPG

XMLTV (`.xml` or `.xml.gz`). If you leave the EPG field empty, the `url-tvg` from the
playlist header is used. Channels are matched by `tvg-id`, falling back to the channel
name. The guide is cached for 12 hours.

## Build

```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Every push also builds it on GitHub
Actions (download it from the run's *Artifacts*). Install with
`adb install app-debug.apk`, or sideload it with an app like *Downloader*.

Built with Kotlin + AndroidX Media3 (ExoPlayer): plays HLS (`.m3u8`) and MPEG-TS streams.
