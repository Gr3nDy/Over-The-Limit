package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

@Mod(modid="otl_smoke",version="1",clientSideOnly=true)
public final class OtlSmoke {
    boolean started;int ticks,state;FaceScreen face;GuiScreenBook bookScreen;io.netty.channel.Channel channel;
    volatile MapInfo remote;volatile boolean remoteDone;long remoteStarted;
    final boolean remoteEnabled=Boolean.getBoolean("otl.smoke.remote");
    final boolean serverEnabled=Boolean.getBoolean("otl.smoke.server");
    final boolean serverOnly=Boolean.getBoolean("otl.smoke.serverOnly");
    final String scope=System.getProperty("otl.smoke.scope","all");
    int scopeEnd=63;
    long serverStarted;
    ResourceLocation allureTexture;
    boolean allureWasFavorite;
    final Map<String,Object> accuracyPreferences=new HashMap<>();
    ItemStack accuracyHeld;int accuracySlot,accuracyPerspective;HudLayout.Position accuracyPosition;
    final AccuracySounds accuracySounds=new AccuracySounds();
    volatile Throwable accuracyPacketFailure;
    final Map<String,Object> minimapPreferences=new HashMap<>();
    HudLayout.Position minimapPosition;double minimapX,minimapY,minimapZ;boolean minimapFlying;
    long minimapStarted,minimapFrames;
    final List<String> packets=Collections.synchronizedList(new ArrayList<String>());
    @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void line(String text){MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,new ChatComponentText(text)));}
    List<GuiButton> buttons(GuiScreen screen){return ReflectionHelper.getPrivateValue(GuiScreen.class,screen,"buttonList","field_146292_n");}
    void click(int id){for(GuiButton b:buttons(face))if(b.id==id){face.actionPerformed(b);return;}throw new AssertionError("Missing button "+id);}
    GuiButton find(GuiScreen s,String label){for(GuiButton b:buttons(s))if(b.displayString.equals(label))return b;throw new AssertionError("Missing button "+label);}
    List<ChatLine> chatHistory(String key){return ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.session().views.get(key).hud,"chatLines","field_146252_h");}
    final java.util.concurrent.CountDownLatch metadataBlocked=new java.util.concurrent.CountDownLatch(1),releaseMetadata=new java.util.concurrent.CountDownLatch(1);
    String hoverQuery(){return remoteEnabled?"Hallows":"Allure";}
    void openGoldChat(){
        java.util.concurrent.ExecutorService lookup=ReflectionHelper.getPrivateValue(MapRemote.class,null,"worker");
        lookup.execute(()->{metadataBlocked.countDown();try{releaseMetadata.await(30,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
        Minecraft mc=Minecraft.getMinecraft();UtilitySettings.chat=true;ReliableChat.session().select("all");
        ChatComponentText gold=new ChatComponentText(hoverQuery());
        gold.getChatStyle().setColor(EnumChatFormatting.GOLD).setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND,"/map "+hoverQuery()));
        gold.getChatStyle().setChatHoverEvent(new net.minecraft.event.HoverEvent(net.minecraft.event.HoverEvent.Action.SHOW_TEXT,new ChatComponentText("Click to view "+hoverQuery())));
        mc.ingameGUI.getChatGUI().printChatMessage(gold);mc.displayGuiScreen(new GuiChat());remoteStarted=System.currentTimeMillis();
    }
    void pointAtChatMap(){
        Minecraft mc=Minecraft.getMinecraft();int scale=new ScaledResolution(mc).getScaleFactor();
        for(int y=mc.currentScreen.height-36;y>=Math.max(0,mc.currentScreen.height-240);y--){
            if(hoverQuery().equals(MapPreview.chatQueryAt(mc.currentScreen,8,y))){
                org.lwjgl.input.Mouse.setCursorPosition(8*scale,mc.displayHeight-y*scale-1);return;
            }
        }
        throw new AssertionError("Gold map link not hit-testable in "+mc.currentScreen.getClass().getSimpleName());
    }
    int count(String command){int count=0;synchronized(packets){for(String s:packets)if(s.equals(command))count++;}return count;}
    void checkBubbles(){
        Minecraft mc=Minecraft.getMinecraft();boolean enabled=UtilitySettings.bubbles,mode=UtilitySettings.bubbleTeamAndChat;int perspective=mc.gameSettings.thirdPersonView;
        net.minecraft.client.entity.EntityOtherPlayerMP mate=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BubbleMate"));
        net.minecraft.client.entity.EntityOtherPlayerMP other=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BubbleOther"));
        mc.theWorld.addEntityToWorld(-9101,mate);mc.theWorld.addEntityToWorld(-9102,other);
        try{
            UtilitySettings.bubbles=true;TeamBubbles.clearBubbles();
            line("<BubbleMate>: global hello");line("From BubbleOther: private hello");line("\u00a7e(Party) BubbleOther: party hello");
            require(TeamBubbles.bubbles.isEmpty(),"Global/party/private chat created bubbles");
            line("(Any Team) \u00a7cBubbleMate\u00a7r: team hello");
            TeamBubbleRules.Stack first=TeamBubbles.bubbles.get(mate.getUniqueID());
            require(first!=null&&first.items.get(0).text.equals("team hello"),"Team prefix did not create bubble without join/color information");
            line("(Any Team) BubbleMate: second");line("(Any Team) BubbleMate: third");line("(Any Team) BubbleMate: fourth");
            require(first.items.size()==3&&first.items.get(0).text.equals("fourth")&&first.items.get(2).text.equals("second"),"Three-bubble stack/order failed");
            line("You joined Blue");require(TeamBubbles.bubbles.isEmpty(),"Team change kept old bubbles");
            line("(Observers) BubbleOther: observer team hello");
            require(TeamBubbles.bubbles.containsKey(other.getUniqueID()),"Explicit Observers team chat was ignored");
            UtilitySettings.bubbleTeamAndChat=true;
            line("\u00a7eYou joined \u00a79Blue\u00a7r team!");
            line("\u00a79<BubbleMate>\u00a7r: blue global hello");
            require(TeamBubbles.bubbles.get(mate.getUniqueID()).items.get(0).text.equals("blue global hello"),"Own team global chat did not bubble");
            line("\u00a7c<BubbleOther>: red global hello");require(!TeamBubbles.bubbles.containsKey(other.getUniqueID()),"Other team's global chat bubbled");
            line("\u00a7eYou joined \u00a77Observers");line("\u00a79<BubbleMate>: spectator global hello");
            require(TeamBubbles.bubbles.isEmpty(),"Observer retained global team-color matching");
            line("(Any Team) "+mc.thePlayer.getName()+": own team hello");
            require(TeamBubbles.bubbles.containsKey(mc.thePlayer.getUniqueID()),"Own server-echoed team chat did not create bubble");
            mc.gameSettings.thirdPersonView=1;require(TeamBubbles.visible(mc.thePlayer),"Own bubble is not visible in third person");
            mc.gameSettings.thirdPersonView=0;require(!TeamBubbles.visible(mc.thePlayer),"Own bubble obscures first person");
            line("Playing Jurassic Attack FFA by Tundren");
            require(TeamBubbles.bubbles.isEmpty(),"FFA map change kept old team bubbles");
            line("\u00a7eYou joined \u00a79Players\u00a7r!");
            line("\u00a79<BubbleMate>: same-color FFA chat");line("(Players) BubbleMate: FFA team chat");
            require(TeamBubbles.bubbles.isEmpty(),"FFA allowed same-color or explicit team bubbles");
            line("<BubbleMate>: Playing Allure by MapMaker");
            line("XML: OvercastCommunity/CommunityMaps ctw/allure");
            line("(Players) BubbleMate: still FFA");
            require(TeamBubbles.bubbles.isEmpty(),"Player quote or map lookup changed active bubble mode");
            line("Playing Allure by MapMaker");line("(Blue) BubbleMate: team mode restored");
            require(TeamBubbles.bubbles.containsKey(mate.getUniqueID()),"Team map did not re-enable bubbles after FFA");
            line("Overcast Community\nview our /servers or click the beacon");
            TeamBubbles.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            line("(Any Team) BubbleMate: hub hello");require(TeamBubbles.bubbles.isEmpty(),"Hub displayed speech bubbles");
            line("Created by MapMaker");
            System.out.println("OTL_SMOKE: team-chat bubbles, FFA exclusion, team-map recovery, own messages, hub exclusion and animated three-message stacks PASS");
        }finally{
            mc.theWorld.removeEntityFromWorld(-9101);mc.theWorld.removeEntityFromWorld(-9102);
            UtilitySettings.bubbles=enabled;UtilitySettings.bubbleTeamAndChat=mode;mc.gameSettings.thirdPersonView=perspective;TeamBubbles.clearBubbles();
        }
    }
    void sniffPackets(Minecraft mc){
        channel=mc.getNetHandler().getNetworkManager().channel();
        channel.pipeline().addLast("otl_packets",new io.netty.channel.ChannelOutboundHandlerAdapter(){public void write(io.netty.channel.ChannelHandlerContext ctx,Object p,io.netty.channel.ChannelPromise promise)throws Exception{if(p instanceof net.minecraft.network.play.client.C01PacketChatMessage)packets.add(((net.minecraft.network.play.client.C01PacketChatMessage)p).getMessage());super.write(ctx,p,promise);}});
    }
    static final class BowSounds {
        final List<net.minecraft.client.audio.ISound> sounds=new ArrayList<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
            if(event.result!=null&&event.name.equals("random.bow"))sounds.add(event.result);
        }
    }
    static final class AccuracySounds {
        boolean replaced;int markerColor,killSounds;
        @SubscribeEvent(priority=EventPriority.LOWEST) public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event){
            if(event.name.equals("note.pling")&&event.result!=null)killSounds++;
            if(event.name.equals("random.successful_hit")){
                replaced=event.result!=null&&event.result.getSoundLocation().equals(new ResourceLocation("random.click"));
                markerColor=BowAccuracy.INSTANCE.feedback.color(System.nanoTime());
            }
        }
    }
    void accuracyPacket(Minecraft mc,Object packet){
        io.netty.channel.ChannelHandlerContext context=mc.getNetHandler().getNetworkManager().channel().pipeline().context("otl_bow_accuracy");
        require(context!=null,"Read-only accuracy observer was not installed");
        context.executor().execute(()->{
            try{((io.netty.channel.ChannelInboundHandlerAdapter)context.handler()).channelRead(context,packet);}
            catch(Throwable failure){accuracyPacketFailure=failure;}
        });
    }
    void prepareAccuracy(Minecraft mc)throws Exception{
        for(String name:new String[]{"bowOptimizer","bowOptimization","bowAccuracy","bowAccuracyPrefix","bowAccuracyMode","bowAccuracyAnimate","bowAccuracyFadeIn","bowAccuracyFadeOut","bowHitSound","bowKillSound","bowHitMarker","bowAccuracyOpacity","bowAccuracyDecimals","bowAccuracySize","bowHitMarkerSize","killFeed","killFeedSlide"})
            accuracyPreferences.put(name,UtilitySettings.class.getDeclaredField(name).get(null));
        accuracySlot=mc.thePlayer.inventory.currentItem;accuracyHeld=mc.thePlayer.getHeldItem();accuracyPerspective=mc.gameSettings.thirdPersonView;
        accuracyPosition=UtilitySettings.hudPositions.get(HudLayout.BOW);
        UtilitySettings.bowOptimizer=true;UtilitySettings.bowOptimization=true;UtilitySettings.bowAccuracy=true;
        UtilitySettings.bowHitSound=false;UtilitySettings.bowHitMarker=false;UtilitySettings.bowAccuracyDecimals=2;
        UtilitySettings.bowKillSound=false;UtilitySettings.bowAccuracyPrefix=true;
        UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.ALWAYS;UtilitySettings.bowAccuracyAnimate=true;
        UtilitySettings.bowAccuracyFadeIn=0;UtilitySettings.bowAccuracyFadeOut=50;
        UtilitySettings.killFeed=true;UtilitySettings.killFeedSlide=true;
        BowOptimizerSettings options=new BowOptimizerSettings(null);mc.displayGuiScreen(options);
        require(find(options,"Bow Hit Sound: OFF")!=null&&find(options,"Hit Marker: OFF")!=null,"Optional bow feedback did not default OFF");
        options.actionPerformed(find(options,"Bow-Kill Sound: OFF"));require(UtilitySettings.bowKillSound,"Bow kill sound toggle did not enable");
        options.actionPerformed(find(options,"Optimization: ON"));require(!BowOptimizer.active(),"Inner optimizer toggle did not disable optimization");
        options.actionPerformed(find(options,"Bow Hit Sound: OFF"));options.actionPerformed(find(options,"Hit Marker: OFF"));
        net.minecraftforge.fml.client.config.GuiSlider marker=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==10).findFirst().get();
        marker.setValue(150);marker.updateSlider();
        options.actionPerformed(find(options,"Bow Accuracy"));
        require(find(options,"Mode: Always")!=null&&find(options,"Animate Percentage: ON")!=null,"Accuracy presentation defaults missing");
        options.actionPerformed(find(options,"Mode: Always"));options.actionPerformed(find(options,"Animate Percentage: ON"));
        require(find(options,"Mode: On Hit")!=null&&find(options,"Animate Percentage: OFF")!=null,"Accuracy presentation controls did not toggle");
        options.actionPerformed(find(options,"Mode: On Hit"));require(find(options,"Mode: Holding Bow")!=null,"Holding Bow mode missing");
        options.actionPerformed(find(options,"Mode: Holding Bow"));require(find(options,"Mode: On Shoot")!=null,"On Shoot mode missing");
        options.actionPerformed(find(options,"Mode: On Shoot"));require(find(options,"Mode: Always")!=null,"Mode cycle did not return to Always");
        options.actionPerformed(find(options,"Mode: Always"));options.actionPerformed(find(options,"Mode: On Hit"));
        options.drawScreen(0,0,0);
        ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-bow-accuracy-settings.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        int prefixedWidth=BowAccuracy.bounds().w;
        options.actionPerformed(find(options,"Prefix: ON"));
        require(!UtilitySettings.bowAccuracyPrefix&&BowAccuracy.bounds().w<prefixedWidth,"Prefix toggle did not hide the label and shrink HUD bounds");
        require(find(options,"Decimal places: 2")!=null,"Accuracy did not default to two decimals");
        options.actionPerformed(find(options,"Decimal places: 2"));require(UtilitySettings.bowAccuracyDecimals==0,"Whole-percent option missing");
        options.actionPerformed(find(options,"Decimal places: 0"));options.actionPerformed(find(options,"Decimal places: 1"));
        net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==3).findFirst().get();
        opacity.setValue(65);opacity.updateSlider();
        net.minecraftforge.fml.client.config.GuiSlider size=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==5).findFirst().get();
        size.setValue(125);size.updateSlider();
        net.minecraftforge.fml.client.config.GuiSlider fadeIn=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==15).findFirst().get();
        net.minecraftforge.fml.client.config.GuiSlider fadeOut=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==16).findFirst().get();
        require(fadeIn.getValueInt()==0&&fadeOut.getValueInt()==50,"Bow fade slider defaults changed");
        fadeIn.setValue(75);fadeIn.updateSlider();fadeOut.setValue(25);fadeOut.updateSlider();
        UtilitySettings.hudPositions.put(HudLayout.BOW,new HudLayout.Position(.75,.25));UtilitySettings.save();
        UtilitySettings.bowAccuracyDecimals=0;UtilitySettings.bowAccuracyOpacity=0;UtilitySettings.bowHitMarkerSize=50;UtilitySettings.bowAccuracyPrefix=true;
        UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.ALWAYS;UtilitySettings.bowAccuracyAnimate=true;UtilitySettings.bowKillSound=false;
        UtilitySettings.bowAccuracyFadeIn=0;UtilitySettings.bowAccuracyFadeOut=0;
        ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
        require(!UtilitySettings.bowOptimization&&UtilitySettings.bowAccuracy&&UtilitySettings.bowHitSound&&UtilitySettings.bowHitMarker
            &&!UtilitySettings.bowAccuracyPrefix&&UtilitySettings.bowAccuracyDecimals==2&&UtilitySettings.bowAccuracyOpacity==65&&UtilitySettings.bowAccuracySize==125&&UtilitySettings.bowHitMarkerSize==150,"Bow feature settings did not persist independently");
        require(UtilitySettings.bowAccuracyMode==BowAccuracy.Mode.HOLDING_BOW&&!UtilitySettings.bowAccuracyAnimate&&UtilitySettings.bowKillSound,"Accuracy mode/animation/kill sound did not persist");
        require(UtilitySettings.bowAccuracyFadeIn==75&&UtilitySettings.bowAccuracyFadeOut==25,"Bow fade sliders did not persist");
        UtilitySettings.bowAccuracyFadeIn=0;UtilitySettings.bowAccuracyFadeOut=50;
        UtilitySettings.bowAccuracyAnimate=true;UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.ON_HIT;
        BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));BowAccuracy.INSTANCE.reset();
        require(BowAccuracy.INSTANCE.display.alpha(System.nanoTime(),BowAccuracy.Mode.ON_HIT)==0,"On Hit HUD visible before a hit");
        mc.thePlayer.inventory.mainInventory[accuracySlot]=new ItemStack(net.minecraft.init.Items.bow);
        ItemStack bow=mc.thePlayer.getHeldItem();
        for(int i=0;i<3;i++)bow.getItem().onPlayerStoppedUsing(bow,mc.theWorld,mc.thePlayer,bow.getMaxItemUseDuration()-20);
        require(BowAccuracy.INSTANCE.stats.shots==3,"Accuracy did not count valid shots while optimization was OFF");
        bow.getItem().onPlayerStoppedUsing(bow,mc.theWorld,mc.thePlayer,bow.getMaxItemUseDuration()-1);
        require(BowAccuracy.INSTANCE.stats.shots==3,"Undercharged shot changed accuracy denominator");
        MinecraftForge.EVENT_BUS.register(accuracySounds);mc.displayGuiScreen(null);mc.gameSettings.thirdPersonView=0;
        accuracyPacket(mc,new net.minecraft.network.play.server.S2BPacketChangeGameState(6,0));
    }
    void checkAccuracy(Minecraft mc){
        require(accuracyPacketFailure==null,"Accuracy observer threw: "+accuracyPacketFailure);
        require(BowAccuracy.INSTANCE.stats.hits==1&&BowAccuracy.INSTANCE.stats.percentage(2).equals("33.33%"),"Server arrow-hit confirmation did not update live accuracy");
        require(accuracySounds.replaced&&accuracySounds.markerColor==0xFFFFFFFF,"Native hit packet did not forward to sound replacement/white marker");
        require(BowAccuracy.INSTANCE.display.alpha(System.nanoTime(),BowAccuracy.Mode.ON_HIT)>0,"Confirmed hit did not reveal On Hit HUD");
        HudEditor editor=new HudEditor();mc.displayGuiScreen(editor);
        HudLayout.Rect box=BowAccuracy.bounds();require(HudEditor.bounds(HudLayout.BOW).x==box.x,"Accuracy HUD does not share movable bounds");
        editor.drawScreen(0,0,0);ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-bow-accuracy-hud.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        mc.displayGuiScreen(null);
        line("BowTarget was shot by "+mc.thePlayer.getName()+" from 20 blocks");
        require(BowAccuracy.INSTANCE.feedback.color(System.nanoTime())==0xFFFF5555,"Confirmed bow kill did not turn hit marker red");
        require(BowAccuracy.INSTANCE.stats.hits==1,"Kill confirmation counted a second accuracy hit");
        require(accuracySounds.killSounds==1,"Confirmed bow kill did not play its separate sound");
        UtilitySettings.bowKillSound=false;line("QuietTarget was shot by "+mc.thePlayer.getName()+" from 20 blocks");
        require(accuracySounds.killSounds==1,"Disabled bow kill sound still played");
        UtilitySettings.bowKillSound=true;UtilitySettings.bowAccuracy=false;UtilitySettings.bowHitMarker=false;UtilitySettings.bowHitSound=false;
        require(BowAccuracy.observing(),"Kill sound alone did not keep bow feedback active");
        line("SoundTarget was shot by "+mc.thePlayer.getName()+" from 20 blocks");
        line("SwordTarget was slain by "+mc.thePlayer.getName()+"'s Iron Sword");
        line("OtherTarget was shot by AnotherArcher from 20 blocks");
        line("<AnotherArcher>: QuotedTarget was shot by "+mc.thePlayer.getName()+" from 20 blocks");
        require(accuracySounds.killSounds==2,"Bow kill sound failed independence or played for non-bow/non-local kills");
        UtilitySettings.bowAccuracy=true;UtilitySettings.bowHitMarker=true;UtilitySettings.bowHitSound=true;
        UtilitySettings.bowAccuracyMode=BowAccuracy.Mode.HOLDING_BOW;
        BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        require(BowAccuracy.INSTANCE.display.alpha(System.nanoTime(),BowAccuracy.Mode.HOLDING_BOW)==1,"Held bow did not reveal accuracy");
        ItemStack held=mc.thePlayer.getHeldItem();mc.thePlayer.inventory.mainInventory[accuracySlot]=null;
        BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        require(BowAccuracy.INSTANCE.display.alpha(System.nanoTime(),BowAccuracy.Mode.HOLDING_BOW)>0,"Switching items skipped the bow HUD grace period");
        mc.thePlayer.inventory.mainInventory[accuracySlot]=held;
        BowAccuracy.INSTANCE.overlay(new net.minecraftforge.client.event.RenderGameOverlayEvent.Post(new net.minecraftforge.client.event.RenderGameOverlayEvent(0,new ScaledResolution(mc)),net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CHAT));
        BowAccuracy.INSTANCE.display.hit(System.nanoTime()-2250000000L);
        BowAccuracy.INSTANCE.overlay(new net.minecraftforge.client.event.RenderGameOverlayEvent.Post(new net.minecraftforge.client.event.RenderGameOverlayEvent(0,new ScaledResolution(mc)),net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CHAT));
        require(org.lwjgl.opengl.GL11.glGetError()==0,"Bow accuracy/hit marker rendering left a GL error");
        accuracyPacket(mc,new net.minecraft.network.play.server.S45PacketTitle(net.minecraft.network.play.server.S45PacketTitle.Type.TITLE,new ChatComponentText("Game over!")));
    }
    void finishAccuracy(Minecraft mc)throws Exception{
        try{
            require(accuracyPacketFailure==null,"Accuracy observer threw: "+accuracyPacketFailure);
            require(BowAccuracy.INSTANCE.stats.shots==0&&BowAccuracy.INSTANCE.stats.hits==0&&BowAccuracy.INSTANCE.stats.ended,"Game-over title did not reset accuracy");
            require(BowAccuracy.INSTANCE.display.alpha(System.nanoTime(),BowAccuracy.Mode.ON_HIT)==0,"Match end left On Hit HUD visible");
            BowAccuracy.INSTANCE.connect(new net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent(mc.getNetHandler().getNetworkManager(),"FML"));
            BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(!BowAccuracy.INSTANCE.stats.ended&&BowAccuracy.INSTANCE.stats.shots==0,"Rejoin did not start a fresh accuracy session");
            BowAccuracy.INSTANCE.stats.shot();BowAccuracy.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(mc.theWorld));
            require(BowAccuracy.INSTANCE.stats.shots==0,"World/map unload did not clear accuracy");
            UtilitySettings.bowOptimizer=false;BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(mc.getNetHandler().getNetworkManager().channel().pipeline().get("otl_bow_accuracy")==null,"OFF left accuracy packet observer installed");
            System.out.println("OTL_SMOKE: bow sub-toggles, live 33.33% accuracy, hit packet forwarding, independent bow-kill sound, Holding Bow grace, On Hit fade, animated percentage controls/persistence, white/red marker and match/rejoin resets PASS");
        }finally{
            MinecraftForge.EVENT_BUS.unregister(accuracySounds);
            for(Map.Entry<String,Object> preference:accuracyPreferences.entrySet())UtilitySettings.class.getDeclaredField(preference.getKey()).set(null,preference.getValue());
            if(accuracyPosition==null)UtilitySettings.hudPositions.remove(HudLayout.BOW);else UtilitySettings.hudPositions.put(HudLayout.BOW,accuracyPosition);
            mc.thePlayer.inventory.mainInventory[accuracySlot]=accuracyHeld;mc.gameSettings.thirdPersonView=accuracyPerspective;
            BowAccuracy.INSTANCE.reset();BowAccuracy.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));UtilitySettings.save();
        }
    }
    void prepareMinimap(Minecraft mc)throws Exception{
        for(String name:new String[]{"minimap","minimapSize","minimapOpacity","minimapLargeOpacity","minimapBorder"})minimapPreferences.put(name,UtilitySettings.class.getDeclaredField(name).get(null));
        minimapPosition=UtilitySettings.hudPositions.get(HudLayout.MINIMAP);
        minimapX=mc.thePlayer.posX;minimapY=mc.thePlayer.posY;minimapZ=mc.thePlayer.posZ;minimapFlying=mc.thePlayer.capabilities.isFlying;
        UtilitySettings.minimap=false;line("            Allure            ");line(" Capture the enemy's wool!");line(" Created by jcane");
        MinimapSettings options=new MinimapSettings(null);mc.displayGuiScreen(options);
        options.actionPerformed(find(options,"Minimap: OFF"));
        net.minecraftforge.fml.client.config.GuiSlider size=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==1).findFirst().get();
        net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==2).findFirst().get();
        size.setValue(120);size.updateSlider();opacity.setValue(65);opacity.updateSlider();
        options.actionPerformed(find(options,"Display"));
        int plain=Minimap.bounds().w;
        require(!UtilitySettings.minimapBorder,"Minimap border is enabled by default");
        options.actionPerformed(find(options,"Border: OFF"));
        require(UtilitySettings.minimapBorder&&Minimap.bounds().w==plain+4,"Minimap border did not add its 2px bevel");
        options.actionPerformed(find(options,"Border: ON"));
        require(!UtilitySettings.minimapBorder&&Minimap.bounds().w==plain,"Minimap border did not drop its padding");
        require(Minimap.nextView(0)==1&&Minimap.nextView(1)==2&&Minimap.nextView(2)==0,"M key does not cycle small/large/hidden");
        UtilitySettings.hudPositions.put(HudLayout.MINIMAP,new HudLayout.Position(0,0));UtilitySettings.save();
        UtilitySettings.minimap=false;UtilitySettings.minimapSize=50;UtilitySettings.minimapOpacity=0;
        ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
        require(UtilitySettings.minimap&&UtilitySettings.minimapSize==120&&UtilitySettings.minimapOpacity==65,"Minimap preferences did not persist");
        mc.displayGuiScreen(null);mc.thePlayer.capabilities.isFlying=true;
        mc.thePlayer.setPosition(-52.5,16,41.5);mc.thePlayer.lastTickPosX=-52.5;mc.thePlayer.lastTickPosY=16;mc.thePlayer.lastTickPosZ=41.5;
        Minimap.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
        minimapStarted=System.nanoTime();
    }
    boolean checkMinimap(Minecraft mc){
        Object model=ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"model");
        long frames=ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"renderedFrames");
        if(model==null||frames==0){require(System.nanoTime()-minimapStarted<15000000000L,"Minimap did not load/render Allure: "+ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"status"));return false;}
        require(Minimap.bounds().w==Minimap.bounds().h,"Minimap background is not square");
        require(HudEditor.bounds(HudLayout.MINIMAP).w==Minimap.bounds().w,"Editor minimap bounds differ from gameplay");
        require(org.lwjgl.opengl.GL11.glGetError()==0,"Minimap left an OpenGL error");
        line("<Alex>: Playing Another Map by Somebody");line("XML: OvercastCommunity/CommunityMaps ctw/another_map [View]");
        require("Allure".equals(ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"query")),"Player quote or map lookup replaced the current minimap");
        ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-isometric-minimap.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        minimapFrames=frames;mc.thePlayer.setPosition(mc.thePlayer.posX+8,mc.thePlayer.posY,mc.thePlayer.posZ);
        UtilitySettings.minimapSize=150;
        return true;
    }
    void finishMinimap(Minecraft mc)throws Exception{
        try{
            long frames=ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"renderedFrames");
            require(frames>minimapFrames&&Minimap.bounds().w==Minimap.bounds().h,"Moving/resizing stopped the square minimap renderer");
            require(MinimapModel.PIXELS_PER_BLOCK==2,"Viewport resizing changed map scale");
            ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-isometric-minimap-moved.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            boolean scissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            Minimap.drawPreview();require(org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)==scissor,"Minimap changed scissor enable state");
            require(org.lwjgl.opengl.GL11.glGetError()==0,"Minimap preview left an OpenGL error");
            UtilitySettings.minimap=false;Minimap.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(!Minimap.INSTANCE.renderer.loaded(),"Disabling minimap did not release its GPU model");
            System.out.println("OTL_SMOKE: real Allure mesh, isometric player-centered pan, square viewport, fixed map scale, HUD bounds, persistence and GPU cleanup PASS");
        }finally{
            for(Map.Entry<String,Object> preference:minimapPreferences.entrySet())UtilitySettings.class.getDeclaredField(preference.getKey()).set(null,preference.getValue());
            if(minimapPosition==null)UtilitySettings.hudPositions.remove(HudLayout.MINIMAP);else UtilitySettings.hudPositions.put(HudLayout.MINIMAP,minimapPosition);
            mc.thePlayer.setPosition(minimapX,minimapY,minimapZ);mc.thePlayer.capabilities.isFlying=minimapFlying;UtilitySettings.save();
        }
    }
    void checkBow(Minecraft mc){
        OtlModule module=OtlModule.ALL.stream().filter(m->m.id.equals("bow_optimizer")).findFirst().get();
        boolean enabled=UtilitySettings.bowOptimizer,left=UtilitySettings.leftHand,optimization=UtilitySettings.bowOptimization;int perspective=mc.gameSettings.thirdPersonView;
        int slot=mc.thePlayer.inventory.currentItem;ItemStack oldHeld=mc.thePlayer.getHeldItem();BowSounds sounds=new BowSounds();
        MinecraftForge.EVENT_BUS.register(sounds);
        try{
            UtilitySettings.bowOptimization=true;
            module.setEnabled(true);UtilitySettings.save();
            UtilitySettings.bowOptimizer=false;ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
            require(module.enabled(),"Bow Optimizer preference did not persist");
            UtilitySettings.leftHand=false;mc.gameSettings.thirdPersonView=0;mc.displayGuiScreen(null);
            LeftHand.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(mc.entityRenderer.itemRenderer instanceof LeftHand.Items,"Bow Optimizer did not attach independently of Preferred Hand");
            ItemStack before=new ItemStack(net.minecraft.init.Items.bow),held=before.copy();held.setItemDamage(1);
            mc.thePlayer.inventory.mainInventory[slot]=held;
            net.minecraft.client.renderer.ItemRenderer renderer=new net.minecraft.client.renderer.ItemRenderer(mc);
            ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,before,"itemToRender","field_78453_b");
            ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,slot,"equippedItemSlot","field_78450_g");
            ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,1F,"equippedProgress","field_78454_c");
            LeftHand.Items wrapped=new LeftHand.Items(mc,renderer);wrapped.updateEquippedItem();
            require((float)ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,"equippedProgress","field_78454_c")==1F,"Bow durability update lowered the equipped bow");
            require(ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,"itemToRender","field_78453_b")==held,"Rendered bow did not use corrected inventory stack");
            module.setEnabled(false);
            ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,before,"itemToRender","field_78453_b");
            wrapped.updateEquippedItem();
            require((float)ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.ItemRenderer.class,renderer,"equippedProgress","field_78454_c")<1F,"OFF did not restore vanilla equip animation");
            module.setEnabled(true);
            held.getItem().onPlayerStoppedUsing(held,mc.theWorld,mc.thePlayer,held.getMaxItemUseDuration()-20);
            require(sounds.sounds.size()==1,"Valid bow release did not produce exactly one immediate local sound");
            net.minecraft.client.audio.ISound local=sounds.sounds.get(0);
            require(local.getPitch()>=1.125F&&local.getPitch()<=1.334F,"Full-draw sound did not keep vanilla pitch");
            net.minecraftforge.client.event.sound.PlaySoundEvent echo=new net.minecraftforge.client.event.sound.PlaySoundEvent(null,
                new net.minecraft.client.audio.PositionedSoundRecord(new ResourceLocation("random.bow"),1,1,(float)mc.thePlayer.posX,(float)mc.thePlayer.posY,(float)mc.thePlayer.posZ),net.minecraft.client.audio.SoundCategory.PLAYERS);
            BowOptimizer.INSTANCE.sound(echo);require(echo.result==null,"Matching delayed sound was not suppressed");
            held.getItem().onPlayerStoppedUsing(held,mc.theWorld,mc.thePlayer,held.getMaxItemUseDuration()-1);
            require(sounds.sounds.size()==1,"Undercharged release produced a false shot sound");
            module.setEnabled(false);
            held.getItem().onPlayerStoppedUsing(held,mc.theWorld,mc.thePlayer,held.getMaxItemUseDuration()-20);
            require(sounds.sounds.size()==1,"OFF still produced predicted bow sound");
            module.setEnabled(true);
            mc.thePlayer.inventory.mainInventory[slot]=new ItemStack(net.minecraft.init.Items.fishing_rod);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.PlaySoundAtEntityEvent(mc.thePlayer,"random.bow",1,1));
            require(sounds.sounds.size()==1,"Fishing rod sound was mistaken for a bow release");
            require(org.lwjgl.opengl.GL11.glGetError()==0,"Bow Optimizer left a GL error");
            System.out.println("OTL_SMOKE: bow toggle/persistence, same-slot durability animation, immediate release sound, echo suppression, short draws and OFF behavior PASS");
        }finally{
            MinecraftForge.EVENT_BUS.unregister(sounds);mc.thePlayer.inventory.mainInventory[slot]=oldHeld;
            module.setEnabled(enabled);UtilitySettings.leftHand=left;UtilitySettings.bowOptimization=optimization;mc.gameSettings.thirdPersonView=perspective;UtilitySettings.save();
        }
    }
    void prepareScope(Minecraft mc){
        if(serverOnly||scope.equals("all"))return;
        mc.setServerData(new net.minecraft.client.multiplayer.ServerData("OTL fixture","play.oc.tc",false));
        switch(scope){
            case "chat":state=0;scopeEnd=3;return;
            case "face":state=4;scopeEnd=5;FaceConfig.reset();UtilitySettings.faceRig=true;UtilitySettings.otherFaces=true;UtilitySettings.fullscreen=false;mc.displayGuiScreen(face=new FaceScreen());return;
            case "menu":state=6;scopeEnd=6;mc.displayGuiScreen(new UtilityScreen());return;
            case "maps":state=7;scopeEnd=23;sniffPackets(mc);return;
            case "hand":state=24;scopeEnd=30;return;
            case "bubbles":state=31;scopeEnd=32;UtilitySettings.nonPlayerChat=false;UtilitySettings.mainHandLeft=false;UtilitySettings.handType=LeftHand.Type.ALL;mc.displayGuiScreen(new PreferedHandSettings(new UtilityScreen()));return;
            case "killfeed":state=33;scopeEnd=42;ReliableChat.session();return;
            case "notices":state=43;scopeEnd=44;mc.displayGuiScreen(new HudEditor());ReliableChat.session();return;
            case "killconfirm":state=45;scopeEnd=51;UtilitySettings.killConfirm=false;mc.displayGuiScreen(new HudEditor());ReliableChat.session();return;
            case "tabs":state=52;scopeEnd=54;prepareTabs(mc);return;
            case "bow":state=55;scopeEnd=58;return;
            case "accuracy":state=56;scopeEnd=58;return;
            case "minimap":state=59;scopeEnd=63;return;
            default:throw new IllegalArgumentException("Unknown smoke scope: "+scope);
        }
    }
    void prepareTabs(Minecraft mc){
        UtilitySettings.tabBackgroundOpacity=0;UtilitySettings.typingTabBackgroundOpacity=0;
        mc.displayGuiScreen(new GuiChat());((ReliableChatScreen)mc.currentScreen).select("team");
        require(ReliableChat.session().selected.equals("team"),"Selected channel did not change with transparent tabs");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!started&&mc.currentScreen instanceof GuiMainMenu){
            started=true;
            if(serverOnly){
                UtilitySettings.load();UtilitySettings.chat=true;UtilitySettings.mapPreview=true;UtilitySettings.previewOnCommand=true;UtilitySettings.devHosts.add("localhost");
                MapRemote.requestCatalog();state=15;serverStarted=System.currentTimeMillis();
                System.out.println("OTL_SMOKE: targeted localhost checks; integrated fixtures SKIPPED");
                mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(),mc,new net.minecraft.client.multiplayer.ServerData("Local PGM","localhost:25565",false)));
            }else mc.launchIntegratedServer("FacePortSmoke","Face Port Smoke",new WorldSettings(12345,WorldSettings.GameType.CREATIVE,true,false,WorldType.FLAT));
            return;
        }
        if(state==15&&serverEnabled&&System.currentTimeMillis()-serverStarted>90000){System.err.println("OTL_SMOKE: FAIL - localhost connection timed out");FMLCommonHandler.instance().exitJava(1,false);return;}
        if(mc.thePlayer==null||++ticks<120||ticks%25!=0)return;
        try{
            mc.gameSettings.pauseOnLostFocus=false;
            if(state==0&&ticks<145)prepareScope(mc);
            if(state==0){
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("OTL fixture","play.oc.tc",false));
                net.minecraft.client.settings.KeyBinding teamBind=Arrays.stream(mc.gameSettings.keyBindings).filter(k->k.getKeyDescription().equals("key.otl.team_chat")).findFirst().get();
                require(teamBind.getKeyCodeDefault()==org.lwjgl.input.Keyboard.KEY_Y,"Open Team Chat is not bound to Y by default");
                int bound=teamBind.getKeyCode();teamBind.setKeyCode(org.lwjgl.input.Keyboard.KEY_U);
                require(teamBind.getKeyCode()==org.lwjgl.input.Keyboard.KEY_U,"Open Team Chat cannot be rebound");teamBind.setKeyCode(bound);
                net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
                sniffPackets(mc);
                line("Overcast Community");line("view our /servers or click the beacon");require(!ReliableChat.fullChat(),"Lobby not detected");
                mc.displayGuiScreen(new GuiChat());ReliableChatScreen ui=(ReliableChatScreen)mc.currentScreen;ui.drawScreen(0,0,0);ui.mouseClicked(10,ui.height-27,0);ui.cycleNext();ui.sendChatMessage("lobby text",true);
            }else if(state==1){
                require(packets.equals(Arrays.asList("lobby text")),"Lobby commands: "+packets);
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.WorldEvent.Load(mc.theWorld));line("Created by MapMaker");require(ReliableChat.fullChat()&&ReliableChat.session().selected.equals("all"),"Created by detection");
            }else if(state==2){
                require(count("/t")==0,"Unexpected automatic /t count");ReliableChatScreen ui=(ReliableChatScreen)mc.currentScreen;ReliableChat.session().select("team");ui.updateScreen();ui.sendChatMessage("team message",true);
                ReliableChat.session().select("all");ui.updateScreen();ui.sendChatMessage("global message",true);
                ReliableChat.session().select("party");ui.updateScreen();ui.sendChatMessage("party message",true);
                ui.cycleNext();ui.drawScreen(0,0,0);ui.mouseClicked(10,ui.height-27,0);
                ui.sendChatMessage("/msg Alice hello",true);line("To Alice: hello");ui.sendChatMessage("/r reply",true);line("To Bob: reply");line("From Bob: incoming");
                line("\u00a79\u00a7oto your monument!");require(!ReliableChat.session().views.containsKey("pm:your"),"False PM tab");require(ReliableChat.session().views.containsKey("pm:bob"),"Missing /r PM");
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.WorldEvent.Load(mc.theWorld));require(ReliableChat.fullChat(),"World change hid tabs");line("Created by OtherMaker");
            }else if(state==3){
                require(count("/t")==0&&count("/g")==0&&count("/party chat")==0,"Tab/map sent bare command: "+packets);
                require(packets.containsAll(Arrays.asList("/t team message","/g global message","/party chat party message","/msg Alice hello","/r reply")),"Missing routed sends: "+packets);
                ReliableChat.session().clear("party");ReliableChat.session().clear("all");ReliableChat.session().select("team");ReliableChat.noteSend(null);
                line("\u00a7cYou are not in a party.");
                List<ChatLine> partyHistory=ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.session().views.get("party").hud,"chatLines","field_146252_h");
                List<ChatLine> allHistory=ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.session().views.get("all").hud,"chatLines","field_146252_h");
                require(partyHistory.size()==1&&allHistory.size()==1&&ReliableChat.session().selected.equals("team"),"Party error missing, duplicated, or stole focus without recent send");
                line("Alex: You are not in a party.");require(partyHistory.size()==1,"Player quote copied as party warning");
                line("Overcast Community\nview our /servers or click the beacon");require(!ReliableChat.fullChat()&&ReliableChat.tabKeys().size()==1,"Return to lobby");line("Welcome to Overcast Community!");
                checkBubbles();
                FaceConfig.reset();UtilitySettings.faceRig=true;UtilitySettings.otherFaces=true;UtilitySettings.fullscreen=false;mc.displayGuiScreen(face=new FaceScreen());
                require(buttons(face).stream().noneMatch(b->b.displayString.contains("Advanced")||b.displayString.contains("Expression")),"Obsolete controls");
            }else if(state==4){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-face-rig.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                click(11);require(buttons(face).stream().anyMatch(b->b.displayString.equals("Save")),"Editor save missing");
            }else if(state==5){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-editor.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int[] original=FaceMarks.marks(mc.thePlayer.getLocationSkin());original=original==null?null:original.clone();
                int x=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gx"),y=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gy");face.panelMouseClicked(x+2,y+2,0);click(28);
                require(Arrays.equals(original,FaceMarks.marks(mc.thePlayer.getLocationSkin())),"Cancel changed marks");
                mc.displayGuiScreen(new UtilityScreen());
            }else if(state==6){
                UtilityScreen menu=(UtilityScreen)mc.currentScreen;
                menu.actionPerformed(find(menu,"Face Rig: ON"));require(!UtilitySettings.faceRig,"Module toggle did not apply");
                require(find(menu,"Face Rig: OFF")!=null,"Module list did not refresh");
                menu.actionPerformed(find(menu,"Face Rig: OFF"));require(UtilitySettings.faceRig,"Module toggle did not revert");
                require(find(menu,"Reliable Chat: ON")!=null,"Second module missing from list");
                require(find(menu,"Map Preview: ON")!=null,"Map Preview module missing from list");
                UtilitySettings.leftHand=false;menu.initGui();
                for(int p=0;p<OtlModule.ALL.size()&&!buttons(menu).stream().anyMatch(b->b.displayString.equals("Prefered Hand: OFF"));p++)menu.actionPerformed(find(menu,">"));
                menu.actionPerformed(find(menu,"Prefered Hand: OFF"));
                require(UtilitySettings.leftHand,"Left Hand toggle did not apply");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.leftHand=false;UtilitySettings.load();
                require(UtilitySettings.leftHand,"Left Hand preference did not persist");
                menu.actionPerformed(find(menu,"Prefered Hand: ON"));require(!UtilitySettings.leftHand,"Prefered Hand toggle did not restore");
                UtilitySettings.mainHandLeft=false;UtilitySettings.handType=LeftHand.Type.ALL;
                PreferedHandSettings hands=new PreferedHandSettings(menu);mc.displayGuiScreen(hands);
                require(find(hands,"Main Hand: Right")!=null&&find(hands,"Type: ALL")!=null,"Preferred hand defaults missing");
                hands.actionPerformed(find(hands,"Main Hand: Right"));hands.actionPerformed(find(hands,"Type: ALL"));
                require(UtilitySettings.mainHandLeft&&UtilitySettings.handType==LeftHand.Type.ITEMS,"Preferred hand settings did not apply");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.mainHandLeft=false;UtilitySettings.handType=LeftHand.Type.ALL;UtilitySettings.load();
                require(UtilitySettings.mainHandLeft&&UtilitySettings.handType==LeftHand.Type.ITEMS&&!UtilitySettings.leftHand,"Preferred hand settings did not persist independently of module toggle");
                for(int i=0;i<5;i++)hands.actionPerformed(find(hands,"Type: "+UtilitySettings.handType.label));
                require(UtilitySettings.handType==LeftHand.Type.ALL,"Preferred hand type cycle omitted an option");
                hands.actionPerformed(find(hands,"Main Hand: Left"));require(!UtilitySettings.mainHandLeft,"Right hand did not restore");
                mc.displayGuiScreen(menu);
                for(net.minecraft.item.Item tool:new net.minecraft.item.Item[]{net.minecraft.init.Items.iron_pickaxe,net.minecraft.init.Items.iron_axe,net.minecraft.init.Items.iron_shovel,net.minecraft.init.Items.iron_hoe,net.minecraft.init.Items.shears,net.minecraft.init.Items.fishing_rod,net.minecraft.init.Items.flint_and_steel})
                    require(LeftHand.Type.TOOLS.matches(new ItemStack(tool)),"Real Minecraft tool was excluded from Tools Only");
                UtilitySettings.devHosts.add("localhost");
                require("localhost".equals(ServerScope.host("localhost:25565")),"dev host allowlist");
                UtilitySettings.devHosts.remove("localhost");
                require("".equals(ServerScope.host("localhost:25565")),"dev host not removed");
                require("".equals(ServerScope.host("play.oc.tc.example.com")),"Scope suffix bypass");
                require("".equals(ServerScope.host("localhost:abc")),"Scope bad port");
                for(int p=0;p<OtlModule.ALL.size()&&!buttons(menu).stream().anyMatch(b->b.displayString.equals("Height Limit Overlay: DISABLED"));p++)menu.actionPerformed(find(menu,">"));
                GuiButton heightToggle=find(menu,"Height Limit Overlay: DISABLED");
                OtlModule height=OtlModule.ALL.stream().filter(m->m.id.equals("height_limit_overlay")).findFirst().get();
                require(!heightToggle.enabled&&!height.available()&&!height.enabled(),"Height module was not visibly locked DISABLED");
                GuiButton heightOptions=buttons(menu).stream().filter(b->b.id==40+OtlModule.ALL.indexOf(height)).findFirst().get();
                require(!heightOptions.enabled,"Disabled height module still offered settings");
                menu.actionPerformed(heightToggle);height.setEnabled(true);
                require(!UtilitySettings.heightLimitOverlay&&mc.currentScreen==menu,"Disabled height module could be re-enabled");
                menu.drawScreen(0,0,0);
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-menu.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            }else if(state==7){
                String key=UtilitySettings.favoriteKey("ctw/allure");allureWasFavorite=UtilitySettings.mapFavorites.contains(key);
                UtilitySettings.mapFavorites.add(key);UtilitySettings.save();
                ItemStack book=new ItemStack(net.minecraft.init.Items.written_book);
                NBTTagCompound nbt=new NBTTagCompound();
                nbt.setString("title","Vote");nbt.setString("author","Server");
                NBTTagList pages=new NBTTagList();pages.appendTag(new NBTTagString("{\"text\":\"Allure\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Mode: CTW\\nTags: Classic, Casual\"}}"));nbt.setTag("pages",pages);
                book.setTagCompound(nbt);
                bookScreen=new GuiScreenBook(mc.thePlayer,book,false);
                mc.displayGuiScreen(bookScreen);
                int scale=new ScaledResolution(mc).getScaleFactor(),left=(bookScreen.width-192)/2+36;
                org.lwjgl.input.Mouse.setCursorPosition((left+2)*scale,mc.displayHeight-36*scale-1);
            }else if(state==8){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-vote-book-favorite.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int left=(bookScreen.width-192)/2+36;
                bookScreen.drawScreen(left+2,36,0);
                MapPreview.Hover hit=MapPreview.INSTANCE.hover(bookScreen,left+2,36);
                require(hit!=null&&"Allure".equalsIgnoreCase(hit.label),"Map hover did not resolve the map name: "+hit);
                require("Classic, Casual".equals(MapPreview.voteTags(bookScreen.func_175385_b(left+2,36))),"Vote-book hover tags were not read");
                require(MapPreview.INSTANCE.hover(bookScreen,left+300,36)==null,"Map hover matched outside the map name");
                MapInfo cached=MapInfo.findLocal("Allure");
                require(cached!=null&&"Allure".equals(cached.name)&&cached.objective.contains("wool")&&cached.players==24,"Local map info: "+cached);
                require(!UtilitySettings.heightLimitOverlay&&cached.maxBuildHeight==35,"Height overlay should start disabled; Allure XML limit must remain available");
                OtlModule heightModule=OtlModule.ALL.stream().filter(m->m.name.equals("Height Limit Overlay")).findFirst().get();
                if(heightModule.available()){
                heightModule.setEnabled(false);UtilitySettings.save();
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.heightLimitOverlay=true;UtilitySettings.load();
                require(!heightModule.enabled(),"Height overlay toggle did not persist");heightModule.setEnabled(true);UtilitySettings.save();
                HeightLimitSettings heightSettings=new HeightLimitSettings(bookScreen);mc.displayGuiScreen(heightSettings);
                net.minecraftforge.fml.client.config.GuiSlider darkness=(net.minecraftforge.fml.client.config.GuiSlider)buttons(heightSettings).stream().filter(b->b.displayString.startsWith("Darkness opacity:")).findFirst().get();
                darkness.setValue(65);darkness.updateSlider();heightSettings.onGuiClosed();
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.heightLimitOpacity=0;UtilitySettings.load();
                require(UtilitySettings.heightLimitOpacity==65,"Height overlay opacity slider did not persist");
                darkness.setValue(37);darkness.updateSlider();UtilitySettings.save();mc.displayGuiScreen(bookScreen);
                mc.displayGuiScreen(heightSettings);
                net.minecraftforge.fml.client.config.GuiSlider fadeSlider=(net.minecraftforge.fml.client.config.GuiSlider)buttons(heightSettings).stream().filter(b->b.displayString.startsWith("Indicator fade:")).findFirst().get();
                fadeSlider.setValue(7);fadeSlider.updateSlider();heightSettings.onGuiClosed();
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.heightLimitIndicatorSeconds=1;UtilitySettings.load();
                require(UtilitySettings.heightLimitIndicatorSeconds==7,"Height indicator fade slider did not persist");
                fadeSlider.setValue(4);fadeSlider.updateSlider();UtilitySettings.save();mc.displayGuiScreen(bookScreen);
                HeightLimitOverlay.INSTANCE.receive(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("Playing Allure by Author")));
                MapInfo activeMap=ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"map");
                require(activeMap!=null&&activeMap.maxBuildHeight==35,"Height overlay did not identify the active PGM map");
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.WorldEvent.Unload(mc.theWorld));
                require(ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"map")==activeMap,"Leaving the hub cleared the announced map");
                int ground=mc.thePlayer.getPosition().down().getY();
                MapInfo overlayFixture=MapInfo.parse("<map><maxbuildheight>"+(ground+2)+"</maxbuildheight></map>","ctw/fixture",true,null,id->null);
                ReflectionHelper.setPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,overlayFixture,"map");
                net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent overlayTick=new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END);
                BlockPos placement=mc.thePlayer.getPosition().add(3,0,0);
                placement=new BlockPos(placement.getX(),ground+1,placement.getZ());
                net.minecraft.block.state.IBlockState original=mc.theWorld.getBlockState(placement);
                net.minecraft.item.ItemStack oldHeld=mc.thePlayer.inventory.getCurrentItem();
                try{
                    require(original.getBlock()==net.minecraft.init.Blocks.air,"Overlay test placement must start as air");
                    HeightLimitOverlay.INSTANCE.tick(overlayTick);
                    @SuppressWarnings("unchecked") java.util.Map<net.minecraft.world.chunk.Chunk,java.util.BitSet[]> tracked=ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"chunks");
                    net.minecraft.world.chunk.Chunk chunk=mc.theWorld.getChunkFromBlockCoords(placement);
                    int bit=(placement.getX()&15)*16+(placement.getZ()&15);
                    require(tracked.containsKey(chunk)&&!tracked.get(chunk)[1].get(bit)&&!tracked.get(chunk)[0].get(bit),"Initial snapshot treated air as terrain");
                    mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem]=new ItemStack(net.minecraft.init.Blocks.stone);
                    net.minecraft.client.entity.EntityOtherPlayerMP other=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Other"));
                    HeightLimitOverlay.INSTANCE.interact(new net.minecraftforge.event.entity.player.PlayerInteractEvent(other,net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,placement.down(),EnumFacing.UP,mc.theWorld,null));
                    require(ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"pending")==null,"Another player's click armed our height indicator");
                    HeightLimitOverlay.INSTANCE.interact(new net.minecraftforge.event.entity.player.PlayerInteractEvent(mc.thePlayer,net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,placement.down(),EnumFacing.UP,mc.theWorld,null));
                    require(placement.equals(ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"pending")),"Own placement was not armed");
                    mc.theWorld.setBlockState(placement,net.minecraft.init.Blocks.stone.getDefaultState());HeightLimitOverlay.INSTANCE.tick(overlayTick);
                    require(mc.theWorld.getBlockState(placement).getBlock()==net.minecraft.init.Blocks.stone,"Fixture world did not accept block placement");
                    require(tracked.get(chunk)[1].get(bit),"New block at limit was not tracked");
                    require((long)ReflectionHelper.getPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,"shownAt")>0,"Own placement did not show height indicator");
                    mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();
                    net.minecraft.client.gui.Gui.drawRect(0,0,bookScreen.width,bookScreen.height,0xFF202020);
                    HeightLimitOverlay.INSTANCE.indicator(new net.minecraftforge.client.event.RenderGameOverlayEvent.Post(new net.minecraftforge.client.event.RenderGameOverlayEvent(0,new ScaledResolution(mc)),net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.CHAT));
                    ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-height-indicator.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    float yaw=mc.thePlayer.rotationYaw,pitch=mc.thePlayer.rotationPitch;
                    try{
                        mc.thePlayer.rotationYaw=-90;mc.thePlayer.prevRotationYaw=-90;
                        mc.thePlayer.rotationPitch=23;mc.thePlayer.prevRotationPitch=23;
                        mc.getFramebuffer().bindFramebuffer(true);
                        mc.entityRenderer.renderWorld(0,System.nanoTime());
                        require(org.lwjgl.opengl.GL11.glGetError()==0,"Height limit overlay left a GL error");
                        ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-height-limit-overlay.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                    }finally{mc.thePlayer.rotationYaw=yaw;mc.thePlayer.prevRotationYaw=yaw;mc.thePlayer.rotationPitch=pitch;mc.thePlayer.prevRotationPitch=pitch;}
                    mc.theWorld.setBlockState(placement,original);HeightLimitOverlay.INSTANCE.tick(overlayTick);
                    require(!tracked.get(chunk)[1].get(bit),"Removed block stayed in overlay");
                }finally{mc.theWorld.setBlockState(placement,original);mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem]=oldHeld;ReflectionHelper.setPrivateValue(HeightLimitOverlay.class,HeightLimitOverlay.INSTANCE,activeMap,"map");}
                }else System.out.println("OTL_SMOKE: height-overlay runtime fixtures SKIPPED (module locked DISABLED)");
                require(!"".equals(cached.authors)&&!"".equals(cached.contributors),"Missing author credits");
                require("100rooms".equals(MapInfo.norm("100 Rooms")),"Map name normalization");
                MapInfoScreen card=new MapInfoScreen("Allure");
                mc.displayGuiScreen(card);card.drawScreen(0,0,0);
            }else if(state==9){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-map-card.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                MapPreviewSettings options=new MapPreviewSettings(null);mc.displayGuiScreen(options);
                options.actionPerformed(find(options,"Preview on command: ON"));
                require(UtilitySettings.mapPreview&&!UtilitySettings.previewOnCommand&&MapPreview.bookPreview()&&!MapPreview.commandPreview(),"Command toggle changed book previews");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.previewOnCommand=true;UtilitySettings.load();
                require(!UtilitySettings.previewOnCommand,"Command toggle did not persist");
                options.actionPerformed(find(options,"Preview on command: OFF"));
                require(MapPreview.commandPreview(),"Command preview did not re-enable");
                UtilitySettings.largeMapPreview=false;options.initGui();
                options.actionPerformed(find(options,"Preview size: Small"));require(UtilitySettings.largeMapPreview,"Large preview size did not enable");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.largeMapPreview=false;UtilitySettings.load();
                require(UtilitySettings.largeMapPreview,"Large preview size did not persist");
                options.actionPerformed(find(options,"Preview size: Large"));require(!UtilitySettings.largeMapPreview,"Small preview size did not restore");
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("Other","example.com",false));
                require(!MapPreview.bookPreview()&&!MapPreview.commandPreview(),"Preview escaped server scope");
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("Fixture","play.oc.tc",false));
                UtilitySettings.mapPreview=false;require(!MapPreview.bookPreview()&&!MapPreview.commandPreview(),"Master toggle did not disable previews");UtilitySettings.mapPreview=true;
                MapGalleryScreen gallery=new MapGalleryScreen(null);mc.displayGuiScreen(gallery);gallery.drawScreen(0,0,0);
                MapGalleryScreen.Grid grid=ReflectionHelper.getPrivateValue(MapGalleryScreen.class,gallery,"grid");
                gallery.actionPerformed(find(gallery,"Next"));gallery.drawScreen(0,0,0);
                require((int)ReflectionHelper.getPrivateValue(MapGalleryScreen.class,gallery,"page")==1,"Gallery Next failed");
                List<String> paths=gallery.paths();
                String selected=paths.get(grid.perPage());
                gallery.mouseClicked(grid.x(0)+5,grid.y(0)+5,0);
                require(mc.currentScreen instanceof MapInfoScreen,"Gallery cell did not open card");
                MapInfo selectedInfo=ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"info");
                require(selectedInfo.dir.equals(selected.substring(0,selected.length()-8).replace("CommunityMaps/master/","")),"Gallery opened a different map");
                ((MapInfoScreen)mc.currentScreen).actionPerformed(find(mc.currentScreen,"Back"));
                require(mc.currentScreen==gallery,"Card did not return to gallery");
                gallery.actionPerformed(find(gallery,"Prev"));require((int)ReflectionHelper.getPrivateValue(MapGalleryScreen.class,gallery,"page")==0,"Gallery Prev failed");
                GuiTextField search=ReflectionHelper.getPrivateValue(MapGalleryScreen.class,gallery,"search");search.setText("Allure");gallery.drawScreen(0,0,0);
                String favorite=UtilitySettings.favoriteKey(gallery.paths().get(0));
                UtilitySettings.mapFavorites.remove(favorite);
                gallery.mouseClicked(grid.x(0)+grid.cellWidth-10,grid.y(0)+10,0);
                require(mc.currentScreen==gallery&&UtilitySettings.mapFavorites.contains(favorite),"Gallery star did not save favorite without opening the card");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.mapFavorites.clear();UtilitySettings.load();
                require(UtilitySettings.mapFavorites.contains(favorite),"Gallery favorite did not persist");
                gallery.actionPerformed(find(gallery,"Favorites"));require(gallery.paths().contains(favorite+"/map.png"),"Favorites tab lost saved map");
                gallery.actionPerformed(find(gallery,"All"));require(gallery.paths().contains(favorite+"/map.png"),"All tab did not restore gallery");
                gallery.drawScreen(0,0,0);
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-gallery.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                gallery.mouseClicked(grid.x(0)+5,grid.y(0)+5,0);
                require("Allure".equals(((MapInfo)ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"info")).name),"Gallery search/click failed");
            }else if(state==10){
                if(!allureWasFavorite)UtilitySettings.mapFavorites.remove(UtilitySettings.favoriteKey("ctw/allure"));UtilitySettings.save();
                ResourceLocation texture=MapPreview.galleryTexture("ctw/allure/map.png");
                require(texture==MapPreview.galleryTexture("ctw/allure/map.png"),"Preview texture was duplicated");
                allureTexture=texture;
                ReliableChatScreen chat=new ReliableChatScreen("");mc.displayGuiScreen(chat);
                chat.select("party");GuiTextField input=ReflectionHelper.getPrivateValue(GuiChat.class,chat,"inputField","field_146415_a");
                input.setMaxStringLength(100);String longMessage=String.join("",Collections.nCopies(100,"x"));input.setText(longMessage);
                int beforePackets=packets.size();chat.keyTyped('\r',org.lwjgl.input.Keyboard.KEY_RETURN);
                require(mc.currentScreen==chat&&chat.text().equals(longMessage)&&packets.size()==beforePackets,"Long message was truncated, sent, or discarded");input.setText("");
                for(int i=0;i<5;i++)line("From Person"+i+": hello");
                chat.select("pm:person4");require("pm:person4".equals(ReliableChat.session().selected),"Hidden PM could not reopen");
                ReliableChat.session().close("pm:person4");chat.select("pm:person4");require("pm:person4".equals(ReliableChat.session().selected),"Closed PM could not reopen");
                ReliableChat.session().pm("pm:keepdraft","KeepDraft").draft="unsent";
                for(int i=5;i<50;i++)line("From Person"+i+": hello");
                ReliableChat.Session session=ReliableChat.session();session.trimPrivate();
                require(session.views.size()<=40&&session.views.containsKey("pm:keepdraft")&&session.views.containsKey(session.selected),"PM retention lost active/draft or grew past cap");
                ReliableChat.send("/msg Expire test");ReliableChat.expirePending(System.nanoTime()+31000000000L);require(ReliableChat.pending.isEmpty(),"Expired pending sends retained");
                if(remoteEnabled){remoteStarted=System.currentTimeMillis();MapRemote.lookup("PublicMaps/main/fun/blitz/100_rooms",m->{remote=m;remoteDone=true;});}
                else System.out.println("OTL_SMOKE: remote checks DISABLED (enable with -PotlSmokeRemote); offline checks continue");
            }else if(state==11){
                if(remoteEnabled){
                    if(!remoteDone){require(System.currentTimeMillis()-remoteStarted<90000,"PublicMaps lookup timed out");return;}
                    require(remote!=null&&remote.dir.startsWith("PublicMaps/main/"),"PublicMaps lookup failed");
                    require(remote.name.equals("100 Rooms")&&remote.players==30&&remote.created.equals("2013-06-02"),"100 Rooms basic metadata missing");
                    require(remote.authors.contains("xXFracXx")&&remote.contributors.contains("ParaSpider")&&remote.teams.equals("Blue 15, Red 15"),"100 Rooms credits/teams missing");
                    require(remote.objective.equals("Typical Blitz game with one life.")&&!remote.incomplete,"100 Rooms objective incomplete");
                    System.out.println("OTL_SMOKE: PublicMaps lookup PASS -> "+remote.name);
                    remoteDone=false;remote=null;remoteStarted=System.currentTimeMillis();
                    MapRemote.lookup("CommunityMaps/master/arcade/infection/infection_cataclysm",m->{remote=m;remoteDone=true;});
                }
            }else if(state==12){
                if(remoteEnabled){
                    if(!remoteDone){require(System.currentTimeMillis()-remoteStarted<90000,"CommunityMaps lookup timed out");return;}
                    require(remote!=null&&remote.name.equals("Infection: Cataclysm")&&remote.players==40,"CommunityMaps/shared metadata lookup failed");
                    require(remote.objective.contains("6 minutes")&&!remote.objective.contains("${")&&remote.teams.contains("Zombie Militant"),"Inactive variant or unresolved includes shown");
                    System.out.println("OTL_SMOKE: CommunityMaps lookup PASS -> "+remote.name);
                    mc.displayGuiScreen(new MapInfoScreen(remote,null));
                }
            }else if(state==13){
                if(remoteEnabled)ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-map-remote.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                if(remoteEnabled){
                    require(MapRemote.catalog.stream().filter(p->p.startsWith("PublicMaps/main/")).count()>800,"PublicMaps absent from gallery catalog");
                    require(MapRemote.catalog.stream().filter(p->p.startsWith("CommunityMaps/master/")).count()>700,"CommunityMaps absent from gallery catalog");
                    MapGalleryScreen publicGallery=new MapGalleryScreen(null);mc.displayGuiScreen(publicGallery);
                    GuiTextField search=ReflectionHelper.getPrivateValue(MapGalleryScreen.class,publicGallery,"search");search.setText("PublicMaps/main/fun/blitz/100_rooms");
                    require(publicGallery.paths().equals(Collections.singletonList("PublicMaps/main/fun/blitz/100_rooms/map.png")),"PublicMaps gallery filter/identity failed");
                }
            }else if(state==14){
                 if(remoteEnabled){
                     require(MapPreview.galleryTexture("PublicMaps/main/fun/blitz/100_rooms/map.png")!=null,"PublicMaps gallery preview missing");
                     ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-public-gallery.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                     MapGalleryScreen gallery=(MapGalleryScreen)mc.currentScreen;MapGalleryScreen.Grid grid=ReflectionHelper.getPrivateValue(MapGalleryScreen.class,gallery,"grid");
                     gallery.mouseClicked(grid.x(0)+5,grid.y(0)+5,0);
                     require(mc.currentScreen instanceof MapInfoScreen,"PublicMaps gallery click did not open card");
                      require("PublicMaps/main/fun/blitz/100_rooms".equals(ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"query")),"PublicMaps gallery click changed repository");
                 }
                 require(count("/t")==0,"Unexpected game re-entry /t");require(org.lwjgl.opengl.GL11.glGetError()==0,"GL error");
                 channel.pipeline().remove("otl_packets");System.out.println("OTL_SMOKE: fixture checks PASS; remote checks "+(remoteEnabled?"PASS (both repositories)":"DISABLED"));
                 if(serverEnabled){
                     mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);
                     UtilitySettings.devHosts.add("localhost");serverStarted=System.currentTimeMillis();
                     mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(),mc,new net.minecraft.client.multiplayer.ServerData("Local PGM","localhost:25565",false)));
                  }else{System.out.println("OTL_SMOKE: localhost checks DISABLED; checking gold chat hover in fixture world");openGoldChat();state=20;return;}
            }else if(state==15){
                require(mc.getCurrentServerData()!=null&&"localhost:25565".equals(mc.getCurrentServerData().serverIP),"Not connected to requested local server");
                require(ServerScope.active(),"Local test scope inactive");
                if(!serverOnly)require(allureTexture==MapPreview.galleryTexture("ctw/allure/map.png"),"Reconnect duplicated the map texture");
                UtilitySettings.previewOnCommand=false;mc.displayGuiScreen(new GuiChat("/map Haven Tide"));
                ((ReliableChatScreen)mc.currentScreen).keyTyped('\r',org.lwjgl.input.Keyboard.KEY_RETURN);
                MapPreview.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                require(mc.currentScreen==null,"Disabled command preview intercepted on localhost");
                UtilitySettings.previewOnCommand=true;mc.displayGuiScreen(new GuiChat(remoteEnabled?"/map 100\u2508Rooms":"/map Haven Tide"));
                ((ReliableChatScreen)mc.currentScreen).keyTyped('\r',org.lwjgl.input.Keyboard.KEY_RETURN);
                require(mc.currentScreen==null,"Command card must wait until chat closes");
                remoteStarted=System.currentTimeMillis();
            }else if(state==16){
                require(mc.currentScreen instanceof MapInfoScreen,"Actual Enter/send command path did not open card");
                MapInfo map=ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"info");
                if(map==null&&remoteEnabled){require(System.currentTimeMillis()-remoteStarted<30000,"Unicode command lookup timed out");return;}
                require(map!=null&&(remoteEnabled?"100 Rooms":"Haven Tide").equals(map.name),"Wrong localhost preview");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-localhost-map.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                System.out.println("OTL_SMOKE: actual Enter/send path PASS; named preview -> "+map.name);
                mc.displayGuiScreen(new GuiChat("/map"));((ReliableChatScreen)mc.currentScreen).keyTyped('\r',org.lwjgl.input.Keyboard.KEY_RETURN);
                remoteStarted=System.currentTimeMillis();
            }else if(state==17){
                if(!(mc.currentScreen instanceof MapInfoScreen)){require(System.currentTimeMillis()-remoteStarted<15000,"Bare /map response did not open card");return;}
                System.out.println("OTL_SMOKE: bare /map server response -> GUI PASS");
                UtilitySettings.chat=false;GuiChat vanilla=new GuiChat("/map Allure");mc.displayGuiScreen(vanilla);
                java.lang.reflect.Method key=GuiChat.class.getDeclaredMethod("keyTyped",char.class,int.class);key.setAccessible(true);key.invoke(vanilla,'\r',org.lwjgl.input.Keyboard.KEY_RETURN);
                require(mc.currentScreen==null,"Vanilla chat did not close after command");
            }else if(state==18){
                require(mc.currentScreen instanceof MapInfoScreen,"Vanilla Enter/send path failed");
                MapInfo map=ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"info");
                require(map!=null&&map.name.equals("Allure"),"Vanilla command opened wrong map");UtilitySettings.chat=true;
                MapPreview.INSTANCE.queueCommand("/map Allure");MapPreview.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                MapPreview.INSTANCE.mapResponse(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("XML: OvercastCommunity/PublicMaps /fun/blitz/100_rooms [View]")));
                MapPreview.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                require("PublicMaps/main/fun/blitz/100_rooms".equals(ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"query")),"Server repository response did not correct card");
                MapPreview.INSTANCE.queueCommand("/map Allure");MapPreview.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                GuiScreen other=new UtilityScreen();mc.displayGuiScreen(other);
                MapPreview.INSTANCE.mapResponse(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("XML: OvercastCommunity/PublicMaps /fun/blitz/100_rooms [View]")));
                MapPreview.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                require(mc.currentScreen==other,"Late map response replaced another screen");
                if(remoteEnabled){
                    ItemStack book=new ItemStack(net.minecraft.init.Items.written_book);NBTTagCompound nbt=new NBTTagCompound();
                    nbt.setString("title","Vote");nbt.setString("author","Server");NBTTagList pages=new NBTTagList();
                    pages.appendTag(new NBTTagString("{\"text\":\"100\u2508Rooms\",\"color\":\"blue\"}"));nbt.setTag("pages",pages);book.setTagCompound(nbt);
                    bookScreen=new GuiScreenBook(mc.thePlayer,book,false);mc.displayGuiScreen(bookScreen);
                    int scale=new ScaledResolution(mc).getScaleFactor();int left=(bookScreen.width-192)/2+36;
                    org.lwjgl.input.Mouse.setCursorPosition((left+2)*scale,mc.displayHeight-36*scale-1);
                }
            }else if(state==19){
                if(remoteEnabled){
                    require(mc.currentScreen==bookScreen,"Public vote book was replaced before rendering");
                    int left=(bookScreen.width-192)/2+36;MapPreview.Hover hit=MapPreview.INSTANCE.hover(bookScreen,left+2,36);
                    require(hit!=null&&hit.entry.path.equals("PublicMaps/main/fun/blitz/100_rooms"),"Public Unicode vote-book hover failed");
                    require(MapPreview.galleryTexture(hit.entry.path+"/map.png")!=null,"Public book hover texture missing");
                    ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-public-book-hover.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                }
                openGoldChat();
            }else if(state==20){
                if(metadataBlocked.getCount()!=0){require(System.currentTimeMillis()-remoteStarted<30000,"Metadata blocker did not start");return;}
                require(mc.currentScreen instanceof ReliableChatScreen,"Reliable Chat hover fixture missing");pointAtChatMap();
            }else if(state==21){
                require(mc.currentScreen instanceof ReliableChatScreen,"Hover unexpectedly opened another GUI");
                if(MapPreview.INSTANCE.chatTexture(hoverQuery())==null){require(System.currentTimeMillis()-remoteStarted<30000,"Gold chat preview texture timed out");return;}
                require(releaseMetadata.getCount()==1&&System.currentTimeMillis()-remoteStarted<30000,"Image check waited for metadata instead of loading independently");
            }else if(state==22){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-gold-chat-hover.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.largeMapPreview=true;
                UtilitySettings.chat=false;mc.displayGuiScreen(new GuiChat());pointAtChatMap();
            }else if(state==23){
                require(mc.currentScreen!=null&&mc.currentScreen.getClass()==GuiChat.class,"Vanilla Chat hover fixture missing");
                require(MapPreview.INSTANCE.chatTexture(hoverQuery())!=null,"Vanilla Chat preview missing");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-gold-vanilla-hover.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                releaseMetadata.countDown();UtilitySettings.chat=true;UtilitySettings.largeMapPreview=false;
                if(remoteEnabled){
                    ItemStack book=new ItemStack(net.minecraft.init.Items.written_book);NBTTagCompound nbt=new NBTTagCompound();
                    nbt.setString("title","Vote");nbt.setString("author","Server");NBTTagList pages=new NBTTagList();
                    pages.appendTag(new NBTTagString("{\"text\":\"Hallows\",\"color\":\"gold\"}"));nbt.setTag("pages",pages);book.setTagCompound(nbt);
                    bookScreen=new GuiScreenBook(mc.thePlayer,book,false);mc.displayGuiScreen(bookScreen);
                    int scale=new ScaledResolution(mc).getScaleFactor();int left=(bookScreen.width-192)/2+36;
                    org.lwjgl.input.Mouse.setCursorPosition((left+2)*scale,mc.displayHeight-36*scale-1);
                }
            }else if(state==24){
                if(remoteEnabled){
                    int left=(bookScreen.width-192)/2+36;MapPreview.Hover hit=MapPreview.INSTANCE.hover(bookScreen,left+2,36);
                    require(hit!=null&&hit.entry.path.equals("PublicMaps/main/ctw/hallows"),"Hallows book hover resolved the wrong map");
                    require(MapPreview.galleryTexture(hit.entry.path+"/map.png")!=null,"Hallows book hover image missing");
                    ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hallows-book-hover.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                }
                UtilitySettings.leftHand=false;mc.gameSettings.thirdPersonView=0;mc.gameSettings.hideGUI=false;
                mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem]=new ItemStack(net.minecraft.init.Items.iron_sword);
                mc.displayGuiScreen(null);
            }else if(state==25){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-right.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.leftHand=true;UtilitySettings.mainHandLeft=true;UtilitySettings.handType=LeftHand.Type.ALL;
            }else if(state==26){
                require(mc.entityRenderer.itemRenderer instanceof LeftHand.Items,"Left Hand renderer was not installed");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-left.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int mode=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE),front=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE);
                java.nio.FloatBuffer before=org.lwjgl.BufferUtils.createFloatBuffer(16),after=org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,before);
                try{
                    LeftHand.mirrored(()->{
                        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,after);
                        require(after.get(0)==-before.get(0),"Hand projection did not mirror");
                        require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE)!=front,"Mirrored hand faces were not corrected");
                        throw new IllegalStateException("test renderer failure");
                    });
                    throw new AssertionError("Expected test renderer failure");
                }catch(IllegalStateException expected){require(expected.getMessage().equals("test renderer failure"),"Unexpected mirror failure");}
                org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,after);
                for(int i=0;i<16;i++)require(before.get(i)==after.get(i),"Hand projection leaked after renderer failure");
                require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE)==mode&&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FRONT_FACE)==front,"Hand graphics state leaked");
                mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem]=null;
            }else if(state==27){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-left-empty.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem]=new ItemStack(net.minecraft.init.Items.iron_sword);
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("Other","example.com",false));
            }else if(state==28){
                require(!ServerScope.active(),"Left Hand off-server fixture still approved");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-off-server.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("Fixture","play.oc.tc",false));UtilitySettings.leftHand=false;
            }else if(state==29){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-disabled.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.leftHand=true;UtilitySettings.mainHandLeft=false;
            }else if(state==30){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hand-preferred-right.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.displayGuiScreen(new PreferedHandSettings(new UtilityScreen()));
            }else if(state==31){
                if(scope.equals("bubbles"))checkBubbles();
                require(find(mc.currentScreen,"Main Hand: Right")!=null&&find(mc.currentScreen,"Type: ALL")!=null,"Preferred hand settings screen did not show Right/ALL");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-preferred-hand-settings.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.leftHand=false;
                require(org.lwjgl.opengl.GL11.glGetError()==0,"Left Hand left a GL error");
                mc.displayGuiScreen(new ServerNoticeSettings(new UtilityScreen()));
                require(find(mc.currentScreen,"Server Notices: OFF")!=null,"Server notices module missing/default not OFF");
                ((ServerNoticeSettings)mc.currentScreen).actionPerformed(find(mc.currentScreen,"Server Notices: OFF"));
                require(UtilitySettings.nonPlayerChat,"Server notices toggle did not enable");
                mc.displayGuiScreen(null);line("OTL smoke: server notice in top left");
                UtilitySettings.bubbles=true;mc.gameSettings.thirdPersonView=1;
                TeamBubbles.clearBubbles();line("(Any Team) "+mc.thePlayer.getName()+": third-person bubble render check");
                require(TeamBubbles.bubbles.containsKey(mc.thePlayer.getUniqueID()),"Own bubble missing before render");
            }else if(state==32){
                long rendered=ReflectionHelper.getPrivateValue(TeamBubbles.class,null,"renderedBubbles");
                require(rendered>0,"World render event never drew the third-person bubble");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-third-person-bubble-notices.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.gameSettings.thirdPersonView=2;
                line("(Any Team) "+mc.thePlayer.getName()+": front third-person bubble check");
            }else if(state==33){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-front-third-person-bubble.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.nonPlayerChat=false;UtilitySettings.bubbles=false;UtilitySettings.save();mc.gameSettings.thirdPersonView=0;
                ReliableChat.session().select(ChatRules.PARTY);int partyBefore=chatHistory("party").size(),allBefore=chatHistory("all").size();
                ReliableChat.send("/unknown-command");ReliableChat.session().select(ChatRules.TEAM);
                line("\u00a7c\u26a0 Unknown command");
                require(chatHistory("party").size()==partyBefore+1&&chatHistory("all").size()==allBefore,"Command warning did not route only to originating channel");
                line("<Alex>: \u26a0 quoted warning");require(chatHistory("party").size()==partyBefore+1,"Quoted player warning leaked to originating channel");
                ReliableChat.expirePending(System.nanoTime()+9000000000L);line("\u26a0 Unrelated later warning");
                require(chatHistory("party").size()==partyBefore+1,"Expired command context captured a later warning");ReliableChat.session().select("all");
                KillFeed.history.clear();UtilitySettings.killFeed=true;UtilitySettings.hideKillChat=false;
                UtilityScreen menu=new UtilityScreen();mc.displayGuiScreen(menu);
                for(int p=0;p<OtlModule.ALL.size()&&!buttons(menu).stream().anyMatch(b->b.displayString.equals("Kill Feed: ON"));p++)menu.actionPerformed(find(menu,">"));
                require(find(menu,"Kill Feed: ON")!=null,"Kill Feed missing from modules menu");
                KillFeedSettings options=new KillFeedSettings(menu);mc.displayGuiScreen(options);
                net.minecraftforge.fml.client.config.GuiSlider count=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==14).findFirst().get();
                count.setValue(1);count.updateSlider();require(KillFeed.rows()==1,"Visible Kill Feed limit did not apply");count.setValue(5);count.updateSlider();
                net.minecraftforge.fml.client.config.GuiSlider life=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==15).findFirst().get();
                life.setValue(12);life.updateSlider();require(UtilitySettings.killFeedSeconds==12,"Fade duration did not apply");life.setValue(10);life.updateSlider();
                require(UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.ALL,"Kill Feed filter should default ALL");
                options.actionPerformed(find(options,"Show entries: ALL"));require(UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.ONLY_ME,"Only Me filter did not apply");
                options.actionPerformed(find(options,"Show entries: Only Me"));require(UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.TEAM,"Only My Team filter did not apply");
                options.actionPerformed(find(options,"Show entries: Only My Team"));require(UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.ALL,"ALL filter did not restore");
                options.actionPerformed(find(options,"Hide deaths in chat: OFF"));
                net.minecraftforge.client.event.ClientChatReceivedEvent kill=new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("Victim was slain by "+mc.thePlayer.getName()+"'s Wooden Sword"));
                MinecraftForge.EVENT_BUS.post(kill);require(kill.isCanceled()&&KillFeed.history.entries.size()==1,"Hide chat did not preserve kill-feed entry");
                require(KillFeed.icon(KillFeed.history.entries.get(0)).getItem()==net.minecraft.init.Items.wooden_sword,"Wrong wooden sword icon");
                KillFeed.history.clear();UtilitySettings.killFeedHeads=KillFeedRules.HeadMode.NONE;
                line("AssistTarget was slain by Archer's Wooden Sword");int beforeAssist=KillFeed.bounds().w;
                line("+3 raindrops | Kill Assist");
                require(KillFeed.history.entries.size()==1&&KillFeed.history.entries.get(0).assist&&KillFeed.bounds().w>beforeAssist,"Assist did not update Kill Feed row width");
                require(KillFeed.history.entries.get(0).border(mc.thePlayer.getName(),UtilitySettings.killBorderColor,UtilitySettings.deathBorderColor,UtilitySettings.assistBorderColor,100)==0xFF55FFFF,"Assist border did not use its own color");
                KillFeed.history.clear();
                line("+3 raindrops | Kill Assist");line("LateTarget was slain by Archer's Bow");
                require(KillFeed.history.entries.get(0).assist,"Reward-first assist did not attach to the next death");
                line("\u00a7eYou joined \u00a79Blue\u00a7r team!");
                require(TeamBubbles.teamColor()=='9',"Team color was not captured for the assist label");
                require(KillFeedRules.assistLabel(mc.thePlayer.getName(),TeamBubbles.teamColor()).equals("\u00a79\u00a7l"+mc.thePlayer.getName()+"\u00a7r"),"Assist label did not use the local name and team color");
                UtilitySettings.killFeedHeads=KillFeedRules.HeadMode.ONLY_ME;
                options.actionPerformed(find(options,"Hide deaths in chat: ON"));
                require(!UtilitySettings.killFeedSlide,"Slide animation should default OFF");options.actionPerformed(find(options,"Slide animation: OFF"));
                options.actionPerformed(find(options,"Appearance"));
                require(UtilitySettings.killFeedHeads==KillFeedRules.HeadMode.ONLY_ME,"Player heads should default to Only Me");
                int meWidth=KillFeed.bounds().w;
                options.actionPerformed(find(options,"Player heads: Only Me"));
                require(UtilitySettings.killFeedHeads==KillFeedRules.HeadMode.ALL&&KillFeed.bounds().w>meWidth,"ALL did not include the other player's head in row width");
                options.actionPerformed(find(options,"Player heads: ALL"));
                require(UtilitySettings.killFeedHeads==KillFeedRules.HeadMode.NONE&&KillFeed.bounds().w<meWidth,"None did not remove heads from row width");
                options.actionPerformed(find(options,"Player heads: None"));
                require(UtilitySettings.killFeedHeads==KillFeedRules.HeadMode.ONLY_ME,"Head mode did not cycle back to Only Me");
                net.minecraftforge.fml.client.config.GuiSlider size=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==4).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==5).findFirst().get();
                size.setValue(125);size.updateSlider();opacity.setValue(25);opacity.updateSlider();
                net.minecraftforge.fml.client.config.GuiSlider outline=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==16).findFirst().get();outline.setValue(60);outline.updateSlider();
                options.actionPerformed(find(options,"Colors"));
                GuiTextField killColor=ReflectionHelper.getPrivateValue(KillFeedSettings.class,options,"killColor"),deathColor=ReflectionHelper.getPrivateValue(KillFeedSettings.class,options,"deathColor"),assistColor=ReflectionHelper.getPrivateValue(KillFeedSettings.class,options,"assistColor");
                killColor.setText("123456");deathColor.setText("654321");assistColor.setText("FEDCBA");options.onGuiClosed();
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.killFeedSize=75;UtilitySettings.killBackgroundOpacity=100;UtilitySettings.killBorderOpacity=100;UtilitySettings.killFeedSlide=false;UtilitySettings.killBorderColor=0;UtilitySettings.deathBorderColor=0;UtilitySettings.assistBorderColor=0;UtilitySettings.killFeedHeads=KillFeedRules.HeadMode.NONE;UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.ONLY_ME;UtilitySettings.load();
                 require(UtilitySettings.killFeedSize==125&&UtilitySettings.killBackgroundOpacity==25&&UtilitySettings.killBorderOpacity==60&&UtilitySettings.killFeedSlide&&UtilitySettings.killBorderColor==0x123456&&UtilitySettings.deathBorderColor==0x654321&&UtilitySettings.assistBorderColor==0xFEDCBA&&UtilitySettings.killFeedHeads==KillFeedRules.HeadMode.ONLY_ME&&UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.ALL,"Kill Feed appearance did not persist");
                killColor.setText("INVALID");options.onGuiClosed();require(UtilitySettings.killBorderColor==0x123456,"Invalid hex color replaced saved color");
                options.actionPerformed(find(options,"Reset colors"));options.actionPerformed(find(options,"Appearance"));
                net.minecraftforge.fml.client.config.GuiSlider sizeReset=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==4).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider opacityReset=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==5).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider outlineReset=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==16).findFirst().get();
                sizeReset.mousePressed(mc,sizeReset.xPosition+sizeReset.width-5,sizeReset.yPosition+8);
                opacityReset.mousePressed(mc,opacityReset.xPosition+opacityReset.width-5,opacityReset.yPosition+8);
                outlineReset.mousePressed(mc,outlineReset.xPosition+outlineReset.width-5,outlineReset.yPosition+8);
                require(UtilitySettings.killFeedSize==75&&UtilitySettings.killBackgroundOpacity==25&&UtilitySettings.killBorderOpacity==100&&UtilitySettings.killBorderColor==0xFFBD45&&UtilitySettings.deathBorderColor==0xFF5555&&UtilitySettings.assistBorderColor==0x55FFFF,"Appearance resets failed");
                options.actionPerformed(find(options,"Appearance"));
                net.minecraftforge.fml.client.config.GuiSlider sizeSnap=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==4).findFirst().get();
                int snapTrack=sizeSnap.width-22,snapX=sizeSnap.xPosition+4+(int)(snapTrack*0.7D);
                sizeSnap.mousePressed(mc,snapX,sizeSnap.yPosition+8);sizeSnap.mouseReleased(snapX,sizeSnap.yPosition+8);
                require(UtilitySettings.killFeedSize==125,"Preset slider did not snap to a preset");
                sizeSnap.mousePressed(mc,sizeSnap.xPosition+sizeSnap.width-5,sizeSnap.yPosition+8);
                require(UtilitySettings.killFeedSize==75,"Preset slider reset glyph failed");
                options.actionPerformed(find(options,"General"));options.actionPerformed(find(options,"Slide animation: ON"));options.actionPerformed(find(options,"Colors"));
                options.actionPerformed(buttons(options).stream().filter(b->b.id==19).findFirst().get());require(mc.currentScreen instanceof ColorSwatches,"Color swatches did not open");
                ((ColorSwatches)mc.currentScreen).actionPerformed(buttons(mc.currentScreen).get(4));
                require(mc.currentScreen==options&&UtilitySettings.killBorderColor==ColorSwatches.COLORS[4],"Swatch selection did not apply/return");options.actionPerformed(find(options,"Reset colors"));
                options.actionPerformed(buttons(options).stream().filter(b->b.id==24).findFirst().get());
                ((ColorSwatches)mc.currentScreen).actionPerformed(buttons(mc.currentScreen).get(12));
                require(mc.currentScreen==options&&UtilitySettings.assistBorderColor==ColorSwatches.COLORS[12],"Assist swatch selection did not apply");options.actionPerformed(find(options,"Reset colors"));
                net.minecraftforge.client.event.ClientChatReceivedEvent normal=new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("Target was shot out of the world by Archer from 50 blocks"));
                KillFeed.INSTANCE.receive(normal);require(!normal.isCanceled(),"Chat deaths hidden despite setting OFF");
                for(int i=0;i<1005;i++)KillFeed.history.add(KillFeedRules.parse("Old"+i+" died"));
                require(KillFeed.history.entries.size()==1000,"History exceeded 1,000");
                KillFeed.scrollBy(Integer.MAX_VALUE);int offset=ReflectionHelper.getPrivateValue(KillFeed.class,null,"scroll");
                require(offset==1000-KillFeed.rows(),"History scroll did not clamp");
                KillFeed.scrollBy(Integer.MIN_VALUE);require((int)ReflectionHelper.getPrivateValue(KillFeed.class,null,"scroll")==0,"History did not scroll back");
                line("\u00a79Victim\u00a7r was slain by \u00a7c"+mc.thePlayer.getName()+"\u00a7r's Wooden Sword");
                line("\u00a79Target\u00a7r was shot out of the world by \u00a7cArcher\u00a7r from 50 blocks");
                line("Alex fell out of the world");
                line("\u00a7c"+mc.thePlayer.getName()+"\u00a7r was slain by \u00a7c"+mc.thePlayer.getName()+"\u00a7r's Wooden Sword");
                line("\u00a7c"+mc.thePlayer.getName()+"\u00a7r was slain by \u00a79Enemy\u00a7r's Iron Sword");mc.displayGuiScreen(new GuiChat());
                ScaledResolution sr=new ScaledResolution(mc);int scale=sr.getScaleFactor();
                org.lwjgl.input.Mouse.setCursorPosition((sr.getScaledWidth()-50)*scale,mc.displayHeight-12*scale-1);
            }else if(state==34){
                require(org.lwjgl.opengl.GL11.glGetError()==0,"Kill Feed left a GL error");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-feed-history.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                ScaledResolution sr=new ScaledResolution(mc);int scale=sr.getScaleFactor();
                org.lwjgl.input.Mouse.setCursorPosition((sr.getScaledWidth()-50)*scale,mc.displayHeight-63*scale-1);
            }else if(state==35){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-feed-hover-second.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());mc.displayGuiScreen(null);
            }else if(state==36){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-feed.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                KillFeedSettings options=new KillFeedSettings(new UtilityScreen());mc.displayGuiScreen(options);options.actionPerformed(find(options,"Appearance"));
            }else if(state==37){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-feed-appearance.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.hideKillChat=false;UtilitySettings.killFeed=true;UtilitySettings.save();KillFeed.history.clear();
                HudEditor editor=new HudEditor();mc.displayGuiScreen(editor);require(find(editor,"Mods")!=null,"HUD editor missing central Mods button");
                GuiButton mods=find(editor,"Mods");require(mods.xPosition+mods.width/2==editor.width/2&&mods.yPosition+mods.height/2==editor.height/2,"Mods button is not centered");
                net.minecraft.client.settings.KeyBinding menuKey=null;
                for(net.minecraft.client.settings.KeyBinding key:mc.gameSettings.keyBindings){
                    require(!key.getKeyDescription().equals("key.otl.menu"),"Removed Face Rig shortcut is still registered");
                    if(key.getKeyDescription().equals("key.otl.modules"))menuKey=key;
                }
                require(menuKey!=null,"Mod Menu is missing from Minecraft Controls");
                int originalKey=menuKey.getKeyCode();menuKey.setKeyCode(org.lwjgl.input.Keyboard.KEY_NONE);
                require(menuKey.getKeyCode()==org.lwjgl.input.Keyboard.KEY_NONE,"Mod Menu cannot be unbound");
                menuKey.setKeyCode(originalKey);net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
                require(buttons(editor).size()==1,"Removed alignment toolbar is still present");
                UtilitySettings.hudPositions.put(HudLayout.KILLS,new HudLayout.Position(1,0));
                HudLayout.Rect r=HudEditor.bounds(HudLayout.KILLS);int px=r.x+r.w-25,py=r.y+2;
                editor.mouseClicked(px,py,0);editor.mouseClickMove(px-40,py+35,0,100);
                require(ReflectionHelper.getPrivateValue(HudEditor.class,editor,"dragging")!=null,"HUD guide grid did not enter drag mode");
                editor.mouseReleased(px-40,py+35,0);
                require(ReflectionHelper.getPrivateValue(HudEditor.class,editor,"dragging")==null,"HUD guide grid remained after drag");
                require(HudEditor.bounds(HudLayout.KILLS).x!=r.x||HudEditor.bounds(HudLayout.KILLS).y!=r.y,"HUD drag did not move panel");
                r=HudEditor.bounds(HudLayout.KILLS);int oldSize=UtilitySettings.killFeedSize;
                editor.mouseClicked(r.x+r.w,r.y,0);editor.mouseClickMove(r.x+r.w+30,r.y-15,0,100);editor.mouseReleased(r.x+r.w+30,r.y-15,0);
                require(UtilitySettings.killFeedSize>oldSize,"HUD corner resize did not increase scale");
                HudLayout.Rect resized=HudEditor.bounds(HudLayout.KILLS);require(Math.abs(resized.x-r.x)<=1&&Math.abs(resized.y+resized.h-r.y-r.h)<=1,"Resize moved opposite corner");
                UtilitySettings.killFeedSize=75;UtilitySettings.hudPositions.put(HudLayout.KILLS,new HudLayout.Position(1,1));
                UtilitySettings.save();UtilitySettings.hudPositions.clear();ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
                require(HudLayout.position(HudLayout.KILLS).x==1&&HudLayout.position(HudLayout.KILLS).y==1,"HUD position did not persist");
                r=HudEditor.bounds(HudLayout.KILLS);HudLayout.Rect frame=editor.frame(r);
                int toolY=editor.toolsY(r)+6;
                editor.mouseClicked(frame.x+frame.w-8,toolY,0);require(!UtilitySettings.killFeed,"HUD disable button did not disable module");
                editor.mouseClicked(frame.x+frame.w-8,toolY,0);require(UtilitySettings.killFeed,"HUD re-enable failed");
                editor.mouseClicked(frame.x+6,toolY,0);require(mc.currentScreen instanceof KillFeedSettings,"HUD gear did not open widget settings");
                ((KillFeedSettings)mc.currentScreen).actionPerformed(find(mc.currentScreen,"Back"));require(mc.currentScreen==editor,"Widget settings did not return to editor");
                editor.actionPerformed(find(editor,"Mods"));require(mc.currentScreen instanceof ModulesScreen,"Mods button did not open modules");
                ModulesScreen modules=(ModulesScreen)mc.currentScreen;
                String home=UtilitySettings.profile;UtilitySettings.minimapBorder=true;
                String created=UtilitySettings.newProfile();
                require(created!=null&&created.equals(UtilitySettings.profile),"New profile was not activated");
                require(!UtilitySettings.minimapBorder,"New profile did not start from defaults");
                String json=UtilitySettings.profileExport(created);
                require(json!=null&&json.contains("\"profile\""),"Profile export was empty");
                String renamed=UtilitySettings.renameProfile(created,"Shared test");
                require("Shared test".equals(renamed)&&"Shared test".equals(UtilitySettings.profile),"Profile rename did not stick");
                String second=UtilitySettings.newProfile();
                String numbered=UtilitySettings.renameProfile(second,"Shared test");
                require(numbered.startsWith("Shared test ")&&!numbered.equals("Shared test"),"Duplicate profile name was not numbered");
                UtilitySettings.switchProfile(home);require(home.equals(UtilitySettings.profile),"Profile switch did not return");
                UtilitySettings.minimapBorder=false;UtilitySettings.save();
                require(UtilitySettings.deleteProfile(renamed)&&UtilitySettings.deleteProfile(numbered),"Could not delete temporary profiles");
                String imported=UtilitySettings.importProfile(json);
                require(imported!=null&&imported.equals(UtilitySettings.profile),"Profile import did not activate a new profile");
                UtilitySettings.switchProfile(home);require(UtilitySettings.deleteProfile(imported),"Could not delete an imported profile");
                OtlModule minimapModule=OtlModule.ALL.stream().filter(m->m.id.equals("minimap")).findFirst().get();
                UtilitySettings.minimap=true;UtilitySettings.minimapSize=125;UtilitySettings.minimapOpacity=75;
                minimapModule.reset();
                require(UtilitySettings.minimapSize==100&&UtilitySettings.minimapOpacity==25&&!UtilitySettings.minimap,"Card reset did not restore module defaults");
                modules.initGui();modules.actionPerformed(find(modules,"DONE"));require(mc.currentScreen==editor,"Modules did not return to editor");
                mc.displayGuiScreen(new ModulesScreen(editor));
            }else if(state==38){
                require(mc.currentScreen instanceof ModulesScreen,"Modules menu missing for the layout screenshot");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-modules-bedrock.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.hudPositions.clear();UtilitySettings.killFeedRows=5;UtilitySettings.killFeedSeconds=10;UtilitySettings.save();
                KillFeed.history.clear();mc.displayGuiScreen(null);String own="\u00a7c"+mc.thePlayer.getName()+"\u00a7r";
                line(own+" fell out of the world");line(own+" hit the ground too hard");line(own+" fell into lava");line(own+" went up in flames");
                line("\u00a79Victim\u00a7r was punched into lava by "+own);UtilitySettings.killBorderOpacity=100;UtilitySettings.killBackgroundOpacity=100;
            }else if(state==39){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-context-opacity-100.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.killBackgroundOpacity=50;
            }else if(state==40){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-context-opacity-50.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.killBackgroundOpacity=0;
            }else if(state==41){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-context-opacity-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                require(org.lwjgl.opengl.GL11.glGetError()==0,"Context icons/opacity left a GL error");mc.displayGuiScreen(new HudEditor());
            }else if(state==42){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-preview-opacity-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.killBorderOpacity=0;UtilitySettings.killBackgroundOpacity=100;
            }else if(state==43){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-preview-opacity-100.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.save();
                UtilitySettings.killBorderOpacity=100;
                UtilitySettings.nonPlayerChat=true;UtilitySettings.noticeMode=ServerNoticeRules.Mode.ALL;ServerNotices.history.clear();
                ReliableChat.session().select("all");int oldChat=chatHistory("all").size();
                line("Server restarts in 60 seconds");require(chatHistory("all").size()==oldChat&&ServerNotices.history.size()==1,"Notice duplicated into normal chat");
                line("\u00a77Alex\u00a7r: observer chat stays here");line("<[VIP] Alex>: global chat stays here");line("(Observers) [VIP] *Alex: team chat stays here");
                require(ServerNotices.history.size()==1,"Player/observer chat entered Server Notices");
                UtilitySettings.noticeMode=ServerNoticeRules.Mode.JOINS_LEAVES;
                line("Alex joined the game");line("Another server announcement");require(ServerNotices.history.size()==2,"Join-only mode routed other notices");
                UtilitySettings.noticeMode=ServerNoticeRules.Mode.EXCEPT_JOINS_LEAVES;
                line("Alex left the game");line("Other announcement");require(ServerNotices.history.size()==3,"Exclude-joins mode routed join/leave notices");
                UtilitySettings.chat=false;line("Notice works without Reliable Chat");require(ServerNotices.history.size()==4,"Standalone notices depend on Reliable Chat");UtilitySettings.chat=true;
                ServerNoticeSettings options=new ServerNoticeSettings(new HudEditor());mc.displayGuiScreen(options);
                UtilitySettings.noticeSize=100;int normalNoticeWidth=ServerNotices.bounds().w;
                net.minecraftforge.fml.client.config.GuiSlider noticeSize=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==5).findFirst().get();noticeSize.setValue(125);noticeSize.updateSlider();
                require(UtilitySettings.noticeSize==125&&ServerNotices.bounds().w>normalNoticeWidth,"Notice slider did not scale HUD bounds");
                net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==2).findFirst().get();opacity.setValue(55);opacity.updateSlider();options.onGuiClosed();
                UtilitySettings.noticeMode=ServerNoticeRules.Mode.ALL;UtilitySettings.noticeOpacity=0;UtilitySettings.noticeSize=100;ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
                require(UtilitySettings.noticeMode==ServerNoticeRules.Mode.EXCEPT_JOINS_LEAVES&&UtilitySettings.noticeOpacity==55&&UtilitySettings.noticeSize==125,"Notice settings did not persist");
                require(buttons(options).stream().noneMatch(b->b.enabled&&b.id==0),"Unavailable notice module still toggles");
                UtilitySettings.restore("notice_size");UtilitySettings.save();
                require(UtilitySettings.noticeSize==100&&ServerNotices.bounds().w==normalNoticeWidth,"Notice size reset failed");
                UtilitySettings.nonPlayerChat=false;UtilitySettings.noticeMode=ServerNoticeRules.Mode.ALL;UtilitySettings.noticeOpacity=0;ReliableChat.session().select("all");
                ChatComponentText original=new ChatComponentText("Full original wrapped message "+String.join(" ",Collections.nCopies(50,"preserved")));
                original.getChatStyle().setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND,"/msg Alex "));
                ReliableChat.receive(original,0);mc.displayGuiScreen(new GuiChat());ReliableChat.currentView().bottom();
                require(chatHistory("all").get(0).getChatComponent()==original,"Vanilla history lost original full component");
                ReliableChat.installed.mirror();
                Object mirrored=ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.installed,"chatLines","field_146252_h");
                require(mirrored==chatHistory("all"),"Client chat extensions cannot see selected history");
                int factor=new ScaledResolution(mc).getScaleFactor();IChatComponent hit=mc.ingameGUI.getChatGUI().getChatComponent(10*factor,50*factor);
                require(hit!=null&&hit.getChatStyle().getChatClickEvent()!=null,"Vanilla click component action was lost");
                for(int i=0;i<90;i++)ReliableChat.receive(new ChatComponentText("<History>: scroll message "+i),0);
                ReliableChatScreen chat=(ReliableChatScreen)mc.currentScreen;ChatScroll.Bar bar=chat.scrollbar();require(bar!=null,"Chat scrollbar missing");
                chat.mouseClicked(bar.x+4,bar.top+1,0);require(ReliableChat.currentView().scroll()>0,"Scrollbar up button did not scroll");
                bar=chat.scrollbar();chat.mouseClicked(bar.x+4,bar.thumbTop+1,0);chat.mouseClickMove(bar.x+4,bar.trackTop,0,100);chat.mouseReleased(bar.x+4,bar.trackTop,0);
                require(ReliableChat.currentView().scroll()==bar.max,"Scrollbar drag did not reach oldest messages");
            }else if(state==44){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-draggable-scrollbar.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                ReliableChatScreen chat=(ReliableChatScreen)mc.currentScreen;ChatScroll.Bar bar=chat.scrollbar();
                chat.mouseClicked(bar.x+4,bar.trackTop+1,0);chat.mouseClickMove(bar.x+4,bar.top+bar.height+100,0,100);chat.mouseReleased(bar.x+4,bar.top+bar.height+100,0);
                require(ReliableChat.currentView().scroll()==0,"Scrollbar did not return to newest messages");
                require(OtlModule.ALL.stream().anyMatch(m->m.name.equals("Server Notices")),"Server Notices missing from modules");
                mc.displayGuiScreen(new HudEditor());
            }else if(state==45){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-all-previews.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.save();
                OtlModule confirm=OtlModule.ALL.stream().filter(m->m.name.equals("Kill Confirm")).findFirst().get();require(!confirm.enabled(),"Kill Confirm should default OFF");
                confirm.setEnabled(true);require(confirm.settings(mc.currentScreen) instanceof KillConfirmSettings,"Kill Confirm module settings missing");
                UtilitySettings.hideKillConfirmChat=false;KillConfirmSettings confirmOptions=new KillConfirmSettings(mc.currentScreen);mc.displayGuiScreen(confirmOptions);
                require(!UtilitySettings.killConfirmSounds,"Kill/assist sounds should default OFF");
                require(!UtilitySettings.totalExpKills,"Life recap should default OFF");
                require(!UtilitySettings.killConfirmPlayerHead,"Kill Confirm should default to the skull icon");
                net.minecraftforge.fml.client.config.GuiSlider confirmSize=(net.minecraftforge.fml.client.config.GuiSlider)buttons(confirmOptions).stream().filter(b->b.id==1).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider confirmOpacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(confirmOptions).stream().filter(b->b.id==3).findFirst().get();
                confirmSize.setValue(125);confirmSize.updateSlider();confirmOpacity.setValue(40);confirmOpacity.updateSlider();confirmOptions.actionPerformed(find(confirmOptions,"Hide rewards in chat: OFF"));confirmOptions.actionPerformed(find(confirmOptions,"Kill/assist sounds: OFF"));confirmOptions.actionPerformed(find(confirmOptions,"Total EXP, Kills & Assists: OFF"));confirmOptions.actionPerformed(find(confirmOptions,"Kill icon: Skull"));confirmOptions.onGuiClosed();
                UtilitySettings.killConfirm=false;UtilitySettings.killConfirmSize=75;UtilitySettings.killConfirmOpacity=0;UtilitySettings.hideKillConfirmChat=false;UtilitySettings.killConfirmSounds=false;UtilitySettings.totalExpKills=false;UtilitySettings.killConfirmPlayerHead=false;
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();require(confirm.enabled()&&UtilitySettings.killConfirmSize==125&&UtilitySettings.killConfirmOpacity==40&&UtilitySettings.hideKillConfirmChat&&UtilitySettings.killConfirmSounds&&UtilitySettings.totalExpKills&&UtilitySettings.killConfirmPlayerHead,"Kill Confirm preferences did not persist");
                confirmOptions.actionPerformed(find(confirmOptions,"Hide rewards in chat: ON"));require(!UtilitySettings.hideKillConfirmChat,"Reward chat hiding did not toggle OFF");
                confirmOptions.actionPerformed(find(confirmOptions,"Kill/assist sounds: ON"));require(!UtilitySettings.killConfirmSounds,"Kill/assist sounds did not toggle OFF");
                confirmOptions.actionPerformed(find(confirmOptions,"Total EXP, Kills & Assists: ON"));require(!UtilitySettings.totalExpKills,"Life recap did not toggle OFF");
                confirmOptions.actionPerformed(find(confirmOptions,"Kill icon: Player Head"));require(!UtilitySettings.killConfirmPlayerHead,"Skull icon did not toggle back");
                net.minecraftforge.fml.client.config.GuiSlider confirmReset=(net.minecraftforge.fml.client.config.GuiSlider)buttons(confirmOptions).stream().filter(b->b.id==1).findFirst().get();
                confirmReset.mousePressed(mc,confirmReset.xPosition+confirmReset.width-5,confirmReset.yPosition+8);require(UtilitySettings.killConfirmSize==75,"Small default size did not reset");
                UtilitySettings.hudPositions.put(HudLayout.CONFIRM,new HudLayout.Position(0,0));confirmOptions.actionPerformed(find(confirmOptions,"Reset position"));
                HudLayout.Rect confirmBox=KillConfirm.bounds();ScaledResolution confirmResolution=new ScaledResolution(mc);
                require(Math.abs(confirmBox.x+6-confirmResolution.getScaledWidth()/2)<=1&&Math.abs(confirmBox.y+27-confirmResolution.getScaledHeight()/2)<=1,"Kill Confirm plus sign is not centred on the crosshair");
                UtilitySettings.killConfirmOpacity=0;UtilitySettings.killFeed=false;UtilitySettings.killConfirmSounds=true;mc.displayGuiScreen(null);
                KillConfirm.state.hit("Enemy",20,System.nanoTime());KillConfirm.state.confirmDuel(14);
                line("Enemy was slain by "+mc.thePlayer.getName()+"'s Iron Sword");line("\u00a7b+5 raindrops \u00a77| \u00a7eKill");
                require(KillConfirm.state.reward.labels(KillConfirm.state.streak).contains("FLAWLESS"),"Flawless duel tag missing");
                require(KillConfirm.state.streak==1&&KillConfirm.state.reward.points==5,"Kill Confirm did not count reward independently of Kill Feed");
            }else if(state==46){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-kill.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                line("+5 raindrops | Kill");require(KillConfirm.state.streak==2&&KillConfirm.state.streakExp==10,"Second kill did not count or stack EXP");
                line("+3 raindrops | Kill Assist");require(KillConfirm.state.streak==2&&KillConfirm.state.streakExp==13&&KillConfirm.state.reward.assist&&KillConfirmRules.streakLabel(KillConfirm.state.streak,true).isEmpty(),"Assist did not stack EXP or changed streak");
                require(KillConfirm.state.reward.labels(KillConfirm.state.streak).equals(java.util.Arrays.asList("KILL ASSIST")),"Assist showed bonus rows");
            }else if(state==47){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-assist.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.nonPlayerChat=true;line("+5 raindrops | Kill");require(KillConfirm.state.streak==3&&KillConfirm.state.streakExp==18&&KillConfirmRules.streakLabel(KillConfirm.state.streak,false).isEmpty(),"Non-milestone kill lost EXP or showed a streak tag");
                require(!KillConfirm.state.reward.labels(KillConfirm.state.streak).contains("3 KILL STREAK"),"Non-milestone streak row leaked into the white stack");
                line(mc.thePlayer.getName()+" fell out of the world");require(KillConfirm.state.streak==0&&KillConfirm.state.reward==null,"Death did not reset streak with Kill Feed OFF");
                line("+5 raindrops | Kill");KillConfirm.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(mc.theWorld));require(KillConfirm.state.streak==0&&KillConfirm.state.reward==null,"World change did not reset confirmations");
                UtilitySettings.hideKillConfirmChat=true;int noticeCount=ServerNotices.history.size(),chatCount=chatHistory("all").size();
                line("+5 raindrops | Kill");line("+3 raindrops | Kill Assist");
                require(KillConfirm.state.streak==1&&KillConfirm.state.reward.assist&&ServerNotices.history.size()==noticeCount&&chatHistory("all").size()==chatCount,"Hidden rewards leaked to chat/notices or stopped popups");
                line("<Alex>: +5 raindrops | Kill");require(chatHistory("all").get(0).getChatComponent().getUnformattedText().equals("<Alex>: +5 raindrops | Kill")&&KillConfirm.state.streak==1,"Reward hiding suppressed player chat");
                KillConfirm.state.clear();UtilitySettings.killConfirm=false;line("+5 raindrops | Kill");require(KillConfirm.state.reward==null&&ServerNotices.history.size()==noticeCount+1,"Disabled module counted/hidden reward");UtilitySettings.hideKillConfirmChat=false;
                UtilitySettings.killFeed=true;UtilitySettings.nonPlayerChat=false;UtilitySettings.killConfirmSounds=false;UtilitySettings.save();
                require(org.lwjgl.opengl.GL11.glGetError()==0,"Kill Confirm left a GL error");
                UtilityScreen appearance=new UtilityScreen(true);mc.displayGuiScreen(appearance);appearance.actionPerformed(find(appearance,"Appearance"));
                net.minecraftforge.fml.client.config.GuiSlider tabs=(net.minecraftforge.fml.client.config.GuiSlider)buttons(appearance).stream().filter(b->b.id==16).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider typingTabs=(net.minecraftforge.fml.client.config.GuiSlider)buttons(appearance).stream().filter(b->b.id==17).findFirst().get();
                int chatBackground=UtilitySettings.chatBackground;tabs.setValue(35);tabs.updateSlider();typingTabs.setValue(70);typingTabs.updateSlider();appearance.onGuiClosed();
                UtilitySettings.tabBackgroundOpacity=100;UtilitySettings.typingTabBackgroundOpacity=100;ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
                require(UtilitySettings.tabBackgroundOpacity==35&&UtilitySettings.typingTabBackgroundOpacity==70&&UtilitySettings.chatBackground==chatBackground,"Typing/idle tab opacity did not persist independently");
                appearance.actionPerformed(find(appearance,"Reset chat options"));require(UtilitySettings.tabBackgroundOpacity==25&&UtilitySettings.typingTabBackgroundOpacity==25,"Tab background reset failed");
            }else if(state==48){
                UtilitySettings.killConfirm=true;UtilitySettings.totalExpKills=true;UtilitySettings.hideKillConfirmChat=false;UtilitySettings.killFeed=false;
                KillConfirm.state.clear();mc.displayGuiScreen(null);
                String local=mc.thePlayer.getName();long now=System.nanoTime();
                KillConfirm.state.receive("RecapTarget was slain by "+local+"'s Iron Sword",local,now-2000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-1900000000L,20);
                KillConfirm.state.receive("+3 raindrops | Kill Assist",local,now-1800000000L,20);
                require(!mc.thePlayer.isDead&&mc.thePlayer.getHealth()>0,"Teleport-respawn fixture must stay alive client-side");
                KillConfirm.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                double deathX=mc.thePlayer.posX,deathY=mc.thePlayer.posY,deathZ=mc.thePlayer.posZ;
                line(local+" fell out of the world");
                require(KillConfirm.state.awaitingRespawn&&KillConfirm.state.recapAt==0,"Death did not arm the respawn recap");
                mc.thePlayer.setPosition(deathX+1,deathY,deathZ);KillConfirm.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                require(KillConfirm.state.recapAt==0,"Ordinary movement triggered the recap");
                mc.thePlayer.setPosition(deathX+10,deathY,deathZ);KillConfirm.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                long recapAt=KillConfirm.state.recapAt;
                require(recapAt>0&&!KillConfirm.state.awaitingRespawn,"PGM-style alive-player teleport did not show the recap");
                mc.thePlayer.setPosition(deathX,deathY,deathZ);KillConfirm.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                require(KillConfirm.state.recapAt==recapAt,"Second teleport replayed the recap");
                require(KillConfirm.state.reward==null&&KillConfirm.state.rows().get(0).text.equals("EXP 8")
                    &&KillConfirm.state.rows().get(1).text.equals("KILLS EXP 5 (1 KILLS)")&&KillConfirm.state.rows().get(2).text.equals("ASSISTS EXP 3 (1 ASSISTS)"),"Respawn recap lost separate kill/assist totals or retained gold EXP");
                System.out.println("OTL_SMOKE: teleport respawn with no client death flag, movement filtering and one-shot EXP/kill recap PASS");
            }else if(state==49){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-respawn-recap.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.killConfirm=true;UtilitySettings.hideKillConfirmChat=false;UtilitySettings.killFeed=false;
                KillConfirm.state.clear();mc.displayGuiScreen(null);
                long now=System.nanoTime();String local=mc.thePlayer.getName();
                for(int i=0;i<4;i++){
                    long at=now-(5000000000L-i*500000000L);
                    KillConfirm.state.receive("Burst"+i+" was slain by "+local+"'s Iron Sword",local,at,20);
                    KillConfirm.state.receive("+5 raindrops | Kill",local,at+1,20);
                }
                java.util.List<KillConfirmRules.Row> rows=KillConfirm.state.rows();
                require(KillConfirm.state.streak==4&&KillConfirm.state.streakExp==20,"Burst EXP did not update immediately");
                require(rows.stream().filter(r->r.text.equals("KILL")).count()==4,"Four-kill stack lost a kill row");
                require(rows.stream().anyMatch(r->r.text.equals("QUAD KILL")),"Fourth kill missing QUAD KILL");
                for(int i=1;i<rows.size();i++)require(rows.get(i).start>rows.get(i-1).start,"Burst rows jumped the queue");
                require(KillConfirmRules.newestRow(rows,now)<rows.size()-1,"Burst did not leave queued rows to reveal");
            }else if(state==50){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-burst-queued.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            }else if(state==51){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-burst-fourth.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                KillConfirm.state.clear();long now=System.nanoTime();String local=mc.thePlayer.getName();
                KillConfirm.state.streak=99;KillConfirm.state.multi=9;KillConfirm.state.lastKillAt=now-4000000000L;
                KillConfirm.state.receive("TierTarget was slain by "+local+"'s Iron Sword",local,now-3000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-2999999999L,20);
                require(KillConfirm.state.reward.labels(100).equals(java.util.Arrays.asList("KILL","MULTI KILL","10 KILL CHAIN","THE LIMIT BREAKER","100 KILL STREAK")),"Expanded chain/streak labels or count order are wrong");
                KillConfirm.draw(false);
                KillConfirm.state.clear();
                KillConfirm.state.receive("CloseTarget was shot by "+local+" from 3 blocks",local,now-1000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-999999999L,20);
                require(KillConfirm.state.reward.labels(1).contains("POINT BLANK"),"Three-block POINT BLANK missing");
                KillConfirm.state.clear();
                KillConfirm.state.receive("FarTarget was shot by "+local+" from 60 blocks",local,now-1000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-999999999L,20);
                require(KillConfirm.state.reward.labels(1).contains("EXTREME RANGE")&&!KillConfirm.state.reward.labels(1).contains("LONG SHOT"),"EXTREME RANGE did not replace LONG SHOT");
                KillConfirm.state.clear();
                for(int count=1;count<=5;count++){
                    long at=now-3000000000L-(5-count)*4000000000L;
                    KillConfirm.state.receive("RepeatTarget was slain by "+local+"'s Iron Sword",local,at,20);
                    KillConfirm.state.receive("+5 raindrops | Kill",local,at+1,20);
                }
                require(KillConfirm.state.reward.labels(5).equals(java.util.Arrays.asList("KILL","DOMINATION","RAMPAGE","5 KILL STREAK")),"Fifth-opponent DOMINATION or streak count missing");
                KillConfirm.draw(false);
                KillConfirm.state.clear();
                for(int count=1;count<=5;count++){
                    long at=now-(6-count)*1000000000L;
                    KillConfirm.state.receive("Unique"+count+" was slain by "+local+"'s Iron Sword",local,at,20);
                    KillConfirm.state.receive("+5 raindrops | Kill",local,at+1,20);
                }
                require(KillConfirm.state.reward.labels(5).containsAll(java.util.Arrays.asList("CLEAN SWEEP","BRAWLER","PENTA KILL")),"Unique/melee chain tags missing");
                KillConfirm.state.clear();
                KillConfirm.state.receive("BlastTarget was blown up by "+local+"'s TNT",local,now-1000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-999999999L,20);
                require(KillConfirm.state.reward.labels(1).contains("DEMOLITION"),"Credited explosion tag missing");
                KillConfirm.state.clear();
                for(int count=1;count<=5;count++){
                    long at=now-1000000000L-(6-count)*4000000000L;
                    KillConfirm.state.receive(local+" was slain by Nemesis's Iron Sword",local,at,20);
                    KillConfirm.state.respawn(at+1,false);
                }
                KillConfirm.state.receive("Nemesis was slain by "+local+"'s Iron Sword",local,now-1000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-999999999L,20);
                require(KillConfirm.state.reward.labels(1).contains("REDEMPTION")&&!KillConfirm.state.reward.labels(1).contains("REVENGE"),"REDEMPTION missing or duplicated REVENGE");
                KillConfirm.draw(false);
                KillConfirm.state.clear();
                KillConfirm.state.receive("SwordTarget was slain by "+local+"'s Iron Sword",local,now-3000000000L,20);
                KillConfirm.state.receive("+5 raindrops | Kill",local,now-2999999999L,20);
                for(int i=0;i<3;i++){
                    long at=now-2000000000L+i*1000000000L;
                    KillConfirm.state.receive("HotTarget"+i+" was shot by "+local+" from 20 blocks",local,at,20);
                    KillConfirm.state.receive("+5 raindrops | Kill",local,at+1,20);
                }
                require(KillConfirm.state.reward.labels(4).equals(java.util.Arrays.asList("BOW KILL","HOT HAND","MIXED ARSENAL","QUAD KILL")),"New weapon-chain tags or bonus limit incorrect");
                KillConfirm.draw(false);
                require(org.lwjgl.opengl.GL11.glGetError()==0,"Expanded chain/streak rendering left a GL error");
                KillConfirm.state.clear();UtilitySettings.killConfirm=false;UtilitySettings.totalExpKills=false;UtilitySettings.killFeed=true;UtilitySettings.save();
                prepareTabs(mc);
            }else if(state==52){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-input-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());mc.displayGuiScreen(null);
            }else if(state==53){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-gameplay-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());mc.displayGuiScreen(new HudEditor());
            }else if(state==54){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-preview-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.tabBackgroundOpacity=100;UtilitySettings.typingTabBackgroundOpacity=100;UtilitySettings.save();
            }else if(state==55){
                checkBow(mc);
            }else if(state==56){
                prepareAccuracy(mc);
            }else if(state==57){
                checkAccuracy(mc);
            }else if(state==58){
                finishAccuracy(mc);
            }else if(state==59){
                prepareMinimap(mc);
            }else if(state==60){
                if(!checkMinimap(mc))return;
            }else if(state==61){
                finishMinimap(mc);
            }else if(state==62){
                UtilitySettings.minimap=true;
                line("Playing OTL Missing Model Fixture by Nobody");
                require(ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"model")==null,"Unknown map kept the previous map model");
                Minimap.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));minimapStarted=System.nanoTime();
            }else if(state==63){
                String status=ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"status");
                if(status.equals("Loading map...")){require(System.nanoTime()-minimapStarted<15000000000L,"Unknown-map fallback did not finish");return;}
                require(status.equals("Preview unavailable")&&ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"model")==null,"Unknown-map fallback failed");
                Minimap.drawPreview();require(!Minimap.INSTANCE.renderer.loaded(),"Unknown map still drew an old GPU model");
                Minimap.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(mc.theWorld));
                require(ReflectionHelper.getPrivateValue(Minimap.class,Minimap.INSTANCE,"query")==null,"World change retained the previous map identity");
                for(Map.Entry<String,Object> preference:minimapPreferences.entrySet())UtilitySettings.class.getDeclaredField(preference.getKey()).set(null,preference.getValue());
                UtilitySettings.save();
                System.out.println("OTL_SMOKE: join banner while disabled, current-map matching, unknown-model fallback and world-change clearing PASS");
                if(scope.equals("all"))System.out.println("OTL_SMOKE: PASS - Minimap, Bow Optimizer/Accuracy, Kill Confirm, native component compatibility, HUD and prior fixtures");
            }
            if(state==scopeEnd){
                if(!scope.equals("all"))System.out.println("OTL_SMOKE: PASS - "+scope);
                if(channel!=null&&channel.pipeline().get("otl_packets")!=null)channel.pipeline().remove("otl_packets");
                mc.shutdown();return;
            }
            state++;
        }catch(Throwable t){t.printStackTrace();System.out.println("OTL_SMOKE: FAIL - "+t);FMLCommonHandler.instance().exitJava(1,false);}
    }
}
