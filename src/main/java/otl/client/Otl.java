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

@Mod(modid="otl",name="Over The Limit",version="0.3.9",clientSideOnly=true,acceptedMinecraftVersions="[1.8.9]",acceptableRemoteVersions="*")
public final class Otl {
    static final String VERSION="0.3.9";
    private final KeyBinding modules=new KeyBinding("key.otl.modules",Keyboard.KEY_P,"key.categories.otl");
    private final KeyBinding teamChat=new KeyBinding("key.otl.team_chat",Keyboard.KEY_Y,"key.categories.otl");
    private final KeyBinding partyChat=new KeyBinding("key.otl.party_chat",Keyboard.KEY_NONE,"key.categories.otl");
    private final KeyBinding minimap=new KeyBinding("key.otl.minimap",Keyboard.KEY_M,"key.categories.otl");
    private final KeyBinding minimapZoom=new KeyBinding("key.otl.minimapzoom",Keyboard.KEY_SEMICOLON,"key.categories.otl");
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(modules);
        ClientRegistry.registerKeyBinding(teamChat);
        ClientRegistry.registerKeyBinding(partyChat);
        ClientRegistry.registerKeyBinding(minimap);
        ClientRegistry.registerKeyBinding(minimapZoom);
        MinecraftForge.EVENT_BUS.register(ReliableChat.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ServerNotices.INSTANCE);
        MinecraftForge.EVENT_BUS.register(KillFeed.INSTANCE);
        MinecraftForge.EVENT_BUS.register(KillConfirm.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BowOptimizer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BowAccuracy.INSTANCE);
        FMLCommonHandler.instance().bus().register(BowAccuracy.INSTANCE);
        MinecraftForge.EVENT_BUS.register(Minimap.INSTANCE);
        FMLCommonHandler.instance().bus().register(Minimap.INSTANCE);
        // Height Limit Overlay is temporarily unavailable; keep all its runtime handlers detached.
        FMLCommonHandler.instance().bus().register(KillConfirm.INSTANCE);
        MinecraftForge.EVENT_BUS.register(MapPreview.INSTANCE);
        FMLCommonHandler.instance().bus().register(MapPreview.INSTANCE);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(MapPreview.COMMAND);
        FMLCommonHandler.instance().bus().register(ReliableChat.INSTANCE);
        MinecraftForge.EVENT_BUS.register(Regions.INSTANCE);
        FMLCommonHandler.instance().bus().register(Regions.INSTANCE);
        MinecraftForge.EVENT_BUS.register(VoteBook.INSTANCE);
        FMLCommonHandler.instance().bus().register(VoteBook.INSTANCE);
        MinecraftForge.EVENT_BUS.register(AutoGg.INSTANCE);
        FMLCommonHandler.instance().bus().register(AutoGg.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TeamBar.INSTANCE);
        FMLCommonHandler.instance().bus().register(LeftHand.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TeamBubbles.INSTANCE);
        FMLCommonHandler.instance().bus().register(TeamBubbles.INSTANCE);
        FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);
        net.minecraft.client.renderer.entity.RenderManager rm=MinecraftAccess.get().getRenderManager();
        if(rm!=null)for(RenderPlayer renderer:rm.getSkinMap().values())renderer.addLayer(new FaceOverlay(renderer));
        ((IReloadableResourceManager)MinecraftAccess.get().getResourceManager()).registerReloadListener(resources->{SkinFace.clear();FaceMarks.clearCache();KillFeed.clearHeads();});
    }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event){
        if(minimap.isPressed()){Minimap.INSTANCE.cycleView();return;}
        if(minimapZoom.isPressed()){Minimap.INSTANCE.cycleZoom();return;}
        if(teamChat.isPressed()){ReliableChat.openChannel(ChatRules.TEAM);return;}
        if(partyChat.isPressed()){ReliableChat.openChannel(ChatRules.PARTY);return;}
        openMenu();
    }
    @SubscribeEvent public void mouse(InputEvent.MouseInputEvent event){if(minimap.isPressed()){Minimap.INSTANCE.cycleView();return;}if(minimapZoom.isPressed()){Minimap.INSTANCE.cycleZoom();return;}openMenu();}
    private void openMenu(){
        Minecraft mc=MinecraftAccess.get();
        boolean pressed=modules.isPressed();
        if(mc.thePlayer==null||mc.currentScreen!=null)return;
        if(pressed)mc.displayGuiScreen(new HudEditor());
    }
    @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event){
        Minecraft mc=MinecraftAccess.get();
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||mc.theWorld==null)return;
        for(net.minecraft.entity.player.EntityPlayer p:new java.util.ArrayList<net.minecraft.entity.player.EntityPlayer>(mc.theWorld.playerEntities))PlayerSignals.tick(p);
    }
    @SubscribeEvent public void worldUnload(WorldEvent.Unload event){
        if(event.world!=null&&event.world.isRemote)PlayerSignals.clear();
    }
}
