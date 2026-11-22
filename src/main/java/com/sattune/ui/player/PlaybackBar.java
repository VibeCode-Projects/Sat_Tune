package com.sattune.ui.player;

import com.sattune.audio.AudioEngine;
import com.sattune.audio.PlayerController;
import com.sattune.model.Song;
import com.sattune.service.LibraryService;
import com.sattune.ui.content.PlaylistUtils;
import com.sattune.util.ArtworkCache;
import com.sattune.util.Theme;
import com.sattune.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bottom persistent playback bar.
 * Layout: [artwork | title/artist | fav]  [controls + progress]  [volume]
 */
public class PlaybackBar extends JPanel {

    private final PlayerController player;
    private final LibraryService   library;

    // Left
    private final JLabel artworkLabel  = UiUtils.artworkLabel(Theme.ARTWORK_SIZE_MD);
    private final JLabel titleLabel;
    private final JLabel artistLabel;
    private final JButton  favBtn;
    private final JButton  addToPlaylistBtn;
    private final JButton  shufflePlayPill; // "🔀 Play Random" pill button when no song is playing

    // Center controls
    private final JButton shuffleBtn;
    private final JButton prevBtn;
    private final JButton playPauseBtn;
    private final JButton nextBtn;
    private final JButton repeatBtn;
    private final JSlider progressSlider;
    private final JLabel  timeLabel;
    private final JLabel  durationLabel;

    // Right
    private final JSlider volumeSlider;
    private final JLabel  volIcon;

    private boolean seekingProgress = false;

