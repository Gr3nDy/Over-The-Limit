package otl.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The objective strip is read from PGM's own sidebar rows, so nothing can be invented. */
public class ObjectivesTest {
    private static final List<String> TEAMS=Arrays.asList("red","blue");

    /** What the named strip shows for a row: everything except the compact-only markers. */
    private static List<Objectives.Part> one(String row){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row),TEAMS);
        assertEquals("row was not read as an objective: "+row,1,entries.size());
        List<Objectives.Part> named=new ArrayList<>();
        for(Objectives.Part part:entries.get(0).parts)if(!part.beside)named.add(part);
        return named;
    }

    private static String teamOf(String row){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row),TEAMS);
        return entries.isEmpty()?null:entries.get(0).team;
    }

    @Test public void compactWoolRowsBecomeColouredSquares(){
        // PGM's compact Capture the Wool row: one state glyph per wool, each in the wool's colour.
        List<Objectives.Part> parts=one(" \u00a7c\u2B1C \u00a79\u2592 \u00a7e\u2B1B");
        assertEquals(3,parts.size());
        for(Objectives.Part part:parts)assertNull(part.text);
        assertEquals(TeamBar.chatColour('c'),parts.get(0).colour);
        assertEquals(TeamBar.chatColour('9'),parts.get(1).colour);
        assertEquals(TeamBar.chatColour('e'),parts.get(2).colour);
        assertEquals(0,parts.get(0).state);
        assertEquals(1,parts.get(1).state);
        assertEquals(2,parts.get(2).state);
    }

    @Test public void aWoolPutsItsSquareFirstThenItsName(){
        // The same order PGM's own sidebar prints: the wool's square, then its name.
        List<Objectives.Part> parts=one(" \u00a7f\u2B1C \u00a7cRed Wool");
        assertEquals(2,parts.size());
        assertNull("a named wool lost its square",parts.get(0).text);
        assertEquals(0,parts.get(0).state);
        assertEquals(TeamBar.chatColour('f'),parts.get(0).colour);
        assertEquals("Red Wool",parts.get(1).text);
        assertEquals(TeamBar.WHITE,parts.get(1).colour);
    }

    @Test public void scoreRowsKeepPgmOrderWithTheTeamsOwnColour(){
        List<Objectives.Part> parts=one("\u00a7f5 \u00a7cRed");
        assertEquals("5",parts.get(0).text);
        assertEquals(TeamBar.WHITE,parts.get(0).colour);
        assertEquals("Red",parts.get(1).text);
        assertEquals(TeamBar.chatColour('c'),parts.get(1).colour);
        assertEquals("3/10",one("\u00a7f3/10 \u00a79Blue").get(0).text);
    }

    @Test public void aGoalIsItsProgressThenItsNameWithNoTickOrCross(){
        // PGM's tick and cross say nothing at this size, so only the name is drawn.
        List<Objectives.Part> done=one("\u00a7a\u2714 \u00a7fNorth Monument");
        assertEquals(1,done.size());
        assertEquals("North Monument",done.get(0).text);
        assertEquals(1,one("\u00a7c\u2715 \u00a7fSouth Monument").size());
        // Progress is text, not an icon, but it still leads the name, as PGM prints it.
        List<Objectives.Part> progress=one("\u00a7e50% \u00a7fSouth Monument");
        assertEquals("50%",progress.get(0).text);
        assertEquals(TeamBar.chatColour('e'),progress.get(0).colour);
        assertEquals("South Monument",progress.get(1).text);
    }

    @Test public void aLongGoalNameIsShortened(){
        List<Objectives.Part> parts=one("\u00a7c\u2715 \u00a7fThe Very Long Monument Name");
        assertEquals(1,parts.size());
        assertTrue(parts.get(0).text.length()<=14);
        assertTrue(parts.get(0).text.startsWith("The Very Long"));
    }

    @Test public void aTeamNameAboveItsGoalsIsKeptSoWoolsStayAttributable(){
        // PGM prints a bare team name above that team's goals; dropping it would leave the wool
        // squares with no way to tell whose they are.
        List<Objectives.Part> parts=one("\u00a7cRed");
        assertEquals(1,parts.size());
        assertEquals("Red",parts.get(0).text);
        assertEquals(TeamBar.chatColour('c'),parts.get(0).colour);
        // A goal name that merely starts with the team name is not a header.
        assertEquals(2,one("\u00a7f\u2B1C \u00a7cRed Wool").size());
    }

    @Test public void destroyableProgressIsAGoalNotAScore(){
        List<Objectives.Part> parts=one("\u00a7c0/2 \u00a7fNorth Monument");
        assertEquals("0/2",parts.get(0).text);
        assertTrue("progress must survive compact mode",parts.get(0).marker);
        assertEquals("North Monument",parts.get(1).text);
        List<Objectives.Part> both=one("\u00a7c1/2 50% \u00a7fSouth Monument");
        assertEquals("1/2 50%",both.get(0).text);
        assertEquals("South Monument",both.get(1).text);
    }

    @Test public void rowsThatAreNotObjectivesAreIgnored(){
        for(String row:new String[]{"Time Limit: 10:00","Online: 12/32","","\u00a77Match starting in 10 seconds","100 Rooms","\u00a7f3 \u00a7cUnknownTeam"}){
            assertTrue("read as an objective: "+row,Objectives.entries(Collections.singletonList(row),TEAMS).isEmpty());
        }
    }

    @Test public void boldDoesNotChangeTheColourAndResetGoesBackToWhite(){
        Objectives.Line bold=Objectives.line("\u00a7c\u00a7lRed");
        assertEquals(TeamBar.chatColour('c'),TeamBar.chatColour(bold.colourAt(0)));
        Objectives.Line reset=Objectives.line("\u00a7cRed\u00a7rWhite");
        assertEquals('c',reset.colourAt(0));
        assertEquals('f',reset.colourAt(3));
    }

    @Test public void goalsBelongToTheTeamNamedAboveThem(){
        // PGM prints a team name, then that team's goals: that is how a compact marker is attributed.
        List<Objectives.Entry> entries=Objectives.entries(
            Arrays.asList("\u00a7cRed"," \u00a7c\u2B1C \u00a79\u2592","\u00a7a\u2714 \u00a7fNorth Monument","\u00a79Blue","\u00a7f12 \u00a79Blue"),TEAMS);
        assertEquals(5,entries.size());
        assertEquals("red",entries.get(1).team);
        assertEquals("red",entries.get(2).team);
        // A score row names its own team, whatever header came before it.
        assertEquals("blue",entries.get(4).team);
    }

    @Test public void compactPutsEachTeamOnItsOwnSideOfTheClock(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7c\u2B1C",
            "\u00a79Blue"," \u00a79\u2592",
            "\u00a7aGreen"," \u00a7a\u2B1B"),Arrays.asList("red","blue","green"));
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);columns.put("green",2);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns);
        // Even columns go left, odd go right - the same rule the head columns follow. A third
        // team must not pile onto one side, or the clock ends up lopsided.
        assertEquals("red",rows.get(0).get(0).get(0).name);
        assertEquals("blue",rows.get(1).get(0).get(0).name);
        assertEquals("green",rows.get(0).get(1).get(0).name);
    }

    @Test public void compactKeepsOnlyMarkersOnTheirOwnTeamsSide(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7c\u2B1C \u00a79\u2592","\u00a7a\u2714 \u00a7fNorth Monument",
            "\u00a79Blue","\u00a7f12 \u00a79Blue"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns);
        // Red is on the left: two wool squares, plus the completed goal's tick beside them.
        assertEquals(2,rows.get(0).size());
        assertEquals(2,rows.get(0).get(0).size());
        for(Objectives.Part part:rows.get(0).get(0)){
            assertNull("a name leaked into compact mode",part.text);
            // A wool's tooltip is its team, since a compact square has no name of its own.
            assertEquals("red",part.name);
        }
        // A goal's tooltip is the goal, not the team.
        assertEquals("North Monument",rows.get(0).get(1).get(0).name);
        // Blue is on the right: its score number is the only marker on that row.
        assertEquals(1,rows.get(1).size());
        assertEquals(1,rows.get(1).get(0).size());
        assertEquals("12",rows.get(1).get(0).get(0).text);
        assertEquals("blue",rows.get(1).get(0).get(0).name);
    }

    @Test public void aCompactGoalKeepsItsNameForTheTooltip(){
        List<Objectives.Entry> entries=Objectives.entries(
            Collections.singletonList("\u00a7a\u2714 \u00a7fNorth Monument"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns);
        assertEquals("a completed goal vanished from compact mode",1,rows.get(0).size());
        Objectives.Part marker=rows.get(0).get(0).get(0);
        assertEquals("North Monument",marker.name);
        // The tick is the goal's state, so it is drawn beside the clock but never in the named strip.
        assertEquals("\u2714",marker.text);
        assertTrue(marker.beside);
    }
}
