package otl.client;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.resources.*;
import org.lwjgl.input.Keyboard;

@Mod(modid="otl",name="Over The Limit",version="0.3.0",clientSideOnly=true,acceptedMinecraftVersions="[1.8.9]",acceptableRemoteVersions="*")
public final class Otl {
    private final KeyBinding menu=new KeyBinding("key.otl.menu",Keyboard.KEY_J,"key.categories.otl");
    private final KeyBinding modules=new KeyBinding("key.otl.modules",Keyboard.KEY_P,"key.categories.otl");
    private final KeyBinding partyChat=new KeyBinding("key.otl.party_chat",Keyboard.KEY_NONE,"key.categories.otl");
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(menu);
        ClientRegistry.registerKeyBinding(modules);
        ClientRegistry.registerKeyBinding(partyChat);
        MinecraftForge.EVENT_BUS.register(ReliableChat.INSTANCE);
        FMLCommonHandler.instance().bus().register(ReliableChat.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TeamBubbles.INSTANCE);
        FMLCommonHandler.instance().bus().register(TeamBubbles.INSTANCE);
        FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);
        for(RenderPlayer renderer:Minecraft.getMinecraft().getRenderManager().getSkinMap().values())renderer.addLayer(new FaceOverlay(renderer));
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(resources->{SkinFace.clear();FaceMarks.clearCache();});
    }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event){
        if(Keyboard.getEventKeyState()&&Keyboard.getEventKey()==Keyboard.KEY_Y){ReliableChat.openChannel(ChatRules.TEAM);return;}
        if(partyChat.isPressed()){ReliableChat.openChannel(ChatRules.PARTY);return;}
        openMenu();
    }
    @SubscribeEvent public void mouse(InputEvent.MouseInputEvent event){openMenu();}
    private void openMenu(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer==null || mc.currentScreen!=null)return;
        if(modules.isPressed())mc.displayGuiScreen(new UtilityScreen());
        else if(menu.isPressed())mc.displayGuiScreen(new FaceScreen());
    }
    @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||mc.theWorld==null)return;
        for(net.minecraft.entity.player.EntityPlayer p:new java.util.ArrayList<net.minecraft.entity.player.EntityPlayer>(mc.theWorld.playerEntities))PlayerSignals.tick(p);
    }
    @SubscribeEvent public void worldUnload(WorldEvent.Unload event){
        if(event.world!=null&&event.world.isRemote)PlayerSignals.clear();
    }
}
