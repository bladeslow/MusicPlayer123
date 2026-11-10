package app;

import java.util.Arrays;

/** 10-band graphic equalizer applied to interleaved stereo float PCM. */
public final class Equalizer {
    public static final String[] BAND_NAMES =
        {"31","62","125","250","500","1K","2K","4K","8K","16K"};
    public static final double[] FREQS =
        {31.25, 62.5, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    public static final double MIN_DB = -12, MAX_DB = 12;

    private final Biquad[][] filters = new Biquad[FREQS.length][2]; // [band][channel]
    private final double[] gainsDb = new double[FREQS.length];
    private volatile boolean enabled = false;
    private double sampleRate = 44100;

    public Equalizer() {
        for (int b = 0; b < FREQS.length; b++) {
            filters[b][0] = new Biquad();
            filters[b][1] = new Biquad();
        }
        recalc();
    }

    public synchronized void setSampleRate(double fs) {
        sampleRate = fs;
        recalc();
    }

    public synchronized void setGain(int band, double db) {
        gainsDb[band] = Math.max(MIN_DB, Math.min(MAX_DB, db));
        recalc();
    }

    public synchronized double getGain(int band) { return gainsDb[band]; }

    public synchronized double[] getGains() { return Arrays.copyOf(gainsDb, gainsDb.length); }

    public synchronized void setGains(double[] db) {
        for (int i = 0; i < gainsDb.length && i < db.length; i++) setGain(i, db[i]);
    }

    public synchronized void setEnabled(boolean on) { enabled = on; recalc(); }
    public boolean isEnabled() { return enabled; }

    private void recalc() {
        for (int b = 0; b < FREQS.length; b++) {
            double g = enabled ? gainsDb[b] : 0.0;
            filters[b][0].setPeaking(FREQS[b], 1.15, g, sampleRate);
            filters[b][1].setPeaking(FREQS[b], 1.15, g, sampleRate);
        }
    }

    /** Process nFrames of interleaved stereo data in place. */
    public void process(float[] buf, int nFrames) {
        if (!enabled) return;
        for (int i = 0; i < nFrames; i++) {
            double l = buf[2 * i], r = buf[2 * i + 1];
            for (int b = 0; b < FREQS.length; b++) {
                l = filters[b][0].process(l);
                r = filters[b][1].process(r);
            }
            buf[2 * i] = (float) l;
            buf[2 * i + 1] = (float) r;
        }
    }
}
