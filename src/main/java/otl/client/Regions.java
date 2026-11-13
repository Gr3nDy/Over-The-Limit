package otl.client;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Draws wireframe borders of the current map's regions. Map detection is identical to the
 * Minimap's: the same chat announcement parser, the same server-session reset and the same
 * world-unload clear. A map that cannot be resolved, or has no regions, draws nothing.
 */
final class Regions {
    static final Regions INSTANCE=new Regions();
    private static final int[] PALETTE={0x55FF55,0x55FFFF,0xFF5555,0xFFFF55,0xFF55FF,0xAAAAFF};
    private final Minimap.Announcement announcement=new Minimap.Announcement();
    private String host="",query,requested;
    private volatile MapInfo map;
    private int request;
    private Object world;
    private Regions(){}

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true) public void chat(ClientChatReceivedEvent event){
        // Any PGM server works: the announcement parser is specific enough on its own.
        if(event.type==2||ChatRules.playerMessage(event.message.getFormattedText()))return;
        syncWorld();
        session();
        String next=announcement.receive(event.message.getFormattedText(),System.nanoTime());
        if(next!=null&&!next.equals(query)){query=next;requested=null;request++;map=null;load();}
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote){world=null;clearMap();}}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        syncWorld();
        session();
        if(!ServerScope.active()&&map!=null&&!map.bundled){request++;map=null;}
    }

    private void syncWorld(){
        Minecraft mc=MinecraftAccess.get();
        if(world!=mc.theWorld){world=mc.theWorld;clearMap();}
    }

    private void session(){
        String next=ServerScope.current();
        // Outside the known hosts an empty scope must not wipe a map found by the chat parser.
        if(next.isEmpty())return;
        if(!host.equals(next)){host=next;clearMap();}
    }

    private void clearMap(){query=requested=null;request++;map=null;announcement.clear();}

    /** Same resolution path as the Minimap: bundled metadata first, then the public repository. */
    private void load(){
        String target=query;
        if(target==null||target.equals(requested))return;
        requested=target;int token=++request;
        MapRemote.lookup(target,found->{
            if(request!=token)return;
            // No metadata, or a map without regions: nothing to draw.
            map=found!=null&&!found.regions.isEmpty()?found:null;
        });
    }

    /** Innermost region containing the point; null when the player is outside every region. */
    static MapInfo.Region regionAt(MapInfo map,double x,double y,double z){
        MapInfo.Region best=null;double bestVolume=Double.MAX_VALUE;
        for(MapInfo.Region r:map.regions){
            if(x<r.min[0]||x>r.max[0]||y<r.min[1]||y>r.max[1]||z<r.min[2]||z>r.max[2])continue;
            double volume=(r.max[0]-r.min[0]+1)*(r.max[1]-r.min[1]+1)*(r.max[2]-r.min[2]+1);
            if(volume<bestVolume){bestVolume=volume;best=r;}
        }
        return best;
    }

    /** HUD text for a region; the prefix is optional. */
    static String hudLabel(String region){
        return (UtilitySettings.regionHudPrefix?"Region: ":"")+region;
    }

    /** Region the local player stands in, or null. Used for the HUD label and its editor box. */
    static String currentRegion(){
        Minecraft mc=MinecraftAccess.get();
        MapInfo selected=INSTANCE.map;
        if(selected==null||selected.regions.isEmpty()||mc.thePlayer==null)return null;
        MapInfo.Region region=regionAt(selected,mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
        return region==null||region.name.isEmpty()?null:region.name;
    }

    /** HUD box for the region text, in the same coordinate space the editor drags. */
    static HudLayout.Rect bounds(){
        Minecraft mc=MinecraftAccess.get();
        String region=currentRegion();
        String label=hudLabel(region==null?"Example":region);
        int w=Math.max(24,mc.fontRendererObj.getStringWidth(label)+6);
        return HudLayout.bounds(HudLayout.REGION,w,12);
    }

    /** Editor preview: greyed with no background while the text is off, so the toggle shows its state. */
    static void drawPreview(){
        Minecraft mc=MinecraftAccess.get();
        String region=currentRegion();
        String label=hudLabel(region==null?"Example":region);
        HudLayout.Rect box=bounds();
        boolean on=UtilitySettings.regionHud;
        int alpha=on?UtilitySettings.regionHudBackground*255/100:0;
        if(alpha>0)Gui.drawRect(box.x,box.y,box.x+box.w,box.y+box.h,alpha<<24);
        mc.fontRendererObj.drawStringWithShadow(label,box.x+3,box.y+4,on?0xFFFFFFFF:0xFF777777);
    }

    private static void drawLabel(Minecraft mc,String label,int x,int y){
        int textWidth=mc.fontRendererObj.getStringWidth(label);
        int alpha=UtilitySettings.regionHudBackground*255/100;
        if(alpha>0)Gui.drawRect(x-3,y-1,x+textWidth+3,y+10,alpha<<24);
        mc.fontRendererObj.drawStringWithShadow(label,x,y+2,0xFFFFFFFF);
    }

    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(event.type!=RenderGameOverlayEvent.ElementType.CHAT)return;
        UtilitySettings.load();
        Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.regionHud||mc.thePlayer==null||mc.gameSettings.hideGUI)return;
        if(mc.currentScreen instanceof HudEditor)return; // the editor draws its own preview
        String region=currentRegion();
        if(region==null)return;
        String label=hudLabel(region);
        HudLayout.Rect box=bounds();
        drawLabel(mc,label,box.x+3,box.y+2);
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event){
        UtilitySettings.load();
        MapInfo selected=map;
        Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.regionOverlay||selected==null||selected.regions.isEmpty()||mc.theWorld==null)return;
        Entity camera=mc.getRenderViewEntity();
        if(camera==null)return;
        float partial=event.partialTicks;
        double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(-cx,-cy,-cz);GlStateManager.disableLighting();GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);
            Tessellator tess=Tessellator.getInstance();WorldRenderer wr=tess.getWorldRenderer();
            wr.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
            int radius=(mc.gameSettings.renderDistanceChunks+1)*16;
            for(MapInfo.Region r:selected.regions){
                if(Math.abs((r.min[0]+r.max[0])/2-cx)>radius||Math.abs((r.min[2]+r.max[2])/2-cz)>radius)continue;
                box(wr,r,color(r.name));
            }
            tess.draw();
        }finally{
            GlStateManager.popMatrix();GlStateManager.depthMask(mask);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            GlStateManager.color(1,1,1,1);
        }
    }

    /** Deterministic color per region name, so a map's borders look stable between sessions. */
    private static int color(String name){
        return name.isEmpty()?0xFFFF55:PALETTE[Math.abs(name.hashCode())%PALETTE.length];
    }

    private static void box(WorldRenderer wr,MapInfo.Region r,int rgb){
        int cr=(rgb>>16)&255,cg=(rgb>>8)&255,cb=rgb&255,a=0x90;
        double x0=r.min[0]-.01,y0=r.min[1]-.01,z0=r.min[2]-.01,x1=r.max[0]+.01,y1=r.max[1]+.01,z1=r.max[2]+.01;
        edge(wr,cr,cg,cb,a,x0,y0,z0,x1,y0,z0);edge(wr,cr,cg,cb,a,x1,y0,z0,x1,y0,z1);
        edge(wr,cr,cg,cb,a,x1,y0,z1,x0,y0,z1);edge(wr,cr,cg,cb,a,x0,y0,z1,x0,y0,z0);
        edge(wr,cr,cg,cb,a,x0,y1,z0,x1,y1,z0);edge(wr,cr,cg,cb,a,x1,y1,z0,x1,y1,z1);
        edge(wr,cr,cg,cb,a,x1,y1,z1,x0,y1,z1);edge(wr,cr,cg,cb,a,x0,y1,z1,x0,y1,z0);
        edge(wr,cr,cg,cb,a,x0,y0,z0,x0,y1,z0);edge(wr,cr,cg,cb,a,x1,y0,z0,x1,y1,z0);
        edge(wr,cr,cg,cb,a,x1,y0,z1,x1,y1,z1);edge(wr,cr,cg,cb,a,x0,y0,z1,x0,y1,z1);
    }

    private static void edge(WorldRenderer wr,int cr,int cg,int cb,int a,double ax,double ay,double az,double bx,double by,double bz){
        wr.pos(ax,ay,az).color(cr,cg,cb,a).endVertex();
        wr.pos(bx,by,bz).color(cr,cg,cb,a).endVertex();
    }
}
