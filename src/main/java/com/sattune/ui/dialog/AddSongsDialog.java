package com.sattune.ui.dialog;

import com.sattune.model.Playlist;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Clean, modern modal dialog allowing the user to search and select multiple
 * songs from their library to add into a playlist.
 */
public class AddSongsDialog extends JDialog {

    private final Playlist       playlist;
    private final LibraryService library;
    private final Runnable       onSuccess;

    private final Set<String>    existingSongIds = new HashSet<>();
    private final Set<String>    selectedSongIds = new HashSet<>();
    private final List<Song>     allLibrarySongs = new ArrayList<>();
    private final List<Song>     filteredSongs   = new ArrayList<>();

    private final SongPickerTableModel tableModel;
    private final JTable               table;
    private final JTextField           searchField;
    private final JLabel               selectedCountLabel;
    private final JButton              addBtn;

    public static void showDialog(Window owner, Playlist playlist, LibraryService library, Runnable onSuccess) {
        AddSongsDialog dialog = new AddSongsDialog(owner, playlist, library, onSuccess);
        dialog.setVisible(true);
    }

    public AddSongsDialog(Window owner, Playlist playlist, LibraryService library, Runnable onSuccess) {
        super(owner, "Add Songs to " + playlist.getName(), ModalityType.APPLICATION_MODAL);
        this.playlist  = playlist;
        this.library   = library;
        this.onSuccess = onSuccess;

        existingSongIds.addAll(playlist.getSongIds());
        allLibrarySongs.addAll(library.getAllSongs());
        filteredSongs.addAll(allLibrarySongs);

        setSize(700, 520);
        setMinimumSize(new Dimension(580, 420));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout(0, 0));

