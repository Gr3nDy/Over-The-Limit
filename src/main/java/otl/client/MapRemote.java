package otl.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bundled assets are preferred; both public repositories provide the fallback. */
final class MapRemote {
    static final String[] SOURCES = {"PublicMaps/main", "CommunityMaps/master"};
    private static final long TTL = 6L * 60 * 60 * 1000;
    private static final Map<String,List<String>> dirs = new HashMap<>();
    private static final Map<String,Long> dirsAt = new HashMap<>();
    private static final Map<String,String> includes=new ConcurrentHashMap<>();
    private static final Set<String> previews=Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private static final Map<String,Long> previewRetry=new ConcurrentHashMap<>();
    private static final AtomicBoolean catalogLoading=new AtomicBoolean();
    static volatile List<String> catalog=Collections.emptyList();
    static volatile String catalogStatus="Loading both repositories";
    private static volatile long catalogRetry;
    private static final ExecutorService worker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"OTL Map Lookup");t.setDaemon(true);return t;});
    private static final ExecutorService imageWorker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"OTL Map Images");t.setDaemon(true);return t;});

    static File cacheDir() {
        return MinecraftAccess.get().mcDataDir.toPath().resolve("config/overcast_util/map_cache").toFile();
    }

    /** Network work only; never call from the render thread. Reports null when nothing matches. */
    static void lookup(String query, Consumer<MapInfo> done) {
        File root = cacheDir();
        try {
            worker.execute(() -> {
                MapInfo found = null;
                try { found = resolve(query, root); }
                catch (Exception e) { System.err.println("OTL: map lookup failed: " + e.getMessage()); }
                done.accept(found);
            });
        } catch (RejectedExecutionException e) {
            System.err.println("OTL: too many pending map lookups");
            done.accept(null);
        }
    }

    static MapInfo resolve(String query, File root) throws Exception {
        MapInfo local=MapInfo.findLocal(query);
        if(local!=null&&local.exact(query))return MapInfo.read(local.dir,true,null,id->include(id,root));
        List<String> candidates = new ArrayList<>();
        for (String source : SOURCES) {
            try { candidates.addAll(directories(source, root)); }
            catch (Exception e) { System.err.println("OTL: " + source + " index unavailable: " + e.getMessage()); }
        }
        String hit = findDirectory(query, candidates);
        if(local!=null&&(hit==null||(!hit.equals(query)&&!MapInfo.norm(hit.substring(hit.lastIndexOf('/')+1)).equals(MapInfo.norm(query)))))
            return MapInfo.read(local.dir,true,null,id->include(id,root));
        if (hit == null) return null;
        File folder = new File(root, hit);
        File xml = new File(folder, "map.xml");
        if (!xml.isFile()) download(rawUrl(hit, "map.xml"), xml);
        File png = new File(folder, "map.png");
        if (!png.isFile()) try { download(rawUrl(hit, "map.png"), png); }
        catch (Exception e) { System.err.println("OTL: no preview for " + hit + ": " + e.getMessage()); }
        return MapInfo.read(hit, false, folder,id->include(id,root));
    }

    static String cachedInclude(String id){return includes.get(id);}

    private static String include(String id,File root){
        String ready=includes.get(id);if(ready!=null)return ready;
        if(!id.matches("[A-Za-z0-9_-]+"))return null;
        File file=new File(root,"includes/"+id+".xml");
        try{
            if(!file.isFile())download(rawUrl("PublicMaps/main/includes",id+".xml"),file);
            String xml=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            includes.put(id,xml);return xml;
        }catch(Exception e){System.err.println("OTL: shared metadata include "+id+" unavailable: "+e.getMessage());return null;}
    }

    static void requestCatalog(){
        if(System.currentTimeMillis()<catalogRetry||!catalogLoading.compareAndSet(false,true))return;
        File root=cacheDir();
        try{imageWorker.execute(()->{
            List<String> all=new ArrayList<>();int failed=0;
            for(String source:SOURCES)try{all.addAll(directories(source,root));}catch(Exception e){failed++;System.err.println("OTL: catalog "+source+": "+e.getMessage());}
            catalog=Collections.unmodifiableList(all);
            catalogStatus=failed==0?"CommunityMaps + PublicMaps":"Repository unavailable; showing cached/bundled maps";
            catalogRetry=System.currentTimeMillis()+(failed==0?TTL:60000);catalogLoading.set(false);
        });}catch(RejectedExecutionException e){catalogLoading.set(false);}
    }

    static File preview(String dir){
        if(!validDirectory(dir))return null;
        File file=new File(cacheDir(),dir+"/map.png");
        if(file.isFile())return file;
        if(System.currentTimeMillis()<previewRetry.getOrDefault(dir,0L)||!previews.add(dir))return null;
        try{imageWorker.execute(()->{
            try{download(rawUrl(dir,"map.png"),file);}
            catch(Exception e){previewRetry.put(dir,System.currentTimeMillis()+60000);}
            finally{previews.remove(dir);}
        });}catch(RejectedExecutionException e){previews.remove(dir);}
        return null;
    }

    static String findDirectory(String query, List<String> candidates) {
        String q = MapInfo.norm(query);
        if (q.length() < 2) return null;
        String hit = null;
        int best = Integer.MAX_VALUE;
        for (String dir : candidates) {
            String slug = MapInfo.norm(dir.substring(dir.lastIndexOf('/') + 1));
            if (dir.equals(query) || slug.equals(q)) return dir;
            if (slug.contains(q) && slug.length() < best) { best = slug.length(); hit = dir; }
        }
        return hit;
    }

    static String rawUrl(String dir, String file) throws Exception {
        if (!validDirectory(dir)) throw new IllegalArgumentException("Invalid map path");
        return "https://raw.githubusercontent.com" + new URI(null, null, "/OvercastCommunity/" + dir + "/" + file, null).toASCIIString();
    }

    private static boolean validDirectory(String dir) {
        if (dir.indexOf('\\')>=0 || dir.indexOf(':')>=0 || dir.startsWith("/") || dir.matches(".*[\\r\\n\\t].*")) return false;
        for(String part:dir.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;
        for(String source:SOURCES)if(dir.startsWith(source+"/"))return true;
        return false;
    }

    private static synchronized List<String> directories(String source, File root) throws Exception {
        long now = System.currentTimeMillis();
        String key = root.getAbsolutePath() + "|" + source;
        if (dirs.containsKey(key) && now - dirsAt.get(key) < TTL) return dirs.get(key);
        File cache = new File(root, source.substring(0,source.indexOf('/')) + "-tree.txt");
        List<String> parsed = null;
        if (cache.isFile() && now - cache.lastModified() < TTL) parsed = readLines(cache);
        if (parsed == null || parsed.isEmpty()) {
            String[] parts = source.split("/");
            String url = "https://api.github.com/repos/OvercastCommunity/" + parts[0] + "/git/trees/" + parts[1] + "?recursive=1";
            parsed = parseTree(source, downloadText(url));
            AtomicFiles.write(cache.toPath(), String.join("\n",parsed).getBytes(StandardCharsets.UTF_8));
        }
        dirs.put(key,parsed);
        dirsAt.put(key,now);
        return parsed;
    }

    static List<String> parseTree(String source, String json) {
        List<String> out = new ArrayList<>();
        com.google.gson.JsonObject response = new JsonParser().parse(json).getAsJsonObject();
        if(response.has("truncated")&&response.get("truncated").getAsBoolean())throw new IllegalArgumentException("Truncated map index");
        JsonArray tree = response.getAsJsonArray("tree");
        for (JsonElement element : tree) {
            String path = element.getAsJsonObject().get("path").getAsString();
            if (!path.endsWith("/map.xml")) continue;
            String dir = path.substring(0, path.length() - 8);
            if (dir.equals("includes") || dir.startsWith("includes/") || dir.endsWith("/includes") || dir.contains("/includes/")) continue;
            if(validDirectory(source+"/"+dir))out.add(source+"/"+dir);
        }
        return out;
    }

    private static String downloadText(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            return new String(readLimited(in,16*1024*1024), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
    }

    private static void download(String url, File target) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            AtomicFiles.write(target.toPath(),readLimited(in,8*1024*1024));
        } finally { c.disconnect(); }
    }

    static byte[] readLimited(InputStream in,int limit) throws java.io.IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[16384];int n;
        while((n=in.read(buf))!=-1){
            if(n>limit-out.size())throw new java.io.IOException("Map download exceeds size limit");
            out.write(buf,0,n);
        }
        return out.toByteArray();
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
            if (validDirectory(line.trim())) out.add(line.trim());
        return out;
    }
}
