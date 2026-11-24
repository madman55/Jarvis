# Jarvis

A personal phone assistant for Android, built in stages.

**Look:** a dark HUD home screen: animated core with clock, live storage / power / memory gauges, and every section wired to the core.
**Music:** search the songs on your phone and play them, or send a search straight to your music app (chosen in Settings).
**Apps:** search and open any app on the phone.

**Stage 1: audiobook cleanup.** Jarvis scans Downloads for audiobook files, flags the ones already in your Smart AudioBook Player library and the leftovers from deleted books, and moves whatever you pick into a "Jarvis Archive" folder. Archived items can be restored for 30 days and are then deleted for good.

## Installing on your phone

1. Open the [latest release](../../releases/latest) on your phone and tap **Jarvis.apk**.
2. Open the download. The first time, Android asks you to allow installs from your browser. Allow it.
3. Open Jarvis, tap **Grant access**, and switch on "Allow access to manage all files".

Every push to `main` builds a new APK automatically (see the Actions tab). New versions install over the old one.

## Roadmap

- [x] Stage 1: audiobook leftovers + safe archive
- [ ] Stage 2: index everything (photos, music, audiobooks, documents) + instant search
- [ ] Stage 3: talk to it: Claude turns requests into actions, previewed before they run
- [ ] Stage 4: proactive: tracks what you use, suggests clean-ups weekly

## How it's put together

| File | What it does |
|---|---|
| `Scanner.kt` | Walks Downloads, groups audio files into books, matches them against your library |
| `Archive.kt` | Moves things into the archive, restores them, deletes after 30 days |
| `Music.kt` / `Apps.kt` | Reads your music library; lists, opens and sends songs to apps |
| `MediaScreens.kt` | The Music and Apps tabs, and the music-app picker |
| `Prefs.kt` | Remembers your folder choices |
| `Screens.kt` | The Clean up, Archive and Settings tabs |
| `MainActivity.kt` | App start-up, permission check, theme |
