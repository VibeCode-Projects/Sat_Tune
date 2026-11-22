package com.sattune.model;

import java.util.Objects;

/**
 * Immutable-ish Song model.
 * File path is the unique identity; metadata is loaded on demand.
 */
public class Song {

    private String id;           // UUID-style unique key
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
        this.title       = title;
        this.artist      = artist;
        this.album       = album;
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

    public String getTitle()             { return title != null ? title : "Unknown"; }
    public void   setTitle(String title) { this.title = title; }

    public String getArtist()              { return artist != null ? artist : "Unknown Artist"; }
    public void   setArtist(String artist) { this.artist = artist; }

    public String getAlbum()             { return album != null ? album : "Unknown Album"; }
    public void   setAlbum(String album) { this.album = album; }

    public long getDurationMs()              { return durationMs; }
    public void setDurationMs(long duration) { this.durationMs = duration; }

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
        return String.format("Song{id='%s', title='%s', artist='%s'}", id, title, artist);
    }
}
