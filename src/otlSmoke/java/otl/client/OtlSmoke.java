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
    int ggBaseline;
    long serverStarted;
    ResourceLocation allureTexture;
    boolean allureWasFavorite;
    MapInfoScreen card,searchCard;String cardDir;long cardWait;
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
    MapInfo cardInfo(MapInfoScreen screen){
        return screen==null?null:ReflectionHelper.getPrivateValue(MapInfoScreen.class,screen,"info");
    }
    void line(String text){MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,new ChatComponentText(text)));}
    /** A chat line already cancelled by Reliable Chat, as it reaches the client in-game. */
    void cancelledLine(String text){
        net.minecraftforge.client.event.ClientChatReceivedEvent e=new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,new ChatComponentText(text));
        e.setCanceled(true);MinecraftForge.EVENT_BUS.post(e);
    }
    /** A PGM vote line: symbol + map name sharing one /votenext command, with a tag hover. */
    void voteLine(String symbol,String map,String tags){
        ChatComponentText root=new ChatComponentText(symbol+" ");
        root.getChatStyle().setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND,"/votenext -o "+map));
        root.getChatStyle().setChatHoverEvent(new net.minecraft.event.HoverEvent(net.minecraft.event.HoverEvent.Action.SHOW_TEXT,new ChatComponentText(tags)));
        ChatComponentText name=new ChatComponentText(map);
        name.getChatStyle().setColor(net.minecraft.util.EnumChatFormatting.GOLD).setBold(true);
        root.appendSibling(name);
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,root));
    }
    List<GuiButton> buttons(GuiScreen screen){return ReflectionHelper.getPrivateValue(GuiScreen.class,screen,"buttonList","field_146292_n");}
    void click(int id){for(GuiButton b:buttons(face))if(b.id==id){face.actionPerformed(b);return;}throw new AssertionError("Missing button "+id);}
    /** Everything the team bar could read from a real match, printed so the live scope can be checked. */
    /** Raw chat text with § shown as & so colour codes survive the console. */
    static String visible(String text){return text==null?"null":text.replace('\u00a7','&');}
    void dumpMatch(Minecraft mc){
        net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
        net.minecraft.scoreboard.ScoreObjective sidebar=board.getObjectiveInDisplaySlot(1);
        System.out.println("OTL_LIVE: sidebar="+(sidebar==null?"none":ChatRules.plain(sidebar.getDisplayName())));
        if(sidebar!=null)for(net.minecraft.scoreboard.Score score:board.getSortedScores(sidebar)){
            net.minecraft.scoreboard.ScorePlayerTeam team=board.getPlayersTeam(score.getPlayerName());
            System.out.println("OTL_LIVE:   ["+score.getScorePoints()+"] "+visible(score.getPlayerName())
                +(team==null?"":"  prefix="+visible(team.getColorPrefix())+" suffix="+visible(team.getColorSuffix())));
        }
        System.out.println("OTL_LIVE: boss="+ChatRules.plain(net.minecraft.entity.boss.BossStatus.bossName==null?"":net.minecraft.entity.boss.BossStatus.bossName));
        try{
            Object tab=mc.ingameGUI.getTabList();
            java.lang.reflect.Field footer=ReflectionHelper.findField(tab.getClass(),"footer","field_175255_h");
            Object value=footer.get(tab);
            System.out.println("OTL_LIVE: footer="+(value instanceof net.minecraft.util.IChatComponent?
                visible(((net.minecraft.util.IChatComponent)value).getFormattedText()):String.valueOf(value)));
        }catch(Exception e){System.out.println("OTL_LIVE: footer=unknown ("+e.getClass().getSimpleName()+")");}
        for(net.minecraft.scoreboard.ScorePlayerTeam team:board.getTeams()){
            if(team.getTeamName().contains("TabView"))continue;
            System.out.println("OTL_LIVE: team '"+team.getTeamName()+"' members="+team.getMembershipCollection());
        }
        for(TeamBar.Side side:TeamBar.sides(mc))System.out.println("OTL_LIVE: bar side '"+side.name+"' "+side.members);
        TeamBar.Timer timer=TeamBar.INSTANCE.timer(mc);
        System.out.println("OTL_LIVE: bar timer="+(timer==null?"none":timer.label+" countdown="+timer.countdown));
    }

    /** Replaces the sidebar's only score line, the way PGM updates it once per second. */
    void sidebar(net.minecraft.scoreboard.Scoreboard board,net.minecraft.scoreboard.ScoreObjective objective,String text){
        for(net.minecraft.scoreboard.Score score:new ArrayList<>(board.getSortedScores(objective)))board.removeObjectiveFromEntity(score.getPlayerName(),objective);
        board.getValueFromObjective(text,objective).setScorePoints(1);
    }
    /** Writes the tab-list footer the way PGM's scoreboard mode does. */
    void tabFooter(Minecraft mc,String text){
        try{
            Object tab=mc.ingameGUI.getTabList();
            java.lang.reflect.Field footer=ReflectionHelper.findField(tab.getClass(),"footer","field_175255_h");
            footer.set(tab,text==null||text.isEmpty()?null:new net.minecraft.util.ChatComponentText(text));
        }catch(Exception broken){throw new AssertionError("Cannot write the tab footer: "+broken);}
    }
    /** Adds a tab-list entry the way the server does; a grey name is how PGM marks a dead player. */
    void tabEntry(Minecraft mc,String name,String formatted){
        com.mojang.authlib.GameProfile profile=new com.mojang.authlib.GameProfile(
            java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name);
        net.minecraft.client.network.NetworkPlayerInfo info=new net.minecraft.client.network.NetworkPlayerInfo(profile);
        info.setDisplayName(new ChatComponentText(formatted));
        Map<UUID,net.minecraft.client.network.NetworkPlayerInfo> map=ReflectionHelper.getPrivateValue(
            net.minecraft.client.network.NetHandlerPlayClient.class,mc.getNetHandler(),"playerInfoMap");
        map.put(profile.getId(),info);
    }
    /** Removes a tab-list entry the way the server does when a player disconnects. */
    void removeTabEntry(Minecraft mc,String name){
        Map<UUID,net.minecraft.client.network.NetworkPlayerInfo> map=ReflectionHelper.getPrivateValue(
            net.minecraft.client.network.NetHandlerPlayClient.class,mc.getNetHandler(),"playerInfoMap");
        UUID gone=null;
        for(net.minecraft.client.network.NetworkPlayerInfo info:map.values())
            if(info.getGameProfile().getName().equals(name)){gone=info.getGameProfile().getId();break;}
        if(gone!=null)map.remove(gone);
    }
    GuiButton find(GuiScreen s,String label){for(GuiButton b:buttons(s))if(b.displayString.equals(label))return b;throw new AssertionError("Missing button "+label);}
    /** One named team from a sides() result, or null. */
    TeamBar.Side side(java.util.List<TeamBar.Side> sides,String name){
        for(TeamBar.Side side:sides)if(side.name.equals(name))return side;
        return null;
    }
    /** Clicks the ON/OFF strip of a module card in the browser grid (tab ALL). */
    void cardToggle(ModulesScreen s,int index)throws java.io.IOException{
        int w=Math.min(470,s.width-16),h=Math.min(272,s.height-16),x=(s.width-w)/2,y=(s.height-h)/2;
        int contentX=x+132+8,cardW=Math.max(70,(x+w-14-contentX-12)/3);
        int cardH=Math.max(64,((y+h-32)-10-(y+52))/2);
        int scrollRow=ReflectionHelper.getPrivateValue(ModulesScreen.class,s,"scrollRow");
        int cx=contentX+(index%3)*(cardW+4),cy=y+52+((index/3)-scrollRow)*(cardH+4);
        s.mouseClicked(cx+cardW/2,cy+cardH-10,0);
    }
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
        if(mc.thePlayer!=null){mate.setPosition(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);other.setPosition(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);}
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
        for(String name:new String[]{"minimap","minimapSize","minimapOpacity","minimapLargeOpacity","minimapLargeBackground","minimapHeadSize","minimapZoom","minimapAltZoom","minimapBorder"})minimapPreferences.put(name,UtilitySettings.class.getDeclaredField(name).get(null));
        minimapPosition=UtilitySettings.hudPositions.get(HudLayout.MINIMAP);
        minimapX=mc.thePlayer.posX;minimapY=mc.thePlayer.posY;minimapZ=mc.thePlayer.posZ;minimapFlying=mc.thePlayer.capabilities.isFlying;
        UtilitySettings.minimap=false;UtilitySettings.minimapBorder=true;line("            Allure            ");line(" Capture the enemy's wool!");line(" Created by jcane");
        MinimapSettings options=new MinimapSettings(null);mc.displayGuiScreen(options);
        options.actionPerformed(find(options,"Minimap: OFF"));
        net.minecraftforge.fml.client.config.GuiSlider size=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==1).findFirst().get();
        net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==2).findFirst().get();
        size.setValue(120);size.updateSlider();opacity.setValue(65);opacity.updateSlider();
        options.actionPerformed(find(options,"Display"));
        int plain=Minimap.bounds().w;
        require(UtilitySettings.minimapBorder,"Minimap border is not enabled by default");
        options.actionPerformed(find(options,"Border: ON"));
        require(!UtilitySettings.minimapBorder&&Minimap.bounds().w==plain-4,"Minimap border did not drop its padding");
        options.actionPerformed(find(options,"Border: OFF"));
        require(UtilitySettings.minimapBorder&&Minimap.bounds().w==plain,"Minimap border did not add back its 2px bevel");
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
        require(Minimap.bounds().w==Minimap.bounds().h-(UtilitySettings.minimapCoordinates?13:0),"Minimap background is not square");
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
            require(frames>minimapFrames&&Minimap.bounds().w==Minimap.bounds().h-(UtilitySettings.minimapCoordinates?13:0),"Moving/resizing stopped the square minimap renderer");
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
        // The custom vote screen hijacks book opens; only the vote scope exercises it.
        UtilitySettings.customVotebook=scope.equals("vote");
        if(serverOnly||scope.equals("all"))return;
        mc.setServerData(new net.minecraft.client.multiplayer.ServerData("OTL fixture","play.oc.tc",false));
        switch(scope){
            case "chat":state=0;scopeEnd=3;return;
            case "face":state=4;scopeEnd=5;FaceConfig.reset();UtilitySettings.faceRig=true;UtilitySettings.otherFaces=true;mc.displayGuiScreen(face=new FaceScreen());return;
            case "menu":state=6;scopeEnd=6;UtilitySettings.faceRig=true;mc.displayGuiScreen(new ModulesScreen(null));return;
            case "gui":state=64;scopeEnd=68;mc.displayGuiScreen(new ModulesScreen(null));return;
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
            case "vote":state=69;scopeEnd=78;return;
            case "teambar":state=79;scopeEnd=89;UtilitySettings.teamBar=true;return;
            case "live":state=90;scopeEnd=110;UtilitySettings.teamBar=true;UtilitySettings.devHosts.add("localhost");return;
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
                FaceConfig.reset();UtilitySettings.faceRig=true;UtilitySettings.otherFaces=true;mc.displayGuiScreen(face=new FaceScreen());
                require(buttons(face).stream().noneMatch(b->b.displayString.contains("Advanced")||b.displayString.contains("Expression")),"Obsolete controls");
            }else if(state==4){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-face-rig.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                click(11);require(buttons(face).stream().anyMatch(b->b.displayString.equals("Save")),"Editor save missing");
                require(buttons(face).stream().noneMatch(b->b.id==23),"Removed Brow tool still present");
                require(find(face,"Auto-detect")!=null,"Auto-detect tool missing");
            }else if(state==5){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-editor.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int[] original=FaceMarks.marks(mc.thePlayer.getLocationSkin());original=original==null?null:original.clone();
                int x=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gx"),y=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gy");face.panelMouseClicked(x+2,y+2,0);click(28);
                require(Arrays.equals(original,FaceMarks.marks(mc.thePlayer.getLocationSkin())),"Cancel changed marks");
                mc.displayGuiScreen(new ModulesScreen(null));
            }else if(state==6){
                ModulesScreen menu=(ModulesScreen)mc.currentScreen;
                require(find(menu,"DONE")!=null,"Modules menu missing its Done button");
                boolean faceRig=UtilitySettings.faceRig;
                cardToggle(menu,0);
                require(UtilitySettings.faceRig!=faceRig,"Module card toggle did not apply");
                cardToggle(menu,0);
                require(UtilitySettings.faceRig==faceRig,"Module card toggle did not revert");
                boolean leftHand=UtilitySettings.leftHand;
                cardToggle(menu,4);
                require(UtilitySettings.leftHand!=leftHand,"Left Hand card toggle did not apply");
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.leftHand=leftHand;UtilitySettings.load();
                require(UtilitySettings.leftHand!=leftHand,"Left Hand preference did not persist");
                cardToggle(menu,4);
                require(UtilitySettings.leftHand==leftHand,"Left Hand card toggle did not restore");
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
                OtlModule height=OtlModule.ALL.stream().filter(m->m.id.equals("height_limit_overlay")).findFirst().get();
                require(!height.available()&&!height.enabled(),"Height module was not visibly locked");
                ReflectionHelper.setPrivateValue(ModulesScreen.class,menu,OtlModule.ALL.indexOf(height)/3,"scrollRow");
                menu.initGui();
                cardToggle(menu,OtlModule.ALL.indexOf(height));
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
                }else System.out.println("OTL_SMOKE: height-overlay runtime fixtures SKIPPED (module locked LOCKED)");
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
                card=(MapInfoScreen)mc.currentScreen;cardDir=selected.substring(0,selected.length()-8).replace("CommunityMaps/master/","");
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
                searchCard=(MapInfoScreen)mc.currentScreen;cardWait=System.currentTimeMillis();
            }else if(state==10){
                // Cards read their map.xml off the worker thread, so the info lands a tick or two later.
                if(cardInfo(card)==null||cardInfo(searchCard)==null){
                    require(System.currentTimeMillis()-cardWait<15000,"Gallery card never loaded its map");
                    return;
                }
                require(cardDir.equals(cardInfo(card).dir),"Gallery opened a different map");
                require("Allure".equals(cardInfo(searchCard).name),"Gallery search/click failed");
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
                if(new otl.client.modules.ServerNoticesModule().available()){
                    require(find(mc.currentScreen,"Server Notices: OFF")!=null,"Server notices module missing/default not OFF");
                    ((ServerNoticeSettings)mc.currentScreen).actionPerformed(find(mc.currentScreen,"Server Notices: OFF"));
                    require(UtilitySettings.nonPlayerChat,"Server notices toggle did not enable");
                }else{
                    GuiButton locked=find(mc.currentScreen,"Server Notices: LOCKED");
                    require(locked!=null&&!locked.enabled,"Server notices not visibly locked");
                    ((ServerNoticeSettings)mc.currentScreen).actionPerformed(locked);
                    require(!UtilitySettings.nonPlayerChat,"Locked Server Notices toggle did not stay disabled");
                }
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
                ModulesScreen menu=new ModulesScreen(null);mc.displayGuiScreen(menu);
                require(OtlModule.byId("kill_feed")!=null&&OtlModule.byId("kill_feed").available(),"Kill Feed missing from the module browser");
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
                require(UtilitySettings.killFeedSize==75&&UtilitySettings.killBackgroundOpacity==25&&UtilitySettings.killBorderOpacity==100&&UtilitySettings.killBorderColor==0xFFBD45&&UtilitySettings.deathBorderColor==0xFF5555&&UtilitySettings.assistBorderColor==0x55FFFF,String.format("Appearance resets failed: size=%d bg=%d outline=%d colors=%06X/%06X/%06X",UtilitySettings.killFeedSize,UtilitySettings.killBackgroundOpacity,UtilitySettings.killBorderOpacity,UtilitySettings.killBorderColor,UtilitySettings.deathBorderColor,UtilitySettings.assistBorderColor));
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
                String home=UtilitySettings.profile;UtilitySettings.minimapBorder=true;UtilitySettings.teamBarTeamColor=false;
                String created=Profiles.newProfile();
                require(created!=null&&created.equals(UtilitySettings.profile),"New profile was not activated");
                require(UtilitySettings.minimapBorder,"New profile did not start from defaults");
                require(UtilitySettings.teamBarTeamColor,"A new profile did not get the Team Bar colour line default");
                String json=Profiles.profileExport(created);
                require(json!=null&&json.contains("\"profile\""),"Profile export was empty");
                String renamed=Profiles.renameProfile(created,"Shared test");
                require("Shared test".equals(renamed)&&"Shared test".equals(UtilitySettings.profile),"Profile rename did not stick");
                String second=Profiles.newProfile();
                String numbered=Profiles.renameProfile(second,"Shared test");
                require(numbered.startsWith("Shared test ")&&!numbered.equals("Shared test"),"Duplicate profile name was not numbered");
                Profiles.switchProfile(home);require(home.equals(UtilitySettings.profile),"Profile switch did not return");
                require(!UtilitySettings.teamBarTeamColor,"Switching back did not restore the profile's Team Bar colour line");
                UtilitySettings.teamBarTeamColor=true;
                UtilitySettings.minimapBorder=true;UtilitySettings.save();
                require(Profiles.deleteProfile(renamed)&&Profiles.deleteProfile(numbered),"Could not delete temporary profiles");
                String imported=Profiles.importProfile(json);
                require(imported!=null&&imported.equals(UtilitySettings.profile),"Profile import did not activate a new profile");
                Profiles.switchProfile(home);require(Profiles.deleteProfile(imported),"Could not delete an imported profile");
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
                require(UtilitySettings.chatScrollbar,"The chat scrollbar defaults to on");
                // Clicking the track grabs the thumb there; there are no arrow buttons any more.
                chat.mouseClicked(bar.x+4,bar.top+1,0);require(ReliableChat.currentView().scroll()>0,"Clicking the scrollbar track did not scroll");
                bar=chat.scrollbar();chat.mouseClicked(bar.x+4,bar.thumbTop+1,0);chat.mouseClickMove(bar.x+4,bar.trackTop,0,100);chat.mouseReleased(bar.x+4,bar.trackTop,0);
                require(ReliableChat.currentView().scroll()==bar.max,"Scrollbar drag did not reach oldest messages");
                // Closing the chat drops the scroll position, so reopening never starts mid-history.
                mc.displayGuiScreen(null);
                require(ReliableChat.currentView().scroll()==0,"Closing the chat left the view scrolled up");
                mc.displayGuiScreen(new ReliableChatScreen(""));
                require(ReliableChat.currentView().scroll()==0,"Reopening the chat did not show the newest line");
                // Turning the scrollbar off hides it and makes its strip click-through.
                UtilitySettings.chatScrollbar=false;
                ReliableChat.currentView().scrollTo(0);
                chat.mouseClicked(bar.x+4,bar.top+1,0);
                require(ReliableChat.currentView().scroll()==0,"A hidden scrollbar still scrolled the chat");
                UtilitySettings.chatScrollbar=true;
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
                UtilityScreen appearance=new UtilityScreen();mc.displayGuiScreen(appearance);appearance.actionPerformed(find(appearance,"Appearance"));
                net.minecraftforge.fml.client.config.GuiSlider tabs=(net.minecraftforge.fml.client.config.GuiSlider)buttons(appearance).stream().filter(b->b.id==16).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider typingTabs=(net.minecraftforge.fml.client.config.GuiSlider)buttons(appearance).stream().filter(b->b.id==17).findFirst().get();
                tabs.setValue(35);tabs.updateSlider();typingTabs.setValue(70);typingTabs.updateSlider();appearance.onGuiClosed();
                UtilitySettings.tabBackgroundOpacity=100;UtilitySettings.typingTabBackgroundOpacity=100;ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();
                require(UtilitySettings.tabBackgroundOpacity==35&&UtilitySettings.typingTabBackgroundOpacity==70,"Typing/idle tab opacity did not persist independently");
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
            if(state==64){
                mc.currentScreen.drawScreen(0,0,0);
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-modules-updated.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.displayGuiScreen(new BowOptimizerSettings(null));
            }else if(state==65){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-switches-updated.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                BowOptimizerSettings options=(BowOptimizerSettings)mc.currentScreen;
                boolean before=UtilitySettings.bowHitSound;
                options.actionPerformed(find(options,"Bow Hit Sound: "+(before?"ON":"OFF")));
                require(UtilitySettings.bowHitSound!=before,"Switch row did not toggle");
                options.actionPerformed(find(options,"Bow Hit Sound: "+(!before?"ON":"OFF")));
                require(UtilitySettings.bowHitSound==before,"Switch row did not restore");
                options.actionPerformed(find(options,"Bow Accuracy"));
            }else if(state==66){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-accuracy-updated.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.displayGuiScreen(new UtilityScreen());
            }else if(state==67){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-switches-updated.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                require(find(mc.currentScreen,"Reliable Chat: "+(UtilitySettings.chat?"ON":"OFF"))!=null,"Chat switch missing");
                mc.displayGuiScreen(new ModulesScreen(null));
            }else if(state==68){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-modules-updated.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                ModulesScreen browser=(ModulesScreen)mc.currentScreen;
                ProfileTransferScreen exporter=new ProfileTransferScreen(browser,UtilitySettings.profile);
                mc.displayGuiScreen(exporter);
                require(find(exporter,"Copy to Clipboard")!=null,"Export action missing");
                exporter.drawScreen(0,0,0);
                ProfileTransferScreen importer=new ProfileTransferScreen(browser,null);
                mc.displayGuiScreen(importer);
                require(find(importer,"Paste from Clipboard")!=null,"Import action missing");
                importer.drawScreen(0,0,0);
                mc.displayGuiScreen(browser);
                require(buttons(browser).stream().noneMatch(b->b.displayString.equals("EDIT HUD LAYOUT")),"Old HUD footer still present");
                // The module grid scrolls past two rows: the scrollbar must actually move it.
                require(browser.rowCount()>2,"The module grid has nothing to scroll");
                int tx=browser.trackLeft();
                browser.mouseClicked(tx,browser.trackBottomY()-2,0);
                require(browser.firstRow()==browser.rowCount()-2,"Clicking the module scrollbar track did not reach the last row");
                browser.mouseReleased(tx,browser.trackBottomY()-2,0);
                browser.mouseClicked(tx,browser.trackTopY()+2,0);
                require(browser.firstRow()==0,"Clicking the module scrollbar top did not return to the first row");
                browser.mouseReleased(tx,browser.trackTopY()+2,0);
                // The wheel over the grid must move the same list the scrollbar shows.
                int gridX=browser.trackLeft()-120;
                for(int i=0;i<3;i++)browser.scrollWheel(gridX,browser.trackTopY()+40,-120);
                require(browser.firstRow()==3,"The mouse wheel did not scroll the module grid: "+browser.firstRow());
                for(int i=0;i<9;i++)browser.scrollWheel(gridX,browser.trackTopY()+40,120);
                require(browser.firstRow()==0,"The wheel did not scroll the module grid back to the top");
                // A wheel over the profile rail must leave the grid alone.
                browser.scrollWheel(gridX,browser.trackTopY()+40,-120);
                int gridRow=browser.firstRow();
                browser.scrollWheel(browser.trackLeft()-420,browser.trackTopY()-10,-120);
                require(browser.firstRow()==gridRow,"A wheel over the profile rail moved the module grid");
                browser.scrollWheel(gridX,browser.trackTopY()+40,-120);
                browser.drawScreen(0,0,0);
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-modules-scrolled.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int bx=ReflectionHelper.getPrivateValue(ModulesScreen.class,browser,"x"),by=ReflectionHelper.getPrivateValue(ModulesScreen.class,browser,"y");
                browser.mouseClicked(bx+10,by+10,0);
                require(mc.currentScreen instanceof HudEditor,"Top-left back arrow did not open HUD editor");
                SettingsScreen[] screens={new KillFeedSettings(null),new KillConfirmSettings(null),new MinimapSettings(null),new HeightLimitSettings(null),new ServerNoticeSettings(null),new TeamBarSettings(null),new UtilityScreen(),new MapPreviewSettings(null),new PreferedHandSettings(null),new BowOptimizerSettings(null),new RegionSettings(null),new ColorSwatches(null,0),new AutoGgSettings(null)};
                int scrollable=0;
                for(SettingsScreen screen:screens){
                    screen.setWorldAndResolution(mc,427,240);
                    require(screen.panelW()==411&&screen.panelH()==224,"Settings panel is not the compact 470x272 frame");
                    require(screen.panelX()==8&&screen.panelY()==8,"Settings panel is not centred");
                    // The scrollbar strip must be draggable, not just painted.
                    if(screen.scrollMaxValue()>0){
                        scrollable++;
                        int sx=screen.panelX()+screen.panelW()-8,top=screen.contentTop(),bottom=screen.contentBottom();
                        screen.mouseClicked(sx,bottom-2,0);
                        require(screen.scrollValue()==screen.scrollMaxValue(),"Clicking the settings scrollbar did not reach the bottom");
                        screen.mouseClickMove(sx,top,0,100);
                        screen.mouseReleased(sx,top,0);
                        require(screen.scrollValue()==0,"Dragging the settings scrollbar did not return to the top");
                    }
                    final int[] picked={-1};
                    ChoiceButton choice=new ChoiceButton(screen,999,10,10,120,"Test",new String[]{"All","None","Only me"},0,v->picked[0]=v);
                    choice.mousePressed(mc,15,25);require(picked[0]==-1,"Selected choice changed value");
                    choice.mousePressed(mc,65,25);require(picked[0]==1,"Choice did not select clicked value");
                    screen.drawScreen(0,0,0);
                    List<GuiButton> controls=buttons(screen);
                    int bandTop=screen.contentTop(),bandBottom=screen.contentBottom();
                    for(int i=0;i<controls.size();i++)for(int j=i+1;j<controls.size();j++){
                        GuiButton a=controls.get(i),b=controls.get(j);
                        // Only the visible part of a scrolled row can collide with anything.
                        int aTop=Math.max(a.yPosition,bandTop),aBottom=Math.min(a.yPosition+a.height,bandBottom);
                        int bTop=Math.max(b.yPosition,bandTop),bBottom=Math.min(b.yPosition+b.height,bandBottom);
                        if(aBottom<=aTop||bBottom<=bTop)continue;
                        require(!(a.xPosition<b.xPosition+b.width&&a.xPosition+a.width>b.xPosition&&aTop<bBottom&&aBottom>bTop),screen.getClass().getSimpleName()+" overlapping controls "+a.id+"/"+b.id);
                    }
                    for(GuiButton b:controls)require(b.xPosition>=screen.panelX()&&b.xPosition+b.width<=screen.panelX()+screen.panelW(),screen.getClass().getSimpleName()+" control outside the panel");
                    // Scroll clamps to the page bottom, then re-clamps when a taller window fits more rows.
                    ReflectionHelper.setPrivateValue(SettingsScreen.class,screen,1000,"scroll");
                    screen.initGui();
                    int content=ReflectionHelper.getPrivateValue(SettingsScreen.class,screen,"contentHeight");
                    int scroll=ReflectionHelper.getPrivateValue(SettingsScreen.class,screen,"scroll");
                    require(scroll==Math.max(0,content-(screen.contentBottom()-screen.contentTop())),"Scrolling must clamp to the page bottom");
                    screen.setWorldAndResolution(mc,427,480);
                    int tallContent=ReflectionHelper.getPrivateValue(SettingsScreen.class,screen,"contentHeight");
                    int tallScroll=ReflectionHelper.getPrivateValue(SettingsScreen.class,screen,"scroll");
                    require(tallScroll==Math.max(0,tallContent-(screen.contentBottom()-screen.contentTop())),"Resizing must clear excess scroll");
                }
                require(scrollable>0,"No settings screen scrolls, so the scrollbar test proved nothing");
                // Region text: the editor element exists and its toggle flips the same flag the HUD reads.
                HudLayout.Rect regionBox=HudEditor.bounds(HudLayout.REGION);
                require(regionBox.w>0&&regionBox.h==12,"Region text has no HUD editor box");
                require(HudEditor.bounds(HudLayout.REGION).x==Regions.bounds().x,"Editor region box differs from gameplay");
                boolean wasRegion=UtilitySettings.regionHud;
                java.lang.reflect.Method toggle=HudEditor.class.getDeclaredMethod("toggle",String.class);
                toggle.setAccessible(true);
                toggle.invoke(new HudEditor(),HudLayout.REGION);
                require(UtilitySettings.regionHud!=wasRegion,"Region text toggle in the HUD editor did nothing");
                UtilitySettings.regionHud=wasRegion;
                RegionSettings regionOptions=new RegionSettings(null);regionOptions.setWorldAndResolution(mc,427,240);
                regionOptions.actionPerformed(find(regionOptions,"Region text: "+(wasRegion?"ON":"OFF")));
                require(UtilitySettings.regionHud!=wasRegion,"Region text toggle in the options screen did nothing");
                UtilitySettings.regionHud=wasRegion;
                mc.displayGuiScreen(new ModulesScreen(null));
            }else if(state==69){
                mc.displayGuiScreen(null);
                line("\u00a7f---- \u00a76Match Stats\u00a7f ----");
                line("Kills: 4 by \u00a7dMiyanosama\u00a7r, \u00a7bGrimpzy");
                line("Deaths: 8 by \u00a7cavonpale");
                line("Assists: 1 by Grimpzy");
                line("Damage: 26.26\u2764 by Grimpzy");
                line("Longest Shot: 53 blocks by ran_parr");
                line("Supervotes: 2");
                line("Match link: https://oc.tc/matches/6eb82d");
                // Legacy PGM path: the vote list is chat, never a book.
                line("\u00a75Click on all of the maps you want to play:");
                String[] maps={"Home by the Sea","Avalon","Deciduous Nights","Cherry City","Hallows"};
                for(int i=0;i<maps.length;i++)voteLine(i==1?"\u2714":"\u2715",maps[i],"Classic Casual");
            }else if(state==70){
                require(mc.currentScreen instanceof VoteScreen,"Chat vote list did not open the custom votebook");
                VoteScreen vote=(VoteScreen)mc.currentScreen;
                require(vote.cardCount()==5,"Chat vote cards were not parsed: "+vote.cardCount());
                require(vote.votedCount()==1,"Chat vote symbol was not read");
                line("\u00a77[\u00a7c\u2715 \u00a7e1\u00a77] \u00a7bHome by the Sea\u00a77 by \u00a73sanschaap");
                require(mc.currentScreen instanceof VoteScreen,"Vote screen closed on a standings update");
                require(Integer.valueOf(1).equals(VoteBook.standings.get(MapInfo.norm("Home by the Sea"))),"Standings count did not reach the card");
                require("https://oc.tc/matches/6eb82d".equals(vote.matchLink()),"Match link was not read from chat");
                require(VoteBook.superVotes==2,"Supervote count was not read from chat: "+VoteBook.superVotes);
                VoteBook.Stat kills=VoteBook.matchStats.get("Kills");
                require(kills!=null&&kills.names.size()==2,"Tied kills were not both read: "+(kills==null?null:kills.names));
                require(kills.styled.get(0).contains("\u00a7d"),"Stat name colours were dropped: "+kills.styled);
                VoteBook.Stat shot=VoteBook.matchStats.get("Longest shot");
                require(shot!=null&&"53".equals(shot.value)&&"ran_parr".equals(shot.names.get(0)),"Longest shot units leaked into the value: "+shot.value);
                require(vote.superEnabled(),"Supervote stayed locked with a vote cast and 2 supervotes");
                // The local player's own row marks them on a separate last line, not in the name.
                String me=mc.thePlayer.getName();
                line("Deaths: 8 by \u00a76\u00a7l[MOD]\u00a7r \u00a7c"+me+", \u00a7cavonpale");
                VoteBook.Stat deaths=VoteBook.matchStats.get("Deaths");
                require(deaths!=null&&VoteBook.you(deaths.names.get(VoteBook.pick(deaths.names,me)),me),"A rank-prefixed local name was not marked as (You)");
                require(!deaths.names.get(VoteBook.pick(deaths.names,me)).toLowerCase(java.util.Locale.ROOT).contains("(you)"),
                    "PGM's own (You) tag was left inside the stat name: "+deaths.names);
                // A name PGM already tags with "(You)" must still be read as the local player,
                // and that tag must not be left inside the drawn name.
                line("Kills: 9 by \u00a7c"+me+" \u00a77(You)");
                VoteBook.Stat tagged=VoteBook.matchStats.get("Kills");
                require(tagged!=null&&VoteBook.you(tagged.names.get(0),me),"PGM's (You) tag broke the local-player match: "+tagged.names);
                require(!tagged.styled.get(0).toLowerCase(java.util.Locale.ROOT).contains("(you)"),
                    "PGM's own (You) tag was left in the styled name: "+tagged.styled);
                require(VoteBook.pick(deaths.names,me)==deaths.names.size()-1,"The local player's own row was not preferred in the tie");
                // No Killstreak in the block: that column is dropped, the other five stay.
                require(vote.columns()==5,"Missing categories were not dropped: "+vote.columns());
                // PGM can carry your value inside the tag: "(You: 12)". The whole tag must leave
                // the name, and your value is drawn on its own last line instead.
                line("Deaths: 5 by \u00a7czKiller \u00a77(You: 12)");
                VoteBook.Stat taggedValue=VoteBook.matchStats.get("Deaths");
                require(taggedValue!=null&&"zKiller".equals(taggedValue.names.get(0)),
                    "A (You: N) tag was left inside the stat name: "+(taggedValue==null?null:taggedValue.names));
                require("12".equals(taggedValue.you),"The (You: N) value was not read: "+(taggedValue==null?null:taggedValue.you));
                require("".equals(VoteBook.youTag("\u00a77Alice (You)")),"A plain (You) tag was not read as empty");
                require(VoteBook.youTag("xX(Youtube)Xx")==null,"A name containing (You...) was read as the tag");
                require("xX(Youtube)Xx".equals(VoteBook.withoutYouTag("xX(Youtube)Xx")),"A name containing (You...) was stripped");
                // A name can change colour part-way (rank tag, then the name). Every code has to
                // survive, or the whole name is drawn in the first colour.
                line("Assists: 3 by \u00a76\u00a7l[MOD]\u00a7r \u00a7cTwoTone");
                VoteBook.Stat twoTone=VoteBook.matchStats.get("Assists");
                require(twoTone!=null&&twoTone.styled.get(0).contains("\u00a76")&&twoTone.styled.get(0).contains("\u00a7c"),
                    "A mid-name colour change was dropped: "+(twoTone==null?null:twoTone.styled));
                // Back to the rank-prefixed tie the render assertions below expect.
                line("Deaths: 8 by \u00a76\u00a7l[MOD]\u00a7r \u00a7c"+me+", \u00a7cavonpale");
                // Every GUI scale: the vote screen must lay itself out without rows colliding.
                for(int[] size:new int[][]{{427,240},{512,288},{640,360},{854,480},{960,540},{1280,720},{1920,1080}}){
                    vote.setWorldAndResolution(mc,size[0],size[1]);
                    vote.drawScreen(-1,-1,0);
                    require(vote.layoutFits(),"Vote layout collides at "+size[0]+"x"+size[1]);
                }
                ScaledResolution real=new ScaledResolution(mc);
                vote.setWorldAndResolution(mc,real.getScaledWidth(),real.getScaledHeight());
                vote.drawScreen(-1,-1,0);
                require(vote.youSlot()>=0,"(You) was not drawn for the local player");
                require(vote.drawnName(vote.youSlot())!=null&&vote.drawnName(vote.youSlot()).contains("[MOD]"),
                    "The rank prefix was lost from the (You) column: "+vote.drawnName(vote.youSlot()));
                vote.mouseClicked(40,40,0);
                require(mc.currentScreen==vote,"Clicking a card closed the vote screen");
            }else if(state==71){
                // A real frame has rendered by now, so the screenshot shows the (You) line.
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-vote-screen.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                mc.displayGuiScreen(null);
                // Servers that do open a real book must still be replaced.
                ItemStack voteBook=new ItemStack(net.minecraft.init.Items.written_book);NBTTagCompound voteNbt=new NBTTagCompound();                voteNbt.setString("title","Map Vote");voteNbt.setString("author","PGM");
                NBTTagList pages=new NBTTagList();
                String[] maps={"Home by the Sea","Avalon","Deciduous Nights","Cherry City","Hallows"};
                StringBuilder page=new StringBuilder("{\"text\":\"\",\"extra\":[{\"text\":\"Click on all of the maps you want to play:\",\"color\":\"dark_purple\"},{\"text\":\"\\n\"}");
                for(int i=0;i<maps.length;i++){
                    page.append(",{\"text\":\"").append(i==1?"\u2714":"\u2715").append("\",\"color\":\"dark_green\","
                        +"\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/votenext -o ").append(maps[i]).append("\"},"
                        +"\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"Classic Casual\"}},"
                        +"\"extra\":[{\"text\":\" \"},{\"text\":\"").append(maps[i]).append("\",\"color\":\"gold\",\"bold\":true}]}");
                }
                page.append("]}");
                pages.appendTag(new NBTTagString(page.toString()));
                voteNbt.setTag("pages",pages);voteBook.setTagCompound(voteNbt);
                mc.thePlayer.inventory.setInventorySlotContents(mc.thePlayer.inventory.currentItem,voteBook);
                mc.displayGuiScreen(new GuiScreenBook(mc.thePlayer,voteBook,false));
                require(mc.currentScreen instanceof VoteScreen,"Custom votebook did not replace the vanilla book");
                require(((VoteScreen)mc.currentScreen).cardCount()==5,"Book vote cards were not parsed");
                require(((VoteScreen)mc.currentScreen).votedCount()==1,"Book vote symbol was not read");
                mc.displayGuiScreen(null);
            }else if(state==72){
                // In-game Reliable Chat cancels every server line; the vote list must still be read.
                String[] plain={"Home by the Sea","Avalon","Deciduous Nights","Cherry City","Hallows"};
                for(int i=0;i<plain.length;i++)cancelledLine((i==1?"\u2714":"\u2715")+" \u00a76\u00a7l"+plain[i]);
            }else if(state==73){
                require(mc.currentScreen instanceof VoteScreen,"Clickless vote list did not open the custom votebook");
                VoteScreen vote=(VoteScreen)mc.currentScreen;
                require(vote.cardCount()==5,"Clickless vote cards were not parsed: "+vote.cardCount());
                require(vote.votedCount()==1,"Clickless vote symbol was not read");
                vote.mouseClicked(40,40,0);
                require(mc.currentScreen==vote,"Clicking a clickless card closed the vote screen");
                // Rank names keep every chat colour when the row is cut to width.
                String rank="\u00a76\u00a7lMOD\u00a7r \u00a7b\u00a7lAl\u00a7cice";
                String cut=VoteScreen.clip(mc.fontRendererObj,rank,1000);
                require(cut.equals(rank),"Trimming a rank name lost its colours: "+cut);
                String narrow=VoteScreen.clip(mc.fontRendererObj,rank,20);
                require(narrow.startsWith("\u00a76\u00a7lMOD"),"A narrow rank name lost its prefix colours: "+narrow);
                require(mc.fontRendererObj.getStringWidth(ChatRules.plain(narrow))<=20,"A narrow rank name was not trimmed to width");
                require(VoteBook.you("Alice","alice"),"(You) did not match the local player ignoring case");
                require(!VoteBook.you("Alice",null),"(You) appeared with no local player");
            }else if(state==74){
                // A book opened from a slot the player is not holding must still be replaced.
                ItemStack offBook=new ItemStack(net.minecraft.init.Items.written_book);NBTTagCompound offNbt=new NBTTagCompound();
                offNbt.setString("title","Map Vote");offNbt.setString("author","PGM");
                NBTTagList offPages=new NBTTagList();
                StringBuilder offPage=new StringBuilder("{\"text\":\"\",\"extra\":[{\"text\":\"Click on all of the maps you want to play:\",\"color\":\"dark_purple\"},{\"text\":\"\\n\"}");
                String[] offMaps={"Home by the Sea","Avalon","Deciduous Nights","Cherry City","Hallows"};
                for(int i=0;i<offMaps.length;i++){
                    offPage.append(",{\"text\":\"").append(i==1?"\u2714":"\u2715").append("\",\"color\":\"dark_green\","
                        +"\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/votenext -o ").append(offMaps[i]).append("\"},"
                        +"\"extra\":[{\"text\":\" \"},{\"text\":\"").append(offMaps[i]).append("\",\"color\":\"gold\",\"bold\":true}]}");
                }
                offPage.append("]}");
                offPages.appendTag(new NBTTagString(offPage.toString()));
                offNbt.setTag("pages",offPages);offBook.setTagCompound(offNbt);
                int offSlot=(mc.thePlayer.inventory.currentItem+1)%9;
                mc.thePlayer.inventory.setInventorySlotContents(offSlot,offBook);
                mc.thePlayer.inventory.setInventorySlotContents(mc.thePlayer.inventory.currentItem,new ItemStack(net.minecraft.init.Items.stick));
                mc.displayGuiScreen(new GuiScreenBook(mc.thePlayer,offBook,false));
                require(mc.currentScreen instanceof VoteScreen,"Book opened from a non-held slot stayed vanilla");
                require(((VoteScreen)mc.currentScreen).cardCount()==5,"Non-held book vote cards were not parsed");
                mc.displayGuiScreen(null);
            }else if(state==75){
                // Auto GG: one gg per match, re-armed only by the next match starting.
                UtilitySettings.autoGg=true;
                AutoGg.INSTANCE.settle=0;
                // The delay is read from the option each time, and randomising stays inside it.
                UtilitySettings.autoGgDelay=0;UtilitySettings.autoGgRandom=false;
                require(AutoGg.delayNanos()==0,"A zero delay still waited");
                UtilitySettings.autoGgDelay=400;
                require(AutoGg.delayNanos()==400_000_000L,"The delay was not read in milliseconds");
                UtilitySettings.autoGgRandom=true;
                for(int i=0;i<50;i++){
                    long wait=AutoGg.delayNanos();
                    require(wait>=0&&wait<=400_000_000L,"A random delay ran past the slider: "+wait/1_000_000L+" ms");
                }
                UtilitySettings.autoGgRandom=false;
                // Real sending path: 400 ms must still arrive, but only after the wait.
                line("The match has started!");
            }else if(state==76){
                require(AutoGg.INSTANCE.sentCount==0,"Auto GG said gg before the match ended: "+AutoGg.INSTANCE.sentCount);
                line("Game over!");
            }else if(state==77){
                require(AutoGg.INSTANCE.sentCount==1,"Auto GG did not say gg on the first match end: "+AutoGg.INSTANCE.sentCount);
                line("Game over!");
                // A long delay must hold the gg back, and the next match must cancel it.
                line("The match has started!");
                UtilitySettings.autoGgDelay=60_000;
                line("Game over!");
            }else if(state==78){
                require(AutoGg.INSTANCE.sentCount==1,"The repeat guard failed, or a 60 s delay was ignored: "+AutoGg.INSTANCE.sentCount);
                // Watching, with "Say gg in observer" off, keeps the client quiet.
                net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
                if(board.getTeam("observers")==null)board.createTeam("observers");
                board.addPlayerToTeam(mc.thePlayer.getName(),"observers");
                UtilitySettings.autoGgObserver=false;
                require(!AutoGg.allowed(mc),"Auto GG spoke while observing with the observer option off");
                UtilitySettings.autoGgObserver=true;
                require(AutoGg.allowed(mc),"Auto GG stayed silent while observing with the observer option on");
                board.removePlayerFromTeam(mc.thePlayer.getName(),board.getTeam("observers"));
                // The new match start already cancelled the pending 60 s gg.
                line("The match has started!");
                require(ReflectionHelper.getPrivateValue(AutoGg.class,AutoGg.INSTANCE,"sendAt").equals(0L),
                    "A new match did not cancel the pending gg");
                UtilitySettings.autoGg=false;
                UtilitySettings.autoGgDelay=0;UtilitySettings.autoGgRandom=false;
                AutoGg.INSTANCE.settle=5_000_000_000L;
            }else if(state==79){
                // Team Bar: own team, a much larger second team, a third team, and a six-player observer team.
                net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
                for(String team:new String[]{"red","blue","green","observers"})if(board.getTeam(team)==null)board.createTeam(team);
                // PGM colours each team, which is what the team-colour line reads.
                board.getTeam("red").setNamePrefix("\u00a7c");
                board.getTeam("blue").setNamePrefix("\u00a79");
                board.getTeam("green").setNamePrefix("\u00a7a");
                for(int i=0;i<5;i++)board.addPlayerToTeam("RedPlayer"+i,"red");
                for(int i=0;i<75;i++)board.addPlayerToTeam("BluePlayer"+i,"blue");
                for(int i=0;i<2;i++)board.addPlayerToTeam("GreenPlayer"+i,"green");
                for(int i=0;i<6;i++)board.addPlayerToTeam("ObsPlayer"+i,"observers");
                // Sorts before the local player's name, so the "own head first" rule is really tested.
                board.addPlayerToTeam("AAAPlayer","red");
                tabEntry(mc,"AAAPlayer","\u00a7cAAAPlayer");
                if(mc.thePlayer!=null)board.addPlayerToTeam(mc.thePlayer.getName(),"red");
                // GhostLeaver disconnects: the tab entry goes away and the head must follow, after the grace.
                board.addPlayerToTeam("GhostLeaver","red");
                tabEntry(mc,"GhostLeaver","\u00a7cGhostLeaver");
                TeamBar.sides(mc); // the bar sees the leaver online once, so the grace starts here
                removeTabEntry(mc,"GhostLeaver");
                net.minecraft.scoreboard.ScoreObjective objective=board.getObjective("otl_teambar");
                if(objective==null)objective=board.addScoreObjective("otl_teambar",net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
                board.setObjectiveInDisplaySlot(1,objective);
                sidebar(board,objective,"Time Limit: 10:00");
                // RedPlayer0 is dead: PGM greys their tab-list name until they respawn.
                for(int i=0;i<5;i++)tabEntry(mc,"RedPlayer"+i,i==0?"\u00a77RedPlayer0":"\u00a7cRedPlayer"+i);
                UtilitySettings.teamBarPlayers=5;
            }else if(state==80){
                java.util.List<TeamBar.Side> sides=TeamBar.sides(mc);
                require(sides.size()==3,"Team Bar did not find all three teams: "+sides.size());
                // Fixed order by team name, so a team's column can never change during a match.
                require("blue".equals(sides.get(0).name)&&"green".equals(sides.get(1).name)&&"red".equals(sides.get(2).name),
                    "Teams were not in a fixed name order: "+sides.get(0).name+"/"+sides.get(1).name+"/"+sides.get(2).name);
                require(sides.get(0).members.size()==75&&sides.get(2).members.size()==8,"Team Bar kept the wrong teams");
                require(sides.get(2).members.contains("GhostLeaver"),"A leaver was dropped before the grace ran out");
                require(sides.get(2).members.get(0).equals(mc.thePlayer.getName()),"The local player's own head was not first in its own team: "+sides.get(2).members);
                for(TeamBar.Side side:sides)require(!"observers".equals(side.name),"The observer team became a side");
                require(TeamBar.dead("RedPlayer0"),"A grey tab-list name was not read as dead");
                require(!TeamBar.dead("RedPlayer1"),"A team-coloured tab-list name was read as dead");
                require(!TeamBar.dead("NotInTheTabList"),"A player outside the tab list was read as dead");
                TeamBar.Timer limit=TeamBar.INSTANCE.timer(mc);
                require(limit!=null&&limit.seconds>=597&&limit.seconds<=600,"Team Bar did not read the sidebar timer: "+(limit==null?"none":limit.label));
                require(limit.countdown,"Time-limit timer was not marked as a countdown");
                require(TeamBar.headSize(1)==16&&TeamBar.headSize(75)==16,"Heads shrank instead of wrapping to another row");
                // Pin the size: a previous smoke run can leave a different value in settings.json.
                UtilitySettings.teamBarSize=100;
                TeamBar.Bar bar=TeamBar.INSTANCE.bar(mc,false);
                require(bar.left.size()==2&&bar.right.size()==1,"Three teams were not stacked one block each: "+bar.left.size()+"/"+bar.right.size());
                require(bar.left.get(0).size()==5&&bar.right.get(0).size()==2,"Players shown was not applied: "+bar.left.get(0).size()+"/"+bar.right.get(0).size());
                require(bar.left.get(1).size()==5,"The third team was not drawn as its own block");
                // Columns follow the fixed name order, not the local player or team sizes.
                require(bar.left.get(1).contains(mc.thePlayer.getName()),"The local team's block is not in the column the fixed order gives it");
                require(bar.left.get(1).get(0).equals(mc.thePlayer.getName()),"The local head was not at the inner end of a left block: "+bar.left.get(1));
                // The clock must not move when the two columns hold different amounts.
                require(bar.textX+bar.textW/2==bar.width/2,"The clock is not centred between the team columns");
                // And it must land on the screen centre. The overlay used to place the bar from the
                // preview width, so a clock-only bar sat well left of centre.
                int screenW=new ScaledResolution(mc).getScaledWidth();
                HudLayout.Rect placed=TeamBar.placed(bar);
                require(Math.abs(placed.x+bar.textX+bar.textW/2-screenW/2)<=1,
                    "The clock is not on the screen centre: "+(placed.x+bar.textX+bar.textW/2)+" vs "+screenW/2);
                // Rows are flush against the clock and grow outward, so no gap ever sits next to it.
                java.util.List<String> shortRow=java.util.Arrays.asList("A","B"),longRow=java.util.Arrays.asList("A","B","C","D","E");
                TeamBar.Bar mirror=new TeamBar.Bar(java.util.Collections.singletonList(shortRow),
                    java.util.Arrays.asList(longRow,shortRow),16,0,null,"");
                require(mirror.rightStart(longRow)==mirror.rightX&&mirror.rightStart(shortRow)==mirror.rightX,"A right row was not flush with the clock");
                require(mirror.leftStart(longRow)==mirror.column-((longRow.size()-1)*mirror.advance+16),"A left row was not flush with the clock");
                require(mirror.leftStart(shortRow)+((shortRow.size()-1)*mirror.advance+16)==mirror.column,"A short left row left a gap next to the clock");
                // Stacked teams must be visibly apart, not butted into one block of heads.
                require(mirror.height==2*16+TeamBar.ROW_GAP,"Stacked team rows were not separated: "+mirror.height);
                // Team-colour line: at each row's own outer end, clock still centred.
                java.util.List<java.util.List<String>> oneRow=java.util.Collections.singletonList(longRow);
                TeamBar.Bar lined=new TeamBar.Bar(oneRow,oneRow,16,24,null,"",7);
                require(lined.leftLine(longRow)==0,"The left team line did not sit on the row's outer end");
                require(lined.rightLine(longRow)+(7-2)==lined.width,"The right team line did not sit on the row's outer end");
                require(lined.textX+lined.textW/2==lined.width/2,"The team line moved the clock off centre");
                boolean lineSetting=UtilitySettings.teamBarTeamColor;
                UtilitySettings.teamBarTeamColor=true;
                int withLine=TeamBar.INSTANCE.bar(mc,false).width;
                UtilitySettings.teamBarTeamColor=false;
                int withoutLine=TeamBar.INSTANCE.bar(mc,false).width;
                require(withLine>withoutLine,"Turning the team colour line on did not make room for it: "+withLine+" vs "+withoutLine);
                require(TeamBar.teamColour(mc,sides.get(2).members)==0xFFFF5555,"The team colour line did not read the team's colour: "+Integer.toHexString(TeamBar.teamColour(mc,sides.get(2).members)));
                require(TeamBar.teamColour(mc,sides.get(0).members)==0xFF5555FF,"The second team got the wrong colour");
                UtilitySettings.teamBarTeamColor=lineSetting;
                require(bar.head==16,"Five heads per side were not kept at full size");
                // The Size option is the only thing that may change head size for a row of ten
                // or fewer: the count itself must never shrink them.
                int wasSize=UtilitySettings.teamBarSize;
                UtilitySettings.teamBarSize=50;
                require(TeamBar.INSTANCE.bar(mc,false).head==8,"Size 50% did not halve the heads: "+TeamBar.INSTANCE.bar(mc,false).head);
                UtilitySettings.teamBarSize=150;
                require(TeamBar.INSTANCE.bar(mc,false).head==24,"Size 150% did not enlarge the heads: "+TeamBar.INSTANCE.bar(mc,false).head);
                UtilitySettings.teamBarSize=wasSize;
                // The Size slider itself must reach the setting and the drawn heads, not just
                // repaint its own label: that is what "the size slider does nothing" looks like.
                TeamBarSettings options=new TeamBarSettings(null);
                options.setWorldAndResolution(mc,854,480);
                GuiButton sizeRow=find(options,"Size: "+UtilitySettings.teamBarSize+"%");
                sizeRow.mousePressed(mc,sizeRow.xPosition+4,sizeRow.yPosition+10);
                options.mouseReleased(sizeRow.xPosition+4,sizeRow.yPosition+10,0);
                require(UtilitySettings.teamBarSize==50,"The Size slider did not reach the setting: "+UtilitySettings.teamBarSize);
                require(TeamBar.INSTANCE.bar(mc,false).head==8,"The Size slider did not reach the heads: "+TeamBar.INSTANCE.bar(mc,false).head);
                UtilitySettings.teamBarSize=wasSize;
                // A row of ten at a larger Size must really be bigger, not squeezed back by the
                // fit-to-screen pass: that pass turned "150%" into heads smaller than 100%.
                UtilitySettings.teamBarPlayers=10;UtilitySettings.teamBarSize=150;
                TeamBar.Bar big=TeamBar.INSTANCE.bar(mc,false);
                require(big.head==24,"A ten-head row was shrunk instead of wrapping: "+big.head);
                require(big.width<=new ScaledResolution(mc).getScaledWidth()-8,"The bigger bar spills off the screen");
                require(big.left.get(0).size()<=TeamBar.MAX_PER_ROW,"A row held more than ten heads");
                UtilitySettings.teamBarPlayers=5;UtilitySettings.teamBarSize=wasSize;
                UtilitySettings.teamBarPlayers=75;
                TeamBar.Bar wide=TeamBar.INSTANCE.bar(mc,false);
                // Ten heads fill a row; a bigger team wraps onto the rows below at full size.
                require(wide.left.get(0).size()==TeamBar.MAX_PER_ROW,"A full row did not hold ten heads: "+wide.left.get(0).size());
                require(wide.left.size()>=8,"Seventy-five players did not wrap onto rows below: "+wide.left.size());
                require(wide.head==16,"Wrapped rows shrank the heads instead of using another row");
                require(wide.textX+wide.textW/2==wide.width/2,"The clock moved when the rows grew");
                require(wide.width<=new ScaledResolution(mc).getScaledWidth()-8,"The full bar spills off the screen");
                UtilitySettings.teamBarPlayers=5;
                HudLayout.Rect box=HudEditor.bounds(HudLayout.TEAM_BAR);
                require(box.y<=8&&box.x>=4&&box.x+box.w<=new ScaledResolution(mc).getScaledWidth()-4,"Team Bar HUD bounds are off screen");
                require(Math.abs(box.x+box.w/2-new ScaledResolution(mc).getScaledWidth()/2)<=2,"Team Bar does not default to top centre");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-team-bar.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                // Many teams keep stacking downward; a stack taller than the screen shrinks to fit.
                net.minecraft.scoreboard.Scoreboard many=mc.theWorld.getScoreboard();
                for(int i=0;i<24;i++){
                    String extra="extra"+i;
                    if(many.getTeam(extra)==null)many.createTeam(extra);
                    many.addPlayerToTeam("Extra"+i+"P0",extra);
                    many.addPlayerToTeam("Extra"+i+"P1",extra);
                }
                TeamBar.Bar stacked=TeamBar.INSTANCE.bar(mc,false);
                int rows=Math.max(stacked.left.size(),stacked.right.size());
                require(rows==14,"Twenty-seven teams were not stacked one row each: "+rows);
                require(stacked.height<=new ScaledResolution(mc).getScaledHeight()-8,"The team stack spills off the screen: "+stacked.height);
                for(int i=0;i<24;i++)if(many.getTeam("extra"+i)!=null)many.removeTeam(many.getTeam("extra"+i));
                // Respawn: the server puts the team colour back, so the head must clear itself.
                tabEntry(mc,"RedPlayer0","\u00a7cRedPlayer0");
                // A death that drops the player from the team must not make the head vanish.
                mc.theWorld.getScoreboard().removePlayerFromTeam("AAAPlayer",mc.theWorld.getScoreboard().getTeam("red"));
                require(side(TeamBar.sides(mc),"red").members.contains("AAAPlayer"),"A dead player's head vanished from the bar");
                mc.theWorld.getScoreboard().addPlayerToTeam("AAAPlayer","red");
                // Changing team moves the head; it must not be left behind on the old row.
                mc.theWorld.getScoreboard().addPlayerToTeam("AAAPlayer","blue");
                require(!side(TeamBar.sides(mc),"red").members.contains("AAAPlayer"),"A player who changed team stayed on the old row");
                // Going to the observers removes the head from the teams entirely.
                mc.theWorld.getScoreboard().addPlayerToTeam("AAAPlayer","observers");
                for(TeamBar.Side side:TeamBar.sides(mc))require(!side.members.contains("AAAPlayer"),"An observer was left on a team row");
                // Same bar during a count-up match: no limit label, so the timer stays white.
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Time: 0:42");
                // Ten a row at 150%: the heads must be bigger, not shrunk back to fit the width.
                UtilitySettings.teamBarPlayers=10;UtilitySettings.teamBarSize=150;
            }else if(state==81){
                // The frame drawn during the wait above used the 150% bar; keep it as evidence.
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-team-bar-large.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                UtilitySettings.teamBarPlayers=5;UtilitySettings.teamBarSize=100;
                require(!TeamBar.dead("RedPlayer0"),"A respawned player stayed greyed out");
                TeamBar.Timer up=TeamBar.INSTANCE.timer(mc);
                require(up!=null&&"0:42".equals(up.label)&&!up.countdown,"Count-up timer was not left white: "+(up==null?"none":up.label));
                // Local clock: read the limit once, then tick from it without any server update.
                line("The match has started!");
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Time Remaining: 10:00");
                net.minecraft.entity.boss.BossStatus.bossName=null;
                long now=System.nanoTime();
                TeamBar.Timer start=TeamBar.INSTANCE.timer(mc,now);
                require(start!=null&&"10:00".equals(start.label)&&start.countdown,"Time limit was not read");
                TeamBar.Timer later=TeamBar.INSTANCE.timer(mc,now+2_000_000_000L);
                require("9:58".equals(later.label),"The clock did not tick locally: "+later.label);
                line("Game over!");
                TeamBar.Timer frozen=TeamBar.INSTANCE.timer(mc,now+2_000_000_000L);
                require("9:58".equals(frozen.label),"The clock stopped somewhere else: "+frozen.label);
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Time Remaining: 9:50");
                TeamBar.Timer stillFrozen=TeamBar.INSTANCE.timer(mc,now+9_000_000_000L);
                require("9:58".equals(stillFrozen.label),"A server update moved the stopped clock: "+stillFrozen.label);
                // The next map's count-in must not overwrite the final time; only the map change does.
                net.minecraft.entity.boss.BossStatus.bossName="Match starting in 10 seconds";
                TeamBar.Timer heldThroughCountIn=TeamBar.INSTANCE.timer(mc,now+9_500_000_000L);
                require("9:58".equals(heldThroughCountIn.label)&&!heldThroughCountIn.starting,"The count-in overwrote the ended match's final time: "+heldThroughCountIn.label);
                net.minecraft.entity.boss.BossStatus.bossName=null;
                line("The match has started!");
                TeamBar.Timer resumed=TeamBar.INSTANCE.timer(mc,now+10_000_000_000L);
                require(resumed!=null&&"9:50".equals(resumed.label),"The clock did not resume for the next match: "+(resumed==null?"none":resumed.label));
                // A new map: the old clock must not linger; the middle resets to 0:00 and waits.
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Players: 24");
                net.minecraft.entity.boss.BossStatus.bossName=null;
                line("Playing Allure");
                TeamBar.Timer changed=TeamBar.INSTANCE.timer(mc,now+11_000_000_000L);
                require(changed!=null&&"0:00".equals(changed.label),"The clock did not reset to 0:00 on a map change: "+(changed==null?"none":changed.label));
                require(changed.starting,"The reset clock was not treated as pre-match");
                // Counting in: the clock shows alone, no heads yet, so nothing suggests teams are set.
                TeamBar.Bar countingIn=TeamBar.INSTANCE.bar(mc,false);
                require(countingIn!=null&&countingIn.left.isEmpty()&&countingIn.right.isEmpty(),"Heads were drawn while the new map loaded");
                // Then PGM counts the next match in from there.
                net.minecraft.entity.boss.BossStatus.bossName="Match starting in 10 seconds";
                TeamBar.Timer next=TeamBar.INSTANCE.timer(mc,now+12_000_000_000L);
                require(next!=null&&next.starting&&"0:10".equals(next.label),"The new map's countdown did not take over: "+(next==null?"none":next.label));
                // Two sources that disagree must not fight over the display: the first one that
                // reported a clock keeps it, so the number cannot jump between them.
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Time Remaining: 9:20");
                TeamBar.Timer locked=TeamBar.INSTANCE.timer(mc,now+12_000_000_000L);
                require(locked!=null&&"0:10".equals(locked.label),"A second clock source overwrote the locked one: "+(locked==null?"none":locked.label));
                // When the locked source disappears, the next one carries on instead of stopping.
                net.minecraft.entity.boss.BossStatus.bossName=null;
                TeamBar.Timer handedOver=TeamBar.INSTANCE.timer(mc,now+12_000_000_000L);
                require(handedOver!=null&&"9:20".equals(handedOver.label),"The clock did not hand over to the next source: "+(handedOver==null?"none":handedOver.label));
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Players: 24");
                // Clock colours: red count-in, yellow limit, red in the last 25 seconds, white count-up.
                require(TeamBar.colour(next)==TeamBar.RED,"The count-in was not red");
                require(TeamBar.colour(new TeamBar.Timer("5:00",300,true,false))==TeamBar.YELLOW,"A limit with time left was not yellow");
                require(TeamBar.colour(new TeamBar.Timer("0:26",26,true,false))==TeamBar.YELLOW,"A limit just above 25 seconds turned red too early");
                require(TeamBar.colour(new TeamBar.Timer("0:25",25,true,false))==TeamBar.RED,"25 seconds left was not red");
                require(TeamBar.colour(new TeamBar.Timer("0:00",0,true,false))==TeamBar.RED,"The last second was not red");
                require(TeamBar.colour(new TeamBar.Timer("0:25",25,false,false))==TeamBar.WHITE,"A count-up was coloured like a time limit");
            }else if(state==82){
                net.minecraft.entity.boss.BossStatus.bossName="Time Remaining: 9:50";
                UtilitySettings.teamBarHideBoss=true;
                require(TeamBar.hidesBossBar(mc),"Hide bossbar did not hide a PGM clock");
                UtilitySettings.teamBarHideBoss=false;
                require(!TeamBar.hidesBossBar(mc),"Hide bossbar stayed on after being turned off");
                // A line that never moves is not the clock. PGM's sidebar carries the map's
                // configured limit ("Time Limit: 10:00") while the live time is elsewhere; locking
                // onto whichever appeared first pinned the bar to the static one.
                net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
                net.minecraft.scoreboard.ScoreObjective sidebar=board.getObjective("otl_teambar");
                if(sidebar==null)sidebar=board.addScoreObjective("otl_teambar",net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
                board.setObjectiveInDisplaySlot(1,sidebar);
                line("Playing Allure");
                sidebar(board,sidebar,"Time Limit: 10:00");
                net.minecraft.entity.boss.BossStatus.bossName=null;
                long now=System.nanoTime();
                TeamBar.Timer first=TeamBar.INSTANCE.timer(mc,now);
                require(first!=null&&"10:00".equals(first.label),"The only clock line was not shown: "+(first==null?"none":first.label));
                // The static limit never ticks, so a live clock takes the bar off it.
                tabFooter(mc,"Time: 4:32");
                TeamBar.INSTANCE.timer(mc,now+500_000_000L);
                tabFooter(mc,"Time: 4:31");
                TeamBar.Timer live=TeamBar.INSTANCE.timer(mc,now+1_500_000_000L);
                require(live!=null&&"4:31".equals(live.label),"The ticking clock did not take over from the static limit: "+(live==null?"none":live.label));
                require(live.countdown,"A live clock was not read as a countdown: "+live.label);
                // Once on the live clock, the static line must not win it back: the bar keeps
                // ticking locally from the last live value instead of jumping to 10:00.
                TeamBar.Timer stayed=TeamBar.INSTANCE.timer(mc,now+TeamBar.STALE+2_000_000_000L);
                require(stayed!=null&&stayed.label.startsWith("4:")&&stayed.countdown,
                    "The static limit took the clock back: "+(stayed==null?"none":stayed.label));
                tabFooter(mc,"");
                sidebar(board,sidebar,"Players: 24");
                OtlModule module=OtlModule.byId("team_bar");
                require(module!=null&&module.enabled(),"Team Bar module is missing from the module list");
                require(module.settings(new HudEditor()) instanceof TeamBarSettings,"Team Bar has no options page");
                module.setEnabled(false);
                require(!UtilitySettings.teamBar,"Team Bar module toggle did not reach the HUD flag");
                module.setEnabled(true);
                UtilitySettings.teamBarPlayers=5;UtilitySettings.teamBarSize=100;UtilitySettings.teamBarHideBoss=false;
                // A time-limit map as Overcast really sends it: the kickoff line starts a local
                // count-up, then the clock arrives as the tab footer's bare "Time: 10:00" with no
                // direction word. Guessing "up" from that made a countdown run up from its limit.
                line("Playing Allure");
                line("The match has started!");
                tabFooter(mc,"Time: 10:00");
                TeamBar.Timer joined=TeamBar.INSTANCE.timer(mc,now);
                require(joined!=null&&"10:00".equals(joined.label),"The time limit was not shown on join: "+(joined==null?"none":joined.label));
                require(!joined.countdown,"A clock with no direction yet was drawn as a countdown: "+joined.label);
                tabFooter(mc,"Time: 9:59");
                TeamBar.Timer counting=TeamBar.INSTANCE.timer(mc,now+1_000_000_000L);
                require(counting!=null&&counting.countdown&&"9:59".equals(counting.label),
                    "A time limit counted up from its own limit: "+(counting==null?"none":counting.label));
                tabFooter(mc,"");
                // Its source going away (PGM swapping the boss bar's text mid-match) must not stop
                // the clock: it keeps counting from the last value with no server line at all.
                TeamBar.Timer alone=TeamBar.INSTANCE.timer(mc,now+3_000_000_000L);
                require(alone!=null&&"9:57".equals(alone.label)&&alone.countdown,
                    "The clock stopped when its source went away: "+(alone==null?"none":alone.label));
                // And a line that never moves must not be picked up when it does: the bar would
                // jump back to the map's limit.
                sidebar(board,sidebar,"Time Limit: 10:00");
                TeamBar.Timer held=TeamBar.INSTANCE.timer(mc,now+4_000_000_000L);
                require(held!=null&&"9:56".equals(held.label),"The bar jumped to the static limit: "+(held==null?"none":held.label));
                sidebar(board,sidebar,"Players: 24");
            }else if(state==83){
                // A leaver: seen online once, then the tab entry goes away. The head must follow
                // after the short grace instead of sitting on the row forever.
                mc.theWorld.getScoreboard().addPlayerToTeam("GhostLeaver","red");
                tabEntry(mc,"GhostLeaver","\u00a7cGhostLeaver");
                TeamBar.sides(mc); // the bar sees the leaver online, so the grace starts here
                removeTabEntry(mc,"GhostLeaver");
                require(side(TeamBar.sides(mc),"red").members.contains("GhostLeaver"),"A leaver was dropped before the grace ran out");
                for(TeamBar.Side side:TeamBar.sides(mc,System.nanoTime()+TeamBar.LEFT_GRACE+1_000_000_000L))
                    require(!side.members.contains("GhostLeaver"),"A player who left the server was drawn forever");
                // Between matches: PGM writes a bare "0:00" into an idle scoreboard, which is not a clock.
                TeamBar.INSTANCE.unload(new net.minecraftforge.event.world.WorldEvent.Unload(mc.theWorld));
                net.minecraft.entity.boss.BossStatus.bossName="0:00";
                require(TeamBar.INSTANCE.timer(mc)==null,"An idle 0:00 was read as a match clock");
                // The hub has scoreboard teams too, so no match clock means no heads at all.
                require(TeamBar.INSTANCE.bar(mc,false)==null,"Team Bar drew heads with no match running");
                require(TeamBar.INSTANCE.bar(mc,true)!=null,"The HUD editor lost its preview without a match");
                // Then PGM loads the next map; the count-in lands in the middle in red.
                line("Game over!");
                net.minecraft.scoreboard.ScoreObjective board=mc.theWorld.getScoreboard().getObjective("otl_teambar");
                sidebar(mc.theWorld.getScoreboard(),board,"Players: 24"); // no clock in the sidebar here
                line("Playing Allure"); // the map change is what clears the ended match
                net.minecraft.entity.boss.BossStatus.bossName="Match starting in 10 seconds";
                long now=System.nanoTime();
                TeamBar.Timer starting=TeamBar.INSTANCE.timer(mc,now);
                require(starting!=null&&starting.starting,"The match-start countdown was not recognised: "+(starting==null?"none":starting.label));
                require("0:10".equals(starting.label),"The countdown did not start at ten: "+starting.label);
                require(starting.countdown,"The countdown was not marked as a countdown");
                require(TeamBar.colour(starting)==TeamBar.RED,"The match-start countdown was not red");
                // The count-in shows the clock alone: heads would suggest the teams are set.
                TeamBar.Bar countingBar=TeamBar.INSTANCE.bar(mc,false);
                require(countingBar!=null&&countingBar.left.isEmpty()&&countingBar.right.isEmpty(),"Heads were drawn during the pre-match countdown");
                require(TeamBar.INSTANCE.bar(mc,true)!=null,"The HUD editor lost its preview during a countdown");
                require("0:07".equals(TeamBar.INSTANCE.timer(mc,now+3_000_000_000L).label),"The countdown did not tick locally");
                // At zero the match clock takes over: a map with no limit counts up from 0:00 in white.
                TeamBar.Timer running=TeamBar.INSTANCE.timer(mc,now+11_000_000_000L);
                require(!running.starting&&!running.countdown,"The countdown did not hand over to a match clock");
                require("0:01".equals(running.label),"A no-limit map did not count up from zero: "+running.label);
                require("0:03".equals(TeamBar.INSTANCE.timer(mc,now+13_000_000_000L).label),"The count-up did not keep time");
                // Dead heads: the texels lose their colour instead of just getting a wash.
                int grey=TeamBar.greyed(0xFFC04020);
                require(((grey>>16)&255)==((grey>>8)&255)&&((grey>>8)&255)==(grey&255),"Dead heads are not grey");
                require(((grey>>16)&255)<0xC0,"Dead heads were not dimmed");
                require(TeamBar.grey("\u00a77\u00a7lRedPlayer"),"A decorated grey tab name was not read as dead");
                require(!TeamBar.grey("\u00a7c\u00a7lRedPlayer"),"A team-coloured decorated name was read as dead");
            }else if(state==84){
                // Match start with no server clock at all: count up from zero in white.
                net.minecraft.entity.boss.BossStatus.bossName=null;
                line("The match has started!");
                long began=System.nanoTime();
                require("0:00".equals(TeamBar.INSTANCE.timer(mc,began).label),"The new match did not start at zero");
                TeamBar.Timer up=TeamBar.INSTANCE.timer(mc,began+2_000_000_000L);
                require("0:02".equals(up.label)&&!up.countdown&&!up.starting,"A no-limit match clock is not counting up white: "+up.label);
                // A time limit replaces the count-up as soon as PGM sends it.
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Time Remaining: 10:00");
                TeamBar.Timer limited=TeamBar.INSTANCE.timer(mc,began+3_000_000_000L);
                require("10:00".equals(limited.label)&&limited.countdown,"The time limit did not take over: "+limited.label);
                sidebar(mc.theWorld.getScoreboard(),mc.theWorld.getScoreboard().getObjective("otl_teambar"),"Players: 24");
                net.minecraft.entity.boss.BossStatus.bossName=null;
            }else if(state==85){
                // Observer: hidden only when the new option says so.
                UtilitySettings.teamBarObserver=true;
                mc.theWorld.getScoreboard().addPlayerToTeam(mc.thePlayer.getName(),"observers");
                require(TeamBar.observing(mc),"Being on the observer team was not detected");
                require(TeamBar.INSTANCE.bar(mc,false)!=null,"The bar was hidden in observer with the option ON");
                UtilitySettings.teamBarObserver=false;
                require(TeamBar.INSTANCE.bar(mc,false)==null,"The bar stayed visible in observer with the option OFF");
                require(TeamBar.INSTANCE.bar(mc,true)!=null,"The HUD editor preview followed the observer option");
                // The server's own timer goes with the bar: with the bar hidden in observer, the
                // boss-bar clock must not be left on screen on its own.
                net.minecraft.entity.boss.BossStatus.bossName="Time Remaining: 4:31";
                UtilitySettings.teamBarHideBoss=false;
                require(TeamBar.hidesBossBar(mc),"The server timer stayed on screen while the bar was hidden in observer");
                UtilitySettings.teamBarObserver=true;
                require(!TeamBar.hidesBossBar(mc),"The server timer was hidden while the bar was shown in observer");
                net.minecraft.entity.boss.BossStatus.bossName=null;
                UtilitySettings.teamBarObserver=true;
                mc.theWorld.getScoreboard().addPlayerToTeam(mc.thePlayer.getName(),"red");
                require(!TeamBar.observing(mc),"A playing team was read as observers");
            }else if(state==86){
                // Respawn: a live body in the world clears the fade even if the tab name is still grey.
                String me=mc.thePlayer.getName();
                require(mc.getNetHandler().getPlayerInfo(me)!=null,"The local player is missing from the tab list");
                // Your own head follows the same two signals as everyone else's: the local body
                // when it knows, and the server's grey tab name when PGM's dead state leaves the
                // body looking alive.
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiGameOver());
                require(TeamBar.dead(me),"The local player's own head did not grey out on death");
                mc.displayGuiScreen(null);
                require(!TeamBar.dead(me),"The local player's head stayed grey after respawn");
                net.minecraft.client.network.NetworkPlayerInfo self=mc.getNetHandler().getPlayerInfo(me);
                String before=TeamBar.tabName(self);
                self.setDisplayName(new ChatComponentText("\u00a77"+me));
                require(TeamBar.dead(me),"The server's grey tab name did not grey the local player's head");
                self.setDisplayName(before==null?null:new ChatComponentText(before));
                require(!TeamBar.dead(me),"The local player's head stayed grey after the tab name cleared");
                // A respawning player is already back in the world, but PGM keeps the grey tab name
                // until the respawn finishes: the head must stay grey with the name, not clear early.
                net.minecraft.client.entity.EntityOtherPlayerMP respawning=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"RespawningMate"));
                mc.theWorld.addEntityToWorld(-9103,respawning);
                tabEntry(mc,"RespawningMate","\u00a77RespawningMate");
                require(TeamBar.dead("RespawningMate"),"A live body cleared the head while the tab name was still grey");
                removeTabEntry(mc,"RespawningMate");
                require(!TeamBar.dead("RespawningMate"),"A live body was read as dead without a tab entry");
                mc.theWorld.removeEntityFromWorld(-9103);
            }else if(state==87){
                // Auto GG: never say gg while the map is starting.
                ggBaseline=AutoGg.INSTANCE.sentCount;
                UtilitySettings.autoGg=true;
                line("Playing Allure");
                line("Your team won!"); // PGM's leftover result right after the map loads
            }else if(state==88){
                require(AutoGg.INSTANCE.sentCount==ggBaseline,"Auto GG said gg while the map was starting");
                // Same match, well past the map-start window: this one is a real ending.
                ReflectionHelper.setPrivateValue(AutoGg.class,AutoGg.INSTANCE,System.nanoTime()-10_000_000_000L,"startedAt");
                line("Your team won!");
            }else if(state==89){
                require(AutoGg.INSTANCE.sentCount==ggBaseline+1,"Auto GG did not say gg at the real match end: "+AutoGg.INSTANCE.sentCount);
                UtilitySettings.autoGg=false;
                // Watching from the observer team, with nobody on a real team yet: the clock must
                // still be drawn, alone, and still on the screen centre.
                net.minecraft.scoreboard.Scoreboard board=mc.theWorld.getScoreboard();
                for(String team:new String[]{"red","blue","green"})if(board.getTeam(team)!=null)board.removeTeam(board.getTeam(team));
                if(board.getTeam("observers")==null)board.createTeam("observers");
                board.addPlayerToTeam(mc.thePlayer.getName(),"observers");
                // A real match clock, so the bar is past the count-in branch and really has no teams.
                line("Playing Allure");
                net.minecraft.scoreboard.ScoreObjective clock=board.getObjective("otl_teambar");
                if(clock==null)clock=board.addScoreObjective("otl_teambar",net.minecraft.scoreboard.IScoreObjectiveCriteria.DUMMY);
                board.setObjectiveInDisplaySlot(1,clock);
                sidebar(board,clock,"Time Limit: 10:00");
                TeamBar.Timer running=TeamBar.INSTANCE.timer(mc);
                require(running!=null&&!running.starting,"The bar is still counting in: "+(running==null?"none":running.label));
                TeamBar.Bar alone=TeamBar.INSTANCE.bar(mc,false);
                require(alone!=null,"The clock vanished while observing with no teams");
                require(alone.left.isEmpty()&&alone.right.isEmpty(),"Heads were drawn with no teams: "+alone.left+"/"+alone.right);
                int sw=new ScaledResolution(mc).getScaledWidth();
                HudLayout.Rect where=TeamBar.placed(alone);
                require(Math.abs(where.x+alone.textX+alone.textW/2-sw/2)<=1,
                    "The clock is off centre while observing: "+(where.x+alone.textX+alone.textW/2)+" vs "+sw/2);
                // FFA: PGM gives every player their own one-member team so each name gets its own
                // colour. That is not a team: the bar must pool the heads and draw no colour line.
                net.minecraft.scoreboard.ScorePlayerTeam watching=board.getPlayersTeam(mc.thePlayer.getName());
                if(watching!=null)board.removePlayerFromTeam(mc.thePlayer.getName(),watching);
                int shownBefore=UtilitySettings.teamBarPlayers;
                UtilitySettings.teamBarPlayers=12;
                for(int i=0;i<12;i++){
                    String team="ffa"+i;
                    if(board.getTeam(team)==null)board.createTeam(team);
                    board.getTeam(team).setNamePrefix("\u00a7"+"0123456789abcdef".charAt(i));
                    board.addPlayerToTeam(i==0?mc.thePlayer.getName():"FfaPlayer"+i,team);
                }
                java.util.List<TeamBar.Side> ffaSides=TeamBar.sides(mc);
                require(TeamBar.isFfa(ffaSides),"FFA was not recognised from one-member teams: "+ffaSides.size());
                TeamBar.Bar ffa=TeamBar.INSTANCE.bar(mc,false);
                require(ffa!=null,"The FFA bar vanished");
                require(ffa.line==0,"FFA still drew a team-colour line: "+ffa.line);
                int heads=0;
                for(java.util.List<String> members:ffa.left)heads+=members.size();
                for(java.util.List<String> members:ffa.right)heads+=members.size();
                require(heads==12,"FFA did not pool every player: "+heads+" in "+ffa.left+"/"+ffa.right);
                require(!ffa.left.isEmpty()&&ffa.left.get(0).get(0).equals(mc.thePlayer.getName()),
                    "The local head did not lead the FFA pool: "+ffa.left);
                require(Math.abs(ffa.left.size()-ffa.right.size())<=1,
                    "FFA rows are lopsided: "+ffa.left.size()+" vs "+ffa.right.size());
                require(ffa.textX+ffa.textW/2==ffa.width/2,"The clock is not centred in the FFA bar");
                UtilitySettings.teamBarPlayers=shownBefore;
            }else if(state==90){
                // Real PGM match on the local server: dump what the scoreboard actually carries.
                mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);
                mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(new GuiMainMenu(),mc,
                    new net.minecraft.client.multiplayer.ServerData("Local PGM","localhost:25565",false)));
            }else if(state==91){
                require(ServerScope.active(),"Local server scope is inactive");
                require(mc.thePlayer!=null&&mc.theWorld!=null,"Not connected to the local server");
                // Join the running match so the bar has real teams and a real clock.
                mc.thePlayer.sendChatMessage("/join");
            }else if(state>=92&&state<=98){
                dumpMatch(mc);
                if(state==95)ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-team-bar-live.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            }else if(state==99){
                // Whatever the real boss bar says, the bar must read the same clock out of it.
                String boss=net.minecraft.entity.boss.BossStatus.bossName;
                String plain=ChatRules.plain(boss==null?"":boss);
                TeamBar.Timer timer=TeamBar.INSTANCE.timer(mc);
                if(java.util.regex.Pattern.compile("\\d{1,3}:\\d{2}").matcher(plain).find())
                    require(timer!=null&&plain.contains(timer.label),"Team Bar missed the boss-bar clock: "+plain+" -> "+(timer==null?"none":timer.label));
                else require(timer==null,"Team Bar invented a timer: "+(timer==null?"none":timer.label));
                System.out.println("OTL_LIVE: boss-bar clock check PASS ("+plain.trim()+")");
                mc.thePlayer.sendChatMessage("/leave");
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
