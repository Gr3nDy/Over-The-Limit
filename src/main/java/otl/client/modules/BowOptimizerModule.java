package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.BowAccuracy;
import otl.client.BowOptimizer;
import otl.client.BowOptimizerSettings;
import otl.client.HudLayout;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Bow Optimizer module. */
public final class BowOptimizerModule extends OtlModule {
    public BowOptimizerModule() { super("bow_optimizer", "Bow Optimizer", "MECHANIC", 80); }
    public boolean enabled() { return UtilitySettings.bowOptimizer; }
    public void setEnabled(boolean on) { UtilitySettings.bowOptimizer = on; BowOptimizer.clearEchoes(); BowAccuracy.resetAccuracy(); }
    public void reset() {
        UtilitySettings.restore("bow_optimizer","bow_optimization","bow_accuracy","bow_accuracy_prefix","bow_accuracy_mode","bow_accuracy_animate",
                "bow_accuracy_opacity","bow_accuracy_decimals","bow_accuracy_size","bow_accuracy_fade_in","bow_accuracy_fade_out",
                "bow_hit_sound","bow_kill_sound","bow_hit_marker","bow_hit_marker_size");
        UtilitySettings.hudPositions.remove(HudLayout.BOW);
        BowOptimizer.clearEchoes();
        BowAccuracy.clearFeedback();
        BowAccuracy.resetAccuracy();
    }
    public GuiScreen settings(GuiScreen back) { return new BowOptimizerSettings(back); }
}
