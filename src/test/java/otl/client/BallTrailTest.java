package otl.client;

import org.junit.Test;

import static org.junit.Assert.*;

public class BallTrailTest {
    @Test public void keepsTheLastThirtyPoints(){
        BallTrail.clear();BallTrail.tickCounter=0;
        for(int i=0;i<35;i++)BallTrail.add(1,i,0,0);
        assertEquals(30,BallTrail.points(1));
        assertEquals(5,BallTrail.paths.get(1).get(0)[0],1.0E-9);
        assertEquals(34,BallTrail.paths.get(1).get(29)[0],1.0E-9);
    }

    @Test public void trailsOfDifferentBallsDoNotMix(){
        BallTrail.clear();BallTrail.tickCounter=0;
        BallTrail.add(1,0,0,0);
        BallTrail.add(2,9,9,9);
        assertEquals(1,BallTrail.points(1));
        assertEquals(1,BallTrail.points(2));
        assertEquals(9,BallTrail.paths.get(2).get(0)[0],1.0E-9);
    }
}
