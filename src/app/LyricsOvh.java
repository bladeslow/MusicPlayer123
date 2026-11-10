package app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Free lyrics database (api.lyrics.ovh) - no API key required. */
public final class LyricsOvh {

    public static String fetch(String artist, String title) throws Exception {
        String a = artist == null ? "" : artist.trim();
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new Exception("нет названия трека");
        String u = "https://api.lyrics.ovh/v1/" + URLEncoder.encode(a, "UTF-8")
                + "/" + URLEncoder.encode(t, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "VibePlayer/1.0");
        c.setConnectTimeout(5000);
        c.setReadTimeout(9000);
        String body = read(c);
        if (c.getResponseCode() != 200) throw new Exception("код " + c.getResponseCode());
        int i = body.indexOf("\"lyrics\":\"");
        if (i < 0) throw new Exception("пустой ответ");
        String raw = body.substring(i + 10);
        int end = raw.lastIndexOf('"');
        if (end <= 0) throw new Exception("пустой ответ");
        raw = raw.substring(0, end);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < raw.length(); k++) {
            char ch = raw.charAt(k);
            if (ch == '\\' && k + 1 < raw.length()) {
                char n = raw.charAt(++k);
                if (n == 'n') sb.append('\n');
                else if (n == '"') sb.append('"');
                else if (n == '\\') sb.append('\\');
                else if (n == 'u' && k + 4 < raw.length()) {
                    sb.append((char) Integer.parseInt(raw.substring(k + 1, k + 5), 16));
                    k += 4;
                } else sb.append(n);
            } else sb.append(ch);
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) throw new Exception("текст пустой");
        return out;
    }

    /** Восстанавливает имя исполнителя по названию через suggest-эндпоинт Deezer. */
    public static String suggestArtist(String title) {
        try {
            String u = "https://api.lyrics.ovh/suggest/" + URLEncoder.encode(title, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestProperty("User-Agent", "VibePlayer/1.0");
            c.setConnectTimeout(4000);
            c.setReadTimeout(8000);
            String body = read(c);
            if (c.getResponseCode() != 200) return null;
            int i = body.indexOf("\"artist\"");
            if (i < 0) return null;
            int j = body.indexOf("\"name\":\"", i);
            if (j < 0) return null;
            int e = body.indexOf('"', j + 8);
            if (e < 0) return null;
            return body.substring(j + 8, e);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String read(HttpURLConnection c) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return sb.toString();
        }
    }
}
