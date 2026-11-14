package otl.client;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ReplayPlayTest {
    private static ReplayPlay.Frame frame(int tick,Map<Integer,double[]> entities){
        ReplayPlay.Frame f=new ReplayPlay.Frame(tick);
        if(entities!=null)f.entities.putAll(entities);
        return f;
    }
    private static Map<Integer,double[]> at(int id,double x,double y,double z){
        java.util.HashMap<Integer,double[]> m=new java.util.HashMap<>();
        m.put(id,new double[]{x,y,z,0,0});
        return m;
    }

    @Test public void replaysTheLastBallFlight(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<20;t++)frames.add(frame(t,at(1,t,t,0)));            // an older throw
        for(int t=30;t<45;t++)frames.add(frame(t,at(2,t,5,0)));            // the play
        for(int t=30;t<60;t++)frames.add(frame(t,at(3,t,4,0)));            // players around
        frames.add(frame(60,at(3,58,4,0)));                                // the run to the end zone
        List<ReplayPlay.Event> events=new ArrayList<>();
        events.add(new ReplayPlay.Event(29,true,"Ace"));
        events.add(new ReplayPlay.Event(45,false,"Ace"));                  // caught it
        ReplayPlay.Play p=ReplayPlay.build(frames,events,60,"Ace",java.util.Collections.singletonMap("Ace",3),java.util.Collections.singleton(2));
        assertNotNull(p);
        assertEquals(2,p.ballId);
        assertEquals(3,p.scorerId);
        assertEquals(45,p.catchTick.intValue());
        assertEquals(10,p.fromTick);                                       // half a second before the throw
        assertEquals(60,p.toTick);
    }

    @Test public void ignoresOlderFlightsAndEmptyBuffers(){
        // A flight that ended outside the eight-second window is not the play; a flight that just
        // ended beside the touchdown would be indistinguishable from the play, so it is not tested.
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<10;t++)frames.add(frame(t,at(1,t,t,0)));
        for(int t=0;t<=180;t++)frames.add(frame(t,at(3,t,4,0)));
        ReplayPlay.Play p=ReplayPlay.build(frames,new ArrayList<>(),180,null,new java.util.HashMap<>(),java.util.Collections.singleton(1));
        assertNull("A flight that ended long before the touchdown is not the play",p);
        assertTrue(ReplayPlay.build(new ArrayList<>(),new ArrayList<>(),0,null,new java.util.HashMap<>(),new java.util.HashSet<>())==null);
    }

    @Test public void namesTheScorerFromWhoeverHoldsTheBallWhenUnnamed(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<10;t++)frames.add(frame(t,at(1,t,5,0)));
        frames.add(frame(10,at(1,10,5,0)));frames.add(frame(10,at(2,10.5,5,0)));
        ReplayPlay.Play p=ReplayPlay.build(frames,new ArrayList<>(),10,null,new java.util.HashMap<>(),java.util.Collections.singleton(1));
        assertNotNull(p);
        assertEquals("the ball ended beside player 2",2,p.scorerId);
    }

    @Test public void unknownPlayersStillReplayTheBall(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<10;t++)frames.add(frame(t,at(1,t,5,0)));
        frames.add(frame(10,at(1,10,5,0)));
        ReplayPlay.Play p=ReplayPlay.build(frames,new ArrayList<>(),10,"Stranger",new java.util.HashMap<>(),java.util.Collections.singleton(1));
        assertNotNull(p);
        assertEquals(-1,p.scorerId);
    }

    @Test public void replaysTheCarriersRunWhenTheBallWasNeverThrown(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<=60;t++)frames.add(frame(t,at(2,t,5,0)));
        ReplayPlay.Play p=ReplayPlay.build(frames,new ArrayList<>(),60,"Ace",java.util.Collections.singletonMap("Ace",2),new java.util.HashSet<>());
        assertNotNull(p);
        assertTrue("a run with no throw replays the carrier",p.solo);
        assertEquals(2,p.ballId);
        assertEquals(2,p.scorerId);
        assertEquals(0,p.fromTick);
    }

    @Test public void soloRunWithAnUnknownScorerReplaysNothing(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        for(int t=0;t<=60;t++)frames.add(frame(t,at(2,t,5,0)));
        assertNull(ReplayPlay.build(frames,new ArrayList<>(),60,null,new java.util.HashMap<>(),new java.util.HashSet<>()));
    }

    @Test public void interpolatesAcrossMissingFrames(){
        List<ReplayPlay.Frame> frames=new ArrayList<>();
        frames.add(frame(0,at(1,0,0,0)));
        frames.add(frame(10,at(1,10,0,0)));
        double[] mid=ReplayPlay.lerp(frames,1,5);
        assertEquals(5,mid[0],1.0E-9);
        assertNull(ReplayPlay.lerp(frames,9,5));
        double[] past=ReplayPlay.lerp(frames,1,-3);
        assertEquals(0,past[0],1.0E-9);
    }
}
