package otl.client.modules;

import otl.client.HudLayout;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Height Limit Overlay module (temporarily unavailable). */
public final class HeightLimitModule extends OtlModule {
    public HeightLimitModule() { super("height_limit_overlay", "Height Limit Overlay", "HUD", 100); }
    public boolean available() { return false; }
    public boolean enabled() { return false; }
    public void setEnabled(boolean on) { UtilitySettings.heightLimitOverlay = false; }
    public void reset() {
        UtilitySettings.restore("height_limit_overlay","height_limit_indicator","height_limit_top_only","height_limit_opacity",
                "height_limit_indicator_seconds","height_limit_size");
        UtilitySettings.heightLimitOverlay = false;
        UtilitySettings.hudPositions.remove(HudLayout.HEIGHT);
    }
    // no settings() override: locked modules offer no options screen, matching OtlModule's contract
}
