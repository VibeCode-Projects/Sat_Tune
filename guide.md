# Sattune Developer & Architecture Guide

Welcome to **Sattune** — a lightweight, fast, offline desktop music player built in Java Swing with FlatLaf.

This guide provides a comprehensive overview of Sattune's architecture, audio decoding pipeline, UI design system, and code structure.

---

## 1. Project Philosophy & Design Goals

1. **100% Offline & Self-Contained**: No external network dependencies, analytics, or required background services.
2. **Minimal Footprint**: Keeps memory usage low (~25–45 MB heap) through buffered streaming and bounded LRU caching.
3. **Format Reliability**: Never trusts file extensions alone. Accurately identifies containers by inspecting header magic bytes.
4. **Resilient Playback**: Gracefully handles corrupted files, rapid song switching, missing tags, and audio format mismatches without crashing.
5. **Modern Minimalist UI**: Dark aesthetic with subtle purple accents (`#A78BFA`), rounded corners, responsive shelves, and crisp embedded artwork display.

---

## 2. Directory Structure & Key Classes

```
src/main/java/com/sattune/
├── SatTune.java                        # Main application entry point & FlatLaf setup
│
├── audio/                              # Audio playback and decoding subsystem
│   ├── AudioEngine.java                # Multi-format streaming decoder & hardware line writer
│   ├── AudioFormatDetector.java        # Magic-bytes container format identifier
│   └── PlayerController.java           # Playback queue, shuffle, repeat, and volume controller
│
├── model/                              # Data models
│   ├── AppData.java                    # Persisted JSON library & preferences structure
│   ├── Playlist.java                   # Playlist model with song ID references
│   └── Song.java                       # Track model with UUID-sanitization & duration helpers
│
├── service/                            # Application services
│   ├── LibraryService.java             # In-memory song library, playlists, and change events
│   ├── MetadataService.java            # Tag & duration extractor using container-aware readers
│   └── PersistenceService.java         # JSON persistence (~/.sattune/data.json)
│
├── ui/                                 # User interface components
│   ├── MainWindow.java                 # Root JFrame with CardLayout view switcher
│   ├── content/
│   │   ├── FavouritesView.java         # Starred tracks list with artwork thumbnails
│   │   ├── HomeView.java               # Responsive horizontal shelves (Recent, Added, Favs)
│   │   ├── LibraryView.java            # Searchable, sortable song table with artwork
│   │   ├── PlaylistsView.java          # Playlists manager & track listing
│   │   ├── RecentView.java             # Play history list with artwork thumbnails
│   │   └── SettingsView.java           # Theme and library directory preferences
│   ├── player/
│   │   └── PlaybackBar.java            # Bottom persistent player bar & controls
│   └── sidebar/
│       └── Sidebar.java                # Left navigation bar with distinctive active state
│
└── util/                               # Utilities & Design tokens
    ├── ArtworkCache.java               # Bounded LRU artwork loader & placeholder generator
    ├── Theme.java                      # Central color palette and font definitions
    └── UiUtils.java                    # Swing component factory & layout helpers
```

---

## 3. Audio Decoding Pipeline

### 3.1 Supported Formats

Sattune supports the following formats out-of-the-box using pure Java Sound SPI decoders:

| Format | Container / Codec | Primary SPI Provider | Library |
|---|---|---|---|
| **MP3** | MPEG-1/2 Audio Layer III | `javazoom.spi.mpeg.sampled.file.MpegAudioFileReader` | JLayer + MP3SPI |
| **M4A / AAC** | ISO MP4 (`ftyp`) / ADTS AAC | `net.sourceforge.jaad.spi.javasound.AACAudioFileReader` | JAAD |
| **FLAC** | Free Lossless Audio Codec | `org.jflac.sound.spi.FlacAudioFileReader` | jflac-codec |
| **OGG** | Ogg Vorbis | `javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader` | VorbisSPI + JOrbis |
| **WAV** | RIFF PCM / IEEE Float | `com.sun.media.sound.WaveFileReader` | Standard Java Sound |

### 3.2 Content-Based Format Detection (`AudioFormatDetector`)

