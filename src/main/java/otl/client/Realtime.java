package otl.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Supabase Realtime client over one WebSocket: join a public channel, send and receive
 * broadcast events, keep the socket alive with heartbeats, reconnect with backoff.
 *
 * <p>Nothing here runs on the game thread. Sends are queued and written by a writer thread, so a
 * stalled network can never hold up a frame. Public channels need no database table and no RLS
 * policy, which is why this needs no schema to deploy. Every event on the channel is delivered to
 * one listener, so a second feature can share the same connection by using its own event name.
 */
final class Realtime implements AutoCloseable {
    interface Listener {
        void onBroadcast(String event,JsonObject payload);
        void onStatus(boolean joined,String detail);
    }

    /** The server drops a connection that stays quiet for 25 seconds. */
    private static final long HEARTBEAT_MS=20000;
    private static final long[] BACKOFF={1000,2000,5000,10000};

    private final String url,topic;
    private final Listener listener;
    private final BlockingQueue<byte[]> outgoing=new LinkedBlockingQueue<>();
    private final Random mask=new Random();
    private final AtomicInteger refs=new AtomicInteger();
    private final Thread worker;
    private volatile WsSocket current;
    private volatile boolean closed;
    private volatile boolean joined;
    private volatile String joinRef;

    private Realtime(String url,String channel,Listener listener){
        this.url=url;
        this.topic=channel(channel);
        this.listener=listener;
        worker=new Thread(this::run,"OTL Realtime");
        worker.setDaemon(true);
    }

    /**
     * The project URL turned into the Realtime socket endpoint. {@code wss://} is rewritten to
     * {@code https://} because it is the same TLS endpoint and {@link URL} only knows https.
     */
    static String endpoint(String base,String key){
        String trimmed=base.trim();
        if(trimmed.startsWith("wss://"))trimmed="https://"+trimmed.substring("wss://".length());
        else if(trimmed.startsWith("ws://"))trimmed="http://"+trimmed.substring("ws://".length());
        while(trimmed.endsWith("/"))trimmed=trimmed.substring(0,trimmed.length()-1);
        try{
            return trimmed+"/realtime/v1/websocket?apikey="
                +java.net.URLEncoder.encode(key,StandardCharsets.UTF_8.name())+"&vsn=1.0.0";
        }catch(java.io.UnsupportedEncodingException impossible){
            throw new IllegalStateException("UTF-8 is unavailable",impossible);
        }
    }

    static Realtime open(String url,String key,String channel,Listener listener){
        Realtime realtime=new Realtime(endpoint(url,key),channel,listener);
        realtime.worker.start();
        return realtime;
    }

    static String channel(String name){return "realtime:"+name;}

    /** Queued and never blocking: the caller is the game thread. Dropped until the join lands. */
    void send(String event,JsonObject payload){
        String ref=joinRef;
        if(!joined||closed||ref==null)return;
        outgoing.offer(WsFrames.text(broadcast(topic,ref,nextRef(),event,payload),mask));
    }

    @Override public void close(){
        closed=true;
        joined=false;
        worker.interrupt();
        // A blocked read ignores an interrupt; closing the socket is what wakes it.
        WsSocket socket=current;
        if(socket!=null)socket.close();
    }

    private void run(){
        int attempt=0;
        while(!closed){
            WsSocket socket=null;
            Thread writer=null;
            boolean wasJoined=false;
            long upAt=System.currentTimeMillis();
            try{
                socket=WsSocket.open(new URL(url));
                current=socket;
                String ref=nextRef();
                joinRef=ref;
                socket.write(WsFrames.text(join(topic,ref),mask));
                writer=writer(socket);
                writer.start();
                readLoop(socket);
            }catch(Exception failure){
                if(!closed)System.err.println("OTL: realtime link: "+failure);
            }finally{
                wasJoined=joined;
                joined=false;
                joinRef=null;
                current=null;
                if(writer!=null)writer.interrupt();
                if(socket!=null)socket.close();
                outgoing.clear();
                if(!closed)listener.onStatus(false,"disconnected");
            }
            if(closed)break;
            // A connection that lived a while was healthy; start its backoff from the bottom again.
            if(wasJoined&&System.currentTimeMillis()-upAt>30000)attempt=0;
            sleep(BACKOFF[Math.min(attempt++,BACKOFF.length-1)]);
        }
    }

    private void readLoop(WsSocket socket)throws IOException{
        WsFrames.Reader reader=new WsFrames.Reader();
        while(!closed){
            WsFrames.Message message=reader.read(socket.in);
            if(message==null)throw new IOException("server closed the socket");
            switch(message.opcode){
                case WsFrames.PING:
                    outgoing.offer(WsFrames.frame(WsFrames.PONG,message.payload,mask));
                    break;
                case WsFrames.PONG:
                    break;
                case WsFrames.CLOSE:
                    throw new IOException("server closed the channel");
                case WsFrames.TEXT:
                    handle(parse(message.text()));
                    break;
                default:
                    break;
            }
        }
    }

