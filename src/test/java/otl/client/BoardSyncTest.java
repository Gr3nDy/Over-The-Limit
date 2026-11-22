package otl.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.*;

/** What other OTL clients are told about a rider, and what a bad payload is allowed to do. */
public class BoardSyncTest {
    private static JsonObject payload(String text){
        return new JsonParser().parse(text).getAsJsonObject();
    }

    @Test public void boardGoesOutAndComesIn(){
        BoardSync.reset();
        assertFalse(BoardSync.riding("Alex"));
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true}"));
        assertTrue(BoardSync.riding("Alex"));
        assertFalse("a board is not a trick",BoardSync.takeTrick("Alex")>=0);
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":false}"));
        assertFalse(BoardSync.riding("Alex"));
        BoardSync.reset();
    }

    /** A trick is an instant, so it is handed to the render tick exactly once. */
    @Test public void trickIsDeliveredOnce(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"k\":2}"));
        assertEquals(2,BoardSync.takeTrick("Alex"));
        assertEquals(-1,BoardSync.takeTrick("Alex"));
        BoardSync.reset();
    }

    @Test public void ridersAreSeparate(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true}"));
        BoardSync.receive(payload("{\"n\":\"Sam\",\"b\":false}"));
        assertTrue(BoardSync.riding("Alex"));
        assertFalse(BoardSync.riding("Sam"));
        assertFalse(BoardSync.riding("Nobody"));
        BoardSync.reset();
    }

    /** A payload missing a name or carrying the wrong type can at worst draw nothing. */
    @Test public void malformedPayloadDrawsNothing(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"b\":true}"));
        BoardSync.receive(payload("{\"n\":\"\",\"b\":true}"));
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":\"yes\"}"));
        BoardSync.receive(payload("{\"n\":\"Alex\",\"k\":\"two\"}"));
        assertFalse(BoardSync.riding("Alex"));
        assertFalse(BoardSync.takeTrick("Alex")>=0);
        BoardSync.reset();
    }

    @Test public void resetForgetsEveryRider(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"k\":1}"));
        BoardSync.reset();
        assertFalse(BoardSync.riding("Alex"));
        assertEquals(-1,BoardSync.takeTrick("Alex"));
    }

    /** Every state has its own word, so the light and the text can never disagree. */
    @Test public void everyStatusHasItsOwnLabel(){
        assertEquals("Off",OtlService.statusLabel(OtlService.OFF));
        assertEquals("Connecting",OtlService.statusLabel(OtlService.CONNECTING));
        assertEquals("Online",OtlService.statusLabel(OtlService.ONLINE));
        assertEquals("Offline",OtlService.statusLabel(OtlService.OFFLINE));
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(int status=0;status<=3;status++)
            assertTrue("duplicate label for status "+status,seen.add(OtlService.statusLabel(status)));
    }

    /** Green is reserved for a joined link: anything else must be visibly different. */
    @Test public void onlyAJoinedLinkIsGreen(){
        int green=OtlService.statusColour(OtlService.ONLINE);
        assertNotEquals(green,OtlService.statusColour(OtlService.CONNECTING));
        assertNotEquals(green,OtlService.statusColour(OtlService.OFFLINE));
        assertNotEquals(green,OtlService.statusColour(OtlService.OFF));
    }

    /** Nothing is dialled before a world loads, so the light must not claim to be offline then. */
    @Test public void nothingRunningIsOff(){
        OtlService.stop();
        assertEquals(OtlService.OFF,OtlService.status());
    }
}
