package otl.client;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares the local skateboard over {@link OtlService} so other OTL players see it.
 *
 * Motion states and short animation cues travel here; positions still come from Minecraft.
 */
final class BoardSync {
    private static final String EVENT="board";
    private static final long REPEAT_MS=2000;
    /** No word for this long and the rider is gone: fade the board out rather than freeze it. */
    private static final long EXPIRE_MS=7000;
    private static final int GROUNDED=1,CROUCHING=2,GRINDING=4,FALL_SHIFT=3,MAX_LEVEL=7;
    private static final int MAX_RIDERS=256;

    private static final Map<String,Remote> REMOTE=new ConcurrentHashMap<>();
    private static boolean subscribed,lastBoard;
    private static long lastSent;
    private static int lastPose=-1;

    private static final class Remote {
        boolean board,push;
        int pose=-1,trick=-1,landing;
        volatile long seen;
    }

    /** A single coherent network snapshot; short cues are consumed only once. */
    static final class Ride {
        final boolean board,push;
        final int pose,trick,landing;

        Ride(Remote remote){
            board=remote.board;pose=remote.pose;trick=remote.trick;push=remote.push;landing=remote.landing;
        }
    }

    private BoardSync(){}

    static boolean enabled(){
        UtilitySettings.load();
        return UtilitySettings.playerAnimation&&UtilitySettings.boardShare&&OtlService.available();
    }

    /** Once per client tick, while a world is loaded. */
    static void tick(String name,boolean board,int pose,int trick,boolean push,int landing){
        if(!enabled()){
            reset();
            return;
        }
        subscribe();
        OtlService.start();
        long now=System.currentTimeMillis();
        for(Map.Entry<String,Remote> entry:REMOTE.entrySet())
            if(now-entry.getValue().seen>EXPIRE_MS)REMOTE.remove(entry.getKey());
        if(OtlService.status()!=OtlService.ONLINE){lastBoard=false;lastPose=-1;lastSent=0;return;}
        if(board==lastBoard&&(!board||pose==lastPose&&trick<0&&!push&&landing==0&&now-lastSent<REPEAT_MS))return;
        lastBoard=board;lastPose=pose;lastSent=now;
        OtlService.send(EVENT,packet(name,board,pose,trick,push,landing));
    }

    static boolean riding(String name){
        Remote remote=REMOTE.get(name);
        if(remote==null)return false;
        synchronized(remote){return remote.board;}
    }

    static Ride take(String name){
        Remote remote=REMOTE.get(name);
        if(remote==null)return null;
        synchronized(remote){
            Ride ride=new Ride(remote);
            remote.trick=-1;remote.push=false;remote.landing=0;
            return ride;
        }
    }

    static JsonObject packet(String name,boolean board,int pose,int trick,boolean push,int landing){
        JsonObject payload=new JsonObject();
        payload.addProperty("n",name);
        payload.addProperty("b",board);
        if(board){
            payload.addProperty("m",pose);
            if(trick>=0)payload.addProperty("k",trick);
            if(push)payload.addProperty("p",true);
            if(landing>0)payload.addProperty("l",landing);
        }
        return payload;
    }

    static int pose(boolean grounded,boolean crouching,boolean grinding,float fall){
        return (grounded?GROUNDED:0)|(crouching?CROUCHING:0)|(grinding?GRINDING:0)|(level(fall)<<FALL_SHIFT);
    }

    static int level(float amount){return Math.round(Math.max(0f,Math.min(1f,amount))*MAX_LEVEL);}
    static float amount(int level){return level/(float)MAX_LEVEL;}
    static boolean grounded(int pose){return (pose&GROUNDED)!=0;}
    static boolean crouching(int pose){return (pose&CROUCHING)!=0;}
    static boolean grinding(int pose){return (pose&GRINDING)!=0;}
    static float falling(int pose){return amount(pose>>FALL_SHIFT);}

    /** World unload: forget every rider. The shared connection is closed by {@link OtlService}. */
    static void reset(){
        REMOTE.clear();
        lastBoard=false;
        lastPose=-1;
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
        if(!name.matches("[A-Za-z0-9_]{1,16}")||!payload.has("b"))return;
        boolean board=bool(payload,"b");
        if(!board){REMOTE.remove(name);return;}
        Remote remote=REMOTE.get(name);
        if(remote==null){
            if(REMOTE.size()>=MAX_RIDERS)return;
            remote=REMOTE.computeIfAbsent(name,n->new Remote());
        }
        synchronized(remote){
            remote.board=true;
            int pose=integer(payload,"m");
            remote.pose=pose>=0&&pose<=((MAX_LEVEL<<FALL_SHIFT)|7)?pose:-1;
            int trick=integer(payload,"k");
            if(trick>=0&&trick<=2)remote.trick=trick;
            if(bool(payload,"p"))remote.push=true;
            int landing=integer(payload,"l");
            if(landing>0&&landing<=MAX_LEVEL)remote.landing=landing;
            remote.seen=System.currentTimeMillis();
        }
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
            return payload.has(key)&&payload.get(key).isJsonPrimitive()
                &&payload.getAsJsonPrimitive(key).isBoolean()&&payload.get(key).getAsBoolean();
        }catch(RuntimeException bad){
            return false;
        }
    }

    private static int integer(JsonObject payload,String key){
        try{
            return payload.has(key)&&payload.get(key).isJsonPrimitive()&&payload.getAsJsonPrimitive(key).isNumber()
                ?Integer.parseInt(payload.get(key).getAsString()):-1;
        }catch(RuntimeException bad){
            return -1;
        }
    }
}
