package com.sattune.ui.content;

import com.sattune.audio.PlayerController;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.util.ArtworkCache;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home view — recently played, recently added, favourites.
 */
public class HomeView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;

    private final JPanel recentPlayedPanel;
    private final JPanel recentAddedPanel;
    private final JPanel favouritesPanel;

    private final ExecutorService artWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sattune-home-art"); t.setDaemon(true); return t;
    });

    public HomeView(LibraryService library, PlayerController player) {
        this.library = library;
        this.player  = player;

        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setBackground(Theme.BG_MAIN);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        // Header
        JLabel heading = UiUtils.label("Good listening.", Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
        heading.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));
        content.add(heading);

        // Sections
        recentPlayedPanel = sectionGrid();
        recentAddedPanel  = sectionGrid();
        favouritesPanel   = sectionGrid();

        content.add(sectionLabel("Recently Played"));
        content.add(recentPlayedPanel);
        content.add(Box.createVerticalStrut(24));
        content.add(sectionLabel("Recently Added"));
        content.add(recentAddedPanel);
        content.add(Box.createVerticalStrut(24));
        content.add(sectionLabel("Favourites"));
        content.add(favouritesPanel);

        JScrollPane scroll = UiUtils.scrollPane(content);
        scroll.setBackground(Theme.BG_MAIN);
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        populateGrid(recentPlayedPanel, library.getRecentlyPlayed().stream().limit(6).toList());
        populateGrid(recentAddedPanel,  library.getRecentlyAdded(6));
        populateGrid(favouritesPanel,   library.getFavourites().stream().limit(6).toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel sectionGrid() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        p.setBackground(Theme.BG_MAIN);
        p.setAlignmentX(0f);
        return p;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = UiUtils.label(text, Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        l.setAlignmentX(0f);
        return l;
    }

    private void populateGrid(JPanel grid, List<Song> songs) {
        grid.removeAll();
        if (songs.isEmpty()) {
            JLabel empty = UiUtils.label("Nothing here yet.", Theme.TEXT_MUTED, Theme.FONT_SMALL);
            grid.add(empty);
        } else {
            for (Song s : songs) {
                grid.add(albumCard(s));
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private static final int CARD_SIZE = 110;

    private JPanel albumCard(Song song) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.BG_SECONDARY);
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setPreferredSize(new Dimension(CARD_SIZE + 16, CARD_SIZE + 48));
        card.setMaximumSize (new Dimension(CARD_SIZE + 16, CARD_SIZE + 48));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel art = new JLabel(ArtworkCache.defaultIcon(CARD_SIZE));
        art.setAlignmentX(0.5f);
        // Load real artwork async
        artWorker.submit(() -> {
            ImageIcon icon = ArtworkCache.getInstance().get(song.getFilePath(), CARD_SIZE);
            SwingUtilities.invokeLater(() -> { art.setIcon(icon); art.repaint(); });
        });

        JLabel title  = UiUtils.label(truncate(song.getTitle(), 14), Theme.TEXT_PRIMARY, Theme.FONT_SMALL);
        JLabel artist = UiUtils.label(truncate(song.getArtist(),14), Theme.TEXT_SECONDARY,Theme.FONT_SMALL);
        title.setAlignmentX(0.5f);
        artist.setAlignmentX(0.5f);

        card.add(art);
        card.add(Box.createVerticalStrut(6));
        card.add(title);
        card.add(artist);

        // Hover effect
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { card.setBackground(Theme.BG_HOVER); }
            @Override public void mouseExited (java.awt.event.MouseEvent e) { card.setBackground(Theme.BG_SECONDARY); }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                player.playSongFromList(library.getAllSongs(), library.getAllSongs().indexOf(song));
            }
        });

        return card;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
