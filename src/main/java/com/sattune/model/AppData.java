package com.sattune.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root persistence model — serialised as a single JSON file.
 */
public class AppData {

    private List<Song>     library;        // All imported songs
    private List<Playlist> playlists;
    private List<String>   recentlyPlayed; // Song IDs, most-recent first, max 50
    private Preferences    preferences;

    public AppData() {
        this.library        = new ArrayList<>();
        this.playlists      = new ArrayList<>();
        this.recentlyPlayed = new ArrayList<>();
        this.preferences    = new Preferences();
    }

    public List<Song>     getLibrary()          { return library; }
    public void           setLibrary(List<Song> l){ this.library = l; }

    public List<Playlist> getPlaylists()         { return playlists; }
    public void           setPlaylists(List<Playlist> p){ this.playlists = p; }

    public List<String>   getRecentlyPlayed()    { return recentlyPlayed; }
    public void           setRecentlyPlayed(List<String> r){ this.recentlyPlayed = r; }

    public Preferences    getPreferences()       { return preferences; }
    public void           setPreferences(Preferences p){ this.preferences = p; }

    // ── Nested Preferences ──────────────────────────────────────────────────

    public static class Preferences {
        private float   volume        = 0.8f;
        private boolean shuffleOn     = false;
        private int     repeatMode    = 0;    // 0=off 1=one 2=all
        private String  lastSongId    = null;
        private long    lastPositionMs = 0L;
        private String  activeView    = "HOME";

        public float   getVolume()           { return volume; }
        public void    setVolume(float v)    { this.volume = v; }

        public boolean isShuffleOn()              { return shuffleOn; }
        public void    setShuffleOn(boolean s)    { this.shuffleOn = s; }

        public int     getRepeatMode()            { return repeatMode; }
        public void    setRepeatMode(int r)       { this.repeatMode = r; }

        public String  getLastSongId()            { return lastSongId; }
        public void    setLastSongId(String id)   { this.lastSongId = id; }

        public long    getLastPositionMs()         { return lastPositionMs; }
        public void    setLastPositionMs(long pos) { this.lastPositionMs = pos; }

        public String  getActiveView()             { return activeView; }
        public void    setActiveView(String v)     { this.activeView = v; }
    }
}
