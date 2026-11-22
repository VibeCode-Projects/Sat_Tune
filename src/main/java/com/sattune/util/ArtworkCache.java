package com.sattune.util;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

/**
 * LRU-cached artwork loader.
 * Images are scaled to the requested size before caching to bound memory usage.
 */
public final class ArtworkCache {

    private static final Logger LOG = Logger.getLogger(ArtworkCache.class.getName());
    private static final int    MAX_ENTRIES = 64;

    // LRU map
    private final Map<String, ImageIcon> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> e) {
            return size() > MAX_ENTRIES;
        }
    };

    private static final ArtworkCache INSTANCE = new ArtworkCache();
    public static ArtworkCache getInstance() { return INSTANCE; }

    private ArtworkCache() {}

    /**
     * Returns a scaled ImageIcon for the song's embedded artwork,
     * or a default placeholder icon of the requested size.
     * This call is cheap when cached; if not cached, do NOT call on the EDT.
     */
    public ImageIcon get(String filePath, int size) {
        String key = filePath + "@" + size;
        synchronized (cache) {
            if (cache.containsKey(key)) return cache.get(key);
        }

        ImageIcon icon = loadAndScale(filePath, size);
        synchronized (cache) { cache.put(key, icon); }
        return icon;
    }

    public void invalidate(String filePath) {
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getKey().startsWith(filePath));
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private ImageIcon loadAndScale(String filePath, int size) {
        try {
            java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
            AudioFile af  = AudioFileIO.read(new File(filePath));
            Tag tag = af.getTag();
            if (tag != null) {
                Artwork art = tag.getFirstArtwork();
                if (art != null && art.getBinaryData() != null) {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(art.getBinaryData()));
                    if (img != null) return scaleIcon(img, size);
                }
            }
        } catch (Exception ignored) {}
        return defaultIcon(size);
    }

    private ImageIcon scaleIcon(BufferedImage src, int size) {
        Image scaled = src.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING);
        return new ImageIcon(scaled);
    }

    public static ImageIcon defaultIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG_SECONDARY);
        g.fillRect(0, 0, size, size);
        // Simple music note placeholder
        g.setColor(Theme.TEXT_MUTED);
        int p = size / 4;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size / 2));
        FontMetrics fm = g.getFontMetrics();
        String note = "♪";
        g.drawString(note, (size - fm.stringWidth(note)) / 2,
                           (size + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        return new ImageIcon(img);
    }
}
