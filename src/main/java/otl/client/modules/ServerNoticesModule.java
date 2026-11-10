package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.HudLayout;
import otl.client.OtlModule;
import otl.client.ServerNoticeSettings;
import otl.client.UtilitySettings;

/** Server Notices module (temporarily unavailable). */
public final class ServerNoticesModule extends OtlModule {
    public ServerNoticesModule() { super("server_notices", "Server Notices", "HUD", 70); }
    public boolean available() { return false; }
    public boolean enabled() { return false; }
    public void setEnabled(boolean on) { UtilitySettings.nonPlayerChat = false; }
    public void reset() {
        UtilitySettings.restore("non_player_chat","notice_opacity","notice_size","notice_mode");
        UtilitySettings.nonPlayerChat = false;
        UtilitySettings.hudPositions.remove(HudLayout.NOTICES);
    }
    public GuiScreen settings(GuiScreen back) { return new ServerNoticeSettings(back); }
}
