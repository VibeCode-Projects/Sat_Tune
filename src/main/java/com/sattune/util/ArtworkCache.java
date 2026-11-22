package com.sattune.util;

import com.sattune.service.MetadataService;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LRU-cached artwork loader.
 * Images are scaled to requested sizes before caching to bound memory usage.
 * Supports asynchronous loading off the EDT and attractive antialiased placeholders.
 */
public final class ArtworkCache {

    private static final Logger LOG = Logger.getLogger(ArtworkCache.class.getName());
    private static final int MAX_ENTRIES = 64;

    private final Map<String, ImageIcon> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> e) {
            return size() > MAX_ENTRIES;
        }
    };

    private final ExecutorService asyncPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sattune-artwork-loader");
        t.setDaemon(true);
        return t;
    });

    private static final ArtworkCache INSTANCE = new ArtworkCache();
    public static ArtworkCache getInstance() { return INSTANCE; }

    private ArtworkCache() {}

    /**
     * Non-blocking cached lookup.
     * Returns the cached ImageIcon if available, or null if not yet loaded.
     * Safe to call on the Swing EDT.
     */
    public ImageIcon getCached(String filePath, int size) {
        if (filePath == null) return null;
        String key = filePath + "@" + size;
        synchronized (cache) {
            return cache.get(key);
        }
    }

    /**
     * Synchronous get. If cached, returns immediately.
     * If not cached, loads from disk and scales. Do NOT call on the EDT.
     */
    public ImageIcon get(String filePath, int size) {
        if (filePath == null) return defaultIcon(size);
        String key = filePath + "@" + size;
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }

        ImageIcon icon = loadAndScale(filePath, size);
        synchronized (cache) {
            cache.put(key, icon);
        }
        return icon;
    }

    /**
     * Asynchronously loads artwork off the EDT and delivers the scaled icon
     * to the callback on the Swing EDT.
     */
    public void loadAsync(String filePath, int size, Consumer<ImageIcon> callback) {
        if (filePath == null) {
            if (callback != null) callback.accept(defaultIcon(size));
            return;
        }

        ImageIcon cached = getCached(filePath, size);
        if (cached != null) {
            if (callback != null) callback.accept(cached);
            return;
        }

        asyncPool.submit(() -> {
            ImageIcon icon = get(filePath, size);
            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.accept(icon));
            }
        });
    }

    public void invalidate(String filePath) {
        if (filePath == null) return;
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getKey().startsWith(filePath));
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private ImageIcon loadAndScale(String filePath, int size) {
        try {
            Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
            File file = new File(filePath);
            if (file.exists() && file.isFile() && file.length() > 0) {
                AudioFile af = MetadataService.readAudioFileSafely(file);
                if (af != null) {
                    Tag tag = af.getTag();
                    if (tag != null) {
                        Artwork art = tag.getFirstArtwork();
                        if (art != null && art.getBinaryData() != null) {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(art.getBinaryData()));
                            if (img != null) {
                                return scaleHighQuality(img, size);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return defaultIcon(size);
    }

    private ImageIcon scaleHighQuality(BufferedImage src, int size) {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw with subtle rounded corners
        int arc = Math.max(6, size / 8);
        g.setClip(new RoundRectangle2D.Float(0, 0, size, size, arc, arc));
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return new ImageIcon(dst);
    }

    /**
     * Consistent, attractive placeholder icon with rounded dark gradient
     * and subtle music glyph.
     */
    public static ImageIcon defaultIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int arc = Math.max(6, size / 8);

        // Subtle dark violet/slate gradient
        GradientPaint gp = new GradientPaint(
                0, 0, new Color(0x1B, 0x18, 0x24),
                size, size, new Color(0x27, 0x22, 0x36)
        );
        g.setPaint(gp);
        g.fillRoundRect(0, 0, size, size, arc, arc);

        // Subtle border
        g.setColor(new Color(0x38, 0x32, 0x4C));
        g.drawRoundRect(0, 0, size - 1, size - 1, arc, arc);

        // Music glyph in accent tone
        g.setColor(Theme.ACCENT);
        int fontSize = Math.max(12, size / 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        FontMetrics fm = g.getFontMetrics();
        String note = "♪";
        int x = (size - fm.stringWidth(note)) / 2;
        int y = (size + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(note, x, y);

        g.dispose();
        return new ImageIcon(img);
    }
}
