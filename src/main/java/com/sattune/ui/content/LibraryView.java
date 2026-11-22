package com.sattune.ui.content;

import com.sattune.audio.PlayerController;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.util.ArtworkCache;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Library view — searchable, sortable song table with artwork.
 */
public class LibraryView extends JPanel {

    private final LibraryService   library;
    private final PlayerController player;

    private       List<Song>       displayedSongs;
    private final SongTableModel   tableModel;
    private final JTable           table;
    private final JTextField       searchField;

    public LibraryView(LibraryService library, PlayerController player) {
        this.library = library;
        this.player  = player;

        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout(0, 0));

        // ── Toolbar ─────────────────────────────────────────────────────────
        JPanel toolbar = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout(0, 0));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JLabel heading = UiUtils.label("Library", Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
        toolbar.add(heading, BorderLayout.WEST);

        searchField = new JTextField(22) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.BG_MAIN);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        searchField.putClientProperty("JTextField.placeholderText", "Search songs, artists…");
        searchField.setBackground(Theme.BG_MAIN);
        searchField.setForeground(Theme.TEXT_PRIMARY);
        searchField.setCaretColor(Theme.TEXT_PRIMARY);
        searchField.setFont(Theme.FONT_LABEL);
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        toolbar.add(searchField, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);

        // ── Table ────────────────────────────────────────────────────────────
        tableModel = new SongTableModel();
        table      = new JTable(tableModel) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        styleTable();

        // Double-click to play
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    int row = table.convertRowIndexToModel(table.getSelectedRow());
                    Song song = displayedSongs.get(row);
                    player.playSongFromList(displayedSongs, row);
                }
            }
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row);
                        showContextMenu(e.getComponent(), e.getX(), e.getY(),
                                displayedSongs.get(table.convertRowIndexToModel(row)));
                    }
                }
            }
        });

        JScrollPane scroll = UiUtils.scrollPane(table);
        add(scroll, BorderLayout.CENTER);

        // ── Status bar ───────────────────────────────────────────────────────
        JPanel status = UiUtils.panel(Theme.BG_SECONDARY, new FlowLayout(FlowLayout.LEFT, 16, 6));
        status.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));

        // ── Wire search ──────────────────────────────────────────────────────
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { doSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { doSearch(); }
            @Override public void changedUpdate(DocumentEvent e){ doSearch(); }
        });
    }

    public void refresh() {
        displayedSongs = library.search(searchField.getText());
        tableModel.setSongs(displayedSongs);
    }

    private void doSearch() {
        displayedSongs = library.search(searchField.getText());
        tableModel.setSongs(displayedSongs);
    }

    private void showContextMenu(Component comp, int x, int y, Song song) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BG_SECONDARY);

        JMenuItem play = new JMenuItem("Play");
        play.setForeground(Theme.TEXT_PRIMARY);
        play.setBackground(Theme.BG_SECONDARY);
        play.addActionListener(e -> player.playSongFromList(displayedSongs, displayedSongs.indexOf(song)));

        JMenuItem fav  = new JMenuItem(song.isFavourite() ? "Remove from Favourites" : "Add to Favourites");
        fav.setForeground(Theme.TEXT_PRIMARY);
        fav.setBackground(Theme.BG_SECONDARY);
        fav.addActionListener(e -> { library.toggleFavourite(song.getId()); refresh(); });

        JMenuItem addTo = new JMenuItem("Add to Playlist…");
        addTo.setForeground(Theme.TEXT_PRIMARY);
        addTo.setBackground(Theme.BG_SECONDARY);
        addTo.addActionListener(e -> showAddToPlaylistDialog(song));

        JMenuItem remove = new JMenuItem("Remove from Library");
        remove.setForeground(Theme.DANGER);
        remove.setBackground(Theme.BG_SECONDARY);
        remove.addActionListener(e -> { library.removeSong(song.getId()); refresh(); });

        menu.add(play); menu.add(fav); menu.add(addTo); menu.addSeparator(); menu.add(remove);
        menu.show(comp, x, y);
    }

    private void showAddToPlaylistDialog(Song song) {
        PlaylistUtils.showAddToPlaylistDialog(this, library, song, null);
    }

    // ── Table styling ─────────────────────────────────────────────────────────

    private void styleTable() {
        table.setBackground(Theme.BG_MAIN);
        table.setForeground(Theme.TEXT_PRIMARY);
        table.setGridColor(Theme.BORDER);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setRowHeight(52);
        table.setFont(Theme.FONT_LABEL);
        table.setSelectionBackground(Theme.BG_SELECTED);
        table.setSelectionForeground(Theme.TEXT_PRIMARY);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader header = table.getTableHeader();
        header.setBackground(Theme.BG_SECONDARY);
        header.setForeground(Theme.TEXT_SECONDARY);
        header.setFont(Theme.FONT_SMALL);
        header.setPreferredSize(new Dimension(0, 36));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));

        // Column widths
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(52);  cm.getColumn(0).setMaxWidth(56);   // art
        cm.getColumn(1).setPreferredWidth(260); // title
        cm.getColumn(2).setPreferredWidth(180); // artist
        cm.getColumn(3).setPreferredWidth(180); // album
        cm.getColumn(4).setPreferredWidth(70);  cm.getColumn(4).setMaxWidth(80);   // dur

        // Column renderers
        cm.getColumn(0).setCellRenderer(new ArtworkCellRenderer());
        cm.getColumn(1).setCellRenderer(new TextCellRenderer(JLabel.LEFT, Theme.TEXT_PRIMARY));
        cm.getColumn(2).setCellRenderer(new TextCellRenderer(JLabel.LEFT, Theme.TEXT_SECONDARY));
        cm.getColumn(3).setCellRenderer(new TextCellRenderer(JLabel.LEFT, Theme.TEXT_MUTED));
        cm.getColumn(4).setCellRenderer(new TextCellRenderer(JLabel.RIGHT, Theme.TEXT_MUTED));

        // Sort
        TableRowSorter<SongTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(1, String.CASE_INSENSITIVE_ORDER);
        sorter.setComparator(2, String.CASE_INSENSITIVE_ORDER);
        table.setRowSorter(sorter);
    }

    // ── Table model ───────────────────────────────────────────────────────────

    static class SongTableModel extends AbstractTableModel {
        private static final String[] COLS = {"", "Title", "Artist", "Album", "Duration"};
        private List<Song> songs = List.of();

        void setSongs(List<Song> s) { this.songs = s; fireTableDataChanged(); }

        @Override public int getRowCount()    { return songs.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override public Object getValueAt(int row, int col) {
            Song s = songs.get(row);
            return switch (col) {
                case 0 -> s.getFilePath(); // artwork uses file path
                case 1 -> s.getTitle();
                case 2 -> s.getArtist();
                case 3 -> s.getAlbum();
                case 4 -> s.getDurationFormatted();
                default -> "";
            };
        }
    }

    // ── Renderers ────────────────────────────────────────────────────────────

    static class TextCellRenderer extends DefaultTableCellRenderer {
        private final int alignment;
        private final Color fgColor;

        TextCellRenderer(int alignment, Color fgColor) {
            this.alignment = alignment;
            this.fgColor = fgColor;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, col);
            label.setBackground(selected ? Theme.BG_SELECTED : Theme.BG_MAIN);
            label.setForeground(selected ? Theme.TEXT_PRIMARY : fgColor);
            label.setHorizontalAlignment(alignment);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            String str = value != null ? value.toString() : "";
            label.setText(str);
            label.setToolTipText(str.isBlank() ? null : str);
            return label;
        }
    }

    class ArtworkCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, "", selected, focused, row, col);
            label.setBackground(selected ? Theme.BG_SELECTED : Theme.BG_MAIN);
            label.setHorizontalAlignment(JLabel.CENTER);

            String path = (String) value;
            if (path == null) {
                label.setIcon(ArtworkCache.defaultIcon(36));
                return label;
            }

            ImageIcon cached = ArtworkCache.getInstance().getCached(path, 36);
            if (cached != null) {
                label.setIcon(cached);
            } else {
                label.setIcon(ArtworkCache.defaultIcon(36));
                ArtworkCache.getInstance().loadAsync(path, 36, icon -> {
                    table.repaint();
                });
            }
            return label;
        }
    }
}
