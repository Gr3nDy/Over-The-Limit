package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReplayChatTest {
    @Test public void namesTheThrowerScorerAndCatcher(){
        assertEquals("Ace",Replay.name("\u00bb Ace threw the Football!"," threw the Football"));
        assertEquals("Ace",Replay.name("Ace threw the Football!"," threw the Football"));
        assertEquals("Bob",Replay.name(" \u00a7e\u00a7l\u00bb\u00a7r Bob\u00a77 takes it to the end zone!"," takes it to the end zone"));
        assertEquals("Cid",Replay.name("\u00bb Cid picked up the Football!"," picked up"));
        assertEquals("Dee",Replay.name("\u00bb Dee intercepted the Football!"," intercepted"));
    }

    @Test public void ignoresLinesWithoutTheMarker(){
        assertNull(Replay.name("the Football rolled away"," threw the Football"));
        assertNull(Replay.name(""," threw the Football"));
    }
}