    public PlaybackBar(PlayerController player, LibraryService library) {
        this.player  = player;
        this.library = library;

        setBackground(Theme.BAR_BG);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        setPreferredSize(new Dimension(0, Theme.PLAYBAR_HEIGHT));
        setLayout(new BorderLayout(0, 0));

        artworkLabel.setIcon(ArtworkCache.defaultIcon(Theme.ARTWORK_SIZE_MD));

        titleLabel  = UiUtils.label("No song playing", Theme.TEXT_PRIMARY, Theme.FONT_LABEL);
        artistLabel = UiUtils.label("Click Play or Shuffle Random", Theme.TEXT_MUTED, Theme.FONT_SMALL);

        shufflePlayPill = new JButton("🔀 Shuffle Library") {
            private boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    @Override public void mouseExited (MouseEvent e) { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? Theme.ACCENT : Theme.BG_SECONDARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.ARC_LG, Theme.ARC_LG);
                g2.setColor(hov ? Theme.BG_MAIN : Theme.ACCENT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        shufflePlayPill.setFont(Theme.FONT_SMALL);
        shufflePlayPill.setPreferredSize(new Dimension(136, 26));
        shufflePlayPill.setBorderPainted(false);
        shufflePlayPill.setContentAreaFilled(false);
        shufflePlayPill.setFocusPainted(false);
        shufflePlayPill.setOpaque(false);
        shufflePlayPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        shuffleBtn   = makeCtrlBtn("⇄", Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY,  18);
        prevBtn      = makeCtrlBtn("⏮",  Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY,  22);
        playPauseBtn = makeCtrlBtn("▶",  Theme.TEXT_PRIMARY,   Theme.ACCENT,        26);
        nextBtn      = makeCtrlBtn("⏭",  Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY,  22);
        repeatBtn    = makeCtrlBtn("⟳",  Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY,  18);
        favBtn       = makeCtrlBtn("♡",  Theme.TEXT_SECONDARY, Theme.DANGER,        16);
        addToPlaylistBtn = makeCtrlBtn("▤", Theme.TEXT_SECONDARY, Theme.ACCENT, 16);
        addToPlaylistBtn.setToolTipText("Add to Playlist");

        timeLabel     = UiUtils.label("0:00",  Theme.TEXT_MUTED, Theme.FONT_SMALL);
        durationLabel = UiUtils.label("0:00",  Theme.TEXT_MUTED, Theme.FONT_SMALL);

        progressSlider = UiUtils.thinSlider(0, 1000, 0);
        volumeSlider   = UiUtils.thinSlider(0, 100,  (int)(player.getVolume() * 100));
        volIcon        = UiUtils.label("🔊", Theme.TEXT_SECONDARY, Theme.FONT_SMALL);

        buildLayout();
        wireEvents();
        registerPlayerListener();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildLayout() {
        // Left: artwork + info + fav
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setBackground(Theme.BAR_BG);
        left.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        left.setPreferredSize(new Dimension(260, Theme.PLAYBAR_HEIGHT));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(Theme.BAR_BG);
        titleLabel.setAlignmentX(0f);
        artistLabel.setAlignmentX(0f);
        info.add(Box.createVerticalGlue());
        info.add(titleLabel);
        info.add(Box.createVerticalStrut(2));
        info.add(artistLabel);
        info.add(Box.createVerticalGlue());

        JPanel leftFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftFlow.setBackground(Theme.BAR_BG);
        leftFlow.setPreferredSize(new Dimension(300, Theme.PLAYBAR_HEIGHT));
        leftFlow.add(artworkLabel);
        leftFlow.add(info);
        leftFlow.add(favBtn);
        leftFlow.add(addToPlaylistBtn);
        leftFlow.add(shufflePlayPill);
        favBtn.setVisible(false); // Only visible when a song is playing
        addToPlaylistBtn.setVisible(false);
        shufflePlayPill.setVisible(true); // Shown when no song is playing
        add(leftFlow, BorderLayout.WEST);

        // Center: controls + progress
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(Theme.BAR_BG);
        center.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        controls.setBackground(Theme.BAR_BG);
        controls.add(shuffleBtn);
        controls.add(prevBtn);
        controls.add(playPauseBtn);
        controls.add(nextBtn);
        controls.add(repeatBtn);
        controls.setAlignmentX(0.5f);
        center.add(controls);

        JPanel progressRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        progressRow.setBackground(Theme.BAR_BG);
        progressRow.setAlignmentX(0.5f);
        progressRow.add(timeLabel);
        progressSlider.setPreferredSize(new Dimension(320, 20));
        progressRow.add(progressSlider);
        progressRow.add(durationLabel);
        center.add(progressRow);

        add(center, BorderLayout.CENTER);

        // Right: volume
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(Theme.BAR_BG);
        right.setPreferredSize(new Dimension(180, Theme.PLAYBAR_HEIGHT));
        right.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
        volumeSlider.setPreferredSize(new Dimension(90, 20));
        right.add(volIcon);
        right.add(volumeSlider);
        add(right, BorderLayout.EAST);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private void wireEvents() {
        playPauseBtn.addActionListener(e -> player.togglePlayPause());
        shufflePlayPill.addActionListener(e -> player.playRandom());
        prevBtn.addActionListener(e -> player.previous());
        nextBtn.addActionListener(e -> player.next());

        shuffleBtn.addActionListener(e -> {
            boolean on = !player.isShuffleOn();
            player.setShuffleOn(on);
            shuffleBtn.setForeground(on ? Theme.ACCENT : Theme.TEXT_SECONDARY);
        });

        repeatBtn.addActionListener(e -> {
            player.cycleRepeat();
            int mode = player.getRepeatMode();
            repeatBtn.setText(mode == 1 ? "⟳₁" : "⟳");
            repeatBtn.setForeground(mode > 0 ? Theme.ACCENT : Theme.TEXT_SECONDARY);
        });

        favBtn.addActionListener(e -> {
            Song s = player.getCurrentSong();
            if (s != null) {
                library.toggleFavourite(s.getId());
                favBtn.setText(s.isFavourite() ? "♥" : "♡");
                favBtn.setForeground(s.isFavourite() ? Theme.DANGER : Theme.TEXT_SECONDARY);
            }
        });

        addToPlaylistBtn.addActionListener(e -> {
            Song s = player.getCurrentSong();
            if (s != null) {
                PlaylistUtils.showAddToPlaylistDialog(this, library, s, null);
            }
        });

        // Progress seek
        progressSlider.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { seekingProgress = true; }
            @Override public void mouseReleased(MouseEvent e) {
                seekingProgress = false;
                Song s = player.getCurrentSong();
                if (s != null && s.getDurationMs() > 0) {
                    long target = (long)(progressSlider.getValue() / 1000.0 * s.getDurationMs());
                    player.seekTo(target);
                }
            }
        });

        // Volume
        volumeSlider.addChangeListener(e -> {
            if (!volumeSlider.getValueIsAdjusting()) {
                player.setVolume(volumeSlider.getValue() / 100f);
            }
        });
    }

    private void registerPlayerListener() {
        player.addListener(new PlayerController.PlayerListener() {
            @Override public void onSongChanged(Song song) { updateSongInfo(song); }
            @Override public void onStateChanged(AudioEngine.State state) { updatePlayPauseBtn(state); }
            @Override public void onPositionChanged(long posMs, long durMs) {
                if (!seekingProgress) updateProgress(posMs, durMs);
            }
            @Override public void onError(String message) {
                titleLabel.setText("Error");
                artistLabel.setText(message);
            }
        });
    }

    // ── Update methods ────────────────────────────────────────────────────────

    private void updateSongInfo(Song song) {
        if (song != null) {
            String t = song.getTitle();
            String a = song.getArtist();
            titleLabel.setText(truncate(t, 26));
            titleLabel.setToolTipText(t);
            artistLabel.setText(truncate(a, 30));
            artistLabel.setToolTipText(a);
            favBtn.setText(song.isFavourite() ? "♥" : "♡");
            favBtn.setForeground(song.isFavourite() ? Theme.DANGER : Theme.TEXT_SECONDARY);
            favBtn.setVisible(true);
            addToPlaylistBtn.setVisible(true);
            shufflePlayPill.setVisible(false);

            // Load artwork asynchronously
            ImageIcon cached = ArtworkCache.getInstance().getCached(song.getFilePath(), Theme.ARTWORK_SIZE_MD);
            if (cached != null) {
                artworkLabel.setIcon(cached);
            } else {
                artworkLabel.setIcon(ArtworkCache.defaultIcon(Theme.ARTWORK_SIZE_MD));
                ArtworkCache.getInstance().loadAsync(song.getFilePath(), Theme.ARTWORK_SIZE_MD, icon -> {
                    if (player.getCurrentSong() != null && player.getCurrentSong().getId().equals(song.getId())) {
                        artworkLabel.setIcon(icon);
                    }
                });
            }
        } else {
            titleLabel.setText("No song playing");
            titleLabel.setToolTipText(null);
            artistLabel.setText("Click Play or Shuffle Random");
            artistLabel.setToolTipText(null);
            favBtn.setVisible(false);
            addToPlaylistBtn.setVisible(false);
            shufflePlayPill.setVisible(true);
            artworkLabel.setIcon(ArtworkCache.defaultIcon(Theme.ARTWORK_SIZE_MD));
        }
    }

    private void updatePlayPauseBtn(AudioEngine.State state) {
        playPauseBtn.setText(state == AudioEngine.State.PLAYING ? "⏸" : "▶");
    }

    private void updateProgress(long posMs, long durMs) {
        if (durMs > 0) {
            posMs = Math.min(posMs, durMs); // Clamp: elapsed never exceeds track duration
            int val = (int)((posMs / (double) durMs) * 1000);
            progressSlider.setValue(Math.min(1000, val));
        } else {
            progressSlider.setValue(0);
        }
        timeLabel.setText(fmt(posMs));
        durationLabel.setText(fmt(durMs));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String fmt(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static JButton makeCtrlBtn(String text, Color normal, Color hover, int fontSize) {
        JButton b = UiUtils.iconButton(text, normal, hover);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        b.setPreferredSize(new Dimension(fontSize + 16, fontSize + 16));
        return b;
    }
}
