package otl.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares the local skateboard over {@link OtlService} so other OTL players see it.
 *
 * <p>Only two facts travel: whether a board is out, and that an ollie just started. Where the
 * board sits and which way it points are already known to every client from the server, so a
 * remote board is drawn by exactly the same code as the local one. Board state is repeated every
 * couple of seconds because broadcast has no memory: someone who joins mid-match still sees a
 * rider instead of an empty world.
 *
 * <p>This is a cosmetic link, not a trust boundary: a name in the payload is only ever matched
 * against a player the server already sent, and a bad payload can at worst draw a board.
 */
final class BoardSync {
    private static final String EVENT="board";
    private static final long REPEAT_MS=2000;
    /** No word for this long and the rider is gone: fade the board out rather than freeze it. */
    private static final long EXPIRE_MS=7000;

    private static final Map<String,Remote> REMOTE=new ConcurrentHashMap<>();
    private static boolean subscribed,lastBoard;
    private static long lastSent;

    private static final class Remote {
        volatile boolean board;
        /** Ollie waiting to be applied, or -1. Read once by the render tick. */
        volatile int trick=-1;
        volatile long seen;
    }

    private BoardSync(){}

    static boolean enabled(){
        UtilitySettings.load();
        return UtilitySettings.playerAnimation&&UtilitySettings.boardShare&&OtlService.available();
    }

    /** Once per client tick, while a world is loaded. */
    static void tick(){
        if(!enabled()){
            REMOTE.clear();
            return;
        }
        subscribe();
        OtlService.start();
        long now=System.currentTimeMillis();
        for(Map.Entry<String,Remote> entry:REMOTE.entrySet())
            if(now-entry.getValue().seen>EXPIRE_MS)REMOTE.remove(entry.getKey());
        publish(now);
    }

    static boolean riding(String name){
        Remote remote=REMOTE.get(name);
        return remote!=null&&remote.board;
    }

    /** The ollie this player started, delivered once; -1 when there is none. */
    static int takeTrick(String name){
        Remote remote=REMOTE.get(name);
        if(remote==null||remote.trick<0)return -1;
        int trick=remote.trick;
        remote.trick=-1;
        return trick;
    }

    /** The local player's own ollie, sent the moment it starts. */
    static void trick(int index){
        if(index<0)return;
        JsonObject payload=new JsonObject();
        payload.addProperty("n",localName());
        payload.addProperty("b",true);
        payload.addProperty("k",index);
        OtlService.send(EVENT,payload);
    }

    /** World unload: forget every rider. The shared connection is closed by {@link OtlService}. */
    static void reset(){
        REMOTE.clear();
        lastBoard=false;
        lastSent=0;
    }

    private static void subscribe(){
        if(subscribed)return;
        subscribed=true;
        OtlService.subscribe(BoardSync::receive);
    }

    /** One rider's update as it arrives; package-visible so the state machine is testable. */
    static void receive(String event,JsonObject payload){
        if(EVENT.equals(event))receive(payload);
    }

    static void receive(JsonObject payload){
        String name=string(payload,"n");
        if(name.isEmpty())return;
        Remote remote=REMOTE.computeIfAbsent(name,n->new Remote());
        remote.board=bool(payload,"b");
        int trick=integer(payload,"k");
        if(trick>=0)remote.trick=trick;
        remote.seen=System.currentTimeMillis();
    }

    private static void publish(long now){
        boolean board=PlayerAnim.localWantsBoard();
        if(board==lastBoard&&(!board||now-lastSent<REPEAT_MS))return;
        lastBoard=board;
        lastSent=now;
        JsonObject payload=new JsonObject();
        payload.addProperty("n",localName());
        payload.addProperty("b",board);
        OtlService.send(EVENT,payload);
    }

    private static String localName(){
        Minecraft mc=MinecraftAccess.get();
        return mc==null||mc.thePlayer==null?"":mc.thePlayer.getName();
    }

    private static String string(JsonObject payload,String key){
        try{
            return payload.has(key)&&payload.get(key).isJsonPrimitive()?payload.get(key).getAsString():"";
        }catch(RuntimeException bad){
            return "";
        }
    }

    private static boolean bool(JsonObject payload,String key){
        try{
            return payload.has(key)&&payload.get(key).isJsonPrimitive()&&payload.get(key).getAsBoolean();
        }catch(RuntimeException bad){
            return false;
        }
    }

    private static int integer(JsonObject payload,String key){
        try{
            return payload.has(key)&&payload.get(key).isJsonPrimitive()?payload.get(key).getAsInt():-1;
        }catch(RuntimeException bad){
            return -1;
        }
    }
}
