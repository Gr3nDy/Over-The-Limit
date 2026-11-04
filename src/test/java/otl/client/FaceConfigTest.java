package otl.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class FaceConfigTest {
    @Test public void scope(){
        assertTrue(FaceConfig.allows(0,true));   // only me, own face
        assertFalse(FaceConfig.allows(0,false)); // only me, others
        assertTrue(FaceConfig.allows(1,false));  // all, others
        assertTrue(FaceConfig.allows(1,true));   // all, own face
        assertFalse(FaceConfig.allows(2,true));  // none
        assertFalse(FaceConfig.allows(2,false)); // none
    }

    @Test public void labels(){assertEquals("Only me",FaceConfig.label(0));assertEquals("All",FaceConfig.label(1));assertEquals("None",FaceConfig.label(2));}

    @Test public void defaultsMatchSpec(){
        assertEquals(2,FaceConfig.blush);      // blush starts None
        assertEquals(2,FaceConfig.glow);       // glowing eyes starts None
        assertEquals(1,FaceConfig.glances);    // glances default All
        assertEquals(1,FaceConfig.blinking);   // blinking default All
        assertEquals(2,FaceConfig.hat);        // over-hat starts None
        assertTrue(FaceConfig.naturalLight);   // world lighting starts Natural
        assertEquals(3.6f,FaceConfig.blinkSeconds,0.0001f); // blink interval default
        assertTrue(FaceConfig.blinkSeconds>=1.5f&&FaceConfig.blinkSeconds<=8f);
    }
}
