package com.sattune.service;

import com.sattune.model.AppData;
import com.sattune.model.Playlist;
import com.sattune.model.Song;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Central in-memory library.  All mutations go through here so the UI
 * always sees a consistent state.  Listeners are notified on the calling
 * thread — callers should dispatch to EDT where needed.
 */
public class LibraryService {

    public interface LibraryListener {
        void onLibraryChanged();
        void onPlaylistsChanged();
    }

    private final List<Song>              songs     = new CopyOnWriteArrayList<>();
    private final List<Playlist>          playlists = new CopyOnWriteArrayList<>();
    private final Deque<String>           recent    = new ArrayDeque<>();   // song IDs
    private       AppData.Preferences     prefs     = new AppData.Preferences();
    private final List<LibraryListener>   listeners = new CopyOnWriteArrayList<>();

    private static final int MAX_RECENT = 50;

    // ── Listeners ───────────────────────────────────────────────────────────

    public void addListener(LibraryListener l) { listeners.add(l); }
    public void removeListener(LibraryListener l) { listeners.remove(l); }

    private void fireLibraryChanged()  { listeners.forEach(LibraryListener::onLibraryChanged); }
    private void firePlaylistsChanged(){ listeners.forEach(LibraryListener::onPlaylistsChanged); }

    // ── Songs ────────────────────────────────────────────────────────────────

    public List<Song> getAllSongs() { return Collections.unmodifiableList(songs); }

    public Optional<Song> findById(String id) {
        return songs.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    /** Add a song only if its file path is not already in the library. */
    public boolean addSong(Song song) {
        boolean exists = songs.stream()
                              .anyMatch(s -> s.getFilePath().equals(song.getFilePath()));
        if (exists) return false;
        songs.add(song);
        fireLibraryChanged();
        return true;
    }

    public void removeSong(String songId) {
        songs.removeIf(s -> s.getId().equals(songId));
        playlists.forEach(p -> p.removeSong(songId));
        recent.remove(songId);
        fireLibraryChanged();
        firePlaylistsChanged();
    }

    public void toggleFavourite(String songId) {
        findById(songId).ifPresent(s -> {
            s.setFavourite(!s.isFavourite());
            fireLibraryChanged();
        });
    }

    public List<Song> getFavourites() {
        return songs.stream().filter(Song::isFavourite).collect(Collectors.toList());
    }

    public List<Song> search(String query) {
        if (query == null || query.isBlank()) return getAllSongs();
        String q = query.toLowerCase(Locale.ROOT);
        return songs.stream()
                    .filter(s -> s.getTitle().toLowerCase().contains(q)
                              || s.getArtist().toLowerCase().contains(q)
                              || s.getAlbum().toLowerCase().contains(q))
                    .collect(Collectors.toList());
    }

    // ── Recently Played ──────────────────────────────────────────────────────

    public void recordPlayed(String songId) {
        recent.remove(songId);
        recent.addFirst(songId);
        while (recent.size() > MAX_RECENT) recent.removeLast();
        findById(songId).ifPresent(s -> s.setLastPlayedAt(System.currentTimeMillis()));
    }

    public List<Song> getRecentlyPlayed() {
        return recent.stream()
                     .map(id -> findById(id).orElse(null))
                     .filter(Objects::nonNull)
                     .collect(Collectors.toList());
    }

    public List<String> getRecentIds() { return new ArrayList<>(recent); }

    public void setRecentIds(List<String> ids) {
        recent.clear();
        recent.addAll(ids);
    }

    // ── Recently Added ──────────────────────────────────────────────────────

    public List<Song> getRecentlyAdded(int limit) {
        return songs.stream()
                    .sorted(Comparator.comparingLong(Song::getAddedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
    }

    // ── Playlists ────────────────────────────────────────────────────────────

    public List<Playlist> getPlaylists() { return Collections.unmodifiableList(playlists); }

    public Optional<Playlist> findPlaylistById(String id) {
        return playlists.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public Playlist createPlaylist(String name) {
        Playlist p = new Playlist(UUID.randomUUID().toString(), name);
        playlists.add(p);
        firePlaylistsChanged();
        return p;
    }

    public void renamePlaylist(String id, String name) {
        findPlaylistById(id).ifPresent(p -> { p.setName(name); firePlaylistsChanged(); });
    }

    public void deletePlaylist(String id) {
        playlists.removeIf(p -> p.getId().equals(id));
        firePlaylistsChanged();
    }

    public void addSongToPlaylist(String playlistId, String songId) {
        findPlaylistById(playlistId).ifPresent(p -> { p.addSong(songId); firePlaylistsChanged(); });
    }

    public void removeSongFromPlaylist(String playlistId, String songId) {
        findPlaylistById(playlistId).ifPresent(p -> { p.removeSong(songId); firePlaylistsChanged(); });
    }

    public List<Song> getSongsForPlaylist(String playlistId) {
        return findPlaylistById(playlistId)
                .map(p -> p.getSongIds().stream()
                           .map(id -> findById(id).orElse(null))
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    // ── Preferences ──────────────────────────────────────────────────────────

    public AppData.Preferences getPreferences() { return prefs; }
    public void setPreferences(AppData.Preferences p) { this.prefs = p; }

    // ── Bulk load from persisted data ────────────────────────────────────────

    public void loadFromAppData(AppData data) {
        songs.clear();
        for (Song s : data.getLibrary()) {
            if (s.getDurationMs() <= 0 || Song.isUuidOrInternalId(s.getTitle())) {
                File f = new File(s.getFilePath());
                if (f.exists()) {
                    Song refreshed = MetadataService.readMetadata(f);
                    s.setTitle(refreshed.getTitle());
                    s.setArtist(refreshed.getArtist());
                    s.setAlbum(refreshed.getAlbum());
                    if (refreshed.getDurationMs() > 0) {
                        s.setDurationMs(refreshed.getDurationMs());
                    }
                }
            }
            songs.add(s);
        }
        playlists.clear();
        playlists.addAll(data.getPlaylists());
        recent.clear();
        recent.addAll(data.getRecentlyPlayed());
        if (data.getPreferences() != null) prefs = data.getPreferences();
        fireLibraryChanged();
        firePlaylistsChanged();
    }

    public AppData toAppData() {
        AppData data = new AppData();
        data.setLibrary(new ArrayList<>(songs));
        data.setPlaylists(new ArrayList<>(playlists));
        data.setRecentlyPlayed(new ArrayList<>(recent));
        data.setPreferences(prefs);
        return data;
    }
}
