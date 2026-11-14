package otl.client;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
/** Touchdown options. */
public final class TouchdownSettings extends SettingsScreen {
    private final GuiScreen parent;
    public TouchdownSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Touchdown";}
    protected String subtitle(){return "The Football, the flag and the touchdown replay.";}
    protected void buildPanel(){
        resetButton("touchdown");
        panel.section("TOUCHDOWN");
        panel.note("Replaces the flag banners with floating footballs on Touchdown maps.");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Touchdown replay: "+(UtilitySettings.touchdownReplay?"ON":"OFF"),false));
        panel.note("After a touchdown, replays the last few seconds: the throw, the catch and the run into the end zone, in slow motion from a broadcast camera. Press any key to skip.");
        panel.section("SAFETY");
        panel.note("The replay never pauses the match: the round keeps running, and it ends the moment PGM announces the next one.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.touchdownReplay=!UtilitySettings.touchdownReplay;
        UtilitySettings.save();initGui();
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
