package otl.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.ResourceLocation;

/** Metadata for one map, read from its map.xml (bundled asset or downloaded copy). */
final class MapInfo {
    final String name, version, objective, created, gamemode, authors, contributors, teams, dir;
    final int players;
    final boolean bundled;
    final File cache;

    private MapInfo(String name, String version, String objective, String created, String gamemode,
                    String authors, String contributors, String teams, String dir, int players, boolean bundled, File cache) {
        this.name = name;
        this.version = version;
        this.objective = objective;
        this.created = created;
        this.gamemode = gamemode;
        this.authors = authors;
        this.contributors = contributors;
        this.teams = teams;
        this.dir = dir;
        this.players = players;
        this.bundled = bundled;
        this.cache = cache;
    }

    ResourceLocation texture() {
        if (bundled) return MapPreview.galleryTexture(dir + "/map.png");
        if (cache == null) return null;
        File png = new File(cache, "map.png");
        return png.isFile() ? MapPreview.fileTexture("map_" + Integer.toHexString(dir.hashCode()), png) : null;
    }

    /** Bundled maps first; the caller falls back to the public repo. */
    static MapInfo findLocal(String query) {
        String q = norm(query);
        if (q.length() < 2) return null;
        String hit = null;
        for (String p : MapPreviewIndex.PATHS) {
            if (!p.endsWith("/map.png")) continue;
            String dir = p.substring(0, p.length() - 8);
            if (norm(last(dir)).equals(q)) { hit = dir; break; }
        }
        if (hit == null && MapPreview.namesReady) {
            int best = Integer.MAX_VALUE;
            for (String p : MapPreviewIndex.PATHS) {
                if (!p.endsWith("/map.png")) continue;
                String dir = p.substring(0, p.length() - 8);
                String name = norm(MapPreview.displayName(dir));
                if (name.isEmpty()) continue;
                if ((name.equals(q) || name.startsWith(q) || name.contains(q)) && name.length() < best) {
                    best = name.length();
                    hit = dir;
                }
            }
        }
        return hit == null ? null : read(hit, true, null);
    }

    static MapInfo read(String dir, boolean bundled, File cache) {
        String xml = null;
        try (InputStream in = bundled
                ? MapInfo.class.getResourceAsStream("/assets/otl/maps/" + dir + "/map.xml")
                : new FileInputStream(new File(cache, "map.xml"))) {
            if (in != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return parse(xml, dir, bundled, cache);
    }

    private static MapInfo parse(String xml, String dir, boolean bundled, File cache) {
        String parent = parent(dir);
        String gamemode = pretty(parent.isEmpty() ? "Other" : last(parent));
        if (xml == null) return new MapInfo(pretty(last(dir)), "", "", "", gamemode, "", "", "", dir, 0, bundled, cache);
        String teams = tag(xml, "teams");
        int players = 0;
        StringBuilder labels = new StringBuilder();
        if (teams != null) {
            Matcher m = Pattern.compile("max=\"(\\d+)\"").matcher(teams);
            while (m.find()) players += Integer.parseInt(m.group(1));
            Matcher team = Pattern.compile("<team\\b([^>]*)>(.*?)</team>", Pattern.DOTALL).matcher(teams);
            while (team.find()) {
                String label = decode(team.group(2).trim());
                String max = attribute(team.group(1), "max");
                if (label.isEmpty()) continue;
                if (labels.length() > 0) labels.append(", ");
                labels.append(label);
                if (!max.isEmpty()) labels.append(' ').append(max);
            }
        }
        return new MapInfo(
                nvl(tag(xml, "name"), pretty(last(dir))),
                nvl(tag(xml, "version"), ""),
                nvl(tag(xml, "objective"), ""),
                nvl(tag(xml, "created"), ""),
                gamemode,
                people(xml, "authors", "author", false),
                people(xml, "contributors", "contributor", true),
                labels.toString(),
                dir, players, bundled, cache);
    }

    /** Names sit in the trailing XML comment (`<author uuid=".."/> <!-- jcane -->`). */
    private static String people(String xml, String section, String element, boolean withRole) {
        String block = tag(xml, section);
        if (block == null) return "";
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("<" + element + "\\b([^>]*?)(?:/>|>(.*?)</" + element + ">)\\s*(?:<!--\\s*(.*?)\\s*-->)?", Pattern.DOTALL).matcher(block);
        while (m.find()) {
            String attrs = m.group(1) == null ? "" : m.group(1);
            String inner = m.group(2) == null ? "" : decode(m.group(2).trim());
            String note = m.group(3) == null ? "" : decode(m.group(3).trim());
            String name = !inner.isEmpty() ? inner : !note.isEmpty() ? note : attribute(attrs, "name");
            if (name.isEmpty()) name = attribute(attrs, "uuid");
            if (name.isEmpty()) continue;
            if (name.length() > 12 && name.indexOf('-') > 0) name = name.substring(0, 8);
            String role = withRole ? attribute(attrs, "contribution") : "";
            if (out.length() > 0) out.append(", ");
            out.append(name);
            if (!role.isEmpty()) out.append(" (").append(role).append(')');
        }
        return out.toString();
    }

    static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + ">(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? decode(m.group(1).trim()) : null;
    }

    private static String attribute(String attrs, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? decode(m.group(1)) : "";
    }

    private static String decode(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String parent(String dir) {
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }

    private static String last(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String pretty(String segment) {
        String s = segment.replace('_', ' ').trim();
        if (s.length() <= 4) return s.toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** Lowercase letters and digits only, so "100 Rooms" == "100_rooms". */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) out.append(c);
        return out.toString();
    }
}
