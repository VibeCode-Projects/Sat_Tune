package com.sattune.ui.content;

import com.sattune.model.AppData;
import com.sattune.service.LibraryService;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Minimal settings panel.
 */
public class SettingsView extends JPanel {

    private final LibraryService library;

    public SettingsView(LibraryService library) {
        this.library = library;
        setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout());

        JPanel toolbar = UiUtils.panel(Theme.BG_SECONDARY, new BorderLayout());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 20)));
        toolbar.add(UiUtils.label("Settings", Theme.TEXT_PRIMARY, Theme.FONT_HEADING), BorderLayout.WEST);
        add(toolbar, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setBackground(Theme.BG_MAIN);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        // About section
        content.add(sectionLabel("About"));
        JPanel about = card();
        about.add(row("Application", "Sattune"));
        about.add(row("Version",     "1.0.0"));
        about.add(row("Theme",       "Dark Minimal"));
        about.add(row("Audio",       "Java Sound API + JLayer"));
        content.add(about);
        content.add(Box.createVerticalStrut(20));

        // Library info
        content.add(sectionLabel("Library"));
        JPanel lib = card();
        lib.add(row("Total songs", String.valueOf(library.getAllSongs().size())));
        lib.add(row("Playlists",   String.valueOf(library.getPlaylists().size())));
        lib.add(row("Favourites",  String.valueOf(library.getFavourites().size())));
        content.add(lib);
        content.add(Box.createVerticalStrut(20));

        // Note
        JLabel note = UiUtils.label("Sattune — Just you and your music.", Theme.TEXT_MUTED, Theme.FONT_SMALL);
        note.setAlignmentX(0f);
        content.add(note);

        content.add(Box.createVerticalGlue());
        add(UiUtils.scrollPane(content), BorderLayout.CENTER);
    }

    private JLabel sectionLabel(String text) {
        JLabel l = UiUtils.label(text.toUpperCase(), Theme.TEXT_MUTED, Theme.FONT_SMALL);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        l.setAlignmentX(0f);
        return l;
    }

    private JPanel card() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.BG_SECONDARY);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(4, 16, 4, 16)));
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));
        return p;
    }

    private JPanel row(String key, String val) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG_SECONDARY);
        p.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        p.add(UiUtils.label(key, Theme.TEXT_SECONDARY, Theme.FONT_LABEL), BorderLayout.WEST);
        p.add(UiUtils.label(val, Theme.TEXT_PRIMARY,   Theme.FONT_LABEL), BorderLayout.EAST);
        return p;
    }
}
