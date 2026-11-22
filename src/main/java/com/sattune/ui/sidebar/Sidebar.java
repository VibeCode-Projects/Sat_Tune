package com.sattune.ui.sidebar;

import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Left navigation sidebar.
 * Emits navigation events via listener callbacks.
 */
public class Sidebar extends JPanel {

    public interface NavListener {
        void onNavigate(String view);
        void onImportFiles();
        void onImportFolder();
        void onOpenSettings();
    }

    private final List<NavItem>  items    = new ArrayList<>();
    private       String         active   = "HOME";
    private final List<NavListener> listeners = new ArrayList<>();

    public Sidebar() {
        setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 0));
        setBackground(Theme.BG_SIDEBAR);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        build();
    }

    public void addNavListener(NavListener l) { listeners.add(l); }

    public void setActiveView(String view) {
        this.active = view;
        items.forEach(i -> i.setActive(i.viewId.equals(view)));
        repaint();
    }

    // ── Build ────────────────────────────────────────────────────────────────

    private void build() {
        // Top — Logo
        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 18));
        logoPanel.setBackground(Theme.BG_SIDEBAR);
        JLabel logo = new JLabel("♪");
        logo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        logo.setForeground(Theme.ACCENT);
        JLabel name = UiUtils.label("Sattune", Theme.TEXT_PRIMARY, Theme.FONT_LOGO);
        logoPanel.add(logo);
        logoPanel.add(name);
        add(logoPanel, BorderLayout.NORTH);

        // Center nav
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(Theme.BG_SIDEBAR);
        nav.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        addSection(nav, "LIBRARY");
        addNavItem(nav, "HOME",     "⌂", "Home");
        addNavItem(nav, "LIBRARY",  "≡", "Your Library");
        addNavItem(nav, "PLAYLISTS","▤", "Playlists");
        addNavItem(nav, "RECENT",   "↺", "Recently Played");
        addNavItem(nav, "FAVOURITES","♥","Favourites");

        nav.add(Box.createVerticalGlue());
        add(nav, BorderLayout.CENTER);

        // Bottom — settings + import
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(Theme.BG_SIDEBAR);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));

        bottom.add(bottomButton("⚙  Settings",    () -> listeners.forEach(NavListener::onOpenSettings)));
        bottom.add(bottomButton("＋  Add Songs",   () -> listeners.forEach(NavListener::onImportFiles)));
        bottom.add(bottomButton("＋  Add Folder",  () -> listeners.forEach(NavListener::onImportFolder)));
        add(bottom, BorderLayout.SOUTH);
    }

    private void addSection(JPanel parent, String label) {
        JLabel lbl = UiUtils.label(label, Theme.TEXT_MUTED, Theme.FONT_SMALL);
        lbl.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 0));
        lbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        parent.add(lbl);
    }

    private void addNavItem(JPanel parent, String viewId, String icon, String title) {
        NavItem item = new NavItem(viewId, icon, title);
        items.add(item);
        parent.add(item);
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                setActiveView(viewId);
                listeners.forEach(l -> l.onNavigate(viewId));
            }
        });
    }

    private JComponent bottomButton(String text, Runnable action) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        row.setBackground(Theme.BG_SIDEBAR);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lbl = UiUtils.label(text, Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        row.add(lbl);

        Color[] hover = {Theme.BG_SIDEBAR};
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hover[0] = Theme.BG_HOVER; row.setBackground(Theme.BG_HOVER); lbl.setForeground(Theme.TEXT_PRIMARY);
            }
            @Override public void mouseExited(MouseEvent e) {
                hover[0] = Theme.BG_SIDEBAR; row.setBackground(Theme.BG_SIDEBAR); lbl.setForeground(Theme.TEXT_SECONDARY);
            }
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        });
        return row;
    }

    // ── NavItem ───────────────────────────────────────────────────────────────

    static class NavItem extends JPanel {
        final String viewId;
        private boolean isActive = false;
        private boolean hovered  = false;

        NavItem(String viewId, String icon, String title) {
            this.viewId = viewId;
            setLayout(new FlowLayout(FlowLayout.LEFT, 12, 9));
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel ico = new JLabel(icon);
            ico.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            ico.setForeground(Theme.TEXT_SECONDARY);
            ico.setPreferredSize(new Dimension(18, 18));

            JLabel lbl = UiUtils.label(title, Theme.TEXT_SECONDARY, Theme.FONT_LABEL);
            add(ico); add(lbl);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        void setActive(boolean active) { this.isActive = active; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (isActive) {
                g2.setColor(Theme.BG_SELECTED);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Accent left bar
                g2.setColor(Theme.ACCENT);
                g2.fillRect(0, 0, 3, getHeight());
            } else if (hovered) {
                g2.setColor(Theme.BG_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();

            // Update child label colors
            Component[] children = getComponents();
            for (Component c : children) {
                if (c instanceof JLabel lbl) {
                    lbl.setForeground(isActive ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
                }
            }
            super.paintComponent(g);
        }
    }
}
