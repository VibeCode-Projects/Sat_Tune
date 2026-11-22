package com.sattune.audio;

import com.sattune.model.Song;

import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Audio engine that handles MP3 (via JLayer SPI) and PCM formats (via
 * javax.sound.sampled).  Runs decoding on a dedicated daemon thread.
 *
 * Key design decisions:
 * - Pause/resume via blocking on a lock rather than stopping the stream.
 * - Volume via FloatControl.MASTER_GAIN on the SourceDataLine.
 * - Position tracking via byte counters — accurate to frame size.
 * - Seeking is done by re-opening the stream and skipping bytes.
 */
public class AudioEngine {

    private static final Logger LOG = Logger.getLogger(AudioEngine.class.getName());

    public enum State { STOPPED, PLAYING, PAUSED }

    public interface PlaybackListener {
        void onStateChanged(State state);
        void onPositionChanged(long positionMs, long durationMs);
        void onTrackEnded();
        void onError(String message);
    }

    // ── State ────────────────────────────────────────────────────────────────

    private volatile State           state     = State.STOPPED;
    private volatile Song            currentSong;
    private volatile float           volume    = 0.8f;   // 0.0–1.0

    private Thread                   playThread;
    private final Object             pauseLock = new Object();
    private final AtomicBoolean      paused    = new AtomicBoolean(false);
    private final AtomicBoolean      stopFlag  = new AtomicBoolean(false);
    private final AtomicLong         seekTarget= new AtomicLong(-1); // ms, -1 = no seek

    private PlaybackListener         listener;

    // ── Public API ───────────────────────────────────────────────────────────

    public void setListener(PlaybackListener l) { this.listener = l; }

    public State getState()   { return state; }
    public Song  getCurrentSong() { return currentSong; }
    public float getVolume()  { return volume; }

    /** Start playing a song from the beginning. */
    public synchronized void play(Song song) {
        stopInternal();
        currentSong = song;
        paused.set(false);
        stopFlag.set(false);
        seekTarget.set(-1);
        playThread = new Thread(() -> decode(song, 0), "sattune-audio");
        playThread.setDaemon(true);
        playThread.start();
        setState(State.PLAYING);
    }

    public synchronized void pause() {
        if (state == State.PLAYING) {
            paused.set(true);
            setState(State.PAUSED);
        }
    }

    public synchronized void resume() {
        if (state == State.PAUSED) {
            paused.set(false);
            setState(State.PLAYING);
            synchronized (pauseLock) { pauseLock.notifyAll(); }
        }
    }

    public synchronized void stop() {
        stopInternal();
        currentSong = null;
        setState(State.STOPPED);
    }

    /** Seek to position in milliseconds. */
    public synchronized void seekTo(long posMs) {
        if (currentSong == null) return;
        Song song = currentSong;
        stopInternal();
        paused.set(false);
        stopFlag.set(false);
        seekTarget.set(-1);
        playThread = new Thread(() -> decode(song, posMs), "sattune-audio");
        playThread.setDaemon(true);
        playThread.start();
        setState(State.PLAYING);
    }

    /** Set volume 0.0–1.0. */
    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
        // Applied to active line inside the decode loop
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void stopInternal() {
        stopFlag.set(true);
        paused.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        if (playThread != null && playThread.isAlive()) {
            try { playThread.join(2000); } catch (InterruptedException ignored) {}
        }
    }

    private void setState(State s) {
        state = s;
        if (listener != null) listener.onStateChanged(s);
    }

    // ── Decode loop ──────────────────────────────────────────────────────────

