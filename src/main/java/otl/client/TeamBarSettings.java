package otl.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Team Bar options: on or off, heads or counts, size, scoreboard and the clock header. */
public final class TeamBarSettings extends SettingsScreen {
    private final GuiScreen parent;
    public TeamBarSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Team Bar";}
    protected String subtitle(){return "Top-centre team cards with the match clock.";}
    protected void buildPanel(){
        resetButton("team_bar");
        panel.section("DISPLAY");
        int visible=panel.row();
        buttonList.add(new NeutralButton(0,panel.columnX(2,0),visible,panel.columnWidth(2),BedrockTokens.CONTROL_H,"Team Bar: "+(UtilitySettings.teamBar?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(3,panel.columnX(2,1),visible,panel.columnWidth(2),BedrockTokens.CONTROL_H,"Scoreboard: "+(UtilitySettings.teamBarHideScoreboard?"Hidden":"Visible"),false));
        buttonList.add(new ChoiceButton(this,4,panel.left(),panel.choice(),panel.width(),"Above the clock",new String[]{"None","Map name","Game mode"},UtilitySettings.teamBarHeader,v->UtilitySettings.teamBarHeader=v));
        panel.section("TEAM CARDS");
        buttonList.add(new ChoiceButton(this,1,panel.left(),panel.choice(),panel.width(),"Player display",new String[]{"Heads","Numbers"},UtilitySettings.teamBarNumbers?1:0,v->UtilitySettings.teamBarNumbers=v==1));
        if(!UtilitySettings.teamBarNumbers)buttonList.add(new PresetSlider(8,panel.left(),panel.row(),panel.width(),"Heads per team: ","",PresetSlider.PLAYERS,UtilitySettings.teamBarPlayers,10,s->UtilitySettings.teamBarPlayers=s.getValueInt()));
        buttonList.add(new PresetSlider(2,panel.left(),panel.row(),panel.width(),"Size: ","%",PresetSlider.SIZE,UtilitySettings.teamBarSize,150,s->UtilitySettings.teamBarSize=s.getValueInt()));
        int opacity=panel.row();
        buttonList.add(new PresetSlider(6,panel.columnX(2,0),opacity,panel.columnWidth(2),"Background: ","%",PresetSlider.PERCENT,UtilitySettings.teamBarBackgroundOpacity,25,s->UtilitySettings.teamBarBackgroundOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(7,panel.columnX(2,1),opacity,panel.columnWidth(2),"Outline: ","%",PresetSlider.PERCENT,UtilitySettings.teamBarOutlineOpacity,25,s->UtilitySettings.teamBarOutlineOpacity=s.getValueInt()));
        panel.note("In chat, click a team card to open its roster.");
        back(5);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==2||b.id==6||b.id==7||b.id==8)return;
        if(b.id==5){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.teamBar=!UtilitySettings.teamBar;
        if(b.id==3)UtilitySettings.teamBarHideScoreboard=!UtilitySettings.teamBarHideScoreboard;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
}
