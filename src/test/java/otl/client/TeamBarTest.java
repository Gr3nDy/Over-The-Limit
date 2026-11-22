package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class TeamBarTest {
    /** Rounded heads clip the single corner texel of the eight-by-eight face and keep the rest. */
    @Test public void roundedHeadsKeepTheFaceAndDropItsCorners(){
        for(int col=0;col<8;col++)for(int row=0;row<8;row++){
            boolean inside=TeamBar.roundedTexel(col,row);
            boolean corner=(col==0||col==7)&&(row==0||row==7);
            if(corner)assertFalse("Corner texel "+col+","+row+" was kept",inside);
            // Only the four corner texels go: the rest of the face is untouched.
            else assertTrue("Texel "+col+","+row+" was dropped",inside);
        }
        // Symmetric in both axes, so the head does not look lopsided.
        for(int col=0;col<8;col++)for(int row=0;row<8;row++){
            assertEquals(TeamBar.roundedTexel(col,row),TeamBar.roundedTexel(7-col,row));
            assertEquals(TeamBar.roundedTexel(col,row),TeamBar.roundedTexel(col,7-row));
        }
    }

    @Test public void unseenLeaverExpiresOnlyAfterRosterIsAvailable(){
        java.util.Map<String,Long> seen=new java.util.HashMap<>();
        assertTrue(TeamBar.keepMissingPlayer(seen,"Alex",1L,false));
        assertTrue(seen.isEmpty());
        assertTrue(TeamBar.keepMissingPlayer(seen,"Alex",10L,true));
        assertTrue(TeamBar.keepMissingPlayer(seen,"Alex",10L+TeamBar.LEFT_GRACE-1,true));
        assertFalse(TeamBar.keepMissingPlayer(seen,"Alex",10L+TeamBar.LEFT_GRACE,true));
    }
    @Test public void confirmedTeamsOverrideFfaGuess(){
        java.util.List<String> empty=java.util.Collections.emptyList(),teams=java.util.Arrays.asList("red","blue");
        assertFalse(TeamBar.ffaMode(true,"koth",teams,empty,teams));
        assertFalse(TeamBar.ffaMode(true,"koth",empty,java.util.Arrays.asList("Red","Blue"),teams));
        assertTrue(TeamBar.ffaMode(true,"",empty,empty,teams));
        assertTrue(TeamBar.ffaMode(false,"ffa",teams,empty,teams));
    }
    @Test public void renderedPlayerListSupportsToggleAndExpiresAfterClosing(){
        long seen=1_000_000_000L;
        assertTrue(TeamBar.recentlyRenderedPlayerList(seen,seen));
        assertTrue(TeamBar.recentlyRenderedPlayerList(seen+100_000_000L,seen));
        assertFalse(TeamBar.recentlyRenderedPlayerList(seen+150_000_000L,seen));
        assertFalse(TeamBar.recentlyRenderedPlayerList(seen,0));
        assertFalse(TeamBar.recentlyRenderedPlayerList(seen-1,seen));
    }
    @Test public void emptyAndUnknownTeamsNeverShareAReservedColumn(){
        java.util.Map<String,Integer> columns=TeamBar.teamColumns(
            java.util.Arrays.asList("Blue","Red","Green"),java.util.Arrays.asList("Red","Blue"));
        assertEquals(Integer.valueOf(0),columns.get("red"));
        assertEquals(Integer.valueOf(1),columns.get("blue"));
        assertEquals(Integer.valueOf(2),columns.get("green"));
        java.util.List<Objectives.Entry> entries=Objectives.entries(java.util.Arrays.asList(
            "Red","\u2714 Red Monument","Blue","\u2715 Blue Monument"),java.util.Arrays.asList("Red","Blue"));
        java.util.List<java.util.List<java.util.List<Objectives.Part>>> split=Objectives.split(entries,columns,false);
        assertEquals("Red Monument",split.get(0).get(1).get(0).text);
        assertEquals("Blue Monument",split.get(1).get(1).get(1).text);
    }
    @Test public void ffaScoresMatchOnlyTheNamedPlayerAndRowsAreCentred(){
        java.util.List<String> players=java.util.Arrays.asList("Alex","Sam","Pat");
        java.util.Map<String,String> scores=TeamBar.ffaScores(java.util.Arrays.asList(
            "\u00a7f25\u00a78/\u00a77200 \u00a7cAlex","7 \u00a79Sam","99 AlexOther"),players);
        assertEquals("25/200",scores.get("alex"));
        assertEquals("7",scores.get("sam"));
        assertFalse(scores.containsKey("pat"));
        java.util.List<java.util.List<String>> rows=java.util.Arrays.asList(players.subList(0,2),players.subList(2,3));
        TeamBar.Bar bar=new TeamBar.Bar(rows,16,24,30,null,"0:00",null,0,scores);
        for(int i=0;i<rows.size();i++){
            java.util.List<String> row=rows.get(i);
            assertTrue(Math.abs(2*bar.headX(0,i)+(row.size()-1)*bar.advance+bar.head-bar.width)<=1);
            int[] bounds=TeamBar.headBounds(bar,row.get(0));
            assertEquals(row.get(0),TeamBar.headAt(bar,bounds[0]+1,bounds[1]+1));
        }
        assertTrue(bar.rowTop(0,0)>=12);
        assertTrue(bar.rowTop(0,1)>=bar.rowTop(0,0)+bar.head+13);
    }
    @Test public void normalClockIsGoldWithoutChangingCountdownWarnings(){
        assertEquals(TeamBar.GOLD,TeamBar.colour(null));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("1:05",65,false,false)));
        assertEquals(TeamBar.YELLOW,TeamBar.colour(new TeamBar.Timer("1:05",65,true,false)));
        assertEquals(TeamBar.RED,TeamBar.colour(new TeamBar.Timer("0:25",25,true,false)));
        assertEquals(TeamBar.RED,TeamBar.colour(new TeamBar.Timer("0:06",6,true,false)));
        assertEquals(TeamBar.DARK_RED,TeamBar.colour(new TeamBar.Timer("0:05",5,true,false)));
        assertEquals(TeamBar.DARK_RED,TeamBar.colour(new TeamBar.Timer("0:00",0,true,false)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("0:10",10,false,true)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("0:00",0,false,true)));
        assertEquals(TeamBar.GOLD,TeamBar.colour(new TeamBar.Timer("1:00",60,true,true)));
    }
    @Test public void theHeaderShowsOneValueAndHoversTheOtherWithTheAuthor(){
        TeamBar.Header mode=TeamBar.header(2,"Shingeki no Brady","Kazoha","Capture the Wool",TeamBar.AQUA);
        assertEquals("Capture the Wool",mode.text);
        assertEquals("Shingeki no Brady by Kazoha",mode.hover);
        assertEquals(TeamBar.AQUA,mode.colour);
        TeamBar.Header map=TeamBar.header(1,"Shingeki no Brady","Kazoha","Capture the Wool",0xFFAA00);
        assertEquals("Shingeki no Brady",map.text);
        assertEquals("Capture the Wool by Kazoha",map.hover);
        assertEquals(TeamBar.AQUA,map.colour);
        assertNull("A disabled header still took a line",TeamBar.header(0,"Shingeki no Brady","Kazoha","Capture the Wool",TeamBar.AQUA));
        assertNull(TeamBar.header(1,"","Kazoha","Capture the Wool",TeamBar.AQUA));
        assertNull("A title-less sidebar invented a gamemode",TeamBar.header(2,"Shingeki no Brady","Kazoha","",TeamBar.AQUA));
        assertEquals("Shingeki no Brady",TeamBar.header(2,"Shingeki no Brady","","Capture the Wool",TeamBar.AQUA).hover);
    }
    @Test public void hoverUsesOriginalHeadBoundsOnBothSidesAndRows(){
        java.util.List<java.util.List<String>> left=java.util.Arrays.asList(java.util.Arrays.asList("Alex","Sam"),java.util.Collections.singletonList("Pat"));
        java.util.List<java.util.List<String>> right=java.util.Collections.singletonList(java.util.Collections.singletonList("Kai"));
        TeamBar.Bar bar=new TeamBar.Bar(left,right,16,20,null,"");
        int x=bar.headX(0,0);
        assertEquals("Alex",TeamBar.headAt(bar,x,0));
        assertEquals("Sam",TeamBar.headAt(bar,x+bar.advance,0));
        assertEquals("Pat",TeamBar.headAt(bar,bar.headX(0,1),bar.rowTop(0,1)+(bar.band-bar.head)/2));
        assertEquals("Kai",TeamBar.headAt(bar,bar.headX(1,0),0));
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

    /**
     * The welcome block's heading is formatting, not words, so the map name survives a client whose
     * chat PGM translates. Only the first line counts, and only when it is a strikethrough rule.
     */
    @Test public void theWelcomeHeadingNamesTheMapInAnyLanguage(){
        assertEquals("Shingeki no Brady",TeamBar.welcomeHeading(
            "\u00a78\u00a7m---------------- \u00a7b\u00a7lShingeki no Brady\u00a78\u00a7m ----------------"));
        assertEquals("Shingeki no Brady",TeamBar.welcomeHeading(
            "\u00a7m   \u00a7lShingeki no Brady\u00a7r   \u00a7m"));
        assertEquals("Avalon",TeamBar.welcomeHeading(
            "\u00a7m== \u00a7lAvalon\u00a7m ==\n\u00a77Control the wool!\n\u00a77Created by jcane"));
        // Not a welcome block: no rule, a player talking, or a rule around no name.
        assertNull(TeamBar.welcomeHeading("Avalon"));
        assertNull(TeamBar.welcomeHeading("    \u00a7lMatch Stats\u00a7r    "));
        assertNull(TeamBar.welcomeHeading("\u00a7m----------------\u00a7m"));
        assertNull(TeamBar.welcomeHeading("\u00a7fAlex: \u00a7mhi\u00a7m"));
        assertNull(TeamBar.welcomeHeading(null));
        // PGM wraps the name in plain spaces on some maps, and no translated sentence is a rule.
        assertEquals("Cursed Library",TeamBar.welcomeHeading("             Cursed Library             "));
        assertNull(TeamBar.welcomeHeading("\u6bd4\u8d5b\u5f00\u59cb\u4e86"));
        assertNull(TeamBar.welcomeHeading("\u8c22\u8c22\u8c22\u8c22"));
    }

    /**
     * A team's plate hugs the clock: the left team's plate ends on the clock's left edge and the
     * right team's starts on its right, so the heads on each side run outward from the time. The
     * name is centred on its own plate rather than pushed to the outer edge.
     */
    @Test public void eachTeamsNameIsCentredOverItsOwnPlate(){
        java.util.List<Objectives.Strip> none=java.util.Collections.emptyList();
        java.util.List<String> left=java.util.Arrays.asList("A","B"),right=java.util.Collections.singletonList("C");
        TeamBar.Bar bar=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.singletonList(right),16,24,null,"10:00",0,none,none,false,null,null,0,
            java.util.Collections.singletonList("Red"),java.util.Collections.singletonList("Blue"),false,0);
        TeamBar.Block red=bar.blocks(0).get(0),blue=bar.blocks(1).get(0);
        assertEquals("the left plate did not end on the clock",bar.leftEnd,red.x+red.w);
        assertEquals("the right plate did not start on the clock",bar.rightX,blue.x);
        // Centred: the text's own centre is the plate's centre, whatever its width.
        for(TeamBar.Block block:java.util.Arrays.asList(red,blue))
            for(int width:new int[]{6,20,39})
                assertTrue("the name was not centred on its plate",
                    Math.abs(TeamBar.labelX(block,0,width)+width/2-(block.x+block.w/2))<=1);
        // The name is drawn in screen space, so the bar's own x has to move it with the card. This
        // is the bug that left the names at the far left of the screen: the offset was dropped.
        int barX=500;
        for(TeamBar.Block block:java.util.Arrays.asList(red,blue)){
            int width=12;
            assertEquals("the name did not follow the bar",TeamBar.labelX(block,0,width)+barX,
                TeamBar.labelX(block,barX,width));
        }
    }

    @Test public void teamScoreIsCentredBelowHeadsWithoutMovingThemOffTheClockRow(){
        java.util.List<Objectives.Strip> none=java.util.Collections.emptyList();
        java.util.List<String> left=java.util.Collections.singletonList("A");
        java.util.List<String> owners=java.util.Collections.singletonList("Red");
        Objectives.Strip score=new Objectives.Strip(java.util.Collections.singletonList(
            new Objectives.Part("5",TeamBar.WHITE,-1)),5,"Red",true);
        TeamBar.Bar scored=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.<java.util.List<String>>emptyList(),16,20,null,"10:00",0,
            java.util.Collections.singletonList(score),none,false,null,null,0,owners,null,false,0);
        // The name line above the heads sits above the clock's row too, so the clock is level with
        // those heads instead of floating a line higher.
        assertEquals(TeamBar.LABEL_H,scored.labelTop);
        TeamBar.Block card=scored.blocks(0).get(0);
        assertEquals(scored.labelTop,scored.rowTop(0,0));
        assertEquals(scored.rowTop(0,0)+scored.band+TeamBar.OBJECTIVE_GAP,scored.stripTop(0,0));
        assertEquals(card.x+(card.w-score.width)/2,scored.stripX(0,score));
        assertEquals(card.y+card.h,scored.stripTop(0,0)+TeamBar.STRIP_LINE);
        assertTrue("the bar did not make room for the score",
            scored.height>=scored.stripTop(0,0)+TeamBar.STRIP_LINE);
    }

    /**
     * PGM prints a bare team-name row above that team's objectives. The card prints the name
     * itself, so the row is dropped rather than doubling it; a team with nobody online has no
     * card, so its row stays as the only thing naming its objectives.
     */
    @Test public void aTeamNameRowIsDroppedWhenThatTeamHasACard(){
        java.util.List<Objectives.Strip> none=java.util.Collections.emptyList();
        Objectives.Strip header=new Objectives.Strip(java.util.Collections.singletonList(
            new Objectives.Part("Red",TeamBar.WHITE,-1)),18,"Red",false,true);
        java.util.List<Objectives.Strip> withHeader=java.util.Collections.singletonList(header);
        TeamBar.Bar online=new TeamBar.Bar(
            java.util.Collections.singletonList(java.util.Collections.singletonList("A")),
            java.util.Collections.<java.util.List<String>>emptyList(),16,20,null,"10:00",0,withHeader,none,
            false,null,null,0,java.util.Collections.singletonList("Red"),null,false,0);
        assertTrue("the team name was printed twice",online.strips(0).isEmpty());
        TeamBar.Bar empty=new TeamBar.Bar(
            java.util.Collections.<java.util.List<String>>emptyList(),
            java.util.Collections.<java.util.List<String>>emptyList(),16,20,null,"10:00",0,withHeader,none,
            false,null,null,0,java.util.Collections.<String>emptyList(),java.util.Collections.<String>emptyList(),false,0);
        assertEquals("an empty team lost the only name it had",1,empty.strips(0).size());
    }

    /** A count replaces a team's heads with one cell, so every team still fits on the bar. */
    @Test public void teamCountsTakeOneCellPerTeamAndKeepTheClockCentred(){
        java.util.List<Objectives.Strip> none=java.util.Collections.emptyList();
        java.util.List<String> left=java.util.Arrays.asList("A","B","C"),right=java.util.Arrays.asList("D","E");
        TeamBar.Bar bar=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.singletonList(right),0,24,null,"10:00",0,none,none,false,null,null,0,
            java.util.Collections.singletonList("Red"),java.util.Collections.singletonList("Blue"),true,18);
        assertTrue(bar.counts);
        // One cell per team, whatever the team size, and the clock still on the exact centre.
        assertEquals(18,bar.rowWidth(left));
        assertEquals(18,bar.rowWidth(right));
        assertEquals(bar.width/2,bar.textX+bar.textW/2);
        // The number's row is one band tall and no head is drawn at all.
        assertEquals(TeamBar.COUNT_BAND,bar.band);
        assertEquals(0,bar.head);
        // The team's objectives go below its count, never beside the clock.
        Objectives.Strip goals=new Objectives.Strip(java.util.Collections.singletonList(
            new Objectives.Part("x",TeamBar.WHITE,-1)),20,"Red");
        TeamBar.Bar withGoals=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.singletonList(right),0,24,null,"10:00",0,
            java.util.Collections.singletonList(goals),none,false,null,null,0,
            java.util.Collections.singletonList("Red"),java.util.Collections.singletonList("Blue"),true,18);
        assertTrue("a team's goals were not placed under its count",withGoals.stripTop(0,0)>=TeamBar.COUNT_BAND);
        assertTrue("the goals did not make the bar taller",withGoals.height>bar.height);
    }

    /**
     * With the heads hidden there is no head row to fill, so a team's name takes the bar's top edge
     * and its objectives follow straight after, instead of hanging below an empty head row.
     */
    @Test public void hiddenHeadsPullTheTeamNameAndObjectivesUp(){
        java.util.List<Objectives.Strip> none=java.util.Collections.emptyList();
        java.util.List<Objectives.Strip> name=java.util.Collections.singletonList(
            new Objectives.Strip(java.util.Collections.singletonList(
                new Objectives.Part("Red",TeamBar.WHITE,-1)),20,"Red"));
        java.util.List<Objectives.Strip> goals=java.util.Arrays.asList(
            new Objectives.Strip(java.util.Collections.singletonList(
                new Objectives.Part("Red",TeamBar.WHITE,-1)),20,"Red"),
            new Objectives.Strip(java.util.Collections.singletonList(
                new Objectives.Part("Monument",TeamBar.WHITE,-1)),40,"Red"));
        java.util.List<String> left=java.util.Collections.singletonList("A");
        java.util.List<String> owners=java.util.Collections.singletonList("Red");
        TeamBar.Bar withHeads=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.<java.util.List<String>>emptyList(),16,20,null,"10:00",7,name,none,
            false,null,null,0,owners,null,false,0);
        TeamBar.Bar noHeads=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.<java.util.List<String>>emptyList(),0,20,null,"10:00",7,name,none,
            false,null,null,0,owners,null,false,0);
        assertTrue("the name did not move up",noHeads.stripTop(0,0)<withHeads.stripTop(0,0));
        // No head row at all: the name sits one gap below the bar's own top edge.
        assertEquals(TeamBar.LABEL_H+TeamBar.OBJECTIVE_GAP,noHeads.stripTop(0,0));
        assertTrue("the bar kept the empty head row",noHeads.height<withHeads.height);
        // Its objectives follow immediately after the name, and the clock still fits the bar.
        TeamBar.Bar stacked=new TeamBar.Bar(java.util.Collections.singletonList(left),
            java.util.Collections.<java.util.List<String>>emptyList(),0,20,null,"10:00",7,goals,none,
            false,null,null,0,owners,null,false,0);
        assertEquals(noHeads.stripTop(0,0)+TeamBar.STRIP_LINE,stacked.stripTop(0,1));
        assertTrue("the clock would be drawn outside the bar",stacked.height>=9);
    }

    /** In FFA a control point scores the players, so it is drawn under the grid, not above it. */
    @Test public void ffaObjectivesSitUnderTheGrid(){
        java.util.List<java.util.List<String>> grid=java.util.Arrays.asList(java.util.Arrays.asList("A","B"));
        java.util.Map<String,String> scores=java.util.Collections.emptyMap();
        TeamBar.Bar plain=new TeamBar.Bar(grid,16,20,30,null,"10:00",null,0,scores);
        Objectives.Strip point=new Objectives.Strip(java.util.Collections.singletonList(
            new Objectives.Part("Control",TeamBar.AQUA,-1)),60);
        TeamBar.Bar withPoint=new TeamBar.Bar(grid,16,20,30,null,"10:00",null,0,scores,
            java.util.Collections.singletonList(point));
        assertEquals(1,withPoint.strips(4).size());
        assertTrue(withPoint.strips(2).isEmpty()&&withPoint.strips(3).isEmpty());
        int rowBottom=withPoint.leftRowTops[0]+withPoint.head;
        assertTrue("the point did not move under the heads",withPoint.stripTop(4,0)>=rowBottom);
        assertEquals("the point was not centred",withPoint.width/2-30,withPoint.stripX(4,point));
        assertEquals("the grid moved down for the point",plain.leftRowTops[0],withPoint.leftRowTops[0]);
        assertTrue("the bar did not grow for the point",withPoint.height>plain.height);
        assertTrue("the point did not widen the bar",withPoint.width>plain.width);
    }
}
