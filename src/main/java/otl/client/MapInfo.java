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
    /** Named and anonymous regions from the map's <regions> block, as bounding boxes. */
    final java.util.List<Region> regions;

    /** One map region, flattened to a bounding box; cylinders/circles/spheres use their extents. */
    static final class Region {
        final String name;
        final double[] min,max;
        Region(String name,double[] box){
            this.name=name==null?"":name;
            this.min=java.util.Arrays.copyOfRange(box,0,3);
            this.max=java.util.Arrays.copyOfRange(box,3,6);
        }
    }

    private MapInfo(String name, String version, String objective, String created, String gamemode,
                     String authors, String contributors, String teams, String dir, int players, boolean bundled, File cache, String variants, boolean incomplete, int maxBuildHeight, java.util.Set<String> kitBlocks, java.util.List<Region> regions) {
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
        this.regions=java.util.Collections.unmodifiableList(regions);
    }

    private volatile File pngFile;
    private volatile boolean pngReady;
    ResourceLocation texture() {
        if (bundled) return MapPreview.galleryTexture(dir + "/map.png");
        if (cache == null) return null;
        if (!pngReady) {
            File png = new File(cache, "map.png");
            if (!png.isFile()) return null;
            pngFile = png;
            pngReady = true;
        }
        return MapPreview.fileTexture(dir, pngFile);
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
            List<Region> regions=new ArrayList<>();
            Element regionsEl=metadata.first("regions");
            if(regionsEl!=null)collectRegions(regionsEl,metadata,regions,maxBuildHeight);
            return new MapInfo(nvl(metadata.value("name"),pretty(last(dir))),metadata.value("version"),objective,metadata.value("created"),
                    nvl(metadata.value("gamemode"),gamemode),metadata.people("authors","author"),metadata.people("contributors","contributor"),
                    String.join(", ",labels),dir,players,bundled,cache,String.join(", ",variants),incomplete,maxBuildHeight,kitBlocks,regions);
        }catch(Exception e){System.err.println("OTL: could not read map metadata "+dir+": "+e.getMessage());}
        return new MapInfo(pretty(last(dir)),"","","",gamemode,"","","",dir,0,bundled,cache,"default",true,-1,java.util.Collections.emptySet(),java.util.Collections.emptyList());
    }

    /** Flattens shape children (through union/intersection) into bounding boxes; everything else is skipped. */
    private static void collectRegions(Element container,MapXml meta,List<Region> out,int height){
        for(Element e:MapXml.children(container)){
            String tag=e.getTagName();
            if(tag.equals("union")||tag.equals("intersection"))collectRegions(e,meta,out,height);
            else{
                double[] box=regionBox(e,meta,height);
                if(box!=null&&out.size()<2000)out.add(new Region(meta.expand(e.getAttribute("id")),box));
            }
        }
    }

    /** Bounding box of one shape in world coordinates; null for unrecognized or non-numeric regions. */
    private static double[] regionBox(Element e,MapXml meta,int height){
        // 2D shapes (rectangle/circle) have no y level: span the map's build limit instead of the full world.
        double top=height>0?height+1:256;
        String tag=e.getTagName();
        try{
            switch(tag){
                case "cuboid":{
                    double[] a=p3(meta.expand(e.getAttribute("min"))),b=p3(meta.expand(e.getAttribute("max")));
                    if(a==null||b==null)return null;
                    return new double[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.min(a[2],b[2]),Math.max(a[0],b[0])+1,Math.max(a[1],b[1])+1,Math.max(a[2],b[2])+1};
                }
                case "rectangle":{
                    double[] a=p2(meta.expand(e.getAttribute("min"))),b=p2(meta.expand(e.getAttribute("max")));
                    if(a==null||b==null)return null;
                    return new double[]{Math.min(a[0],b[0]),0,Math.min(a[1],b[1]),Math.max(a[0],b[0])+1,top,Math.max(a[1],b[1])+1};
                }
                case "cylinder":{
                    double[] base=p3(meta.expand(e.getAttribute("base")));
                    if(base==null)return null;
                    double r=Double.parseDouble(meta.expand(e.getAttribute("radius")));
                    double h=Math.max(1,Double.parseDouble(meta.expand(e.getAttribute("height"))));
                    return new double[]{base[0]-r,base[1],base[2]-r,base[0]+r,base[1]+h,base[2]+r};
                }
                case "circle":{
                    double[] c=p2(meta.expand(e.getAttribute("center")));
                    if(c==null)return null;
                    double r=Double.parseDouble(meta.expand(e.getAttribute("radius")));
                    return new double[]{c[0]-r,0,c[1]-r,c[0]+r,top,c[1]+r};
                }
                case "sphere":{
                    double[] o=p3(meta.expand(e.getAttribute("origin")));
                    if(o==null)return null;
                    double r=Double.parseDouble(meta.expand(e.getAttribute("radius")));
                    return new double[]{o[0]-r,o[1]-r,o[2]-r,o[0]+r,o[1]+r,o[2]+r};
                }
                case "block":{
                    double[] b=p3(e.getTextContent().trim());
                    return b==null?null:new double[]{b[0],b[1],b[2],b[0]+1,b[1]+1,b[2]+1};
                }
                case "point":{
                    double[] b=p3(e.getTextContent().trim());
                    return b==null?null:new double[]{b[0]-.5,b[1]-.5,b[2]-.5,b[0]+.5,b[1]+.5,b[2]+.5};
                }
            }
        }catch(NumberFormatException ignored){}
        return null;
    }

    private static double[] p3(String v){
        if(v==null)return null;
        String[] parts=v.trim().split(",");
        try{
            if(parts.length!=3)return null;
            return new double[]{Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim()),Double.parseDouble(parts[2].trim())};
        }catch(NumberFormatException e){return null;}
    }

    private static double[] p2(String v){
        if(v==null)return null;
        String[] parts=v.trim().split(",");
        try{
            if(parts.length!=2)return null;
            return new double[]{Double.parseDouble(parts[0].trim()),Double.parseDouble(parts[1].trim())};
        }catch(NumberFormatException e){return null;}
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
