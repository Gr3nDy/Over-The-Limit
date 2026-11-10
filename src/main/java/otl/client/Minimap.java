package otl.client;

import com.google.gson.*;
import java.io.*;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Bundled terrain only. The centered marker is the local player, never another player's position. */
final class Minimap {
    static final Minimap INSTANCE=new Minimap();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"OTL Minimap");t.setDaemon(true);return t;});
    private Map<String,String> index;
    private final Announcement announcement=new Announcement();
    private String query,requested,host="";
    private volatile int request;
    private volatile MinimapModel model;
    private volatile String status="Waiting for map";
    final MinimapRenderer renderer=new MinimapRenderer();
    final Failure failure=new Failure();
    private int view;
    private long renderedFrames;
    private boolean active(){return UtilitySettings.minimap&&ServerScope.active();}
    static final class Failure {
        private volatile long at;
        void fail(long now){if(at==0)at=now;}
        void clear(){at=0;}
        boolean failed(){return at!=0;}
        float alpha(long now){return at==0?1:Math.max(0,Math.min(1,(3500000000L-(now-at))/500000000F));}
    }
    static int nextView(int view){return (view+1)%3;}
    void cycleView(){Minecraft mc=MinecraftAccess.get();if(active()&&mc.thePlayer!=null&&mc.currentScreen==null&&!failure.failed())view=nextView(view);}
    static HudLayout.Rect expandedBounds(int width,int height,boolean coordinates){
        int side=Math.max(1,Math.min(width-8,height-(coordinates?34:8)));
        return new HudLayout.Rect((width-side)/2,(height-side)/2,side,side);
    }
    static String coordinates(double x,double y,double z){return "XYZ: "+(int)Math.floor(x)+" "+(int)Math.floor(y)+" "+(int)Math.floor(z);}
    static double[] cardinal(double x,double z,double angle,double radius){
        double px=MinimapModel.screenX(x,z,angle),py=MinimapModel.screenY(x,0,z,angle),scale=radius/Math.max(Math.abs(px),Math.abs(py));
        return new double[]{px*scale,py*scale};
    }
    static final class Announcement {
        private static final Pattern TITLE=Pattern.compile("^\\s{4,}(\\S(?:.*\\S)?)\\s{4,}$");
        private String title;private long titleAt;
        void clear(){title=null;titleAt=0;}
        String receive(String formatted,long now){
            if(ChatRules.playerMessage(formatted))return null;
            String found=null;
            for(String line:ChatRules.plain(formatted).split("[\\r\\n]+")){
                String text=line.trim();
                if(text.startsWith("Playing ")){
                    String next=MapPreview.serverQuery(text);if(next!=null){found=next;clear();}continue;
                }
                Matcher match=TITLE.matcher(line);
                if(match.matches()){title=match.group(1);titleAt=now;continue;}
                if(text.startsWith("Created by ")&&title!=null&&now>=titleAt&&now-titleAt<=5000000000L){found=title;clear();}
            }
            return found;
        }
    }
    static int side(int width,int height,int size){return Math.max(1,Math.min(140*size/100,Math.min(width,height)-8));}
    static int borderPad(){return UtilitySettings.minimapBorder?2:0;}
    static HudLayout.Rect bounds(){
        ScaledResolution sr=new ScaledResolution(MinecraftAccess.get());
        int pad=borderPad(),footer=UtilitySettings.minimapCoordinates?13:0;
        int side=side(sr.getScaledWidth()-pad*2,sr.getScaledHeight()-footer-pad*2,UtilitySettings.minimapSize);
        return HudLayout.bounds(HudLayout.MINIMAP,side+pad*2,side+pad*2+footer);
    }
    /** Vanilla inventory bevel: black outline, white highlight top-left, dark shadow bottom-right. */
    static void border(HudLayout.Rect outer,int alpha){
        int edge=alpha<<24,light=edge|0xFFFFFF,dark=edge|0x555555;
        Gui.drawRect(outer.x,outer.y,outer.x+outer.w,outer.y+1,edge);
        Gui.drawRect(outer.x,outer.y+outer.h-1,outer.x+outer.w,outer.y+outer.h,edge);
        Gui.drawRect(outer.x,outer.y,outer.x+1,outer.y+outer.h,edge);
        Gui.drawRect(outer.x+outer.w-1,outer.y,outer.x+outer.w,outer.y+outer.h,edge);
        Gui.drawRect(outer.x+1,outer.y+1,outer.x+outer.w-1,outer.y+2,light);
        Gui.drawRect(outer.x+1,outer.y+1,outer.x+2,outer.y+outer.h-1,light);
        Gui.drawRect(outer.x+1,outer.y+outer.h-2,outer.x+outer.w-1,outer.y+outer.h-1,dark);
        Gui.drawRect(outer.x+outer.w-2,outer.y+1,outer.x+outer.w-1,outer.y+outer.h-1,dark);
    }
    @SubscribeEvent public void chat(ClientChatReceivedEvent event){
        if(event.type==2||!ServerScope.active()||ChatRules.playerMessage(event.message.getFormattedText()))return;
        session();
        String next=announcement.receive(event.message.getFormattedText(),System.nanoTime());
        if(next!=null&&!next.equals(query)){query=next;requested=null;request++;model=null;failure.clear();if(view==1)view=0;status="Loading map...";}
    }
    private void session(){
        String next=ServerScope.current();
        if(!host.equals(next)){host=next;clearMap();}
    }
    private void clearMap(){query=requested=null;request++;model=null;status="Waiting for map";announcement.clear();failure.clear();}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)clearMap();}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;UtilitySettings.load();session();
        if(!UtilitySettings.minimap)view=0;
        if(!active()){
            if(model!=null||requested!=null){request++;model=null;requested=null;}
            renderer.release();return;
        }
        if(view==1&&(MinecraftAccess.get().currentScreen!=null||failure.failed()))view=0;
        if(failure.failed())return;
        if(query==null||query.equals(requested))return;
        requested=query;String target=query;int token=++request;model=null;status="Loading map...";
        worker.execute(()->{
            try{
                String asset=asset(target);
                if(asset==null){unavailable(token,null);return;}
                if(!ConfigDir.validAsset(asset)){unavailable(token,"invalid asset path: "+asset);return;}
                MinimapModel loaded=readCached(asset);
                if(loaded==null)loaded=downloadAndRead(asset);
                final MinimapModel finished=loaded;
                if(request!=token)return;
                if(finished==null){unavailable(token,"map not in this build yet: "+target);return;}
                model=finished;status="";
            }catch(Exception error){
                unavailable(token,"could not load "+target+": "+error.getMessage());
            }
        });
    }
    private void unavailable(int token,String reason){
        if(request!=token)return;
        model=null;status="Preview unavailable";failure.fail(System.nanoTime());
        if(reason!=null)System.err.println("OTL: minimap "+reason);
    }
    /** Reads a previously downloaded mesh for asset, or null; corrupt files are re-fetched. */
    private MinimapModel readCached(String asset){
        File file=mapsFile(asset);
        if(!file.isFile())return null;
        try(InputStream in=new java.io.FileInputStream(file)){return MinimapModel.read(in);}
        catch(Exception e){
            System.err.println("OTL: cached mesh unreadable, re-downloading: "+e.getMessage());
            try{java.nio.file.Files.deleteIfExists(file.toPath());}catch(java.io.IOException ignored){}
            return null;
        }
    }

    private MinimapModel downloadAndRead(String asset){
        status="Downloading map...";
        try{
            File target=mapsFile(asset);
            java.nio.file.Files.createDirectories(target.getParentFile().toPath());
            MapRemote.downloadPublic("https://raw.githubusercontent.com/Gr3nDy/Over-The-Limit/beta/Maps/"+asset,target);
            return readCached(asset);
        }catch(Exception e){System.err.println("OTL: could not download minimap mesh "+asset+": "+e.getMessage());return null;}
    }
    private File mapsFile(String asset){return ConfigDir.parts(asset.split("/"));}

    String asset(String name)throws IOException{
        if(index==null){
            Map<String,String> loaded=new LinkedHashMap<>();
            Map<String,String> sources=new HashMap<>();
            try(InputStream in=Minimap.class.getResourceAsStream("/assets/otl/maps/minimap-index.json")){
                if(in==null)throw new IOException("Minimap index missing");
                JsonArray entries=new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
                for(JsonElement entry:entries){
                    JsonObject item=entry.getAsJsonObject();String asset=item.get("asset").getAsString();
                    if(UtilitySettings.favoriteKey(asset.substring(0,asset.lastIndexOf('/')))==null||!asset.endsWith("/minimap.mesh.gz"))throw new IOException("Invalid minimap asset path");
                    String label=MapInfo.norm(item.get("name").getAsString()),path=item.get("path").getAsString();
                    String repo=path.substring(0,path.indexOf('/')),previous=loaded.get(label);
                    if(previous==null){loaded.put(label,asset);sources.put(label,repo);}
                    else if(repo.equals(sources.get(label))&&!previous.equals(asset))loaded.put(label,"");
                    loaded.put(item.get("path").getAsString(),asset);
                }
            }
            index=loaded;
        }
        String exact=index.get(name),selected=exact!=null?exact:index.get(MapInfo.norm(name));
        return selected==null||selected.isEmpty()?null:selected;
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        Minecraft mc=MinecraftAccess.get();
        if(event.type==RenderGameOverlayEvent.ElementType.CHAT&&active()&&mc.thePlayer!=null&&!mc.gameSettings.hideGUI&&!(mc.currentScreen instanceof HudEditor))draw(false,event.partialTicks);
    }
    static void drawPreview(){INSTANCE.draw(true,1);}
    private void draw(boolean preview,float partial){
        boolean large=view==1&&!preview;
        if(view==2&&!preview)return;
        float mapOpacity=large?UtilitySettings.minimapLargeOpacity/100F:1F;
        float visibility=preview?1:failure.alpha(System.nanoTime());
        float alpha=visibility*mapOpacity;int textAlpha=(int)(255*alpha);if(textAlpha<4)return;
        Minecraft mc=MinecraftAccess.get();ScaledResolution sr=new ScaledResolution(mc);HudLayout.Rect widget=bounds();
        int pad=borderPad();
        boolean showCoordinates=UtilitySettings.minimapCoordinates;
        HudLayout.Rect outer=large?expandedBounds(sr.getScaledWidth(),sr.getScaledHeight(),showCoordinates):new HudLayout.Rect(widget.x,widget.y,widget.w,widget.h-(showCoordinates?13:0));
        HudLayout.Rect box=new HudLayout.Rect(outer.x+pad,outer.y+pad,outer.w-pad*2,outer.h-pad*2);
        MinimapModel selected=model;MinimapRenderer.State saved=new MinimapRenderer.State();
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer old=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_SCISSOR_BOX,old);
        int scale=sr.getScaleFactor();
        int sx=outer.x*scale,sy=mc.displayHeight-(outer.y+outer.h)*scale,sw=outer.w*scale,sh=outer.h*scale;
        if(scissor){int ex=Math.min(sx+sw,old.get(0)+old.get(2)),ey=Math.min(sy+sh,old.get(1)+old.get(3));sx=Math.max(sx,old.get(0));sy=Math.max(sy,old.get(1));sw=Math.max(0,ex-sx);sh=Math.max(0,ey-sy);}
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(sx,sy,sw,sh);
        try{
            int background=large?UtilitySettings.minimapLargeOpacity:UtilitySettings.minimapOpacity;
            Gui.drawRect(box.x,box.y,box.x+box.w,box.y+box.h,((int)(background*255/100*visibility))<<24);
            if(pad>0)border(outer,(int)(255*visibility));
            double cx=box.x+box.w/2D,cy=box.y+box.h/2D;
            double[] player=mc.thePlayer==null?new double[3]:new double[]{mc.thePlayer.lastTickPosX+(mc.thePlayer.posX-mc.thePlayer.lastTickPosX)*partial,mc.thePlayer.lastTickPosY+(mc.thePlayer.posY-mc.thePlayer.lastTickPosY)*partial,mc.thePlayer.lastTickPosZ+(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)*partial};
            float yaw=mc.thePlayer==null?0:mc.thePlayer.prevRotationYaw+net.minecraft.util.MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw-mc.thePlayer.prevRotationYaw)*partial;
            double angle=MinimapModel.angle(UtilitySettings.minimapRotate,yaw);
            boolean staticView=(UtilitySettings.minimapStatic||large)&&selected!=null;
            double worldHalf=selected==null?box.w/2D:staticView?selected.screenRadius+1:box.w/(2D*MinimapModel.PIXELS_PER_BLOCK);
            double[] anchor=staticView?selected.center:player;
            if(selected!=null&&mc.thePlayer!=null){
                try{
                    int[] head=mc.thePlayer==null?null:KillFeed.face(mc.thePlayer.getName());
                    if(!renderer.render(selected,anchor,player,angle,box.w*scale,worldHalf,UtilitySettings.minimapCutaway,head))throw new IllegalStateException("framebuffers unavailable");
                    renderer.composite(box,alpha);renderedFrames++;
                }catch(RuntimeException error){renderer.release();unavailable(request,"rendering unavailable: "+error.getMessage());selected=null;}
            }else if(renderer.loaded())renderer.release();
            GlStateManager.enableTexture2D();GlStateManager.disableDepth();
            int color=textAlpha<<24|0xFFFFFF;
            if(selected==null){
                String label=preview?"Isometric Minimap":status;
                mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(label,box.w-8),box.x+4,box.y+5,color);
            }
            if(UtilitySettings.minimapCardinals){
                String[] labels={"N","W","E","S"};double[][] directions={{0,-1},{-1,0},{1,0},{0,1}};
                for(int i=0;i<4;i++){
                    double[] offset=cardinal(directions[i][0],directions[i][1],angle,Math.max(0,box.w/2D-8));
                    mc.fontRendererObj.drawStringWithShadow(labels[i],(float)(cx+offset[0]-mc.fontRendererObj.getStringWidth(labels[i])/2F),(float)(cy+offset[1]-4),color);
                }
            }
            GL11.glScissor(old.get(0),old.get(1),old.get(2),old.get(3));if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if(showCoordinates&&mc.thePlayer!=null){
                String text=coordinates(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
                float textScale=Math.min(1,(box.w-4F)/mc.fontRendererObj.getStringWidth(text));
                GlStateManager.pushMatrix();
                try{GlStateManager.translate(outer.x+2,outer.y+outer.h+2,0);GlStateManager.scale(textScale,textScale,1);mc.fontRendererObj.drawStringWithShadow(text,0,0,color);}
                finally{GlStateManager.popMatrix();}
            }
        }finally{
            GL11.glScissor(old.get(0),old.get(1),old.get(2),old.get(3));if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
            saved.restore();
        }
    }
}
