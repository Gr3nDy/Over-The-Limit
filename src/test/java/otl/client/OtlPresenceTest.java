package otl.client;

import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

/** Presence is only ever "a name the server also lists", so the name check is the trust boundary. */
public class OtlPresenceTest {
    private static JsonObject payload(String text){
        return new com.google.gson.JsonParser().parse(text).getAsJsonObject();
    }

    @Test public void realNamesAreAccepted(){
        assertTrue(OtlPresence.plausible("Notch"));
        assertTrue(OtlPresence.plausible("a_b_C123"));
        assertTrue(OtlPresence.plausible("abcdefghijklmnop"));
    }

    @Test public void anythingElseIsRefused(){
        assertFalse(OtlPresence.plausible(null));
        assertFalse(OtlPresence.plausible(""));
        assertFalse("too long for an account",OtlPresence.plausible("abcdefghijklmnopq"));
        assertFalse("spaces are not names",OtlPresence.plausible("two words"));
        assertFalse("chat codes are not names",OtlPresence.plausible("\u00a7cRed"));
        assertFalse("a name is not a sentence",OtlPresence.plausible("<script>"));
    }

    @Test public void aBroadcastRecordsTheName(){
        OtlPresence.reset();
        OtlPresence.receive("here",payload("{\"n\":\"Alex\"}"));
        assertEquals(1,OtlPresence.known());
        OtlPresence.reset();
    }

    @Test public void otherEventsAreNotPresence(){
        OtlPresence.reset();
        OtlPresence.receive("board",payload("{\"n\":\"Alex\",\"b\":true}"));
        assertEquals(0,OtlPresence.known());
        OtlPresence.reset();
    }

    /** A flood of invented names must not grow the map: only the cap's worth is kept. */
    @Test public void nameCountIsCapped(){
        OtlPresence.reset();
        for(int i=0;i<500;i++)OtlPresence.receive("here",payload("{\"n\":\"P"+i+"\"}"));
        assertEquals(200,OtlPresence.known());
        OtlPresence.reset();
    }

    /** Without a world there is nobody to match against, so the list must be empty, not stale. */
    @Test public void noWorldMeansNobodyIsHere(){
        OtlPresence.reset();
        OtlPresence.receive("here",payload("{\"n\":\"Alex\"}"));
        assertTrue(OtlPresence.here().isEmpty());
        OtlPresence.reset();
    }
}
