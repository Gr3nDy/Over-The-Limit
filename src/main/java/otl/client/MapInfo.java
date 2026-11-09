package otl.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import net.minecraft.util.ResourceLocation;

/** Metadata for one map, read from its map.xml (bundled asset or downloaded copy). */
final class MapInfo {
    final String name, version, objective, created, gamemode, authors, contributors, teams, dir, variants;
    final int players;
    final int maxBuildHeight;
    final boolean bundled;
    final File cache;
    final boolean incomplete;
    final java.util.Set<String> kitBlocks;

    private MapInfo(String name, String version, String objective, String created, String gamemode,
                     String authors, String contributors, String teams, String dir, int players, boolean bundled, File cache, String variants, boolean incomplete, int maxBuildHeight, java.util.Set<String> kitBlocks) {
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
        this.variants=variants;this.incomplete=incomplete;
        this.maxBuildHeight=maxBuildHeight;
        this.kitBlocks=java.util.Collections.unmodifiableSet(new java.util.HashSet<>(kitBlocks));
    }

    ResourceLocation texture() {
        if (bundled) return MapPreview.galleryTexture(dir + "/map.png");
        if (cache == null) return null;
        File png = new File(cache, "map.png");
        return png.isFile() ? MapPreview.fileTexture(dir, png) : null;
    }

    boolean exact(String query){
        if(query.startsWith("CommunityMaps/master/"))query=query.substring("CommunityMaps/master/".length());
        return dir.equals(query)||norm(name).equals(norm(query))||norm(last(dir)).equals(norm(query));
    }

    /** Bundled maps first; the caller falls back to the public repo. */
    static MapInfo findLocal(String query) {
        if(query.startsWith("CommunityMaps/master/"))query=query.substring("CommunityMaps/master/".length());
        if(query.startsWith("PublicMaps/"))return null;
        String q = norm(query);
        if (q.length() < 2) return null;
        String hit = null;
        for (String p : MapPreviewIndex.PATHS) {
            if (!p.endsWith("/map.xml")) continue;
            String dir = p.substring(0, p.length() - 8);
            if (dir.equals(query) || norm(last(dir)).equals(q)) { hit = dir; break; }
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
        return read(dir,bundled,cache,MapRemote::cachedInclude);
    }

    static MapInfo read(String dir, boolean bundled, File cache,Function<String,String> includes) {
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
        return parse(xml, dir, bundled, cache,includes);
    }

    static MapInfo parse(String xml, String dir, boolean bundled, File cache,Function<String,String> includes) {
        String parent = parent(dir);
        String gamemode = pretty(parent.isEmpty() ? "Other" : last(parent));
        if(xml!=null)try{
            MapXml metadata=new MapXml(xml,includes);
            int players=0;List<String> labels=new ArrayList<>();
            Element teams=metadata.first("teams");
            if(teams!=null)for(Element team:MapXml.children(teams))if(team.getTagName().equals("team")){
                String max=metadata.expand(team.getAttribute("max"));
                if(max.matches("[0-9]{1,5}"))players+=Integer.parseInt(max);
                labels.add(metadata.expand(team.getTextContent().trim())+(max.isEmpty()?"":" "+max));
            }
            Element ffa=metadata.first("players");
            if(ffa!=null){String max=metadata.expand(ffa.getAttribute("max"));if(max.matches("[0-9]{1,5}"))players=Integer.parseInt(max);}
            List<String> variants=new ArrayList<>();variants.add("default");
            for(Element e:MapXml.children(metadata.root))if(e.getTagName().equals("variant")&&!e.getAttribute("id").isEmpty())variants.add(e.getAttribute("id"));
            String objective=metadata.value("objective");
            boolean incomplete=metadata.incomplete&&(objective.isEmpty()||players==0||teams==null||objective.contains("server-defined"));
            String height=metadata.value("maxbuildheight");Element limit=metadata.first("maxbuildheight");
            int maxBuildHeight=height.matches("[0-9]{1,3}")&&limit!=null&&!limit.hasAttribute("place")?Integer.parseInt(height):-1;
            if(maxBuildHeight<1||maxBuildHeight>255)maxBuildHeight=-1;
            java.util.Set<String> kitBlocks=new java.util.HashSet<>();
            NodeList items=metadata.root.getElementsByTagName("item");
            for(int i=0;i<items.getLength();i++){
                Element item=(Element)items.item(i);String material=item.getAttribute("material");
                if(!material.isEmpty())kitBlocks.add(kitMaterial(material));
            }
            return new MapInfo(nvl(metadata.value("name"),pretty(last(dir))),metadata.value("version"),objective,metadata.value("created"),
                    nvl(metadata.value("gamemode"),gamemode),metadata.people("authors","author"),metadata.people("contributors","contributor"),
                    String.join(", ",labels),dir,players,bundled,cache,String.join(", ",variants),incomplete,maxBuildHeight,kitBlocks);
        }catch(Exception e){System.err.println("OTL: could not read map metadata "+dir+": "+e.getMessage());}
        return new MapInfo(pretty(last(dir)),"","","",gamemode,"","","",dir,0,bundled,cache,"default",true,-1,java.util.Collections.emptySet());
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

    static String kitMaterial(String value){
        String s=value.toLowerCase(Locale.ROOT).trim();
        int colon=s.indexOf(':');if(colon>=0)s=s.substring(0,colon);
        return norm(s.replace(' ','_').replace('-', '_'));
    }
}
