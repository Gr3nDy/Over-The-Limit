package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
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
    boolean started;int ticks,state;FaceScreen face;io.netty.channel.Channel channel;
    final List<String> packets=Collections.synchronizedList(new ArrayList<String>());
    @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void line(String text){MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatReceivedEvent((byte)1,new ChatComponentText(text)));}
    List<GuiButton> buttons(GuiScreen screen){return ReflectionHelper.getPrivateValue(GuiScreen.class,screen,"buttonList","field_146292_n");}
    void click(int id){for(GuiButton b:buttons(face))if(b.id==id){face.actionPerformed(b);return;}throw new AssertionError("Missing button "+id);}
    int count(String command){int count=0;synchronized(packets){for(String s:packets)if(s.equals(command))count++;}return count;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        if(!started&&mc.currentScreen instanceof GuiMainMenu){started=true;mc.launchIntegratedServer("FacePortSmoke","Face Port Smoke",new WorldSettings(12345,WorldSettings.GameType.CREATIVE,true,false,WorldType.FLAT));return;}
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
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.WorldEvent.Load(mc.theWorld));line("Created by MapMaker");require(ReliableChat.fullChat()&&ReliableChat.session().selected.equals("team"),"Created by detection");
            }else if(state==2){
                require(count("/t")==1,"Initial /t count");ReliableChatScreen ui=(ReliableChatScreen)mc.currentScreen;ui.updateScreen();ui.sendChatMessage("team message",true);
                ReliableChat.session().select("all");ui.updateScreen();ui.sendChatMessage("global message",true);
                ReliableChat.session().select("party");ui.updateScreen();ui.sendChatMessage("party message",true);
                ui.cycleNext();ui.drawScreen(0,0,0);ui.mouseClicked(10,ui.height-27,0);
                ui.sendChatMessage("/msg Alice hello",true);line("To Alice: hello");ui.sendChatMessage("/r reply",true);line("To Bob: reply");line("From Bob: incoming");
                line("\u00a79\u00a7oto your monument!");require(!ReliableChat.session().views.containsKey("pm:your"),"False PM tab");require(ReliableChat.session().views.containsKey("pm:bob"),"Missing /r PM");
                MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.WorldEvent.Load(mc.theWorld));require(ReliableChat.fullChat(),"World change hid tabs");line("Created by OtherMaker");
            }else if(state==3){
                require(count("/t")==1&&count("/g")==0&&count("/party chat")==0,"Tab/map sent bare command: "+packets);
                require(packets.containsAll(Arrays.asList("/t team message","/g global message","/party chat party message","/msg Alice hello","/r reply")),"Missing routed sends: "+packets);
                ReliableChat.session().clear("party");ReliableChat.session().clear("all");ReliableChat.session().select("team");ReliableChat.noteSend(null);
                line("\u00a7cYou are not in a party.");
                List<ChatLine> partyHistory=ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.session().views.get("party").hud,"chatLines","field_146252_h");
                List<ChatLine> allHistory=ReflectionHelper.getPrivateValue(GuiNewChat.class,ReliableChat.session().views.get("all").hud,"chatLines","field_146252_h");
                require(partyHistory.size()==1&&allHistory.size()==1&&ReliableChat.session().selected.equals("team"),"Party error missing, duplicated, or stole focus without recent send");
                line("Alex: You are not in a party.");require(partyHistory.size()==1,"Player quote copied as party warning");
                line("Overcast Community\nview our /servers or click the beacon");require(!ReliableChat.fullChat()&&ReliableChat.tabKeys().size()==1,"Return to lobby");line("Welcome to Overcast Community!");
                FaceConfig.reset();UtilitySettings.faceRig=true;UtilitySettings.otherFaces=true;UtilitySettings.fullscreen=false;mc.displayGuiScreen(face=new FaceScreen());
                require(buttons(face).stream().noneMatch(b->b.displayString.contains("Advanced")||b.displayString.contains("Expression")),"Obsolete controls");
            }else if(state==4){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-face-rig.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                click(11);require(buttons(face).stream().anyMatch(b->b.displayString.equals("Save")),"Editor save missing");
            }else if(state==5){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-editor.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                int[] original=FaceMarks.marks(mc.thePlayer.getLocationSkin());original=original==null?null:original.clone();
                int x=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gx"),y=ReflectionHelper.getPrivateValue(FaceScreen.class,face,"gy");face.panelMouseClicked(x+2,y+2,0);click(27);
                require(Arrays.equals(original,FaceMarks.marks(mc.thePlayer.getLocationSkin())),"Cancel changed marks");
                mc.displayGuiScreen(new UtilityScreen());
            }else if(state==6){
                ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-menu.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                require(count("/t")==2,"Game re-entry /t missing");require(org.lwjgl.opengl.GL11.glGetError()==0,"GL error");
                channel.pipeline().remove("otl_packets");System.out.println("OTL_SMOKE: PASS - lobby/direct/map transitions; packet routing; tabs send no commands; PM confirmations; phantom PM rejected; vanilla menus/editor/cancel rendered");mc.shutdown();
            }
            state++;
        }catch(Throwable t){t.printStackTrace();System.out.println("OTL_SMOKE: FAIL");mc.shutdown();}
    }
}
