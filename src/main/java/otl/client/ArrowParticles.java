package otl.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Hides the critical-hit sparkle an arrow leaves behind. Arrows and their sparkles are both
 * simulated on the client, so the vanilla particle factory is wrapped once instead of filtering
 * packets; only sparkles close to a flying arrow go, melee crits are untouched.
 */
final class ArrowParticles {
    static final ArrowParticles INSTANCE=new ArrowParticles();
    /** Flying arrow positions, replaced each tick so the particle thread sees a stable array. */
    static volatile double[][] arrows=new double[0][];
    static boolean installed;
    private ArrowParticles(){}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.bowOptimizer||!UtilitySettings.bowHideArrowParticles){
            if(arrows.length!=0)arrows=new double[0][];
        }else if(mc.theWorld!=null){
            List<double[]> found=new ArrayList<>();
            for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntityArrow){
                EntityArrow a=(EntityArrow)o;
                // Clearing the client's critical flag stops the vanilla sparkle at its source, so this
                // works even when another client mod owns the particle factory.
                if(a.getIsCritical())a.setIsCritical(false);
                if(a.motionX*a.motionX+a.motionY*a.motionY+a.motionZ*a.motionZ>0.0025D)found.add(new double[]{a.posX,a.posY,a.posZ});
            }
            arrows=found.toArray(new double[found.size()][]);
        }else if(arrows.length!=0)arrows=new double[0][];
        if(!installed&&mc.effectRenderer!=null)installed=install(mc.effectRenderer);
    }

    static boolean hides(double x,double y,double z,double[][] arrows){
        if(!UtilitySettings.bowOptimizer||!UtilitySettings.bowHideArrowParticles)return false;
        for(double[] a:arrows){
            double dx=x-a[0],dy=y-a[1],dz=z-a[2];
            if(dx*dx+dy*dy+dz*dz<=2.25D)return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean install(EffectRenderer renderer){
        try{
            Map<Integer,IParticleFactory> types=ObfuscationReflectionHelper.getPrivateValue(EffectRenderer.class,renderer,"field_178932_g");
            final int crit=EnumParticleTypes.CRIT.getParticleID();
            final IParticleFactory vanilla=types==null?null:types.get(crit);
            if(vanilla==null)return false;
            types.put(crit,new IParticleFactory(){
                @Override public EntityFX getEntityFX(int id,World world,double x,double y,double z,double xs,double ys,double zs,int... args){
                    return hides(x,y,z,arrows)?null:vanilla.getEntityFX(id,world,x,y,z,xs,ys,zs,args);
                }
            });
            return true;
        }catch(Exception ex){
            System.err.println("OTL: arrow particle filter unavailable: "+ex.getMessage());
            return false;
        }
    }
}
