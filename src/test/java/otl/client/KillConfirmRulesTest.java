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
    @Test public void namedStreakTiersKeepTheExactCountImmediatelyBelow(){
        int[] starts={5,7,10,15,20,25,30,40,50,60,70,80,90,100};
        String[] names={"RAMPAGE","DOMINATING","UNSTOPPABLE","GODLIKE","LEGENDARY","IMMORTAL","BEYOND THE LIMIT","ANNIHILATION","ONE MAN ARMY","APOCALYPSE","WORLD ENDER","ABSOLUTE ZERO","NO EQUAL","THE LIMIT BREAKER"};
        for(int tier=0;tier<starts.length;tier++){
            int end=tier+1<starts.length?starts[tier+1]-1:101;
            for(int count:new int[]{starts[tier],end}){
                assertEquals(names[tier],KillConfirmRules.streakLabel(count,false));
                KillConfirmRules.Reward kill=new KillConfirmRules.Reward(5,false);
                assertEquals(java.util.Arrays.asList("KILL",names[tier],count+" KILL STREAK"),kill.labels(count));
                assertEquals("",KillConfirmRules.streakLabel(count,true));
            }
        }
        assertEquals("THE LIMIT BREAKER",KillConfirmRules.streakLabel(Integer.MAX_VALUE,false));
        KillConfirmRules.Reward kill=new KillConfirmRules.Reward(5,false);
        assertEquals(java.util.Arrays.asList("KILL","4 KILL STREAK"),kill.labels(4));
    }
    @Test public void rapidKillTagsReachNineThenShowAnExactMultiKillCount(){
        String[] names={"DOUBLE KILL","TRIPLE KILL","QUAD KILL","PENTA KILL","HEXA KILL","HEPTA KILL","OCTA KILL","NONA KILL"};
        assertEquals("",KillConfirmRules.chainLabel(0));assertEquals("",KillConfirmRules.chainLabel(1));
        for(int count=2;count<=9;count++){
            KillConfirmRules.Reward kill=new KillConfirmRules.Reward(5,false);kill.multi=count;
            assertEquals(java.util.Arrays.asList("KILL",names[count-2]),kill.labels(1));
        }
        for(int count:new int[]{10,12,100}){
            KillConfirmRules.Reward kill=new KillConfirmRules.Reward(5,false);kill.multi=count;
            assertEquals(java.util.Arrays.asList("KILL","MULTI KILL",count+" KILL CHAIN","THE LIMIT BREAKER","100 KILL STREAK"),kill.labels(100));
        }
    }
    @Test public void chainCountDoesNotUseABonusSlotOrAppearWithoutItsTag(){
        KillConfirmRules.Reward kill=new KillConfirmRules.Reward(5,false);kill.multi=12;kill.revenge=true;kill.survivor=true;
        assertEquals(java.util.Arrays.asList("KILL","REVENGE","MULTI KILL","12 KILL CHAIN","SURVIVOR","RAMPAGE","5 KILL STREAK"),kill.labels(5));
        kill.shutdown=true;kill.flawless=true;
        assertEquals(java.util.Arrays.asList("KILL","SHUTDOWN","REVENGE","FLAWLESS","RAMPAGE","5 KILL STREAK"),kill.labels(5));
    }
    @Test public void chainWindowStaysAtThreeSecondsAndDeathResetsIt(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int count=1;count<=12;count++){
            long at=count*3000000000L;
            state.receive("Target"+count+" was slain by Local's Iron Sword","Local",at,20);
            state.receive("+5 raindrops | Kill","Local",at+1,20);
            assertEquals(count,state.reward.multi);
            assertEquals(count,state.streak);
        }
        assertTrue(state.reward.labels(state.streak).contains("12 KILL CHAIN"));
        state.receive("Next was slain by Local's Iron Sword","Local",39000000001L,20);
        state.receive("+5 raindrops | Kill","Local",39000000002L,20);
        assertEquals(1,state.reward.multi);assertEquals(13,state.streak);
        state.receive("Local fell out of the world","Local",40000000000L,20);
        state.receive("AfterDeath was slain by Local's Iron Sword","Local",41000000000L,20);
        state.receive("+5 raindrops | Kill","Local",41000000001L,20);
        assertEquals(1,state.reward.multi);assertEquals(1,state.streak);
    }
    @Test public void expandedCountRowsRevealBelowTheirLabelsInOrder(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.streak=99;state.multi=9;state.lastKillAt=1000000000L;
        state.receive("Target was slain by Local's Iron Sword","Local",2000000000L,20);
        state.receive("+5 raindrops | Kill","Local",2000000001L,20);
        java.util.List<KillConfirmRules.Row> rows=state.rows();
        assertEquals(java.util.Arrays.asList("KILL","MULTI KILL","10 KILL CHAIN","THE LIMIT BREAKER","100 KILL STREAK"),
            rows.stream().map(r->r.text).collect(java.util.stream.Collectors.toList()));
        for(int i=1;i<rows.size();i++)assertEquals(KillConfirmRules.ROW_NANOS,rows.get(i).start-rows.get(i-1).start);
        assertTrue(KillConfirmRules.rowOffset(4,rows,rows.get(4).start+KillConfirmRules.REVEAL_NANOS)
            >KillConfirmRules.rowOffset(3,rows,rows.get(4).start+KillConfirmRules.REVEAL_NANOS));
    }
    @Test public void bowDistanceTagsUseExactReportedRanges(){
        for(String distance:new String[]{"0","3","3.1","29.9","30","59.9","60","100"}){
            for(boolean rewardFirst:new boolean[]{false,true}){
                KillConfirmRules.State state=new KillConfirmRules.State();
                if(rewardFirst)state.receive("+5 raindrops | Kill","Local",1000000000L,20);
                state.receive("Target was shot by Local from "+distance+" blocks","Local",1100000000L,20);
                if(!rewardFirst)state.receive("+5 raindrops | Kill","Local",1200000000L,20);
                double blocks=Double.parseDouble(distance);
                assertEquals(blocks<=3,state.reward.labels(1).contains("POINT BLANK"));
                assertEquals(blocks>=30&&blocks<60,state.reward.labels(1).contains("LONG SHOT"));
                assertEquals(blocks>=60,state.reward.labels(1).contains("EXTREME RANGE"));
            }
        }
        for(String death:new String[]{"Target was shot by Local's Skeleton","Target was slain by Local's Bow","Target was shot by Local with a Snowball from 2 blocks"}){
            KillConfirmRules.State state=new KillConfirmRules.State();
            state.receive(death,"Local",1000000000L,20);state.receive("+5 raindrops | Kill","Local",1100000000L,20);
            assertFalse(state.reward.pointBlank);assertFalse(state.reward.extremeRange);
        }
    }
    @Test public void dominationStartsAtFiveAndOnlyThatOpponentsKillResetsIt(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int count=1;count<=6;count++){
            long at=count*4000000000L;
            state.receive("Enemy was slain by Local's Iron Sword","Local",at,20);
            state.receive("+5 raindrops | Kill","Local",at+1,20);
            assertEquals(count>=5,state.reward.labels(state.streak).contains("DOMINATION"));
        }
        state.receive("Local was slain by Other's Iron Sword","Local",25000000000L,20);
        state.receive("eNeMy was slain by Local's Iron Sword","Local",29000000000L,20);
        state.receive("+5 raindrops | Kill","Local",29000000001L,20);
        assertTrue(state.reward.domination);
        state.receive("Local was slain by ENEMY's Iron Sword","Local",30000000000L,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",34000000000L,20);
        state.receive("+5 raindrops | Kill","Local",34000000001L,20);
        assertFalse(state.reward.domination);
        state.clear();
        state.receive("Enemy was slain by Local's Iron Sword","Local",40000000000L,20);
        state.receive("+5 raindrops | Kill","Local",40000000001L,20);
        assertFalse(state.reward.domination);
    }
    @Test public void dominationCountsOpponentsSeparatelyAndSupportsRewardFirst(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int count=1;count<=5;count++){
            long at=count*8000000000L;
            state.receive("+5 raindrops | Kill","Local",at,20);
            state.receive("Enemy was slain by Local's Iron Sword","Local",at+1,20);
            assertEquals(count>=5,state.reward.domination);
            state.receive("Other"+count+" was slain by Local's Iron Sword","Local",at+4000000000L,20);
            state.receive("+5 raindrops | Kill","Local",at+4000000001L,20);
            assertFalse(state.reward.domination);
        }
    }
    @Test public void redemptionRequiresFiveDeathsToThatOpponentAndIsConsumedOnTheirKill(){
        for(int deaths:new int[]{4,5})for(boolean rewardFirst:new boolean[]{false,true}){
            KillConfirmRules.State state=new KillConfirmRules.State();
            for(int count=1;count<=deaths;count++){
                long at=count*4000000000L;
                state.died(null);state.deathObserved(at); // Health can report death before chat identifies the killer.
                state.receive("Local was slain by Enemy's Iron Sword","Local",at+1,20);
                state.died(state.lastAttacker);state.deathObserved(at+2);
                state.receive("Local was slain by Enemy's Iron Sword","Local",at+3,20);
                state.respawn(at+4,false);
            }
            long at=30000000000L;
            if(rewardFirst)state.receive("+5 raindrops | Kill","Local",at,20);
            state.receive("eNeMy was slain by Local's Iron Sword","Local",at+1,20);
            if(!rewardFirst)state.receive("+5 raindrops | Kill","Local",at+2,20);
            assertEquals(deaths==5,state.reward.redemption);
            assertEquals(deaths==5,state.reward.labels(1).contains("REDEMPTION"));
            if(deaths==5)assertFalse(state.reward.labels(1).contains("REVENGE"));
            state.receive("Enemy was slain by Local's Iron Sword","Local",at+4000000000L,20);
            state.receive("+5 raindrops | Kill","Local",at+4000000001L,20);
            assertFalse(state.reward.redemption);
        }
    }
    @Test public void redemptionCountsEachOpponentSeparatelyAndClearResetsIt(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int count=1;count<=5;count++){
            long at=count*4000000000L;
            state.receive("Local was slain by Enemy"+count+"'s Iron Sword","Local",at,20);
            state.respawn(at+1,false);
        }
        state.receive("Enemy1 was slain by Local's Iron Sword","Local",24000000000L,20);
        state.receive("+5 raindrops | Kill","Local",24000000001L,20);
        assertFalse(state.reward.redemption);
        for(int count=1;count<=5;count++){
            long at=30000000000L+count*4000000000L;
            state.receive("Local was slain by Enemy's Iron Sword","Local",at,20);state.respawn(at+1,false);
        }
        state.clear();
        state.receive("Enemy was slain by Local's Iron Sword","Local",60000000000L,20);
        state.receive("+5 raindrops | Kill","Local",60000000001L,20);
        assertFalse(state.reward.redemption);
    }
    @Test public void cleanSweepCountsUniqueOpponentsAndResetsOnTimeGapOrDeath(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        for(int count=1;count<=5;count++){
            long at=count*1000000000L;
            state.receive("Target"+count+" was shot by Local from 10 blocks","Local",at,20);
            state.receive("+5 raindrops | Kill","Local",at+1,20);
            assertEquals(count>=5,state.reward.cleanSweep);
        }
        assertTrue(state.reward.labels(state.streak).contains("CLEAN SWEEP"));
        state.receive("Later was shot by Local from 10 blocks","Local",8000000001L,20);
        state.receive("+5 raindrops | Kill","Local",8000000002L,20);
        assertFalse(state.reward.cleanSweep);
        state.clear();
        for(int count=1;count<=5;count++){
            long at=count*1000000000L;
            state.receive("Same was shot by Local from 10 blocks","Local",at,20);
            state.receive("+5 raindrops | Kill","Local",at+1,20);
            assertFalse(state.reward.cleanSweep);
        }
        state.receive("Local fell out of the world","Local",5500000000L,20);
        state.receive("New was shot by Local from 10 blocks","Local",6000000000L,20);
        state.receive("+5 raindrops | Kill","Local",6000000001L,20);
        assertFalse(state.reward.cleanSweep);assertFalse(state.reward.brawler);
    }
    @Test public void brawlerNeedsThreeMeleeKillsWithinTheSameChain(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        String[] deaths={"One was slain by Local's Iron Sword","Two was shot by Local from 10 blocks","Three felt the fury of Local's fists","Four was slain by Local's Iron Sword"};
        for(int i=0;i<deaths.length;i++){
            long at=(i+1)*1000000000L;
            state.receive(deaths[i],"Local",at,20);state.receive("+5 raindrops | Kill","Local",at+1,20);
            assertEquals(i==3,state.reward.brawler);
        }
        assertTrue(state.reward.labels(state.streak).contains("BRAWLER"));
        state.receive("Late was slain by Local's Iron Sword","Local",8000000000L,20);
        state.receive("+5 raindrops | Kill","Local",8000000001L,20);
        assertFalse(state.reward.brawler);
    }
    @Test public void demolitionRequiresAnExplosionCreditedToLocalAndSupportsEitherOrder(){
        for(boolean rewardFirst:new boolean[]{false,true}){
            KillConfirmRules.State state=new KillConfirmRules.State();
            if(rewardFirst)state.receive("+5 raindrops | Kill","Local",1000000000L,20);
            state.receive("Target was blown up by Local's TNT","Local",1100000000L,20);
            if(!rewardFirst)state.receive("+5 raindrops | Kill","Local",1200000000L,20);
            assertTrue(state.reward.labels(1).contains("DEMOLITION"));assertFalse(state.reward.brawler);
        }
        for(String death:new String[]{"Target blew up","Target was blown up by Other's TNT","Local was blown up by Local's TNT"}){
            KillConfirmRules.State state=new KillConfirmRules.State();
            state.receive(death,"Local",1000000000L,20);state.receive("+5 raindrops | Kill","Local",1100000000L,20);
            assertFalse(state.reward.demolition);
        }
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
            state.receive("Match starts in 10 seconds","Local",0,20);
            state.receive("Local was slain by Enemy's Iron Sword","Local",1,20);
            assertEquals("Enemy",state.lastAttacker);
            state.died(state.lastAttacker); // Actual death tick must retain the known attacker.
            if(rewardFirst)state.receive("+25 raindrops | Kill","Local",2,3);
            state.receive("Enemy was shot by Local from 25 blocks","Local",3,3);
            if(!rewardFirst)state.receive("+25 raindrops | Kill","Local",4,3);
            assertEquals(1,state.streak);assertEquals(25,state.streakExp);
            assertEquals(java.util.Arrays.asList("BOW KILL","REVENGE","SURVIVOR"),state.reward.labels(1));assertNull(state.lastAttacker);
            assertEquals("Enemy",state.reward.victim);
            assertEquals("Enemy",state.rows().get(0).victim);
            assertNull(state.rows().get(1).victim);
            state.receive("+5 raindrops | Kill","Local",5,20);assertEquals(30,state.streakExp);
            state.receive("Enemy was slain by Local's Iron Sword","Local",6,20);
            assertEquals("KILL",state.reward.actionLabel());assertFalse(state.reward.labels(0).contains("REVENGE"));assertFalse(state.reward.labels(0).contains("BOW KILL"));
        }
    }
    @Test public void assistDoesNotStealKillContextAndOldContextExpires(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Match starts in 10 seconds","Local",0,20);
        state.receive("Target was shot by Local from 25 blocks","Local",1,4);
        state.receive("+3 raindrops | Kill Assist","Local",2,4);assertEquals(java.util.Arrays.asList("KILL ASSIST"),state.reward.labels(10));
        state.receive("+5 raindrops | Kill","Local",3,20);assertEquals(java.util.Arrays.asList("BOW KILL","SURVIVOR","FIRST BLOOD"),state.reward.labels(1));
        state.receive("Other was shot by Local from 25 blocks","Local",4,20);
        state.receive("+5 raindrops | Kill","Local",3000000000L,20);assertEquals(java.util.Arrays.asList("KILL"),state.reward.labels(0));
        state.receive("+5 raindrops | Kill","Local",3000000001L,0);assertEquals(java.util.Arrays.asList("KILL"),state.reward.labels(0));
    }
    @Test public void secondKillAppearsBelowTheFirstAndExpUpdatesAtOnce(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Match starts in 10 seconds","Local",0,20);
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
    @Test public void shutdownUnderdogAndMultiComeFromDeathStream(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Match starts in 10 seconds","Local",0,20);
        for(int i=0;i<10;i++)state.receive("Feed"+i+" was slain by Enemy's Iron Sword","Local",i,20);
        state.receive("Enemy was slain by Local's Iron Sword","Local",10,20);
        state.receive("+5 raindrops | Kill","Local",11,20);
        assertEquals(java.util.Arrays.asList("KILL","SHUTDOWN","UNDERDOG"),state.reward.labels(1));
        state.receive("A was slain by Local's Iron Sword","Local",2000000000L,20);
        state.receive("+5 raindrops | Kill","Local",2000000001L,20);
        assertEquals("DOUBLE KILL",state.reward.labels(2).get(1));
    }
    @Test public void firstBloodBelongsOnlyToTheFirstPlayerKillOfAnObservedMatch(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Other was slain by Local's Iron Sword","Local",1,20);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertFalse(state.reward.firstBlood); // Joining mid-match cannot establish who got the first kill.
        state.receive("Match starts in 10 seconds","Local",3,20);
        state.receive("Other fell out of the world","Local",4,20);
        state.receive("Victim was slain by Enemy's Iron Sword","Local",5,20);
        state.receive("Next was slain by Local's Iron Sword","Local",6,20);
        state.receive("+5 raindrops | Kill","Local",7,20);
        assertFalse(state.reward.firstBlood);
        state.receive("Match starts in 10 seconds","Local",8,20);
        state.receive("Another was slain by Local's Iron Sword","Local",9,20);
        state.receive("+5 raindrops | Kill","Local",10,20);
        assertTrue(state.reward.firstBlood);
        state.receive("YetAnother was slain by Local's Iron Sword","Local",11,20);
        state.receive("+5 raindrops | Kill","Local",12,20);
        assertFalse(state.reward.firstBlood);
        state.receive("Match starts in 10 seconds","Local",13,20);
        state.receive("Local was slain by Enemy's Iron Sword","Local",14,20);
        state.receive("Another was slain by Local's Iron Sword","Local",15,20);
        state.receive("+5 raindrops | Kill","Local",16,20);
        assertFalse(state.reward.firstBlood);
        state.clear();assertFalse(state.matchStartSeen);
    }
    @Test public void killIconNeverReusesAnOlderVictimForUnmatchedRewards(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("Alex was slain by Local's Iron Sword","Local",1,20);
        state.receive("+5 raindrops | Kill","Local",2,20);
        assertEquals("Alex",state.rows().get(0).victim);
        state.receive("+5 raindrops | Kill","Local",3000000000L,20);
        assertNull(state.reward.victim);
        assertNull(state.rows().stream().filter(r->r.text.equals("KILL")).reduce((a,b)->b).get().victim);
    }
    @Test public void rewardFirstKillWaitsForItsVictimWithoutReusingAnotherHead(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("+5 raindrops | Kill","Local",1000000000L,20);
        assertNull(state.rows().get(0).victim);
        state.receive("Alex was slain by Local's Iron Sword","Local",1100000000L,20);
        assertEquals("Alex",state.rows().get(0).victim);
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
        state.receive("Match starts in 10 seconds","Local",0,20);
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
        assist.shutdown=true;assist.revenge=true;assist.multi=3;assist.survivor=true;assist.pointBlank=true;assist.extremeRange=true;assist.domination=true;
        assist.redemption=true;assist.cleanSweep=true;assist.demolition=true;assist.brawler=true;
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
    @Test public void respawnShowsOnlyThePreviousLifesExpAndKills(){
        KillConfirmRules.State state=new KillConfirmRules.State();
        state.receive("A was slain by Local's Iron Sword","Local",1,20);
        state.receive("+5 raindrops | Kill","Local",2,20);
        state.receive("+3 raindrops | Kill Assist","Local",3,20);
        state.receive("B was slain by Local's Iron Sword","Local",4,20);
        state.receive("+5 raindrops | Kill","Local",5,20);
        state.receive("Local was slain by Enemy's Iron Sword","Local",6,20);
        state.died("Enemy"); // Death message and health tick can both report the same death.
        assertEquals(0,state.streakExp);assertEquals(0,state.lifeKills);assertNull(state.reward);
        state.respawn(1000000000L,true);
        assertEquals(java.util.Arrays.asList("TOTAL EXP: 13","TOTAL KILLS: 2"),
            state.rows().stream().map(r->r.text).collect(java.util.stream.Collectors.toList()));
        assertEquals(0,KillConfirmRules.newestRow(state.rows(),1000000000L));
        assertEquals(1,KillConfirmRules.newestRow(state.rows(),1650000000L));
        state.respawn(2000000000L,true);assertEquals(1000000000L,state.recapAt);
        state.receive("+5 raindrops | Kill","Local",3000000000L,20);
        assertEquals(0,state.recapAt);assertEquals(5,state.streakExp);
        state.died(null);state.respawn(4000000000L,false);assertTrue(state.rows().isEmpty());
        state.clear();assertEquals(0,state.recapAt);assertFalse(state.awaitingRespawn);
    }
    @Test public void thirdWhiteRowAppearsBelowPositionAWithoutMovingOlderRows(){
        java.util.List<KillConfirmRules.Row> rows=java.util.Arrays.asList(
            new KillConfirmRules.Row("KILL",1000000000L),new KillConfirmRules.Row("FLAWLESS",1650000000L),
            new KillConfirmRules.Row("FIRST BLOOD",2300000000L),new KillConfirmRules.Row("2 KILL STREAK",2950000000L));
        assertEquals(0,KillConfirmRules.rowOffset(0,rows,1220000000L),.001);
        assertEquals(-14,KillConfirmRules.rowOffset(0,rows,1870000000L),.001);
        assertEquals(0,KillConfirmRules.rowOffset(1,rows,1870000000L),.001);
        assertEquals(-14,KillConfirmRules.rowOffset(0,rows,2410000000L),.001);
        assertEquals(0,KillConfirmRules.rowOffset(1,rows,2410000000L),.001);
        assertEquals(14,KillConfirmRules.rowOffset(2,rows,2410000000L),.001);
        assertEquals(-14,KillConfirmRules.rowOffset(1,rows,3170000000L),.001);
        assertEquals(0,KillConfirmRules.rowOffset(2,rows,3170000000L),.001);
        assertEquals(14,KillConfirmRules.rowOffset(3,rows,3170000000L),.001);
    }
}
