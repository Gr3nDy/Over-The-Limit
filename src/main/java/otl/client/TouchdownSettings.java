package otl.client;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
/** Touchdown options. */
public final class TouchdownSettings extends SettingsScreen {
    private final GuiScreen parent;
    public TouchdownSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Touchdown";}
    protected String subtitle(){return "The Football and the flag.";}
    protected void buildPanel(){
        resetButton("touchdown");
        panel.section("TOUCHDOWN");
        panel.note("On Touchdown maps the flag on the post becomes a floating football. The flag a carrier wears stays a flag, so you can always see who has it.");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Ball trail: "+(UtilitySettings.touchdownTrail?"ON":"OFF"),false));
        panel.note("Draws a fading contrail behind the thrown Football, so long passes are easy to follow. A line, not particles.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.touchdownTrail=!UtilitySettings.touchdownTrail;
        UtilitySettings.save();initGui();
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
