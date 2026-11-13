package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.HudLayout;
import otl.client.OtlModule;
import otl.client.ReliableChat;
import otl.client.UtilityScreen;
import otl.client.UtilitySettings;

/** Reliable Chat module. */
public final class ReliableChatModule extends OtlModule {
    public ReliableChatModule() { super("reliable_chat", "Reliable Chat", "HUD", 20); }
    public boolean enabled() { return UtilitySettings.chat; }
    public void setEnabled(boolean on) { UtilitySettings.chat = on; ReliableChat.applyPreferences(); }
    public void reset() {
        UtilitySettings.restore("reliable_chat","unread_counts","other_faces","remember_drafts","unlimited_history","shift_tab",
                "team_bubbles","bubble_team_and_chat","bubble_characters","bubble_seconds","show_tab_chat","tab_chat_position",
                "tab_background_opacity","typing_tab_background_opacity");
        UtilitySettings.hudPositions.remove(HudLayout.TABS);
        ReliableChat.applyPreferences();
    }
    public GuiScreen settings(GuiScreen back) { return new UtilityScreen(back); }
}
