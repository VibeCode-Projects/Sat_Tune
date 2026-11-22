package com.sattune.model;

import java.io.File;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable-ish Song model.
 * File path is the unique identity; metadata is loaded on demand.
 */
public class Song {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HEX_HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{24,}$");

    private String id;           // UUID-style internal key
    private String filePath;     // Absolute path to audio file
    private String title;
    private String artist;
    private String album;
    private long   durationMs;   // Duration in milliseconds
    private boolean favourite;
    private long    addedAt;     // epoch ms when imported
    private long    lastPlayedAt;// epoch ms, 0 = never

    public Song() {}

    public Song(String id, String filePath, String title, String artist,
                String album, long durationMs) {
        this.id          = id;
        this.filePath    = filePath;
        this.title       = sanitizeTitle(title, filePath);
        this.artist      = sanitizeArtist(artist);
        this.album       = (album != null && !album.isBlank()) ? album.trim() : "Unknown Album";
        this.durationMs  = durationMs;
        this.addedAt     = System.currentTimeMillis();
        this.lastPlayedAt = 0L;
        this.favourite   = false;
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getFilePath()              { return filePath; }
    public void   setFilePath(String filePath){ this.filePath = filePath; }

    public String getTitle() {
        if (title == null || title.isBlank() || isUuidOrInternalId(title)) {
            return sanitizeTitle(null, filePath);
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = sanitizeTitle(title, this.filePath);
    }

    public String getArtist() {
        return (artist != null && !artist.isBlank()) ? artist : "Unknown Artist";
    }

    public void setArtist(String artist) {
        this.artist = sanitizeArtist(artist);
    }

    public String getAlbum() {
        return (album != null && !album.isBlank()) ? album : "Unknown Album";
    }

    public void setAlbum(String album) {
        this.album = (album != null && !album.isBlank()) ? album.trim() : "Unknown Album";
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long duration) {
        if (duration > 0) {
            this.durationMs = duration;
        }
    }

    public boolean isFavourite()                 { return favourite; }
    public void    setFavourite(boolean favourite){ this.favourite = favourite; }

    public long getAddedAt()           { return addedAt; }
    public void setAddedAt(long addedAt){ this.addedAt = addedAt; }

    public long getLastPlayedAt()                { return lastPlayedAt; }
    public void setLastPlayedAt(long lastPlayedAt){ this.lastPlayedAt = lastPlayedAt; }

    /** Human-readable duration string mm:ss */
    public String getDurationFormatted() {
        if (durationMs <= 0) return "--:--";
        long totalSec = durationMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    // ── Title sanitization & internal ID protection ──────────────────────────

    public static boolean isUuidOrInternalId(String text) {
        if (text == null) return false;
        String t = text.trim();
        return UUID_PATTERN.matcher(t).matches() || HEX_HASH_PATTERN.matcher(t).matches();
    }

    public static String sanitizeTitle(String candidateTitle, String filePath) {
        if (candidateTitle != null && !candidateTitle.isBlank() && !isUuidOrInternalId(candidateTitle)) {
            return candidateTitle.trim();
        }

        // Fallback: derive clean title from filename
        if (filePath != null && !filePath.isBlank()) {
            File f = new File(filePath);
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String base = (dot > 0) ? name.substring(0, dot) : name;

            // If filename itself is not a UUID/hash, clean it up
            if (!isUuidOrInternalId(base)) {
                // Replace underscores with spaces and collapse multiple spaces
                String clean = base.replace('_', ' ').replaceAll("\\s+", " ").trim();
                if (!clean.isBlank()) return clean;
            }
        }

        return "Unknown Track";
    }

    private static String sanitizeArtist(String artist) {
        if (artist == null || artist.isBlank() || isUuidOrInternalId(artist)) {
            return "Unknown Artist";
        }
        return artist.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        Song s = (Song) o;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() {
        return String.format("Song{id='%s', title='%s', artist='%s'}", id, getTitle(), getArtist());
    }
}
