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

    private static List<Objectives.Strip> lines(Objectives.Strip... strips){
        return Arrays.asList(strips);
    }

    private static TeamBar.Bar bar(List<String> left,List<String> right,int textW,
        List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips){
        return new TeamBar.Bar(Collections.singletonList(left),Collections.singletonList(right),16,textW,null,"",7,leftStrips,rightStrips);
    }

    /** Everything the layout promises, checked on any bar. */
    private static void assertCentred(TeamBar.Bar bar,String what){
        assertEquals(what+": clock off centre",bar.width/2,bar.textX+bar.textW/2);
        // Each side's heads sit just outside its own objectives, never on top of them.
        List<String> left=bar.left.get(0),right=bar.right.get(0);
        int leftEnd=bar.leftStart(left)+bar.advance*(left.size()-1)+bar.head;
        int rightStart=bar.rightStart(right);
        if(!bar.leftStrips.isEmpty())assertTrue(what+": heads overlapped the left objectives",leftEnd<=bar.leftStripX);
        if(!bar.rightStrips.isEmpty())
            assertTrue(what+": heads overlapped the right objectives",rightStart>=bar.rightStripX+TeamBar.stripWidth(bar.rightStrips));
        assertTrue(what+": heads off the bar",leftEnd>=0&&rightStart+bar.advance*(right.size()-1)+bar.head<=bar.width);
    }

    @Test public void equalSidesAreEquidistantFromTheClock(){
        assertCentred(bar(Collections.singletonList("A"),Collections.singletonList("B"),20,lines(strip(2,40)),lines(strip(2,40))),"equal");
    }

    @Test public void unevenTeamsKeepTheClockBetweenTheHeads(){
        assertCentred(bar(Arrays.asList("A","B","C","D","E"),Arrays.asList("F","G"),20,null,null),"uneven");
    }

    @Test public void objectivesTakeTheSameRoomOnBothSides(){
        // The strips sit between the heads and the clock, so the heads move outwards - but by the
        // same amount on both sides, or the bar looks lopsided.
        TeamBar.Bar plain=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,null,null);
        TeamBar.Bar wide=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,lines(strip(9,60)),lines(strip(9,60)));
        assertCentred(wide,"wide strip");
        assertTrue("the objectives did not reserve any room",wide.width>plain.width);
        // The heads sit outside both strips, never on top of them.
        assertTrue(wide.leftStart(wide.left.get(0))+wide.head<=wide.leftStripX);
        assertTrue(wide.rightStart(wide.right.get(0))>=wide.rightStripX+60);
    }

    @Test public void eachTeamsObjectivesSitBesideTheClockOnItsOwnSide(){
        TeamBar.Bar bar=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,lines(strip(1,30)),lines(strip(1,12)));
        // Both sides are beside the clock: one right-aligned to its left, one to its right.
        assertEquals(bar.textX-TeamBar.OBJECTIVE_GAP-30,bar.leftStripX);
        assertEquals(bar.textX+bar.textW+TeamBar.OBJECTIVE_GAP,bar.rightStripX);
        // The left lines are right-aligned against the clock, so a shorter line starts further in.
        assertEquals(bar.textX-TeamBar.OBJECTIVE_GAP-12,bar.stripX(0,strip(1,12)));
        assertCentred(bar,"beside the clock");
        // The heads stay clear of both strips.
        assertTrue(bar.leftStart(bar.left.get(0))+bar.head<=bar.leftStripX);
        assertTrue(bar.rightStart(bar.right.get(0))>=bar.rightStripX+12);
    }

    @Test public void aLongStripOnOneSideDoesNotShoveTheClock(){
        // A long strip on one side must not push the clock sideways.
        TeamBar.Bar bar=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,lines(strip(3,60)),lines(strip(1,8)));
        assertCentred(bar,"uneven strips");
    }

    @Test public void compactMarkersStayOnTheClocksRow(){
        // Even when the bar is taller than one row, compact markers start beside the clock.
        TeamBar.Bar bar=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,
            lines(strip(1,20),strip(1,20),strip(1,20)),lines(strip(1,20)));
        assertEquals(3*TeamBar.STRIP_LINE,bar.stripH);
        assertEquals((bar.band-(2*TeamBar.STRIP_LINE+Objectives.HEIGHT))/2,bar.stripTop(0));
        assertEquals((bar.band-Objectives.HEIGHT)/2,bar.stripTop(1));
        assertTrue(bar.height>=bar.stripH);
        assertCentred(bar,"tall stack");
    }

    @Test public void twoCompactRowsShareTheHeadsAndClockCentreWhileTheOtherSideStaysSingle(){
        TeamBar.Bar bar=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,
            lines(strip(2,30),strip(2,30)),lines(strip(2,12)));
        int centre=bar.band/2;
        assertEquals(centre,bar.stripTop(0)+(TeamBar.STRIP_LINE+Objectives.HEIGHT)/2);
        assertEquals(centre,bar.stripTop(1)+Objectives.HEIGHT/2);
        assertTrue(bar.stripTop(0)>=0);
        assertTrue(bar.stripTop(0)+TeamBar.STRIP_LINE+Objectives.HEIGHT<=bar.band);
        assertCentred(bar,"two compact rows");
    }

    @Test public void namedObjectivesStayBelowTheHeadsWithoutMovingThemApart(){
        TeamBar.Bar plain=bar(Collections.singletonList("A"),Collections.singletonList("B"),20,null,null);
        TeamBar.Bar named=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"",7,
            lines(strip(1,30),strip(2,60)),lines(strip(1,30),strip(2,60)),false);
        assertTrue(named.stripY>=named.band);
        assertEquals(plain.textX-plain.leftEnd,named.textX-named.leftEnd);
        assertEquals(plain.rightX-plain.textX,named.rightX-named.textX);
        assertEquals(named.width/2,named.textX+named.textW/2);
        assertEquals(named.stripY+named.stripH,named.height);
    }

    @Test public void aBarWithNoObjectivesIsUnaffected(){
        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"");
        assertTrue(bar.leftStrips.isEmpty());
        assertTrue(bar.rightStrips.isEmpty());
        assertCentred(bar,"no objectives");
        assertEquals(bar.band,bar.height);
    }

    @Test public void controlPointsStayUnderTheirTeamOrExactlyUnderTheClock(){
        for(boolean compact:new boolean[]{false,true}){
            Objectives.Strip left=strip(2,60),right=strip(2,80),neutral=strip(2,101);
            TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
                Collections.singletonList(Collections.singletonList("B")),16,21,null,"",7,
                lines(strip(1,12)),lines(strip(1,12)),compact,
                Arrays.asList(lines(left),lines(right),lines(neutral,neutral)));
            assertEquals(bar.width/2,bar.textX+bar.textW/2);
            assertEquals(bar.width/2,bar.stripX(4,neutral)+neutral.width/2);
            assertEquals(bar.leftEnd,bar.stripX(2,left)+left.width);
            assertEquals(bar.rightX,bar.stripX(3,right));
            assertTrue(bar.pointY>=bar.band);
            assertTrue(bar.stripTop(4)>=bar.stripTop(2)+TeamBar.STRIP_LINE);
            assertEquals(bar.stripTop(4)+2*TeamBar.STRIP_LINE,bar.height);
            assertTrue(bar.stripX(2,left)>=0);assertTrue(bar.stripX(3,right)+right.width<=bar.width);
        }
    }
}
