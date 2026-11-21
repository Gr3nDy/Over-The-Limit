package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Checks added after the smoke driver ran out of room: OtlSmoke's own class file is at the JVM's
 * constant-pool limit, so a new check gets its own file rather than one more method there.
 */
final class OtlSmokeChecks {
    private final OtlSmoke smoke;
    OtlSmokeChecks(OtlSmoke smoke){this.smoke=smoke;}

    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    /**
     * The animated model is installed by reflection, so a wrong mapping would only show up as a
     * logged warning and a game that looks unchanged. Assert the swap really landed.
     */
    void animatedModel(Minecraft mc){
        java.util.Map<String,net.minecraft.client.renderer.entity.RenderPlayer> skin=mc.getRenderManager().getSkinMap();
        require(skin.size()>=2,"Skin map lost a player renderer");
        AnimatedModelPlayer model=null;
        for(net.minecraft.client.renderer.entity.RenderPlayer renderer:skin.values()){
            require(renderer.getMainModel() instanceof AnimatedModelPlayer,
                "Player renderer still uses the vanilla model");
            model=(AnimatedModelPlayer)renderer.getMainModel();
        }
        // One anatomical cut: the hand/foot stays attached to the forearm/shin segment.
        require(model.lowerArmR.childModels==null||model.lowerArmR.childModels.isEmpty(),"The arm has an artificial extra joint");
        require(model.lowerLegL.childModels==null||model.lowerLegL.childModels.isEmpty(),"The leg has an artificial extra joint");
        require(model.lowerArmR.cubeList.size()==1&&model.lowerArmR.cubeList.get(0).posY2>model.lowerArmR.cubeList.get(0).posY1,
            "The forearm has no geometry");
    }

    /**
     * The sidebar toggle: it hides the vanilla sidebar only while the bar is up, and the mod puts
     * the sidebar back itself, so turning the option off can never leave the screen empty.
     */
    void scoreboardToggle(Minecraft mc){
        UtilitySettings.teamBar=true;
        UtilitySettings.teamBarHideScoreboard=false;
        require(!TeamBar.hidesScoreboard(mc),"The sidebar was hidden with its option off");
        UtilitySettings.teamBarHideScoreboard=true;
        require(TeamBar.hidesScoreboard(mc),"The sidebar stayed visible with its option on");
        // The real render flag is driven from the top of the frame, before vanilla draws the sidebar.
        net.minecraftforge.client.GuiIngameForge.renderObjective=true;
        net.minecraftforge.client.event.RenderGameOverlayEvent frame=
            new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre all=
            new net.minecraftforge.client.event.RenderGameOverlayEvent.Pre(frame,
                net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL);
        TeamBar.INSTANCE.scoreboard(all);
        require(!net.minecraftforge.client.GuiIngameForge.renderObjective,"The sidebar was still drawn with its option on");
        UtilitySettings.teamBarHideScoreboard=false;
        TeamBar.INSTANCE.scoreboard(all);
        require(net.minecraftforge.client.GuiIngameForge.renderObjective,"The sidebar did not come back when the option was turned off");
        UtilitySettings.teamBar=false;
        require(!TeamBar.hidesScoreboard(mc),"The sidebar was hidden with Team Bar disabled");
        UtilitySettings.teamBar=true;
    }

