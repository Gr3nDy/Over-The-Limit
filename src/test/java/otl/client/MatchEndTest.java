package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class MatchEndTest {
    @Test public void recognisesPgmEndLines() {
        assertTrue(MatchEnd.is("Game over!"));
        assertTrue(MatchEnd.is("Tied!"));
        assertTrue(MatchEnd.is("Your team won!"));
        assertTrue(MatchEnd.is("Your team lost"));
        assertTrue(MatchEnd.is("Avalon wins!"));
        assertTrue(MatchEnd.is("Ran_parr has ended the match."));
    }

    @Test public void ignoresChatAndStartLines() {
        assertFalse(MatchEnd.is("Alice: game over!"));
        assertFalse(MatchEnd.is("The match has started!"));
        assertFalse(MatchEnd.is("Go!"));
        assertFalse(MatchEnd.is(""));
        assertFalse(MatchEnd.is(null));
    }
}
