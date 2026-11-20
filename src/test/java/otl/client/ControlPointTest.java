package otl.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ControlPointTest {
    private static final List<String> TEAMS=Arrays.asList("red","blue");

    @Test public void fourTeamHillOwnersUseTheScoreboardTeamColours(){
        List<String> teams=Arrays.asList("Red","Blue","Green","Yellow");
        List<String> rows=Arrays.asList("\u00a7f120/3000 \u00a74Red", "\u00a7f90/3000 \u00a79Blue",
            "\u00a7f80/3000 \u00a72Green", "\u00a7f70/3000 \u00a7eYellow");
        Map<Character,String> colours=new HashMap<>();
        java.util.Set<Character> ambiguous=new java.util.HashSet<>();
        java.util.Set<String> scored=new java.util.HashSet<>();
        Objectives.scoreTeamColours(rows,teams,colours,ambiguous,scored);
        assertEquals("Red",colours.get('4'));assertEquals("Blue",colours.get('9'));
        assertEquals("Green",colours.get('2'));assertEquals("Yellow",colours.get('e'));
        Objectives.PointTracker points=new Objectives.PointTracker();points.colours.putAll(colours);points.pointMode=true;
        List<Objectives.Entry> goals=Objectives.entries(Arrays.asList(
            "\u00a74\u29bf \u00a74Red Hill", "\u00a79\u29bf \u00a79Blue Hill",
            "\u00a72\u29bf \u00a72Green Hill", "\u00a7e\u29bf \u00a7eYellow Hill",
            "\u00a7490% \u00a7eMiddle"),teams,points,colours);
        Map<String,Integer> columns=TeamBar.teamColumns(teams,teams);
        for(boolean compact:new boolean[]{false,true}){
            List<List<List<Objectives.Part>>> sides=Objectives.split(goals,columns,compact);
            assertEquals(2,sides.get(2).size());
            assertEquals(3,sides.get(3).size());
            assertTrue("A right-team hill stayed neutral",sides.get(4).isEmpty());
        }
        assertEquals("Yellow",goals.get(4).team);
    }

    private Objectives.PointTracker tracker(){
        Objectives.PointTracker tracker=new Objectives.PointTracker();
        tracker.colours.put('c',"red");tracker.colours.put('9',"blue");
        return tracker;
    }

    private Objectives.Entry point(Objectives.PointTracker tracker,String row){
        List<Objectives.Entry> entries=Objectives.entries(Collections.singletonList(row),TEAMS,tracker);
        assertEquals(1,entries.size());
        assertTrue(entries.get(0).controlPoint);
        return entries.get(0);
    }

    @Test public void neutralProgressStaysCentralUntilLabelConfirmsCapture(){
        Objectives.PointTracker tracker=tracker();
        assertNull(point(tracker,"\u00a7f\u29be \u00a7fTop").team);
        assertNull(point(tracker,"\u00a7c25% \u00a7fTop").team);
        assertNull(point(tracker,"\u00a7c99% \u00a7fTop").team);
        assertEquals("red",point(tracker,"\u00a7c\u29bf \u00a7cTop").team);
        // Blue's capture-progress colour must not move a point still controlled by Red.
        assertEquals("red",point(tracker,"\u00a7910% \u00a7cTop").team);
        assertEquals("red",point(tracker,"\u00a7999% \u00a7cTop").team);
        assertEquals("blue",point(tracker,"\u00a79\u29bf \u00a79Top").team);
        assertNull(point(tracker,"\u00a7f\u29be \u00a7fTop").team);
    }

    @Test public void joiningMidCaptureWorksWhenScoreboardIdentifiesControlPointMode(){
        Objectives.PointTracker tracker=tracker();tracker.pointMode=true;
        assertNull(point(tracker,"\u00a7c44% \u00a7fCave").team);
        assertEquals("blue",point(tracker,"\u00a7c44% \u00a79Valley").team);
    }

    @Test public void multiplePointsUseLeftRightAndCentreInEitherCompactMode(){
        Objectives.PointTracker tracker=tracker();
        Map<String,Integer> columns=new HashMap<>();columns.put("blue",0);columns.put("red",1);
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "\u00a7f\u29be \u00a7fTop", "\u00a7c\u29bf \u00a7cCave",
            "\u00a79\u29bf \u00a79Valley", "\u00a7f\u29be \u00a7fBridge"),TEAMS,tracker);
        for(boolean compact:new boolean[]{false,true}){
            List<List<List<Objectives.Part>>> sides=Objectives.split(entries,columns,compact);
            assertTrue(sides.get(0).isEmpty());assertTrue(sides.get(1).isEmpty());
            assertEquals(1,sides.get(2).size());assertEquals(1,sides.get(3).size());assertEquals(2,sides.get(4).size());
            assertEquals("Valley",sides.get(2).get(0).get(0).text);
            assertEquals("Cave",sides.get(3).get(0).get(1).text);
            assertEquals("Top",sides.get(4).get(0).get(1).text);
        }
    }

    @Test public void neutralPointProgressLeansTowardTheCapturingTeamsOwnSide(){
        Objectives.PointTracker tracker=tracker();
        Map<String,Integer> columns=new HashMap<>();columns.put("blue",0);columns.put("red",1);
        // A percentage-only row is only a point once the name has been seen as one.
        Objectives.entries(Collections.singletonList("\u00a7f\u29be \u00a7fTop"),TEAMS,tracker);
        for(boolean compact:new boolean[]{false,true}){
            List<Objectives.Entry> blue=Objectives.entries(Collections.singletonList("\u00a7925% \u00a7fTop"),TEAMS,tracker);
            List<List<List<Objectives.Part>>> left=Objectives.split(blue,columns,compact);
            assertTrue(left.get(0).isEmpty()&&left.get(1).isEmpty()&&left.get(2).isEmpty()&&left.get(3).isEmpty());
            assertEquals(1,left.get(4).size());
            assertEquals("25%",left.get(4).get(0).get(0).text);
            assertEquals("Top",left.get(4).get(0).get(1).text);

            List<Objectives.Entry> red=Objectives.entries(Collections.singletonList("\u00a7c25% \u00a7fTop"),TEAMS,tracker);
            List<List<List<Objectives.Part>>> right=Objectives.split(red,columns,compact);
            assertEquals(1,right.get(4).size());
            assertEquals("Top",right.get(4).get(0).get(0).text);
            assertEquals("25%",right.get(4).get(0).get(1).text);

            // Nobody capturing: keep the existing centred order instead of guessing a side.
            List<Objectives.Entry> idle=Objectives.entries(Collections.singletonList("\u00a7f\u29be \u00a7fTop"),TEAMS,tracker);
            List<List<List<Objectives.Part>>> none=Objectives.split(idle,columns,compact);
            assertEquals("\u29be",none.get(4).get(0).get(0).text);
        }
    }

    @Test public void turningOffTrackingLeavesTheOriginalScoreboardTeamGrouping(){
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList("Blue","\u00a7c25% \u00a7fTop"),TEAMS);
        assertEquals("blue",entries.get(1).team);assertFalse(entries.get(1).controlPoint);
        Objectives.PointTracker tracker=tracker();
        point(tracker,"\u00a7f\u29be \u00a7fTop");tracker.clear();
        assertFalse(tracker.matches("25%","Top"));
    }

    @Test public void ordinaryWoolsAndMonumentsAreNotReclassifiedByTracking(){
        Objectives.PointTracker tracker=tracker();
        List<Objectives.Entry> entries=Objectives.entries(Arrays.asList(
            "Blue","\u00a7c\u2592 Red Wool","\u00a7c25% Monument"),TEAMS,tracker);
        assertEquals("blue",entries.get(1).team);assertEquals("blue",entries.get(2).team);
        assertFalse(entries.get(1).controlPoint);assertFalse(entries.get(2).controlPoint);
    }

    @Test public void ownedSiteNamesFillHorizontallyAndWrapOnlyWhenTheNextSiteDoesNotFit(){
        for(boolean markerFirst:new boolean[]{false,true}){
            java.util.List<Objectives.Strip> sites=new java.util.ArrayList<>();
            for(String name:Arrays.asList("Top","Cave","Valley")){
                Objectives.Part label=new Objectives.Part(name,TeamBar.chatColour('9'),-1);
                Objectives.Part marker=new Objectives.Part("\u29bf",TeamBar.chatColour('9'),-1,true,name);
                List<Objectives.Part> pair=markerFirst?Arrays.asList(marker,label):Arrays.asList(label,marker);
                pair.get(1).space=Objectives.SPACE;
                sites.add(new Objectives.Strip(pair,30));
            }
            List<Objectives.Strip> all=Objectives.packPointLines(sites,106);
            assertEquals(1,all.size());assertEquals(106,all.get(0).width);
            assertEquals(6,all.get(0).parts.size());
            assertEquals(Objectives.ENTRY_GAP,all.get(0).parts.get(2).space);
            assertNotSame(sites.get(0).parts.get(0),all.get(0).parts.get(0));
            List<Objectives.Strip> wrapped=Objectives.packPointLines(sites,68);
            assertEquals(2,wrapped.size());assertEquals(68,wrapped.get(0).width);assertEquals(30,wrapped.get(1).width);
            assertEquals(0,wrapped.get(1).parts.get(0).space);
            assertEquals(markerFirst?"Valley":"\u29bf",wrapped.get(1).parts.get(1).text);
            assertEquals(3,Objectives.packPointLines(sites,67).size());
            assertEquals(0,sites.get(1).parts.get(0).space);
        }
    }
}
