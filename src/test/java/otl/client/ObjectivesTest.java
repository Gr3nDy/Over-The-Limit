package otl.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The objective strip is read from PGM's own sidebar rows, so nothing can be invented. */
public class ObjectivesTest {
    private static final List<String> TEAMS=Arrays.asList("red","blue");

    private static List<Objectives.Part> one(String row){
        List<List<Objectives.Part>> entries=Objectives.entries(Collections.singletonList(row),TEAMS);
        assertEquals("row was not read as an objective: "+row,1,entries.size());
        return entries.get(0);
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

    @Test public void aWoolWithItsNameStaysText(){
        List<Objectives.Part> parts=one(" \u00a7f\u2B1C \u00a7cRed Wool");
        assertEquals(2,parts.size());
        assertEquals("\u2B1C",parts.get(0).text);
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

    @Test public void goalRowsShowTheirStatusAndName(){
        List<Objectives.Part> parts=one("\u00a7a\u2714 \u00a7fNorth Monument");
        assertEquals("\u2714",parts.get(0).text);
        assertEquals(TeamBar.chatColour('a'),parts.get(0).colour);
        assertEquals("North Monument",parts.get(1).text);
        assertEquals("50%",one("\u00a7e50% \u00a7fSouth Monument").get(0).text);
    }

    @Test public void aLongGoalNameIsShortened(){
        List<Objectives.Part> parts=one("\u00a7c\u2715 \u00a7fThe Very Long Monument Name");
        assertTrue(parts.get(1).text.length()<=14);
        assertTrue(parts.get(1).text.startsWith("The Very Long"));
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
        assertEquals("North Monument",parts.get(1).text);
        assertEquals("1/2 50%",one("\u00a7c1/2 50% \u00a7fSouth Monument").get(0).text);
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
}
