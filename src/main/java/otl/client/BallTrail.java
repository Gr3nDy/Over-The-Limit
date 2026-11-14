package otl.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.projectile.EntitySnowball;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.opengl.GL11;

/**
 * A contrail behind the thrown Football: the last second and a half of the ball's flight drawn as
 * one fading white line, so a long pass is easy to follow across the map. Positions are recorded
 * per tick and drawn through the fixed-function pipeline like the Football mesh itself - no
 * particles.
 */
final class BallTrail {
    static final BallTrail INSTANCE=new BallTrail();
    /** Ticks of flight a trail remembers: half a tick per point, thirty points, a second and a half. */
    static final int MAX=30;
    static final Map<Integer,List<double[]>> paths=new HashMap<>();
    static final Map<Integer,Integer> lastSeen=new HashMap<>();
    static int tickCounter;
    private static Object world;
    private BallTrail(){}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=world){world=mc.theWorld;clear();}
        tick(mc);
    }

    static void tick(Minecraft mc){
        if(!UtilitySettings.touchdown||!UtilitySettings.touchdownTrail||!Touchdown.active()||mc.theWorld==null){
            clear();return;
        }
        tickCounter++;
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntitySnowball){
            EntitySnowball b=(EntitySnowball)o;
            add(b.getEntityId(),b.posX,b.posY,b.posZ);
        }
        for(Integer id:new ArrayList<Integer>(lastSeen.keySet()))
            if(tickCounter-lastSeen.get(id)>MAX){paths.remove(id);lastSeen.remove(id);}
    }

    /** One flight point; keeps only the newest MAX so the trail has a fixed length. */
    static void add(int id,double x,double y,double z){
        List<double[]> path=paths.get(id);
        if(path==null){path=new ArrayList<double[]>();paths.put(id,path);}
        path.add(new double[]{x,y,z});
        if(path.size()>MAX)path.remove(0);
        lastSeen.put(id,tickCounter);
    }

    static void clear(){paths.clear();lastSeen.clear();}

    /** Recorded points for one entity, for the smoke test. */
    static int points(int id){
        List<double[]> path=paths.get(id);
        return path==null?0:path.size();
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event){
        render(MinecraftAccess.get().getRenderManager(),event.partialTicks);
    }

    static void render(RenderManager view,float partialTicks){
        if(paths.isEmpty()||view==null)return;
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int width=GL11.glGetInteger(GL11.GL_LINE_WIDTH);
        GlStateManager.disableTexture2D();GlStateManager.disableLighting();
        GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2F);
        for(List<double[]> path:paths.values()){
            if(path.size()<2)continue;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for(int i=0;i<path.size();i++){
                double[] p=path.get(i);
                float a=(i+1)/(float)path.size();
                GlStateManager.color(1F,1F,1F,0.06F+0.6F*a); // fade out toward the tail
                GL11.glVertex3d(p[0]-view.viewerPosX,p[1]-view.viewerPosY,p[2]-view.viewerPosZ);
            }
            GL11.glEnd();
        }
        GlStateManager.color(1F,1F,1F,1F);
        GL11.glLineWidth(width);
        if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
        if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
        if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
        if(depth)GL11.glEnable(GL11.GL_DEPTH_TEST);else GL11.glDisable(GL11.GL_DEPTH_TEST);
    }
}
