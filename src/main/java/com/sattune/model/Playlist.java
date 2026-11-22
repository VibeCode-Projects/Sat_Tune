package com.sattune.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A named, ordered list of song IDs.
 */
public class Playlist {

    private String       id;
    private String       name;
    private List<String> songIds; // references Song.id
    private long         createdAt;

    public Playlist() {
        this.songIds   = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public Playlist(String id, String name) {
        this.id        = id;
        this.name      = name;
        this.songIds   = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String       getId()          { return id; }
    public void         setId(String id) { this.id = id; }

    public String       getName()              { return name; }
    public void         setName(String name)   { this.name = name; }

    public List<String> getSongIds()           { return songIds; }
    public void         setSongIds(List<String> songIds){ this.songIds = songIds; }

    public long         getCreatedAt()         { return createdAt; }
    public void         setCreatedAt(long t)   { this.createdAt = t; }

    public void addSong(String songId) {
        if (!songIds.contains(songId)) songIds.add(songId);
    }

    public void removeSong(String songId) {
        songIds.remove(songId);
    }

    public int size() { return songIds.size(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Playlist)) return false;
        return Objects.equals(id, ((Playlist) o).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() { return name; }
}
