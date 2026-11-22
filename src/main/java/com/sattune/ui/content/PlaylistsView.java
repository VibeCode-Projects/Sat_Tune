package com.sattune.ui.content;

import com.sattune.audio.PlayerController;
import com.sattune.model.Playlist;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.ui.dialog.AddSongsDialog;
import com.sattune.util.ArtworkCache;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Playlists view — sidebar list of playlists + song list in selected playlist.
 * Supports adding songs from the library via modal dialog, right-click, and callouts.
 */
public class PlaylistsView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;

    private final DefaultListModel<Playlist> playlistModel = new DefaultListModel<>();
    private final JList<Playlist>            playlistList;
    private final JPanel                     songListPanel;
    private final JLabel                     playlistTitle;
    private final JButton                    addSongsBtn;
    private final JButton                    playAllBtn;

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

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setBackground(Theme.BG_SECONDARY);

        addSongsBtn = UiUtils.accentButton("＋ Add Songs");
        addSongsBtn.setEnabled(false);
        addSongsBtn.addActionListener(e -> openAddSongsDialog());

        playAllBtn = UiUtils.textButton("▶ Play All");
        playAllBtn.setEnabled(false);
        playAllBtn.addActionListener(e -> playAll());

        headerActions.add(addSongsBtn);
        headerActions.add(playAllBtn);

        rightHeader.add(playlistTitle, BorderLayout.WEST);
        rightHeader.add(headerActions, BorderLayout.EAST);
        right.add(rightHeader, BorderLayout.NORTH);

        songListPanel = UiUtils.panel(Theme.BG_MAIN, null);
        songListPanel.setLayout(new BoxLayout(songListPanel, BoxLayout.Y_AXIS));
        JScrollPane rightScroll = UiUtils.scrollPane(songListPanel);
        right.add(rightScroll, BorderLayout.CENTER);

        split.setRightComponent(right);
        add(split, BorderLayout.CENTER);
    }

    public void refresh() {
        Playlist previousSelected = playlistList.getSelectedValue();
        playlistModel.clear();
        library.getPlaylists().forEach(playlistModel::addElement);
        if (previousSelected != null) {
            for (int i = 0; i < playlistModel.size(); i++) {
                if (playlistModel.get(i).getId().equals(previousSelected.getId())) {
                    playlistList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (playlistList.getSelectedIndex() < 0 && !playlistModel.isEmpty()) {
            playlistList.setSelectedIndex(0);
        }
        refreshSongList();
    }

    private void refreshSongList() {
        Playlist sel = playlistList.getSelectedValue();
        if (sel == null) {
            playlistTitle.setText("Select a playlist");
            addSongsBtn.setEnabled(false);
            playAllBtn.setEnabled(false);
            songListPanel.removeAll();
        } else {
            playlistTitle.setText(sel.getName() + "  (" + sel.size() + " songs)");
            addSongsBtn.setEnabled(true);

            List<Song> songs = library.getSongsForPlaylist(sel.getId());
            playAllBtn.setEnabled(!songs.isEmpty());
            songListPanel.removeAll();

            if (songs.isEmpty()) {
                JPanel emptyPanel = new JPanel();
                emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
                emptyPanel.setBackground(Theme.BG_MAIN);
                emptyPanel.setBorder(BorderFactory.createEmptyBorder(48, 24, 48, 24));

                JLabel emptyIcon = UiUtils.label("▤", Theme.TEXT_MUTED, new Font(Font.SANS_SERIF, Font.PLAIN, 40));
                emptyIcon.setAlignmentX(0.5f);

                JLabel emptyLabel = UiUtils.label("This playlist is empty.", Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
                emptyLabel.setAlignmentX(0.5f);

                JLabel subLabel = UiUtils.label("Add tracks from your library to start listening.", Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
                subLabel.setAlignmentX(0.5f);

                JButton addBtn = UiUtils.accentButton("＋  Add Songs to Playlist");
                addBtn.setAlignmentX(0.5f);
                addBtn.addActionListener(e -> openAddSongsDialog());

                emptyPanel.add(emptyIcon);
                emptyPanel.add(Box.createVerticalStrut(12));
                emptyPanel.add(emptyLabel);
                emptyPanel.add(Box.createVerticalStrut(6));
                emptyPanel.add(subLabel);
                emptyPanel.add(Box.createVerticalStrut(18));
                emptyPanel.add(addBtn);

                songListPanel.add(emptyPanel);
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
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setPreferredSize(new Dimension(0, 48));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 16, 0, 16)));
        row.setToolTipText(song.getTitle() + " • " + song.getArtist());
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        left.setBackground(Theme.BG_MAIN);
        left.setOpaque(false);

        JLabel num = UiUtils.label(String.valueOf(index + 1), Theme.TEXT_MUTED, Theme.FONT_SMALL);
        num.setPreferredSize(new Dimension(24, 34));
        left.add(num);

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

        JButton remove = UiUtils.iconButton("×", Theme.TEXT_MUTED, Theme.DANGER);
        remove.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        remove.setToolTipText("Remove from playlist");
        remove.addActionListener(e -> {
            library.removeSongFromPlaylist(playlist.getId(), song.getId());
            refreshSongList();
        });

        row.add(left,   BorderLayout.WEST);
        row.add(info,   BorderLayout.CENTER);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 10));
        rightSide.setBackground(Theme.BG_MAIN);
        rightSide.setOpaque(false);
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
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(Theme.BG_HOVER); }
            @Override public void mouseExited (MouseEvent e) { row.setBackground(Theme.BG_MAIN); }
        });

        return row;
    }

    private void openAddSongsDialog() {
        Playlist sel = playlistList.getSelectedValue();
        if (sel == null) return;
        AddSongsDialog.showDialog(SwingUtilities.getWindowAncestor(this), sel, library, this::refreshSongList);
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
            Playlist p = library.createPlaylist(name.trim());
            refresh();
            playlistList.setSelectedValue(p, true);
            // Prompt right away to add songs to the newly created playlist!
            openAddSongsDialog();
        }
    }

    private void showPlaylistMenu(MouseEvent e, Playlist playlist) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem play = new JMenuItem("Play All");
        play.addActionListener(ev -> {
            List<Song> songs = library.getSongsForPlaylist(playlist.getId());
            if (!songs.isEmpty()) player.playSongFromList(songs, 0);
        });

        JMenuItem addSongs = new JMenuItem("Add Songs…");
        addSongs.addActionListener(ev -> AddSongsDialog.showDialog(
                SwingUtilities.getWindowAncestor(this), playlist, library, this::refreshSongList));

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

        menu.add(play);
        menu.add(addSongs);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
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
