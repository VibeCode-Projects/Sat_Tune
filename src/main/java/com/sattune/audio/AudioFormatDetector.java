package com.sattune.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Lightweight, zero-dependency audio container & format detector based on
 * inspecting magic bytes at the beginning of the file.
 *
 * Never relies solely on file extensions.
 */
public final class AudioFormatDetector {

    public enum AudioType {
        MP3("MP3 Audio"),
        M4A_AAC("AAC / M4A Audio"),
        FLAC("FLAC Audio"),
        OGG("Ogg Vorbis Audio"),
        WAV("WAV Audio"),
        UNKNOWN("Unknown Format");

        private final String displayName;

        AudioType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private AudioFormatDetector() {}

    /**
     * Inspects file content and returns the detected real audio format.
     */
    public static AudioType detect(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 4) {
            return AudioType.UNKNOWN;
        }

        byte[] header = new byte[32];
        int bytesRead = 0;
        try (InputStream in = new FileInputStream(file)) {
            bytesRead = in.read(header);
        } catch (Exception ignored) {
            return fallbackByExtension(file.getName());
        }

        if (bytesRead < 4) {
            return fallbackByExtension(file.getName());
        }

        // 1. FLAC: starts with "fLaC" (0x66 0x4C 0x61 0x43)
        if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') {
            return AudioType.FLAC;
        }

        // 2. OGG: starts with "OggS" (0x4F 0x67 0x67 0x53)
        if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') {
            return AudioType.OGG;
        }

        // 3. WAV: starts with "RIFF" and bytes 8..11 are "WAVE"
        if (bytesRead >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E') {
            return AudioType.WAV;
        }

        // 4. MP4 / M4A / AAC container: "ftyp" box at offset 4
        if (bytesRead >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return AudioType.M4A_AAC;
        }

        // 5. MP3 with ID3v2 header: starts with "ID3" (0x49 0x44 0x33)
        if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            return AudioType.MP3;
        }

        // 6. Raw MPEG frame sync or ADTS AAC
        int b0 = header[0] & 0xFF;
        int b1 = header[1] & 0xFF;
        if (b0 == 0xFF) {
            // ADTS AAC: sync word 0xFFF, layer == 00
            if ((b1 & 0xF6) == 0xF0 && (b1 & 0x06) == 0x00) {
                return AudioType.M4A_AAC;
            }
            // MPEG-1/2/2.5 Layer III sync word: bits 11..16 are 1, layer != 00
            if ((b1 & 0xE0) == 0xE0 && (b1 & 0x06) != 0x00) {
                return AudioType.MP3;
            }
        }

        // 7. Fallback to extension if magic bytes were inconclusive
        return fallbackByExtension(file.getName());
    }

    private static AudioType fallbackByExtension(String name) {
        if (name == null) return AudioType.UNKNOWN;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return AudioType.MP3;
        if (lower.endsWith(".m4a") || lower.endsWith(".aac")) return AudioType.M4A_AAC;
        if (lower.endsWith(".flac")) return AudioType.FLAC;
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return AudioType.OGG;
        if (lower.endsWith(".wav")) return AudioType.WAV;
        return AudioType.UNKNOWN;
    }
}
