package otl.client.modules;

import net.minecraft.client.gui.GuiScreen;
import otl.client.OtlModule;
import otl.client.TouchdownSettings;
import otl.client.UtilitySettings;

/** Touchdown: a 3D football item and floating footballs in place of the flag banners. */
public final class TouchdownModule extends OtlModule {
    public TouchdownModule(){super("touchdown","Touchdown","VISUAL",15);}
    public boolean enabled(){return UtilitySettings.touchdown;}
    public void setEnabled(boolean on){UtilitySettings.touchdown=on;}
    public void reset(){UtilitySettings.restore("touchdown","touchdown_replay");}
    public GuiScreen settings(GuiScreen back){return new TouchdownSettings(back);}
}
