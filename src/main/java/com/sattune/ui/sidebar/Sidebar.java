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
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10)) {
            private boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                if (hov) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.BG_HOVER);
                    g2.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, Theme.ARC, Theme.ARC);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lbl = UiUtils.label(text, Theme.TEXT_SECONDARY, Theme.FONT_SMALL);
        row.add(lbl);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { lbl.setForeground(Theme.TEXT_PRIMARY); }
            @Override public void mouseExited(MouseEvent e)  { lbl.setForeground(Theme.TEXT_SECONDARY); }
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        });
        return row;
    }

    // ── NavItem ───────────────────────────────────────────────────────────────

    static class NavItem extends JPanel {
        final String viewId;
        private final JLabel ico;
        private final JLabel lbl;
        private boolean isActive = false;
        private boolean hovered  = false;

        NavItem(String viewId, String icon, String title) {
            this.viewId = viewId;
            setLayout(new FlowLayout(FlowLayout.LEFT, 14, 10));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            ico = new JLabel(icon);
            ico.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            ico.setForeground(Theme.TEXT_SECONDARY);
            ico.setPreferredSize(new Dimension(20, 20));

            lbl = UiUtils.label(title, Theme.TEXT_SECONDARY, Theme.FONT_LABEL);
            add(ico); add(lbl);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
            });
        }

        void setActive(boolean active) { this.isActive = active; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Theme.ARC;
            int margin = 6;
            if (isActive) {
                g2.setColor(Theme.BG_SELECTED);
                g2.fillRoundRect(margin, 2, getWidth() - margin * 2, getHeight() - 4, arc, arc);
                // Subtle accent curved indicator pill
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(margin + 2, 8, 4, getHeight() - 16, 2, 2);
            } else if (hovered) {
                g2.setColor(Theme.BG_HOVER);
                g2.fillRoundRect(margin, 2, getWidth() - margin * 2, getHeight() - 4, arc, arc);
            }
            g2.dispose();

            // Update child label colors
            ico.setForeground(isActive ? Theme.ACCENT : (hovered ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY));
            lbl.setForeground(isActive ? Theme.TEXT_PRIMARY : (hovered ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY));
            if (isActive) {
                lbl.setFont(Theme.FONT_LABEL.deriveFont(Font.BOLD));
            } else {
                lbl.setFont(Theme.FONT_LABEL);
            }

            super.paintComponent(g);
        }
    }
}
