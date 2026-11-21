package otl.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The objective strip is read from PGM's own sidebar rows, so nothing can be invented. */
public class ObjectivesTest {
    @Test public void stackedTextUsesOnlyKnownNumericTargets(){
        int previous=UtilitySettings.teamBarScoreStyle;
        try{
            UtilitySettings.teamBarScoreStyle=3;
            Objectives.Part score=one("\u00a7f100\u00a78/\u00a77200 \u00a7cRed").get(0);
            assertTrue(score.stacked);assertEquals("100/200",ChatRules.plain(score.text));
            assertFalse(one("5 Red").get(0).stacked);
            assertFalse(one("5/0 Red").get(0).stacked);
            Objectives.Part percent=one("55% Core").get(0);
            assertEquals("55%",percent.text);assertFalse(percent.stacked);
        }finally{UtilitySettings.teamBarScoreStyle=previous;}
    }
    @Test public void sharedFlagsStayCentredWhateverTheirState(){
        int previous=UtilitySettings.teamBarScoreStyle;
        try{
            UtilitySettings.teamBarScoreStyle=0;
            java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);columns.put("blue",1);
            java.util.Map<Character,String> colours=new java.util.HashMap<>();colours.put('c',"red");colours.put('9',"blue");
            for(boolean compact:new boolean[]{false,true}){
                for(boolean trackPoints:new boolean[]{false,true}){
                    Objectives.PointTracker points=trackPoints?new Objectives.PointTracker():null;
                    java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Collections.singletonList("\u00a79\u2794 \u00a7fFootball"),TEAMS,points,colours);
                    java.util.List<java.util.List<java.util.List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                    // Blue carries it, and it still sits under the clock: the row never changes sides.
                    assertTrue("A shared flag left the centre",sides.get(0).isEmpty()&&sides.get(1).isEmpty());
                    assertEquals(1,sides.get(4).size());
                    Objectives.Part arrow=sides.get(4).get(0).get(0);
                    assertEquals("\u2794",arrow.text);assertEquals("Football",arrow.name);
                    if(!compact)assertEquals("Football",sides.get(4).get(0).get(1).text);
                }
            }
            java.util.List<Objectives.Entry> unknown=Objectives.entries(java.util.Collections.singletonList("\u00a7e\u2794 \u00a7fFootball"),TEAMS,null,colours);
            assertTrue(unknown.get(0).shared);
            java.util.List<Objectives.Entry> left=Objectives.entries(java.util.Collections.singletonList("\u00a7c\u2794 \u00a7fFootball"),TEAMS,null,colours);
            java.util.List<java.util.List<java.util.List<Objectives.Part>>> leftSides=Objectives.split(left,columns,true);
            assertEquals(1,leftSides.get(4).size());
            Objectives.Part leftArrow=leftSides.get(4).get(0).get(0);
            assertEquals("\u2794",leftArrow.text);assertFalse(leftArrow.mirror);
            java.util.List<Objectives.Entry> returned=Objectives.entries(java.util.Collections.singletonList("\u00a79\u2691 \u00a7fFootball"),TEAMS,null,colours);
            assertTrue(returned.get(0).shared);
        }finally{UtilitySettings.teamBarScoreStyle=previous;}
    }
    @Test public void carriedFlagArrowsMirrorOnTheRightWithoutMovingTheirTeam(){
        int previous=UtilitySettings.teamBarScoreStyle;
        try{
            UtilitySettings.teamBarScoreStyle=0;
            java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);columns.put("blue",1);
            java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Arrays.asList(
                "Red","\u00a7c\u2794 \u00a7fRed Flag","Blue","\u00a79\u2794 \u00a7fBlue Flag"),TEAMS);
            for(boolean compact:new boolean[]{false,true}){
                java.util.List<java.util.List<java.util.List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                java.util.List<Objectives.Part> left=sides.get(0).get(compact?0:1),right=sides.get(1).get(compact?0:1);
                Objectives.Part leftArrow=left.get(compact?0:1),rightArrow=right.get(0);
                assertEquals("\u2794",leftArrow.text);
                assertEquals("\u2794",rightArrow.text);
                assertFalse(leftArrow.mirror);
                assertTrue(rightArrow.mirror);
                assertEquals("Blue Flag",rightArrow.name);
                assertEquals(TeamBar.chatColour('9'),rightArrow.colour);
                if(!compact)assertEquals("Blue Flag",right.get(1).text);
            }
        }finally{UtilitySettings.teamBarScoreStyle=previous;}
    }
    private static final List<String> TEAMS=Arrays.asList("red","blue");

    @Test public void compactMarkersMirrorAboutTheClock(){
        // A team's score and its flag share one compact line. The left line is right-aligned
        // against the clock, so its last marker hugs the time; the right line must lead with that
        // same marker instead of the score, or the two sides read differently.
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);columns.put("blue",1);
        java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Arrays.asList(
            "\u00a7cRed","\u00a7f0\u00a78/\u00a7f3 \u00a7cRed","\u00a7c\u2690 \u00a7fFlag",
            "\u00a79Blue","\u00a7f1\u00a78/\u00a7f3 \u00a79Blue","\u00a79\u2690 \u00a7fFlag"),TEAMS);
        java.util.List<java.util.List<Objectives.Entry>> sides=Objectives.splitEntries(entries,columns,true);
        java.util.List<Objectives.Part> left=markers(Objectives.compactLines(sides.get(0),TEAMS,false));
        java.util.List<Objectives.Part> right=markers(Objectives.compactLines(sides.get(1),TEAMS,true));
        assertEquals("\u2690",left.get(left.size()-1).text);
        assertEquals("\u2690",right.get(0).text);
        assertFalse(left.get(0).text.equals("\u2690"));
    }

    /** Flattens one side's compact lines into the parts of its first line. */
    private static List<Objectives.Part> markers(List<List<List<Objectives.Part>>> lines){
        List<Objectives.Part> parts=new java.util.ArrayList<>();
        for(List<Objectives.Part> row:lines.get(0))parts.addAll(row);
        return parts;
    }

    @Test public void blinkingSharedArrowKeepsCarrierAndNameColour(){
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        java.util.Map<Character,String> colours=new java.util.HashMap<>();colours.put('9',"blue");colours.put('0',"red");
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true}){
            tracker.clear();
            Objectives.entries(Collections.singletonList("\u00a79\u2794 \u00a7fFlag"),TEAMS,null,colours,tracker);
            List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList("\u00a70\u2794 \u00a7fFlag"),TEAMS,null,colours,tracker);
            assertEquals("blue",entries.get(0).team);
            // The row stays centred, but the blinking arrow still takes the last carrier's colour.
            List<Objectives.Part> row=Objectives.split(entries,columns,compact).get(4).get(0);
            Objectives.Part arrow=row.get(0);
            assertEquals("\u2794",arrow.text);assertEquals(TeamBar.chatColour('0'),arrow.colour);
            if(!compact){assertEquals("Flag",row.get(1).text);assertEquals(TeamBar.WHITE,row.get(1).colour);}
        }
    }

    @Test public void sharedFlagKeepsLastConfirmedCarrierUntilAnotherTeamTakesIt(){
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        java.util.Map<Character,String> colours=new java.util.HashMap<>();colours.put('c',"red");colours.put('9',"blue");
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true}){
            String[] rows={"\u00a79\u2794 \u00a7fFlag","\u00a77\u2794 \u00a7fFlag","\u00a77 10 Flag","\u00a77\u2690 Flag","\u00a77\u2691 Flag","\u00a7c\u2794 \u00a7fFlag","\u00a77 5 Flag","\u00a77\u2691 Flag"};
            String[] teams={"blue","blue","blue","blue","blue","red","red","red"};
            for(int i=0;i<rows.length;i++){
                List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(rows[i]),TEAMS,null,colours,tracker);
                assertEquals(teams[i],entries.get(0).team);
                List<List<List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                // The carrier is remembered, but the row itself never leaves the centre.
                assertEquals(1,sides.get(4).size());
                assertTrue(sides.get(0).isEmpty()&&sides.get(1).isEmpty());
            }
            tracker.clear();
            assertNull(Objectives.entries(Collections.singletonList("10 Flag"),TEAMS,null,colours,tracker).get(0).team);
        }
    }

    @Test public void severalSharedFlagsStackInTheCentre(){
        java.util.Map<Character,String> colours=new java.util.HashMap<>();
        colours.put('c',"red");colours.put('9',"blue");
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        // A CTF sidebar lists every flag in one shared section; red is carrying two of them.
        java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Arrays.asList(
            "\u00a7c\u2794 \u00a7fRed Flag","\u00a7c\u2794 \u00a7fBlue Flag","\u00a79\u2794 \u00a7fGreen Flag"),TEAMS,null,colours);
        for(boolean compact:new boolean[]{false,true}){
            java.util.List<java.util.List<java.util.List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
            assertTrue("A shared flag left the centre",sides.get(0).isEmpty()&&sides.get(1).isEmpty());
            assertEquals(3,sides.get(4).size());
            // Every flag keeps its own row, each with its arrow first.
            for(java.util.List<Objectives.Part> row:sides.get(4))
                assertEquals("\u2794",row.get(0).text);
        }
    }

    @Test public void compactMarkersStayGroupedByTheirOwnTeam(){
        // Four teams: red and green share the left column. Each keeps its own group of markers so
        // one team's icons never end up on the line that belongs to the block above it. The sidebar
        // prints green first here, because PGM sorts it by score; the bar's own order still wins.
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);columns.put("green",2);columns.put("yellow",3);
        java.util.List<String> teams=Arrays.asList("red","blue","green","yellow");
        java.util.List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7aGreen"," \u00a7a\u2B1C \u00a79\u2592",
            "\u00a7cRed"," \u00a7c\u2B1C \u00a79\u2592 \u00a7e\u2B1B"),teams);
        java.util.List<java.util.List<Objectives.Entry>> sides=Objectives.splitEntries(entries,columns,true);
        assertTrue("Blue's markers left their side",sides.get(1).isEmpty());
        java.util.List<java.util.List<java.util.List<Objectives.Part>>> groups=Objectives.compactGroups(sides.get(0),teams);
        assertEquals("Two teams on one side did not stay apart",2,groups.size());
        assertEquals("Red's markers did not stay under red's heads",3,groups.get(0).get(0).size());
        assertEquals(2,groups.get(1).get(0).size());
        // One team keeps the old single group, so a two-team map is laid out exactly as before.
        assertEquals(1,Objectives.compactGroups(java.util.Collections.singletonList(entries.get(1)),teams).size());
    }

    @Test public void compactMarkersKeepSmallLayoutAndBalanceLargerGroupsIntoExactlyTwoRows(){
        for(int count=0;count<=12;count++){
            List<List<Objectives.Part>> entries=new java.util.ArrayList<>();
            for(int i=0;i<count;i++)entries.add(Collections.singletonList(new Objectives.Part(null,TeamBar.WHITE,0,true,"Goal "+i)));
            List<List<List<Objectives.Part>>> rows=Objectives.compactRows(entries);
            assertEquals(count>3?2:1,rows.size());
            if(count<=3)assertSame(entries,rows.get(0));
            List<Objectives.Part> recovered=new java.util.ArrayList<>();
            for(List<List<Objectives.Part>> row:rows)for(List<Objectives.Part> entry:row)recovered.addAll(entry);
            assertEquals(count,recovered.size());
            for(int i=0;i<count;i++)assertEquals("Goal "+i,recovered.get(i).name);
            if(count>3){
                assertEquals((count+1)/2,rows.get(0).get(0).size());
                assertEquals(count/2,rows.get(1).get(0).size());
            }
        }
        List<Objectives.Part> wools=Arrays.asList(new Objectives.Part(null,TeamBar.WHITE,0,true),
            new Objectives.Part(null,TeamBar.WHITE,1,true),new Objectives.Part(null,TeamBar.WHITE,2,true),
            new Objectives.Part(null,TeamBar.WHITE,0,true));
        assertEquals(2,Objectives.compactRows(Collections.singletonList(wools)).size());
    }

    /** Parsed state and name before left/right mirroring. */
    private static List<Objectives.Part> one(String row){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row),TEAMS);
        assertEquals("row was not read as an objective: "+row,1,entries.size());
        return entries.get(0).parts;
    }

    private static String teamOf(String row){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row),TEAMS);
        return entries.isEmpty()?null:entries.get(0).team;
    }

    @Test public void sharedFlagStatesAndCountdownFollowTheSidebarInBothLayouts(){
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true})for(String status:new String[]{"\u2690","\u2691","\u2794","30","4"}){
            List<List<List<Objectives.Part>>> sides=Objectives.split(Objectives.entries(
                Collections.singletonList("\u00a77"+status+" \u00a7fFlag"),TEAMS),columns,compact);
            assertTrue(sides.get(0).isEmpty());
            assertTrue(sides.get(1).isEmpty());
            assertEquals(1,sides.get(4).size());
            assertEquals(status,sides.get(4).get(0).get(0).text);
            assertEquals("Flag",sides.get(4).get(0).get(1).text);
        }
        assertFalse(Objectives.entries(Collections.singletonList("10 Red"),TEAMS).get(0).shared);
        assertFalse(Objectives.entries(Collections.singletonList("\u2714 Monument"),TEAMS).get(0).shared);
        Objectives.ScoreTracker tracker=new Objectives.ScoreTracker();
        Objectives.entries(Collections.singletonList("\u2691 Crown"),TEAMS,null,Collections.<Character,String>emptyMap(),tracker);
        assertTrue(Objectives.entries(Collections.singletonList("10 Crown"),TEAMS,null,Collections.<Character,String>emptyMap(),tracker).get(0).shared);
        tracker.clear();
        assertTrue(Objectives.entries(Collections.singletonList("10 Crown"),TEAMS,null,Collections.<Character,String>emptyMap(),tracker).isEmpty());
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
        assertEquals(TeamBar.chatColour('c'),parts.get(1).colour);
    }

    @Test public void scoreRowsKeepPgmOrderWithTheTeamsOwnColour(){
        List<Objectives.Part> parts=one("\u00a7f5 \u00a7cRed");
        assertEquals("5",parts.get(0).text);
        assertEquals(TeamBar.WHITE,parts.get(0).colour);
        assertEquals("Red",parts.get(1).text);
        assertEquals(TeamBar.chatColour('c'),parts.get(1).colour);
        assertEquals("3/10",one("\u00a7f3/10 \u00a79Blue").get(0).text);
    }

    @Test public void numericTeamScoresUseTheirExplicitTargetForBars(){
        int previous=UtilitySettings.teamBarScoreStyle;
        try{
            for(int style=1;style<=2;style++){
                UtilitySettings.teamBarScoreStyle=style;
                for(boolean compact:new boolean[]{false,true}){
                    java.util.Map<String,Integer> columns=new java.util.HashMap<>();
                    columns.put("red",0);columns.put("blue",1);
                    List<List<List<Objectives.Part>>> rows=Objectives.split(Objectives.entries(Arrays.asList(
                        "\u00a7f0\u00a78/\u00a77200 \u00a7cRed", "\u00a7f100/200 \u00a79Blue"),TEAMS),columns,compact);
                    Objectives.Part red=rows.get(0).get(0).get(compact?0:1),blue=rows.get(1).get(0).get(0);
                    assertEquals(0F,red.fraction,0.001F);
                    assertEquals(0.5F,blue.fraction,0.001F);
                    assertEquals("Red 0/200",red.name);
                    assertEquals(TeamBar.chatColour('c'),red.colour);
                    assertNull(red.text);
                }
                assertEquals(-1F,one("5 Red").get(0).fraction,0F);
                assertEquals(-1F,one("5/0 Red").get(0).fraction,0F);
                assertEquals(1F,one("250/200 Red").get(0).fraction,0F);
            }
            UtilitySettings.teamBarScoreStyle=0;
            assertEquals("0/200",one("0/200 Red").get(0).text);
        }finally{UtilitySettings.teamBarScoreStyle=previous;}
    }

    @Test public void percentageObjectivesFollowTheDisplayChoiceAndKeepExactHover(){
        int previous=UtilitySettings.teamBarScoreStyle;
        try{
            for(int style=1;style<=2;style++){
                UtilitySettings.teamBarScoreStyle=style;
                for(boolean compact:new boolean[]{false,true}){
                    java.util.Map<String,Integer> columns=new java.util.HashMap<>();columns.put("red",0);
                    java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Arrays.asList(
                        "Red","\u00a7e55% \u00a7755/100 \u00a7cCore"),TEAMS);
                    java.util.List<java.util.List<java.util.List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                    java.util.List<Objectives.Part> row=sides.get(0).get(compact?0:1);
                    Objectives.Part bar=row.get(compact?0:1);
                    assertNull(bar.text);
                    assertEquals(.55F,bar.fraction,0.001F);
                    assertEquals("Core 55% 55/100",bar.name);
                    assertEquals(TeamBar.chatColour('e'),bar.colour);
                    Objectives.Strip strip=new Objectives.Strip(java.util.Collections.singletonList(bar),24);
                    assertEquals(.55F,Objectives.packPointLines(java.util.Collections.singletonList(strip),40).get(0).parts.get(0).fraction,0.001F);
                }
                assertEquals(1F,one("999% Core").get(0).fraction,0F);
                assertEquals(-1F,one("\u2714 Core").get(0).fraction,0F);
            }
            UtilitySettings.teamBarScoreStyle=0;
            assertEquals("55%",one("55% Core").get(0).text);
        }finally{UtilitySettings.teamBarScoreStyle=previous;}
    }

    @Test public void pgmScoreKeepsWhiteCurrentDarkGreySlashAndGreyLimitInBothModes(){
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true}){
            List<List<List<Objectives.Part>>> sides=Objectives.split(Objectives.entries(Arrays.asList(
                "\u00a7f340\u00a78/\u00a77750 \u00a7cRed",
                "\u00a7f254 \u00a78/\u00a77750 \u00a79Blue"),TEAMS),columns,compact);
            Objectives.Part left=sides.get(0).get(0).get(compact?0:1),right=sides.get(1).get(0).get(0);
            assertEquals("340\u00a78/\u00a77750",left.text);
            assertEquals("254 \u00a78/\u00a77750",right.text);
            assertEquals(TeamBar.WHITE,left.colour);assertEquals(TeamBar.WHITE,right.colour);
            if(!compact){
                assertEquals(TeamBar.chatColour('c'),sides.get(0).get(0).get(0).colour);
                assertEquals(TeamBar.chatColour('9'),sides.get(1).get(0).get(1).colour);
            }
        }
        assertEquals("1234\u00a78/\u00a771500",one("\u00a7f1234\u00a78/\u00a771500 \u00a7cRed").get(0).text);
    }

    @Test public void objectiveNamesAndProgressKeepColourChangesInsideTheirText(){
        List<Objectives.Part> parts=one("\u00a7e50% \u00a7712% \u00a7cNorth \u00a79Monument");
        assertEquals("50% \u00a7712%",parts.get(0).text);
        assertEquals(TeamBar.chatColour('e'),parts.get(0).colour);
        assertEquals("North \u00a79Monument",parts.get(1).text);
        assertEquals(TeamBar.chatColour('c'),parts.get(1).colour);
    }

    @Test public void aGoalKeepsTheScoreboardsIconAndName(){
        List<Objectives.Part> done=one("\u00a7a\u2714 \u00a7fNorth Monument");
        assertEquals(2,done.size());
        assertEquals("\u2714",done.get(0).text);
        assertEquals(TeamBar.chatColour('a'),done.get(0).colour);
        assertEquals("North Monument",done.get(1).text);
        assertEquals("\u2715",one("\u00a7c\u2715 \u00a7fSouth Monument").get(0).text);
        // Progress is text, not an icon, but it still leads the name, as PGM prints it.
        List<Objectives.Part> progress=one("\u00a7e50% \u00a7fSouth Monument");
        assertEquals("50%",progress.get(0).text);
        assertEquals(TeamBar.chatColour('e'),progress.get(0).colour);
        assertEquals("South Monument",progress.get(1).text);
    }

    @Test public void aDestroyableKeepsEveryStateItShowsBeforeItIsFinished(){
        // PGM's touch glyph: a monument someone has broken blocks of but not destroyed.
        List<Objectives.Part> touched=one("\u00a7e\u2733 \u00a7fNorth Monument");
        assertEquals(2,touched.size());
        assertEquals("\u2733",touched.get(0).text);
        assertTrue(touched.get(0).marker);
        assertEquals(TeamBar.chatColour('e'),touched.get(0).colour);
        assertEquals("North Monument",touched.get(1).text);
        // Progress shown as a percentage plus the precise breaks: both belong to the state, not the name.
        List<Objectives.Part> precise=one("\u00a7e55% \u00a7755/100 \u00a7fNorth Monument");
        assertEquals("55% \u00a7755/100",precise.get(0).text);
        assertEquals("North Monument",precise.get(1).text);
        assertTrue(precise.get(0).marker);
        List<Objectives.Part> fraction=one("\u00a7e55% \u00a7711/20 \u00a7fSouth Monument");
        assertEquals("55% \u00a7711/20",fraction.get(0).text);
        assertEquals("South Monument",fraction.get(1).text);
    }

    @Test public void aLongGoalNameIsKeptInFull(){
        List<Objectives.Part> parts=one("\u00a7c\u2715 \u00a7fThe Very Long Monument Name");
        assertEquals(2,parts.size());
        assertEquals("The Very Long Monument Name",parts.get(1).text);
    }

    @Test public void iconChangesReachBothModesFromTheLatestScoreboardRows(){
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true}){
            for(String icon:new String[]{"\u2715","\u2714","\u2715"}){
                String colour="\u2714".equals(icon)?"\u00a7a":"\u00a7c";
                List<List<List<Objectives.Part>>> rows=Objectives.split(Objectives.entries(
                    Arrays.asList("Blue",colour+icon+" Red Monument"),TEAMS),columns,compact);
                Objectives.Part marker=rows.get(1).get(compact?0:1).get(0);
                assertEquals(icon,marker.text);
                assertEquals(TeamBar.chatColour(colour.charAt(1)),marker.colour);
                assertTrue(marker.marker);
            }
        }
    }

    @Test public void multipleObjectivesStayInTheirScoreboardBlocksInEitherColumnOrder(){
        for(boolean compact:new boolean[]{false,true})for(boolean reversed:new boolean[]{false,true}){
            java.util.Map<String,Integer> columns=new java.util.HashMap<>();
            columns.put("red",reversed?1:0);columns.put("blue",reversed?0:1);
            // Name/status colours contradict the team headers deliberately.
            List<Objectives.Entry> goals=Objectives.entries(Arrays.asList(
                "Red", "\u00a7e74% \u00a79North Monument", "\u00a7a100% \u00a79South Monument",
                "Blue", "\u00a7c0% \u00a7cNorth Monument", "\u00a7c0% \u00a7cSouth Monument"),TEAMS);
            assertEquals(6,goals.size());
            assertEquals("red",goals.get(1).team);assertEquals("red",goals.get(2).team);
            assertEquals("blue",goals.get(4).team);assertEquals("blue",goals.get(5).team);
            List<List<List<Objectives.Part>>> sides=Objectives.split(goals,columns,compact);
            assertEquals(compact?2:3,sides.get(0).size());assertEquals(compact?2:3,sides.get(1).size());
            for(int side=0;side<2;side++){
                boolean red=side==columns.get("red");
                for(List<Objectives.Part> row:sides.get(side).subList(compact?0:1,sides.get(side).size())){
                    Objectives.Part marker=row.get(compact||side==1?0:1);
                    assertEquals(red?("North Monument".equals(marker.name)?"74%":"100%"):"0%",marker.text);
                }
            }
            List<Objectives.Entry> changed=Objectives.entries(Arrays.asList(
                "Red", "\u00a7a\u2714 \u00a79North Monument", "Blue", "\u00a7c\u2715 \u00a7cNorth Monument"),TEAMS);
            assertEquals("red",changed.get(1).team);assertEquals("blue",changed.get(3).team);
        }
    }

    @Test public void scoreboardHeadersOverrideContradictoryMonumentColours(){
        List<Objectives.Entry> goals=Objectives.entries(Arrays.asList(
            "Red", "\u00a7a100% \u00a79North Monument", "\u00a7c0% \u00a79South Monument",
            "Blue", "\u00a7e74% \u00a7cNorth Monument", "\u00a7a\u2714 \u00a7cSouth Monument"),TEAMS);
        assertEquals("red",goals.get(1).team);assertEquals("red",goals.get(2).team);
        assertEquals("blue",goals.get(4).team);assertEquals("blue",goals.get(5).team);
    }

    @Test public void namedMonumentsKeepScoreboardIconsOnBothMirroredSides(){
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        List<List<List<Objectives.Part>>> rows=Objectives.split(Objectives.entries(Arrays.asList(
            "Red", "\u00a7c\u2715 Red Monument", "Blue", "\u00a7a\u2714 Blue Monument"),TEAMS),columns,false);
        List<Objectives.Part> left=rows.get(0).get(1),right=rows.get(1).get(1);
        assertEquals("Red Monument",left.get(0).text);
        assertEquals("\u2715",left.get(1).text);
        assertEquals(TeamBar.chatColour('c'),left.get(1).colour);
        assertEquals("\u2714",right.get(0).text);
        assertEquals(TeamBar.chatColour('a'),right.get(0).colour);
        assertEquals("Blue Monument",right.get(1).text);
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
            Arrays.asList("\u00a7cRed"," \u00a7c\u2B1C \u00a79\u2592","\u00a7a\u2714 \u00a7cNorth Monument","\u00a79Blue","\u00a7f12 \u00a79Blue"),TEAMS);
        assertEquals(5,entries.size());
        assertEquals("red",entries.get(1).team);
        assertEquals("red",entries.get(2).team);
        // A score row names its own team, whatever header came before it.
        assertEquals("blue",entries.get(4).team);
    }

    @Test public void goalsBeforeTheFirstTeamHeaderAreNotAssignedToAFutureBlock(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7f\u2B1C \u00a7cRed Wool","\u00a7cRed",
            "\u00a79Blue","\u00a7f\u2592 \u00a79Blue Wool"),TEAMS);
        assertEquals(4,entries.size());
        assertNull(entries.get(0).team);
        // A row under a header still takes that header, not the one below it.
        assertEquals("blue",entries.get(3).team);
    }

    @Test public void aRowWithNoTeamAtAllStaysUnattributed(){
        // Nothing in this sidebar names a team, so nothing can say whose the goals are; they must
        // not silently pile onto one side.
        List<Objectives.Entry> entries=Objectives.entries(
            Collections.singletonList("\u00a7a\u2714 \u00a7fNorth Monument"),TEAMS);
        assertEquals(1,entries.size());
        assertNull(entries.get(0).team);
    }

    @Test public void compactPutsEachTeamOnItsOwnSideOfTheClock(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7c\u2B1C",
            "\u00a79Blue"," \u00a79\u2592",
            "\u00a7aGreen"," \u00a7a\u2B1B"),Arrays.asList("red","blue","green"));
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);columns.put("green",2);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,true);
        // Even columns go left, odd go right - the same rule the head columns follow. A third
        // team must not pile onto one side, or the clock ends up lopsided.
        assertEquals("red",rows.get(0).get(0).get(0).name);
        assertEquals("blue",rows.get(1).get(0).get(0).name);
        assertEquals("green",rows.get(0).get(1).get(0).name);
    }

    @Test public void aTeamsObjectivesFollowThatTeamsOwnColumn(){
        // The bar puts a team's heads on a side from its column; the objectives must match, or a
        // team's goals end up beside the other team's row.
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7c\u2B1C",
            "\u00a79Blue"," \u00a79\u2592"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        // Blue is the first column here, so blue's goals go left and red's go right.
        columns.put("blue",0);columns.put("red",1);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,true);
        assertEquals("blue",rows.get(0).get(0).get(0).name);
        assertEquals("red",rows.get(1).get(0).get(0).name);
    }

    @Test public void monumentNamesNeverOverrideTheirScoreboardHeader(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "Red", "\u2714 Blue Monument", "\u2715 Red Monument"),TEAMS);
        assertEquals("red",entries.get(1).team);
        assertEquals("red",entries.get(2).team);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("blue",0);columns.put("red",1);
        List<List<List<Objectives.Part>>> compact=Objectives.split(entries,columns,true);
        assertTrue(compact.get(0).isEmpty());
        assertEquals("Blue Monument",compact.get(1).get(0).get(0).name);
        assertEquals("Red Monument",compact.get(1).get(1).get(0).name);
        assertEquals("blue",Objectives.entries(Arrays.asList("Blue","\u2714 Redstone Vault"),TEAMS).get(1).team);
    }

    @Test public void theIconsHugTheClockOnBothSides(){
        // The left strip is right-aligned against the clock and the right one left-aligned, so a
        // mirrored order puts the state markers nearest the time on both sides.
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7f\u2B1C \u00a7cRed Wool",
            "\u00a79Blue"," \u00a7f\u2592 \u00a79Blue Wool"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,false);
        // Row 0 is the team header, row 1 the wool itself.
        List<Objectives.Part> left=rows.get(0).get(1);
        assertEquals("Red Wool",left.get(0).text);
        assertNull("the left wool lost its square",left.get(1).text);
        // Right: marker then name, mirroring it.
        List<Objectives.Part> right=rows.get(1).get(1);
        assertNull("the right wool lost its square",right.get(0).text);
        assertEquals("Blue Wool",right.get(1).text);
    }

    @Test public void compactKeepsOnlyMarkersOnTheirOwnTeamsSide(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7cRed"," \u00a7c\u2B1C \u00a79\u2592","\u00a7a\u2714 \u00a7cNorth Monument",
            "\u00a79Blue","\u00a7f12 \u00a79Blue"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,true);
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
            Arrays.asList("Red","\u00a7a\u2714 \u00a7cNorth Monument"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);
        List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,true);
        assertEquals("a completed goal vanished from compact mode",1,rows.get(0).size());
        Objectives.Part marker=rows.get(0).get(0).get(0);
        assertEquals("North Monument",marker.name);
        // The scoreboard's actual tick survives in both modes.
        assertEquals("\u2714",marker.text);
    }

    @Test public void privateMapWithoutOwnershipEvidenceDoesNotDumpGoalsOnTheLeft(){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList("\u00a7c0% \u00a7fMonument"),TEAMS);
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        for(boolean compact:new boolean[]{false,true}){
            List<List<List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
            assertTrue(sides.get(0).isEmpty());assertTrue(sides.get(1).isEmpty());
        }
    }

    @Test public void screenshotBlueGetsRedWoolAndRedGetsBlueWoolInBothModes(){
        java.util.Map<String,Integer> columns=new java.util.HashMap<>();
        columns.put("red",0);columns.put("blue",1);
        for(int state=0;state<3;state++)for(boolean compact:new boolean[]{false,true}){
            List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
                "\u00a79Blue", "\u00a7c"+Objectives.WOOL.charAt(state)+" \u00a7fRed Wool",
                "\u00a7cRed", "\u00a79"+Objectives.WOOL.charAt(0)+" \u00a7fBlue Wool"),TEAMS);
            assertEquals("blue",entries.get(1).team);assertEquals("red",entries.get(3).team);
            List<List<List<Objectives.Part>>> rows=Objectives.split(entries,columns,compact);
            List<Objectives.Part> red=rows.get(0).get(compact?0:1),blue=rows.get(1).get(compact?0:1);
            Objectives.Part redMarker=red.get(compact?0:1),blueMarker=blue.get(0);
            assertEquals("Blue Wool",redMarker.name);assertEquals("Red Wool",blueMarker.name);
            assertEquals(TeamBar.chatColour('9'),redMarker.colour);
            assertEquals(TeamBar.chatColour('c'),blueMarker.colour);
            assertEquals(0,redMarker.state);assertEquals(state,blueMarker.state);
            if(!compact){assertEquals("Blue Wool",red.get(0).text);assertEquals("Red Wool",blue.get(1).text);}
        }
    }

    @Test public void livePgmPrefixedTeamNamesKeepAllObjectivesInBothModes(){
        for(String prefix:new String[]{"\u00ba","\u00a7"}){
            String red=prefix+"cRed",blue=prefix+"9Blue";
            List<String> teams=Arrays.asList(red,blue);
            java.util.Map<String,Integer> columns=new java.util.HashMap<>();
            columns.put(MapInfo.norm(red),0);columns.put(MapInfo.norm(blue),1);
            List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
                "\u00a79Blue", "\u00a7c\u2592 \u00a7fRed Wool", "\u00a7e74% \u00a7fNorth Monument",
                "\u00a7cRed", "\u00a79\u2B1C \u00a7fBlue Wool", "\u00a7a\u2714 \u00a7fSouth Monument"),teams);
            assertEquals(6,entries.size());
            assertEquals(blue,entries.get(1).team);assertEquals(blue,entries.get(2).team);
            assertEquals(red,entries.get(4).team);assertEquals(red,entries.get(5).team);
            for(boolean compact:new boolean[]{false,true}){
                List<List<List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
                assertEquals(compact?2:3,sides.get(0).size());
                assertEquals(compact?2:3,sides.get(1).size());
                List<Objectives.Part> redWool=sides.get(1).get(compact?0:1);
                assertEquals("Red Wool",redWool.get(0).name);
                assertEquals(1,redWool.get(0).state);
            }
        }
        assertTrue(Objectives.sameTeam("Marley","\u00bacMarley"));
        assertTrue(Objectives.sameTeam("Eldia","\u00ba9Eldia"));
        assertFalse(Objectives.sameTeam("Blue Wool","\u00ba9Blue"));
    }
}
