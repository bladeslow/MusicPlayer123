package app;

import javax.sound.sampled.*;
import java.io.File;

/** Loads audio files: WAV/AIFF natively (lossless), MP3 via JLayer. */
public final class AudioLoader {

    public static String[] EXTENSIONS = {"mp3", "wav", "aif", "aiff"};

    public static boolean isSupported(File f) {
        String n = f.getName().toLowerCase();
        for (String e : EXTENSIONS) if (n.endsWith("." + e)) return true;
        return false;
    }

    public static AudioData load(File f) throws Exception {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".mp3")) {
            Mp3Decoder.Result r = Mp3Decoder.decode(f);
            return new AudioData(r.samples, r.sampleRate);
        }
        AudioInputStream in = AudioSystem.getAudioInputStream(f);
        AudioFormat base = in.getFormat();
        AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16, base.getChannels(),
                base.getChannels() * 2, base.getSampleRate(), false);
        AudioInputStream cin = AudioSystem.getAudioInputStream(pcm, in);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[65536]; int rd;
            while ((rd = cin.read(tmp)) != -1) bos.write(tmp, 0, rd);
            byte[] bytes = bos.toByteArray();
        cin.close(); in.close();
        int frames = bytes.length / (2 * pcm.getChannels());
        float[] out = new float[frames * 2];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < 2; c++) {
                int sc = Math.min(c, pcm.getChannels() - 1);
                int off = (i * pcm.getChannels() + sc) * 2;
                short v = (short) ((bytes[off + 1] << 8) | (bytes[off] & 0xff));
                out[2 * i + c] = v / 32768f;
            }
        }
        return new AudioData(out, (int) pcm.getSampleRate());
    }

    /** Fast duration estimate without full decode (for the playlist). */
    public static double durationSeconds(File f) {
        try {
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".mp3")) {
                AudioInputStream in = AudioSystem.getAudioInputStream(f);
                AudioFormat fmt = in.getFormat();
                long bytes = f.length() - in.getFrameLength() * fmt.getFrameSize();
                // frame length known for wav/aiff
                long fr = in.getFrameLength();
                in.close();
                if (fr > 0) return fr / fmt.getFrameRate();
                return 0;
            }
            // MP3: estimate from first MPEG frame bitrate
            double br = firstFrameBitrate(f);
            long audioBytes = f.length() - id3v2Size(f);
            return br > 0 ? (audioBytes * 8.0) / (br * 1000.0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int id3v2Size(File f) {
        try (java.io.FileInputStream is = new java.io.FileInputStream(f)) {
            byte[] h = new byte[10];
            if (is.read(h) != 10) return 0;
            if (!(h[0]=='I'&&h[1]=='D'&&h[2]=='3')) return 0;
            return 10 + ((h[6]&0x7f)<<21 | (h[7]&0x7f)<<14 | (h[8]&0x7f)<<7 | (h[9]&0x7f));
        } catch (Exception e) { return 0; }
    }

    private static double firstFrameBitrate(File f) {
        try (java.io.FileInputStream is = new java.io.FileInputStream(f)) {
            byte[] b = new byte[4];
            int skip = id3v2Size(f);
            if (skip > 0) is.skip(skip);
            while (is.read(b) == 4) {
                int h = ((b[0]&0xff)<<24)|((b[1]&0xff)<<16)|((b[2]&0xff)<<8)|(b[3]&0xff);
                if ((h & 0xffe00000) == 0xffe00000) {
                    int idx = (h >> 12) & 0xf; // MPEG1 layer3 table
                    int ver = (h >> 19) & 3;
                    int layer = (h >> 17) & 3;
                    if (layer == 1 && idx > 0) { // Layer III
                        if (ver == 3) { // MPEG1
                            int[] br = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320};
                            return br[idx];
                        } else { // MPEG2/2.5
                            int[] br = {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
                            return br[idx];
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return 128; // fallback
    }
}
