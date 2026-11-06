package otl.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Looks up maps that are not bundled, straight from OvercastCommunity/PublicMaps. */
final class MapRemote {
    private static final String RAW = "https://raw.githubusercontent.com/OvercastCommunity/PublicMaps/main/";
    private static final String TREE = "https://api.github.com/repos/OvercastCommunity/PublicMaps/git/trees/main?recursive=1";
    private static final long TTL = 6L * 60 * 60 * 1000;
    private static volatile List<String> dirs;
    private static volatile long dirsAt;

    static File cacheDir() {
        return MinecraftAccess.get().mcDataDir.toPath().resolve("config/overcast_util/map_cache").toFile();
    }

    /** Network work only; never call from the render thread. Reports null when nothing matches. */
    static void lookup(String query, Consumer<MapInfo> done) {
        Thread worker = new Thread(() -> {
            MapInfo found = null;
            try { found = resolve(query); } catch (Exception e) { System.err.println("OTL: map lookup failed: " + e); }
            done.accept(found);
        }, "OTL Map Lookup");
        worker.setDaemon(true);
        worker.start();
    }

    private static MapInfo resolve(String query) throws Exception {
        String q = MapInfo.norm(query);
        if (q.length() < 2) return null;
        String hit = null;
        int best = Integer.MAX_VALUE;
        for (String dir : directories()) {
            String slug = MapInfo.norm(dir.substring(dir.lastIndexOf('/') + 1));
            if (slug.equals(q)) { hit = dir; break; }
            if (slug.contains(q) && slug.length() < best) { best = slug.length(); hit = dir; }
        }
        if (hit == null) return null;
        File folder = new File(cacheDir(), hit.replace('/', '_'));
        if (!folder.isDirectory() && !folder.mkdirs()) return null;
        File xml = new File(folder, "map.xml");
        if (!xml.isFile()) download(RAW + hit + "/map.xml", xml);
        File png = new File(folder, "map.png");
        if (!png.isFile()) try { download(RAW + hit + "/map.png", png); } catch (Exception ignored) {}
        return MapInfo.read(hit, false, folder);
    }

    private static List<String> directories() throws Exception {
        long now = System.currentTimeMillis();
        if (dirs != null && now - dirsAt < TTL) return dirs;
        File cache = new File(cacheDir(), "tree.txt");
        List<String> parsed = null;
        if (cache.isFile() && now - cache.lastModified() < TTL) parsed = readLines(cache);
        if (parsed == null || parsed.isEmpty()) {
            parsed = parseTree(downloadText(TREE));
            writeText(cache, join(parsed));
        }
        dirs = parsed;
        dirsAt = now;
        return parsed;
    }

    private static List<String> parseTree(String json) {
        List<String> out = new ArrayList<>();
        JsonArray tree = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("tree");
        for (JsonElement element : tree) {
            String path = element.getAsJsonObject().get("path").getAsString();
            if (!path.endsWith("/map.xml")) continue;
            String dir = path.substring(0, path.length() - 8);
            if (dir.startsWith("includes/") || dir.contains("/includes/")) continue;
            out.add(dir);
        }
        return out;
    }

    private static String downloadText(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File target) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            target.getParentFile().mkdirs();
            File tmp = new File(target.getPath() + ".tmp");
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OTL-Mod/1.8.9");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        return c;
    }

    private static List<String> readLines(File file) throws Exception {
        List<String> out = new ArrayList<>();
        for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n"))
            if (!line.trim().isEmpty()) out.add(line.trim());
        return out;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String v : values) out.append(v).append('\n');
        return out.toString();
    }

    private static void writeText(File file, String text) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
