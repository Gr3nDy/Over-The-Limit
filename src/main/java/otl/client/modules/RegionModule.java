package otl.client.modules;

import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Region Borders module: wireframes of the current map's regions from map.xml, plus the region text label. */
public final class RegionModule extends OtlModule {
    public RegionModule() { super("region_overlay", "Region Borders", "HUD", 95); }
    public boolean enabled() { return UtilitySettings.regionOverlay; }
    public void setEnabled(boolean on) { UtilitySettings.regionOverlay = on; }
    public void reset() { UtilitySettings.restore("region_overlay","region_hud","region_hud_prefix","region_hud_background"); }
    public net.minecraft.client.gui.GuiScreen settings(net.minecraft.client.gui.GuiScreen back) { return new otl.client.RegionSettings(back); }
}
