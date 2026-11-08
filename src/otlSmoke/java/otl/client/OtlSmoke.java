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
    int scopeEnd=53;
    long serverStarted;
    ResourceLocation allureTexture;
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
        Minecraft mc=Minecraft.getMinecraft();boolean enabled=UtilitySettings.bubbles;int perspective=mc.gameSettings.thirdPersonView;
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
            line("You joined Blue");require(TeamBubbles.bubbles.get(mate.getUniqueID())==first,"Join message changed team-chat bubbles");
            line("(Observers) BubbleOther: observer team hello");
            require(TeamBubbles.bubbles.containsKey(other.getUniqueID()),"Explicit Observers team chat was ignored");
            line("(Any Team) "+mc.thePlayer.getName()+": own team hello");
            require(TeamBubbles.bubbles.containsKey(mc.thePlayer.getUniqueID()),"Own server-echoed team chat did not create bubble");
            mc.gameSettings.thirdPersonView=1;require(TeamBubbles.visible(mc.thePlayer),"Own bubble is not visible in third person");
            mc.gameSettings.thirdPersonView=0;require(!TeamBubbles.visible(mc.thePlayer),"Own bubble obscures first person");
            line("Overcast Community\nview our /servers or click the beacon");
            TeamBubbles.INSTANCE.tick(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            line("(Any Team) BubbleMate: hub hello");require(TeamBubbles.bubbles.isEmpty(),"Hub displayed speech bubbles");
            line("Created by MapMaker");
            System.out.println("OTL_SMOKE: explicit team-chat bubbles, own messages, hub exclusion and animated three-message stacks PASS");
        }finally{
            mc.theWorld.removeEntityFromWorld(-9101);mc.theWorld.removeEntityFromWorld(-9102);
            UtilitySettings.bubbles=enabled;mc.gameSettings.thirdPersonView=perspective;TeamBubbles.clearBubbles();
        }
    }
    void sniffPackets(Minecraft mc){
        channel=mc.getNetHandler().getNetworkManager().channel();
        channel.pipeline().addLast("otl_packets",new io.netty.channel.ChannelOutboundHandlerAdapter(){public void write(io.netty.channel.ChannelHandlerContext ctx,Object p,io.netty.channel.ChannelPromise promise)throws Exception{if(p instanceof net.minecraft.network.play.client.C01PacketChatMessage)packets.add(((net.minecraft.network.play.client.C01PacketChatMessage)p).getMessage());super.write(ctx,p,promise);}});
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
            case "bubbles":state=31;scopeEnd=32;UtilitySettings.mainHandLeft=false;UtilitySettings.handType=LeftHand.Type.ALL;mc.displayGuiScreen(new PreferedHandSettings(new UtilityScreen()));return;
            case "killfeed":state=33;scopeEnd=42;ReliableChat.session();return;
            case "notices":state=43;scopeEnd=44;mc.displayGuiScreen(new HudEditor());ReliableChat.session();return;
            case "killconfirm":state=45;scopeEnd=50;UtilitySettings.killConfirm=false;mc.displayGuiScreen(new HudEditor());ReliableChat.session();return;
            case "tabs":state=51;scopeEnd=53;prepareTabs(mc);return;
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
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-menu.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            }else if(state==7){
                ItemStack book=new ItemStack(net.minecraft.init.Items.written_book);
                NBTTagCompound nbt=new NBTTagCompound();
                nbt.setString("title","Vote");nbt.setString("author","Server");
                NBTTagList pages=new NBTTagList();pages.appendTag(new NBTTagString("{\"text\":\"Allure\"}"));nbt.setTag("pages",pages);
                book.setTagCompound(nbt);
                bookScreen=new GuiScreenBook(mc.thePlayer,book,false);
                mc.displayGuiScreen(bookScreen);
            }else if(state==8){
                int left=(bookScreen.width-192)/2+36;
                bookScreen.drawScreen(left+2,36,0);
                MapPreview.Hover hit=MapPreview.INSTANCE.hover(bookScreen,left+2,36);
                require(hit!=null&&"Allure".equalsIgnoreCase(hit.label),"Map hover did not resolve the map name: "+hit);
                require(MapPreview.INSTANCE.hover(bookScreen,left+300,36)==null,"Map hover matched outside the map name");
                MapInfo cached=MapInfo.findLocal("Allure");
                require(cached!=null&&"Allure".equals(cached.name)&&cached.objective.contains("wool")&&cached.players==24,"Local map info: "+cached);
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
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-gallery.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                gallery.mouseClicked(grid.x(0)+5,grid.y(0)+5,0);
                require("Allure".equals(((MapInfo)ReflectionHelper.getPrivateValue(MapInfoScreen.class,(MapInfoScreen)mc.currentScreen,"info")).name),"Gallery search/click failed");
            }else if(state==10){
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
                options.actionPerformed(find(options,"Hide deaths in chat: OFF"));
                net.minecraftforge.client.event.ClientChatReceivedEvent kill=new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)0,new ChatComponentText("Victim was slain by "+mc.thePlayer.getName()+"'s Wooden Sword"));
                MinecraftForge.EVENT_BUS.post(kill);require(kill.isCanceled()&&KillFeed.history.entries.size()==1,"Hide chat did not preserve kill-feed entry");
                require(KillFeed.icon(KillFeed.history.entries.get(0)).getItem()==net.minecraft.init.Items.wooden_sword,"Wrong wooden sword icon");
                options.actionPerformed(find(options,"Hide deaths in chat: ON"));
                require(!UtilitySettings.killFeedSlide,"Slide animation should default OFF");options.actionPerformed(find(options,"Slide animation: OFF"));
                options.actionPerformed(find(options,"Appearance"));
                net.minecraftforge.fml.client.config.GuiSlider size=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==4).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider opacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==5).findFirst().get();
                size.setValue(125);size.updateSlider();opacity.setValue(25);opacity.updateSlider();
                net.minecraftforge.fml.client.config.GuiSlider outline=(net.minecraftforge.fml.client.config.GuiSlider)buttons(options).stream().filter(b->b.id==16).findFirst().get();outline.setValue(60);outline.updateSlider();
                options.actionPerformed(find(options,"Colors"));
                GuiTextField killColor=ReflectionHelper.getPrivateValue(KillFeedSettings.class,options,"killColor"),deathColor=ReflectionHelper.getPrivateValue(KillFeedSettings.class,options,"deathColor");
                killColor.setText("123456");deathColor.setText("654321");options.onGuiClosed();
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.killFeedSize=75;UtilitySettings.killBackgroundOpacity=100;UtilitySettings.killBorderOpacity=100;UtilitySettings.killFeedSlide=false;UtilitySettings.killBorderColor=0;UtilitySettings.deathBorderColor=0;UtilitySettings.load();
                require(UtilitySettings.killFeedSize==125&&UtilitySettings.killBackgroundOpacity==25&&UtilitySettings.killBorderOpacity==60&&UtilitySettings.killFeedSlide&&UtilitySettings.killBorderColor==0x123456&&UtilitySettings.deathBorderColor==0x654321,"Kill Feed appearance did not persist");
                killColor.setText("INVALID");options.onGuiClosed();require(UtilitySettings.killBorderColor==0x123456,"Invalid hex color replaced saved color");
                options.actionPerformed(find(options,"Reset colors"));options.actionPerformed(find(options,"Appearance"));
                options.actionPerformed(find(options,"Reset size"));options.actionPerformed(find(options,"Reset"));options.actionPerformed(find(options,"Reset line"));
                require(UtilitySettings.killFeedSize==75&&UtilitySettings.killBackgroundOpacity==100&&UtilitySettings.killBorderOpacity==100&&UtilitySettings.killBorderColor==0xFFBD45&&UtilitySettings.deathBorderColor==0xFF5555,"Appearance resets failed");
                options.actionPerformed(find(options,"General"));options.actionPerformed(find(options,"Slide animation: ON"));options.actionPerformed(find(options,"Colors"));
                options.actionPerformed(buttons(options).stream().filter(b->b.id==19).findFirst().get());require(mc.currentScreen instanceof ColorSwatches,"Color swatches did not open");
                ((ColorSwatches)mc.currentScreen).actionPerformed(buttons(mc.currentScreen).get(4));
                require(mc.currentScreen==options&&UtilitySettings.killBorderColor==ColorSwatches.COLORS[4],"Swatch selection did not apply/return");options.actionPerformed(find(options,"Reset colors"));
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
                require(buttons(editor).size()==1,"Removed alignment toolbar is still present");
                UtilitySettings.hudPositions.put(HudLayout.KILLS,new HudLayout.Position(1,0));
                HudLayout.Rect r=HudEditor.bounds(HudLayout.KILLS);int px=r.x+r.w-25,py=r.y+2;
                editor.mouseClicked(px,py,0);editor.mouseClickMove(px-40,py+35,0,100);editor.mouseReleased(px-40,py+35,0);
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
                editor.actionPerformed(find(editor,"Mods"));require(mc.currentScreen instanceof UtilityScreen,"Mods button did not open modules");
                ((UtilityScreen)mc.currentScreen).actionPerformed(find(mc.currentScreen,"Done"));require(mc.currentScreen==editor,"Modules did not return to editor");
            }else if(state==38){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-editor.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
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
                options.actionPerformed(find(options,"Reset size"));require(UtilitySettings.noticeSize==100&&ServerNotices.bounds().w==normalNoticeWidth,"Notice size reset failed");
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
                net.minecraftforge.fml.client.config.GuiSlider confirmSize=(net.minecraftforge.fml.client.config.GuiSlider)buttons(confirmOptions).stream().filter(b->b.id==1).findFirst().get();
                net.minecraftforge.fml.client.config.GuiSlider confirmOpacity=(net.minecraftforge.fml.client.config.GuiSlider)buttons(confirmOptions).stream().filter(b->b.id==3).findFirst().get();
                confirmSize.setValue(125);confirmSize.updateSlider();confirmOpacity.setValue(40);confirmOpacity.updateSlider();confirmOptions.actionPerformed(find(confirmOptions,"Hide rewards in chat: OFF"));confirmOptions.actionPerformed(find(confirmOptions,"Kill/assist sounds: OFF"));confirmOptions.onGuiClosed();
                UtilitySettings.killConfirm=false;UtilitySettings.killConfirmSize=75;UtilitySettings.killConfirmOpacity=0;UtilitySettings.hideKillConfirmChat=false;UtilitySettings.killConfirmSounds=false;
                ReflectionHelper.setPrivateValue(UtilitySettings.class,null,false,"loaded");UtilitySettings.load();require(confirm.enabled()&&UtilitySettings.killConfirmSize==125&&UtilitySettings.killConfirmOpacity==40&&UtilitySettings.hideKillConfirmChat&&UtilitySettings.killConfirmSounds,"Kill Confirm preferences did not persist");
                confirmOptions.actionPerformed(find(confirmOptions,"Hide rewards in chat: ON"));require(!UtilitySettings.hideKillConfirmChat,"Reward chat hiding did not toggle OFF");
                confirmOptions.actionPerformed(find(confirmOptions,"Kill/assist sounds: ON"));require(!UtilitySettings.killConfirmSounds,"Kill/assist sounds did not toggle OFF");
                confirmOptions.actionPerformed(find(confirmOptions,"Reset size"));require(UtilitySettings.killConfirmSize==75,"Small default size did not reset");
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
                UtilitySettings.nonPlayerChat=true;line("+5 raindrops | Kill");require(KillConfirm.state.streak==3&&KillConfirm.state.streakExp==18&&!KillConfirmRules.streakLabel(KillConfirm.state.streak,false).isEmpty(),"Next kill did not restore streak text or stack EXP");
                require(KillConfirm.state.reward.labels(KillConfirm.state.streak).contains("3 KILL STREAK"),"Streak row missing from white stack");
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
                appearance.actionPerformed(find(appearance,"Reset chat options"));require(UtilitySettings.tabBackgroundOpacity==100&&UtilitySettings.typingTabBackgroundOpacity==100,"Tab background reset failed");
            }else if(state==48){
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
                require(rows.stream().anyMatch(r->r.text.equals("MULTI KILL")),"Fourth kill missing MULTI KILL");
                for(int i=1;i<rows.size();i++)require(rows.get(i).start>rows.get(i-1).start,"Burst rows jumped the queue");
                require(KillConfirmRules.newestRow(rows,now)<rows.size()-1,"Burst did not leave queued rows to reveal");
            }else if(state==49){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-burst-queued.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            }else if(state==50){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-kill-confirm-burst-fourth.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                KillConfirm.state.clear();UtilitySettings.killConfirm=false;UtilitySettings.killFeed=true;UtilitySettings.save();
                prepareTabs(mc);
            }else if(state==51){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-input-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());mc.displayGuiScreen(null);
            }else if(state==52){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-gameplay-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());mc.displayGuiScreen(new HudEditor());
            }else if(state==53){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-tab-background-preview-0.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());UtilitySettings.tabBackgroundOpacity=100;UtilitySettings.typingTabBackgroundOpacity=100;UtilitySettings.save();
                if(scope.equals("all"))System.out.println("OTL_SMOKE: PASS - Kill Confirm burst, native component compatibility, HUD and prior fixtures");
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
