package app;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;

/** Minimal monochrome theme: near-white surfaces, black accent, hairline borders. */
public final class Theme {
    public static final Color BG        = new Color(0xE7E7EA);
    public static final Color PANEL     = new Color(0xF6F6F8);
    public static final Color PANEL2    = new Color(0xDEDEE2);
    public static final Color SEL_BG    = new Color(0xD3D3DA);
    public static final Color BORDER    = new Color(0xC6C6CD);
    public static final Color TEXT      = new Color(0x17171A);
    public static final Color TEXT_DIM  = new Color(0x707078);
    public static final Color ACCENT    = new Color(0x17171A);
    public static final Color ACCENT_HI = new Color(0x17171A);
    public static final Font FONT       = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD, 13);

    public static void init() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", FONT);
        UIManager.put("Button.font", FONT);
        UIManager.put("ToggleButton.font", FONT);
        UIManager.put("Slider.font", FONT);
        UIManager.put("List.font", FONT);
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.selectionBackground", SEL_BG);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", TEXT_DIM);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextPane.background", PANEL);
        UIManager.put("TextPane.foreground", TEXT);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TextField.background", PANEL2);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("ComboBox.background", PANEL2);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", SEL_BG);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.buttonBackground", PANEL2);
        UIManager.put("ComboBox.buttonForeground", TEXT);
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.background", BG);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
    }

    public static JButton flatButton(String text, Runnable action) {
        JButton b = new JButton(text) {
            private boolean hover;
            {
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setForeground(TEXT);
                setFont(FONT);
                setBorder(new EmptyBorder(6, 14, 6, 14));
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hover ? SEL_BG : PANEL2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JSlider hSlider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setUI(new BasicSliderUI(s) {
            @Override public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int y = trackRect.y + trackRect.height / 2 - 2;
                int x1 = trackRect.x, x2 = trackRect.x + trackRect.width;
                double frac = (slider.getValue() - slider.getMinimum())
                        / (double) (slider.getMaximum() - slider.getMinimum());
                int xp = x1 + (int) (frac * (x2 - x1));
                g2.setColor(BORDER); g2.fillRoundRect(x1, y, x2 - x1, 4, 4, 4);
                g2.setColor(ACCENT); g2.fillRoundRect(x1, y, Math.max(4, xp - x1), 4, 4, 4);
                g2.dispose();
            }
            @Override public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = thumbRect.x + thumbRect.width / 2;
                int cy = thumbRect.y + thumbRect.height / 2;
                g2.setColor(ACCENT_HI);
                g2.fillOval(cx - 6, cy - 6, 12, 12);
                g2.setColor(BG);
                g2.fillOval(cx - 2, cy - 2, 4, 4);
                g2.dispose();
            }
        });
        s.setOpaque(false);
        s.setFocusable(false);
        s.addChangeListener(e -> s.repaint()); // full repaint: no track-fill ghosting
        return s;
    }

    public static JSlider vSlider(int min, int max, int value) {
        JSlider s = new JSlider(JSlider.VERTICAL, min, max, value);
        s.setUI(new BasicSliderUI(s) {
            @Override public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = trackRect.x + trackRect.width / 2 - 2;
                int y1 = trackRect.y, y2 = trackRect.y + trackRect.height;
                double frac = (slider.getValue() - slider.getMinimum())
                        / (double) (slider.getMaximum() - slider.getMinimum());
                int yp = y2 - (int) (frac * (y2 - y1));
                g2.setColor(BORDER); g2.fillRoundRect(x, y1, 4, y2 - y1, 4, 4);
                g2.setColor(ACCENT); g2.fillRoundRect(x, yp, 4, Math.max(4, y2 - yp), 4, 4);
                g2.dispose();
            }
            @Override public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = thumbRect.x + thumbRect.width / 2;
                int cy = thumbRect.y + thumbRect.height / 2;
                g2.setColor(ACCENT_HI);
                g2.fillOval(cx - 6, cy - 6, 12, 12);
                g2.setColor(BG);
                g2.fillOval(cx - 2, cy - 2, 4, 4);
                g2.dispose();
            }
        });
        s.setOpaque(false);
        s.setFocusable(false);
        s.addChangeListener(e -> s.repaint());
        return s;
    }

    public static class CoverLabel extends JLabel {
        private Image img;
        public void setCover(Image i) { img = i; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(PANEL2);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            if (img != null) {
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 12, 12));
                g2.drawImage(img, 0, 0, w, h, null);
                g2.setClip(null);
            } else {
                g2.setColor(new Color(0xC4C4CB));
                g2.setStroke(new BasicStroke(2.5f));
                int cx = w / 2, cy = h / 2;
                g2.drawOval(cx - 24, cy - 24, 48, 48);
                g2.drawLine(cx + 24, cy, cx + 24, cy - 32);
                g2.drawLine(cx + 24, cy - 32, cx + 35, cy - 36);
            }
            g2.dispose();
        }
    }

    /** Icons drawn programmatically (no external assets). Color parameterised. */
    public static final class Icons {
        public static Icon play(int s, Color c)  { return draw(s, (g,x,y,z) -> { g.setColor(c);
            Path2D.Double t = new Path2D.Double();
            t.moveTo(x+z*0.30, y+z*0.20); t.lineTo(x+z*0.30, y+z*0.80);
            t.lineTo(x+z*0.82, y+z*0.50); t.closePath();
            g.fill(t); }); }
        public static Icon pause(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.fillRoundRect((int)(x+z*0.25), (int)(y+z*0.20), (int)(z*0.16), (int)(z*0.60), 3, 3);
            g.fillRoundRect((int)(x+z*0.57), (int)(y+z*0.20), (int)(z*0.16), (int)(z*0.60), 3, 3); }); }
        public static Icon plus(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.setStroke(new BasicStroke((float)(z*0.12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int)(x+z*0.5), (int)(y+z*0.18), (int)(x+z*0.5), (int)(y+z*0.82));
            g.drawLine((int)(x+z*0.18), (int)(y+z*0.5), (int)(x+z*0.82), (int)(y+z*0.5));
        }); }
        public static Icon folder(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.setStroke(new BasicStroke((float)(z*0.10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
            p.moveTo(x+z*0.12, y+z*0.25); p.lineTo(x+z*0.38, y+z*0.25);
            p.lineTo(x+z*0.48, y+z*0.38); p.lineTo(x+z*0.88, y+z*0.38);
            p.lineTo(x+z*0.88, y+z*0.78); p.lineTo(x+z*0.12, y+z*0.78); p.closePath();
            g.draw(p);
            g.drawLine((int)(x+z*0.12), (int)(y+z*0.52), (int)(x+z*0.88), (int)(y+z*0.52));
        }); }
        public static Icon equalizer(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            double[] h = {0.38, 0.72, 0.52, 0.85};
            double bw = z * 0.14;
            for (int i = 0; i < 4; i++) {
                double bh = z * h[i];
                double bx = x + z * (0.08 + i * 0.24);
                double by = y + z * 0.92 - bh;
                g.fillRoundRect((int) bx, (int) by, (int) bw, (int) bh, (int) bw, (int) bw);
            }
        }); }
        public static Icon lyrics(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            // нота слева
            g.fillOval((int)(x+z*0.04), (int)(y+z*0.62), (int)(z*0.30), (int)(z*0.24));
            g.setStroke(new BasicStroke((float)(z*0.11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int)(x+z*0.32), (int)(y+z*0.70), (int)(x+z*0.32), (int)(y+z*0.12));
            java.awt.geom.Path2D.Double flag = new java.awt.geom.Path2D.Double();
            flag.moveTo(x+z*0.32, y+z*0.12); flag.lineTo(x+z*0.58, y+z*0.26);
            flag.lineTo(x+z*0.32, y+z*0.42); flag.closePath();
            g.fill(flag);
            // две строки текста справа
            g.setStroke(new BasicStroke((float)(z*0.10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int)(x+z*0.62), (int)(y+z*0.42), (int)(x+z*0.94), (int)(y+z*0.42));
            g.drawLine((int)(x+z*0.62), (int)(y+z*0.72), (int)(x+z*0.86), (int)(y+z*0.72));
        }); }
        public static Icon stop(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.fillRoundRect((int)(x+z*0.24), (int)(y+z*0.24), (int)(z*0.52), (int)(z*0.52), 3, 3); }); }
        public static Icon next(int s, Color c)  { return draw(s, (g,x,y,z) -> { g.setColor(c);
            Path2D.Double t = new Path2D.Double();
            t.moveTo(x+z*0.15, y+z*0.20); t.lineTo(x+z*0.15, y+z*0.80);
            t.lineTo(x+z*0.50, y+z*0.50); t.closePath();
            g.fill(t);
            g.fillRoundRect((int)(x+z*0.58), (int)(y+z*0.20), (int)(z*0.12), (int)(z*0.60), 3, 3); }); }
        public static Icon prev(int s, Color c)  { return draw(s, (g,x,y,z) -> { g.setColor(c);
            Path2D.Double t = new Path2D.Double();
            t.moveTo(x+z*0.85, y+z*0.20); t.lineTo(x+z*0.85, y+z*0.80);
            t.lineTo(x+z*0.50, y+z*0.50); t.closePath();
            g.fill(t);
            g.fillRoundRect((int)(x+z*0.30), (int)(y+z*0.20), (int)(z*0.12), (int)(z*0.60), 3, 3); }); }
        public static Icon shuffle(int s, Color c){ return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.setStroke(new BasicStroke((float)(z*0.10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine((int)(x+z*0.15), (int)(y+z*0.25), (int)(x+z*0.45), (int)(y+z*0.25));
            g.drawLine((int)(x+z*0.55), (int)(y+z*0.75), (int)(x+z*0.85), (int)(y+z*0.75));
            g.drawLine((int)(x+z*0.15), (int)(y+z*0.75), (int)(x+z*0.50), (int)(y+z*0.28));
            g.drawLine((int)(x+z*0.50), (int)(y+z*0.72), (int)(x+z*0.85), (int)(y+z*0.25)); }); }
        public static Icon repeat(int s, Color c) { return draw(s, (g,x,y,z) -> { g.setColor(c);
            g.setStroke(new BasicStroke((float)(z*0.10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect((int)(x+z*0.15), (int)(y+z*0.30), (int)(z*0.70), (int)(z*0.40), 8, 8);
            g.drawLine((int)(x+z*0.30), (int)(y+z*0.18), (int)(x+z*0.72), (int)(y+z*0.18));
            g.drawLine((int)(x+z*0.72), (int)(y+z*0.10), (int)(x+z*0.72), (int)(y+z*0.28));
            g.drawLine((int)(x+z*0.28), (int)(y+z*0.82), (int)(x+z*0.70), (int)(y+z*0.82));
            g.drawLine((int)(x+z*0.28), (int)(y+z*0.74), (int)(x+z*0.28), (int)(y+z*0.92)); }); }
        public static Icon vol(int s, Color c, boolean mute) { return draw(s, (g,x,y,z) -> { g.setColor(mute?TEXT_DIM:c);
            Path2D.Double t = new Path2D.Double();
            t.moveTo(x+z*0.10, y+z*0.40); t.lineTo(x+z*0.25, y+z*0.40);
            t.lineTo(x+z*0.45, y+z*0.22); t.lineTo(x+z*0.45, y+z*0.78);
            t.lineTo(x+z*0.25, y+z*0.60); t.lineTo(x+z*0.10, y+z*0.60); t.closePath();
            g.fill(t);
            if (!mute) { g.setStroke(new BasicStroke((float)(z*0.07), BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
                g.drawArc((int)(x+z*0.42), (int)(y+z*0.30), (int)(z*0.40), (int)(z*0.40), -60, 120); } }); }

        interface Painter { void paint(Graphics2D g, double x, double y, double z); }
        static Icon draw(int size, Painter p) {
            return new Icon() {
                @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    p.paint(g2, x, y, size);
                    g2.dispose();
                }
                @Override public int getIconWidth() { return size; }
                @Override public int getIconHeight() { return size; }
            };
        }
    }

    public static String fmtTime(double sec) {
        if (sec < 0) sec = 0;
        int s = (int) sec;
        return String.format("%d:%02d", s / 60, s % 60);
    }


    /** Минимальный UI вкладок: без рамок и обводок, выбранная — чёрное подчёркивание. */
    public static javax.swing.plaf.basic.BasicTabbedPaneUI minimalTabUI() {
        return new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                // никаких рамок
            }
            @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                // никаких рамок
            }
            @Override protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                if (isSelected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(ACCENT);
                    g2.fillRect(x, y + h - 2, w, 2);
                    g2.dispose();
                }
            }
        };
    }

    /** Fix Nimbus tabbed-pane colors: no blue, minimal monochrome style. Call AFTER Nimbus is installed. */
    public static void tabFix() {
        UIManager.put("TabbedPane.contentAreaColor", PANEL);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane:TabbedPaneTabArea[Enabled].backgroundPainter",
            (javax.swing.Painter<JComponent>) (g, c, w, h) -> { g.setColor(PANEL); g.fillRect(0, 0, w, h); });
        UIManager.put("TabbedPane:TabbedPaneContent[Enabled].backgroundPainter",
            (javax.swing.Painter<JComponent>) (g, c, w, h) -> { g.setColor(PANEL); g.fillRect(0, 0, w, h); });
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled].backgroundPainter",
            (javax.swing.Painter<JComponent>) (g, c, w, h) -> { g.setColor(PANEL); g.fillRect(0, 0, w, h); });
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected].backgroundPainter",
            (javax.swing.Painter<JComponent>) (g, c, w, h) -> {
                g.setColor(PANEL); g.fillRect(0, 0, w, h);
                g.setColor(ACCENT); g.fillRect(0, h - 2, w, 2);
            });
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+MouseOver].backgroundPainter",
            (javax.swing.Painter<JComponent>) (g, c, w, h) -> { g.setColor(PANEL2); g.fillRect(0, 0, w, h); });
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled].textForeground", TEXT_DIM);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+MouseOver].textForeground", TEXT);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected].textForeground", TEXT);
        javax.swing.Painter<JComponent> emptyBorder =
            (g, comp, w, h) -> { /* no border */ };
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+MouseOver].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Pressed].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected+MouseOver].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected+Focused].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Focused].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+MouseOver+Focused].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Pressed+Focused].borderPainter", emptyBorder);
        UIManager.put("TabbedPane:TabbedPaneTab[Enabled+Selected+Pressed].borderPainter", emptyBorder);
    }
}
