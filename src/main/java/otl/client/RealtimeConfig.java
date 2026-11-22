package otl.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Where the Supabase project lives. The anon (publishable) key is public by design and is meant to
 * ship in the jar, so the values belong here. {@code realtime.json} next to settings.json overrides
 * them, which points a build at a project without a rebuild.
 *
 * <p>Empty values mean board sharing is off: nothing connects, and the mod behaves exactly as it
 * did before this feature existed.
 */
final class RealtimeConfig {
    private static final String URL="https://chonoaqagvkxcrqvryhv.supabase.co";
    private static final String KEY="sb_publishable_TcvU1LLltGPPBGsdYW997A_gYLUhiDw";

    private static boolean loaded;
    private static String url="",key="";

    private RealtimeConfig(){}

    static synchronized void load(){
        if(loaded)return;
        loaded=true;
        url=URL;
        key=KEY;
        try{
            java.nio.file.Path file=ConfigDir.parts("realtime.json").toPath();
            if(Files.exists(file)){
                JsonObject j=new JsonParser().parse(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).getAsJsonObject();
                if(j.has("url"))url=j.get("url").getAsString().trim();
                if(j.has("key"))key=j.get("key").getAsString().trim();
            }
        }catch(Exception unreadable){
            System.err.println("OTL: could not read realtime.json: "+unreadable.getMessage());
        }
    }

    static String url(){load();return url;}
    static String key(){load();return key;}
    static boolean configured(){load();return !url.isEmpty()&&!key.isEmpty();}
}
