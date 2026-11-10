package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.HudLayout;
import otl.client.KillConfirm;
import otl.client.KillConfirmSettings;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Kill Confirm module. */
public final class KillConfirmModule extends OtlModule {
    public KillConfirmModule() { super("kill_confirm", "Kill Confirm", "HUD", 60); }
    public boolean enabled() { return UtilitySettings.killConfirm; }
    public void setEnabled(boolean on) { UtilitySettings.killConfirm = on; KillConfirm.clearState(); }
    public void reset() {
        UtilitySettings.restore("kill_confirm","kill_confirm_size","kill_confirm_sounds","total_exp_kills","kill_confirm_player_head",
                "kill_confirm_opacity","hide_kill_confirm_chat");
        UtilitySettings.hudPositions.remove(HudLayout.CONFIRM);
        KillConfirm.clearState();
    }
    public GuiScreen settings(GuiScreen back) { return new KillConfirmSettings(back); }
}
