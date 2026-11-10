package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.OtlModule;
import otl.client.PreferedHandSettings;
import otl.client.UtilitySettings;

/** Prefered Hand module. */
public final class LeftHandModule extends OtlModule {
    public LeftHandModule() { super("left_hand", "Prefered Hand", "VISUAL", 40); }
    public boolean enabled() { return UtilitySettings.leftHand; }
    public void setEnabled(boolean on) { UtilitySettings.leftHand = on; }
    public void reset() { UtilitySettings.restore("left_hand","main_hand_left","hand_type"); }
    public GuiScreen settings(GuiScreen back) { return new PreferedHandSettings(back); }
}
