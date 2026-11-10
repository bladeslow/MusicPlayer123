package app;

/** Fully decoded audio: interleaved stereo float PCM, normalized to [-1,1]. */
public final class AudioData {
    public final float[] samples;   // interleaved L,R
    public final int sampleRate;
    public final long frames;

    public AudioData(float[] samples, int sampleRate) {
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.frames = samples.length / 2;
    }

    public double durationSec() { return frames / (double) sampleRate; }
}
