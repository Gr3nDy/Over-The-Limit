package otl.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.*;

/** The Supabase Realtime 1.0.0 wire format, which is the part a socket test cannot reach. */
public class RealtimeTest {
    private static JsonObject json(String text){
        return new JsonParser().parse(text).getAsJsonObject();
    }

    @Test public void joinAsksForAPublicBroadcastChannel(){
        JsonObject join=json(Realtime.join("realtime:otl-boards","1"));
        assertEquals("realtime:otl-boards",join.get("topic").getAsString());
        assertEquals("phx_join",join.get("event").getAsString());
        assertEquals("1",join.get("ref").getAsString());
        assertEquals("1",join.get("join_ref").getAsString());
        JsonObject config=join.getAsJsonObject("payload").getAsJsonObject("config");
        assertFalse("public channel needs no RLS",config.get("private").getAsBoolean());
        assertFalse(config.getAsJsonObject("broadcast").get("self").getAsBoolean());
        assertFalse(config.getAsJsonObject("presence").get("enabled").getAsBoolean());
    }

    @Test public void broadcastCarriesTheJoinRef(){
        JsonObject data=new JsonObject();
        data.addProperty("n","Alex");
        String sent=Realtime.broadcast("realtime:otl-boards","1","2","board",data);
        JsonObject wire=json(sent);
        assertEquals("broadcast",wire.get("event").getAsString());
        assertEquals("2",wire.get("ref").getAsString());
        assertEquals("1",wire.get("join_ref").getAsString());
        assertEquals("board",wire.getAsJsonObject("payload").get("event").getAsString());
        assertEquals("Alex",wire.getAsJsonObject("payload").getAsJsonObject("payload").get("n").getAsString());
    }

    /** The server wraps a broadcast in an envelope; the inner event and payload are what matter. */
    @Test public void incomingBroadcastIsUnwrapped(){
        Realtime.Incoming message=Realtime.parse("{\"topic\":\"realtime:otl-boards\",\"event\":\"broadcast\","
            +"\"payload\":{\"type\":\"broadcast\",\"event\":\"board\",\"payload\":{\"n\":\"Alex\",\"b\":true}}}");
        assertEquals(Realtime.Incoming.BROADCAST,message.kind);
        assertEquals("board",message.event);
        assertEquals("Alex",message.payload.get("n").getAsString());
        assertTrue(message.payload.get("b").getAsBoolean());
    }

    @Test public void repliesAndFailuresAreClassified(){
        Realtime.Incoming ok=Realtime.parse("{\"event\":\"phx_reply\",\"ref\":\"1\",\"payload\":{\"status\":\"ok\"}}");
        assertEquals(Realtime.Incoming.REPLY,ok.kind);
        assertEquals("1",ok.ref);
        assertEquals(Realtime.Incoming.FAILED,
            Realtime.parse("{\"event\":\"phx_reply\",\"ref\":\"1\",\"payload\":{\"status\":\"error\"}}").kind);
        assertEquals(Realtime.Incoming.FAILED,Realtime.parse("{\"event\":\"phx_close\",\"payload\":{}}").kind);
        assertEquals(Realtime.Incoming.FAILED,Realtime.parse("{\"event\":\"phx_error\",\"payload\":{}}").kind);
    }

    /** A newer server may send events this build never asked for; they must not break the reader. */
    @Test public void unknownOrBrokenMessagesAreIgnored(){
        assertEquals(Realtime.Incoming.IGNORE,Realtime.parse("{\"event\":\"system\",\"payload\":{\"status\":\"ok\"}}").kind);
        assertEquals(Realtime.Incoming.IGNORE,Realtime.parse("not json").kind);
        assertEquals(Realtime.Incoming.IGNORE,Realtime.parse("{\"event\":\"broadcast\",\"payload\":{}}").kind);
    }

    @Test public void heartbeatUsesThePhoenixTopic(){
        JsonObject beat=json(Realtime.heartbeat("7"));
        assertEquals("phoenix",beat.get("topic").getAsString());
        assertEquals("heartbeat",beat.get("event").getAsString());
        assertEquals("7",beat.get("ref").getAsString());
    }

    @Test public void channelNamesAreNamespacedOnce(){
        assertEquals("realtime:otl-boards",Realtime.channel("otl-boards"));
    }

    /** A wrong path here would make every connection fail, so the endpoint is pinned. */
    @Test public void endpointPointsAtTheRealtimeSocket(){
        assertEquals("https://abc.supabase.co/realtime/v1/websocket?apikey=anon+key&vsn=1.0.0",
            Realtime.endpoint("https://abc.supabase.co/","anon key"));
        assertEquals("https://abc.supabase.co/realtime/v1/websocket?apikey=k&vsn=1.0.0",
            Realtime.endpoint("https://abc.supabase.co","k"));
    }
}
