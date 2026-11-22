package com.sattune.ui;

import com.sattune.audio.PlayerController;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.service.MetadataService;
import com.sattune.service.PersistenceService;
import com.sattune.ui.content.*;
import com.sattune.ui.player.PlaybackBar;
import com.sattune.ui.sidebar.Sidebar;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Main application window.
 */
public class MainWindow extends JFrame {

    private final LibraryService    library;
    private final PlayerController  player;
    private final PersistenceService persist;
    private final MetadataService   metadata = new MetadataService();

    // ── View panels ───────────────────────────────────────────────────────────
    private final HomeView       homeView;
    private final LibraryView    libraryView;
    private final PlaylistsView  playlistsView;
    private final RecentView     recentView;
    private final FavouritesView favouritesView;
    private final SettingsView   settingsView;

    private final JPanel         contentArea;
    private final CardLayout     cardLayout  = new CardLayout();
    private final Sidebar        sidebar;
    private final PlaybackBar    playbackBar;

    private final ExecutorService scanWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sattune-scanner"); t.setDaemon(true); return t;
    });

    public MainWindow(LibraryService library, PlayerController player, PersistenceService persist) {
        this.library = library;
        this.player  = player;
        this.persist = persist;

        setTitle("Sattune");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setPreferredSize(new Dimension(1180, 750));
        setBackground(Theme.BG_MAIN);

        // ── Build views ───────────────────────────────────────────────────────
        homeView       = new HomeView(library, player);
        libraryView    = new LibraryView(library, player);
        playlistsView  = new PlaylistsView(library, player);
        recentView     = new RecentView(library, player);
        favouritesView = new FavouritesView(library, player);
        settingsView   = new SettingsView(library);

        contentArea = new JPanel(cardLayout);
        contentArea.setBackground(Theme.BG_MAIN);
        contentArea.add(homeView,       "HOME");
        contentArea.add(libraryView,    "LIBRARY");
        contentArea.add(playlistsView,  "PLAYLISTS");
        contentArea.add(recentView,     "RECENT");
        contentArea.add(favouritesView, "FAVOURITES");
        contentArea.add(settingsView,   "SETTINGS");

        // ── Sidebar ───────────────────────────────────────────────────────────
        sidebar = new Sidebar();
        sidebar.addNavListener(new Sidebar.NavListener() {
            @Override public void onNavigate(String view) { showView(view); }
            @Override public void onImportFiles()   { importFiles(); }
            @Override public void onImportFolder()  { importFolder(); }
            @Override public void onOpenSettings()  { showView("SETTINGS"); sidebar.setActiveView("SETTINGS"); }
        });

        // ── Playback bar ──────────────────────────────────────────────────────
        playbackBar = new PlaybackBar(player, library);

        // ── Root layout ───────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.BG_MAIN);
        root.add(sidebar,     BorderLayout.WEST);
        root.add(contentArea, BorderLayout.CENTER);
        root.add(playbackBar, BorderLayout.SOUTH);
        setContentPane(root);

        // ── Library change listener ───────────────────────────────────────────
        library.addListener(new LibraryService.LibraryListener() {
            @Override public void onLibraryChanged() {
                SwingUtilities.invokeLater(() -> { refreshCurrentView(); });
            }
            @Override public void onPlaylistsChanged() {
                SwingUtilities.invokeLater(() -> playlistsView.refresh());
            }
        });

        // ── Save on close ─────────────────────────────────────────────────────
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveData(); }
        });

        pack();
        setLocationRelativeTo(null);

        // Initial render
        SwingUtilities.invokeLater(this::refreshAllViews);
        showView("HOME");

        // ── Auto-scan ~/Music on first launch if library is empty ─────────────
        SwingUtilities.invokeLater(this::autoScanMusicFolderIfEmpty);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void showView(String name) {
        cardLayout.show(contentArea, name);
        switch (name) {
            case "HOME"       -> homeView.refresh();
            case "LIBRARY"    -> libraryView.refresh();
            case "PLAYLISTS"  -> playlistsView.refresh();
            case "RECENT"     -> recentView.refresh();
            case "FAVOURITES" -> favouritesView.refresh();
        }
    }

    private void refreshCurrentView() {
        // Refresh whichever view is visible
        for (Component c : contentArea.getComponents()) {
            if (c.isVisible()) {
                if (c == homeView)       homeView.refresh();
                else if (c == libraryView) libraryView.refresh();
                else if (c == recentView)  recentView.refresh();
                else if (c == favouritesView) favouritesView.refresh();
            }
        }
    }

    private void refreshAllViews() {
        homeView.refresh();
        libraryView.refresh();
        playlistsView.refresh();
        recentView.refresh();
        favouritesView.refresh();
    }

    // ── Auto-scan ─────────────────────────────────────────────────────────────

    /**
     * On first launch (empty library), automatically scan ~/Music so the user
     * sees their music immediately without any manual import step.
     */
    private void autoScanMusicFolderIfEmpty() {
        if (!library.getAllSongs().isEmpty()) return; // already has songs

        List<File> musicDirs = new ArrayList<>();

        // Standard music folder locations
        String home = System.getProperty("user.home");
        List<String> candidates = List.of(
            home + "/Music",
            home + "/music",
            home + "/Downloads",   // people often have mp3s here too
            "/sdcard/Music"        // Android-style paths on some Linux setups
        );

        for (String path : candidates) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                musicDirs.add(dir);
            }
        }

        if (musicDirs.isEmpty()) return;

        // Silently scan in background — show a small status label, no blocking dialog
        JDialog silent = new JDialog(this, false);
        silent.setUndecorated(true);
        JLabel lbl = UiUtils.label("  ♪  Scanning your music library…  ", Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        lbl.setBackground(Theme.BG_SECONDARY);
        lbl.setOpaque(true);
        lbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        silent.getContentPane().add(lbl);
        silent.pack();
        // Position at bottom-right
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        silent.setLocation(screen.width - silent.getWidth() - 20,
                           screen.height - silent.getHeight() - 60);
        silent.setVisible(true);

        int[] count = {0};
        scanWorker.submit(() -> {
            metadata.scan(musicDirs, new MetadataService.ScanCallback() {
                @Override public void onSongFound(Song song) {
                    // Skip tiny IDE sound files (under 10 seconds)
                    if (song.getDurationMs() > 0 && song.getDurationMs() < 10_000) return;
                    // Skip files in known system/app directories
                    String path = song.getFilePath();
                    if (path.contains("Antigravity IDE") || path.contains("/opt/") ||
                        path.contains("/.local/share/") || path.contains("/usr/")) return;

                    boolean added = library.addSong(song);
                    if (added) {
                        count[0]++;
                        SwingUtilities.invokeLater(() ->
                            lbl.setText("  ♪  Found " + count[0] + " songs…  "));
                    }
                }
                @Override public void onScanComplete(int total) {
                    SwingUtilities.invokeLater(() -> {
                        silent.dispose();
                        if (count[0] > 0) {
                            saveData();
                            refreshAllViews();
                            showView("LIBRARY");
                            sidebar.setActiveView("LIBRARY");
                        }
                    });
                }
            });
        });
    }

    // ── Import ────────────────────────────────────────────────────────────────

    private void importFiles() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.home") + "/Music");
        fc.setMultiSelectionEnabled(true);
        fc.setAcceptAllFileFilterUsed(true);
        fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "Audio files (MP3, M4A, FLAC, WAV, OGG, AAC)",
                "mp3", "m4a", "flac", "wav", "ogg", "aac"));
        fc.setFileFilter(fc.getChoosableFileFilters()[1]); // set audio filter as default
        fc.setDialogTitle("Select Music Files");

        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selected = fc.getSelectedFiles();
            if (selected == null || selected.length == 0) {
                // getSelectedFiles can return empty if user picks one file without multi-select
                File single = fc.getSelectedFile();
                if (single != null) selected = new File[]{single};
            }
            if (selected != null && selected.length > 0) {
                scanFiles(Arrays.asList(selected));
            }
        }
    }

    private void importFolder() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select Music Folder");
        fc.setAcceptAllFileFilterUsed(false);

        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File folder = fc.getSelectedFile();
            if (folder != null && folder.exists()) {
                scanFiles(List.of(folder));
            }
        }
    }

    private void scanFiles(List<File> targets) {
        // Progress toast at bottom of window
        JDialog progress = new JDialog(this, false);
        progress.setUndecorated(true);
        JLabel msg = UiUtils.label("  ♪  Scanning…  ", Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        msg.setBackground(Theme.BG_SECONDARY);
        msg.setOpaque(true);
        msg.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        progress.getContentPane().add(msg);
        progress.pack();
        progress.setLocationRelativeTo(this);
        progress.setVisible(true);

        int[] count = {0};

        scanWorker.submit(() -> {
            metadata.scan(targets, new MetadataService.ScanCallback() {
                @Override public void onSongFound(Song song) {
                    boolean added = library.addSong(song);
                    if (added) {
                        count[0]++;
                        SwingUtilities.invokeLater(() ->
                            msg.setText("  ♪  Imported " + count[0] + " songs…  "));
                    }
                }
                @Override public void onScanComplete(int total) {
                    SwingUtilities.invokeLater(() -> {
                        progress.dispose();
                        saveData();
                        refreshAllViews();
                        // Switch to Library so user sees results
                        showView("LIBRARY");
                        sidebar.setActiveView("LIBRARY");
                        if (count[0] == 0) {
                            JOptionPane.showMessageDialog(MainWindow.this,
                                "No new audio files found.\n\nSupported: MP3, M4A, FLAC, WAV, OGG, AAC",
                                "Import", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            });
        });
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveData() {
        // Update volume preference
        library.getPreferences().setVolume(player.getVolume());
        library.getPreferences().setShuffleOn(player.isShuffleOn());
        library.getPreferences().setRepeatMode(player.getRepeatMode());
        Song cur = player.getCurrentSong();
        if (cur != null) library.getPreferences().setLastSongId(cur.getId());
        persist.saveAsync(library.toAppData());
    }
}
