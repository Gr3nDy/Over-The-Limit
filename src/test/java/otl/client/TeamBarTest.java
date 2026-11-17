package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class TeamBarTest {
    @Test public void normalClockIsGreenWithoutChangingCountdownWarnings(){
        assertEquals(TeamBar.GREEN,TeamBar.colour(null));
        assertEquals(TeamBar.GREEN,TeamBar.colour(new TeamBar.Timer("1:05",65,false,false)));
        assertEquals(TeamBar.YELLOW,TeamBar.colour(new TeamBar.Timer("1:05",65,true,false)));
        assertEquals(TeamBar.RED,TeamBar.colour(new TeamBar.Timer("0:25",25,true,false)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("0:10",10,false,true)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("0:00",0,false,true)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("1:00",60,true,true)));
    }
    @Test public void hoverUsesOriginalHeadBoundsOnBothSidesAndRows(){
        java.util.List<java.util.List<String>> left=java.util.Arrays.asList(java.util.Arrays.asList("Alex","Sam"),java.util.Collections.singletonList("Pat"));
        java.util.List<java.util.List<String>> right=java.util.Collections.singletonList(java.util.Collections.singletonList("Kai"));
        TeamBar.Bar bar=new TeamBar.Bar(left,right,16,20,null,"");
        int x=bar.leftStart(left.get(0));
        assertEquals("Alex",TeamBar.headAt(bar,x,0));
        assertEquals("Sam",TeamBar.headAt(bar,x+bar.advance,0));
        assertEquals("Pat",TeamBar.headAt(bar,bar.leftStart(left.get(1)),bar.band+TeamBar.ROW_GAP));
        assertEquals("Kai",TeamBar.headAt(bar,bar.rightStart(right.get(0)),0));
        assertNull(TeamBar.headAt(bar,x,bar.band));
        assertNull(TeamBar.headAt(bar,-1,0));
    }
    @Test public void deadHeadOpacityPreservesSkinAlphaAndLeavesLivingHeadsAlone(){
        int pixel=0x80C04020;
        assertEquals(pixel,TeamBar.headColour(pixel,false,25));
        assertEquals(32,TeamBar.headColour(pixel,true,25)>>>24);
        assertEquals(64,TeamBar.headColour(pixel,true,50)>>>24);
        assertEquals(128,TeamBar.headColour(pixel,true,100)>>>24);
        assertEquals(TeamBar.greyed(pixel)&0xFFFFFF,TeamBar.headColour(pixel,true,50)&0xFFFFFF);
    }
    @Test public void deathColourComesFromThePlayersNameNotItsRankOrSuffix() {
        assertEquals(Boolean.TRUE,TeamBar.greyPlayerName("\u00a7c[Rank] \u00a78Alex\u00a7c [VIP]","Alex"));
        assertEquals(Boolean.FALSE,TeamBar.greyPlayerName("\u00a7c[Rank] \u00a77Alex\u00a7c [VIP]","Alex"));
        assertEquals(Boolean.FALSE,TeamBar.greyPlayerName("\u00a77[Rank] \u00a7cAlex\u00a77 [VIP]","Alex"));
        assertEquals(Boolean.FALSE,TeamBar.greyPlayerName("\u00a77[Rank] \u00a7rAlex","Alex"));
        assertNull(TeamBar.greyPlayerName("\u00a77Alexandra","Alex"));
    }
    @Test public void headsShrinkForEveryFiveMorePlayers() {
        assertEquals(16,TeamBar.headSize(5));
        assertEquals(16,TeamBar.headSize(9));
        assertEquals(15,TeamBar.headSize(10));
        assertEquals(14,TeamBar.headSize(15));
        assertEquals(13,TeamBar.headSize(20));
        assertEquals(2,TeamBar.headSize(75));
        assertEquals(2,TeamBar.headSize(100));
    }
    @Test public void teamOrderFollowsXmlRatherThanNamesOrPlayerCounts() {
        java.util.List<String> order=java.util.Arrays.asList("Marley","Eldia","Zombies");
        TeamBar.Side blue=new TeamBar.Side("\u00a79Eldia",java.util.Arrays.asList("A","B","C"));
        TeamBar.Side red=new TeamBar.Side("\u00a7cMarley",java.util.Collections.singletonList("D"));
        java.util.List<TeamBar.Side> sides=new java.util.ArrayList<>(java.util.Arrays.asList(blue,red));
        TeamBar.sortSides(sides,order);
        assertSame(red,sides.get(0));assertSame(blue,sides.get(1));
        assertEquals(1,TeamBar.teamRank("\u00a79Eldia",order));
        assertEquals(3,TeamBar.teamRank("Unknown",order));
        sides.remove(red);
        assertEquals(1,TeamBar.teamRank(sides.get(0).name,order));
    }
    @Test public void ffaSlotsFollowArrivalAndKeepSidesWhenPlayersLeave() {
        java.util.List<String> slots=new java.util.ArrayList<>();
        TeamBar.Side z=new TeamBar.Side("z",java.util.Collections.singletonList("Zoe"));
        TeamBar.Side a=new TeamBar.Side("a",java.util.Collections.singletonList("Alice"));
        TeamBar.Side b=new TeamBar.Side("b",java.util.Collections.singletonList("Bob"));
        TeamBar.updateFfaSlots(slots,java.util.Arrays.asList(z));
        TeamBar.updateFfaSlots(slots,java.util.Arrays.asList(a,z));
        TeamBar.updateFfaSlots(slots,java.util.Arrays.asList(a,b,z));
        assertEquals(java.util.Arrays.asList("Zoe","Alice","Bob"),slots);
        TeamBar.updateFfaSlots(slots,java.util.Arrays.asList(b,z));
        assertEquals(java.util.Arrays.asList("Zoe",null,"Bob"),slots);
        TeamBar.Side c=new TeamBar.Side("c",java.util.Collections.singletonList("Cara"));
        TeamBar.updateFfaSlots(slots,java.util.Arrays.asList(b,c,z));
        assertEquals(java.util.Arrays.asList("Zoe","Cara","Bob"),slots);
    }
    @Test public void localRespawnSubtitlesAreRecognized() {
        assertTrue(TeamBar.respawnSubtitle("\u00a7aLeft click to respawn in 3.0s"));
        assertTrue(TeamBar.respawnSubtitle("Respawning in 2.0s"));
        assertTrue(TeamBar.respawnSubtitle("You will respawn as soon as possible..."));
        assertFalse(TeamBar.respawnSubtitle("They can respawn in 2.0s"));
        assertFalse(TeamBar.respawnSubtitle(null));
    }
    @Test public void greyTabNamesAreDeadPlayers() {
        assertFalse(TeamBar.grey("\u00a77RedPlayer0"));
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
