package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.HudLayout;
import otl.client.KillFeedSettings;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Kill Feed module. */
public final class KillFeedModule extends OtlModule {
    public KillFeedModule() { super("kill_feed", "Kill Feed", "HUD", 50); }
    public boolean enabled() { return UtilitySettings.killFeed; }
    public void setEnabled(boolean on) { UtilitySettings.killFeed = on; }
    public void reset() {
        UtilitySettings.restore("kill_feed","hide_kill_chat","kill_feed_heads","kill_feed_filter","kill_feed_size","kill_border_opacity",
                "kill_background_opacity","kill_feed_slide","kill_border_color","death_border_color","assist_border_color",
                "kill_feed_rows","kill_feed_seconds");
        UtilitySettings.hudPositions.remove(HudLayout.KILLS);
    }
    public GuiScreen settings(GuiScreen back) { return new KillFeedSettings(back); }
}
