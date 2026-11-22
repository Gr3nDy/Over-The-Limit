package otl.client;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The OTL service: one Supabase Realtime connection shared by every networked feature.
 *
 * <p>Features subscribe by event name and ignore what is not theirs, so adding a second one costs
 * a handler and never a second socket. The connection is dialled by whichever feature needs it and
 * closed when the world unloads; nothing here runs on the game thread.
 *
 * <p>{@link #status()} is what the module menu's light shows. It deliberately distinguishes
 * "nothing running" from "tried and failed", because a dead link and a disabled feature look the
 * same to a user and must not be mistaken for each other.
 */
final class OtlService {
    interface Handler {
        void onBroadcast(String event,JsonObject payload);
    }

    static final int OFF=0,CONNECTING=1,ONLINE=2,OFFLINE=3;

    private static final List<Handler> HANDLERS=new CopyOnWriteArrayList<>();
    private static Realtime link;
    /** Written by the network threads, read by the game thread, so both are volatile. */
    private static volatile boolean online,failed;

    private OtlService(){}

    /** True when this build knows which project to talk to. */
    static boolean available(){return RealtimeConfig.configured();}

    static void subscribe(Handler handler){
        if(!HANDLERS.contains(handler))HANDLERS.add(handler);
    }

    /** Idempotent: a feature may ask every tick without opening a second connection. */
    static void start(){
        if(link!=null||!available())return;
        link=Realtime.open(RealtimeConfig.url(),RealtimeConfig.key(),CHANNEL,new Realtime.Listener(){
            @Override public void onBroadcast(String event,JsonObject payload){
                for(Handler handler:HANDLERS)handler.onBroadcast(event,payload);
            }
            @Override public void onStatus(boolean joined,String detail){
                online=joined;
                failed=!joined;
            }
        });
    }

    static void stop(){
        Realtime open=link;
        link=null;
        online=false;
        failed=false;
        if(open!=null)open.close();
    }

    /** Queued, never blocking; a call while disconnected is dropped rather than buffered. */
    static void send(String event,JsonObject payload){
        Realtime open=link;
        if(open!=null)open.send(event,payload);
    }

    static int status(){
        if(!available()||link==null)return OFF;
        if(online)return ONLINE;
        return failed?OFFLINE:CONNECTING;
    }

    /** The word beside the light, kept next to the states so the two cannot drift apart. */
    static String statusLabel(int status){
        switch(status){
            case ONLINE:return "Online";
            case CONNECTING:return "Connecting";
            case OFFLINE:return "Offline";
            default:return "Off";
        }
    }

    /** The light's colour: green only when the link is actually joined. */
    static int statusColour(int status){
        switch(status){
            case ONLINE:return BedrockTokens.STATE_ENABLED;
            case CONNECTING:return 0xFFD9A21B;
            case OFFLINE:return BedrockTokens.STATE_DISABLED;
            default:return BedrockTokens.STATE_LOCKED;
        }
    }

    static final String CHANNEL="otl";
}