    private void handle(Incoming message)throws IOException{
        if(message.kind==Incoming.FAILED)
            throw new IOException("realtime rejected: "+(message.event==null?"connection lost":message.event));
        if(message.kind==Incoming.REPLY){
            if(!joined&&message.ref!=null&&message.ref.equals(joinRef)){
                joined=true;
                listener.onStatus(true,"joined");
            }
            return;
        }
        if(message.kind==Incoming.BROADCAST)listener.onBroadcast(message.event,message.payload);
    }

    /**
     * The only writer of ordinary frames. It also owns the heartbeat, because a quiet channel
     * would otherwise leave the reader blocked and never send one.
     */
    private Thread writer(WsSocket socket){
        Thread thread=new Thread(()->{
            long nextBeat=System.currentTimeMillis()+HEARTBEAT_MS;
            try{
                while(!closed&&!Thread.currentThread().isInterrupted()){
                    long wait=nextBeat-System.currentTimeMillis();
                    byte[] message=wait>0?outgoing.poll(wait,TimeUnit.MILLISECONDS):outgoing.poll();
                    if(message==null){
                        if(System.currentTimeMillis()>=nextBeat){
                            socket.write(WsFrames.text(heartbeat(nextRef()),mask));
                            nextBeat=System.currentTimeMillis()+HEARTBEAT_MS;
                        }
                        continue;
                    }
                    socket.write(message);
                }
            }catch(InterruptedException stop){
                Thread.currentThread().interrupt();
            }catch(IOException lost){
                // The reader hits the same failure; closing here unblocks it immediately.
                socket.close();
            }
        },"OTL Realtime writer");
        thread.setDaemon(true);
        return thread;
    }

    private String nextRef(){return Integer.toString(refs.incrementAndGet());}

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException stop){Thread.currentThread().interrupt();}
    }

    // ---- Protocol 1.0.0 messages. Pure, so the wire format is testable without a socket. ----

    static String join(String topic,String ref){
        JsonObject broadcast=new JsonObject();
        broadcast.addProperty("self",false);
        broadcast.addProperty("ack",false);
        JsonObject presence=new JsonObject();
        presence.addProperty("enabled",false);
        JsonObject config=new JsonObject();
        config.add("broadcast",broadcast);
        config.add("presence",presence);
        config.addProperty("private",false);
        JsonObject payload=new JsonObject();
        payload.add("config",config);
        return envelope(topic,"phx_join",payload,ref,ref);
    }

    static String broadcast(String topic,String joinRef,String ref,String event,JsonObject data){
        JsonObject payload=new JsonObject();
        payload.addProperty("type","broadcast");
        payload.addProperty("event",event);
        payload.add("payload",data);
        return envelope(topic,"broadcast",payload,ref,joinRef);
    }

    static String heartbeat(String ref){
        return envelope("phoenix","heartbeat",new JsonObject(),ref,null);
    }

    private static String envelope(String topic,String event,JsonObject payload,String ref,String joinRef){
        JsonObject message=new JsonObject();
        message.addProperty("topic",topic);
        message.addProperty("event",event);
        message.add("payload",payload);
        if(ref!=null)message.addProperty("ref",ref);
        if(joinRef!=null)message.addProperty("join_ref",joinRef);
        return message.toString();
    }

    static final class Incoming {
        static final int IGNORE=0,BROADCAST=1,REPLY=2,FAILED=3;
        final int kind;
        final String ref,event;
        final JsonObject payload;
        Incoming(int kind,String ref,String event,JsonObject payload){
            this.kind=kind;this.ref=ref;this.event=event;this.payload=payload;
        }
    }

    /** Anything unrecognised is ignored: a newer server may send events this build never uses. */
    static Incoming parse(String json){
        try{
            JsonObject message=new JsonParser().parse(json).getAsJsonObject();
            String event=message.has("event")?message.get("event").getAsString():"";
            if("broadcast".equals(event)){
                JsonObject payload=message.has("payload")&&message.get("payload").isJsonObject()
                    ?message.getAsJsonObject("payload"):new JsonObject();
                String inner=payload.has("event")?payload.get("event").getAsString():"";
                if(inner.isEmpty())return new Incoming(Incoming.IGNORE,null,null,null);
                JsonObject data=payload.has("payload")&&payload.get("payload").isJsonObject()
                    ?payload.getAsJsonObject("payload"):new JsonObject();
                return new Incoming(Incoming.BROADCAST,null,inner,data);
            }
            if("phx_reply".equals(event)){
                JsonObject payload=message.has("payload")&&message.get("payload").isJsonObject()
                    ?message.getAsJsonObject("payload"):null;
                boolean ok=payload!=null&&payload.has("status")&&"ok".equals(payload.get("status").getAsString());
                String ref=message.has("ref")?message.get("ref").getAsString():null;
                return new Incoming(ok?Incoming.REPLY:Incoming.FAILED,ref,null,null);
            }
            if("phx_error".equals(event)||"phx_close".equals(event))
                return new Incoming(Incoming.FAILED,null,event,null);
            return new Incoming(Incoming.IGNORE,null,null,null);
        }catch(RuntimeException malformed){
            return new Incoming(Incoming.IGNORE,null,null,null);
        }
    }
}
