package otl.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.EnumChatFormatting;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * The Touchdown replay. The client sees no replay data, so the mod records its own: every tick,
 * the position of nearby players and Footballs, ten seconds deep. When PGM announces a touchdown,
 * the last recorded play - throw, catch and the run into the end zone - is replayed over the map
 * with ghost copies, half speed, from a follow camera. The round keeps running underneath; the
 * replay ends the instant the next round starts ("Go!" arrives as a title packet, so a netty tap,
 * the same pattern Bow Accuracy proved, watches for it), on any key, or at the end of the play.
 */
final class Replay {
    static final Replay INSTANCE=new Replay();
    private static final int BUFFER=200; // ten seconds of recording
    private static final double SPEED=0.5; // slow motion: two real ticks per replay tick
    private static final String HANDLER="otl_replay_title";

    private static final List<ReplayPlay.Frame> frames=new ArrayList<>();
    private static final List<ReplayPlay.Event> events=new ArrayList<>();
    private static final Map<Integer,String> names=new HashMap<>();
    private static final Set<Integer> balls=new HashSet<>();
    private static final Set<Integer> roster=new HashSet<>(); // players shown in this replay
    private static final Map<Integer,EntityOtherPlayerMP> ghosts=new HashMap<>();
    private static int tick;

    private static ReplayPlay.Play play;
    private static double cursor;
    private static boolean placed;
    private static EntityOtherPlayerMP cameraGhost;
    private static GuiScreen overlay;
    private static String scoredBy;

    private static Object world;
    private static Channel channel;
    private static boolean connectionDirty;
    private Replay(){}

    private static boolean feature(){return UtilitySettings.touchdown&&UtilitySettings.touchdownReplay&&Touchdown.active();}
    static boolean playing(){return play!=null;}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void chat(ClientChatReceivedEvent event){
        if(event.type==2)return;
        String text=ChatRules.plain(event.message.getFormattedText());
        for(String raw:text.split("[\\r\\n]+")){
            String line=raw.trim();
            if(line.contains("TOUCHDOWN")){trigger();continue;}
            if(playing()&&(MatchEnd.is(line)||line.equals("Go!"))){stop();continue;}
            String name;
            if((name=name(line," threw the Football"))!=null)events.add(new ReplayPlay.Event(tick,true,name));
            else if((name=name(line," picked up"))!=null||(name=name(line," intercepted"))!=null)
                events.add(new ReplayPlay.Event(tick,false,name));
            else if((name=name(line," takes it to the end zone"))!=null)scoredBy=name;
        }
    }
    /** The player name in a PGM line: the last word before the fixed marker, color codes stripped. */
    static String name(String line,String marker){
        line=ChatRules.plain(line);
        int at=line.indexOf(marker);
        if(at<=0)return null;
        String head=line.substring(0,at).trim();
        if(head.isEmpty())return null;
        int space=head.lastIndexOf(' ');
        return space<0?head:head.substring(space+1);
    }

