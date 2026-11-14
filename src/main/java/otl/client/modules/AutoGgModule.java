package otl.client.modules;
import net.minecraft.client.gui.GuiScreen;
import otl.client.AutoGgSettings;
import otl.client.OtlModule;
import otl.client.UtilitySettings;
/** Auto GG: says "gg" in chat once when the match ends. */
public final class AutoGgModule extends OtlModule {
    public AutoGgModule() { super("auto_gg", "Auto GG", "MECHANIC", 85); }
    public boolean enabled() { return UtilitySettings.autoGg; }
    public void setEnabled(boolean on) { UtilitySettings.autoGg = on; }
    public GuiScreen settings(GuiScreen back) { return new AutoGgSettings(back); }
    public void reset() { UtilitySettings.restore("auto_gg","auto_gg_observer","auto_gg_delay","auto_gg_random"); }
}
