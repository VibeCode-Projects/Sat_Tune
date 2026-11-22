package com.sattune;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sattune.audio.PlayerController;
import com.sattune.model.AppData;
import com.sattune.service.LibraryService;
import com.sattune.service.PersistenceService;
import com.sattune.ui.MainWindow;
import com.sattune.util.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sattune — Minimal Offline Desktop Music Player
 * Entry point.
 */
public class SatTune {

    public static void main(String[] args) {
        // Suppress noisy loggers
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        Logger.getLogger("com.sun").setLevel(Level.WARNING);

        // Apply FlatLaf dark theme before any Swing component is created
        try {
            FlatDarkLaf.setup();
            // Fine-tune FlatLaf properties to match our palette
            UIManager.put("Panel.background",          Theme.BG_MAIN);
            UIManager.put("OptionPane.background",     Theme.BG_SECONDARY);
            UIManager.put("TextField.background",      Theme.BG_MAIN);
            UIManager.put("TextField.foreground",      Theme.TEXT_PRIMARY);
            UIManager.put("TextField.caretForeground", Theme.TEXT_PRIMARY);
            UIManager.put("PopupMenu.background",      Theme.BG_SECONDARY);
            UIManager.put("MenuItem.background",       Theme.BG_SECONDARY);
            UIManager.put("MenuItem.foreground",       Theme.TEXT_PRIMARY);
            UIManager.put("MenuItem.selectionBackground", Theme.BG_HOVER);
            UIManager.put("SplitPane.background",      Theme.BG_MAIN);
            UIManager.put("SplitPaneDivider.background", Theme.BORDER);
            UIManager.put("ScrollBar.thumb",           Theme.BG_HOVER);
            UIManager.put("ScrollBar.track",           Theme.BG_MAIN);
            UIManager.put("ScrollBar.width",           6);
            UIManager.put("Component.focusWidth",      0);
            UIManager.put("Component.innerFocusWidth", 0);
        } catch (Exception e) {
            System.err.println("FlatLaf unavailable, using default: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            // ── Bootstrap services ────────────────────────────────────────────
            PersistenceService persist  = new PersistenceService();
            LibraryService     library  = new LibraryService();
            PlayerController   player   = new PlayerController(library);

            // Load persisted data
            AppData data = persist.load();
            library.loadFromAppData(data);

            // Restore preferences
            AppData.Preferences prefs = library.getPreferences();
            player.setVolume(prefs.getVolume());
            player.setShuffleOn(prefs.isShuffleOn());
            player.setRepeatMode(prefs.getRepeatMode());

            // ── Create and show window ────────────────────────────────────────
            MainWindow window = new MainWindow(library, player, persist);
            window.setVisible(true);
        });
    }
}