        // ── Header ───────────────────────────────────────────────────────────
        JPanel header = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout(0, 4));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JLabel title = UiUtils.label("Add Songs to " + playlist.getName(), Theme.TEXT_PRIMARY, Theme.FONT_HEADING);
        JLabel subtitle = UiUtils.label("Select tracks from your library to add to this playlist.", Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // ── Center (Search + Table) ──────────────────────────────────────────
        JPanel center = UiUtils.panel(Theme.BG_MAIN, new BorderLayout(0, 8));
        center.setBorder(BorderFactory.createEmptyBorder(12, 18, 10, 18));

        // Search & select-all bar
        JPanel searchBar = UiUtils.panel(Theme.BG_MAIN, new BorderLayout(10, 0));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search songs by title, artist, or album…");
        searchField.setBackground(Theme.BG_SECONDARY);
        searchField.setForeground(Theme.TEXT_PRIMARY);
        searchField.setCaretColor(Theme.TEXT_PRIMARY);
        searchField.setFont(Theme.FONT_LABEL);
        searchField.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        searchBar.add(searchField, BorderLayout.CENTER);

        JPanel selectBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        selectBtns.setBackground(Theme.BG_MAIN);
        JButton selectAllBtn = UiUtils.textButton("Select All");
        JButton deselectAllBtn = UiUtils.textButton("Deselect All");
        selectAllBtn.setFont(Theme.FONT_SMALL);
        deselectAllBtn.setFont(Theme.FONT_SMALL);
        selectAllBtn.addActionListener(e -> selectAllFiltered(true));
        deselectAllBtn.addActionListener(e -> selectAllFiltered(false));
        selectBtns.add(selectAllBtn);
        selectBtns.add(deselectAllBtn);
        searchBar.add(selectBtns, BorderLayout.EAST);

        center.add(searchBar, BorderLayout.NORTH);

        // Song table
        tableModel = new SongPickerTableModel();
        table = new JTable(tableModel);
        styleTable();

        JScrollPane scrollPane = UiUtils.scrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        center.add(scrollPane, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        // ── Bottom action bar ────────────────────────────────────────────────
        JPanel bottom = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout(0, 0));
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));

        selectedCountLabel = UiUtils.label("0 tracks selected", Theme.TEXT_MUTED, Theme.FONT_SMALL);
        bottom.add(selectedCountLabel, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBackground(Theme.BG_SECONDARY);

        JButton cancelBtn = UiUtils.textButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        addBtn = UiUtils.accentButton("＋ Add to Playlist");
        addBtn.setEnabled(false);
        addBtn.addActionListener(e -> onAddSelectedSongs());

        actions.add(cancelBtn);
        actions.add(addBtn);
        bottom.add(actions, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);

        // ── Wire search events ───────────────────────────────────────────────
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
    }

    private void styleTable() {
        table.setBackground(Theme.BG_MAIN);
        table.setForeground(Theme.TEXT_PRIMARY);
        table.setGridColor(Theme.BORDER);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setRowHeight(38);
        table.setFont(Theme.FONT_LABEL);
        table.setSelectionBackground(Theme.BG_SELECTED);
        table.setSelectionForeground(Theme.TEXT_PRIMARY);
        table.setFillsViewportHeight(true);

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(44);  cm.getColumn(0).setMaxWidth(50);   // Checkbox
        cm.getColumn(1).setPreferredWidth(220); // Title
        cm.getColumn(2).setPreferredWidth(160); // Artist
        cm.getColumn(3).setPreferredWidth(140); // Album
        cm.getColumn(4).setPreferredWidth(65);  cm.getColumn(4).setMaxWidth(75);   // Duration
        cm.getColumn(5).setPreferredWidth(95);  cm.getColumn(5).setMaxWidth(110);  // Status

        // Text renderers with tooltips and disabled styling for songs already in playlist
        DefaultTableCellRenderer textRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, col);
                l.setBackground(selected ? Theme.BG_SELECTED : Theme.BG_MAIN);
                l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

                Song song = filteredSongs.get(row);
                boolean inPlaylist = existingSongIds.contains(song.getId());

                if (inPlaylist) {
                    l.setForeground(Theme.TEXT_MUTED);
                } else {
                    l.setForeground(col == 1 ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
                }

                String text = value != null ? value.toString() : "";
                l.setText(text);
                l.setToolTipText(text.isBlank() ? null : text);
                return l;
            }
        };

        for (int i = 1; i <= 4; i++) {
            cm.getColumn(i).setCellRenderer(textRenderer);
        }

        // Status renderer
        cm.getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, col);
                l.setBackground(selected ? Theme.BG_SELECTED : Theme.BG_MAIN);
                l.setHorizontalAlignment(CENTER);

                Song song = filteredSongs.get(row);
                boolean inPlaylist = existingSongIds.contains(song.getId());
                if (inPlaylist) {
                    l.setForeground(Theme.ACCENT_DIM);
                    l.setText("In playlist");
                } else if (selectedSongIds.contains(song.getId())) {
                    l.setForeground(Theme.SUCCESS);
                    l.setText("Selected");
                } else {
                    l.setText("");
                }
                return l;
            }
        });
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredSongs.clear();
        if (query.isEmpty()) {
            filteredSongs.addAll(allLibrarySongs);
        } else {
            for (Song s : allLibrarySongs) {
                if (s.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || s.getArtist().toLowerCase(Locale.ROOT).contains(query)
                        || s.getAlbum().toLowerCase(Locale.ROOT).contains(query)) {
                    filteredSongs.add(s);
                }
            }
        }
        tableModel.fireTableDataChanged();
    }

    private void selectAllFiltered(boolean select) {
        for (Song s : filteredSongs) {
            if (!existingSongIds.contains(s.getId())) {
                if (select) {
                    selectedSongIds.add(s.getId());
                } else {
                    selectedSongIds.remove(s.getId());
                }
            }
        }
        updateSelectionStatus();
        tableModel.fireTableDataChanged();
    }

    private void updateSelectionStatus() {
        int count = selectedSongIds.size();
        selectedCountLabel.setText(count + " track" + (count == 1 ? "" : "s") + " selected");
        addBtn.setEnabled(count > 0);
    }

    private void onAddSelectedSongs() {
        if (selectedSongIds.isEmpty()) return;
        int added = 0;
        for (String songId : selectedSongIds) {
            library.addSongToPlaylist(playlist.getId(), songId);
            added++;
        }
        if (onSuccess != null) {
            onSuccess.run();
        }
        dispose();
    }

    // ── Table Model ───────────────────────────────────────────────────────────

    private class SongPickerTableModel extends AbstractTableModel {
        private final String[] COLS = {"", "Title", "Artist", "Album", "Duration", "Status"};

        @Override public int getRowCount() { return filteredSongs.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return (c == 0) ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 0) {
                Song song = filteredSongs.get(row);
                // Disabled if song is already in this playlist
                return !existingSongIds.contains(song.getId());
            }
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            Song s = filteredSongs.get(row);
            return switch (col) {
                case 0 -> existingSongIds.contains(s.getId()) || selectedSongIds.contains(s.getId());
                case 1 -> s.getTitle();
                case 2 -> s.getArtist();
                case 3 -> s.getAlbum();
                case 4 -> s.getDurationFormatted();
                case 5 -> existingSongIds.contains(s.getId()) ? "In playlist"
                        : (selectedSongIds.contains(s.getId()) ? "Selected" : "");
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object val, int row, int col) {
            if (col == 0 && val instanceof Boolean b) {
                Song s = filteredSongs.get(row);
                if (!existingSongIds.contains(s.getId())) {
                    if (b) {
                        selectedSongIds.add(s.getId());
                    } else {
                        selectedSongIds.remove(s.getId());
                    }
                    updateSelectionStatus();
                    fireTableCellUpdated(row, 0);
                    fireTableCellUpdated(row, 5);
                }
            }
        }
    }
}
