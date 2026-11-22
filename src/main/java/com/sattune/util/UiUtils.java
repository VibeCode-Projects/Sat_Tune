package com.sattune.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable UI factory / utility methods for consistent component styling.
 */
public final class UiUtils {

    private UiUtils() {}

    // ── Labels ────────────────────────────────────────────────────────────────

    public static JLabel label(String text, Color color, Font font) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(font);
        l.setOpaque(false);
        return l;
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    /**
     * Icon-style flat button — no border, transparent background,
     * accent highlight on hover.
     */
    public static JButton iconButton(String text, Color normalColor, Color hoverColor) {
        JButton b = new JButton(text) {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hovered ? hoverColor : normalColor);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth()  - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(normalColor);
        return b;
    }

    /**
     * Text button with a rounded filled background.
     */
    public static JButton textButton(String text) {
        JButton b = new JButton(text) {
            private boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                    @Override public void mouseExited (MouseEvent e) { hov = false; repaint(); }
                });
                setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? Theme.BG_HOVER : Theme.BG_SECONDARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC));
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth()  - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(Theme.TEXT_PRIMARY);
        b.setFont(Theme.FONT_LABEL);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ── Separators ────────────────────────────────────────────────────────────

    public static JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        sep.setBackground(Theme.BG_MAIN);
        return sep;
    }

    // ── Panels ────────────────────────────────────────────────────────────────

    public static JPanel panel(Color bg, LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(bg);
        p.setOpaque(true);
        return p;
    }

    // ── Sliders ───────────────────────────────────────────────────────────────

    public static JSlider thinSlider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int h    = getHeight();
                int tw   = getWidth();
                int trackH = 3;
                int ty   = (h - trackH) / 2;
                float ratio = (getValue() - getMinimum()) / (float)(getMaximum() - getMinimum());
                int filled = (int)(tw * ratio);

                // Track background
                g2.setColor(Theme.BORDER);
                g2.fillRoundRect(0, ty, tw, trackH, 3, 3);

                // Filled portion
                if (filled > 0) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(0, ty, filled, trackH, 3, 3);
                }

                // Thumb — small circle
                int thumbR = 6;
                int tx = filled - thumbR;
                tx = Math.max(0, Math.min(tw - thumbR * 2, tx));
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.fillOval(tx, (h - thumbR * 2) / 2, thumbR * 2, thumbR * 2);
                g2.dispose();
            }
        };
        s.setOpaque(false);
        s.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        s.setPaintLabels(false);
        s.setPaintTicks(false);
        s.setFocusable(false);
        s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return s;
    }

    // ── Rounded artwork panel ─────────────────────────────────────────────────

    public static JLabel artworkLabel(int size) {
        JLabel l = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                if (getIcon() == null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.BG_SECONDARY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC, Theme.ARC);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        l.setPreferredSize(new Dimension(size, size));
        l.setMinimumSize(new Dimension(size, size));
        l.setMaximumSize(new Dimension(size, size));
        l.setOpaque(false);
        return l;
    }

    // ── Scroll pane ───────────────────────────────────────────────────────────

    public static JScrollPane scrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(Theme.BG_MAIN);
        sp.getViewport().setBackground(Theme.BG_MAIN);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    // ── Color transition helper ───────────────────────────────────────────────

    public static Color blend(Color a, Color b, float t) {
        int r = (int)(a.getRed()   + t * (b.getRed()   - a.getRed()));
        int g = (int)(a.getGreen() + t * (b.getGreen() - a.getGreen()));
        int bl= (int)(a.getBlue()  + t * (b.getBlue()  - a.getBlue()));
        return new Color(r, g, bl);
    }

    public static void setHandCursor(JComponent c) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
