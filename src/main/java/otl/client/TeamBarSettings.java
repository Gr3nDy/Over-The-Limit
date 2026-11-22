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
        panel.section("TEAM BAR");
        int visible=panel.row();
        buttonList.add(new NeutralButton(0,panel.columnX(2,0),visible,panel.columnWidth(2),20,"Team Bar: "+(UtilitySettings.teamBar?"ON":"OFF"),false));
        buttonList.add(new PresetSlider(2,panel.columnX(2,1),visible,panel.columnWidth(2),"Size: ","%",PresetSlider.SIZE,UtilitySettings.teamBarSize,150,s->UtilitySettings.teamBarSize=s.getValueInt()));
        int players=panel.row();
        buttonList.add(new ChoiceButton(this,1,panel.columnX(2,0),players,panel.columnWidth(2),"Players",new String[]{"Heads","Numbers"},UtilitySettings.teamBarNumbers?1:0,v->UtilitySettings.teamBarNumbers=v==1));
        buttonList.add(new NeutralButton(3,panel.columnX(2,1),players,panel.columnWidth(2),20,"Hide scoreboard: "+(UtilitySettings.teamBarHideScoreboard?"ON":"OFF"),false));
        buttonList.add(new ChoiceButton(this,4,panel.left(),panel.choice(),panel.width(),"Above clock",new String[]{"None","Map name","Gamemode"},UtilitySettings.teamBarHeader,v->UtilitySettings.teamBarHeader=v));
        int opacity=panel.row();
        buttonList.add(new PresetSlider(6,panel.columnX(2,0),opacity,panel.columnWidth(2),"Background: ","%",PresetSlider.PERCENT,UtilitySettings.teamBarBackgroundOpacity,25,s->UtilitySettings.teamBarBackgroundOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(7,panel.columnX(2,1),opacity,panel.columnWidth(2),"Outline: ","%",PresetSlider.PERCENT,UtilitySettings.teamBarOutlineOpacity,25,s->UtilitySettings.teamBarOutlineOpacity=s.getValueInt()));
        panel.note("Team names appear above the players. Scores appear below.");
        panel.note("Numbers shows how many players are on the team instead of their heads.");
        panel.note("Hover a card for the full team; click a head for profile, teleport, friend and message.");
        back(5);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==2||b.id==6||b.id==7)return;
        if(b.id==5){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.teamBar=!UtilitySettings.teamBar;
        if(b.id==3)UtilitySettings.teamBarHideScoreboard=!UtilitySettings.teamBarHideScoreboard;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
}