File extensions are often misleading (e.g., an MP3 file named `.m4a` or an M4A track named `.mp3`). Sattune never relies solely on file extensions. Instead, `AudioFormatDetector` inspects the initial 32 bytes of the file:

- **FLAC**: Header starts with `fLaC` (`0x66 0x4C 0x61 0x43`).
- **OGG**: Header starts with `OggS` (`0x4F 0x67 0x67 0x53`).
- **WAV**: Header starts with `RIFF....WAVE`.
- **M4A / AAC**: Header contains box type `ftyp` at offset 4, or ADTS sync word `0xFFF...`.
- **MP3**: Header starts with `ID3` tag or MPEG sync word `0xFFE...`.

### 3.3 Preventing JAAD NPE on Mismatched Files

Previously, JAAD's `AACAudioFileReader` would attempt to decode non-AAC files as raw ADTS streams, causing `DecoderConfig.getSampleFrequency()` to return `null` and throw an unchecked `NullPointerException`. Standard `AudioSystem.getAudioInputStream()` does not catch `NullPointerException` (only `UnsupportedAudioFileException`), resulting in premature failure.

**Solution in Sattune**:
1. `AudioEngine` queries `AudioFormatDetector.detect(file)` before touching decoders.
2. The decoder matching the verified container is prioritized first.
3. If a decoder fails or throws any `Throwable` (checked or unchecked), resources are immediately released and the next compatible reader is tried in a protected loop.
4. JAAD is never invoked on MP3 or other non-AAC containers.

### 3.4 Hardware Output Compatibility

Decoded audio streams are converted to **signed 16-bit little-endian PCM** at runtime. This guarantees compatibility with modern sound systems (ALSA, PulseAudio, PipeWire, DirectSound, CoreAudio) and prevents static noise, distortion, or silence.

### 3.5 Immediate Non-Blocking Track Switching

When changing tracks or seeking, `AudioEngine.stopInternal()` immediately halts and flushes the active `SourceDataLine` and closes active streams. This avoids blocking on audio buffer drains and ensures track transitions are instantaneous without orphan audio threads.

---

## 4. Evaluation of Decoders: Pure Java vs. FFmpeg

As part of Sattune's audio reliability objective, we evaluated integrating **FFmpeg** as an optional fallback decoder versus using **Pure Java Sound SPI**:

| Criterion | Pure Java Sound SPI (Current) | External FFmpeg Process |
|---|---|---|
| **External Dependencies** | Zero. Self-contained in JAR. | Requires `ffmpeg` installed on host OS or bundled binaries. |
| **Cross-Platform Portability** | Runs anywhere JDK runs (Linux, macOS, Windows). | Requires separate compiled binaries per OS and CPU arch (x86_64, arm64). |
| **Memory Footprint** | ~25 MB heap total. | Spawns separate sub-processes; +50–100 MB RAM per process. |
| **CPU Usage** | Minimal (< 1% on modern CPUs). | Higher process launch & IPC pipe overhead. |
| **Distribution Size** | ~10 MB total shaded JAR. | +70 MB to +150 MB binary distribution size. |
| **Supported Core Formats** | MP3, M4A/AAC, FLAC, OGG, WAV. | All audio formats (including Opus, WMA, AC3). |

**Conclusion**: For desktop music playback of standard formats (MP3, M4A, FLAC, WAV, OGG), pure Java SPI decoders provide complete reliability, instant startup, low memory usage, and zero setup friction for the end user. FFmpeg is not required for these core formats.

---

## 5. UI Architecture & Components

### 5.1 Design Tokens (`Theme.java`)
- `BG_MAIN`: `#090909` (Main window background)
- `BG_SIDEBAR`: `#0D0D0D` (Sidebar background)
- `BG_SECONDARY`: `#141414` (Panels and cards background)
- `BG_HOVER`: `#1B1B1B` (Hover state)
- `BG_SELECTED`: `#241D35` (Active sidebar & list selection — subtle deep purple tint)
- `ACCENT`: `#A78BFA` (Vibrant pastel purple accent)
- `TEXT_PRIMARY`: `#F5F5F5` (High-contrast clean white)
- `TEXT_SECONDARY`: `#888888` (Readable light gray)
- `TEXT_MUTED`: `#555555` (Subtle metadata gray)

