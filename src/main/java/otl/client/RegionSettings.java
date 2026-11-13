package otl.client;

import net.minecraft.client.gui.*;

/** Options for the Region Borders module: the region text, its prefix and its background. */
public final class RegionSettings extends SettingsScreen {
    private final GuiScreen parent;
    public RegionSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Region Borders";}
    protected String subtitle(){return "Shows the region you are standing in.";}
    protected void buildPanel(){
        resetButton("region_overlay");
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Region text: "+(UtilitySettings.regionHud?"ON":"OFF"),UtilitySettings.regionHud));
        NeutralButton prefix=new NeutralButton(2,panel.left(),panel.row(),panel.width(),20,"Prefix: "+(UtilitySettings.regionHudPrefix?"ON":"OFF"),UtilitySettings.regionHudPrefix);
        prefix.enabled=UtilitySettings.regionHud; // the prefix only matters while the text is on
        buttonList.add(prefix);
        buttonList.add(new PresetSlider(3,panel.left(),panel.row(),panel.width(),"Background: ","%",PresetSlider.PERCENT,UtilitySettings.regionHudBackground,25,s->UtilitySettings.regionHudBackground=s.getValueInt()));
        panel.note("Move it in the HUD editor.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==1){UtilitySettings.regionHud=!UtilitySettings.regionHud;UtilitySettings.save();initGui();return;}
        if(b.id==2){UtilitySettings.regionHudPrefix=!UtilitySettings.regionHudPrefix;UtilitySettings.save();initGui();return;}
        if(b.id==3)return;
        if(b.id==4)mc.displayGuiScreen(parent);
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
