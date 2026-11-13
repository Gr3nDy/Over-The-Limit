package otl.client;

import net.minecraft.event.ClickEvent;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class VoteBookTest {
    @Before public void clear() {
        VoteBook.INSTANCE.reset();
    }

    @Test public void lineCardReadsSymbolPrefixedNames() {
        VoteBook.Card card = VoteBook.lineCard("\u2715 Supernova");
        assertNotNull(card);
        assertEquals("Supernova", card.text);
        assertFalse(card.voted);
        assertEquals(ClickEvent.Action.RUN_COMMAND, card.action);
        assertEquals("/votenext -o Supernova", card.click);
    }

    @Test public void lineCardMarksOwnVotes() {
        VoteBook.Card card = VoteBook.lineCard("\u2714 Supernova");
        assertNotNull(card);
        assertTrue(card.voted);
    }

    @Test public void lineCardIgnoresStandingsAndPlainChat() {
        assertNull(VoteBook.lineCard("[\u2715 1] Supernova by Builder"));
        assertNull(VoteBook.lineCard("Alice: hello there"));
        assertNull(VoteBook.lineCard("\u2715 "));
    }

    @Test public void statNormalizesLabels() {
        assertTrue(VoteBook.stat("Most kills: 47 by Alice"));
        assertEquals("47", VoteBook.matchStats.get("Kills").value);
        assertEquals("Alice", VoteBook.matchStats.get("Kills").names.get(0));

        assertTrue(VoteBook.stat("Most damage dealt: 1234.5 by Bob"));
        assertEquals("1234.5", VoteBook.matchStats.get("Damage").value);

        assertTrue(VoteBook.stat("Most longest shot distance: 60 by Carl"));
        assertEquals("60", VoteBook.matchStats.get("Longest shot").value);

        assertTrue(VoteBook.stat("Deaths: 22 by Dana"));
        assertEquals("22", VoteBook.matchStats.get("Deaths").value);

        assertFalse(VoteBook.stat("Alice: hello"));
    }

    /** The live PGM rows carry units, hearts and colour codes that must not leak into the value. */
    @Test public void statReadsUnitsHeartsAndTies() {
        assertTrue(VoteBook.stat("Longest Shot: 53 blocks by ran_parr"));
        assertEquals("53", VoteBook.matchStats.get("Longest shot").value);
        assertEquals("ran_parr", VoteBook.matchStats.get("Longest shot").names.get(0));

        assertTrue(VoteBook.stat("Damage: 177.73\u2764 by \u00a7calejitox"));
        VoteBook.Stat damage = VoteBook.matchStats.get("Damage");
        assertEquals("177.73", damage.value);
        assertEquals("alejitox", damage.names.get(0));

        assertTrue(VoteBook.stat("Most kills: 16 by \u00a7calejitox\u00a7r, \u00a7dbob"));
        VoteBook.Stat kills = VoteBook.matchStats.get("Kills");
        assertEquals("16", kills.value);
        assertEquals(2, kills.names.size());
        assertEquals("bob", kills.names.get(1));
        assertTrue("colours must survive for the row text", kills.styled.get(1).contains("\u00a7d"));
    }

    @Test public void statKeepsSeparateValuesApart() {
        assertTrue(VoteBook.stat("Most kills: 16 by Alice"));
        assertTrue(VoteBook.stat("Most kills: 9 by Bob"));
        VoteBook.Stat kills = VoteBook.matchStats.get("Kills");
        assertEquals("9", kills.value);
        assertEquals(1, kills.names.size());
        assertEquals("Bob", kills.names.get(0));
    }

    @Test public void ownNameIsMarkedAndPreferred() {
        assertTrue(VoteBook.you("Alice", "Alice"));
        assertTrue(VoteBook.you("Alice", "alice"));
        assertFalse(VoteBook.you("Alice", "Bob"));
        assertFalse(VoteBook.you("Alice", null));
        // PGM puts the rank in front of the name in its stats rows.
        assertTrue(VoteBook.you("[MOD] Alice", "Alice"));
        assertTrue(VoteBook.you("[OWNER]Alice", "Alice"));
        assertFalse(VoteBook.you("[MOD] Alice", "Ali"));
        assertFalse(VoteBook.you("[MOD] Malice", "Alice"));
        // PGM tags your own row with "(You)" in the name itself.
        assertTrue(VoteBook.you("Alice (You)", "Alice"));
        assertTrue(VoteBook.you("[MOD] Alice (You)", "Alice"));
        assertFalse(VoteBook.you("Alice (You)", "Alic"));
        VoteBook.Stat stat = new VoteBook.Stat("16");
        stat.add("Alice", "Alice");
        stat.add("Bob", "Bob");
        assertEquals(0, VoteBook.pick(stat.names, null));
        assertEquals(0, VoteBook.pick(stat.names, "Nobody"));
        assertEquals(1, VoteBook.pick(stat.names, "bob"));
    }

    @Test public void resetClearsEverything() {
        VoteBook.INSTANCE.reset();
        VoteBook.stat("Most kills: 1 by Alice");
        VoteBook.standings.put("supernova", 3);
        VoteBook.matchLink = "https://example.test/match";
        VoteBook.votedCommand = "/votenext -o Supernova";
        VoteBook.superVotes = 2;

        VoteBook.INSTANCE.reset();

        assertTrue(VoteBook.matchStats.isEmpty());
        assertTrue(VoteBook.standings.isEmpty());
        assertNull(VoteBook.matchLink);
        assertEquals("", VoteBook.votedCommand);
        assertEquals(-1, VoteBook.superVotes);
    }
}
