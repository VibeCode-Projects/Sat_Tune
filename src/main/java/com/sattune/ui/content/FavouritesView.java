package com.sattune.ui.content;

import com.sattune.audio.PlayerController;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.util.ArtworkCache;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Favourites view — compact list of favourite songs.
 */
public class FavouritesView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;
    private final JPanel           listPanel;

    public FavouritesView(LibraryService library, PlayerController player) {
        this.library = library;
        this.player  = player;

        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout());

        JPanel toolbar = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)));

        JPanel headerContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerContent.setBackground(Theme.BG_SECONDARY);
        JLabel heart = UiUtils.label("♥", Theme.DANGER, new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        headerContent.add(heart);
        headerContent.add(UiUtils.label("Favourites", Theme.TEXT_PRIMARY, Theme.FONT_HEADING));
        toolbar.add(headerContent, BorderLayout.WEST);
        add(toolbar, BorderLayout.NORTH);

        listPanel = UiUtils.panel(Theme.BG_MAIN, null);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        add(UiUtils.scrollPane(listPanel), BorderLayout.CENTER);
    }

    public void refresh() {
        listPanel.removeAll();
        List<Song> favs = library.getFavourites();
        if (favs.isEmpty()) {
            JLabel empty = UiUtils.label("No favourites yet. Click ♥ while a song plays.", Theme.TEXT_MUTED, Theme.FONT_LABEL);
            empty.setBorder(BorderFactory.createEmptyBorder(24, 24, 0, 0));
            listPanel.add(empty);
        } else {
            for (int i = 0; i < favs.size(); i++) {
                listPanel.add(songRow(favs, favs.get(i), i));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel songRow(List<Song> allSongs, Song song, int index) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Theme.BG_MAIN);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setPreferredSize(new Dimension(0, 48));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 16, 0, 20)));
        row.setToolTipText(song.getTitle() + " • " + song.getArtist());
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        left.setBackground(Theme.BG_MAIN);
        left.setOpaque(false);

        JLabel heartIcon = UiUtils.label("♥", Theme.DANGER, Theme.FONT_SMALL);
        heartIcon.setPreferredSize(new Dimension(20, 34));
        left.add(heartIcon);

        JLabel artLabel = new JLabel(ArtworkCache.defaultIcon(34));
        ImageIcon cached = ArtworkCache.getInstance().getCached(song.getFilePath(), 34);
        if (cached != null) {
            artLabel.setIcon(cached);
        } else {
            ArtworkCache.getInstance().loadAsync(song.getFilePath(), 34, icon -> {
                artLabel.setIcon(icon);
                artLabel.repaint();
            });
        }
        left.add(artLabel);

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setBackground(Theme.BG_MAIN);
        info.setOpaque(false);
        info.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
        info.add(UiUtils.label(song.getTitle(),  Theme.TEXT_PRIMARY,   Theme.FONT_LABEL));
        info.add(UiUtils.label(song.getArtist(), Theme.TEXT_SECONDARY, Theme.FONT_SMALL));

        JLabel dur = UiUtils.label(song.getDurationFormatted(), Theme.TEXT_MUTED, Theme.FONT_SMALL);

        row.add(left, BorderLayout.WEST);
        row.add(info, BorderLayout.CENTER);
        row.add(dur,  BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) player.playSongFromList(allSongs, index);
            }
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e, song, allSongs, index);
                }
            }
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(Theme.BG_HOVER); }
            @Override public void mouseExited (MouseEvent e) { row.setBackground(Theme.BG_MAIN); }
        });
        return row;
    }

    private void showContextMenu(MouseEvent e, Song song, List<Song> allSongs, int index) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BG_SECONDARY);

        JMenuItem play = new JMenuItem("Play");
        play.setForeground(Theme.TEXT_PRIMARY);
        play.addActionListener(ev -> player.playSongFromList(allSongs, index));

        JMenuItem addPlaylist = new JMenuItem("Add to Playlist…");
        addPlaylist.setForeground(Theme.TEXT_PRIMARY);
        addPlaylist.addActionListener(ev -> PlaylistUtils.showAddToPlaylistDialog(this, library, song, null));

        JMenuItem removeFav = new JMenuItem("Remove from Favourites");
        removeFav.setForeground(Theme.DANGER);
        removeFav.addActionListener(ev -> { library.toggleFavourite(song.getId()); refresh(); });

        menu.add(play);
        menu.add(addPlaylist);
        menu.addSeparator();
        menu.add(removeFav);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
}
