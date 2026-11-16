package otl.client;

import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import org.junit.Test;
import static org.junit.Assert.*;

public class BallParticlesTest {
    private S2APacketParticles packet(EnumParticleTypes type,float x,float y,float z){
        return new S2APacketParticles(type,false,x,y,z,0,0,0,0,1);
    }

    @Test public void onlyRedstoneNearAFlightIsMatched(){
        double[][] balls={{10,20,30,1,0,0}};
        assertTrue(BallParticles.matches(packet(EnumParticleTypes.REDSTONE,10,20,30),balls));
        assertTrue(BallParticles.matches(packet(EnumParticleTypes.REDSTONE,12,20,30),balls));
        assertFalse(BallParticles.matches(packet(EnumParticleTypes.REDSTONE,30,20,30),balls));
        assertFalse(BallParticles.matches(packet(EnumParticleTypes.SMOKE_NORMAL,10,20,30),balls));
        assertFalse(BallParticles.matches(packet(EnumParticleTypes.REDSTONE,10,20,30),new double[0][]));
    }
}
