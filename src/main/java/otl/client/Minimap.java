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
    private volatile boolean permanentFailure;
    private volatile long retryAt;
    private int retries;
    private static final int MAX_RETRIES=3;
    private static final long RETRY_NANOS=10_000_000_000L;
    private volatile boolean altZoom;
    private int view;
    private long renderedFrames;
    private boolean active(){return UtilitySettings.minimap&&ServerScope.active();}

    static final double[] ZOOM_SCALE={1,2,4,7,12};

    void cycleZoom(){Minecraft mc=MinecraftAccess.get();if(active()&&mc.thePlayer!=null&&mc.currentScreen==null)altZoom=!altZoom;}
    int effectiveZoom(){return altZoom?UtilitySettings.minimapAltZoom:UtilitySettings.minimapZoom;}

    static int nextView(int view){return (view+1)%3;}
    void cycleView(){Minecraft mc=MinecraftAccess.get();if(active()&&mc.thePlayer!=null&&mc.currentScreen==null)view=nextView(view);}
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
        if(next!=null&&!next.equals(query)){query=next;requested=null;request++;model=null;permanentFailure=false;retries=0;retryAt=0;if(view==1)view=0;status="Loading map...";}
    }
    private void session(){
        String next=ServerScope.current();
        if(!host.equals(next)){host=next;clearMap();}
    }
    private void clearMap(){query=requested=null;request++;model=null;status="Waiting for map";announcement.clear();permanentFailure=false;retries=0;retryAt=0;}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)clearMap();}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;UtilitySettings.load();session();
        if(!UtilitySettings.minimap)view=0;
        if(!active()){
            if(model!=null||requested!=null){request++;model=null;requested=null;}
            renderer.release();return;
        }
        if(view==1&&(MinecraftAccess.get().currentScreen!=null||permanentFailure))view=0;
        if(retryAt!=0&&System.nanoTime()>=retryAt){requested=null;retryAt=0;}
        if(permanentFailure)return;
        if(query==null||query.equals(requested))return;
        requested=query;String target=query;int token=++request;model=null;status="Loading map...";
        worker.execute(()->{
            try{
                String dir=meshDir(target);
                if(dir==null){unavailable(token,null,false);return;}
                if(!meshPath(dir)){unavailable(token,"invalid mesh path: "+dir,false);return;}
                MinimapModel loaded=readCached(dir);
                if(loaded==null)loaded=downloadAndRead(dir,target);
                final MinimapModel finished=loaded;
                if(request!=token)return;
                if(finished==null){unavailable(token,"mesh download failed for "+target,true);return;}
                MinecraftAccess.get().addScheduledTask(()->{if(request==token){model=finished;status="";}});
            }catch(Exception error){
                unavailable(token,"could not load "+target+": "+error.getMessage(),true);
            }
        });
    }
    private void unavailable(int token,String reason,boolean retry){
        if(request!=token)return;
        model=null;
        if(retry&&retries<MAX_RETRIES){
            retries++;
            status="Retry "+retries+"/"+MAX_RETRIES+" in a few seconds...";
            retryAt=System.nanoTime()+RETRY_NANOS;
            return;
        }
        status=reason!=null&&reason.startsWith("mesh download failed")?"Map broken; try again later":"Preview unavailable";
        permanentFailure=true;
        if(reason!=null)System.err.println("OTL: minimap "+reason);
    }
    /** Belt and braces: safe GL state for the next world frame, even with Lunar or FBO leaks. */
    private static void hardGlReset(){
        GL11.glColorMask(true,true,true,true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.cullFace(GL11.GL_BACK);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();GlStateManager.disableAlpha();
        GlStateManager.color(1,1,1,1);
    }
    /** Animated "..." so a slow download doesn't look frozen; always fully opaque. */
    private String statusLine(){
        int dots=(int)((System.nanoTime()/400000000L)%4);
        String base=status;
        if(base.endsWith("..."))return base.substring(0,base.length()-3)+new String(new char[dots]).replace('\0','.');
        return base;
    }

    /** URL-encodes each path segment so map directories with spaces, % or non-ASCII work. */
    private static String encodePath(String path){
        StringBuilder out=new StringBuilder();
        for(String part:path.split("/",-1)){
            if(out.length()>0)out.append('/');
            for(byte b:part.getBytes(StandardCharsets.UTF_8)){
                char c=(char)(b&255);
                if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~')out.append(c);
                else out.append('%').append(String.format("%02X",b));
            }
        }
        return out.toString();
    }
    /** Reads a previously downloaded mesh for the map directory, or null; corrupt files are re-fetched. */
    private MinimapModel readCached(String dir){
        File file=mapsFile(dir);
        if(!file.isFile())return null;
        try(InputStream in=new java.io.FileInputStream(file)){return MinimapModel.read(in);}
        catch(Exception e){
            System.err.println("OTL: cached mesh unreadable, re-downloading: "+e.getMessage());
            try{java.nio.file.Files.deleteIfExists(file.toPath());}catch(java.io.IOException ignored){}
            return null;
        }
    }

    private MinimapModel downloadAndRead(String dir,String targetName){
        status="Downloading "+targetName+"...";
        try{
            File destination=mapsFile(dir);
            java.nio.file.Files.createDirectories(destination.getParentFile().toPath());
            MapRemote.downloadPublic("https://raw.githubusercontent.com/Gr3nDy/Over-The-Limit/beta/Maps/"+encodePath(dir)+"/minimap.mesh.gz",destination);
            return readCached(dir);
        }catch(Exception e){System.err.println("OTL: could not download minimap mesh "+dir+": "+e.getMessage());return null;}
    }
    private File mapsFile(String dir){return new File(ConfigDir.parts(dir.split("/")),"minimap.mesh.gz");}

    /** True for paths shaped like PublicMaps/main/... or CommunityMaps/master/... with no traversal. */
    private static boolean meshPath(String p){
        if(!p.startsWith("PublicMaps/main/")&&!p.startsWith("CommunityMaps/master/"))return false;
        for(String part:p.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.indexOf('\\')>=0||part.indexOf(':')>=0)return false;
        return true;
    }

    String meshDir(String name)throws IOException{
        if(index==null){
            Map<String,String> loaded=new LinkedHashMap<>();
            Map<String,String> sources=new HashMap<>();
            try(InputStream in=Minimap.class.getResourceAsStream("/assets/otl/maps/minimap-index.json")){
                if(in==null)throw new IOException("Minimap index missing");
                JsonArray entries=new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();
                int bad=0;
                for(JsonElement entry:entries){
                    try{
                        JsonObject item=entry.getAsJsonObject();String asset=item.get("asset").getAsString();
                        int slash=asset.lastIndexOf('/');
                        if(slash<=0||UtilitySettings.favoriteKey(asset.substring(0,slash))==null||!asset.endsWith("/minimap.mesh.gz"))throw new IllegalArgumentException("Invalid minimap asset path");
                        String label=MapInfo.norm(item.get("name").getAsString()),path=item.get("path").getAsString();
                        if(path.isEmpty()||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.matches(".*[\\r\\n\\t].*"))throw new IllegalArgumentException("Invalid minimap repository path");
                        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IllegalArgumentException("Invalid minimap repository path");
                        String repo=path.substring(0,path.indexOf('/')),previous=loaded.get(label);
                        if(previous==null){loaded.put(label,path);sources.put(label,repo);}
                        else if(repo.equals(sources.get(label))&&!previous.equals(path))loaded.put(label,"");
                        loaded.put(path,path);
                    }catch(Exception error){bad++;}
                }
                if(bad>0)System.err.println("OTL: skipped "+bad+" invalid minimap index entries");
                if(loaded.isEmpty())throw new IOException("Minimap index has no usable entries ("+bad+" invalid)");
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
        float visibility=preview?1:1F;
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
            int background=large?UtilitySettings.minimapLargeBackground:UtilitySettings.minimapOpacity;
            Gui.drawRect(box.x,box.y,box.x+box.w,box.y+box.h,((int)(background*255/100*visibility))<<24);
            if(pad>0)border(outer,(int)(255*visibility));
            double cx=box.x+box.w/2D,cy=box.y+box.h/2D;
            double[] player=mc.thePlayer==null?new double[3]:new double[]{mc.thePlayer.lastTickPosX+(mc.thePlayer.posX-mc.thePlayer.lastTickPosX)*partial,mc.thePlayer.lastTickPosY+(mc.thePlayer.posY-mc.thePlayer.lastTickPosY)*partial,mc.thePlayer.lastTickPosZ+(mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)*partial};
            float yaw=mc.thePlayer==null?0:mc.thePlayer.prevRotationYaw+net.minecraft.util.MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw-mc.thePlayer.prevRotationYaw)*partial;
            double angle=MinimapModel.angle(UtilitySettings.minimapRotate,yaw);
            boolean staticView=(UtilitySettings.minimapStatic||large)&&selected!=null;
            double worldHalf=selected==null?box.w/2D:staticView?selected.screenRadius+1:box.w/(2D*MinimapModel.PIXELS_PER_BLOCK*ZOOM_SCALE[effectiveZoom()-1]);
            double[] anchor=staticView?selected.center:player;
            if(selected!=null&&mc.thePlayer!=null){
                try{
                    int[] head=mc.thePlayer==null?null:KillFeed.face(mc.thePlayer.getName());
                    if(!renderer.render(selected,anchor,player,angle,box.w*scale,worldHalf,UtilitySettings.minimapCutaway,head))throw new IllegalStateException("framebuffers unavailable");
                    renderer.composite(box,alpha);renderedFrames++;
                }catch(RuntimeException error){renderer.release();unavailable(request,"rendering unavailable: "+error.getMessage(),true);selected=null;}
            }else if(renderer.loaded())renderer.release();
            GlStateManager.enableTexture2D();GlStateManager.disableDepth();
            int color=textAlpha<<24|0xFFFFFF;
            if(selected==null){
                String label=preview?"Isometric Minimap":statusLine();
                int max=Math.max(1,box.w-8);
                String trimmed=mc.fontRendererObj.trimStringToWidth(label,max);
                if(trimmed.length()<label.length()){String dots="...";trimmed=mc.fontRendererObj.trimStringToWidth(label,Math.max(1,max-mc.fontRendererObj.getStringWidth(dots)))+dots;}
                label=trimmed;
                mc.fontRendererObj.drawStringWithShadow(label,box.x+(box.w-mc.fontRendererObj.getStringWidth(label))/2,box.y+box.h/2-4,0xFFFFFFFF);
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
            hardGlReset();
        }
    }
}
