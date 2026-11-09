package otl.client;

import java.util.Locale;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemBow;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** Session-only player-hit accuracy; the network observer always forwards the original packets. */
final class BowAccuracy {
    static final BowAccuracy INSTANCE=new BowAccuracy();
    private static final String HANDLER="otl_bow_accuracy";
    final Stats stats=new Stats();
    final Feedback feedback=new Feedback();
    private Channel channel;
    private Object world;
    private boolean listening;
    private volatile boolean connectionDirty;
    static boolean observing(){UtilitySettings.load();return UtilitySettings.bowOptimizer&&(UtilitySettings.bowAccuracy||UtilitySettings.bowHitSound||UtilitySettings.bowHitMarker)&&ServerScope.active();}

    static final class Feedback {
        private long hitUntil,killUntil,soundUntil;
        void hit(long now){hitUntil=now+250000000L;soundUntil=now+500000000L;}
        void kill(long now){killUntil=now+400000000L;}
        int color(long now){return now<killUntil?0xFFFF5555:now<hitUntil?0xFFFFFFFF:0;}
        boolean sound(long now){boolean pending=now<soundUntil;soundUntil=0;return pending;}
        void clear(){hitUntil=killUntil=soundUntil=0;}
    }

    static final class Stats {
        long shots,hits;
        volatile long generation;
        boolean ended;
        String map;
        void reset(){shots=hits=0;ended=false;generation++;}
        void clear(){map=null;reset();}
        void shot(){if(!ended&&shots<Long.MAX_VALUE)shots++;}
        void hit(){if(!ended&&hits<shots)hits++;}
        String percentage(int decimals){return String.format(Locale.ROOT,"%."+Math.max(0,Math.min(2,decimals))+"f%%",shots==0?0D:100D*hits/shots);}
        void notice(String formatted){
            if(ChatRules.playerMessage(formatted))return;
            for(String line:ChatRules.plain(formatted).split("[\\r\\n]+")){
                String text=line.trim();
                if(text.matches("(?i)^(?:Game over!|Tied!|Your team won!|Your team lost!?|[^\\r\\n]{1,80} wins?!|[A-Za-z0-9_]{1,16} has ended the match\\.)$")){
                    reset();ended=true;continue;
                }
                if(text.equalsIgnoreCase("The match has started!")||text.equalsIgnoreCase("Go!")){reset();continue;}
                if(text.startsWith("Playing ")){
                    String name=MapPreview.serverQuery(text);
                    if(name!=null&&(!name.equals(map)||ended)){map=name;reset();}
                }
            }
        }
    }
    void reset(){stats.clear();feedback.clear();}
    private void notice(String formatted){
        long generation=stats.generation;stats.notice(formatted);
        if(stats.generation!=generation)feedback.clear();
    }
    private void hit(){
        if(stats.ended)return;
        stats.hit();feedback.hit(System.nanoTime());
    }
    private void sync(){
        Minecraft mc=MinecraftAccess.get();
        NetworkManager manager=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        Channel next=observing()&&manager!=null?manager.channel():null;
        if(connectionDirty||world!=mc.theWorld||channel!=next){
            reset();world=mc.theWorld;connectionDirty=false;
        }
        if(channel==next)return;
        if(channel!=null&&channel.pipeline().get(HANDLER)!=null)channel.pipeline().remove(HANDLER);
        channel=next;listening=false;
        if(next==null)return;
        try{
            ChannelHandlerContext target=next.pipeline().context(manager);
            if(target==null)throw new IllegalStateException("Client packet handler not found");
            next.pipeline().addBefore(target.name(),HANDLER,new ChannelInboundHandlerAdapter(){
                @Override public void channelRead(ChannelHandlerContext ctx,Object packet){
                    try{
                        if(packet instanceof S2BPacketChangeGameState||packet instanceof S45PacketTitle){
                            long generation=stats.generation;
                            mc.addScheduledTask(()->{
                                if(channel!=ctx.channel()||stats.generation!=generation||!observing())return;
                                if(packet instanceof S2BPacketChangeGameState){
                                    if(((S2BPacketChangeGameState)packet).getGameState()==6)hit();
                                }else{
                                    S45PacketTitle title=(S45PacketTitle)packet;
                                    if(title.getMessage()!=null)notice(title.getMessage().getFormattedText());
                                }
                            });
                        }
                    }finally{ctx.fireChannelRead(packet);}
                }
            });
            listening=true;
        }catch(RuntimeException ex){System.err.println("OTL: Bow Accuracy unavailable: "+ex.getMessage());}
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)sync();}
    @SubscribeEvent public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event){connectionDirty=true;}
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){connectionDirty=true;}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote){reset();world=null;}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void shot(PlaySoundAtEntityEvent event){
        Minecraft mc=MinecraftAccess.get();
        if(!observing()||event.entity!=mc.thePlayer||mc.thePlayer==null||mc.thePlayer.isDead||!event.entity.worldObj.isRemote
            ||!"random.bow".equals(event.name)||mc.thePlayer.getHeldItem()==null||!(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow))return;
        sync();if(listening)stats.shot();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void chat(ClientChatReceivedEvent event){
        if(event.type==2||!observing())return;
        sync();String text=event.message.getFormattedText();notice(text);
        if(UtilitySettings.bowHitMarker&&!stats.ended&&MinecraftAccess.get().thePlayer!=null){
            KillFeedRules.Death death=KillFeedRules.parse(text);
            if(death!=null&&death.ownKill(MinecraftAccess.get().thePlayer.getName())&&!death.suicide()&&KillConfirmRules.isBow(death))feedback.kill(System.nanoTime());
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hitSound(PlaySoundEvent event){
        if(!observing()||!UtilitySettings.bowHitSound||event.result==null||!"random.successful_hit".equals(event.name))return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null||mc.thePlayer.getDistanceSq(event.sound.getXPosF(),event.sound.getYPosF()-mc.thePlayer.getEyeHeight(),event.sound.getZPosF())>.25)return;
        if(!feedback.sound(System.nanoTime()))return;
        // Original COD-inspired tick using vanilla sounds, not copied game audio.
        event.result=PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("random.click"),1.8F);
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new net.minecraft.util.ResourceLocation("note.hat"),.3F,1.6F,
            (float)mc.thePlayer.posX,(float)(mc.thePlayer.posY+mc.thePlayer.getEyeHeight()),(float)mc.thePlayer.posZ));
    }
    static String hudText(String percentage){return (UtilitySettings.bowAccuracyPrefix?"Bow Accuracy: ":"")+percentage;}
    static HudLayout.Rect bounds(){
        float scale=UtilitySettings.bowAccuracySize/100F;
        String widest=hudText(String.format(Locale.ROOT,"%."+UtilitySettings.bowAccuracyDecimals+"f%%",100D));
        return HudLayout.bounds(HudLayout.BOW,(int)Math.ceil((MinecraftAccess.get().fontRendererObj.getStringWidth(widest)+8)*scale),(int)Math.ceil(17*scale));
    }
    static void drawPreview(){INSTANCE.draw(true);}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        Minecraft mc=MinecraftAccess.get();
        if(event.type!=RenderGameOverlayEvent.ElementType.CHAT||!observing()||mc.thePlayer==null||mc.currentScreen instanceof HudEditor||mc.gameSettings.hideGUI)return;
        if(UtilitySettings.bowAccuracy)draw(false);
        if(UtilitySettings.bowHitMarker&&mc.gameSettings.thirdPersonView==0)drawMarker(event.resolution.getScaledWidth()/2F,event.resolution.getScaledHeight()/2F);
    }
    private void drawMarker(float x,float y){
        int color=feedback.color(System.nanoTime());if(color==0)return;
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x,y,0);float scale=UtilitySettings.bowHitMarkerSize/100F;GlStateManager.scale(scale,scale,1);
            for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1})for(int step=3;step<8;step++){
                int px=sx>0?step:-step-1,py=sy>0?step:-step-1;
                Gui.drawRect(px,py,px+1,py+1,color);
            }
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}
    }
    private void draw(boolean preview){
        Minecraft mc=MinecraftAccess.get();HudLayout.Rect box=bounds();float scale=UtilitySettings.bowAccuracySize/100F;
        String percent=preview?String.format(Locale.ROOT,"%."+UtilitySettings.bowAccuracyDecimals+"f%%",66.6666667):listening?stats.percentage(UtilitySettings.bowAccuracyDecimals):"--%";
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(box.x,box.y,0);GlStateManager.scale(scale,scale,1);
            int alpha=UtilitySettings.bowAccuracyOpacity*255/100;
            if(alpha>0)Gui.drawRect(0,0,(int)Math.ceil(box.w/scale),17,alpha<<24);
            mc.fontRendererObj.drawStringWithShadow(hudText(percent),4,4,preview&&!(UtilitySettings.bowOptimizer&&UtilitySettings.bowAccuracy)?0x73888888:0xFFFFFFFF);
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}
    }
    // shortcut: counts vanilla server-confirmed player hits, not mobs or servers that omit the arrow-hit signal.
}
