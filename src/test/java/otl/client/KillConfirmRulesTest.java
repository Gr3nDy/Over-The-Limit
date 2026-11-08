package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class KillConfirmRulesTest {
    @Test public void streakTextStartsAtTwoKillsAndTiersAtFive(){
        assertEquals("",KillConfirmRules.streakLabel(0,false));assertEquals("",KillConfirmRules.streakLabel(1,false));
        assertEquals("2 KILL STREAK",KillConfirmRules.streakLabel(2,false));assertEquals("4 KILL STREAK",KillConfirmRules.streakLabel(4,false));
        assertEquals("RAMPAGE",KillConfirmRules.streakLabel(5,false));assertEquals("UNSTOPPABLE",KillConfirmRules.streakLabel(10,false));
        assertEquals("GODLIKE",KillConfirmRules.streakLabel(15,false));assertEquals("LEGENDARY",KillConfirmRules.streakLabel(20,false));
        assertEquals("",KillConfirmRules.streakLabel(2,true));assertEquals("",KillConfirmRules.streakLabel(10,true));
    }
    @Test public void parsesOnlyExplicitRewardMessages(){
        KillConfirmRules.Reward kill=KillConfirmRules.parse("\u00a7b+5 raindrops \u00a77| \u00a7eKill");
        assertEquals(5,kill.points);assertFalse(kill.assist);
        assertTrue(KillConfirmRules.parse(" +3 raindrops | Kill Assist ").assist);
        for(String text:new String[]{"<Alex>: +5 raindrops | Kill","(Blue) Alex: +5 raindrops | Kill","From Alex: +5 raindrops | Kill","+5 raindrops | Killstreak","+9999999 raindrops | Kill","+5 raindrops | Kill extra","5 raindrops | Kill"})assertNull(text,KillConfirmRules.parse(text));
        assertNull(KillConfirmRules.parse(null));
    }
    @Test public void onlyKillsIncreaseStreakAndLocalDeathResets(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("+3 raindrops | Kill Assist","Local",1,20);assertEquals(0,state.streak);assertTrue(state.reward.assist);assertEquals(3,state.streakExp);
        state.receive("+5 raindrops | Kill","Local",2,20);state.receive("+5 raindrops | Kill","Local",3,20);assertEquals(2,state.streak);assertEquals(13,state.streakExp);
        state.receive("+3 raindrops | Kill Assist","Local",4,20);assertEquals(2,state.streak);assertEquals(16,state.streakExp);assertTrue(state.reward.assist);assertEquals(4,state.receivedAt);
        assertEquals("",KillConfirmRules.streakLabel(state.streak,state.reward.assist));
        state.receive("Other died","Local",5,20);assertEquals(2,state.streak);
        state.receive("Local was slain by Enemy's Iron Sword","local",6000000000L,20);assertEquals(0,state.streak);assertEquals(0,state.streakExp);assertNull(state.reward);
        state.receive("+5 raindrops | Kill","Local",8000000000L,20);state.clear();assertEquals(0,state.streak);assertEquals(0,state.streakExp);assertNull(state.reward);
    }
    @Test public void animationPopsThenFadesOut(){
        assertEquals(1,KillConfirmRules.pop(0),.001);assertEquals(1.45,KillConfirmRules.pop(95000000),.001);
        assertEquals(1.133,KillConfirmRules.pop(190000000),.001);assertEquals(1,KillConfirmRules.pop(380000000),.001);
        assertEquals(1,KillConfirmRules.alpha(0),0);assertEquals(.5,KillConfirmRules.alpha(3200000000L),.001);assertEquals(0,KillConfirmRules.alpha(3400000000L),0);
        assertEquals(0,KillConfirmRules.reveal(400000000,450000000),0);assertEquals(.875,KillConfirmRules.reveal(560000000,450000000),.001);assertEquals(1,KillConfirmRules.reveal(670000000,450000000),.001);
        assertEquals(0,KillConfirmRules.slideIn(0,0),.001);assertEquals(1,KillConfirmRules.slideIn(130000000,0),.001);
        assertEquals(.875,KillConfirmRules.slideIn(65000000,0),.001);assertEquals(.700,KillConfirmRules.slideIn(43000000,0),.001);
        assertEquals(0,KillConfirmRules.slideIn(49000000,50000000),.001);
    }
    @Test public void bowRevengeAndSurvivorMatchDeathBeforeOrAfterReward(){
        for(boolean rewardFirst:new boolean[]{false,true}){
            KillConfirmRules.State state=new KillConfirmRules.State();
            state.receive("Local was slain by Enemy's Iron Sword","Local",1,20);
            assertEquals("Enemy",state.lastAttacker);
            state.died(state.lastAttacker); // Actual death tick must retain the known attacker.
            if(rewardFirst)state.receive("+25 raindrops | Kill","Local",2,3);
            state.receive("Enemy was shot by Local from 25 blocks","Local",3,3);
            if(!rewardFirst)state.receive("+25 raindrops | Kill","Local",4,3);
            assertEquals(1,state.streak);assertEquals(25,state.streakExp);
            assertEquals(java.util.Arrays.asList("BOW KILL","REVENGE","SURVIVOR","FIRST BLOOD"),state.reward.labels(1));assertNull(state.lastAttacker);
            state.receive("+5 raindrops | Kill","Local",5,20);assertEquals(30,state.streakExp);
            state.receive("Enemy was slain by Local's Iron Sword","Local",6,20);
            assertEquals("KILL",state.reward.actionLabel());assertFalse(state.reward.labels(0).contains("REVENGE"));assertFalse(state.reward.labels(0).contains("BOW KILL"));
        }
    }
    @Test public void assistDoesNotStealKillContextAndOldContextExpires(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Target was shot by Local from 25 blocks","Local",1,4);
        state.receive("+3 raindrops | Kill Assist","Local",2,4);assertEquals(java.util.Arrays.asList("KILL ASSIST"),state.reward.labels(10));
        state.receive("+5 raindrops | Kill","Local",3,20);assertEquals(java.util.Arrays.asList("BOW KILL","SURVIVOR","FIRST BLOOD"),state.reward.labels(1));
        state.receive("Other was shot by Local from 25 blocks","Local",4,20);
        state.receive("+5 raindrops | Kill","Local",3000000000L,20);assertEquals(java.util.Arrays.asList("KILL"),state.reward.labels(0));
        state.receive("+5 raindrops | Kill","Local",3000000001L,0);assertEquals(java.util.Arrays.asList("KILL"),state.reward.labels(0));
    }
    @Test public void secondKillAppearsBelowTheFirstAndExpUpdatesAtOnce(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Alpha was slain by Local's Iron Sword","Local",1000000000L,20);
        state.receive("+5 raindrops | Kill","Local",1000000001L,20);
        assertEquals(5,state.streakExp);
        state.receive("Bravo was slain by Local's Iron Sword","Local",2000000000L,20);
        state.receive("+5 raindrops | Kill","Local",2000000001L,20);
        assertEquals(10,state.streakExp);
        java.util.List<KillConfirmRules.Row> rows=state.rows();
        assertEquals(2,rows.stream().filter(r->r.text.equals("KILL")).count());
        assertEquals("KILL",rows.get(0).text);
        int lastKill=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).text.equals("KILL"))lastKill=i;
        assertEquals(2300000001L,rows.get(lastKill).start);
        assertTrue(lastKill>0&&lastKill<rows.size()-1);
        for(KillConfirmRules.Row r:rows)assertTrue(r.start<=3700000000L);
        assertEquals(-1,KillConfirmRules.newestRow(new java.util.ArrayList<>(),1));
        assertEquals(rows.size()-1,KillConfirmRules.newestRow(rows,4000000000L));
        assertEquals(12,KillConfirmRules.slideOffset(0));assertEquals(0,KillConfirmRules.slideOffset(1));assertEquals(6,KillConfirmRules.slideOffset(.5F));
    }
    @Test public void shutdownUnderdogFirstBloodAndMultiComeFromDeathStream(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int i=0;i<10;i++)state.receive("Feed"+i+" was slain by Enemy's Iron Sword","Local",i,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",10,20);
        state.receive("+5 raindrops | Kill","Local",11,20);
        assertEquals(java.util.Arrays.asList("KILL","SHUTDOWN","UNDERDOG","FIRST BLOOD"),state.reward.labels(1));
        state.receive("A was slain by Local's Iron Sword","Local",2000000000L,20);
        state.receive("+5 raindrops | Kill","Local",2000000001L,20);
        assertEquals("DOUBLE KILL",state.reward.labels(2).get(1));
    }
    @Test public void underdogNeedsTenMoreKillsThanTheLocalPlayer(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int i=0;i<9;i++)state.receive("Feed"+i+" was slain by Enemy's Iron Sword","Local",i,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",10,20);
        state.receive("+5 raindrops | Kill","Local",11,20);
        assertFalse(state.reward.underdog);
        state.clear();
        for(int i=0;i<10;i++)state.receive("Feed"+i+" was slain by Enemy's Iron Sword","Local",i,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",11,20);
        state.receive("+5 raindrops | Kill","Local",12,20);
        assertTrue(state.reward.underdog);
    }
    @Test public void longShotAndComebackAndAirTags(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Target was shot by Local from 40 blocks","Local",1,20,true,false);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertEquals(java.util.Arrays.asList("BOW KILL","LONG SHOT","FIRST BLOOD","AIRBORNE"),state.reward.labels(1));
        for(int i=0;i<3;i++)state.receive("Local fell out of the world","Local",3000000000L+i*2100000000L,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",9000000000L,20);
        state.receive("+5 raindrops | Kill","Local",9000000001L,20);
        assertTrue(state.reward.labels(1).contains("COMEBACK"));
    }
    @Test public void flawlessNeedsFullVictimAnUnbrokenDuelAndMelee(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.hit("Enemy",20,1);state.confirmDuel(14);
        state.receive("Enemy was slain by Local's Iron Sword","Local",2,20);
        state.receive("+5 raindrops | Kill","Local",3,20);
        assertTrue(state.reward.labels(1).contains("FLAWLESS"));
        state.hit("Enemy",20,10);state.confirmDuel(14);state.damaged();
        state.receive("Enemy was slain by Local's Iron Sword","Local",11,20);
        state.receive("+5 raindrops | Kill","Local",12,20);
        assertFalse(state.reward.labels(1).contains("FLAWLESS"));
        state.hit("Enemy",10,20);state.confirmDuel(4);
        state.receive("Enemy was slain by Local's Iron Sword","Local",21,20);
        state.receive("+5 raindrops | Kill","Local",22,20);
        assertFalse(state.reward.labels(1).contains("FLAWLESS"));
        state.hit("Enemy",20,30);state.confirmDuel(14);
        state.receive("Enemy was shot by Local from 40 blocks","Local",31,20);
        state.receive("+5 raindrops | Kill","Local",32,20);
        assertFalse(state.reward.labels(1).contains("FLAWLESS"));
    }
    @Test public void assistsNeverCarryTags(){
        KillConfirmRules.Reward assist=new KillConfirmRules.Reward(3,true);
        assist.shutdown=true;assist.revenge=true;assist.multi=3;assist.survivor=true;
        assertEquals(java.util.Arrays.asList("KILL ASSIST"),assist.labels(10));
    }
    @Test public void bowJumpAndTwoBlockAirborneAreIndependentTags(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Target was shot by Local from 10 blocks","Local",1,20,false,true,false);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertTrue(state.reward.labels(1).contains("JUMPSHOT"));
        assertFalse(state.reward.labels(1).contains("AIRBORNE"));
        state.clear();
        state.receive("Target was shot by Local from 10 blocks","Local",1,20,true,true,false);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertTrue(state.reward.labels(1).containsAll(java.util.Arrays.asList("JUMPSHOT","AIRBORNE")));
        state.clear();
        state.receive("Target was slain by Local's Iron Sword","Local",1,20,true,true,false);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertTrue(state.reward.labels(1).contains("AIRBORNE"));
        assertFalse(state.reward.labels(1).contains("JUMPSHOT"));
    }
}
