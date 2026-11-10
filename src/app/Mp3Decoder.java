package app;

import java.io.File;
import java.io.FileInputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.SampleBuffer;

/** Decodes MP3 to stereo float PCM using JLayer. */
public final class Mp3Decoder {

    public static class Result {
        public float[] samples;
        public int sampleRate;
        public int channels;
    }

    public static Result decode(File f) throws Exception {
        Bitstream bs = null;
        try {
            bs = new Bitstream(new FileInputStream(f));
            Decoder dec = new Decoder();
            java.util.ArrayList<short[]> chunks = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> lens = new java.util.ArrayList<>();
            int total = 0;
            while (true) {
                javazoom.jl.decoder.Header h = bs.readFrame();
                if (h == null) break;
                SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                short[] s = out.getBuffer();
                int n = out.getBufferLength();
                // JLayer reuses its internal buffer every frame - copy immediately!
                short[] copy = new short[n];
                System.arraycopy(s, 0, copy, 0, n);
                chunks.add(copy); lens.add(n);
                total += n;
                bs.closeFrame();
            }
            int rate = dec.getOutputFrequency();
            int ch = dec.getOutputChannels();
            float[] pcm = new float[total];
            int pos = 0;
            for (int i = 0; i < chunks.size(); i++) {
                short[] s = chunks.get(i);
                int n = lens.get(i);
                for (int j = 0; j < n; j++) pcm[pos + j] = s[j] / 32768f;
                pos += n;
            }
            Result r = new Result();
            r.sampleRate = rate;
            r.channels = ch;
            r.samples = stereo(pcm, ch);
            return r;
        } finally {
            if (bs != null) try { bs.close(); } catch (Exception ignored) {}
        }
    }

    /** Convert interleaved channels (1 or 2) to interleaved stereo float. */
    static float[] stereo(float[] src, int ch) {
        if (ch == 2) return src;
        float[] out = new float[src.length * 2];
        for (int i = 0; i < src.length; i++) {
            out[2 * i] = src[i];
            out[2 * i + 1] = src[i];
        }
        return out;
    }
}
