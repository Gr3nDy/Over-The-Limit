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
    long serverStarted;
    ResourceLocation allureTexture;
    final List<String> packets=Collections.synchronizedList(new ArrayList<String>());
    @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void line(String text){MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,new ChatComponentText(text)));}
    List<GuiButton> buttons(GuiScreen screen){return ReflectionHelper.getPrivateValue(GuiScreen.class,screen,"buttonList","field_146292_n");}
    void click(int id){for(GuiButton b:buttons(face))if(b.id==id){face.actionPerformed(b);return;}throw new AssertionError("Missing button "+id);}
    GuiButton find(GuiScreen s,String label){for(GuiButton b:buttons(s))if(b.displayString.equals(label))return b;throw new AssertionError("Missing button "+label);}
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
            TeamBubbles.Bubble first=TeamBubbles.bubbles.get(mate.getUniqueID());
            require(first!=null&&first.text.equals("team hello"),"Team prefix did not create bubble without join/color information");
            line("(Any Team) BubbleMate: spam");require(TeamBubbles.bubbles.get(mate.getUniqueID())==first,"Spam replaced or extended the team bubble");
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
            System.out.println("OTL_SMOKE: explicit team-chat bubbles, own messages, no join/color filtering, hub exclusion and spam cooldown PASS");
        }finally{
            mc.theWorld.removeEntityFromWorld(-9101);mc.theWorld.removeEntityFromWorld(-9102);
            UtilitySettings.bubbles=enabled;mc.gameSettings.thirdPersonView=perspective;TeamBubbles.clearBubbles();
        }
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
        if(state==15&&System.currentTimeMillis()-serverStarted>90000){System.err.println("OTL_SMOKE: FAIL - localhost connection timed out");FMLCommonHandler.instance().exitJava(1,false);return;}
        if(mc.thePlayer==null||++ticks<120||ticks%25!=0)return;
        try{
            mc.gameSettings.pauseOnLostFocus=false;
            if(state==0){
                mc.setServerData(new net.minecraft.client.multiplayer.ServerData("OTL fixture","play.oc.tc",false));
                channel=mc.getNetHandler().getNetworkManager().channel();
                channel.pipeline().addLast("otl_packets",new io.netty.channel.ChannelOutboundHandlerAdapter(){public void write(io.netty.channel.ChannelHandlerContext ctx,Object p,io.netty.channel.ChannelPromise promise)throws Exception{if(p instanceof net.minecraft.network.play.client.C01PacketChatMessage)packets.add(((net.minecraft.network.play.client.C01PacketChatMessage)p).getMessage());super.write(ctx,p,promise);}});
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
                UtilitySettings.leftHand=false;menu.initGui();menu.actionPerformed(find(menu,"Prefered Hand: OFF"));
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
                System.out.println("OTL_SMOKE: PASS - "+(serverOnly?"targeted localhost":"fixtures")+(remoteEnabled?"; remote maps enabled":"; remote maps DISABLED")+(serverEnabled?"; command paths; bare /map; Public Unicode book hover":"; localhost checks DISABLED")+"; gold chat hover; Left Hand sword/empty, toggle persistence, scope and GL restoration");mc.shutdown();
            }
            state++;
        }catch(Throwable t){t.printStackTrace();System.out.println("OTL_SMOKE: FAIL - "+t);FMLCommonHandler.instance().exitJava(1,false);}
    }
}