### 5.2 Artwork Management (`ArtworkCache.java`)
- **Container-Aware Extraction**: Uses `MetadataService.readAudioFileSafely` to extract embedded artwork from ID3 (MP3), MP4/M4A tags (`covr`), FLAC picture blocks, and Vorbis comments, even if files have wrong extensions.
- **LRU Memory Bounding**: Caches up to 64 images scaled to target dimensions (e.g. 34px for rows, 52px for playbar, 120px for cards) to prevent memory ballooning.
- **Asynchronous Loading**: Dedicated background thread pool loads and scales artwork, notifying Swing via `SwingUtilities.invokeLater` without blocking the Event Dispatch Thread (EDT).
- **Graceful Placeholder**: When no artwork is embedded or while loading, displays an antialiased rounded gradient badge (`#1B1824` to `#272236`) with a crisp accent music glyph (`♪`).

### 5.3 Responsive Home Layout (`HomeView.java`)
Instead of a rigid single-row layout that clips or leaves large empty gaps, `HomeView` uses responsive horizontal shelves wrapped in smooth scrolling containers displaying up to 16 cards per section. Each card includes hover effects, rounded artwork, and full-title hover tooltips.

### 5.4 Sidebar Visual Distinction (`Sidebar.java`)
The active navigation item features a subtle purple-tinted background (`#241D35`), a rounded 4px accent indicator pill (`#A78BFA`), and brightened labels for clear visual orientation.

### 5.5 Accurate Duration & Position Display
- `AudioEngine` strictly clamps decoded position: `posMs = Math.min(posMs, durationMs)`.
- `PlaybackBar` progress slider and elapsed time never exceed the track duration.
- `Song.java` and `MetadataService.java` detect and prevent raw UUIDs or hashes from appearing as titles, falling back to clean sanitized filenames.

### 5.6 Playlist Management & Adding Songs (`AddSongsDialog`, `PlaylistUtils`)
Users can add songs to playlists through multiple intuitive entry points:
- **Inside Playlists View**:
  - Prominent **`＋ Add Songs`** button in the top-right header whenever a playlist is selected.
  - Interactive **empty-state callout** with **`＋ Add Songs to Playlist`** button if the playlist has no tracks yet.
  - Right-click menu on the playlist in the sidebar: **`Add Songs…`**.
  - Opens `AddSongsDialog`: a modal multi-select song picker with live search, Select/Deselect All, duration details, and duplicate protection (`"In playlist"` indicator).
- **Across All Views (Library, Recent, Favourites, Home)**:
  - Right-click any track or album card to select **`Add to Playlist…`**.
  - Choose an existing playlist or select **`＋ New Playlist…`** on the fly.
- **Bottom Playback Bar**:
  - Click the **`▤`** button next to the heart (`♥`) icon while any song is playing to add the current song to a playlist.

---

## 6. Building and Running

### Prerequisites
- Java Development Kit (JDK 17 or higher)
- Apache Maven 3.8+

### Compile and Package
```bash
mvn clean package -DskipTests
```
This produces an executable shaded JAR at `target/sattune-1.0.0.jar`.

### Run
```bash
java -jar target/sattune-1.0.0.jar
```
Or run the launch script:
```bash
./run.sh
```

---

## 7. Testing Checklist

When verifying audio and UI changes, verify:
- [x] **MP3 Playback**: Normal MP3 files decode smoothly.
- [x] **Renamed Files**: MP3 files with `.m4a` extension and M4A files with `.mp3` extension decode properly without JAAD NPE.
- [x] **FLAC Playback**: 16/24-bit FLAC files play and report correct durations.
- [x] **OGG Vorbis Playback**: OGG files stream without packet stutter.
- [x] **WAV Playback**: Standard uncompressed PCM files play smoothly.
- [x] **Corrupted Files**: Truncated or corrupt audio files fail gracefully with a clean user notification.
- [x] **Unsupported Files**: Non-audio files are rejected without application freeze.
- [x] **Controls**: Play, pause, resume, seek, next, previous, and volume sliders function accurately.
- [x] **Resource Management**: Rapid song switching does not leak audio threads or file handles.
