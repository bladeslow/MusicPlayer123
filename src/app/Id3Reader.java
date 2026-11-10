package app;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/** Minimal ID3v1/ID3v2 reader: title, artist, album, cover art (APIC). */
public final class Id3Reader {
    public static class Info {
        public String title, artist, album;
        public byte[] cover; // image bytes (jpg/png) or null
    }

    public static Info read(File f) {
        Info info = new Info();
        try {
            byte[] all = Files.readAllBytes(f.toPath());
            readV2(all, info);
            readV1(all, info);
        } catch (Exception ignored) {}
        if (info.title == null || info.title.trim().isEmpty()) {
            String n = f.getName();
            info.title = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
        }
        if (info.artist == null) info.artist = "";
        if (info.album == null) info.album = "";
        return info;
    }

    private static void readV1(byte[] all, Info info) {
        if (all.length < 128) return;
        String tag = new String(all, all.length - 128, 3, Charset.forName("ISO-8859-1"));
        if (!"TAG".equals(tag)) return;
        if (info.title == null) info.title = clean(new String(all, all.length - 125, 30, Charset.forName("ISO-8859-1")));
        if (info.artist == null) info.artist = clean(new String(all, all.length - 95, 30, Charset.forName("ISO-8859-1")));
        if (info.album == null) info.album = clean(new String(all, all.length - 65, 30, Charset.forName("ISO-8859-1")));
    }

    private static String clean(String s) {
        int i = s.indexOf(0);
        if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    private static void readV2(byte[] all, Info info) throws IOException {
        if (all.length < 10) return;
        if (!(all[0] == 'I' && all[1] == 'D' && all[2] == '3')) return;
        int major = all[3];
        int size = ((all[6] & 0x7f) << 21) | ((all[7] & 0x7f) << 14) |
                   ((all[8] & 0x7f) << 7) | (all[9] & 0x7f);
        ByteArrayInputStream in = new ByteArrayInputStream(all, 10, size);
        while (in.available() >= 10) {
            byte[] fh = new byte[10];
            in.read(fh);
            String id = new String(fh, 0, 4, Charset.forName("ISO-8859-1"));
            if (id.indexOf(0) == 0) break; // padding
            int fsz;
            if (major == 4) {
                fsz = ((fh[4] & 0x7f) << 21) | ((fh[5] & 0x7f) << 14) |
                      ((fh[6] & 0x7f) << 7) | (fh[7] & 0x7f);
            } else {
                fsz = ((fh[4] & 0xff) << 24) | ((fh[5] & 0xff) << 16) |
                      ((fh[6] & 0xff) << 8) | (fh[7] & 0xff);
            }
            if (fsz <= 0 || fsz > in.available()) break;
            byte[] data = new byte[fsz];
            in.read(data);
            switch (id) {
                case "TIT2": info.title = text(data); break;
                case "TPE1": info.artist = text(data); break;
                case "TALB": info.album = text(data); break;
                case "APIC": info.cover = apic(data); break;
                default: break;
            }
        }
    }

    private static String text(byte[] d) {
        if (d.length == 0) return "";
        int enc = d[0] & 0xff;
        int start = 1, len = d.length - 1;
        String cs;
        switch (enc) {
            case 0: cs = "ISO-8859-1"; break;
            case 1: cs = "UTF-16"; break;
            case 2: cs = "UTF-16BE"; break;
            default: cs = "UTF-8"; break;
        }
        String s = new String(d, start, len, Charset.forName(cs));
        int i = s.indexOf(0);
        if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    private static byte[] apic(byte[] d) {
        // encoding(1) mime\0 type(1) desc\0 image
        int enc = d[0] & 0xff;
        int i = 1;
        while (i < d.length && d[i] != 0) i++;          // mime
        i++;                                            // type byte
        if (enc == 1 || enc == 2) {                     // UTF-16 desc
            while (i + 1 < d.length) {
                boolean end = (d[i] == 0 && d[i + 1] == 0);
                i += 2;
                if (end) break;
            }
        } else {
            while (i < d.length && d[i] != 0) i++;
            i++;
        }
        if (i >= d.length) return null;
        byte[] img = new byte[d.length - i];
        System.arraycopy(d, i, img, 0, img.length);
        return img;
    }
}
