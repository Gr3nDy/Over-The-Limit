package otl.client;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
/** Touchdown options. */
public final class TouchdownSettings extends SettingsScreen {
    private final GuiScreen parent;private int page;
    public TouchdownSettings(GuiScreen parent){this.parent=parent;}
    TouchdownSettings(GuiScreen parent,boolean card){this.parent=parent;this.page=card?1:0;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Touchdown";}
    protected String subtitle(){return "The Football and the flag.";}
    protected void buildPanel(){
        resetButton("touchdown");
        int tabs=panel.row(18);
        buttonList.add(new NeutralButton(20,panel.columnX(2,0),tabs,panel.columnWidth(2),18,"Football",page==0));
        buttonList.add(new NeutralButton(21,panel.columnX(2,1),tabs,panel.columnWidth(2),18,"Player Card",page==1));
        if(page==0){
        panel.section("TOUCHDOWN");
        panel.note("With 3D football on, the post flag becomes a football. The flag a carrier wears stays a flag.");
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"3D football: "+(UtilitySettings.touchdownFootball3d?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Ball trail: "+(UtilitySettings.touchdownTrail?"ON":"OFF"),false));
        panel.note("Draws a fading contrail behind the thrown Football, so long passes are easy to follow. A line, not particles.");
        buttonList.add(new NeutralButton(2,panel.left(),panel.row(),panel.width(),20,"Hide ball particles: "+(UtilitySettings.touchdownHideBallParticles?"ON":"OFF"),false));
        panel.note("Hides the server's particle trail near the ball; the contrail setting above is separate.");
        }else{
        panel.section("PLAYER CARD");
        buttonList.add(new NeutralButton(3,panel.left(),panel.row(),panel.width(),20,"Touchdown player card: "+(UtilitySettings.touchdownCard?"ON":"OFF"),false));
        String[] animations={"Fade","Left","Right","Up","Down"};
        buttonList.add(new ChoiceButton(this,5,panel.left(),panel.choice(),panel.width(),"Card animation",animations,UtilitySettings.touchdownCardAnimation,v->UtilitySettings.touchdownCardAnimation=v));
        panel.note("Shows the scorer and a confirmed passer; move the card in the HUD editor.");
        }
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==20||b.id==21){page=b.id-20;initGui();return;}
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.touchdownTrail=!UtilitySettings.touchdownTrail;
        if(b.id==1)UtilitySettings.touchdownFootball3d=!UtilitySettings.touchdownFootball3d;
        if(b.id==2)UtilitySettings.touchdownHideBallParticles=!UtilitySettings.touchdownHideBallParticles;
        if(b.id==3)UtilitySettings.touchdownCard=!UtilitySettings.touchdownCard;
        UtilitySettings.save();initGui();
    }
    public void onGuiClosed(){UtilitySettings.save();}
}
