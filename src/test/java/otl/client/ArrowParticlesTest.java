package otl.client;

import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArrowParticlesTest {
    private boolean optimizer, hide;

    @Before public void defaults(){
        optimizer=UtilitySettings.bowOptimizer;hide=UtilitySettings.bowHideArrowParticles;
        UtilitySettings.bowOptimizer=true;UtilitySettings.bowHideArrowParticles=true;
    }

    @After public void restore(){
        UtilitySettings.bowOptimizer=optimizer;UtilitySettings.bowHideArrowParticles=hide;
        ArrowParticles.arrows=new double[0][];
    }

    private static S2APacketParticles particle(EnumParticleTypes type,double x,double y,double z){
        return new S2APacketParticles(type,false,(float)x,(float)y,(float)z,0,0,0,0,1);
    }

    @Test public void onlySparklesNearAFlyingArrowAreHidden(){
        double[][] arrows={{10,20,30}};
        assertTrue(ArrowParticles.hides(10,20,30,arrows));
        assertTrue(ArrowParticles.hides(11.4,20,30,arrows));
        assertFalse(ArrowParticles.hides(13,20,30,arrows));
        assertFalse(ArrowParticles.hides(10,20,30,new double[0][]));
    }

    /** The packet carries the sparkle where the arrow was up to a tick earlier, so the motion leads it. */
    @Test public void sparklesAheadOfTheLastSampledPositionAreStillTheArrowsOwn(){
        double[][] fast={{10,20,30,1.2,0,0}};
        assertTrue(ArrowParticles.hides(11.2,20,30,fast));
        assertTrue(ArrowParticles.hides(12.4,20,30,fast));
        assertFalse(ArrowParticles.hides(14,20,30,fast));
        // A trailing sparkle from the tick before the sample goes too.
        assertTrue(ArrowParticles.hides(8.8,20,30,fast));
    }

    @Test public void serversideCriticalSparklesAreDroppedForAnArrowInFlight(){
        ArrowParticles.arrows=new double[][]{{10,20,30,0.9,0,0}};
        assertTrue(ArrowParticles.drops(particle(EnumParticleTypes.CRIT,10.9,20,30)));
        assertFalse(ArrowParticles.drops(particle(EnumParticleTypes.CRIT,40,20,30)));
        // A melee crit carries a different particle type and must survive.
        assertFalse(ArrowParticles.drops(particle(EnumParticleTypes.CRIT_MAGIC,10.9,20,30)));
        assertFalse(ArrowParticles.drops(particle(EnumParticleTypes.SMOKE_NORMAL,10.9,20,30)));
    }

    @Test public void optionOffOrModuleOffKeepsEverySparkle(){
        double[][] arrows={{10,20,30}};
        ArrowParticles.arrows=arrows;
        UtilitySettings.bowHideArrowParticles=false;
        assertFalse(ArrowParticles.hides(10,20,30,arrows));
        assertFalse(ArrowParticles.drops(particle(EnumParticleTypes.CRIT,10,20,30)));
        UtilitySettings.bowHideArrowParticles=true;UtilitySettings.bowOptimizer=false;
        assertFalse(ArrowParticles.hides(10,20,30,arrows));
        assertFalse(ArrowParticles.drops(particle(EnumParticleTypes.CRIT,10,20,30)));
    }
}
