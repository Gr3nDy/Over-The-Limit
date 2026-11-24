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
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraft.client.resources.*;
import org.lwjgl.input.Keyboard;

@Mod(modid="otl",name="Over The Limit",version="0.9.1",clientSideOnly=true,acceptedMinecraftVersions="[1.8.9]",acceptableRemoteVersions="*")
public final class Otl {
    static final String VERSION="0.9.1";
    private final KeyBinding modules=new KeyBinding("key.otl.modules",Keyboard.KEY_P,"key.categories.otl");
    private final KeyBinding teamChat=new KeyBinding("key.otl.team_chat",Keyboard.KEY_Y,"key.categories.otl");
    private final KeyBinding partyChat=new KeyBinding("key.otl.party_chat",Keyboard.KEY_NONE,"key.categories.otl");
    private final KeyBinding minimap=new KeyBinding("key.otl.minimap",Keyboard.KEY_M,"key.categories.otl");
    private final KeyBinding minimapZoom=new KeyBinding("key.otl.minimapzoom",Keyboard.KEY_SEMICOLON,"key.categories.otl");
    private final KeyBinding emote=new KeyBinding("key.otl.emote",Keyboard.KEY_B,"key.categories.otl");
    private final KeyBinding skateboard=new KeyBinding("key.otl.skateboard",Keyboard.KEY_N,"key.categories.otl");
    private final java.util.Map<RenderPlayer,net.minecraft.client.model.ModelBase> vanillaPlayerModels=new java.util.HashMap<>();
    private final java.util.Map<RenderPlayer,AnimatedModelPlayer> animatedPlayerModels=new java.util.HashMap<>();
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(modules);
        ClientRegistry.registerKeyBinding(teamChat);
        ClientRegistry.registerKeyBinding(partyChat);
        ClientRegistry.registerKeyBinding(minimap);
        ClientRegistry.registerKeyBinding(minimapZoom);
        ClientRegistry.registerKeyBinding(emote);
        ClientRegistry.registerKeyBinding(skateboard);
        MinecraftForge.EVENT_BUS.register(ReliableChat.INSTANCE);
        MinecraftForge.EVENT_BUS.register(KillFeed.INSTANCE);
        MinecraftForge.EVENT_BUS.register(KillConfirm.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BowOptimizer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BowAccuracy.INSTANCE);
        FMLCommonHandler.instance().bus().register(BowAccuracy.INSTANCE);
        MinecraftForge.EVENT_BUS.register(Minimap.INSTANCE);
        FMLCommonHandler.instance().bus().register(Minimap.INSTANCE);
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
        MinecraftForge.EVENT_BUS.register(Touchdown.INSTANCE);
        FMLCommonHandler.instance().bus().register(Touchdown.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TouchdownCard.INSTANCE);
        FMLCommonHandler.instance().bus().register(TouchdownCard.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BallTrail.INSTANCE);
        FMLCommonHandler.instance().bus().register(BallTrail.INSTANCE);
        MinecraftForge.EVENT_BUS.register(BallParticles.INSTANCE);
        FMLCommonHandler.instance().bus().register(BallParticles.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ArrowParticles.INSTANCE);
        FMLCommonHandler.instance().bus().register(ArrowParticles.INSTANCE);
        MinecraftForge.EVENT_BUS.register(TabListMenu.INSTANCE);
        FMLCommonHandler.instance().bus().register(TabListMenu.INSTANCE);
        MinecraftForge.EVENT_BUS.register(UpdateNotice.INSTANCE);
        FMLCommonHandler.instance().bus().register(UpdateNotice.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PlayerAnim.INSTANCE);
        FMLCommonHandler.instance().bus().register(PlayerAnim.INSTANCE);
        installTouchdown();
        FMLCommonHandler.instance().bus().register(this);MinecraftForge.EVENT_BUS.register(this);
        net.minecraft.client.renderer.entity.RenderManager rm=MinecraftAccess.get().getRenderManager();
        if(rm!=null)for(java.util.Map.Entry<String,RenderPlayer> entry:rm.getSkinMap().entrySet()){
            RenderPlayer renderer=entry.getValue();
            renderer.addLayer(new FaceOverlay(renderer));
            // RenderPlayer reads getMainModel() everywhere except the first-person arm, so swapping
            // this field animates every rendered player and leaves the held hand alone.
            installSkateHeldLayer(renderer);
            animatedPlayerModels.put(renderer,new AnimatedModelPlayer("slim".equals(entry.getKey())));
        }
        ((IReloadableResourceManager)MinecraftAccess.get().getResourceManager()).registerReloadListener(resources->{SkinFace.clear();FaceMarks.clearCache();KillFeed.clearHeads();});
    }

    /** Specials.Pre is the concrete subclass: the bus cannot instantiate the abstract Specials. */
    @SubscribeEvent
    public void skateNameplate(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre event){
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer!=null&&event.entity==mc.thePlayer&&PlayerAnim.skating())event.setCanceled(true);
    }

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void localSkateModel(RenderPlayerEvent.Pre event){
        // Remember the real model every time it is not ours, so an OptiFine resource reload that
        // swaps the renderer's model is picked up instead of being restored to a stale instance.
        net.minecraft.client.model.ModelBase current=event.renderer.getMainModel();
        boolean ours=current instanceof AnimatedModelPlayer;
        if(!ours)vanillaPlayerModels.put(event.renderer,current);
        // The split-limb rig exists only for emotes. Skateboarding leaves the player model
        // completely vanilla: no swap, no resize, no reposition.
        if(PlayerAnim.emoting(event.entityPlayer)){
            AnimatedModelPlayer model=animatedPlayerModels.get(event.renderer);
            if(model!=null)setMainModel(event.renderer,model);
        }else if(ours){
            // A missed Post must never leave the custom model on the shared renderer, or every
            // other player would draw with the split-limb rig and broken armour.
            net.minecraft.client.model.ModelBase vanilla=vanillaPlayerModels.get(event.renderer);
            if(vanilla!=null)setMainModel(event.renderer,vanilla);
        }
    }

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void restoreLocalSkateModel(RenderPlayerEvent.Post event){
        PlayerAnim.renderBoard(event.entityPlayer,event.partialRenderTick,event.x,event.y,event.z);
        net.minecraft.client.model.ModelBase vanilla=vanillaPlayerModels.get(event.renderer);
        if(vanilla!=null)setMainModel(event.renderer,vanilla);
    }

    private static void setMainModel(RenderPlayer renderer,net.minecraft.client.model.ModelBase model){
        try{
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(
                net.minecraft.client.renderer.entity.RendererLivingEntity.class,renderer,model,"mainModel","field_77045_g");
        }catch(RuntimeException ignored){/* a differing mapping just leaves vanilla rendering */}
    }

    private static void installSkateHeldLayer(RenderPlayer renderer){
        try{
            java.util.List<net.minecraft.client.renderer.entity.layers.LayerRenderer> layers=
                net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                    net.minecraft.client.renderer.entity.RendererLivingEntity.class,renderer,
                    "layerRenderers","field_177097_h");
            if(layers==null)return;
            for(java.util.Iterator<net.minecraft.client.renderer.entity.layers.LayerRenderer> it=layers.iterator();it.hasNext();)
                if(it.next() instanceof net.minecraft.client.renderer.entity.layers.LayerHeldItem)it.remove();
            layers.add(new SkateHeldItemLayer(renderer));
        }catch(RuntimeException fail){System.err.println("OTL: could not install skate held-item layer: "+fail);}
    }

    /** Reliable Chat cancels the lines it absorbs, so this must still see them. */
    /** Menus replace the game HUD: Lunar's chrome does not show the hotbar and crosshair through its panels. */
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void hideHudBehindMenus(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null)return;
        // Cancelling the ALL pass skips the whole frame, screens included; only drop element passes.
        if(event.type==net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL)return;
        if(mc.currentScreen instanceof ModulesScreen||mc.currentScreen instanceof SettingsScreen)event.setCanceled(true);
    }
    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true)
    public void chat(net.minecraftforge.client.event.ClientChatReceivedEvent event){
        if(event.type!=2)ServerScope.observe(ChatRules.plain(event.message.getFormattedText()));
    }
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event){
        if(minimap.isPressed()){Minimap.INSTANCE.cycleView();return;}
        if(minimapZoom.isPressed()){Minimap.INSTANCE.cycleZoom();return;}
        if(emote.isPressed()){PlayerAnim.emoteKey(MinecraftAccess.get());return;}
        if(skateboard.isPressed()){PlayerAnim.skateKey(MinecraftAccess.get());return;}
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
        OtlPresence.tick();
    }
    @SubscribeEvent public void worldUnload(WorldEvent.Unload event){
        if(event.world!=null&&event.world.isRemote){
            PlayerSignals.clear();PlayerAnim.clear();
            // The shared link and the presence list belong to the world that just went away.
            OtlService.stop();OtlPresence.reset();
        }
    }
    /** The snowball's baked model is swapped once per resource reload; it delegates while no Touchdown map runs. */
    @SubscribeEvent public void modelBake(net.minecraftforge.client.event.ModelBakeEvent event){
        net.minecraft.client.resources.model.ModelResourceLocation snowball=
            new net.minecraft.client.resources.model.ModelResourceLocation("minecraft:snowball","inventory");
        net.minecraft.client.resources.model.IBakedModel base=event.modelRegistry.getObject(snowball);
        if(base!=null&&!(base instanceof FootballItemModel))
            event.modelRegistry.putObject(snowball,new FootballItemModel(base));
    }
    @SuppressWarnings("unchecked")
    private static void installTouchdown(){
        Minecraft mc=MinecraftAccess.get();
        net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer items=
            net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer.instance;
        if(!(items instanceof FootballItemRenderer))
            net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer.instance=new FootballItemRenderer(items);
        net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher tiles=
            net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance;
        net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<net.minecraft.tileentity.TileEntityBanner> banner=
            (net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<net.minecraft.tileentity.TileEntityBanner>)
                tiles.mapSpecialRenderers.get(net.minecraft.tileentity.TileEntityBanner.class);
        if(!(banner instanceof FootballBannerRenderer))
            tiles.mapSpecialRenderers.put(net.minecraft.tileentity.TileEntityBanner.class,
                new FootballBannerRenderer(banner==null?new net.minecraft.client.renderer.tileentity.TileEntityBannerRenderer():banner));
        net.minecraft.client.renderer.entity.RenderManager manager=mc.getRenderManager();
        if(manager!=null){
            manager.entityRenderMap.put(net.minecraft.entity.projectile.EntitySnowball.class,
                new RenderFootballSnowball(manager,net.minecraft.init.Items.snowball,mc.getRenderItem()));
            net.minecraft.client.renderer.entity.RenderEntityItem dropped=
                (net.minecraft.client.renderer.entity.RenderEntityItem)
                    manager.entityRenderMap.get(net.minecraft.entity.item.EntityItem.class);
            // The touchdown flag floats at the post as a dropped banner item; keep everything else vanilla.
            if(dropped!=null&&!(dropped instanceof RenderFootballFlag))
                manager.entityRenderMap.put(net.minecraft.entity.item.EntityItem.class,
                    new RenderFootballFlag(manager,mc.getRenderItem()));
        }
    }
}
