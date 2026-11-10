package app;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.awt.FileDialog;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.prefs.Preferences;

/** MPL - minimal music player. Spotify-like layout. */
public final class UI {
    private final JFrame frame;
    private final DefaultListModel<Track> model = new DefaultListModel<>();
    private final DefaultListModel<Track> viewModel = new DefaultListModel<>();
    private final JList<Track> list = new JList<>(viewModel);
    private final JTextField searchField = new JTextField();
    private JTabbedPane sidebar;
    private JDialog eqDialog;
    private volatile boolean lyricsBusy;
    private final JLabel meta = new JLabel(" ");
    private final JLabel queueStatus = new JLabel(" ");
    private int hoverRow = -1;
    private final Map<String, ImageIcon> plCovers = new HashMap<>();
    private final Icon pinDot = new Icon() {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.ACCENT);
            g2.fillOval(x + 2, y + 2, 8, 8);
            g2.dispose();
        }
        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }
    };
    private final Timer vizTimer;
    private final JPanel viz = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            long now = System.currentTimeMillis();
            boolean on = player != null && player.isPlaying();
            int n = 5, bw = 8, gap = 6;
            int totalW = n * bw + (n - 1) * gap;
            int x0 = (getWidth() - totalW) / 2;
            for (int i = 0; i < n; i++) {
                double v = on ? 0.30 + 0.70 * Math.abs(Math.sin(now / 280.0 + i * 1.3) * Math.sin(now / 160.0 + i * 0.7))
                              : 0.18;
                int h = (int) (4 + v * (getHeight() - 8));
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(x0 + i * (bw + gap), getHeight() - h, bw, h, 4, 4);
            }
            g2.dispose();
        }
    };
    private final Player player;
    private final Equalizer eq = new Equalizer();
    private final Preferences prefs = Preferences.userNodeForPackage(UI.class);

    private Track current;
    private int repeat; // 0 off, 1 all, 2 one
    private boolean shuffle;
    private boolean seeking;
    private final Random rnd = new Random();

    private static final String MAIN_PL = "Все треки";
    private String currentPlaylist = MAIN_PL;
    private boolean loadingPl;

    // ----- sorting -----
    private static final int SORT_TITLE = 0, SORT_ARTIST = 1, SORT_COUNT = 2, SORT_DURATION = 3;
    private int sortKey = -1;
    private boolean sortAsc = true;
    private final JButton[] sortBtns = new JButton[4];

    private final Theme.CoverLabel cover = new Theme.CoverLabel();
    private final JLabel title = new JLabel("—", SwingConstants.CENTER);
    private final JLabel artist = new JLabel("Выбери трек", SwingConstants.CENTER);
    private final JLabel curTime = new JLabel("0:00");
    private final JLabel totTime = new JLabel("0:00");
    private final JSlider seek;
    private final JSlider volume;
    private final JButton playBtn;
    private HoverIconButton repBtn;
    private final JToggleButton shufBtn;
    private final JToggleButton eqToggle;
    private final JSlider[] bandSliders = new JSlider[10];
    private static final String[] EQ_BUILTIN_NAMES =
            {"По умолчанию", "Поп", "Рок", "Джаз", "Бас", "Вокал", "Электроника", "Классика"};
    private static final String EQ_CUSTOM_SUFFIX = " (свой)";
    private final DefaultComboBoxModel<String> presetModel = new DefaultComboBoxModel<>();
    private JComboBox<String> presets;
    private boolean presetsUpdating;
    private final JTextArea lyrics = new JTextArea();
    private final JLabel lyricsHeader = new JLabel("Текст песни");
    private final Timer timer;
    private final JButton muteBtn;
    private final JComponent vu;
    private final DefaultListModel<String> plModel = new DefaultListModel<>();
    private final JList<String> plList = new JList<>(plModel);

    public UI() {
        frame = new JFrame("Vibe");
        try {
            java.net.URL icUrl = UI.class.getResource("/app/icon.png");
            if (icUrl != null) frame.setIconImage(new ImageIcon(icUrl).getImage());
        } catch (Exception ignored) {}
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setMinimumSize(new Dimension(980, 620));
        frame.setLocationRelativeTo(null);

        player = new Player(eq);
        player.setListener(new Player.Listener() {
            @Override public void onTrackEnded() { SwingUtilities.invokeLater(UI.this::handleEnded); }
            @Override public void onError(String msg) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, msg, "Ошибка", JOptionPane.ERROR_MESSAGE));
            }
        });

        seek = Theme.hSlider(0, 1000, 0);
        volume = Theme.hSlider(0, 100, (int) (prefs.getDouble("volume", 0.8) * 100));
        vu = createVu(90, 6);

        muteBtn = new JButton(Theme.Icons.vol(18, Theme.TEXT, false));
        muteBtn.setOpaque(false);
        muteBtn.setContentAreaFilled(false);
        muteBtn.setBorderPainted(false);
        muteBtn.setFocusPainted(false);
        muteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        muteBtn.addActionListener(e -> volume.setValue(volume.getValue() == 0 ? 80 : 0));
        volume.addChangeListener(e -> muteBtn.setIcon(Theme.Icons.vol(18, Theme.TEXT, volume.getValue() == 0)));

        playBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT);
                g2.fillOval(1, 1, getWidth() - 2, getHeight() - 2);
                boolean showPlay = player.current() == null || player.isPaused();
                Icon ic = showPlay ? Theme.Icons.play(22, Color.BLACK) : Theme.Icons.pause(22, Color.BLACK);
                ic.paintIcon(this, g2, (getWidth() - 22) / 2, (getHeight() - 22) / 2);
                g2.dispose();
            }
        };
        playBtn.setPreferredSize(new Dimension(52, 52));
        playBtn.setMaximumSize(new Dimension(52, 52));
        playBtn.setOpaque(false);
        playBtn.setContentAreaFilled(false);
        playBtn.setBorderPainted(false);
        playBtn.setFocusPainted(false);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.addActionListener(e -> togglePlay());

        HoverIconButton eqBtn = new HoverIconButton(Theme.Icons.equalizer(18, Theme.TEXT), "Эквалайзер", () -> {
            if (eqDialog == null) {
                eqDialog = new JDialog(frame, "Эквалайзер", false);
                eqDialog.setContentPane(eqPanel());
                eqDialog.pack();
                eqDialog.setLocationRelativeTo(frame);
            }
            eqDialog.setVisible(!eqDialog.isVisible());
        });
        HoverIconButton lyricsBtn = new HoverIconButton(Theme.Icons.lyrics(18, Theme.TEXT), "Текст песни",
                () -> { if (sidebar != null) sidebar.setSelectedIndex(2); });
        HoverIconButton prevBtn = new HoverIconButton(Theme.Icons.prev(19, Theme.TEXT), "Предыдущий", () -> step(-1));
        HoverIconButton nextBtn = new HoverIconButton(Theme.Icons.next(19, Theme.TEXT), "Следующий", () -> step(1));

        shufBtn = new JToggleButton() {
            private boolean hover;
            { setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
              setOpaque(false);
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              setPreferredSize(new Dimension(34, 34));
              setMaximumSize(new Dimension(34, 34));
              setMinimumSize(new Dimension(34, 34));
              addMouseListener(new java.awt.event.MouseAdapter() {
                  @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                  @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
              });
              addItemListener(e -> repaint()); }
            @Override protected void paintComponent(Graphics g) {
                if (hover || isSelected()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.PANEL2);
                    g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                    g2.dispose();
                }
                Icon ic = Theme.Icons.shuffle(17, isSelected() ? Theme.ACCENT_HI
                        : hover ? Color.WHITE : Theme.TEXT_DIM);
                ic.paintIcon(this, g, (getWidth() - 17) / 2, (getHeight() - 17) / 2);
            }
        };
        shufBtn.setToolTipText("Перемешать");
        shufBtn.addActionListener(e -> shuffle = shufBtn.isSelected());

        repBtn = new HoverIconButton(Theme.Icons.repeat(17, Theme.TEXT_DIM), "Повтор: выкл", () -> {
            repeat = (repeat + 1) % 3;
            repBtn.setToolTipText(repeat == 0 ? "Повтор: выкл" : repeat == 1 ? "Повтор: очередь" : "Повтор: песня");
            repBtn.setIconEx(Theme.Icons.repeat(17, repeat == 0 ? Theme.TEXT_DIM : Theme.ACCENT_HI));
            repBtn.setBadge(repeat == 2 ? "1" : null);
        });

        eqToggle = new JToggleButton("Эквалайзер вкл");
        styleToggle(eqToggle);
        eqToggle.addActionListener(e -> {
            eq.setEnabled(eqToggle.isSelected());
            saveEq();
        });
        eqToggle.setSelected(prefs.getBoolean("eqOn", false));
        eq.setEnabled(eqToggle.isSelected());

        buildLayout(eqBtn, lyricsBtn, prevBtn, nextBtn);
        initDragDrop();
        list.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i != hoverRow) { hoverRow = i; list.repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; list.repaint(); }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "removeSelectedTracks");
        list.getActionMap().put("removeSelectedTracks", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { removeSelected(); }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; list.repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i < 0) return;
                Rectangle r = list.getCellBounds(i, i);
                if (r == null || e.getY() < r.y || e.getY() > r.y + r.height) return;
                if (e.getX() > list.getWidth() - 40 && e.getX() < list.getWidth() - 8) {
                    removeTrackAt(i);
                    e.consume();
                }
            }
        });
        loadPlaylists();

        volume.addChangeListener(e -> {
            player.setVolume(volume.getValue() / 100f);
            prefs.putDouble("volume", volume.getValue() / 100.0);
        });
        player.setVolume(volume.getValue() / 100f);

        seek.addChangeListener(e -> {
            if (seeking) curTime.setText(Theme.fmtTime(seek.getValue() / 1000.0 * duration()));
        });
        seek.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { seeking = true; }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                if (duration() > 0) player.seek(seek.getValue() / 1000.0 * duration());
                seeking = false;
            }
        });

        timer = new Timer(90, e -> { tick(); vu.repaint(); });
        vizTimer = new Timer(90, e -> viz.repaint());
        vizTimer.start();
        timer.start();
    }

    private JComponent createVu(int w, int h) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PANEL2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                double lv = Math.min(1, player.getLevel());
                if (lv > 0.01) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillRoundRect(0, 0, Math.max(getHeight(), (int) (getWidth() * lv)),
                            getHeight(), getHeight(), getHeight());
                }
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(w, h));
        c.setOpaque(false);
        return c;
    }

    private double duration() {
        AudioData d = player.current();
        return d == null ? 0 : d.durationSec();
    }

    private void styleToggle(JToggleButton b) {
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setForeground(Theme.TEXT_DIM);
        b.addItemListener(e -> b.setForeground(b.isSelected() ? Theme.ACCENT_HI : Theme.TEXT_DIM));
    }

    /** Circular hover icon button. */
    private static class HoverIconButton extends JButton {
        private Icon icon;
        private boolean hover;
        private String badge;
        HoverIconButton(Icon icon, String tip, Runnable action) {
            this.icon = icon;
            setToolTipText(tip);
            setPreferredSize(new Dimension(34, 34));
            setMaximumSize(new Dimension(34, 34));
            setMinimumSize(new Dimension(34, 34));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> action.run());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
            });
        }
        void setIconEx(Icon ic) { this.icon = ic; repaint(); }
        void setBadge(String b) { this.badge = b; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PANEL2);
                g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                g2.dispose();
            }
            icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2,
                    (getHeight() - icon.getIconHeight()) / 2);
            if (badge != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_HI);
                g2.fillOval(getWidth() - 13, getHeight() - 13, 13, 13);
                g2.setColor(Theme.BG);
                g2.setFont(Theme.FONT_BOLD.deriveFont(9f));
                g2.drawString(badge, getWidth() - 9, getHeight() - 4);
                g2.dispose();
            }
        }
    }

    // ============================== LAYOUT ==============================

    private void buildLayout(HoverIconButton eqBtn, HoverIconButton lyricsBtn, HoverIconButton prevBtn, HoverIconButton nextBtn) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(2, 8, 10, 4));

        // ----- queue + playlist selector -----
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setFixedCellHeight(58);
        list.setCellRenderer(new TrackRenderer());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0)
                    playTrack(list.getSelectedIndex());
            }
        });
        JScrollPane playlistScroll = new JScrollPane(list);
        playlistScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        playlistScroll.getVerticalScrollBar().setUnitIncrement(14);
        styleScroll(playlistScroll);

        searchField.setBackground(Theme.PANEL2);
        searchField.setForeground(Theme.TEXT);
        searchField.setCaretColor(Theme.ACCENT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 10, 4, 10)));
        searchField.setToolTipText("Поиск по названию или исполнителю");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuildView(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuildView(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildView(); }
        });
        queueStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        queueStatus.setForeground(Theme.TEXT_DIM);
        queueStatus.setBorder(new EmptyBorder(2, 10, 0, 10));
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);
        topRow.add(searchField, BorderLayout.CENTER);
        JPanel addIcons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        addIcons.setOpaque(false);
        addIcons.add(new HoverIconButton(Theme.Icons.plus(16, Theme.TEXT), "Добавить файлы", this::addFiles));
        addIcons.add(new HoverIconButton(Theme.Icons.folder(16, Theme.TEXT), "Добавить папку", this::addFolder));
        topRow.add(addIcons, BorderLayout.EAST);
        JPanel queuePanel = new JPanel(new BorderLayout(0, 6));
        queuePanel.setOpaque(false);
        queuePanel.add(topRow, BorderLayout.NORTH);
        JPanel listWrap = new JPanel(new BorderLayout(0, 4));
        listWrap.setOpaque(false);
        listWrap.add(createSortBar(), BorderLayout.NORTH);
        listWrap.add(playlistScroll, BorderLayout.CENTER);
        queuePanel.add(listWrap, BorderLayout.CENTER);
        queuePanel.add(queueStatus, BorderLayout.SOUTH);

        sidebar = new JTabbedPane();
        sidebar.setUI(Theme.minimalTabUI());
        UIManager.put("TabbedPane.foreground", Theme.TEXT_DIM);
        UIManager.put("TabbedPane.selectedForeground", Theme.TEXT);
        sidebar.addTab("Треки", queuePanel);
        sidebar.addTab("Плейлисты", playlistsPanel());
        sidebar.addTab("Текст песни", lyricsPanel());
        sidebar.setPreferredSize(new Dimension(400, 0));
        sidebar.setFocusable(false);
        sidebar.addChangeListener(e -> {
            if (sidebar.getSelectedIndex() == 2 && current != null && !lyricsBusy) fetchLyrics();
        });

        // ----- center -----
        cover.setPreferredSize(new Dimension(320, 320));
        cover.setMaximumSize(new Dimension(320, 320));
        cover.setMinimumSize(new Dimension(320, 320));
        cover.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        artist.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        artist.setForeground(Theme.TEXT_DIM);
        artist.setAlignmentX(Component.CENTER_ALIGNMENT);
        meta.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        meta.setForeground(Theme.TEXT_DIM);
        meta.setAlignmentX(Component.CENTER_ALIGNMENT);
        viz.setOpaque(false);
        viz.setAlignmentX(Component.CENTER_ALIGNMENT);
        viz.setPreferredSize(new Dimension(160, 30));
        viz.setMaximumSize(new Dimension(160, 30));

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(Box.createVerticalGlue());
        info.add(cover);
        info.add(Box.createVerticalStrut(18));
        info.add(title);
        info.add(Box.createVerticalStrut(6));
        info.add(artist);
        info.add(Box.createVerticalStrut(4));
        info.add(meta);
        info.add(Box.createVerticalStrut(10));
        info.add(viz);
        info.add(Box.createVerticalStrut(14));
        JPanel seekRow = new JPanel(new GridBagLayout());
        seekRow.setOpaque(false);
        seekRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        seekRow.setMaximumSize(new Dimension(560, 30));
        GridBagConstraints sc = new GridBagConstraints();
        sc.insets = new Insets(0, 2, 0, 2);
        sc.gridy = 0;
        seekRow.add(curTime, sc);
        sc.fill = GridBagConstraints.HORIZONTAL; sc.weightx = 1.0;
        seekRow.add(seek, sc);
        sc.fill = GridBagConstraints.NONE; sc.weightx = 0;
        seekRow.add(totTime, sc);
        info.add(seekRow);
        info.add(Box.createVerticalGlue());

        // ----- transport -----
        JPanel transport = new JPanel(new GridBagLayout());
        transport.setOpaque(false);
        transport.setBorder(new EmptyBorder(8, 6, 2, 6));
        curTime.setForeground(Theme.TEXT_DIM);
        totTime.setForeground(Theme.TEXT_DIM);
        seek.setPreferredSize(new Dimension(120, 20));
        seek.setMinimumSize(new Dimension(60, 20));
        volume.setPreferredSize(new Dimension(110, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.insets = new Insets(0, 2, 0, 2);
        c.weightx = 1.3;
        transport.add(Box.createHorizontalGlue(), c);
        c.weightx = 0; c.fill = GridBagConstraints.NONE;
        transport.add(eqBtn, c);
        c.insets = new Insets(0, 2, 0, 2);
        transport.add(shufBtn, c);
        c.insets = new Insets(0, 6, 0, 2);
        transport.add(prevBtn, c);
        c.insets = new Insets(0, 6, 0, 6);
        transport.add(playBtn, c);
        c.insets = new Insets(0, 2, 0, 6);
        transport.add(nextBtn, c);
        c.insets = new Insets(0, 2, 0, 2);
        transport.add(repBtn, c);
        c.insets = new Insets(0, 2, 0, 2);
        transport.add(lyricsBtn, c);
        c.insets = new Insets(0, 2, 0, 2);
        c.weightx = 0.5;
        transport.add(Box.createHorizontalGlue(), c);
        c.weightx = 0;
        c.insets = new Insets(0, 12, 0, 4);
        transport.add(muteBtn, c);
        c.insets = new Insets(0, 0, 0, 2);
        c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0.8;
        transport.add(volume, c);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(info, BorderLayout.CENTER);
        right.add(transport, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, right);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerLocation(400);

        root.add(header, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        frame.setContentPane(root);
    }

    // ============================== PLAYLISTS ==============================

    private JPanel playlistsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 12, 10, 12));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        btns.add(Theme.flatButton("+ Создать", () -> {
            String name = JOptionPane.showInputDialog(frame, "Название плейлиста:", "Новый плейлист");
            if (name != null && !name.trim().isEmpty()) createPlaylist(name.trim());
        }));
        btns.add(Theme.flatButton("Обложка", () -> selectPlCover()));
        btns.add(Theme.flatButton("Закрепить", () -> togglePin()));
        btns.add(Theme.flatButton("Переименовать", () -> renamePlaylist()));
        btns.add(Theme.flatButton("Удалить", this::deletePlaylist));
        p.add(btns, BorderLayout.NORTH);
        plList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        plList.setFixedCellHeight(36);
        plList.setFixedCellHeight(46);
        plList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean foc) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                lb.setBorder(new EmptyBorder(3, 10, 3, 10));
                lb.setFont(Theme.FONT);
                lb.setOpaque(true);
                String nm = String.valueOf(v);
                ImageIcon cov = plCovers.get(nm);
                if (cov != null) { lb.setIcon(cov); lb.setIconTextGap(10); }
                else if (isPinned(nm)) { lb.setIcon(pinDot); lb.setIconTextGap(10); }
                else lb.setIcon(null);
                if (sel) { lb.setBackground(Theme.SEL_BG); lb.setForeground(Theme.ACCENT_HI); }
                else { lb.setBackground(i % 2 == 0 ? Theme.PANEL : Theme.PANEL2); lb.setForeground(Theme.TEXT); }
                return lb;
            }
        });
        plList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int i = plList.getSelectedIndex();
                if (i >= 0 && !loadingPl) switchPlaylist(plModel.get(i));
            }
        });
        JScrollPane sp = new JScrollPane(plList);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        styleScroll(sp);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private void loadPlaylists() {
        // миграция старого имени "Основной"
        if (prefs.get("pl.data.Основной", null) != null) {
            prefs.put("pl.data." + MAIN_PL, prefs.get("pl.data.Основной", ""));
            prefs.remove("pl.data.Основной");
        }
        if (prefs.get("pl.cover.Основной", null) != null) {
            prefs.put("pl.cover." + MAIN_PL, prefs.get("pl.cover.Основной", ""));
            prefs.remove("pl.cover.Основной");
        }
        if (prefs.getBoolean("pl.pin.Основной", false)) {
            prefs.putBoolean("pl.pin." + MAIN_PL, true);
            prefs.remove("pl.pin.Основной");
        }
        if (prefs.get("pl.ord.Основной", null) != null) {
            prefs.put("pl.ord." + MAIN_PL, prefs.get("pl.ord.Основной", ""));
            prefs.remove("pl.ord.Основной");
        }
        String oldNames = prefs.get("pl.names", "");
        if (oldNames.contains("Основной"))
            prefs.put("pl.names", oldNames.replace("Основной", MAIN_PL));
        if (prefs.get("pl.current", "").equals("Основной"))
            prefs.put("pl.current", MAIN_PL);
        if (prefs.get("pl.names", "").isEmpty()) prefs.put("pl.names", MAIN_PL);
        currentPlaylist = prefs.get("pl.current", MAIN_PL);
        loadingPl = true;
        plModel.clear();
        for (String n : prefs.get("pl.names", MAIN_PL).split(";"))
            if (!n.isEmpty()) plModel.addElement(n);
        sortPl();
        refreshPlCovers();
        plList.setSelectedValue(currentPlaylist, true);
        loadingPl = false;
        loadPlaylist();
    }

    private void createPlaylist(String name) {
        String names = prefs.get("pl.names", MAIN_PL);
        if (names.contains(name)) return;
        prefs.put("pl.names", names + ";" + name);
        prefs.put("pl.data." + name, "");
        prefs.put("pl.ord." + name, "");
        savePlaylist();
        loadingPl = true;
        plModel.addElement(name);
        sortPl();
        refreshPlCovers();
        plList.setSelectedValue(name, true);
        loadingPl = false;
        switchPlaylist(name);
    }

    private void deletePlaylist() {
        if (MAIN_PL.equals(currentPlaylist)) {
            JOptionPane.showMessageDialog(frame, "Плейлист «" + MAIN_PL + "» удалить нельзя.");
            return;
        }
        if (JOptionPane.showConfirmDialog(frame, "Удалить плейлист \u00ab" + currentPlaylist + "\u00bb?",
                "Удаление", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String names = prefs.get("pl.names", "");
        StringBuilder sb = new StringBuilder();
        for (String n : names.split(";"))
            if (!n.equals(currentPlaylist) && !n.isEmpty())
                sb.append(sb.length() > 0 ? ";" : "").append(n);
        prefs.put("pl.names", sb.toString());
        prefs.remove("pl.data." + currentPlaylist);
        prefs.remove("pl.ord." + currentPlaylist);
        prefs.remove("pl.cover." + currentPlaylist);
        prefs.remove("pl.pin." + currentPlaylist);
        plCovers.remove(currentPlaylist);
        loadingPl = true;
        plModel.removeElement(currentPlaylist);
        plList.setSelectedValue(MAIN_PL, true);
        loadingPl = false;
        switchPlaylist(MAIN_PL);
    }

    private void switchPlaylist(String name) {
        savePlaylist();
        currentPlaylist = name;
        prefs.put("pl.current", name);
        loadPlaylist();
    }

    private String plKey() { return "pl.data." + currentPlaylist; }

    /** Preferences keys are limited to 80 chars on Windows -> store a short hash of the path. */
    private static String trackKey(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(f.getAbsolutePath().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder("pc.");
            for (int i = 0; i < 8 && i < d.length; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "pc." + Integer.toHexString(f.getAbsolutePath().hashCode());
        }
    }

    private static int parseOrd(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return fallback; }
    }

    // ============================== EQUALIZER ==============================

    private JPanel eqPanel;

    private JPanel eqPanel() {
        eqPanel = new JPanel(new BorderLayout(0, 6));
        eqPanel.setOpaque(false);
        eqPanel.setBorder(new EmptyBorder(10, 14, 10, 14));
        JPanel bands = new JPanel(new GridLayout(1, 10, 4, 0));
        bands.setOpaque(false);
        double[] saved = getSavedEq();
        for (int i = 0; i < 10; i++) {
            JPanel col = new JPanel(new BorderLayout());
            col.setOpaque(false);
            JSlider s = Theme.vSlider((int) Equalizer.MIN_DB, (int) Equalizer.MAX_DB, 0);
            final int band = i;
            s.setValue((int) Math.round(saved[i]));
            s.addChangeListener(e -> {
                if (!eq.isEnabled()) {
                    eqToggle.setSelected(true);
                    eq.setEnabled(true);
                }
                eq.setGain(band, s.getValue());
                saveEq();
            });
            eq.setGain(i, saved[i]);
            bandSliders[i] = s;
            JLabel db = new JLabel(String.valueOf(s.getValue()), SwingConstants.CENTER);
            db.setFont(Theme.FONT.deriveFont(11f));
            db.setForeground(Theme.TEXT_DIM);
            s.addChangeListener(e -> db.setText(String.valueOf(s.getValue())));
            JLabel hz = new JLabel(Equalizer.BAND_NAMES[i], SwingConstants.CENTER);
            hz.setFont(Theme.FONT.deriveFont(11f));
            hz.setForeground(Theme.TEXT_DIM);
            col.add(db, BorderLayout.NORTH);
            col.add(s, BorderLayout.CENTER);
            col.add(hz, BorderLayout.SOUTH);
            bands.add(col);
        }
        eqPanel.add(bands, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.setOpaque(false);
        bottom.add(eqToggle);
        presets = new JComboBox<>(presetModel);
        rebuildPresetCombo();
        presets.addActionListener(e -> {
            Object sel = presets.getSelectedItem();
            if (!presetsUpdating && sel != null) applyPresetName(sel.toString());
        });
        bottom.add(presets);
        bottom.add(Theme.flatButton("Сохранить пресет", this::saveEqPreset));
        bottom.add(Theme.flatButton("Удалить пресет", this::deleteEqPreset));
        bottom.add(Theme.flatButton("Сбросить", () -> {
            for (JSlider s : bandSliders) s.setValue(0);
        }));
        JPanel vuRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        vuRow.setOpaque(false);
        JLabel vuLabel = new JLabel("Уровень:");
        vuLabel.setForeground(Theme.TEXT_DIM);
        vuLabel.setFont(Theme.FONT.deriveFont(11f));
        vuRow.add(vuLabel);
        vuRow.add(vu);
        JPanel southBox = new JPanel();
        southBox.setOpaque(false);
        southBox.setLayout(new BoxLayout(southBox, BoxLayout.Y_AXIS));
        southBox.add(bottom);
        southBox.add(vuRow);
        eqPanel.add(southBox, BorderLayout.SOUTH);
        return eqPanel;
    }

    private double[] getSavedEq() {
        double[] g = new double[10];
        String s = prefs.get("eqGains", "");
        if (!s.isEmpty()) {
            String[] p = s.split(",");
            for (int i = 0; i < 10 && i < p.length; i++)
                try { g[i] = Double.parseDouble(p[i]); } catch (NumberFormatException ignored) {}
        }
        return g;
    }

    private void saveEq() {
        StringBuilder sb = new StringBuilder();
        double[] g = eq.getGains();
        for (int i = 0; i < g.length; i++) sb.append(i > 0 ? "," : "").append((int) Math.round(g[i]));
        prefs.put("eqGains", sb.toString());
        prefs.putBoolean("eqOn", eq.isEnabled());
    }

    private void applyPresetName(String item) {
        String name = item.endsWith(EQ_CUSTOM_SUFFIX)
                ? item.substring(0, item.length() - EQ_CUSTOM_SUFFIX.length()) : item;
        double[] p = null;
        switch (name) {
            case "Поп":          p = new double[]{-2, 2, 4, 4, 2, 0, -1, -1, 2, 3}; break;
            case "Рок":          p = new double[]{3, 2, 1, -2, -3, -1, 2, 4, 5, 5}; break;
            case "Джаз":         p = new double[]{3, 2, 0, 2, 3, 3, 0, 1, 3, 4}; break;
            case "Бас":          p = new double[]{7, 6, 5, 3, 1, 0, 0, 0, 0, 0}; break;
            case "Вокал":        p = new double[]{-4, -3, -2, 1, 4, 5, 4, 2, 0, -1}; break;
            case "Электроника":  p = new double[]{4, 3, 1, 0, -2, 1, 3, 4, 5, 5}; break;
            case "Классика":     p = new double[]{4, 3, 2, 2, 1, 1, 2, 2, 3, 3}; break;
            case "По умолчанию": p = new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}; break;
            default:             p = readCustomPresets().get(name); break;
        }
        if (p == null) return;
        for (int i = 0; i < 10; i++) bandSliders[i].setValue((int) Math.round(p[i]));
    }

    // ----- custom EQ presets (stored in preferences) -----

    private java.util.Map<String, double[]> readCustomPresets() {
        java.util.Map<String, double[]> m = new java.util.LinkedHashMap<>();
        String s = prefs.get("eq.presets", "");
        if (s.isEmpty()) return m;
        for (String entry : s.split(";")) {
            int i = entry.indexOf('=');
            if (i <= 0) continue;
            String name = entry.substring(0, i);
            String[] vals = entry.substring(i + 1).split(",");
            double[] g = new double[10];
            for (int v = 0; v < 10 && v < vals.length; v++)
                try { g[v] = Double.parseDouble(vals[v]); }
                catch (NumberFormatException ignored) {}
            m.put(name, g);
        }
        return m;
    }

    private void writeCustomPresets(java.util.Map<String, double[]> m) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, double[]> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=');
            double[] g = e.getValue();
            for (int i = 0; i < g.length; i++)
                sb.append(i > 0 ? "," : "").append((int) Math.round(g[i]));
        }
        prefs.put("eq.presets", sb.toString());
    }

    private void rebuildPresetCombo() {
        presetModel.removeAllElements();
        for (String n : EQ_BUILTIN_NAMES) presetModel.addElement(n);
        for (String n : readCustomPresets().keySet())
            presetModel.addElement(n + EQ_CUSTOM_SUFFIX);
    }

    private void saveEqPreset() {
        String name = JOptionPane.showInputDialog(
                eqDialog != null ? eqDialog : frame,
                "Название пресета для текущих настроек:", "Мой пресет");
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) return;
        if (name.contains(";") || name.contains("=") || name.endsWith(EQ_CUSTOM_SUFFIX)) {
            JOptionPane.showMessageDialog(frame,
                    "Название не должно содержать ; и =", "Ошибка", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.Map<String, double[]> m = readCustomPresets();
        m.put(name, eq.getGains());
        writeCustomPresets(m);
        rebuildPresetCombo();
        presetsUpdating = true;
        presets.setSelectedItem(name + EQ_CUSTOM_SUFFIX);
        presetsUpdating = false;
    }

    private void deleteEqPreset() {
        Object sel = presets.getSelectedItem();
        if (sel == null) return;
        String s = sel.toString();
        if (!s.endsWith(EQ_CUSTOM_SUFFIX)) {
            JOptionPane.showMessageDialog(frame,
                    "Встроенные пресеты удалить нельзя — выбери свой.",
                    "Удаление пресета", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = s.substring(0, s.length() - EQ_CUSTOM_SUFFIX.length());
        if (JOptionPane.showConfirmDialog(frame, "Удалить пресет \u00ab" + name + "\u00bb?",
                "Удаление пресета", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        java.util.Map<String, double[]> m = readCustomPresets();
        m.remove(name);
        writeCustomPresets(m);
        rebuildPresetCombo();
        presetsUpdating = true;
        presets.setSelectedItem(EQ_BUILTIN_NAMES[0]);
        presetsUpdating = false;
    }

    // ============================== LYRICS ==============================

    private JPanel lyricsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(8, 14, 10, 14));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(Theme.flatButton("Найти текст на Genius", this::fetchLyrics));
        lyricsHeader.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lyricsHeader.setForeground(Theme.TEXT);
        lyricsHeader.setBorder(new EmptyBorder(4, 2, 4, 0));
        JPanel northBox = new JPanel();
        northBox.setOpaque(false);
        northBox.setLayout(new BoxLayout(northBox, BoxLayout.Y_AXIS));
        northBox.add(top);
        northBox.add(lyricsHeader);
        p.add(northBox, BorderLayout.NORTH);

        lyrics.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        lyrics.setLineWrap(true);
        lyrics.setWrapStyleWord(true);
        lyrics.setBackground(Theme.PANEL);
        lyrics.setForeground(Theme.TEXT);
        lyrics.setCaretColor(Theme.ACCENT);
        lyrics.setMargin(new Insets(14, 16, 14, 16));
        lyrics.setEditable(false);
        lyrics.setText("Нажми «Найти текст на Genius», когда играет трек.");
        JScrollPane sp = new JScrollPane(lyrics);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.PANEL);
        styleScroll(sp);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        String t = s.trim()
                .replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", " ")
                .replaceAll("(?i)\\s*(feat|ft)\\.?\\s+.*$", "")
                .replaceAll("\\s+", " ").trim();
        return t;
    }

    private static String cleanArtist(String s) {
        if (s == null) return "";
        String a = s.trim();
        if (a.isEmpty()) return "";
        String low = a.toLowerCase();
        if (low.equals("unknown") || low.equals("unknown artist")
                || low.equals("неизвестен") || low.equals("неизвестный исполнитель")) return "";
        return a;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase()
                .replaceAll("[^a-zа-яё0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static GeniusClient.Hit bestHit(List<GeniusClient.Hit> hits, String artist, String title) {
        if (hits == null || hits.isEmpty()) return null;
        if (hits.size() == 1) return hits.get(0);
        String[] qt = norm(title).split(" ");
        String[] qa = norm(artist).split(" ");
        GeniusClient.Hit best = hits.get(0);
        int bestScore = -1;
        for (GeniusClient.Hit h : hits) {
            String ht = norm(h.title), ha = norm(h.artist);
            int score = 0;
            for (String w : qt) if (!w.isEmpty() && ht.contains(w)) score += 2;
            for (String w : qa) if (!w.isEmpty() && ha.contains(w)) score += 3;
            if (score > bestScore) { bestScore = score; best = h; }
        }
        return best;
    }

    private void fetchLyrics() {
        if (current == null || lyricsBusy) return;
        lyricsBusy = true;
        String token = GeniusClient.TOKEN;
        String artist = cleanArtist(current.artist);
        String title = cleanTitle(current.title);
        if (artist.isEmpty())
            artist = cleanArtist(LyricsOvh.suggestArtist(title));
        String query = artist.isEmpty() ? title : artist + " " + title;
        final String fArtist = artist, fTitle = title, fQuery = query, fToken = token;
        lyrics.setText("Ищу: " + query + " …");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                String plain = null, src = null;
                if (!fArtist.isEmpty()) {
                    try { plain = LyricsOvh.fetch(fArtist, fTitle); src = "lyrics.ovh"; }
                    catch (Exception ignored) {}
                }
                if (plain == null) {
                    GeniusClient.Hit hit = bestHit(GeniusClient.search(fToken, fQuery), fArtist, fTitle);
                    if (hit == null) throw new Exception("Ничего не найдено на Genius");
                    plain = GeniusClient.fetchLyrics(hit.url);
                    src = "Genius";
                }
                String head = fArtist.isEmpty() ? fTitle : fArtist + " — " + fTitle;
                return head + "\n" + plain.trim() + "\n\n(источник: " + src + ")";
            }
            @Override protected void done() {
                lyricsBusy = false;
                try {
                    String all = get();
                    int nl = all.indexOf('\n');
                    if (nl > 0) {
                        lyricsHeader.setText(all.substring(0, nl));
                        lyrics.setText(all.substring(nl + 1).trim());
                    } else {
                        lyrics.setText(all);
                    }
                    lyrics.setCaretPosition(0);
                } catch (Exception ex) {
                    Throwable cc = ex.getCause() == null ? ex : ex.getCause();
                    lyrics.setText("Не удалось загрузить текст: " + cc.getMessage());
                }
            }
        }.execute();
    }

    // ============================== PLAYLIST / QUEUE ==============================

    private void addFiles() {
        FileDialog fd = new FileDialog(frame, "Выбрать аудиофайлы", FileDialog.LOAD);
        fd.setMultipleMode(true);
        fd.setDirectory(prefs.get("lastDir", ""));
        fd.setFilenameFilter((dir, name) -> AudioLoader.isSupported(new File(dir, name)));
        fd.setVisible(true);
        File[] files = fd.getFiles();
        if (files.length == 0) return;
        prefs.put("lastDir", fd.getDirectory());
        addTracks(Arrays.asList(files));
    }

    private void addFolder() {
        // temporary Windows LAF -> native-looking folder picker
        javax.swing.LookAndFeel prev = UIManager.getLookAndFeel();
        File dir = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JFileChooser fc = new JFileChooser(prefs.get("lastDir", ""));
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Выбрать папку");
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                dir = fc.getSelectedFile();
                prefs.put("lastDir", fc.getCurrentDirectory().getAbsolutePath());
            }
        } catch (Exception ignored) {
        } finally {
            try { UIManager.setLookAndFeel(prev); } catch (Exception ignored) {}
        }
        if (dir == null) return;
        List<File> files = new ArrayList<>();
        collect(dir, files, 0);
        addTracks(files);
    }

    private static void collect(File dir, List<File> out, int depth) {
        if (depth > 4) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out, depth + 1);
            else if (AudioLoader.isSupported(f)) out.add(f);
        }
    }

    private void addTracks(List<File> files) {
        // validation: only supported audio formats
        List<File> ok = new ArrayList<>();
        List<File> rejected = new ArrayList<>();
        for (File f : files) {
            if (AudioLoader.isSupported(f)) ok.add(f);
            else rejected.add(f);
        }
        if (!rejected.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                    "Эти файлы не добавлены. Поддерживаются только: mp3, wav, aiff:\n");
            for (int i = 0; i < rejected.size() && i < 8; i++)
                sb.append("• ").append(rejected.get(i).getName()).append('\n');
            if (rejected.size() > 8) sb.append("… и ещё ").append(rejected.size() - 8);
            JOptionPane.showMessageDialog(frame, sb.toString(),
                    "Неподдерживаемый формат", JOptionPane.WARNING_MESSAGE);
        }
        files = ok;
        List<Track> added = new ArrayList<>();
        for (File f : files) {
            boolean dup = false;
            for (int i = 0; i < model.size(); i++)
                if (model.get(i).file.equals(f)) { dup = true; break; }
            if (!dup) {
                Track t = new Track(f);
                int maxNum = 0;
                for (int i = 0; i < model.size(); i++)
                    maxNum = Math.max(maxNum, model.get(i).number);
                t.number = maxNum + 1;
                model.addElement(t);
                added.add(t);
            }
        }
        savePlaylist();
        if (!added.isEmpty()) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (Track t : added) t.loadMeta();
                    return null;
                }
                @Override protected void done() { rebuildView(); list.repaint(); }
            }.execute();
        }
        rebuildView();
    }

    private void removeTrackAt(int viewIndex) {
        if (viewIndex < 0 || viewIndex >= viewModel.size()) return;
        Track t = viewModel.get(viewIndex);
        model.removeElement(t);
        rebuildView();
        if (t == current) { player.stop(); current = null; resetNow(); }
        savePlaylist();
    }

    private boolean isPinned(String name) { return prefs.getBoolean("pl.pin." + name, false); }

    private void sortPl() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < plModel.size(); i++) items.add(plModel.get(i));
        Collections.sort(items, (a, b) -> {
            boolean pa = isPinned(a), pb = isPinned(b);
            if (pa != pb) return pa ? -1 : 1;
            return a.compareToIgnoreCase(b);
        });
        plModel.clear();
        StringBuilder sb = new StringBuilder();
        for (String s : items) { plModel.addElement(s); sb.append(sb.length() > 0 ? ";" : "").append(s); }
        prefs.put("pl.names", sb.toString());
        try { prefs.flush(); } catch (Exception ignored) {}
    }

    private void refreshPlCovers() {
        plCovers.clear();
        for (int i = 0; i < plModel.size(); i++) {
            String name = plModel.get(i);
            String path = prefs.get("pl.cover." + name, "");
            if (path.isEmpty()) continue;
            try {
                BufferedImage bi = ImageIO.read(new File(path));
                if (bi != null) {
                    // растягиваем до квадрата, заполняет всю ячейку (без полей по краям)
                    BufferedImage sq = new BufferedImage(68, 68, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = sq.createGraphics();
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, 68, 68);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(bi, 0, 0, 68, 68, null);
                    g2.dispose();
                    plCovers.put(name, new ImageIcon(sq));
                }
            } catch (IOException ignored) {}
        }
        plList.repaint();
    }

    private void selectPlCover() {
        String name = currentPlaylist;
        if (name == null || name.isEmpty()) return;
        Object[] opts = {"Файл с диска", "Поиск в интернете", "Отмена"};
        int ch = JOptionPane.showOptionDialog(frame,
                "Откуда взять обложку для плейлиста \u00ab" + name + "\u00bb?",
                "Обложка плейлиста", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (ch == JOptionPane.YES_OPTION) pickCoverFromFile(name);
        else if (ch == JOptionPane.NO_OPTION) searchCoverOnline(name);
    }

    private void pickCoverFromFile(String name) {
        FileDialog fd = new FileDialog(frame, "Выбрать обложку плейлиста", FileDialog.LOAD);
        fd.setFilenameFilter((dir, fn) -> {
            String n = fn.toLowerCase();
            return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                    || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp");
        });
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        prefs.put("pl.cover." + name, new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
        try { prefs.flush(); } catch (Exception ignored) {}
        refreshPlCovers();
    }

    /** Кэш скачанных обложек: ~/.vibeplayer/covers/ */
    private static File coverCacheFile(String key) {
        File dir = new File(new File(System.getProperty("user.home"), ".vibeplayer"), "covers");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
    }

    private static byte[] httpGet(String u, int timeout) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "VibePlayer/1.0");
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) buf.write(b, 0, n);
        in.close();
        return buf.toByteArray();
    }

    private static String jsonUnescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') sb.append('\n');
                else if (n == 'u' && i + 4 < s.length()) {
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                } else sb.append(n);
            } else sb.append(ch);
        }
        return sb.toString();
    }

    /** Поиск обложки альбома через публичный API iTunes (без ключа). */
    private void searchCoverOnline(String plName) {
        final String playlist = plName;
        JDialog dlg = new JDialog(frame, "Обложка из интернета", true);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));
        root.setBackground(Theme.PANEL);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        final JTextField q = new JTextField(playlist);
        q.setBackground(Theme.PANEL2);
        q.setForeground(Theme.TEXT);
        q.setCaretColor(Theme.ACCENT);
        JButton find = Theme.flatButton("Найти", () -> {});
        top.add(q, BorderLayout.CENTER);
        top.add(find, BorderLayout.EAST);

        final JLabel status = new JLabel("Введи название альбома или исполнителя и нажми «Найти».");
        status.setForeground(Theme.TEXT);
        status.setFont(Theme.FONT);

        final JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
        grid.setOpaque(false);
        final JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(4 * 108 + 24, 340));

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(frame);

        final java.util.List<SwingWorker<?, ?>> workers = new ArrayList<>();
        final Runnable search = () -> {
            for (SwingWorker<?, ?> w : workers) w.cancel(true);
            workers.clear();
            grid.removeAll();
            grid.revalidate();
            grid.repaint();
            final String query = q.getText().trim();
            if (query.isEmpty()) return;
            status.setText("Ищу: " + query + " …");
            SwingWorker<java.util.List<String[]>, Void> sw = new SwingWorker<java.util.List<String[]>, Void>() {
                @Override protected java.util.List<String[]> doInBackground() throws Exception {
                    String u = "https://itunes.apple.com/search?term="
                            + URLEncoder.encode(query, "UTF-8") + "&entity=album&limit=32";
                    String body = new String(httpGet(u, 8000), "UTF-8");
                    java.util.List<String[]> items = new ArrayList<>();
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                        "\"collectionName\":\"((?:[^\"\\\\]|\\\\.)*)\".*?\"artworkUrl100\":\"(https://[^\"]+)\"",
                        java.util.regex.Pattern.DOTALL);
                    java.util.regex.Matcher m = p.matcher(body);
                    while (m.find()) {
                        String art = m.group(2);
                        items.add(new String[] { jsonUnescape(m.group(1)), art,
                                art.replace("100x100bb", "600x600bb") });
                    }
                    return items;
                }
                @Override protected void done() {
                    if (isCancelled()) return;
                    try {
                        final java.util.List<String[]> items = get();
                        if (items.isEmpty()) { status.setText("Ничего не найдено."); return; }
                        status.setText("Найдено вариантов: " + items.size() + ". Кликни по обложке.");
                        for (final String[] it : items) {
                            final JButton b = new JButton();
                            b.setOpaque(false);
                            b.setContentAreaFilled(false);
                            b.setBorderPainted(false);
                            b.setFocusPainted(false);
                            b.setToolTipText(it[0]);
                            b.setPreferredSize(new Dimension(100, 100));
                            b.setEnabled(false);
                            b.addActionListener(ev -> applyOnlineCover(dlg, playlist, it[1], it[2]));
                            grid.add(b);
                            SwingWorker<ImageIcon, Void> dw = new SwingWorker<ImageIcon, Void>() {
                                @Override protected ImageIcon doInBackground() throws Exception {
                                    byte[] data = httpGet(it[1], 8000);
                                    return new ImageIcon(data);
                                }
                                @Override protected void done() {
                                    if (isCancelled()) return;
                                    try { b.setIcon(get()); b.setEnabled(true); }
                                    catch (Exception ignored) { grid.remove(b); }
                                    grid.revalidate();
                                    grid.repaint();
                                }
                            };
                            workers.add(dw);
                            dw.execute();
                        }
                        grid.revalidate();
                        grid.repaint();
                    } catch (Exception ex) {
                        status.setText("Ошибка поиска: " + ex.getMessage());
                    }
                }
            };
            workers.add(sw);
            sw.execute();
        };
        find.addActionListener(e -> search.run());
        q.addActionListener(e -> search.run());
        dlg.setVisible(true);
        for (SwingWorker<?, ?> w : workers) w.cancel(true);
    }

    private void applyOnlineCover(JDialog dlg, String playlist, String previewUrl, String bigUrl) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                File f = coverCacheFile(bigUrl);
                if (!f.isFile()) {
                    byte[] data = httpGet(bigUrl, 10000);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    out.write(data);
                    out.close();
                }
                return f.getAbsolutePath();
            }
            @Override protected void done() {
                try {
                    prefs.put("pl.cover." + playlist, get());
                    try { prefs.flush(); } catch (Exception ignored) {}
                    refreshPlCovers();
                    dlg.dispose();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "Не удалось скачать обложку: " + ex.getMessage(),
                            "Ошибка", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void togglePin() {
        String name = currentPlaylist;
        if (name == null || name.isEmpty()) return;
        if (isPinned(name)) prefs.remove("pl.pin." + name);
        else prefs.putBoolean("pl.pin." + name, true);
        try { prefs.flush(); } catch (Exception ignored) {}
        sortPl();
    }

    private void renamePlaylist() {
        String oldName = currentPlaylist;
        if (oldName == null || oldName.isEmpty()) return;
        String newName = (String) JOptionPane.showInputDialog(frame, "Новое имя плейлиста:",
                "Переименовать", JOptionPane.PLAIN_MESSAGE, null, null, oldName);
        if (newName == null) return;
        newName = newName.trim();
        if (newName.isEmpty() || newName.equals(oldName)) return;
        if ((";" + prefs.get("pl.names", "") + ";").contains(";" + newName + ";")) {
            JOptionPane.showMessageDialog(frame, "Плейлист с таким именем уже есть.");
            return;
        }
        prefs.put("pl.data." + newName, prefs.get("pl.data." + oldName, ""));
        prefs.remove("pl.data." + oldName);
        prefs.put("pl.ord." + newName, prefs.get("pl.ord." + oldName, ""));
        prefs.remove("pl.ord." + oldName);
        String cover = prefs.get("pl.cover." + oldName, "");
        if (!cover.isEmpty()) { prefs.put("pl.cover." + newName, cover); prefs.remove("pl.cover." + oldName); }
        if (isPinned(oldName)) { prefs.putBoolean("pl.pin." + newName, true); prefs.remove("pl.pin." + oldName); }
        try { prefs.flush(); } catch (Exception ignored) {}
        int ix = plModel.indexOf(oldName);
        if (ix >= 0) plModel.set(ix, newName);
        if (oldName.equals(currentPlaylist)) currentPlaylist = newName;
        sortPl();
        refreshPlCovers();
    }

    private void removeSelected() {
        int[] sel = list.getSelectedIndices();
        if (sel.length == 0) return;
        boolean currentRemoved = false;
        List<Track> toRemove = new ArrayList<>();
        for (int i : sel) {
            Track t = viewModel.get(i);
            if (t == current) currentRemoved = true;
            toRemove.add(t);
        }
        for (Track t : toRemove) model.removeElement(t);
        rebuildView();
        if (currentRemoved) { player.stop(); current = null; resetNow(); }
        savePlaylist();
    }

    // ============================== SORTING ==============================

    private JComponent createSortBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 4, 2, 4));
        String[] tips = {"Сортировка по названию", "Сортировка по исполнителю",
                "Сортировка по числу прослушиваний", "Сортировка по длительности"};
        for (int i = 0; i < 4; i++) {
            sortBtns[i] = new SortButton(i, tips[i]);
            bar.add(sortBtns[i]);
        }
        return bar;
    }

    /** Sort header with a drawn icon: first click sorts, second click reverses (like Spotify). */
    private final class SortButton extends JButton {
        private final int key;
        private boolean hover;
        SortButton(int key, String tip) {
            this.key = key;
            setToolTipText(tip);
            setPreferredSize(new Dimension(30, 28));
            setMaximumSize(new Dimension(30, 28));
            setMinimumSize(new Dimension(30, 28));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> applySort(key));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (hover) {
                g2.setColor(Theme.PANEL2);
                g2.fillOval(1, 1, w - 2, h - 2);
            }
            boolean active = sortKey == key;
            Color col = active ? Theme.ACCENT_HI : hover ? Color.WHITE : Theme.TEXT_DIM;
            int ix = (w - 16) / 2 - (active ? 2 : 0);
            int iy = (h - 16) / 2;
            switch (key) {
                case SORT_TITLE:    drawNoteIcon(g2, ix, iy, col); break;
                case SORT_ARTIST:   drawArtistIcon(g2, ix, iy, col); break;
                case SORT_COUNT:    drawCountIcon(g2, ix, iy, col); break;
                default:            drawClockIcon(g2, ix, iy, col); break;
            }
            if (active) {
                int ax = w - 11, ay = h - 10;
                g2.setColor(Theme.ACCENT_HI);
                java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
                if (sortAsc) { // стрелка вверх
                    tri.moveTo(ax, ay + 6); tri.lineTo(ax + 8, ay + 6); tri.lineTo(ax + 4, ay);
                } else {       // стрелка вниз
                    tri.moveTo(ax, ay); tri.lineTo(ax + 8, ay); tri.lineTo(ax + 4, ay + 6);
                }
                tri.closePath();
                g2.fill(tri);
            }
            g2.dispose();
        }
    }

    // --- sort icons, drawn manually so they never depend on fonts ---
    private static void sortStroke(Graphics2D g2) {
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }
    private static void drawNoteIcon(Graphics2D g2, int x, int y, Color c) {
        sortStroke(g2); g2.setColor(c);
        g2.fillOval(x + 1, y + 9, 7, 5);                 // нота: головка
        g2.drawLine(x + 7, y + 11, x + 7, y + 2);        // штиль
        java.awt.geom.Path2D.Double flag = new java.awt.geom.Path2D.Double();
        flag.moveTo(x + 7, y + 2);
        flag.curveTo(x + 11, y + 4, x + 13, y + 6, x + 13, y + 9); // флажок
        g2.draw(flag);
    }
    private static void drawArtistIcon(Graphics2D g2, int x, int y, Color c) {
        sortStroke(g2); g2.setColor(c);
        g2.drawOval(x + 5, y + 1, 6, 6);                 // голова
        java.awt.geom.Path2D.Double body = new java.awt.geom.Path2D.Double();
        body.moveTo(x + 1, y + 15);
        body.curveTo(x + 1, y + 9.5, x + 15, y + 9.5, x + 15, y + 15); // плечи
        g2.draw(body);
    }
    private static void drawCountIcon(Graphics2D g2, int x, int y, Color c) {
        sortStroke(g2); g2.setColor(c);
        g2.drawOval(x + 1, y + 1, 14, 14);               // значок прослушиваний
        java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
        tri.moveTo(x + 6, y + 5); tri.lineTo(x + 6, y + 11); tri.lineTo(x + 11, y + 8);
        tri.closePath();
        g2.fill(tri);
    }
    private static void drawClockIcon(Graphics2D g2, int x, int y, Color c) {
        sortStroke(g2); g2.setColor(c);
        g2.drawOval(x + 1, y + 1, 14, 14);               // циферблат
        g2.drawLine(x + 8, y + 8, x + 8, y + 4);         // часовая стрелка
        g2.drawLine(x + 8, y + 8, x + 11, y + 10);       // минутная
    }

    /** Spotify-like: first click sorts, second click reverses the order. */
    private void applySort(int key) {
        if (sortKey == key) {
            sortAsc = !sortAsc;
        } else {
            sortKey = key;
            // название/исполнитель: сначала А→Я; счёт/время: сначала максимум
            sortAsc = key == SORT_TITLE || key == SORT_ARTIST;
        }
        sortModel();
    }

    private void sortModel() {
        if (sortKey < 0) return;
        List<Track> all = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) all.add(model.get(i));
        Comparator<Track> cmp = sortComparator();
        if (!sortAsc) cmp = cmp.reversed();
        Collections.sort(all, cmp);
        model.clear();
        for (Track t : all) model.addElement(t);
        savePlaylist();
        rebuildView();
        updateSortHeaders();
    }

    private Comparator<Track> sortComparator() {
        Comparator<Track> byTitle = (a, b) -> cmpStr(a.title, b.title);
        switch (sortKey) {
            case SORT_ARTIST:
                return ((Comparator<Track>) (a, b) -> cmpStr(a.artist, b.artist)).thenComparing(byTitle);
            case SORT_COUNT:
                return ((Comparator<Track>) (a, b) -> Integer.compare(a.playCount, b.playCount)).thenComparing(byTitle);
            case SORT_DURATION:
                return ((Comparator<Track>) (a, b) -> Double.compare(a.durationSec, b.durationSec)).thenComparing(byTitle);
            default:
                return byTitle;
        }
    }

    private static int cmpStr(String a, String b) {
        return (a == null ? "" : a).compareToIgnoreCase(b == null ? "" : b);
    }

    private void updateSortHeaders() {
        for (JButton b : sortBtns) b.repaint();
    }

    private void resetSort() {
        sortKey = -1;
        updateSortHeaders();
    }

    private void rebuildView() {
        String q = searchField.getText().trim().toLowerCase();
        viewModel.clear();
        for (int i = 0; i < model.size(); i++) {
            Track t = model.get(i);
            String artist = t.artist == null ? "" : t.artist.toLowerCase();
            String title = t.title == null ? "" : t.title.toLowerCase();
            if (q.isEmpty() || title.contains(q) || artist.contains(q))
                viewModel.addElement(t);
        }
        int n = model.size();
        double total = 0;
        for (int i = 0; i < n; i++) total += model.get(i).durationSec;
        queueStatus.setText(plural(n) + "  ·  " + Theme.fmtTime(total));
    }

    private static String plural(int n) {
        int m = n % 100, d = n % 10;
        if (m >= 11 && m <= 14) return n + " треков";
        if (d == 1) return n + " трек";
        if (d >= 2 && d <= 4) return n + " трека";
        return n + " треков";
    }

    private void savePlaylist() {
        StringBuilder sb = new StringBuilder();
        StringBuilder ob = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            Track t = model.get(i);
            sb.append(t.file.getAbsolutePath().replace("\\", "\\\\"))
              .append(File.pathSeparatorChar);
            if (i > 0) ob.append(',');
            ob.append(t.number);
        }
        prefs.put(plKey(), sb.toString());
        prefs.put("pl.ord." + currentPlaylist, ob.toString());
    }

    private void loadPlaylist() {
        model.clear();
        String s = prefs.get(plKey(), "");
        List<Track> tracks = new ArrayList<>();
        if (!s.isEmpty()) {
            String[] paths = s.split(java.util.regex.Pattern.quote(File.pathSeparator));
            String[] ords = prefs.get("pl.ord." + currentPlaylist, "").split(",");
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                if (path.isEmpty()) continue;
                File f = new File(path);
                if (f.exists()) {
                    Track t = new Track(f);
                    t.playCount = prefs.getInt(trackKey(f), 0);
                    t.number = i < ords.length ? parseOrd(ords[i], i + 1) : i + 1;
                    model.addElement(t);
                    tracks.add(t);
                }
            }
        }
        if (!tracks.isEmpty()) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    for (Track t : tracks) t.loadMeta();
                    return null;
                }
                @Override protected void done() { rebuildView(); list.repaint(); }
            }.execute();
        }
        resetSort();
        rebuildView();
        list.repaint();
    }

    private static final DataFlavor TRACK_FLAVOR =
            new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=app.Track", "Track");

    private void initDragDrop() {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return MOVE; }
            @Override protected Transferable createTransferable(JComponent c) {
                int i = list.getSelectedIndex();
                if (i < 0) return null;
                final Track t = viewModel.get(i);
                return new Transferable() {
                    @Override public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[]{TRACK_FLAVOR};
                    }
                    @Override public boolean isDataFlavorSupported(DataFlavor f) {
                        return f.equals(TRACK_FLAVOR);
                    }
                    @Override public Object getTransferData(DataFlavor f) { return t; }
                };
            }
            @Override public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(TRACK_FLAVOR)
                    || s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport s) {
                try {
                    if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) s.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        List<File> dropped = new ArrayList<>();
                        for (File f : files)
                            if (f.isDirectory()) collect(f, dropped, 0);
                            else dropped.add(f);
                        addTracks(dropped);
                        return true;
                    }
                    if (s.isDataFlavorSupported(TRACK_FLAVOR)) {
                        if (!searchField.getText().trim().isEmpty()) return false;
                        JList.DropLocation dl = (JList.DropLocation) s.getDropLocation();
                        int drop = dl.getIndex();
                        Track t = (Track) s.getTransferable().getTransferData(TRACK_FLAVOR);
                        int old = indexOf(t);
                        if (old < 0) return false;
                        model.remove(old);
                        if (drop > old) drop--;
                        model.add(Math.max(0, Math.min(drop, model.size())), t);
                        list.setSelectedValue(t, true);
                        savePlaylist();
                        resetSort(); // ручной порядок отменяет активную сортировку
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        });
    }

    // ============================== PLAYBACK ==============================

    private void playTrack(int index) {
        if (index < 0 || index >= viewModel.size()) return;
        Track t = viewModel.get(index);
        list.setSelectedIndex(index);
        current = t;
        t.playCount++;
        prefs.putInt(trackKey(t.file), t.playCount);
        list.repaint();
        title.setText(t.title);
        artist.setText(t.artist == null || t.artist.trim().isEmpty() ? "Неизвестный исполнитель" : t.artist);
        meta.setText("Трек " + (model.indexOf(t) + 1) + " из " + model.size() + "   ·   " + t.lengthText());
        cover.setCover(null);
        ImageIcon ic = t.cover(320);
        if (ic != null) cover.setCover(ic.getImage());
        curTime.setText("0:00");
        totTime.setText(t.lengthText());
        new SwingWorker<AudioData, Void>() {
            @Override protected AudioData doInBackground() throws Exception {
                return AudioLoader.load(t.file);
            }
            @Override protected void done() {
                try {
                    AudioData d = get();
                    totTime.setText(Theme.fmtTime(d.durationSec()));
                    player.play(d);
                    playBtn.repaint();
                    if (sidebar != null && sidebar.getSelectedIndex() == 2) fetchLyrics();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String msg = cause instanceof OutOfMemoryError
                        ? "Файл слишком большой для памяти.\nЗапустите плеер через run.bat (выделяет 1 ГБ) или используйте MP3."
                        : "Не удалось открыть файл:\n" + cause.getMessage();
                    JOptionPane.showMessageDialog(frame, msg, "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void togglePlay() {
        if (player.current() == null) {
            if (list.getSelectedIndex() >= 0) playTrack(list.getSelectedIndex());
            else if (model.size() > 0) playTrack(0);
            return;
        }
        if (player.isPaused()) player.resume(); else player.pause();
        playBtn.repaint();
    }

    private void step(int dir) {
        int size = model.size();
        if (size == 0) return;
        int i = current == null ? -1 : indexOf(current);
        int next;
        if (shuffle && size > 1) {
            do { next = rnd.nextInt(size); } while (next == i);
        } else {
            next = (i + dir + size) % size;
        }
        playTrack(next);
    }

    private int indexOf(Track t) {
        for (int i = 0; i < model.size(); i++) if (model.get(i) == t) return i;
        return -1;
    }

    private void handleEnded() {
        if (repeat == 2) { playTrack(indexOf(current)); return; }
        int i = indexOf(current);
        if (i == model.size() - 1 && repeat == 0) { playBtn.repaint(); return; }
        step(1);
    }

    private void resetNow() {
        title.setText("—");
        artist.setText("Выбери трек");
        meta.setText(" ");
        cover.setCover(null);
        curTime.setText("0:00");
        totTime.setText("0:00");
        seek.setValue(0);
        playBtn.repaint();
    }

    private void tick() {
        if (seeking) return;
        AudioData d = player.current();
        if (d == null) return;
        double pos = player.positionSec();
        seek.setValue((int) (pos / d.durationSec() * 1000));
        curTime.setText(Theme.fmtTime(pos));
    }

    // ============================== WIDGETS ==============================

    private void styleScroll(JScrollPane sp) {
        JScrollBar v = sp.getVerticalScrollBar();
        v.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = Theme.BORDER;
                this.trackColor = Theme.PANEL;
            }
            @Override protected JButton createDecreaseButton(int o) { return noBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return noBtn(); }
            private JButton noBtn() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    private class TrackRenderer extends JPanel implements ListCellRenderer<Track> {
        private final JLabel coverSmall = new JLabel();
        private final JLabel idx = new JLabel();
        private final JLabel line1 = new JLabel();
        private final JLabel line2 = new JLabel();
        private final JLabel time = new JLabel();
        private final JLabel count = new JLabel();
        private final Icon countMark = new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_DIM);
                java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
                tri.moveTo(x, y); tri.lineTo(x, y + 10); tri.lineTo(x + 8, y + 5);
                tri.closePath();
                g2.fill(tri);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 8; }
            @Override public int getIconHeight() { return 10; }
        };
        private final JLabel cross = new JLabel(new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT_DIM);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 5, y + 5, x + 13, y + 13);
                g2.drawLine(x + 13, y + 5, x + 5, y + 13);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 18; }
            @Override public int getIconHeight() { return 18; }
        });
        private final Icon playMark = new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_HI);
                java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
                tri.moveTo(x, y); tri.lineTo(x, y + 12); tri.lineTo(x + 9, y + 6); tri.closePath();
                g2.fill(tri);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 10; }
            @Override public int getIconHeight() { return 12; }
        };
        TrackRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            coverSmall.setPreferredSize(new Dimension(46, 46));
            coverSmall.setHorizontalAlignment(SwingConstants.CENTER);
            idx.setFont(Theme.FONT_BOLD.deriveFont(12f));
            idx.setForeground(Theme.TEXT_DIM);
            idx.setPreferredSize(new Dimension(22, 0));
            idx.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            line1.setFont(Theme.FONT_BOLD);
            line2.setFont(Theme.FONT.deriveFont(12f));
            texts.add(line1);
            texts.add(Box.createVerticalStrut(2));
            texts.add(line2);
            JPanel center = new JPanel(new BorderLayout(8, 0));
            center.setOpaque(false);
            center.add(idx, BorderLayout.WEST);
            center.add(texts, BorderLayout.CENTER);
            add(coverSmall, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            count.setForeground(Theme.TEXT_DIM);
            count.setFont(Theme.FONT.deriveFont(12f));
            right.add(count);
            right.add(time);
            right.add(cross);
            add(right, BorderLayout.EAST);
            time.setForeground(Theme.TEXT_DIM);
            time.setFont(Theme.FONT.deriveFont(12f));
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getBackground().equals(Theme.SEL_BG)) {
                g.setColor(Theme.ACCENT);
                g.fillRect(0, 8, 3, getHeight() - 16);
            }
        }
        @Override public Component getListCellRendererComponent(JList<? extends Track> l,
                Track t, int index, boolean selected, boolean focus) {
            boolean isCurrent = (t == current);
            cross.setVisible(index == hoverRow);
            if (isCurrent) {
                idx.setIcon(playMark);
                idx.setText(null);
                idx.setForeground(Theme.ACCENT_HI);
                line1.setForeground(Theme.ACCENT_HI);
            } else {
                idx.setIcon(null);
                idx.setText(String.valueOf(t.number));
                idx.setForeground(Theme.TEXT_DIM);
                line1.setForeground(Theme.TEXT);
            }
            line2.setText(t.artist == null || t.artist.trim().isEmpty() ? "Неизвестный исполнитель" : t.artist);
            time.setText(t.lengthText());
            count.setIcon(t.playCount > 0 ? countMark : null);
            count.setText(t.playCount > 0 ? String.valueOf(t.playCount) : "");
            count.setIconTextGap(5);
            line1.setText(t.title);
            ImageIcon ic = t.cover(46);
            if (ic != null) {
                coverSmall.setIcon(new Icon() {
                    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(x, y, 46, 46, 10, 10));
                        ic.paintIcon(c, g2, x, y);
                        g2.dispose();
                    }
                    @Override public int getIconWidth() { return 46; }
                    @Override public int getIconHeight() { return 46; }
                });
                coverSmall.setText(null);
            } else {
                coverSmall.setIcon(null);
                coverSmall.setText(null);
            }
            if (selected) setBackground(Theme.SEL_BG);
            else setBackground(index % 2 == 0 ? Theme.PANEL : Theme.PANEL2);
            line2.setForeground(Theme.TEXT_DIM);
            return this;
        }
    }

    public void show() { frame.setVisible(true); }
}
