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

    private static Objectives.Strip strip(String team,int parts,int width){
        List<Objectives.Part> list=new ArrayList<>();
        for(int i=0;i<parts;i++)list.add(new Objectives.Part("x",TeamBar.WHITE,-1));
        return new Objectives.Strip(list,width,team);
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
        // Each side's heads sit just outside its own objectives, never on top of them - but only
        // when the objectives share the clock's row. Below the heads they are out of the way.
        List<String> left=bar.left.get(0),right=bar.right.get(0);
        int leftEnd=bar.headX(0,0)+bar.advance*(left.size()-1)+bar.head;
        int rightStart=bar.headX(1,0);
        if(!bar.leftStrips.isEmpty()&&bar.stripY<bar.band)
            assertTrue(what+": heads overlapped the left objectives",leftEnd<=bar.leftStripX);
        if(!bar.rightStrips.isEmpty()&&bar.stripY<bar.band)
            assertTrue(what+": heads overlapped the right objectives",rightStart>=bar.rightStripX+TeamBar.stripWidth(bar.rightStrips));
        assertTrue(what+": heads off the bar",leftEnd>=0&&rightStart+bar.advance*(right.size()-1)+bar.head<=bar.width);
    }

    @Test public void equalSidesAreEquidistantFromTheClock(){
        assertCentred(bar(Collections.singletonList("A"),Collections.singletonList("B"),20,lines(strip(2,40)),lines(strip(2,40))),"equal");
    }

    @Test public void unevenTeamsKeepTheClockBetweenTheHeads(){
        assertCentred(bar(Arrays.asList("A","B","C","D","E"),Arrays.asList("F","G"),20,null,null),"uneven");
    }

    @Test public void objectivesNeverShoveTheHeadsSideways(){
        // The objectives are printed under the cards, so a line that appears, grows or disappears
        // must leave the bar's width, the clock and every head exactly where they were.
        List<String> left=Collections.singletonList("A"),right=Collections.singletonList("B");
        TeamBar.Bar plain=namedBar(left,right,null,null);
        TeamBar.Bar wide=namedBar(left,right,lines(strip(9,60)),lines(strip(9,60)));
        assertCentred(wide,"wide strip");
        assertEquals("the objectives resized the bar",plain.width,wide.width);
        assertEquals("the objectives moved the left heads",plain.headX(0,0),wide.headX(0,0));
        assertEquals("the objectives moved the right heads",plain.headX(1,0),wide.headX(1,0));
        // A line wider than the bar is pulled onto it rather than hanging off the screen.
        assertTrue(wide.stripX(0,wide.leftStrips.get(0))>=0);
        assertTrue(wide.stripX(1,wide.rightStrips.get(0))+60<=wide.width);
    }

    /** Named objectives: the normal mode, where every line is printed under its team's card. */
    private static TeamBar.Bar namedBar(List<String> left,List<String> right,
        List<Objectives.Strip> leftStrips,List<Objectives.Strip> rightStrips){
        return new TeamBar.Bar(Collections.singletonList(left),Collections.singletonList(right),16,20,
            null,"",7,leftStrips,rightStrips,false);
    }

    @Test public void eachTeamsObjectivesHugTheClockInsideTheBar(){
        TeamBar.Bar bar=namedBar(Collections.singletonList("A"),Collections.singletonList("B"),
            lines(strip(1,30)),lines(strip(1,12)));
        // Both sides are beside the clock: one right-aligned to its left, one to its right.
        assertEquals(bar.textX-TeamBar.OBJECTIVE_GAP-30,bar.leftStripX);
        assertEquals(bar.textX+bar.textW+TeamBar.OBJECTIVE_GAP,bar.rightStripX);
        // The left lines are right-aligned against the clock, so a shorter line starts further in.
        assertEquals(bar.textX-TeamBar.OBJECTIVE_GAP-12,bar.stripX(0,strip(1,12)));
        assertCentred(bar,"beside the clock");
        // Under the heads, not beside them, so nothing has to move aside for a line.
        assertTrue(bar.stripTop(0)>=bar.band);
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

    /** The card one team's name, heads and objectives are drawn in. */
    private static TeamBar.Block block(TeamBar.Bar bar,int side,String team){
        for(TeamBar.Block one:bar.blocks(side))if(team.equals(one.team))return one;
        throw new AssertionError("no card for "+team);
    }

    @Test public void onAFourTeamBarTheTeamsSitSideBySide(){
        // Four teams make two cards on each side of the clock, never a tall stack of four rows:
        // on a KOTH map the bar grows sideways instead of covering half the screen.
        List<List<String>> left=Arrays.asList(Collections.singletonList("A1"),Collections.singletonList("C1"));
        List<List<String>> right=Arrays.asList(Collections.singletonList("B1"),Collections.singletonList("D1"));
        TeamBar.Bar bar=new TeamBar.Bar(left,right,16,20,null,"",7,
            lines(strip("Alpha",1,30),strip("Charlie",1,30)),lines(strip("Bravo",1,30),strip("Delta",1,30)),false,
            Arrays.asList(Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList()),
            null,0,Arrays.asList("Alpha","Charlie"),Arrays.asList("Bravo","Delta"));
        // Both cards on a side start on the same line: teams grow sideways, not downwards.
        assertEquals("the left cards were stacked",bar.rowTop(0,0),bar.rowTop(0,1));
        assertEquals("the right cards were stacked",bar.rowTop(1,0),bar.rowTop(1,1));
        // And that line is the clock's own row, so the heads read level with the time.
        assertEquals("the clock's row was pushed above the heads",bar.labelTop,bar.rowTop(0,0));
        // Each card sits beside the last, inside its own side of the clock, and holds its heads.
        TeamBar.Block alpha=block(bar,0,"Alpha"),charlie=block(bar,0,"Charlie");
        List<String> full=new ArrayList<>(left.get(0));
        for(int i=2;i<=30;i++)full.add("A"+i);
        bar.rosters=Collections.singletonList(new TeamBar.Side("Alpha",full));
        assertNull(TeamBar.headAt(bar,alpha.x,alpha.y));
        assertSame(bar.rosters.get(0),TeamBar.teamAt(bar,alpha.x,alpha.y));
        assertNull(TeamBar.teamAt(bar,bar.textX,alpha.y));
        assertEquals("Alpha (30)\n"+String.join("\n",full),TeamBar.cardAt(bar,alpha.x,alpha.y));
        assertTrue("the left cards overlapped",alpha.x+alpha.w<=charlie.x);
        assertTrue("a left card crossed the clock",charlie.x+charlie.w<=bar.leftEnd);
        assertTrue("a left head left its card",
            bar.headX(0,0)>=alpha.x&&bar.headX(0,0)+bar.head<=alpha.x+alpha.w);
        TeamBar.Block bravo=block(bar,1,"Bravo"),delta=block(bar,1,"Delta");
        assertTrue("the right cards overlapped",bravo.x+bravo.w<=delta.x);
        assertTrue("a right card crossed the clock",bravo.x>=bar.rightX);
        // Each team's objectives are read on its own card, never loose under the bar.
        assertEquals(alpha.x+alpha.w,bar.stripX(0,bar.leftStrips.get(0))+30);
        assertEquals(charlie.x+charlie.w,bar.stripX(0,bar.leftStrips.get(1))+30);
        assertEquals(bar.stripTop(0,0),bar.stripTop(0,1));
        assertEquals("The four-team bar pushed the clock off centre",bar.width/2,bar.textX+bar.textW/2);
        assertEquals("the bar kept a line it did not draw",bar.stripTop(0,0)+TeamBar.STRIP_LINE,bar.height);
    }

    @Test public void numbersModeKeepsLongObjectivesInsideTheirOwnCards(){
        List<List<String>> rows=Arrays.asList(Collections.singletonList("A"),Collections.singletonList("B"));
        List<String> owners=Arrays.asList("Red","Gold");
        Objectives.Strip longGoal=strip("Red",2,96),shortGoal=strip("Red",2,35),other=strip("Gold",2,72);
        TeamBar.Bar bar=new TeamBar.Bar(rows,rows,0,20,null,"",0,
            lines(longGoal,shortGoal,other),lines(longGoal,shortGoal,other),false,null,null,0,owners,owners,true,14);
        for(int side=0;side<2;side++){
            TeamBar.Block red=block(bar,side,"Red"),gold=block(bar,side,"Gold");
            assertTrue(red.x+red.w<=gold.x);
            for(Objectives.Strip goal:lines(longGoal,shortGoal,other)){
                TeamBar.Block card=goal==other?gold:red;
                int x=bar.stripX(side,goal);
                assertTrue(x>=card.x&&x+goal.width<=card.x+card.w);
                assertEquals(side==0?card.x+card.w:card.x,side==0?x+goal.width:x);
            }
        }
    }

    @Test public void markerEdgesStayAlignedAtEveryHeadPresetAndHudScale(){
        List<String> owners=Collections.singletonList("Red");
        Objectives.Strip a=strip("Red",2,90),b=strip("Red",2,27);
        for(int shown:new int[]{5,10,20,30})for(int scale:new int[]{50,75,100,125,150}){
            int head=TeamBar.headSize(shown)*scale/100;
            List<List<String>> rows=Collections.singletonList(Collections.singletonList("A"));
            TeamBar.Bar bar=new TeamBar.Bar(rows,rows,head,20,null,"",0,lines(a,b),lines(a,b),false,
                null,null,0,owners,owners,false,0,TeamBar.COUNT_BAND,96);
            for(Objectives.Strip s:lines(a,b)){
                assertTrue(bar.stripX(0,s)>=bar.rowX(0,0));
                assertEquals(bar.leftEnd,bar.stripX(0,s)+s.width);
                assertEquals(bar.rightX,bar.stripX(1,s));
            }
            assertEquals(bar.stripTop(0,1),bar.stripTop(1,1));
        }
    }

    @Test public void aShuffledSidebarStillPutsObjectivesOnTheirOwnCards(){
        // PGM sorts the sidebar by score, the bar sorts by the map's team order: on a four-team
        // map the two orders disagree, so the lines arrive shuffled. Matching must be by name.
        List<List<String>> left=Arrays.asList(Collections.singletonList("A1"),Collections.singletonList("A2"),Collections.singletonList("C1"));
        List<List<String>> right=Arrays.asList(Collections.singletonList("B1"),Collections.singletonList("D1"));
        TeamBar.Bar bar=new TeamBar.Bar(left,right,16,20,null,"",7,
            lines(strip("Charlie",1,30),strip("Alpha",1,30)),
            lines(strip("Delta",1,30),strip("Bravo",1,30)),false,
            Arrays.asList(Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList(),Collections.<Objectives.Strip>emptyList()),
            null,0,Arrays.asList("Alpha","Alpha","Charlie"),Arrays.asList("Bravo","Delta"));
        // Alpha's line arrived second but still belongs to Alpha's card.
        TeamBar.Block charlie=block(bar,0,"Charlie"),alpha=block(bar,0,"Alpha");
        assertEquals(charlie.x+charlie.w,bar.stripX(0,bar.leftStrips.get(0))+30);
        assertEquals(alpha.x+alpha.w,bar.stripX(0,bar.leftStrips.get(1))+30);
        assertEquals("Bravo's line went to Delta's card",block(bar,1,"Bravo").x,bar.stripX(1,bar.rightStrips.get(1)));
        assertEquals(bar.stripTop(0,0),bar.stripTop(0,1));
        assertEquals(bar.stripTop(0,0),bar.stripTop(1,0));
        assertTrue(bar.stripTop(0,0)>=bar.rowTop(0,1)+bar.band);
        assertEquals("the shuffled lines lost a card",bar.stripTop(0,0)+TeamBar.STRIP_LINE,bar.height);
    }

    @Test public void aBarWithNoObjectivesIsUnaffected(){        TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
            Collections.singletonList(Collections.singletonList("B")),16,20,null,"");
        assertTrue(bar.leftStrips.isEmpty());
        assertTrue(bar.rightStrips.isEmpty());
        assertCentred(bar,"no objectives");
        assertEquals(bar.band,bar.height);
    }

    @Test public void controlPointsStayUnderTheirTeamOrExactlyUnderTheClock(){
        Objectives.Strip left=strip(2,60),right=strip(2,80),neutral=strip(2,101);
        // Named lines are read under the cards in both modes now, so only the compact markers share
        // the clock's row; either way the clock itself keeps the bar's exact centre.
        for(boolean compact:new boolean[]{false,true}){
            TeamBar.Bar bar=new TeamBar.Bar(Collections.singletonList(Collections.singletonList("A")),
                Collections.singletonList(Collections.singletonList("B")),16,21,null,"",7,
                lines(strip(1,12)),lines(strip(1,12)),compact,
                Arrays.asList(lines(left),lines(right),lines(neutral,neutral)));
            assertEquals(bar.width/2,bar.textX+bar.textW/2);
            assertTrue(bar.pointY>=bar.band);
            assertTrue(bar.stripTop(4)>=bar.stripTop(2)+TeamBar.STRIP_LINE);
            assertEquals(bar.stripTop(4)+2*TeamBar.STRIP_LINE,bar.height);
            // Every objective line stays inside the bar's own edges.
            assertTrue("the left line ran off the bar",bar.stripX(2,left)>=0&&bar.stripX(2,left)+left.width<=bar.width);
            assertTrue("the right line ran off the bar",bar.stripX(3,right)>=0&&bar.stripX(3,right)+right.width<=bar.width);
            assertTrue("a point ran off the bar",bar.stripX(4,neutral)>=0&&bar.stripX(4,neutral)+neutral.width<=bar.width);
        }
    }
}
