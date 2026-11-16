package otl.client;

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

    @Test public void onlySparklesNearAFlyingArrowAreHidden(){
        double[][] arrows={{10,20,30}};
        assertTrue(ArrowParticles.hides(10,20,30,arrows));
        assertTrue(ArrowParticles.hides(11.4,20,30,arrows));
        assertFalse(ArrowParticles.hides(13,20,30,arrows));
        assertFalse(ArrowParticles.hides(10,20,30,new double[0][]));
    }

    @Test public void optionOffOrModuleOffKeepsEverySparkle(){
        double[][] arrows={{10,20,30}};
        UtilitySettings.bowHideArrowParticles=false;
        assertFalse(ArrowParticles.hides(10,20,30,arrows));
        UtilitySettings.bowHideArrowParticles=true;UtilitySettings.bowOptimizer=false;
        assertFalse(ArrowParticles.hides(10,20,30,arrows));
    }
}
