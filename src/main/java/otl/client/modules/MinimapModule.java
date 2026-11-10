package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.HudLayout;
import otl.client.MinimapSettings;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Minimap module. */
public final class MinimapModule extends OtlModule {
    public MinimapModule() { super("minimap", "Minimap", "HUD", 90); }
    public boolean enabled() { return UtilitySettings.minimap; }
    public void setEnabled(boolean on) { UtilitySettings.minimap = on; }
    public void reset() {
        UtilitySettings.restore("minimap","minimap_static","minimap_cutaway","minimap_border","minimap_rotate","minimap_cardinals",
                "minimap_coordinates","minimap_size","minimap_opacity","minimap_large_opacity");
        UtilitySettings.hudPositions.remove(HudLayout.MINIMAP);
    }
    public GuiScreen settings(GuiScreen back) { return new MinimapSettings(back); }
}
