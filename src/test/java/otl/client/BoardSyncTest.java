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
        assertEquals("a board is not a trick",-1,BoardSync.take("Alex").trick);
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":false}"));
        assertFalse(BoardSync.riding("Alex"));
        BoardSync.reset();
    }

    /** A trick is an instant, so it is handed to the render tick exactly once. */
    @Test public void trickIsDeliveredOnce(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"k\":2}"));
        assertEquals(2,BoardSync.take("Alex").trick);
        assertEquals(-1,BoardSync.take("Alex").trick);
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
        assertNull(BoardSync.take("Alex"));
        BoardSync.reset();
    }

    @Test public void resetForgetsEveryRider(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"k\":1}"));
        BoardSync.reset();
        assertFalse(BoardSync.riding("Alex"));
        assertNull(BoardSync.take("Alex"));
    }

    @Test public void fullPoseAndOneShotCuesSurviveAHeartbeat(){
        BoardSync.reset();
        int pose=BoardSync.pose(false,true,false,0.8f);
        BoardSync.receive("board",BoardSync.packet("Alex",true,pose,1,true,5));
        BoardSync.receive("board",BoardSync.packet("Alex",true,pose,-1,false,0));
        BoardSync.Ride remote=BoardSync.take("Alex");
        assertTrue(remote.board&&remote.push);
        assertFalse(BoardSync.grounded(remote.pose));
        assertTrue(BoardSync.crouching(remote.pose));
        assertEquals(6f/7,BoardSync.falling(remote.pose),1e-6f);
        assertEquals(1,remote.trick);
        assertEquals(5,remote.landing);
        remote=BoardSync.take("Alex");
        assertFalse(remote.push);
        assertEquals(-1,remote.trick);
        assertEquals(0,remote.landing);
        int grind=BoardSync.pose(true,false,true,0f);
        BoardSync.receive("board",BoardSync.packet("Alex",true,grind,-1,false,0));
        assertTrue(BoardSync.grinding(BoardSync.take("Alex").pose));
        BoardSync.receive("board",BoardSync.packet("Alex",false,0,-1,false,0));
        assertNull(BoardSync.take("Alex"));
        BoardSync.reset();
    }

    @Test public void oldMessagesWorkAndInvalidCuesAreIgnored(){
        BoardSync.reset();
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"k\":0}"));
        BoardSync.Ride legacy=BoardSync.take("Alex");
        assertEquals(-1,legacy.pose);
        assertEquals(0,legacy.trick);
        BoardSync.receive(payload("{\"n\":\"Alex\",\"b\":true,\"m\":99,\"p\":\"true\",\"k\":80,\"l\":12}"));
        BoardSync.Ride invalid=BoardSync.take("Alex");
        assertEquals(-1,invalid.pose);
        assertFalse(invalid.push);
        assertEquals(-1,invalid.trick);
        assertEquals(0,invalid.landing);
        BoardSync.reset();
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
