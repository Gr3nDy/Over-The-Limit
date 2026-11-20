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
