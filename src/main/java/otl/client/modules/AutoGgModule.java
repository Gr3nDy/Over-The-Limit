package otl.client.modules;

import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Auto GG: says "gg" in chat once when the match ends. */
public final class AutoGgModule extends OtlModule {
    public AutoGgModule() { super("auto_gg", "Auto GG", "MECHANIC", 85); }
    public boolean enabled() { return UtilitySettings.autoGg; }
    public void setEnabled(boolean on) { UtilitySettings.autoGg = on; }
    public void reset() { UtilitySettings.restore("auto_gg"); }
}
