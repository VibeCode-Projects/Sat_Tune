package com.sattune.service;

import com.sattune.audio.AudioFormatDetector;
import com.sattune.model.Song;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.flac.FlacFileReader;
import org.jaudiotagger.audio.mp3.MP3FileReader;
import org.jaudiotagger.audio.mp4.Mp4FileReader;
import org.jaudiotagger.audio.ogg.OggFileReader;
import org.jaudiotagger.audio.wav.WavFileReader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans files/folders and extracts Song metadata using JAudioTagger and
 * magic-bytes format detection.
 *
 * Detects the real audio container format first so files with incorrect
 * extensions (such as MP3 with .m4a or M4A with .mp3) extract metadata
 * and embedded artwork properly.
 */
public class MetadataService {

    private static final Logger LOG = Logger.getLogger(MetadataService.class.getName());

    private static final Set<String> SUPPORTED_EXT = Set.of(
            "mp3", "flac", "wav", "ogg", "oga", "aac", "m4a"
    );

    static {
        // Suppress JAudioTagger's verbose logging globally
        try {
            Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        } catch (Exception ignored) {}
    }

    public interface ScanCallback {
        /** Called for each discovered song (may be called from worker thread). */
        void onSongFound(Song song);
        void onScanComplete(int total);
    }

    /**
     * Scan a list of files/directories and report results through the callback.
     * This method is blocking — run it from a background thread.
     */
    public void scan(List<File> targets, ScanCallback callback) {
        int count = 0;
        for (File target : targets) {
            count += scanEntry(target, callback);
        }
        callback.onScanComplete(count);
    }

    private int scanEntry(File file, ScanCallback callback) {
        if (file.isDirectory()) {
            int n = 0;
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, Comparator.comparing(File::getName));
                for (File child : children) n += scanEntry(child, callback);
            }
            return n;
        }
        if (file.isFile() && isSupported(file)) {
            Song song = extract(file);
            if (song != null) {
                callback.onSongFound(song);
                return 1;
            }
        }
        return 0;
    }

    private boolean isSupported(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && SUPPORTED_EXT.contains(name.substring(dot + 1))) {
            return true;
        }
        // Also check if magic bytes detect a supported format even with weird extension
        AudioFormatDetector.AudioType type = AudioFormatDetector.detect(f);
        return type != AudioFormatDetector.AudioType.UNKNOWN;
    }

    public Song extract(File file) {
        return readMetadata(file);
    }

    public static Song readMetadata(File file) {
        try {
            AudioFile af = readAudioFileSafely(file);
            AudioHeader ah = (af != null) ? af.getAudioHeader() : null;
            Tag tag = (af != null) ? af.getTag() : null;

            String title = (tag != null) ? clean(tag.getFirst(FieldKey.TITLE)) : null;
            String artist = (tag != null) ? clean(tag.getFirst(FieldKey.ARTIST)) : null;
            String album = (tag != null) ? clean(tag.getFirst(FieldKey.ALBUM)) : null;

            long durationMs = 0L;
            if (ah != null && ah.getTrackLength() > 0) {
                durationMs = (long) ah.getTrackLength() * 1000L;
            }

            // Create song — Song constructor automatically sanitizes UUIDs and internal IDs
            return new Song(UUID.randomUUID().toString(),
                    file.getAbsolutePath(),
                    title, artist, album, durationMs);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot read metadata for: " + file.getName(), e);
            // Fallback: minimal song with sanitized title from filename, never raw UUID
            return new Song(UUID.randomUUID().toString(),
                    file.getAbsolutePath(),
                    null, "Unknown Artist", "Unknown Album", 0L);
        }
    }

    /**
     * Reads audio tags safely by inspecting the actual container format first,
     * preventing crashes when an M4A file is named .mp3 or an MP3 is named .m4a.
     */
    public static AudioFile readAudioFileSafely(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 16) {
            return null;
        }

        AudioFormatDetector.AudioType type = AudioFormatDetector.detect(file);

        // Try the reader matching the detected real container first
        try {
            switch (type) {
                case MP3 -> {
                    return new MP3FileReader().read(file);
                }
                case M4A_AAC -> {
                    return new Mp4FileReader().read(file);
                }
                case FLAC -> {
                    return new FlacFileReader().read(file);
                }
                case OGG -> {
                    return new OggFileReader().read(file);
                }
                case WAV -> {
                    return new WavFileReader().read(file);
                }
                default -> {
                    // Try generic AudioFileIO
                    return AudioFileIO.read(file);
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Primary tag reader failed for " + file.getName() + " (" + type + "): " + t.getMessage());
        }

        // Fallbacks in order of popularity
        try { return AudioFileIO.read(file); } catch (Throwable ignored) {}
        try { return new MP3FileReader().read(file); } catch (Throwable ignored) {}
        try { return new Mp4FileReader().read(file); } catch (Throwable ignored) {}
        try { return new FlacFileReader().read(file); } catch (Throwable ignored) {}
        try { return new OggFileReader().read(file); } catch (Throwable ignored) {}
        try { return new WavFileReader().read(file); } catch (Throwable ignored) {}

        return null;
    }

    private static String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
