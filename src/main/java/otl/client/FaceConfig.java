package otl.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.client.Minecraft;

/** Face Rig preferences, stored under config/Over The Limit. 0=Only me, 1=All, 2=None. */
public final class FaceConfig {
    static int blush=2,glow=2,glances=1,blinking=1,hat=2;
    static float blinkSeconds=3.6f;
    static boolean naturalLight=true;
    private static boolean loaded;
    static String label(int scope){return new String[]{"Only me","All","None"}[scope];}
    static boolean allows(int scope,boolean own){return scope==1||scope==0&&own;}
    private static Path path(){return ConfigDir.parts("face.json").toPath();}
    static void load(){
        if(loaded)return;loaded=true;
        try{
            if(!Files.exists(path()))return;
            JsonObject j=new JsonParser().parse(new String(Files.readAllBytes(path()),StandardCharsets.UTF_8)).getAsJsonObject();
            blush=get(j,"blush",2);glow=get(j,"glowing_eyes",2);glances=get(j,"glances",1);blinking=get(j,"blinking",1);hat=get(j,"hat",2);
            if(j.has("blink_seconds")){float v=j.get("blink_seconds").getAsFloat();if(!Float.isNaN(v)&&!Float.isInfinite(v))blinkSeconds=Math.max(1.5f,Math.min(8f,v));}
            if(j.has("world_lighting"))naturalLight=!j.get("world_lighting").getAsString().trim().equalsIgnoreCase("fullbright");
        }catch(Exception e){System.err.println("OTL: could not load face settings: "+e.getMessage());}
    }
    private static int get(JsonObject j,String key,int fallback){
        try{return j.has(key)?Math.max(0,Math.min(2,j.get(key).getAsInt())):fallback;}catch(Exception e){return fallback;}
    }
    static void save(){
        try{
            JsonObject j=new JsonObject();
            j.addProperty("blush",blush);j.addProperty("glowing_eyes",glow);j.addProperty("glances",glances);j.addProperty("blinking",blinking);j.addProperty("hat",hat);
            j.addProperty("blink_seconds",(double)Math.round(blinkSeconds*10)/10);
            j.addProperty("world_lighting",naturalLight?"natural":"fullbright");
            AtomicFiles.write(path(),new GsonBuilder().setPrettyPrinting().create().toJson(j).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){System.err.println("OTL: could not save face settings: "+e.getMessage());}
    }
    public static void reset(){blush=2;glow=2;glances=1;blinking=1;hat=2;blinkSeconds=3.6f;naturalLight=true;save();}
}
