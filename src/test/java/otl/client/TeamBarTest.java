package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class TeamBarTest {
    @Test public void greyTabNamesAreDeadPlayers() {
        assertTrue(TeamBar.grey("\u00a77RedPlayer0"));
        assertTrue(TeamBar.grey("\u00a78RedPlayer0"));
    }

    @Test public void teamColoursAreAlive() {
        assertFalse(TeamBar.grey("\u00a7cRedPlayer0"));
        assertFalse(TeamBar.grey("\u00a79BluePlayer0"));
        assertFalse(TeamBar.grey("RedPlayer0"));
        assertFalse(TeamBar.grey(null));
    }

    /** Real PGM: "|xx-uuid" tab-sorting keys and "§" coloured entries are not accounts. */
    @Test public void tabSortingKeysAreNotPlayers() {
        assertFalse(TeamBar.realPlayer("|cd-47beb25a3108"));
        assertFalse(TeamBar.realPlayer("\u00a7cRedPlayer0"));
        assertFalse(TeamBar.realPlayer(""));
        assertFalse(TeamBar.realPlayer(null));
        assertTrue(TeamBar.realPlayer("Player328"));
    }

    /** The clock is the middle of the bar, whatever each team's row holds. */
    @Test public void clockStaysCentredWhateverTheTeamSizes() {
        java.util.List<java.util.List<String>> left=new java.util.ArrayList<>(),right=new java.util.ArrayList<>();
        left.add(new java.util.ArrayList<>(java.util.Arrays.asList("A","B","C")));
        right.add(java.util.Arrays.asList("D"));
        right.add(java.util.Arrays.asList("E","F"));
        TeamBar.Bar bar=new TeamBar.Bar(left,right,16,24,null,"");
        assertEquals(bar.width/2,bar.textX+bar.textW/2);
        // Two stacked rows plus the gap between them.
        assertEquals(2*16+TeamBar.ROW_GAP,bar.height);
        left.get(0).add("G"); // a player joins; the columns stay symmetric, so the clock cannot shift
        TeamBar.Bar grown=new TeamBar.Bar(left,right,16,24,null,"");
        assertEquals(grown.width/2,grown.textX+grown.textW/2);
    }
}