    private void decode(Song song, long startMs) {
        File file = new File(song.getFilePath());
        if (!file.exists()) {
            notifyError("File not found: " + file.getName());
            return;
        }

        AudioInputStream rawStream  = null;
        AudioInputStream pcmStream  = null;
        SourceDataLine   line       = null;

        try {
            rawStream = AudioSystem.getAudioInputStream(file);
            AudioFormat baseFormat = rawStream.getFormat();

            AudioInputStream targetStream = rawStream;
            AudioFormat pcmFormat = baseFormat;

            // Audio output on modern platforms (ALSA/Pulse/PipeWire/DirectSound) requires
            // signed 16-bit little-endian PCM format!
            if (baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED 
                    || baseFormat.getSampleSizeInBits() != 16 
                    || baseFormat.isBigEndian()) {
                float sampleRate = baseFormat.getSampleRate() > 0 ? baseFormat.getSampleRate() : 44100;
                int channels = baseFormat.getChannels() > 0 ? baseFormat.getChannels() : 2;
                pcmFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate,
                        16,
                        channels,
                        channels * 2,
                        sampleRate,
                        false); // Always little-endian for hardware DAC
                pcmStream = AudioSystem.getAudioInputStream(pcmFormat, rawStream);
                targetStream = pcmStream;
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
            line = getBestSourceDataLine(info, pcmFormat);
            line.open(pcmFormat, 65536);
            line.start();

            applyVolume(line);

            // Seek: skip bytes proportional to startMs
            long totalFrames      = targetStream.getFrameLength();
            float frameRate       = pcmFormat.getFrameRate();
            int   frameSize       = pcmFormat.getFrameSize();
            long durationMs       = song.getDurationMs() > 0 ? song.getDurationMs()
                                    : (totalFrames > 0 && frameRate > 0 ? (long)(totalFrames / frameRate * 1000) : 0);

            if (startMs > 0 && frameRate > 0 && frameSize > 0) {
                long skipFrames = (long)(startMs / 1000.0 * frameRate);
                long skipBytes  = skipFrames * frameSize;
                skipQuietly(targetStream, skipBytes);
            }

            // Bytes decoded so far (after seek)
            long bytesDecoded = startMs > 0 && frameRate > 0 && frameSize > 0
                              ? (long)(startMs / 1000.0 * frameRate) * frameSize : 0L;

            byte[] buffer = new byte[8192];
            int    read;

            while (!stopFlag.get() && (read = targetStream.read(buffer, 0, buffer.length)) != -1) {

                // Handle pause
                while (paused.get() && !stopFlag.get()) {
                    synchronized (pauseLock) {
                        try { pauseLock.wait(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
                if (stopFlag.get()) break;

                // Apply volume change
                applyVolume(line);

                line.write(buffer, 0, read);
                bytesDecoded += read;

                // Report position
                if (listener != null && frameRate > 0 && frameSize > 0) {
                    long posMs = (long)(bytesDecoded / (double) frameSize / frameRate * 1000);
                    listener.onPositionChanged(posMs, durationMs);
                }
            }

            if (!stopFlag.get()) {
                line.drain();
                if (listener != null) listener.onTrackEnded();
            }

        } catch (Exception e) {
            if (!stopFlag.get()) {
                LOG.log(Level.WARNING, "Playback error for: " + song.getFilePath(), e);
                notifyError("Cannot play: " + song.getTitle() + " — " + e.getMessage());
            }
        } finally {
            closeQuietly(line, pcmStream, rawStream);
        }
    }

    /**
     * Finds the best SourceDataLine. On Linux systems with PipeWire/PulseAudio,
     * ALSA hardware ports (like HDMI or hw:0) can be silent or locked, so we prioritize
     * default/pulse/pipewire/analog mixers over HDMI.
     */
    private SourceDataLine getBestSourceDataLine(DataLine.Info info, AudioFormat format) throws LineUnavailableException {
        // Priority 1: Check for "default", "pulse", "pipewire" or "analog" mixers
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            String name = mi.getName().toLowerCase();
            String desc = mi.getDescription().toLowerCase();
            if (name.contains("default") || name.contains("pulse") || name.contains("pipewire")
                    || (desc.contains("analog") && !name.contains("hdmi"))) {
                Mixer m = AudioSystem.getMixer(mi);
                if (m.isLineSupported(info)) {
                    try {
                        return (SourceDataLine) m.getLine(info);
                    } catch (Exception ignored) {}
                }
            }
        }

        // Priority 2: Non-HDMI mixer
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            String name = mi.getName().toLowerCase();
            if (!name.contains("hdmi")) {
                Mixer m = AudioSystem.getMixer(mi);
                if (m.isLineSupported(info)) {
                    try {
                        return (SourceDataLine) m.getLine(info);
                    } catch (Exception ignored) {}
                }
            }
        }

        // Fallback: system default line
        return (SourceDataLine) AudioSystem.getLine(info);
    }

    private void applyVolume(SourceDataLine line) {
        if (line == null) return;
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                // Convert linear 0–1 to dB
                float dB = volume <= 0.0001f ? gain.getMinimum()
                         : (float)(20.0 * Math.log10(volume));
                dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                gain.setValue(dB);
            }
        } catch (Exception ignored) {}
    }

    private void skipQuietly(AudioInputStream s, long bytes) {
        try {
            long remaining = bytes;
            byte[] buf = new byte[8192];
            while (remaining > 0) {
                int r = s.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (r < 0) break;
                remaining -= r;
            }
        } catch (Exception ignored) {}
    }

    private void closeQuietly(SourceDataLine line, AudioInputStream... streams) {
        if (line != null) { try { line.stop(); line.close(); } catch (Exception ignored) {} }
        for (AudioInputStream s : streams) {
            if (s != null) { try { s.close(); } catch (Exception ignored) {} }
        }
    }

    private void notifyError(String msg) {
        setState(State.STOPPED);
        if (listener != null) listener.onError(msg);
    }
}
