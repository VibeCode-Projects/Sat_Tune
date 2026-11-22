package com.sattune.ui.content;

import com.sattune.audio.PlayerController;
import com.sattune.model.Playlist;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Playlists view — sidebar list of playlists + song list in selected playlist.
 */
public class PlaylistsView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;

    private final DefaultListModel<Playlist> playlistModel = new DefaultListModel<>();
    private final JList<Playlist>            playlistList;
    private final JPanel                     songListPanel;
    private final JLabel                     playlistTitle;

    public PlaylistsView(LibraryService library, PlayerController player) {
        this.library = library;
        this.player  = player;

        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout(0, 0));

        // ── Top toolbar ──────────────────────────────────────────────────────
        JPanel toolbar = UiUtils.panel(Theme.BG_SECONDARY, new FlowLayout(FlowLayout.LEFT, 16, 10));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        JLabel heading = UiUtils.label("Playlists", Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
        JButton newBtn = UiUtils.textButton("+ New Playlist");
        newBtn.addActionListener(e -> createPlaylist());
        toolbar.add(heading);
        toolbar.add(Box.createHorizontalStrut(12));
        toolbar.add(newBtn);
        add(toolbar, BorderLayout.NORTH);

        // ── Split: playlist list left, songs right ────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerSize(1);
        split.setDividerLocation(200);
        split.setBorder(null);
        split.setBackground(Theme.BG_MAIN);

        // Left: playlist list
        playlistList = new JList<>(playlistModel);
        playlistList.setBackground(Theme.BG_SIDEBAR);
        playlistList.setForeground(Theme.TEXT_PRIMARY);
        playlistList.setFont(Theme.FONT_LABEL);
        playlistList.setFixedCellHeight(36);
        playlistList.setSelectionBackground(Theme.BG_SELECTED);
        playlistList.setSelectionForeground(Theme.TEXT_PRIMARY);
        playlistList.setCellRenderer(new PlaylistCellRenderer());
        playlistList.setBorder(BorderFactory.createEmptyBorder());

        playlistList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshSongList();
        });
        playlistList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int idx = playlistList.locationToIndex(e.getPoint());
                if (idx >= 0 && SwingUtilities.isRightMouseButton(e)) {
                    playlistList.setSelectedIndex(idx);
                    showPlaylistMenu(e, playlistModel.get(idx));
                }
            }
        });

        JScrollPane leftScroll = UiUtils.scrollPane(playlistList);
        leftScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        split.setLeftComponent(leftScroll);

        // Right: songs in selected playlist
        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.BG_MAIN);

        JPanel rightHeader = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout(0, 0));
        rightHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        playlistTitle = UiUtils.label("Select a playlist", Theme.TEXT_SECONDARY, Theme.FONT_HEADING);
        JButton playAllBtn = UiUtils.textButton("▶ Play All");
        playAllBtn.addActionListener(e -> playAll());
        rightHeader.add(playlistTitle, BorderLayout.WEST);
        rightHeader.add(playAllBtn,   BorderLayout.EAST);
        right.add(rightHeader, BorderLayout.NORTH);

        songListPanel = UiUtils.panel(Theme.BG_MAIN, null);
        songListPanel.setLayout(new BoxLayout(songListPanel, BoxLayout.Y_AXIS));
        JScrollPane rightScroll = UiUtils.scrollPane(songListPanel);
        right.add(rightScroll, BorderLayout.CENTER);

        split.setRightComponent(right);
        add(split, BorderLayout.CENTER);
    }

    public void refresh() {
        playlistModel.clear();
        library.getPlaylists().forEach(playlistModel::addElement);
        refreshSongList();
    }

    private void refreshSongList() {
        Playlist sel = playlistList.getSelectedValue();
        if (sel == null) {
            playlistTitle.setText("Select a playlist");
            songListPanel.removeAll();
        } else {
            playlistTitle.setText(sel.getName() + "  (" + sel.size() + " songs)");
            List<Song> songs = library.getSongsForPlaylist(sel.getId());
            songListPanel.removeAll();
            if (songs.isEmpty()) {
                JLabel empty = UiUtils.label("No songs in this playlist yet.", Theme.TEXT_MUTED, Theme.FONT_SMALL);
                empty.setBorder(BorderFactory.createEmptyBorder(20, 20, 0, 0));
                songListPanel.add(empty);
            } else {
                for (int i = 0; i < songs.size(); i++) {
                    songListPanel.add(songRow(sel, songs.get(i), i));
                }
            }
        }
        songListPanel.revalidate();
        songListPanel.repaint();
    }

    private JPanel songRow(Playlist playlist, Song song, int index) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setBackground(Theme.BG_MAIN);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 16, 0, 16)));

        JLabel num = UiUtils.label(String.valueOf(index + 1), Theme.TEXT_MUTED, Theme.FONT_SMALL);
        num.setPreferredSize(new Dimension(28, 44));

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setBackground(Theme.BG_MAIN);
        info.add(UiUtils.label(song.getTitle(),  Theme.TEXT_PRIMARY,   Theme.FONT_LABEL));
        info.add(UiUtils.label(song.getArtist(), Theme.TEXT_SECONDARY, Theme.FONT_SMALL));

        JLabel dur = UiUtils.label(song.getDurationFormatted(), Theme.TEXT_MUTED, Theme.FONT_SMALL);

        JButton remove = UiUtils.iconButton("×", Theme.TEXT_MUTED, Theme.DANGER);
        remove.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        remove.addActionListener(e -> {
            library.removeSongFromPlaylist(playlist.getId(), song.getId());
            refreshSongList();
        });

        row.add(num,    BorderLayout.WEST);
        row.add(info,   BorderLayout.CENTER);
        row.add(dur,    BorderLayout.EAST);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightSide.setBackground(Theme.BG_MAIN);
        rightSide.add(dur);
        rightSide.add(remove);
        row.add(rightSide, BorderLayout.EAST);

        // Double-click to play
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    List<Song> songs = library.getSongsForPlaylist(playlist.getId());
                    player.playSongFromList(songs, songs.indexOf(song));
                }
            }
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(Theme.BG_HOVER); info.setBackground(Theme.BG_HOVER); rightSide.setBackground(Theme.BG_HOVER); }
            @Override public void mouseExited (MouseEvent e) { row.setBackground(Theme.BG_MAIN);  info.setBackground(Theme.BG_MAIN);  rightSide.setBackground(Theme.BG_MAIN); }
        });

        return row;
    }

    private void playAll() {
        Playlist sel = playlistList.getSelectedValue();
        if (sel == null) return;
        List<Song> songs = library.getSongsForPlaylist(sel.getId());
        if (!songs.isEmpty()) player.playSongFromList(songs, 0);
    }

    private void createPlaylist() {
        String name = JOptionPane.showInputDialog(this, "Playlist name:", "New Playlist", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            library.createPlaylist(name.trim());
            refresh();
        }
    }

    private void showPlaylistMenu(MouseEvent e, Playlist playlist) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "New name:", playlist.getName());
            if (name != null && !name.isBlank()) { library.renamePlaylist(playlist.getId(), name.trim()); refresh(); }
        });
        JMenuItem delete = new JMenuItem("Delete");
        delete.setForeground(Theme.DANGER);
        delete.addActionListener(ev -> {
            int r = JOptionPane.showConfirmDialog(this, "Delete playlist \"" + playlist.getName() + "\"?",
                    "Delete", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) { library.deletePlaylist(playlist.getId()); refresh(); }
        });
        menu.add(rename); menu.add(delete);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    static class PlaylistCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof Playlist p) {
                l.setText("▤  " + p.getName());
                l.setFont(Theme.FONT_LABEL);
                l.setForeground(selected ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
                l.setBackground(selected ? Theme.BG_SELECTED : Theme.BG_SIDEBAR);
                l.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
            }
            return l;
        }
    }
}
