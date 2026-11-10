package app;

import javax.sound.sampled.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Playback engine: streams decoded PCM through a 10-band EQ to the soundcard. */
public final class Player implements AutoCloseable {

    public interface Listener {
        void onTrackEnded();
        void onError(String msg);
    }

    private static final int CHUNK = 2048; // frames per processing block

    private final Equalizer equalizer;
    private volatile AudioData data;
    private volatile SourceDataLine line;
    private volatile long cursor;          // frames
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean closing;
    private volatile float volume = 0.8f;
    private volatile double level = 0;
    private boolean checkLogged = false;
    private volatile boolean stopped = false;
    private Thread thread;
    private Listener listener;

    public Player(Equalizer eq) { this.equalizer = eq; }

    public void setListener(Listener l) { this.listener = l; }

    public void setVolume(float v) { this.volume = Math.max(0, Math.min(1, v)); }

    public float getVolume() { return volume; }

    public synchronized void play(AudioData d) {
        stopInternal();
        this.data = d;
        this.cursor = 0;
        this.equalizer.setSampleRate(d.sampleRate);
        checkLogged = false;
        if (!openLine(d.sampleRate)) {
            playing = false;
            return; // onError already reported by openLine
        }
        playing = true;
        paused = false;
        thread = new Thread(this::pump, "audio-pump");
        thread.setDaemon(true);
        thread.start();
    }

    private AudioFormat fmt(int rate) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                rate, 16, 2, 4, rate, false);
    }

    /** Try default line, then every mixer. Returns true on success. */
    private boolean openLine(int rate) {
        AudioFormat f = fmt(rate);
        int bufBytes = Math.max(32768, rate); // ~250 ms at 16-bit stereo
        // 1) default mixer
        try {
            SourceDataLine l = AudioSystem.getSourceDataLine(f);
            l.open(f, bufBytes);
            l.start();
            line = l;
            log("LINE OK (default mixer), rate=" + rate + " buf=" + bufBytes);
            return true;
        } catch (Exception e) {
            log("default mixer failed: " + e);
        }
        // 2) enumerate all mixers
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                SourceDataLine l = AudioSystem.getSourceDataLine(f, mi);
                l.open(f, bufBytes);
                l.start();
                line = l;
                log("LINE OK (mixer: " + mi.getName() + "), rate=" + rate);
                return true;
            } catch (Exception e) {
                log("mixer " + mi.getName() + " failed: " + e);
            }
        }
        reportError("Аудиоустройство недоступно. Проверьте, что в Windows выбрано устройство воспроизведения по умолчанию и оно не отключено. Детали в vibe-debug.log");
        return false;
    }

    private void pump() {
        float[] block = new float[CHUNK * 2];
        byte[] out = new byte[CHUNK * 4];
        try {
            while (!stopped && playing) {
                AudioData d = data;
                SourceDataLine ln = line;
                if (d == null || ln == null) break;
                if (paused) { sleep(30); continue; }
                long pos = cursor;
                if (pos >= d.frames) { ended(ln); break; }
                int n = (int) Math.min(CHUNK, d.frames - pos);
                System.arraycopy(d.samples, (int) pos * 2, block, 0, n * 2);
                cursor = pos + n;
                equalizer.process(block, n);
                float v = volume;
                double peak = 0;
                for (int i = 0; i < n * 2; i++) {
                    float s = block[i] * v;
                    if (s > 1f) s = 1f; else if (s < -1f) s = -1f;
                    double a = s < 0 ? -s : s;
                    if (a > peak) peak = a;
                    int si = (int) (s * 32767f);
                    out[2 * i] = (byte) (si & 0xff);
                    out[2 * i + 1] = (byte) ((si >> 8) & 0xff);
                }
                level = Math.max(peak, level * 0.86);
                ln.write(out, 0, n * 4);
                if (!checkLogged && pos > d.sampleRate * 2) {
                    checkLogged = true;
                    try {
                        log("playback check: linePos="
                            + String.format("%.2f", ln.getMicrosecondPosition() / 1e6)
                            + "s written=" + String.format("%.2f", cursor / (double) d.sampleRate) + "s");
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            if (!closing && !stopped) {
                log("pump error: " + e);
                reportError("Ошибка воспроизведения: " + e.getMessage());
            }
        }
    }

    private void ended(SourceDataLine ln) {
        playing = false;
        try { ln.drain(); } catch (Exception ignored) {} // let the tail sound
        if (listener != null) SwingInvoke.later(() -> listener.onTrackEnded());
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    static final class SwingInvoke {
        static void later(Runnable r) { javax.swing.SwingUtilities.invokeLater(r); }
    }

    private void reportError(String msg) {
        log("ERROR: " + msg);
        if (listener != null) SwingInvoke.later(() -> listener.onError(msg));
    }

    static synchronized void log(String msg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                System.getProperty("user.home") + "/vibe-debug.log", true))) {
            pw.println(new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + msg);
        } catch (Exception ignored) {}
    }

    public void pause() { paused = true; }
    public void resume() { paused = false; }
    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return playing && !paused; }

    public void seek(double sec) {
        AudioData d = data;
        if (d == null) return;
        cursor = Math.max(0, Math.min(d.frames - 1, (long) (sec * d.sampleRate)));
    }

    public double positionSec() {
        AudioData d = data;
        if (d == null) return 0;
        return cursor / (double) d.sampleRate;
    }

    public AudioData current() { return data; }

    /** Smoothed output level 0..1 (post-EQ, post-volume) for the VU meter. */
    public double getLevel() { return level; }

    private synchronized void stopInternal() {
        stopped = true;
        closing = true;
        playing = false;
        if (thread != null) {
            try { thread.join(400); } catch (InterruptedException ignored) {}
        }
        stopped = false;
        closing = false;
        if (line != null) {
            try { line.stop(); line.flush(); line.close(); } catch (Exception ignored) {}
            line = null;
        }
    }

    public void stop() { stopInternal(); data = null; }

    @Override public void close() { stop(); }
}
