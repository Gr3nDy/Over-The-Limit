package otl.client;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
/** Auto GG options: who says it, how long after the match ends, and whether that wait varies. */
public final class AutoGgSettings extends SettingsScreen {
    private final GuiScreen parent;
    public AutoGgSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Auto GG";}
    protected String subtitle(){return "Says gg in chat once when the match ends.";}
    protected void buildPanel(){
        resetButton("auto_gg");
        panel.section("AUTO GG");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Auto GG: "+(UtilitySettings.autoGg?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Say gg in observer: "+(UtilitySettings.autoGgObserver?"ON":"OFF"),false));
        panel.section("DELAY");
        buttonList.add(new PresetSlider(2,panel.left(),panel.row(),panel.width(),"Delay: "," ms",PresetSlider.DELAY,UtilitySettings.autoGgDelay,1000,s->UtilitySettings.autoGgDelay=s.getValueInt()));
        buttonList.add(new NeutralButton(3,panel.left(),panel.row(),panel.width(),20,"Randomise delay: "+(UtilitySettings.autoGgRandom?"ON":"OFF"),false));
        panel.note("Randomise picks any delay up to the value above.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==2)return; // sliders persist on mouse release
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.autoGg=!UtilitySettings.autoGg;
        if(b.id==1)UtilitySettings.autoGgObserver=!UtilitySettings.autoGgObserver;
        if(b.id==3)UtilitySettings.autoGgRandom=!UtilitySettings.autoGgRandom;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
