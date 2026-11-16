package otl.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntitySnowball;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * A contrail behind the thrown Football and, separately, behind a flying arrow: the last second
 * and a half of flight drawn as one fading white line, so a long pass or shot is easy to follow
 * across the map. Positions are recorded per tick and drawn through the fixed-function pipeline
 * like the Football mesh itself - no particles.
 */
final class BallTrail {
    static final BallTrail INSTANCE=new BallTrail();
    /** Ticks of flight a trail remembers: half a tick per point, thirty points, a second and a half. */
    static final int MAX=30;
    static final Map<Integer,List<double[]>> paths=new HashMap<>();
    static final Map<Integer,Integer> lastSeen=new HashMap<>();
    /** Arrows get their own, thinner trail, so the two never share a path. */
    static final Map<Integer,List<double[]>> arrowPaths=new HashMap<>();
    static final Map<Integer,Integer> arrowSeen=new HashMap<>();
    static int tickCounter;
    private static FloatBuffer points=BufferUtils.createFloatBuffer(16384),colors=BufferUtils.createFloatBuffer(16384);
    private static Object world;
    private BallTrail(){}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=world){world=mc.theWorld;clear();}
        tick(mc);
    }

    static void tick(Minecraft mc){
        tickCounter++;
        if(mc.theWorld==null){clear();clearArrows();return;}
        if(!UtilitySettings.touchdown||!UtilitySettings.touchdownTrail||!Touchdown.active())clear();
        else for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntitySnowball){
            EntitySnowball b=(EntitySnowball)o;
            add(b.getEntityId(),b.posX,b.posY,b.posZ);
        }
        if(!UtilitySettings.bowOptimizer||!UtilitySettings.bowArrowTrail)clearArrows();
        else for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntityArrow){
            EntityArrow a=(EntityArrow)o;
            // An arrow in the ground still reports a position; a still one would leave a dot.
            if(a.motionX*a.motionX+a.motionY*a.motionY+a.motionZ*a.motionZ>0.0025D)addArrow(a.getEntityId(),a.posX,a.posY,a.posZ);
        }
        expire(lastSeen,paths);expire(arrowSeen,arrowPaths);
    }

    /**
     * Drop one old point per tick once an entity stops being recorded, so a trail that lost its
     * ball retracts from the tail instead of vanishing whole when the entity unloads.
     */
    private static void expire(Map<Integer,Integer> seen,Map<Integer,List<double[]>> paths){
        Iterator<Map.Entry<Integer,Integer>> it=seen.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<Integer,Integer> entry=it.next();
            if(tickCounter<=entry.getValue())continue;
            List<double[]> path=paths.get(entry.getKey());
            if(path==null){it.remove();continue;}
            if(!path.isEmpty())path.remove(0);
            if(path.size()<2){paths.remove(entry.getKey());it.remove();}
        }
    }

    /** One flight point; keeps only the newest MAX so the trail has a fixed length. */
    static void add(int id,double x,double y,double z){
        append(paths,lastSeen,id,x,y,z);
    }

    static void addArrow(int id,double x,double y,double z){
        append(arrowPaths,arrowSeen,id,x,y,z);
    }

    private static void append(Map<Integer,List<double[]>> paths,Map<Integer,Integer> lastSeen,int id,double x,double y,double z){
        List<double[]> path=paths.get(id);
        if(path==null){path=new ArrayList<double[]>();paths.put(id,path);}
        path.add(new double[]{x,y,z});
        if(path.size()>MAX)path.remove(0);
        lastSeen.put(id,tickCounter);
    }

    static void clear(){paths.clear();lastSeen.clear();}

    static void clearArrows(){arrowPaths.clear();arrowSeen.clear();}

    /** Recorded points for one entity, for the smoke test. */
    static int points(int id){
        List<double[]> path=paths.get(id);
        return path==null?0:path.size();
    }

    static int arrowPoints(int id){
        List<double[]> path=arrowPaths.get(id);
        return path==null?0:path.size();
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event){
        render(MinecraftAccess.get().getRenderManager(),event.partialTicks);
    }

    static void render(RenderManager view,float partialTicks){
        renderPaths(view,paths,2.5F);
        renderPaths(view,arrowPaths,1.2F);
    }

    static void renderPaths(RenderManager view,Map<Integer,List<double[]>> paths,float lineWidth){
        if(paths.isEmpty()||view==null)return;
        // Every visible trail goes into one buffered draw: the cost no longer grows with a draw call and
        // a GL colour change per vertex, which is what made busy bow fights stutter.
        int vertices=0;
        for(List<double[]> path:paths.values())if(path.size()>1)vertices+=(path.size()-1)*2;
        if(vertices==0)return;
        if(points.capacity()<vertices*3)points=BufferUtils.createFloatBuffer(vertices*3);
        if(colors.capacity()<vertices*4)colors=BufferUtils.createFloatBuffer(vertices*4);
        points.clear();colors.clear();
        float viewX=(float)view.viewerPosX,viewY=(float)view.viewerPosY,viewZ=(float)view.viewerPosZ;
        for(List<double[]> path:paths.values()){
            int size=path.size();
            for(int i=1;i<size;i++){
                double[] from=path.get(i-1),to=path.get(i);
                points.put((float)from[0]-viewX).put((float)from[1]-viewY).put((float)from[2]-viewZ);
                points.put((float)to[0]-viewX).put((float)to[1]-viewY).put((float)to[2]-viewZ);
                colors.put(1F).put(1F).put(1F).put(alpha(i-1,size));
                colors.put(1F).put(1F).put(1F).put(alpha(i,size));
            }
        }
        points.flip();colors.flip();
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        float width=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GlStateManager.disableTexture2D();GlStateManager.disableLighting();
        GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(lineWidth);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glVertexPointer(3,0,points);
        GL11.glColorPointer(4,0,colors);
        GL11.glDrawArrays(GL11.GL_LINES,0,vertices);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.color(1F,1F,1F,1F);
        GL11.glLineWidth(width);
        if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
        if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
        if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
        if(depth)GL11.glEnable(GL11.GL_DEPTH_TEST);else GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /** Newest end is brightest; the tail reaches zero so removed points never pop. */
    private static float alpha(int point,int size){return 0.6F*(point+1)/(float)size;}
}
