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
        panel.section("GENERAL");
        int visible=panel.row();
        buttonList.add(new NeutralButton(0,panel.columnX(2,0),visible,panel.columnWidth(2),20,"Team Bar: "+(UtilitySettings.teamBar?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(5,panel.columnX(2,1),visible,panel.columnWidth(2),20,"In observer: "+(UtilitySettings.teamBarObserver?"ON":"OFF"),false));
        int quiet=panel.row();
        buttonList.add(new NeutralButton(1,panel.columnX(2,0),quiet,panel.columnWidth(2),20,"Hide bossbar: "+(UtilitySettings.teamBarHideBoss?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(8,panel.columnX(2,1),quiet,panel.columnWidth(2),20,"Hide on player list: "+(UtilitySettings.teamBarHideOnTab?"ON":"OFF"),false));
        panel.section("HEADS");
        int sizing=panel.row();
        buttonList.add(new PresetSlider(2,panel.columnX(2,0),sizing,panel.columnWidth(2),"Players: ","",PresetSlider.PLAYERS,UtilitySettings.teamBarPlayers,10,s->UtilitySettings.teamBarPlayers=s.getValueInt()));
        buttonList.add(new PresetSlider(3,panel.columnX(2,1),sizing,panel.columnWidth(2),"Size: ","%",PresetSlider.SIZE,UtilitySettings.teamBarSize,150,s->UtilitySettings.teamBarSize=s.getValueInt()));
        int fading=panel.row();
        buttonList.add(new PresetSlider(7,panel.columnX(2,0),fading,panel.columnWidth(2),"Dead opacity: ","%",new int[]{25,50,75,100},UtilitySettings.teamBarDeadOpacity,50,s->UtilitySettings.teamBarDeadOpacity=s.getValueInt()));
        buttonList.add(new NeutralButton(6,panel.columnX(2,1),fading,panel.columnWidth(2),20,"Team colour line: "+(UtilitySettings.teamBarTeamColor?"ON":"OFF"),false));
        panel.note("Ten heads per row; extras wrap below. Dead players fade until respawn.");
        panel.section("OBJECTIVES");
        int shown=panel.row();
        buttonList.add(new NeutralButton(9,panel.columnX(2,0),shown,panel.columnWidth(2),20,"Objectives: "+(UtilitySettings.teamBarObjectives?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(10,panel.columnX(2,1),shown,panel.columnWidth(2),20,"Compact: "+(UtilitySettings.teamBarObjectivesCompact?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(11,panel.left(),panel.row(),panel.width(),20,"Control point tracking: "+(UtilitySettings.teamBarControlPoints?"ON":"OFF"),false));
        buttonList.add(new ChoiceButton(this,12,panel.left(),panel.choice(),panel.width(),"Score display",new String[]{"Text","Bar (horizontal)","Bar (vertical)"},UtilitySettings.teamBarScoreStyle,v->UtilitySettings.teamBarScoreStyle=v));
        buttonList.add(new ChoiceButton(this,13,panel.left(),panel.choice(),panel.width(),"Above clock",new String[]{"None","Map name","Gamemode"},UtilitySettings.teamBarHeader,v->UtilitySettings.teamBarHeader=v));
        panel.note("Compact shows markers only, beside the clock. Control points stay with their owner until PGM confirms the capture.");
        panel.note("Score display styles symbolic scores (circle/square/heart maps). Above clock shows the map name or gamemode in bold; hover it for the other value and the author.");
        panel.note("In a match, hover a head for its name or click it for profile, teleport, friend and message actions.");
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
        if(b.id==10)UtilitySettings.teamBarObjectivesCompact=!UtilitySettings.teamBarObjectivesCompact;
        if(b.id==11)UtilitySettings.teamBarControlPoints=!UtilitySettings.teamBarControlPoints;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
}
