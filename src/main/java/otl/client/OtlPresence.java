package otl.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who else has OTL running in this world.
 *
 * <p>Every client announces its own name on join and every few seconds after, and forgets a name
 * it has not heard from. A name only counts when the game server also lists that player in the
 * world, so a forged message cannot invent a person: it can only name someone who is already there.
 *
 * <p>That check is the whole trust model. Nothing here is stored, and nothing is sent but a name.
 */
final class OtlPresence {
    private static final String EVENT="here";
    private static final long ANNOUNCE_MS=10000;
    /** Three missed announcements, so one network hiccup does not read as "they left". */
    private static final long EXPIRE_MS=35000;
    /** A flood of invented names must not grow the map without bound. */
    private static final int MAX_NAMES=200;

    private static final Map<String,Long> SEEN=new ConcurrentHashMap<>();
    private static boolean subscribed;
    private static long announced;

    private OtlPresence(){}

    static boolean enabled(){
        UtilitySettings.load();
        return UtilitySettings.boardShare&&OtlService.available();
    }

    /** Once per client tick, while a world is loaded. */
    static void tick(){
        if(!enabled()){
            SEEN.clear();
            return;
        }
        subscribe();
        OtlService.start();
        long now=System.currentTimeMillis();
        for(Map.Entry<String,Long> entry:SEEN.entrySet())
            if(now-entry.getValue()>EXPIRE_MS)SEEN.remove(entry.getKey());
        if(now-announced<ANNOUNCE_MS)return;
        announced=now;
        announce();
    }

    /** Other OTL users standing in this world right now, sorted so the list never reorders. */
    static List<String> here(){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null||mc.theWorld==null)return Collections.emptyList();
        List<String> names=new ArrayList<>();
        String self=mc.thePlayer==null?"":mc.thePlayer.getName();
        for(EntityPlayer player:mc.theWorld.playerEntities){
            String name=player.getName();
            if(!name.equals(self)&&SEEN.containsKey(name))names.add(name);
        }
        Collections.sort(names,String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static void reset(){
        SEEN.clear();
        announced=0;
    }

    /** How many names are held, including ones for players who are not here. Test hook. */
    static int known(){return SEEN.size();}

    private static void subscribe(){
        if(subscribed)return;
        subscribed=true;
        OtlService.subscribe(OtlPresence::receive);
    }

    private static void announce(){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null||mc.thePlayer==null)return;
        JsonObject payload=new JsonObject();
        payload.addProperty("n",mc.thePlayer.getName());
        OtlService.send(EVENT,payload);
    }

    static void receive(String event,JsonObject payload){
        if(!EVENT.equals(event))return;
        String name=name(payload);
        if(name.isEmpty())return;
        if(SEEN.size()>=MAX_NAMES&&!SEEN.containsKey(name))return;
        SEEN.put(name,System.currentTimeMillis());
    }

    private static String name(JsonObject payload){
        try{
            if(!payload.has("n")||!payload.get("n").isJsonPrimitive())return "";
            String name=payload.get("n").getAsString();
            return plausible(name)?name:"";
        }catch(RuntimeException bad){
            return "";
        }
    }

    /** A name that could be a real account: Minecraft's own alphabet, and no longer than one. */
    static boolean plausible(String name){
        if(name==null||name.isEmpty()||name.length()>16)return false;
        for(int i=0;i<name.length();i++){
            char c=name.charAt(i);
            boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_';
            if(!ok)return false;
        }
        return true;
    }
}
