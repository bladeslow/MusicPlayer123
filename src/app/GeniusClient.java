package app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches song lyrics from genius.com (API token is bundled in the app). */
public final class GeniusClient {

    /** Встроенный API-токен Genius — общий для всех копий приложения. */
    public static final String TOKEN = "-d2jyBM9vXrW-wPmQ9K0xsFaw0AJXM8mFjGqSInHqvhNPB0P6933F10ok0bQ3K0X";

    public static class Hit {
        public String title, artist, url;
    }

    /** Вырезает все <div>, содержащие marker, с учётом вложенности тегов. */
    private static void extractContainers(String html, String marker, StringBuilder sb) {
        int from = 0;
        while (true) {
            int i = html.indexOf(marker, from);
            if (i < 0) break;
            int open = html.lastIndexOf("<div", i);
            if (open < 0) { from = i + marker.length(); continue; }
            int gt = html.indexOf('>', open);
            if (gt < 0) break;
            int depth = 1, k = gt + 1, end = -1;
            while (k < html.length()) {
                int lt = html.indexOf('<', k);
                if (lt < 0) break;
                if (html.startsWith("<div", lt)) { depth++; k = lt + 4; }
                else if (html.startsWith("</div>", lt)) {
                    depth--;
                    if (depth == 0) { end = lt; break; }
                    k = lt + 6;
                } else k = lt + 1;
            }
            if (end < 0) break;
            String text = stripTags(html.substring(gt + 1, end)).trim();
            if (!text.isEmpty()) sb.append(text).append('\n');
            from = end + 6;
        }
    }

    public static List<Hit> search(String token, String query) throws Exception {
        String u = "https://api.genius.com/search?q=" +
                URLEncoder.encode(query, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        String body = read(c);
        if (c.getResponseCode() != 200)
            throw new Exception("Genius API ответил кодом " + c.getResponseCode());
        List<Hit> hits = new ArrayList<>();
        for (String m : findAll(body, "\"url\"\\s*:\\s*\"(https://genius\\.com/[^\"]+)\"")) {
            // matched inside response hits below
        }
        Pattern p = Pattern.compile(
            "\"full_title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\".*?\"url\"\\s*:\\s*\"(https://genius\\.com/(?:lyrics/)?[^\"]+)\"",
            Pattern.DOTALL);
        Matcher mm = p.matcher(body);
        while (mm.find()) {
            Hit h = new Hit();
            h.title = unescape(mm.group(1));
            h.url = mm.group(2);
            hits.add(h);
        }
        // skip translated versions (Spanish/English translations) - keep originals
        List<Hit> original = new ArrayList<>();
        for (Hit h : hits) {
            String hu = h.url.toLowerCase();
            String ht = h.title.toLowerCase();
            if (hu.contains("traduccion") || hu.contains("translation")
                    || hu.contains("traducao") || hu.contains("tradução")
                    || hu.contains("traducao-") || hu.contains("portuguese-translation")
                    || ht.contains("traducci") || ht.contains("translat")
                    || ht.contains("traduç") || ht.contains("traduz")
                    || ht.contains(" перевод")) continue;
            original.add(h);
        }
        return original.isEmpty() ? hits : original;
    }

    public static String fetchLyrics(String pageUrl) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(pageUrl).openConnection();
        c.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        String html = read(c);
        // Genius puts lyrics into divs with class starting with Lyrics__Container
        StringBuilder sb = new StringBuilder();
        // современные страницы: контейнеры с вложенными div — вырезаем с балансировкой тегов
        extractContainers(html, "Lyrics__Container", sb);
        if (sb.length() == 0) extractContainers(html, "data-lyrics-container=\"true\"", sb);
        if (sb.length() == 0) {
            String[] patterns = {
                "<div[^>]*class=\"[^\"]*lyrics?[^\"]*\"[^>]*>(.*?)</div>"
            };
            for (String pat : patterns) {
                Matcher m = Pattern.compile(pat, Pattern.DOTALL).matcher(html);
                while (m.find()) {
                    String chunk = stripTags(m.group(1)).trim();
                    if (chunk.isEmpty()) continue;
                    sb.append(chunk).append('\n');
                }
            }
        }
        String out = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        // remove Genius service header: "12 Contributors" and "Song Title Lyrics"
        out = out.replaceFirst("^\\s*\\d+\\s+Contributors\\s*", "");
        int firstNl = out.indexOf('\n');
        if (firstNl > 0 && out.substring(0, firstNl).trim().endsWith("Lyrics"))
            out = out.substring(firstNl + 1).trim();
        if (out.isEmpty()) throw new Exception("Текст не найден на странице Genius");
        return out;
    }

    private static String stripTags(String html) {
        String s = html.replaceAll("<br\\s*/?>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        return unescape(s);
    }

    private static String unescape(String s) {
        // numeric entities first: &#x27; &#39; ...
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("&#(?:x([0-9a-fA-F]+)|(\\d+));").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int cp = m.group(1) != null ? Integer.parseInt(m.group(1), 16)
                                        : Integer.parseInt(m.group(2));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    String.valueOf((char) cp)));
        }
        m.appendTail(sb);
        s = sb.toString();
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&nbsp;", " ").replace("	", " ");
    }

    private static List<String> findAll(String body, String regex) {
        return new ArrayList<>();
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
