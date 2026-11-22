# Sattune — Minimal Offline Desktop Music Player

> *Just you and your music.*

Sattune is a lightweight, elegant desktop music player built entirely with native Java Swing. It has no cloud dependencies, no telemetry, and no bloat — just clean local music playback.

---

## Screenshots

The interface uses an almost-black dark palette (`#090909`) with subtle purple accents (`#A78BFA`).

| View | Description |
|------|-------------|
| Home | Recently played · Recently added · Favourites |
| Library | Searchable, sortable song table |
| Playlists | Create / rename / delete playlists, drag songs |
| Settings | App info, library stats |

---

## Features

- 🎵 **Play MP3, FLAC, WAV, OGG, AAC, M4A** files locally
- 📂 **Import files or entire folders**
- 🔀 **Shuffle & Repeat** (off / one / all)
- ♥  **Favourites** — mark and browse favourite tracks
- 🕐 **Recently Played** history (up to 50 tracks)
- 📋 **Playlists** — create, rename, delete, add/remove songs
- 🔍 **Search** library by title, artist, or album
- 💾 **Persistent library** — data saved between sessions as JSON
- ⚡ **Low RAM & CPU** — lazy artwork loading, capped LRU image cache

---

## Requirements

- **Java 17+** (OpenJDK or Oracle JDK)
- Linux, macOS, or Windows

---

## Running

```bash
# Build
mvn clean package -q

# Run
java -jar target/sattune-1.0.0.jar
```

Or use the provided launch script:

```bash
./run.sh
```

---

## Project Structure

```
src/main/java/com/sattune/
├── SatTune.java               # Entry point
├── audio/
│   ├── AudioEngine.java       # Low-level PCM decode + SourceDataLine playback
│   └── PlayerController.java  # Queue, shuffle, repeat, events
├── model/
│   ├── Song.java
│   ├── Playlist.java
│   └── AppData.java           # Root persistence model
├── service/
│   ├── LibraryService.java    # In-memory library + listeners
│   ├── MetadataService.java   # JAudioTagger-based file scanner
│   └── PersistenceService.java# Gson JSON load/save
├── ui/
│   ├── MainWindow.java        # Root JFrame + navigation wiring
│   ├── sidebar/Sidebar.java
│   ├── player/PlaybackBar.java
│   └── content/
│       ├── HomeView.java
│       ├── LibraryView.java
│       ├── PlaylistsView.java
│       ├── RecentView.java
│       ├── FavouritesView.java
│       └── SettingsView.java
└── util/
    ├── Theme.java             # Design tokens (colors, fonts, dimensions)
    ├── UiUtils.java           # Component factory helpers
    └── ArtworkCache.java      # LRU-cached embedded artwork loader
```

---

## Data Storage

User data is stored as a single `library.json` file:

| OS | Location |
|----|----------|
| Linux | `~/.local/share/SatTune/library.json` |
| macOS | `~/Library/Application Support/SatTune/library.json` |
| Windows | `%APPDATA%\SatTune\library.json` |

Music files are **not** copied — only their paths are recorded.

---

## Packaging (jpackage)

Requires the full JDK (not just JRE).

### Linux (DEB)
```bash
jpackage \
  --type deb \
  --name sattune \
  --app-version 1.0.0 \
  --input target \
  --main-jar sattune-1.0.0.jar \
  --main-class com.sattune.SatTune \
  --dest dist/linux
```

### Windows (EXE installer)
```bash
jpackage \
  --type exe \
  --name Sattune \
  --app-version 1.0.0 \
  --input target \
  --main-jar sattune-1.0.0.jar \
  --main-class com.sattune.SatTune \
  --win-menu \
  --win-shortcut \
  --dest dist/windows
```

> **Note:** `jpackage` bundles a JRE so users do not need Java installed. Run from the same OS/architecture you are targeting.

---

## Technology Stack

| Component | Library |
|-----------|---------|
| GUI | Java Swing |
| Theme | FlatLaf 3.4.1 |
| Audio decoding | Java Sound API (SPI) + JLayer |
| Metadata | JAudioTagger 3.0.1 |
| Persistence | Gson 2.10.1 |
| Build | Maven 3.9+ |

---

## Design Philosophy

- Almost-black background (`#090909`)
- Minimal typography — no oversized headings
- Thin separators instead of heavy borders
- Accent color (`#A78BFA`) only on small interactive details
- Artwork loaded lazily — only the visible card/row
- LRU image cache capped at 64 entries
- Playback runs on a dedicated daemon thread, never blocking the EDT

---

*Sattune — Less UI. Less animation. Less RAM. More music.*
