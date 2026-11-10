package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.FaceScreen;
import otl.client.OtlModule;
import otl.client.UtilitySettings;

/** Face Rig module. */
public final class FaceRigModule extends OtlModule {
    public FaceRigModule() { super("face_rig", "Face Rig", "VISUAL", 10); }
    public boolean enabled() { return UtilitySettings.faceRig; }
    public void setEnabled(boolean on) { UtilitySettings.faceRig = on; }
    public void reset() { UtilitySettings.restore("face_rig"); }
    public GuiScreen settings(GuiScreen back) { return new FaceScreen(back); }
}
