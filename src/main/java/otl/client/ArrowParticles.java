package otl.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Hides the critical-hit sparkle an arrow leaves behind. Arrows and their sparkles are both
 * simulated on the client, so the vanilla particle factory is wrapped once instead of filtering
 * packets; only sparkles close to a flying arrow go, melee crits are untouched. Sparkles a server
 * broadcasts as particles are dropped the same way, since a Spigot server sends those too.
 */
final class ArrowParticles {
    static final ArrowParticles INSTANCE=new ArrowParticles();
    private static final String HANDLER="otl_arrow_particles";
    /** Flying arrow positions and motion, replaced each tick so the particle thread sees a stable array. */
    static volatile double[][] arrows=new double[0][];
    static boolean installed;
    private static EffectRenderer renderer;
    private volatile boolean enabled;
    private Channel channel;
    private ArrowParticles(){}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        Minecraft mc=MinecraftAccess.get();
        if(event.phase==TickEvent.Phase.START){
            // Before the world moves the arrows: the first tick after release is the puff you see at
            // your own bow, and a tick-end clear is always one tick too late for it.
            if(UtilitySettings.bowOptimizer&&UtilitySettings.bowHideArrowParticles&&mc.theWorld!=null)
                for(Object o:mc.theWorld.loadedEntityList)
                    if(o instanceof EntityArrow&&((EntityArrow)o).getIsCritical())((EntityArrow)o).setIsCritical(false);
            return;
        }
        enabled=UtilitySettings.bowOptimizer&&UtilitySettings.bowHideArrowParticles;
        if(!enabled){
            if(arrows.length!=0)arrows=new double[0][];
        }else if(mc.theWorld!=null){
            List<double[]> found=new ArrayList<>();
            for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntityArrow){
                EntityArrow a=(EntityArrow)o;
                // Clearing the client's critical flag stops the vanilla sparkle at its source, so this
                // works even when another client mod owns the particle factory.
                if(a.getIsCritical())a.setIsCritical(false);
                if(a.motionX*a.motionX+a.motionY*a.motionY+a.motionZ*a.motionZ>0.0025D)
                    found.add(new double[]{a.posX,a.posY,a.posZ,a.motionX,a.motionY,a.motionZ});
            }
            arrows=found.toArray(new double[found.size()][]);
        }else if(arrows.length!=0)arrows=new double[0][];
        // The renderer is rebuilt with every world, so a permanent "installed" flag lost the wrapper.
        if(mc.effectRenderer!=null&&mc.effectRenderer!=renderer){
            renderer=mc.effectRenderer;installed=install(mc.effectRenderer);
        }
        listen(mc);
    }

    /** Drops the server's own copy of the sparkle, which no client-side flag can stop. */
    private void listen(Minecraft mc){
        NetworkManager manager=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        Channel next=enabled&&manager!=null?manager.channel():null;
        if(channel==next)return;
        if(channel!=null&&channel.pipeline().get(HANDLER)!=null)channel.pipeline().remove(HANDLER);
        channel=next;
        if(next==null)return;
        try{
            ChannelHandlerContext target=next.pipeline().context(manager);
            if(target==null)throw new IllegalStateException("Client packet handler not found");
            next.pipeline().addBefore(target.name(),HANDLER,new ChannelInboundHandlerAdapter(){
                @Override public void channelRead(ChannelHandlerContext ctx,Object packet){
                    if(packet instanceof S2APacketParticles&&drops((S2APacketParticles)packet))return;
                    ctx.fireChannelRead(packet);
                }
            });
        }catch(RuntimeException ex){channel=null;System.err.println("OTL: arrow particle filter unavailable: "+ex.getMessage());}
    }

    /** True for a crit-sparkle packet that belongs to an arrow already in flight. */
    static boolean drops(S2APacketParticles packet){
        if(!UtilitySettings.bowOptimizer||!UtilitySettings.bowHideArrowParticles)return false;
        if(packet.getParticleType()!=EnumParticleTypes.CRIT)return false;
        return hides(packet.getXCoordinate(),packet.getYCoordinate(),packet.getZCoordinate(),arrows);
    }

    static boolean hides(double x,double y,double z,double[][] arrows){
        if(!UtilitySettings.bowOptimizer||!UtilitySettings.bowHideArrowParticles)return false;
        for(double[] a:arrows){
            // A tick of travel leads the sampled position, so walk the arrow's own motion too.
            double vx=a.length>3?a[3]:0,vy=a.length>3?a[4]:0,vz=a.length>3?a[5]:0;
            for(int step=-1;step<=2;step++){
                double dx=x-a[0]-step*vx,dy=y-a[1]-step*vy,dz=z-a[2]-step*vz;
                if(dx*dx+dy*dy+dz*dz<=2.25D)return true;
            }
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
