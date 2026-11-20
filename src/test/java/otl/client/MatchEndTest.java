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

    @Test public void theEndSoundIsLanguageFree() {
        // PGM sends the wither's death to the winners and its spawn to the losers, on 1.8 as the
        // legacy name and on modern servers as the registry key.
        assertTrue(MatchEnd.endedBySound("minecraft:mob.wither.death"));
        assertTrue(MatchEnd.endedBySound("minecraft:mob.wither.spawn"));
        assertTrue(MatchEnd.endedBySound("minecraft:entity.wither.death"));
        assertTrue(MatchEnd.endedBySound("MOB.WITHER.DEATH"));
        assertFalse(MatchEnd.endedBySound("minecraft:mob.wither.ambient"));
        assertFalse(MatchEnd.endedBySound("minecraft:note.pling"));
        assertFalse(MatchEnd.endedBySound(""));
        assertFalse(MatchEnd.endedBySound(null));
    }

    @Test public void ignoresChatAndStartLines() {
        assertFalse(MatchEnd.is("Alice: game over!"));
        assertFalse(MatchEnd.is("The match has started!"));
        assertFalse(MatchEnd.is("Go!"));
        assertFalse(MatchEnd.is(""));
        assertFalse(MatchEnd.is(null));
    }
}