    /**
     * Deleting a profile asks first. The Yes callback refreshed the menu without making it current
     * again, so a successful delete left the confirmation sitting on screen with nothing behind it.
     * This drives the real rail click and the real callback rather than rebuilding the dialog.
     */
    void profileDeleteDialog(Minecraft mc,ModulesScreen menu){
        String home=UtilitySettings.profile,doomed="zz delete me";
        try{UtilitySettings.write(Profiles.file(doomed),UtilitySettings.DEFAULTS);}
        catch(Exception e){throw new AssertionError("Could not create the test profile: "+e.getMessage());}
        menu.initGui();
        java.util.List<String> rail=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"profiles");
        int index=rail.indexOf(doomed);
        require(index>=0,"The test profile never reached the rail");
        int x=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"x");
        int y=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"y");
        int h=ReflectionHelper.getPrivateValue(ModulesScreen.class,menu,"h");
        int rows=Math.max(1,(h-104)/22);
        // The rail shows only the first rows; scroll the new profile into view before clicking it.
        int scroll=Math.max(0,Math.min(index,rail.size()-rows));
        ReflectionHelper.setPrivateValue(ModulesScreen.class,menu,scroll,"railScroll");
        int row=index-scroll;
        require(row>=0&&row<rows,"The test profile could not be scrolled into the rail's view");
        // The rail is 132 wide with 52 reserved for its actions: the X starts 32px into that.
        try{menu.mouseClicked(x+8+120-52+34,y+30+row*22+5,0);}
        catch(java.io.IOException e){throw new AssertionError("Rail click failed: "+e.getMessage());}
        require(mc.currentScreen instanceof net.minecraft.client.gui.GuiYesNo,"Deleting a profile did not ask first");
        net.minecraft.client.gui.GuiYesNo dialog=(net.minecraft.client.gui.GuiYesNo)mc.currentScreen;
        net.minecraft.client.gui.GuiYesNoCallback callback=
            ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiYesNo.class,dialog,"parentScreen");
        callback.confirmClicked(true,0);
        require(mc.currentScreen instanceof ModulesScreen,"The delete confirmation stayed on screen after Yes");
        require(!Profiles.profileNames().contains(doomed),"The profile was not deleted");
        require(home.equals(UtilitySettings.profile),"Deleting a profile changed the active profile");
    }

    /**
     * A filtered tab must be the only chat on screen. The All tab is the client's own chat object,
     * so if that object still holds its lines the client paints them behind the filtered tab and
     * the chat reads as doubled.
     */
    void chatDoubling(Minecraft mc,int state){
        if(state==155){
            UtilitySettings.chat=true;UtilitySettings.showTabChat=true;
            ReliableChat.session().select("team");
            // Cancelling the Chat event also skips Forge's post(CHAT), where the Team Bar is drawn,
            // so a filtered tab must never cancel it: blank the client's lines instead. The tab is
            // still empty here, so this posts the handler without drawing anything.
            net.minecraftforge.client.event.RenderGameOverlayEvent frame=
                new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
            net.minecraftforge.client.event.RenderGameOverlayEvent.Chat chat=
                new net.minecraftforge.client.event.RenderGameOverlayEvent.Chat(frame,0,mc.currentScreen==null?0:mc.currentScreen.height-48);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(chat);
            require(!chat.isCanceled(),"A filtered tab cancelled the Chat event, which also hides the Team Bar");
            smoke.line("Created by MapMaker");
            smoke.line("\u00a79(Blue Team) Alex: team hello");
            smoke.line("Bob: global hello");
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
            require(mc.currentScreen instanceof ReliableChatScreen,"The chat screen was not replaced");
            ((ReliableChatScreen)mc.currentScreen).select("team");
            require(ReliableChat.session().selected.equals("team"),"The Team tab was not selected");
            require(ReliableChat.session().views.get("team").totalLines()>0,"The team line never reached the Team tab");
        }else if(state==156){
            // A screenshot taken in the same tick as a change captures the previous frame, so the
            // blanked chat is only visible one tick later.
            require(clientLines(mc).isEmpty(),"The client's own chat still paints All behind the Team tab");
        }else if(state==157){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-team-open.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            require(clientLines(mc).isEmpty(),"The client's own chat repopulated behind the Team tab");
            ((ReliableChatScreen)mc.currentScreen).select("all");
        }else if(state==158){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-chat-all-open.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            require(!clientLines(mc).isEmpty(),"Switching back to All did not restore the client's own chat");
        }
    }

    /**
     * A death line must still reach the kill feed while a filtered chat tab is on screen. The feed
     * draws on the frame's own chat stage, which an earlier chat fix skipped entirely.
     */
    void killFeedWithFilteredTab(Minecraft mc,int state){
        if(state==159){
            UtilitySettings.chat=true;UtilitySettings.killFeed=true;
            UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.ALL;UtilitySettings.showTabChat=true;
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());
            ((ReliableChatScreen)mc.currentScreen).select("team");
            require(ReliableChat.session().selected.equals("team"),"The Team tab was not selected");
            KillFeed.history.clear();
            smoke.line("\u00a7cAlex\u00a7r was slain by \u00a7cSteve\u00a7r's Iron Sword");
        }else if(state==160){
            require(!KillFeed.history.entries.isEmpty(),"A death line never reached the kill feed with the Team tab on");
            KillFeedRules.Death death=KillFeed.history.entries.get(0);
            require("Alex".equals(death.victim)&&"Steve".equals(death.killer),"The death was read wrong: "+death.victim+" / "+death.killer);
            // The feed draws from the frame's chat stage: post the real event and check it is not cancelled.
            net.minecraftforge.client.event.RenderGameOverlayEvent frame=
                new net.minecraftforge.client.event.RenderGameOverlayEvent(0F,new net.minecraft.client.gui.ScaledResolution(mc));
            net.minecraftforge.client.event.RenderGameOverlayEvent.Chat chat=
                new net.minecraftforge.client.event.RenderGameOverlayEvent.Chat(frame,0,mc.currentScreen.height-48);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(chat);
            require(!chat.isCanceled(),"The chat stage was cancelled, so the kill feed could not draw");
        }
    }
    private java.util.List<?> clientLines(Minecraft mc){
        return ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiNewChat.class,mc.ingameGUI.getChatGUI(),"drawnChatLines","field_146253_i");
    }

    /** Stands in for the mod that was cancelling the chat stage on the user's client. */
    static final class ChatCanceller{
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void cancel(net.minecraftforge.client.event.RenderGameOverlayEvent.Chat event){event.setCanceled(true);}
    }
    private ChatCanceller canceller;
    private long fallbacksBefore;

    /**
     * Another mod cancelling the chat stage skips post(CHAT), and every HUD widget used to draw
     * there: the bar and the feed kept running with nothing on screen. The end of the frame has to
     * take over, so this registers a canceller and checks the HUD keeps drawing.
     */
    void hudSurvivesACancelledChatStage(Minecraft mc,int state){
        if(state==161){
            UtilitySettings.chat=true;UtilitySettings.killFeed=true;
            UtilitySettings.killFeedFilter=KillFeedRules.FilterMode.ALL;
            if(mc.currentScreen!=null)mc.displayGuiScreen(null);
            KillFeed.history.clear();
            smoke.line("\u00a7cAlex\u00a7r was slain by \u00a7cSteve\u00a7r's Iron Sword");
            fallbacksBefore=HudStage.fallbacks;
            canceller=new ChatCanceller();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(canceller);
        }else if(state==162){
            require(HudStage.fallbacks>fallbacksBefore,"The HUD stopped drawing when the chat stage was cancelled");
            require(!KillFeed.history.entries.isEmpty(),"The death line never reached the feed");
        }else if(state==163){
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,"otl-hud-chat-cancelled.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(canceller);canceller=null;
        }
    }

    /**
     * The end-of-match sound, through Forge's own event object and the real handlers. On a client
     * whose chat PGM translates, this sound is the only end signal left, so it is checked with the
     * English lines out of the picture: no chat line is sent here, only the sound.
     */
    void matchEndSound(){
        // The event only reads the sound's own name, so it needs no live sound manager.
        net.minecraftforge.client.event.sound.PlaySoundEvent event=
            new net.minecraftforge.client.event.sound.PlaySoundEvent(null,
                net.minecraft.client.audio.PositionedSoundRecord.create(
                    new net.minecraft.util.ResourceLocation("minecraft","mob.wither.death"),1F),
                net.minecraft.client.audio.SoundCategory.MASTER);
        // On 1.8 the client names the sound without a namespace; modern servers send it prefixed.
        require(MatchEnd.endedBySound(event.name),"The event did not carry the wither sound: "+event.name);
        require(!(boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The bar was already stopped before the sound");
        UtilitySettings.autoGgDelay=0;UtilitySettings.autoGgRandom=false;
        AutoGg.INSTANCE.sound(event);
        TeamBar.INSTANCE.sound(event);
        require((boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The end-of-match sound did not stop the bar's clock");
        require(!ReflectionHelper.getPrivateValue(AutoGg.class,AutoGg.INSTANCE,"sendAt").equals(0L),
            "The end-of-match sound did not arm a gg");
        // A second copy of the sound must not say gg twice.
        AutoGg.INSTANCE.sound(event);
        int armed=AutoGg.INSTANCE.sentCount;
        smoke.line("The match has started!");
        require(ReflectionHelper.getPrivateValue(AutoGg.class,AutoGg.INSTANCE,"sendAt").equals(0L),
            "The next match did not cancel the gg the sound armed");
        require(AutoGg.INSTANCE.sentCount==armed,"The sound said gg more than once for one match");
        require(!(boolean)ReflectionHelper.getPrivateValue(TeamBar.class,TeamBar.INSTANCE,"ended"),
            "The next match did not restart the bar's clock");
    }
}
