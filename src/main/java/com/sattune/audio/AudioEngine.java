package com.sattune.audio;

import com.sattune.model.Song;

import javax.sound.sampled.*;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Robust audio engine supporting MP3, M4A/AAC, FLAC, WAV, and OGG Vorbis.
 * 
 * Features:
 * - Content-based format detection (never relies purely on file extension).
 * - Priority-ordered decoder selection with safe fallback across all registered SPI readers.
 * - Protection against unchecked decoder exceptions (e.g. JAAD NullPointerException).
 * - Signed 16-bit little-endian PCM normalization for cross-platform audio line compatibility.
 * - Non-blocking track switching with immediate line flush/stop.
 * - Position clamping so elapsed time never exceeds track duration.
 * - Buffered streaming with low RAM and CPU usage.
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

    private volatile State              state        = State.STOPPED;
    private volatile Song               currentSong;
    private volatile float              volume       = 0.8f;   // 0.0–1.0

    private Thread                      playThread;
    private final Object                pauseLock    = new Object();
    private final AtomicBoolean         paused       = new AtomicBoolean(false);
    private final AtomicBoolean         stopFlag     = new AtomicBoolean(false);
    private final AtomicLong            seekTarget   = new AtomicLong(-1);

    private volatile SourceDataLine     activeLine;
    private volatile AudioInputStream   activeStream;

    private PlaybackListener            listener;

    // ── Public API ───────────────────────────────────────────────────────────

    public void setListener(PlaybackListener l) { this.listener = l; }

    public State getState()          { return state; }
    public Song  getCurrentSong()    { return currentSong; }
    public float getVolume()         { return volume; }

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
        playThread = new Thread(() -> decode(song, Math.max(0, posMs)), "sattune-audio");
        playThread.setDaemon(true);
        playThread.start();
        setState(State.PLAYING);
    }

    /** Set volume 0.0–1.0. */
    public void setVolume(float v) {
        this.volume = Math.max(0f, Math.min(1f, v));
        applyVolume(activeLine);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void stopInternal() {
        stopFlag.set(true);
        paused.set(false);
        synchronized (pauseLock) { pauseLock.notifyAll(); }

        // Immediately stop active line and stream to unblock line.write() and stream.read()
        SourceDataLine line = activeLine;
        if (line != null) {
            try { line.stop(); line.flush(); } catch (Exception ignored) {}
        }
        AudioInputStream stream = activeStream;
        if (stream != null) {
            try { stream.close(); } catch (Exception ignored) {}
        }

        if (playThread != null && playThread.isAlive() && Thread.currentThread() != playThread) {
            try { playThread.join(400); } catch (InterruptedException ignored) {}
        }
    }

    private void setState(State s) {
        state = s;
        if (listener != null) listener.onStateChanged(s);
    }

    // ── Decode loop ──────────────────────────────────────────────────────────

    private void decode(Song song, long startMs) {
        File file = new File(song.getFilePath());
        if (!file.exists() || !file.isFile() || file.length() == 0) {
            notifyError("File not found or empty: " + file.getName());
            return;
        }

        AudioInputStream rawStream  = null;
        AudioInputStream pcmStream  = null;
        SourceDataLine   line       = null;

        try {
            rawStream = getAudioInputStreamSafely(file);
            activeStream = rawStream;
            AudioFormat baseFormat = rawStream.getFormat();

            AudioInputStream targetStream = rawStream;
            AudioFormat pcmFormat = baseFormat;

            // Output format conversion: modern hardware DACs (ALSA, Pulse, PipeWire, DirectSound, CoreAudio)
            // require signed 16-bit little-endian PCM format.
            if (baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                    || baseFormat.getSampleSizeInBits() != 16
                    || baseFormat.isBigEndian()) {
                float sampleRate = baseFormat.getSampleRate() > 0 ? baseFormat.getSampleRate() : 44100.0f;
                int channels = baseFormat.getChannels() > 0 ? baseFormat.getChannels() : 2;
                pcmFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate,
                        16,
                        channels,
                        channels * 2,
                        sampleRate,
                        false); // Little-endian DAC standard
                pcmStream = AudioSystem.getAudioInputStream(pcmFormat, rawStream);
                targetStream = pcmStream;
                activeStream = pcmStream;
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
            line = getBestSourceDataLine(info, pcmFormat);
            activeLine = line;

            // Open buffer with 64KB for smooth, glitch-free audio
            line.open(pcmFormat, 65536);
            line.start();

            applyVolume(line);

            float frameRate = pcmFormat.getFrameRate();
            int   frameSize = pcmFormat.getFrameSize();
            long totalFrames = targetStream.getFrameLength();
            long durationMs = song.getDurationMs() > 0 ? song.getDurationMs()
                    : (totalFrames > 0 && frameRate > 0 ? (long)(totalFrames / frameRate * 1000) : 0);

            // Seek support: skip bytes proportional to startMs
            if (startMs > 0 && frameRate > 0 && frameSize > 0) {
                long skipFrames = (long)(startMs / 1000.0 * frameRate);
                long skipBytes  = skipFrames * frameSize;
                skipQuietly(targetStream, skipBytes);
            }

            // Bytes decoded so far (after seek)
            long bytesDecoded = startMs > 0 && frameRate > 0 && frameSize > 0
                    ? (long)(startMs / 1000.0 * frameRate) * frameSize : 0L;

            byte[] buffer = new byte[8192];
            int read;

            while (!stopFlag.get() && (read = targetStream.read(buffer, 0, buffer.length)) != -1) {
                // VorbisSPI may return 0 bytes while decoding header packets — continue without error
                if (read == 0) {
                    continue;
                }

                // Handle pause
                while (paused.get() && !stopFlag.get()) {
                    synchronized (pauseLock) {
                        try { pauseLock.wait(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
                if (stopFlag.get()) break;

                // Write decoded PCM to hardware audio line
                line.write(buffer, 0, read);
                bytesDecoded += read;

                // Report position with strict clamping to track duration
                if (listener != null && frameRate > 0 && frameSize > 0) {
                    long posMs = (long)(bytesDecoded / (double) frameSize / frameRate * 1000);
                    if (durationMs > 0 && posMs > durationMs) {
                        posMs = durationMs; // Clamp: elapsed never exceeds duration
                    }
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
                notifyError("Cannot play: " + song.getTitle() + " — " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        } finally {
            activeLine = null;
            activeStream = null;
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

    /**
     * Safely acquires an AudioInputStream using content-based format detection.
     * Orders decoder SPI readers according to the detected container and tries
     * compatible readers first, preventing unchecked exceptions (like JAAD NPE)
     * from crashing playback.
     */
    private AudioInputStream getAudioInputStreamSafely(File file) throws Exception {
        AudioFormatDetector.AudioType detected = AudioFormatDetector.detect(file);
        List<AudioFileReader> orderedReaders = new ArrayList<>();

        // Prioritize reader matching the detected real audio format
        for (AudioFileReader r : ServiceLoader.load(AudioFileReader.class)) {
            String name = r.getClass().getName();
            if (detected == AudioFormatDetector.AudioType.MP3 && name.contains("Mpeg")) {
                orderedReaders.add(0, r);
            } else if (detected == AudioFormatDetector.AudioType.M4A_AAC && name.contains("AAC")) {
                orderedReaders.add(0, r);
            } else if (detected == AudioFormatDetector.AudioType.FLAC && name.contains("Flac")) {
                orderedReaders.add(0, r);
            } else if (detected == AudioFormatDetector.AudioType.OGG && name.contains("Vorbis")) {
                orderedReaders.add(0, r);
            } else if (detected == AudioFormatDetector.AudioType.WAV && name.contains("Wave")) {
                orderedReaders.add(0, r);
            } else {
                orderedReaders.add(r);
            }
        }

        Throwable lastError = null;
        for (AudioFileReader reader : orderedReaders) {
            try {
                AudioInputStream stream = reader.getAudioInputStream(file);
                if (stream != null) {
                    return stream;
                }
            } catch (Throwable t) {
                lastError = t;
            }
        }

        throw new UnsupportedAudioFileException("Unsupported or corrupted audio: " + file.getName()
                + (lastError != null && lastError.getMessage() != null ? " (" + lastError.getMessage() + ")" : ""));
    }

    private void applyVolume(SourceDataLine line) {
        if (line == null) return;
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                // Convert linear 0.0–1.0 to decibels (dB)
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
        if (line != null) {
            try { line.stop(); line.close(); } catch (Exception ignored) {}
        }
        for (AudioInputStream s : streams) {
            if (s != null) {
                try { s.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void notifyError(String msg) {
        setState(State.STOPPED);
        if (listener != null) listener.onError(msg);
    }
}
