package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateNoticeTest {
    @Test public void aNewerReleaseIsRecognised(){
        assertTrue(UpdateNotice.newer("v0.7.2","0.7.1"));
        assertTrue(UpdateNotice.newer("0.8.0","0.7.9"));
        // Numeric, not textual: 10 comes after 9.
        assertTrue(UpdateNotice.newer("v0.10.0","0.9.9"));
        assertTrue(UpdateNotice.newer("v1.0.0","0.10.0"));
    }

    @Test public void theSameOrOlderReleaseSaysNothing(){
        assertFalse(UpdateNotice.newer("v0.7.1","0.7.1"));
        assertFalse(UpdateNotice.newer("0.7.0","0.7.1"));
        assertFalse(UpdateNotice.newer("v0.6.9","0.7.1"));
        // A tag with no numbers, or a build string, must not be read as an upgrade.
        assertFalse(UpdateNotice.newer("nightly","0.7.1"));
        assertFalse(UpdateNotice.newer(null,"0.7.1"));
        assertFalse(UpdateNotice.newer("v0.7.1",""));
    }

    @Test public void theReleaseTagIsReadFromTheApiReply(){
        assertEquals("v0.7.2",UpdateNotice.tagOf("{\"tag_name\":\"v0.7.2\",\"name\":\"OTL v0.7.2\"}"));
        assertNull(UpdateNotice.tagOf("{\"message\":\"Not Found\"}"));
        assertNull(UpdateNotice.tagOf("<html>rate limited</html>"));
        assertNull(UpdateNotice.tagOf("{\"tag_name\":null}"));
    }
}
