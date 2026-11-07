package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class KillFeedRulesTest {
    @Test public void slideIsOptionalClampedAndSettlesAfterTwoHundredMilliseconds(){
        assertEquals(0,KillFeedRules.slideOffset(0,false),0);
        assertEquals(-22,KillFeedRules.slideOffset(-1,true),0);
        assertEquals(-2.75,KillFeedRules.slideOffset(100000000,true),.001);
        assertEquals(0,KillFeedRules.slideOffset(200000000,true),0);
        assertEquals(0,KillFeedRules.slideOffset(Long.MAX_VALUE,true),0);
    }
    @Test public void backgroundAndOutlineOpacityAreIndependent(){
        KillFeedRules.Death d=KillFeedRules.parse("Victim was slain by Killer's Wooden Sword");
        assertEquals(0,KillFeedRules.panelColor(false,0,1)>>>24);
        assertEquals(255,d.border("Killer",0xFFBD45,0xFF5555,100)>>>24);
        assertEquals(204,KillFeedRules.panelColor(false,100,1)>>>24);
        assertEquals(0,d.border("Killer",0xFFBD45,0xFF5555,0)>>>24);
    }
    @Test public void opacityControlsThePanelIncludingHoverNotOnlyTheColoredOutline(){
        assertEquals(0,KillFeedRules.panelColor(false,0,1)>>>24);
        assertEquals(0,KillFeedRules.panelColor(true,0,1)>>>24);
        assertEquals(0x33,KillFeedRules.panelColor(false,25,1)>>>24);
        assertEquals(0xCC,KillFeedRules.panelColor(false,100,1)>>>24);
        assertEquals(0x66,KillFeedRules.panelColor(false,100,.5F)>>>24);
    }
    @Test public void lavaPreservesTheAttackAndFireIsNotLava(){
        KillFeedRules.Death lava=KillFeedRules.parse("Victim was punched into lava by Killer");
        assertEquals(KillFeedRules.Cause.MELEE,lava.cause);assertEquals(KillFeedRules.Context.LAVA,lava.context());assertTrue(lava.extraContext());
        KillFeedRules.Death fire=KillFeedRules.parse("Victim went up in flames");
        assertEquals(KillFeedRules.Context.FIRE,fire.context());assertTrue(fire.suicide());assertTrue(fire.extraContext());
        assertEquals(KillFeedRules.Context.LAVA,KillFeedRules.parse("Victim fell into lava").context());
        assertEquals(KillFeedRules.Context.LAVA,KillFeedRules.parse("Victim was burnt to a crisp by Killer's Lava").context());
        assertEquals(KillFeedRules.Context.FALL,KillFeedRules.parse("Victim hit the ground too hard").context());
        assertEquals(KillFeedRules.Context.VOID,KillFeedRules.parse("Victim fell out of the world").context());
        assertFalse(KillFeedRules.parse("Victim died").extraContext());
    }
    @Test public void fadesDuringFinalSecondButHistoryRemainsReadable(){
        assertEquals(1,KillFeedRules.opacity(8000000000L,10,false),.001);
        assertEquals(.5,KillFeedRules.opacity(9500000000L,10,false),.001);
        assertEquals(0,KillFeedRules.opacity(10000000000L,10,false),.001);
        assertEquals(1,KillFeedRules.opacity(100000000000L,1,true),.001);
        assertEquals(0x7FFFFFFF,KillFeedRules.faded(0xFFFFFFFF,.5F));
    }
    @Test public void hoverSelectsOnlyOneRowAndNotTheGapAtAnyScale(){
        for(float scale:new float[]{.5F,.75F,1F,1.5F}){
            float mouseX=100*scale,mouseY=25*scale;int hits=0;
            for(int row=0;row<8;row++)if(KillFeedRules.hitRow(mouseX/scale,mouseY/scale,50,row*22,100))hits++;
            assertEquals(1,hits);
            assertFalse(KillFeedRules.hitRow(100,21,50,0,100));
            assertFalse(KillFeedRules.hitRow(150,5,50,0,100));
        }
    }
    @Test public void localDeathIsRedButSuicidesNeverHaveColoredBorders(){
        KillFeedRules.Death death=KillFeedRules.parse("Victim was slain by Killer's Wooden Sword");
        assertEquals(0xFFFF5555,death.border("Victim",0xFFBD45,0xFF5555,100));
        assertEquals(0xFFFFBD45,death.border("Killer",0xFFBD45,0xFF5555,100));
        assertEquals(0,death.border("Other",0xFFBD45,0xFF5555,100));
        assertEquals(0x7F123456,death.border("Victim",0xFFBD45,0x123456,50));
        assertEquals(0xFF000000,death.border("Victim",0xFFBD45,0,100));
        assertEquals(0,death.border("Victim",0xFFBD45,0xFF5555,0)>>>24);
        for(String text:new String[]{"Victim died","Victim fell out of the world","Victim was slain by Victim's Wooden Sword"}){
            KillFeedRules.Death self=KillFeedRules.parse(text);assertTrue(self.suicide());assertEquals(0,self.border("Victim",0xFFBD45,0xFF5555,100));
        }
        assertFalse(KillFeedRules.parse("Victim was slain by a Zombie").suicide());
    }
    @Test public void distanceUsesRequestedFormatAndAbsentDistanceUsesNoSpace(){
        assertEquals(" (+50 Blocks)",KillFeedRules.parse("Victim was shot by Archer from 50 blocks").distanceLabel());
        assertEquals("",KillFeedRules.parse("Victim was slain by Killer's Wooden Sword").distanceLabel());
    }
    @Test public void boldsBothNamesOnlyWhenLocalPlayerIsInvolved(){
        KillFeedRules.Death death=KillFeedRules.parse("\u00a79Victim\u00a7r was slain by \u00a7cKiller\u00a7r's Wooden Sword");
        for(String local:new String[]{"killer","victim"}){
            assertEquals("\u00a7c\u00a7lKiller\u00a7r",KillFeedRules.feedLabel(death.killerLabel,death,local));
            assertEquals("\u00a79\u00a7lVictim\u00a7r",KillFeedRules.feedLabel(death.victimLabel,death,local));
        }
        assertEquals(death.killerLabel,KillFeedRules.feedLabel(death.killerLabel,death,"Other"));
    }
    @Test public void victimFirstPgmBecomesKillerFirstWithOriginalColors(){
        String original="\u00a79Victim\u00a7r was slain by \u00a7cKiller\u00a7r's Wooden Sword";
        KillFeedRules.Death d=KillFeedRules.parse(original);
        assertNotNull(d);assertEquals("Victim",d.victim);assertEquals("Killer",d.killer);
        assertEquals("Wooden Sword",d.weapon);assertEquals(original,d.original);
        assertTrue(d.killerLabel.startsWith("\u00a7c"));assertTrue(d.victimLabel.startsWith("\u00a79"));
        assertTrue(d.ownKill("killer"));assertFalse(d.ownKill("Victim"));
    }
    @Test public void bowVoidAndDistanceAreIndependentIndicators(){
        KillFeedRules.Death d=KillFeedRules.parse("Victim was shot out of the world by Archer from 50 blocks");
        assertNotNull(d);assertEquals("Archer",d.killer);assertEquals("50",d.distance);
        assertEquals(KillFeedRules.Cause.BOW,d.cause);assertTrue(d.voidFall);
        d=KillFeedRules.parse("Victim was sniped into lava by Archer with a Snowball from 100 blocks (predicted)");
        assertNotNull(d);assertEquals("Snowball",d.weapon);assertEquals("100",d.distance);assertTrue(d.lava);
    }
    @Test public void recognizesEnvironmentalDeathsWithoutInventingKillers(){
        for(String line:new String[]{"Alex fell out of the world","Alex forgot to breathe","Alex died","Alex was killed by magic","Alex was blown up by TNT","Alex was burnt to a crisp by Lava","Alex was slain by a Zombie"}){
            KillFeedRules.Death d=KillFeedRules.parse(line);assertNotNull(line,d);assertNull(line,d.killer);
        }
        assertEquals(KillFeedRules.Cause.MAGIC,KillFeedRules.parse("Alex was killed by magic").cause);
    }
    @Test public void explosionSpleefAndMeleeKnockback(){
        for(String zone:new String[]{"off a high place","into lava","out of the world"}){
            assertEquals("Alex",KillFeedRules.parse("Bob was knocked "+zone+" by Alex's Stone Sword").killer);
            assertEquals("TNT",KillFeedRules.parse("Bob was blown "+zone+" by Alex's TNT from 20 blocks").weapon);
            assertEquals("Alex",KillFeedRules.parse("Bob was spleefed "+zone+" by Alex").killer);
        }
        assertEquals("Alex",KillFeedRules.parse("Bob felt the fury of Alex's fists").killer);
        assertEquals(KillFeedRules.Cause.MAGIC,KillFeedRules.parse("Bob was killed by Alex's magic").cause);
    }
    @Test public void neverTreatsPlayerChatOrAnnouncementsAsDeaths(){
        for(String line:new String[]{"<Alex>: Bob was slain by Alex's Wooden Sword","(Blue) Alex: Bob died","From Alex: Bob died","Match starts in 10 seconds","Alex joined the game","Objective was destroyed by Bob","Bob was shot by a Skeleton from 50 blocks"})assertNull(line,KillFeedRules.parse(line));
        assertNull(KillFeedRules.parse(null));assertNull(KillFeedRules.parse(new String(new char[1025])));
    }
    @Test public void historyIsBoundedNewestFirstAndClearable(){
        KillFeedRules.History history=new KillFeedRules.History();
        for(int i=0;i<1005;i++)history.add(KillFeedRules.parse("P"+i+" died"));
        assertEquals(1000,history.entries.size());assertEquals("P1004",history.entries.get(0).victim);
        assertEquals("P5",history.entries.get(999).victim);history.clear();assertTrue(history.entries.isEmpty());
    }
}
