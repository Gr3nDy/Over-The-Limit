package otl.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** The clock, the objectives and both head columns must share one centre. */
public class TeamBarCentreTest {
    private static Objectives.Strip strip(int parts,int width){
        List<Objectives.Part> list=new ArrayList<>();
        for(int i=0;i<parts;i++)list.add(new Objectives.Part("x",TeamBar.WHITE,-1));
        return new Objectives.Strip(list,width);
    }

    private static TeamBar.Bar bar(List<String> left,List<String> right,int textW,Objectives.Strip strip){
        return new TeamBar.Bar(Collections.singletonList(left),Collections.singletonList(right),16,textW,null,"",7,strip);
    }

    /** Everything the layout promises, checked on any bar. */
    private static void assertCentred(TeamBar.Bar bar,String what){
        assertEquals(what+": clock off centre",bar.width/2,bar.textX+bar.textW/2);
        if(bar.strip!=null)assertEquals(what+": objectives off centre",bar.width/2,bar.stripX+bar.strip.width/2);
        // Both head rows are the same distance from the clock, whatever the team sizes.
        List<String> left=bar.left.get(0),right=bar.right.get(0);
        int leftGap=bar.textX-(bar.leftStart(left)+bar.advance*(left.size()-1)+bar.head);
        int rightGap=bar.rightStart(right)-(bar.textX+bar.textW);
        assertEquals(what+": heads not equidistant from the clock",leftGap,rightGap);
    }

    /** Where the heads sit relative to the clock, so a strip cannot be shown to move them. */
    private static String headLayout(TeamBar.Bar bar){
        List<String> left=bar.left.get(0),right=bar.right.get(0);
        int leftGap=bar.textX-(bar.leftStart(left)+bar.advance*(left.size()-1)+bar.head);
        int rightGap=bar.rightStart(right)-(bar.textX+bar.textW);
        return leftGap+","+rightGap;
    }

    @Test public void equalSidesAreEquidistantFromTheClock(){
        TeamBar.Bar bar=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,strip(2,200));
        assertCentred(bar,"equal");
    }

    @Test public void unevenTeamsKeepTheClockBetweenTheHeads(){
        assertCentred(bar(Arrays.asList("A","B","C","D","E"),Arrays.asList("F","G"),20,strip(2,200)),"uneven");
    }

    @Test public void aStripWiderThanTheClockStillSharesItsCentre(){
        // The strip is the widest thing in the bar: it must not drag the clock off centre.
        assertCentred(bar(Collections.singletonList("A"),Collections.singletonList("B"),20,strip(9,260)),"wide strip");
    }

    @Test public void objectivesNeverPushTheHeadsAwayFromTheClock(){
        // The regression this guards: a long objective line widened the middle, so the heads sat
        // far from the clock and the bar looked lopsided.
        TeamBar.Bar plain=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,null);
        TeamBar.Bar wide=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,strip(9,260));
        assertEquals("the objectives moved the heads",headLayout(plain),headLayout(wide));
        // The bar grows to hold the strip, symmetrically.
        assertTrue(wide.width>=wide.strip.width);
        assertEquals(wide.width/2,wide.stripX+wide.strip.width/2);
    }

    @Test public void theObjectivesSitOnTheirOwnLineUnderTheHeads(){
        TeamBar.Bar bar=new TeamBar.Bar(Arrays.asList(Arrays.asList("A","B"),Collections.singletonList("C")),
            Collections.singletonList(Collections.singletonList("D")),16,20,null,"",7,strip(2,200));
        assertTrue("the objectives overlap the head rows",bar.stripY>=2*bar.band+TeamBar.ROW_GAP);
        assertEquals("the bar left no room for its objectives",bar.stripY+Objectives.HEIGHT,bar.height);
    }

    @Test public void compactObjectivesSitBesideTheClockNotUnderIt(){
        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"",7,null,strip(1,30),strip(1,12));
        // Both strips are beside the clock: one right-aligned to its left, one to its right.
        assertEquals(bar.textX-TeamBar.OBJECTIVE_GAP-30,bar.leftStripX);
        assertEquals(bar.textX+bar.textW+TeamBar.OBJECTIVE_GAP,bar.rightStripX);
        assertNull("compact mode must not also draw a line under the clock",bar.strip);
        assertCentred(bar,"compact");
        // The heads stay clear of both strips.
        assertTrue(bar.leftStart(bar.left.get(0))+bar.head<=bar.leftStripX);
        assertTrue(bar.rightStart(bar.right.get(0))>=bar.rightStripX+12);
    }

    @Test public void compactModeGivesBothSidesTheSameRoomSoTheClockStaysCentred(){
        // A long strip on one side must not shove the clock sideways.
        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"",7,null,strip(3,60),strip(1,8));
        assertCentred(bar,"uneven compact");
    }

    @Test public void aClockWiderThanTheStripStillSharesItsCentre(){
        assertCentred(bar(Collections.singletonList("A"),Collections.singletonList("B"),40,strip(1,12)),"wide clock");
    }

    @Test public void aBarWithNoObjectivesIsUnaffected(){
        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"");
        assertNull(bar.strip);
        assertCentred(bar,"no objectives");
        assertEquals(bar.band,bar.height);
    }
}