    private static void trigger(){
        if(playing()||!feature())return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld==null||mc.thePlayer==null)return;
        Map<String,Integer> ids=new HashMap<String,Integer>();
        for(Map.Entry<Integer,String> e:names.entrySet())ids.put(e.getValue(),e.getKey());
        ReplayPlay.Play p=ReplayPlay.build(new ArrayList<ReplayPlay.Frame>(frames),new ArrayList<ReplayPlay.Event>(events),tick,
            scoredBy,ids,new HashSet<Integer>(balls));
        scoredBy=null;
        if(p==null)return; // nothing of this play was in render distance: skip silently
        begin(mc,p);
    }

    private static void begin(Minecraft mc,ReplayPlay.Play p){
        play=p;cursor=p.fromTick;placed=false;
        ghosts.clear();roster.clear();
        for(ReplayPlay.Frame f:frames)
            if(f.tick>=p.fromTick&&f.tick<=p.toTick)
                for(int id:f.entities.keySet())if(!balls.contains(id))roster.add(id);
        cameraGhost=new EntityOtherPlayerMP(mc.theWorld,new GameProfile(
            UUID.nameUUIDFromBytes("otl-replay-camera".getBytes(StandardCharsets.UTF_8)),"camera"));
        cameraGhost.ticksExisted=1; // keeps the render path interpolating, never resets the camera
        cameraGhost.setAlwaysRenderNameTag(false);
        mc.setRenderViewEntity(cameraGhost);
        overlay=new GuiScreen(){
            public boolean doesGuiPauseGame(){return false;}
            public void drawScreen(int mx,int my,float partial){
                String s=EnumChatFormatting.RED.toString()+EnumChatFormatting.BOLD+"REPLAY";
                int w=fontRendererObj.getStringWidth(s)*2;
                GlStateManager.pushMatrix();
                GlStateManager.translate((width-w)/2F,8,0);
                GlStateManager.scale(2,2,1);
                fontRendererObj.drawStringWithShadow(s,0,0,0xFFFF5555);
                GlStateManager.popMatrix();
            }
            protected void keyTyped(char c,int code){stop();}
            protected void mouseClicked(int mx,int my,int button){stop();}
        };
        mc.displayGuiScreen(overlay);
    }

    /** Any key ends the replay. */
    static void skip(){stop();}

    private static void stop(){
        boolean wasPlaying=playing();
        play=null;cursor=0;roster.clear();ghosts.clear();
        Minecraft mc=MinecraftAccess.get();
        if(cameraGhost!=null&&mc.getRenderViewEntity()==cameraGhost)mc.setRenderViewEntity(mc.thePlayer);
        cameraGhost=null;
        if(wasPlaying&&mc.currentScreen!=null)mc.displayGuiScreen(null);
        overlay=null;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        sync(mc);
        if(playing()){
            if(mc.theWorld==null||mc.thePlayer==null||mc.getRenderViewEntity()!=cameraGhost){stop();return;}
            cursor+=SPEED;
            if(cursor>=play.toTick){stop();return;}
            follow(mc,cursor);
            for(EntityOtherPlayerMP g:ghosts.values())g.ticksExisted++;
            return;
        }
        record(mc);
    }

    private static void record(Minecraft mc){
        if(!feature()||mc.theWorld==null||mc.thePlayer==null)return;
        if(world!=mc.theWorld){world=mc.theWorld;frames.clear();events.clear();balls.clear();tick=0;}
        ReplayPlay.Frame f=new ReplayPlay.Frame(tick);
        for(EntityPlayer p:(List<EntityPlayer>)mc.theWorld.playerEntities){
            f.entities.put(p.getEntityId(),new double[]{p.posX,p.posY,p.posZ,p.rotationYaw,p.rotationPitch});
            names.put(p.getEntityId(),p.getName());
        }
        for(Object o:mc.theWorld.loadedEntityList)if(o instanceof EntitySnowball){
            EntitySnowball b=(EntitySnowball)o;
            f.entities.put(b.getEntityId(),new double[]{b.posX,b.posY,b.posZ,0,0});
            balls.add(b.getEntityId());
        }
        frames.add(f);tick++;
        while(frames.size()>BUFFER){
            frames.remove(0);
            int oldest=frames.get(0).tick;
            while(!events.isEmpty()&&events.get(0).tick<oldest)events.remove(0);
        }
    }

    /** The camera follows the ball in flight, then the carrier: over the shoulder, slightly aside. */
    private static void follow(Minecraft mc,double at){
        double[] target=play.solo?null:ReplayPlay.lerp(frames,play.ballId,at);
        if(target==null&&play.scorerId>=0)target=ReplayPlay.lerp(frames,play.scorerId,at);
        if(target==null)return; // hold the camera when the recording loses the play
        int id=play.solo||ReplayPlay.lerp(frames,play.ballId,at)==null?play.scorerId:play.ballId;
        double[] prev=ReplayPlay.lerp(frames,id,at-SPEED*2);
        double dx=prev==null?0:target[0]-prev[0],dy=prev==null?0:target[1]-prev[1],dz=prev==null?0:target[2]-prev[2];
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double cx,cy,cz;
        if(length>0.05){ // behind the movement, a touch to the side: a broadcast angle
            cx=target[0]-dx/length*6-dz/length*2.5;
            cz=target[2]-dz/length*6+dx/length*2.5;
            cy=target[1]+3;
        }else if(placed){ // the play stood still: hold the angle we had
            cx=cameraGhost.posX;cy=cameraGhost.posY;cz=cameraGhost.posZ;
        }else{ // no movement to read yet: start behind and above
            cx=target[0]+4;cy=target[1]+3;cz=target[2]+4;
        }
        placed=true;
        cameraGhost.setPosition(cx,cy,cz);
        cameraGhost.prevPosX=cx;cameraGhost.prevPosY=cy;cameraGhost.prevPosZ=cz;
        double lookX=target[0]-cx,lookY=target[1]+1-cy,lookZ=target[2]-cz;
        float yaw=(float)(Math.toDegrees(Math.atan2(lookZ,lookX))-90);
        float pitch=(float)-Math.toDegrees(Math.atan2(lookY,Math.sqrt(lookX*lookX+lookZ*lookZ)));
        cameraGhost.rotationYaw=yaw;cameraGhost.rotationPitch=Math.max(-90,Math.min(90,pitch));
        cameraGhost.prevRotationYaw=yaw;cameraGhost.prevRotationPitch=cameraGhost.rotationPitch;
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event){
        if(!playing())return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld==null)return;
        double at=Math.max(play.fromTick,cursor-SPEED+event.partialTicks*SPEED);
        RenderManager rm=mc.getRenderManager();
        if(!play.solo){
            double[] b=ReplayPlay.lerp(frames,play.ballId,at);
            if(b!=null){
                GlStateManager.pushMatrix();
                GlStateManager.translate(b[0]-rm.viewerPosX,b[1]-rm.viewerPosY,b[2]-rm.viewerPosZ);
                GlStateManager.scale(0.62F,0.62F,0.62F);
                FootballMesh.draw();
                GlStateManager.popMatrix();
            }
        }
        for(int id:roster){
            double[] p=ReplayPlay.lerp(frames,id,at);
            if(p==null)continue;
            EntityOtherPlayerMP g=ghost(mc,id);
            g.setPositionAndRotation(p[0],p[1],p[2],(float)p[3],(float)p[4]);
            g.prevPosX=p[0];g.prevPosY=p[1];g.prevPosZ=p[2];
            g.prevRotationYaw=(float)p[3];g.prevRotationPitch=(float)p[4];
            rm.renderEntityStatic(g,event.partialTicks,false);
        }
    }

    /** Ghost copies are rendered by hand, so they never enter the world's own lists. */
    private static EntityOtherPlayerMP ghost(Minecraft mc,int id){
        EntityOtherPlayerMP g=ghosts.get(id);
        if(g!=null&&g.worldObj==mc.theWorld)return g;
        GameProfile profile=null;
        Entity real=mc.theWorld.getEntityByID(id);
        if(real instanceof EntityPlayer)profile=((EntityPlayer)real).getGameProfile();
        if(profile==null){
            String name=names.get(id);
            profile=new GameProfile(UUID.nameUUIDFromBytes(("otl-ghost-"+id).getBytes(StandardCharsets.UTF_8)),
                name==null?"Player":name);
        }
        g=new EntityOtherPlayerMP(mc.theWorld,profile);
        g.ticksExisted=1; // keeps the render path interpolating between our recorded positions
        g.setAlwaysRenderNameTag(true);
        ghosts.put(id,g);
        return g;
    }

    /** Real players keep living in the round underneath, so hide them for the few replay seconds. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void hide(RenderPlayerEvent.Pre event){
        if(!playing())return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=null&&mc.theWorld.playerEntities.contains(event.entity))event.setCanceled(true);
    }

    @SubscribeEvent public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event){connectionDirty=true;}
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){connectionDirty=true;stop();}
    @SubscribeEvent public void unload(WorldEvent.Unload event){
        if(!event.world.isRemote)return;
        stop();world=null;frames.clear();events.clear();tick=0;
    }

    /** The next round announces itself with a title packet, not chat, so tap the netty pipeline. */
    private static void sync(Minecraft mc){
        NetworkManager manager=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        Channel next=feature()&&manager!=null?manager.channel():null;
        if(connectionDirty||world!=mc.theWorld||channel!=next){
            if(channel!=null&&channel.pipeline().get(HANDLER)!=null)channel.pipeline().remove(HANDLER);
            channel=next;connectionDirty=false;
            if(next==null)return;
            try{
                ChannelHandlerContext target=next.pipeline().context(manager);
                if(target==null)throw new IllegalStateException("Client packet handler not found");
                next.pipeline().addBefore(target.name(),HANDLER,new ChannelInboundHandlerAdapter(){
                    @Override public void channelRead(ChannelHandlerContext ctx,Object packet){
                        try{
                            if(packet instanceof S45PacketTitle){
                                Minecraft mc=MinecraftAccess.get();
                                mc.addScheduledTask(()->title(ctx,(S45PacketTitle)packet));
                            }
                        }finally{ctx.fireChannelRead(packet);}
                    }
                });
            }catch(RuntimeException ex){System.err.println("OTL: Touchdown replay title tap unavailable: "+ex.getMessage());}
        }
    }

    private static void title(ChannelHandlerContext ctx,S45PacketTitle packet){
        if(channel!=ctx.channel()||packet.getMessage()==null)return;
        String text=ChatRules.plain(packet.getMessage().getFormattedText()).trim();
        if(text.contains("TOUCHDOWN"))trigger();
        else if(text.equals("Go!")||MatchEnd.is(text))stop(); // the next round started: return now
    }
}
