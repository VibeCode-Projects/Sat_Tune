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

/**
 * Home view — horizontal shelves for recently played, recently added, and favourites.
 * Effectively utilizes available horizontal space with responsive shelves.
 */
public class HomeView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;

    private final JPanel recentPlayedPanel;
    private final JPanel recentAddedPanel;
    private final JPanel favouritesPanel;

    public HomeView(LibraryService library, PlayerController player) {
        this.library = library;
        this.player  = player;

        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setBackground(Theme.BG_MAIN);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));

        // Header
        JLabel heading = UiUtils.label("Good listening.", Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        content.add(heading);

        // Sections
        recentPlayedPanel = createShelfPanel();
        recentAddedPanel  = createShelfPanel();
        favouritesPanel   = createShelfPanel();

        content.add(sectionLabel("Recently Played"));
        content.add(wrapShelf(recentPlayedPanel));
        content.add(Box.createVerticalStrut(28));

        content.add(sectionLabel("Recently Added"));
        content.add(wrapShelf(recentAddedPanel));
        content.add(Box.createVerticalStrut(28));

        content.add(sectionLabel("Favourites"));
        content.add(wrapShelf(favouritesPanel));

        JScrollPane scroll = UiUtils.scrollPane(content);
        scroll.setBackground(Theme.BG_MAIN);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public void refresh() {
        populateGrid(recentPlayedPanel, library.getRecentlyPlayed().stream().limit(16).toList());
        populateGrid(recentAddedPanel,  library.getRecentlyAdded(16));
        populateGrid(favouritesPanel,   library.getFavourites().stream().limit(16).toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JPanel createShelfPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        p.setBackground(Theme.BG_MAIN);
        p.setAlignmentX(0f);
        return p;
    }

    private JComponent wrapShelf(JPanel shelf) {
        JScrollPane sp = new JScrollPane(shelf);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 5));
        sp.getHorizontalScrollBar().setUnitIncrement(24);
        sp.setAlignmentX(0f);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_SIZE + 86));
        sp.setPreferredSize(new Dimension(Short.MAX_VALUE, CARD_SIZE + 86));
        return sp;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = UiUtils.label(text, Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        l.setAlignmentX(0f);
        return l;
    }

    private void populateGrid(JPanel grid, List<Song> songs) {
        grid.removeAll();
        if (songs.isEmpty()) {
            JLabel empty = UiUtils.label("Nothing here yet.", Theme.TEXT_MUTED, Theme.FONT_SMALL);
            empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 0));
            grid.add(empty);
        } else {
            for (Song s : songs) {
                grid.add(albumCard(s));
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private static final int CARD_SIZE = 120;

    private JPanel albumCard(Song song) {
        JPanel card = new JPanel() {
            private boolean hov = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) { hov = true;  repaint(); }
                    @Override public void mouseExited (java.awt.event.MouseEvent e) { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? Theme.BG_HOVER : Theme.BG_SECONDARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
                // Subtle border
                g2.setColor(hov ? Theme.ACCENT_DIM : Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.ARC, Theme.ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setPreferredSize(new Dimension(CARD_SIZE + 20, CARD_SIZE + 62));
        card.setMaximumSize (new Dimension(CARD_SIZE + 20, CARD_SIZE + 62));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText(song.getTitle() + " • " + song.getArtist());

        JLabel art = new JLabel(ArtworkCache.defaultIcon(CARD_SIZE));
        art.setAlignmentX(0.5f);

        // Load real artwork async via ArtworkCache
        ImageIcon cached = ArtworkCache.getInstance().getCached(song.getFilePath(), CARD_SIZE);
        if (cached != null) {
            art.setIcon(cached);
        } else {
            ArtworkCache.getInstance().loadAsync(song.getFilePath(), CARD_SIZE, icon -> {
                art.setIcon(icon);
                art.repaint();
            });
        }

        JLabel title  = UiUtils.label(truncate(song.getTitle(), 16), Theme.TEXT_PRIMARY, Theme.FONT_LABEL);
        JLabel artist = UiUtils.label(truncate(song.getArtist(), 18), Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        title.setToolTipText(song.getTitle());
        artist.setToolTipText(song.getArtist());
        title.setAlignmentX(0.5f);
        artist.setAlignmentX(0.5f);

        card.add(art);
        card.add(Box.createVerticalStrut(8));
        card.add(title);
        card.add(Box.createVerticalStrut(2));
        card.add(artist);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    player.playSongFromList(library.getAllSongs(), library.getAllSongs().indexOf(song));
                }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showCardContextMenu(e, song);
                }
            }
        });

        return card;
    }

    private void showCardContextMenu(java.awt.event.MouseEvent e, Song song) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BG_SECONDARY);

        JMenuItem play = new JMenuItem("Play");
        play.setForeground(Theme.TEXT_PRIMARY);
        play.addActionListener(ev -> player.playSongFromList(library.getAllSongs(), library.getAllSongs().indexOf(song)));

        JMenuItem addPlaylist = new JMenuItem("Add to Playlist…");
        addPlaylist.setForeground(Theme.TEXT_PRIMARY);
        addPlaylist.addActionListener(ev -> PlaylistUtils.showAddToPlaylistDialog(this, library, song, null));

        JMenuItem fav = new JMenuItem(song.isFavourite() ? "Remove from Favourites" : "Add to Favourites");
        fav.setForeground(Theme.TEXT_PRIMARY);
        fav.addActionListener(ev -> { library.toggleFavourite(song.getId()); refresh(); });

        menu.add(play);
        menu.add(addPlaylist);
        menu.add(fav);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
