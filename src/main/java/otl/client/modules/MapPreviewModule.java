package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.MapPreviewSettings;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Map Preview module. */
public final class MapPreviewModule extends OtlModule {
    public MapPreviewModule() { super("map_preview", "Map Preview", "HUD", 30); }
    public boolean enabled() { return UtilitySettings.mapPreview; }
    public void setEnabled(boolean on) { UtilitySettings.mapPreview = on; }
    public void reset() { UtilitySettings.restore("map_preview","preview_on_command","large_map_preview"); }
    public GuiScreen settings(GuiScreen back) { return new MapPreviewSettings(back); }
}
