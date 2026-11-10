package app;

import java.io.File;

public final class Track {
    public final File file;
    public String title;
    public String artist;
    public String album;
    public double durationSec;
    public int playCount;
    /** fixed playlist number; does not change when sorting */
    public int number;
    private byte[] coverBytes;
    private javax.swing.ImageIcon cover;

    public Track(File f) {
        this.file = f;
        this.title = f.getName();
    }

    /** Read tags + duration; call off the EDT. */
    public void loadMeta() {
        try {
            if (file.getName().toLowerCase().endsWith(".mp3")) {
                Id3Reader.Info info = Id3Reader.read(file);
                title = info.title;
                artist = info.artist;
                album = info.album;
                coverBytes = info.cover;
            } else {
                String n = file.getName();
                title = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
                artist = "";
                album = "";
            }
            durationSec = AudioLoader.durationSeconds(file);
            if (coverBytes == null) fetchCoverOnline();
        } catch (Exception e) {
            durationSec = 0;
        }
    }

    /** Обложка из интернета (iTunes Search API, без ключа), если в тегах её нет. Кэш на диске. */
    private void fetchCoverOnline() {
        try {
            String a = artist == null ? "" : artist.trim();
            String t = title == null ? "" : title.trim();
            if (t.isEmpty()) {
                String n = file.getName();
                if (n.contains(".")) n = n.substring(0, n.lastIndexOf('.'));
                if (n.contains(" - ")) {
                    String[] parts = n.split(" - ", 2);
                    if (a.isEmpty()) a = parts[0].trim();
                    t = parts[1].trim();
                } else t = n.trim();
            }
            String query = (a + " " + t).trim();
            if (query.isEmpty()) return;
            File dir = new File(new File(System.getProperty("user.home"), ".vibeplayer"), "covers");
            if (!dir.isDirectory()) dir.mkdirs();
            File cache = new File(dir, "t_" + Integer.toHexString(file.getAbsolutePath().hashCode()) + ".jpg");
            if (cache.isFile()) {
                byte[] data = java.nio.file.Files.readAllBytes(cache.toPath());
                if (data.length > 1000) { coverBytes = data; return; }
            }
            String u = "https://itunes.apple.com/search?term="
                    + java.net.URLEncoder.encode(query, "UTF-8") + "&entity=song&limit=1";
            String body = new String(httpGet(u, 6000), "UTF-8");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "\"artworkUrl100\":\"(https://[^\"]+)\"").matcher(body);
            if (!m.find()) return;
            byte[] img = httpGet(m.group(1).replace("100x100bb", "600x600bb"), 10000);
            if (img.length <= 1000) return;
            coverBytes = img;
            try { java.nio.file.Files.write(cache.toPath(), img); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static byte[] httpGet(String u, int timeout) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
        c.setRequestProperty("User-Agent", "VibePlayer/1.0");
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        java.io.InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) buf.write(b, 0, n);
        in.close();
        return buf.toByteArray();
    }

    private transient java.awt.image.BufferedImage fullImage;
    private final java.util.Map<Integer, javax.swing.ImageIcon> covers =
            new java.util.HashMap<>();

    public javax.swing.ImageIcon cover(int size) {
        if (fullImage == null && coverBytes != null) {
            try {
                fullImage = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(coverBytes));
            } catch (Exception e) { coverBytes = null; }
        }
        if (fullImage == null) return null;
        javax.swing.ImageIcon ic = covers.get(size);
        if (ic == null) {
            ic = new javax.swing.ImageIcon(fullImage.getScaledInstance(
                    size, size, java.awt.Image.SCALE_SMOOTH));
            covers.put(size, ic);
        }
        return ic;
    }

    public String displayName() {
        return artist == null || artist.trim().isEmpty() ? title : artist + " – " + title;
    }

    public String lengthText() {
        if (durationSec <= 0) return "--:--";
        int s = (int) durationSec;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override public String toString() { return displayName(); }
}
