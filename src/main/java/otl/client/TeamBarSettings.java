package otl.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Team Bar options: how many heads per side, their scale, and whether the boss bar stays. */
public final class TeamBarSettings extends SettingsScreen {
    private final GuiScreen parent;
    public TeamBarSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Team Bar";}
    protected String subtitle(){return "Top-centre heads with the match clock.";}
    protected void buildPanel(){
        resetButton("team_bar");
        panel.section("TEAM BAR");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Team Bar: "+(UtilitySettings.teamBar?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Hide bossbar: "+(UtilitySettings.teamBarHideBoss?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(8,panel.left(),panel.row(),panel.width(),20,"Hide while player list is open: "+(UtilitySettings.teamBarHideOnTab?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(5,panel.left(),panel.row(),panel.width(),20,"Show in observer: "+(UtilitySettings.teamBarObserver?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(6,panel.left(),panel.row(),panel.width(),20,"Team colour line: "+(UtilitySettings.teamBarTeamColor?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(9,panel.left(),panel.row(),panel.width(),20,"Objectives: "+(UtilitySettings.teamBarObjectives?"ON":"OFF"),false));
        buttonList.add(new PresetSlider(2,panel.left(),panel.row(),panel.width(),"Players shown: ","",PresetSlider.PLAYERS,UtilitySettings.teamBarPlayers,10,s->UtilitySettings.teamBarPlayers=s.getValueInt()));
        buttonList.add(new PresetSlider(3,panel.left(),panel.row(),panel.width(),"Size: ","%",PresetSlider.SIZE,UtilitySettings.teamBarSize,150,s->UtilitySettings.teamBarSize=s.getValueInt()));
        buttonList.add(new PresetSlider(7,panel.left(),panel.row(),panel.width(),"Dead head opacity: ","%",new int[]{25,50,75,100},UtilitySettings.teamBarDeadOpacity,50,s->UtilitySettings.teamBarDeadOpacity=s.getValueInt()));
        panel.note("Heads shrink for every five more players shown. Ten per row; extras wrap below. Dead players fade until respawn.");
        panel.note("Objectives shows PGM's wools, goal states and scores under the clock.");
        panel.note("Open chat to move the pointer over a head and see the player's name.");
        back(4);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==2||b.id==3||b.id==7)return; // sliders persist on mouse release
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.teamBar=!UtilitySettings.teamBar;
        if(b.id==1)UtilitySettings.teamBarHideBoss=!UtilitySettings.teamBarHideBoss;
        if(b.id==5)UtilitySettings.teamBarObserver=!UtilitySettings.teamBarObserver;
        if(b.id==6)UtilitySettings.teamBarTeamColor=!UtilitySettings.teamBarTeamColor;
        if(b.id==8)UtilitySettings.teamBarHideOnTab=!UtilitySettings.teamBarHideOnTab;
        if(b.id==9)UtilitySettings.teamBarObjectives=!UtilitySettings.teamBarObjectives;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
}
