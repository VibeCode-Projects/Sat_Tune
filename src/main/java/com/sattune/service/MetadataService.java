package com.sattune.service;

import com.sattune.model.Song;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans files/folders and extracts Song metadata using JAudioTagger.
 * Runs off the EDT — callers must handle threading.
 */
public class MetadataService {

    private static final Logger LOG = Logger.getLogger(MetadataService.class.getName());

    private static final Set<String> SUPPORTED_EXT = Set.of("mp3", "flac", "wav", "ogg", "aac", "m4a");

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
            if (song != null) { callback.onSongFound(song); return 1; }
        }
        return 0;
    }

    private boolean isSupported(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SUPPORTED_EXT.contains(name.substring(dot + 1));
    }

    public Song extract(File file) {
        try {
            // Suppress JAudioTagger's very verbose logging
            java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);

            AudioFile af  = AudioFileIO.read(file);
            AudioHeader ah = af.getAudioHeader();
            Tag tag       = af.getTag();

            String title  = tag != null ? clean(tag.getFirst(FieldKey.TITLE))  : null;
            String artist = tag != null ? clean(tag.getFirst(FieldKey.ARTIST)) : null;
            String album  = tag != null ? clean(tag.getFirst(FieldKey.ALBUM))  : null;

            if (title  == null || title.isBlank())  title  = stripExt(file.getName());
            if (artist == null || artist.isBlank()) artist = "Unknown Artist";
            if (album  == null || album.isBlank())  album  = "Unknown Album";

            long durationMs = (long) ah.getTrackLength() * 1000L;

            return new Song(UUID.randomUUID().toString(),
                            file.getAbsolutePath(),
                            title, artist, album, durationMs);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Cannot read metadata for: " + file, e);
            // Fallback: minimal song with filename as title
            return new Song(UUID.randomUUID().toString(),
                            file.getAbsolutePath(),
                            stripExt(file.getName()),
                            "Unknown Artist", "Unknown Album", 0L);
        }
    }

    private static String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
