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
        panel.note("On Touchdown maps the flag becomes a floating football: on the post, in the hand, in inventory slots and on the carrier's head.");
        panel.section("REPLAY");
        panel.note("After a touchdown, the last few seconds replay in slow motion: the throw, the catch and the run into the end zone, from a camera that follows the ball and then the carrier. Solo scores replay the carrier's run. Press any key to skip.");
        panel.section("SAFETY");
        panel.note("The replay never pauses the match: the round keeps running, and it ends the moment PGM announces the next one.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4)mc.displayGuiScreen(parent);
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
