package com.sattune.audio;

import com.sattune.model.Song;
import com.sattune.service.LibraryService;

import javax.swing.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the playback queue, shuffle, repeat modes and delegates to AudioEngine.
 *
 * Repeat modes: 0 = off, 1 = repeat one, 2 = repeat all.
 */
public class PlayerController {

    private static final Logger LOG = Logger.getLogger(PlayerController.class.getName());

    public interface PlayerListener {
        void onSongChanged(Song song);
        void onStateChanged(AudioEngine.State state);
        void onPositionChanged(long posMs, long durationMs);
        void onError(String message);
    }

    private final AudioEngine    engine  = new AudioEngine();
    private final LibraryService library;
    private final List<PlayerListener> listeners = new ArrayList<>();

    // ── Queue state ───────────────────────────────────────────────────────────

    private List<Song>   queue         = new ArrayList<>(); // current ordered/shuffled queue
    private List<Song>   originalQueue = new ArrayList<>(); // pre-shuffle order
    private int          queueIndex    = -1;
    private boolean      shuffleOn     = false;
    private int          repeatMode    = 0;    // 0=off 1=one 2=all

    private Song         currentSong;

    public PlayerController(LibraryService library) {
        this.library = library;
        engine.setListener(new AudioEngine.PlaybackListener() {
            @Override public void onStateChanged(AudioEngine.State state) {
                fireStateChanged(state);
            }
            @Override public void onPositionChanged(long posMs, long durationMs) {
                firePositionChanged(posMs, durationMs);
            }
            @Override public void onTrackEnded() {
                handleTrackEnded();
            }
            @Override public void onError(String msg) {
                fireError(msg);
            }
        });
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    public void addListener(PlayerListener l) { listeners.add(l); }
    public void removeListener(PlayerListener l) { listeners.remove(l); }

    // ── Playback ──────────────────────────────────────────────────────────────

    /** Play a single song, resetting any existing queue to just this song. */
    public void playSong(Song song) {
        playSongFromList(List.of(song), 0);
    }

    /** Play a list of songs starting at the given index (e.g. whole library or playlist). */
    public void playSongFromList(List<Song> songs, int index) {
        originalQueue = new ArrayList<>(songs);
        if (shuffleOn) {
            queue = shuffled(songs, index);
            queueIndex = 0; // shuffled puts starting song at front
        } else {
            queue = new ArrayList<>(songs);
            queueIndex = index;
        }
        startCurrentSong();
    }

    public void pause()  { engine.pause(); }
    public void resume() { engine.resume(); }

    public void togglePlayPause() {
        switch (engine.getState()) {
            case PLAYING -> engine.pause();
            case PAUSED  -> engine.resume();
            case STOPPED -> {
                if (currentSong != null) {
                    engine.play(currentSong);
                } else {
                    playRandom();
                }
            }
        }
    }

    /**
     * Plays a random song from the entire library or shuffle playlist.
     */
    public void playRandom() {
        List<Song> all = library.getAllSongs();
        if (all.isEmpty()) return;
        setShuffleOn(true);
        int randomIndex = new Random().nextInt(all.size());
        playSongFromList(all, randomIndex);
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == 1) { startCurrentSong(); return; }
        queueIndex++;
        if (queueIndex >= queue.size()) {
            if (repeatMode == 2) queueIndex = 0;
            else { engine.stop(); return; }
        }
        startCurrentSong();
    }

    public void previous() {
        if (queue.isEmpty()) return;
        // If more than 3 seconds in, restart; otherwise go to previous
        // We approximate: always go previous for simplicity
        queueIndex = Math.max(0, queueIndex - 1);
        startCurrentSong();
    }

    public void stop() { engine.stop(); }

    public void seekTo(long ms) { engine.seekTo(ms); }

    // ── Settings ──────────────────────────────────────────────────────────────

    public boolean isShuffleOn() { return shuffleOn; }

    public void setShuffleOn(boolean on) {
        this.shuffleOn = on;
        if (queue.isEmpty()) return;
        Song current = currentSong;
        if (on) {
            queue = shuffled(originalQueue, originalQueue.indexOf(current));
            queueIndex = 0;
        } else {
            queue = new ArrayList<>(originalQueue);
            queueIndex = Math.max(0, originalQueue.indexOf(current));
        }
    }

    public int  getRepeatMode()       { return repeatMode; }
    public void setRepeatMode(int m)  { this.repeatMode = m % 3; }
    public void cycleRepeat()         { setRepeatMode(repeatMode + 1); }

    public void setVolume(float v)    { engine.setVolume(v); }
    public float getVolume()          { return engine.getVolume(); }

    public AudioEngine.State getState()   { return engine.getState(); }
    public Song              getCurrentSong() { return currentSong; }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void startCurrentSong() {
        if (queueIndex < 0 || queueIndex >= queue.size()) return;
        currentSong = queue.get(queueIndex);
        library.recordPlayed(currentSong.getId());
        engine.play(currentSong);
        fireSongChanged(currentSong);
    }

    private void handleTrackEnded() {
        SwingUtilities.invokeLater(() -> {
            if (repeatMode == 1) { startCurrentSong(); return; }
            queueIndex++;
            if (queueIndex < queue.size()) {
                startCurrentSong();
            } else if (repeatMode == 2) {
                queueIndex = 0;
                startCurrentSong();
            } else {
                currentSong = null;
                fireStateChanged(AudioEngine.State.STOPPED);
            }
        });
    }

    private List<Song> shuffled(List<Song> songs, int keepFirstIndex) {
        List<Song> list = new ArrayList<>(songs);
        Song first = (keepFirstIndex >= 0 && keepFirstIndex < songs.size())
                     ? songs.get(keepFirstIndex) : null;
        Collections.shuffle(list);
        if (first != null) {
            list.remove(first);
            list.add(0, first);
        }
        return list;
    }

    // ── Fire events on EDT ────────────────────────────────────────────────────

    private void fireSongChanged(Song s) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onSongChanged(s)));
    }
    private void fireStateChanged(AudioEngine.State st) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onStateChanged(st)));
    }
    private void firePositionChanged(long pos, long dur) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onPositionChanged(pos, dur)));
    }
    private void fireError(String msg) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onError(msg)));
    }
}
