package otl.client;

import java.util.ArrayList;
import java.util.List;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Hides PGM's redstone trail only near a visible thrown football. */
final class BallParticles {
    static final BallParticles INSTANCE=new BallParticles();
    private static final String HANDLER="otl_ball_particles";
    private volatile double[][] balls=new double[0][];
    private volatile boolean enabled;
    private volatile double[] recentSpawn;
    private volatile long spawnedAt;
    private Channel channel;
    private BallParticles(){}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        enabled=UtilitySettings.touchdownHideBallParticles&&Touchdown.active()&&mc.theWorld!=null;
        if(!enabled)recentSpawn=null;
        List<double[]> positions=new ArrayList<>();
        if(enabled)for(Object item:mc.theWorld.loadedEntityList)if(item instanceof EntitySnowball){
            EntitySnowball ball=(EntitySnowball)item;
            positions.add(new double[]{ball.posX,ball.posY,ball.posZ,ball.motionX,ball.motionY,ball.motionZ});
        }
        balls=positions.toArray(new double[positions.size()][]);
        NetworkManager manager=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        Channel next=!enabled||manager==null?null:manager.channel();
        if(channel==next)return;
        if(channel!=null&&channel.pipeline().get(HANDLER)!=null)channel.pipeline().remove(HANDLER);
        channel=next;
        if(next==null)return;
        try{
            ChannelHandlerContext target=next.pipeline().context(manager);
            if(target==null)throw new IllegalStateException("Client packet handler not found");
            next.pipeline().addBefore(target.name(),HANDLER,new ChannelInboundHandlerAdapter(){
                @Override public void channelRead(ChannelHandlerContext ctx,Object packet){
                    if(enabled&&packet instanceof S0EPacketSpawnObject){
                        S0EPacketSpawnObject spawn=(S0EPacketSpawnObject)packet;
                        if(spawn.getType()==61){
                            recentSpawn=new double[]{spawn.getX()/32D,spawn.getY()/32D,spawn.getZ()/32D,spawn.getSpeedX()/8000D,spawn.getSpeedY()/8000D,spawn.getSpeedZ()/8000D};
                            spawnedAt=System.nanoTime();
                        }
                    }
                    if(packet instanceof S2APacketParticles&&enabled){
                        S2APacketParticles p=(S2APacketParticles)packet;
                        double[] spawn=recentSpawn;
                        if(matches(p,balls)||(spawn!=null&&System.nanoTime()-spawnedAt<250_000_000L&&matches(p,new double[][]{spawn})))return;
                    }
                    ctx.fireChannelRead(packet);
                }
            });
        }catch(RuntimeException ex){channel=null;System.err.println("OTL: ball particle filter unavailable: "+ex.getMessage());}
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event){
        if(event.world.isRemote){enabled=false;balls=new double[0][];recentSpawn=null;}
    }

    static boolean matches(S2APacketParticles packet,double[][] balls){
        if(packet.getParticleType()!=EnumParticleTypes.REDSTONE)return false;
        // shortcut: nearby unrelated redstone particles can match; use entity-linked packets if PGM exposes them.
        for(double[] b:balls)for(int step=-1;step<=2;step++){
            double x=packet.getXCoordinate()-b[0]-step*b[3],y=packet.getYCoordinate()-b[1]-step*b[4],z=packet.getZCoordinate()-b[2]-step*b[5];
            if(x*x+y*y+z*z<=4)return true;
        }
        return false;
    }
}
