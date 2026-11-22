package com.sattune.ui.content;

import com.sattune.model.Playlist;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility for playlist interactions across all views.
 */
public final class PlaylistUtils {

    private PlaylistUtils() {}

    /**
     * Prompts the user to select an existing playlist or create a new one,
     * then adds the song directly into the playlist.
     */
    public static void showAddToPlaylistDialog(Component parent, LibraryService library, Song song, Runnable onAdded) {
        if (song == null) return;
        List<Playlist> playlists = library.getPlaylists();

        if (playlists.isEmpty()) {
            int ans = JOptionPane.showConfirmDialog(parent,
                    "No playlists yet. Would you like to create a new playlist for \"" + song.getTitle() + "\"?",
                    "Create Playlist",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) {
                String name = JOptionPane.showInputDialog(parent, "Playlist name:", "New Playlist", JOptionPane.PLAIN_MESSAGE);
                if (name != null && !name.isBlank()) {
                    Playlist p = library.createPlaylist(name.trim());
                    library.addSongToPlaylist(p.getId(), song.getId());
                    if (onAdded != null) onAdded.run();
                }
            }
            return;
        }

        List<String> options = new ArrayList<>();
        for (Playlist p : playlists) {
            options.add(p.getName());
        }
        options.add("＋ New Playlist…");

        String chosen = (String) JOptionPane.showInputDialog(parent,
                "Add \"" + song.getTitle() + "\" to playlist:",
                "Add to Playlist",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options.toArray(new String[0]),
                options.get(0));

        if (chosen == null) return;

        if (chosen.equals("＋ New Playlist…")) {
            String name = JOptionPane.showInputDialog(parent, "Playlist name:", "New Playlist", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.isBlank()) {
                Playlist p = library.createPlaylist(name.trim());
                library.addSongToPlaylist(p.getId(), song.getId());
                if (onAdded != null) onAdded.run();
            }
        } else {
            playlists.stream()
                     .filter(p -> p.getName().equals(chosen))
                     .findFirst()
                     .ifPresent(p -> {
                         library.addSongToPlaylist(p.getId(), song.getId());
                         if (onAdded != null) onAdded.run();
                     });
        }
    }
}
