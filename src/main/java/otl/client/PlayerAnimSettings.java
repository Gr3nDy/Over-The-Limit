package otl.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Player Animation options. The emote and skateboard keys live in Controls. */
public final class PlayerAnimSettings extends SettingsScreen {
    private final GuiScreen parent;
    public PlayerAnimSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Player Animation";}
    protected String subtitle(){return "Emotes and the skateboard. Nothing changes until you use them.";}
    protected void buildPanel(){
        resetButton("player_animation");
        panel.section("ANIMATION");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Player Animation: "+(UtilitySettings.playerAnimation?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Emote camera: "+(UtilitySettings.emoteCamera?"ON":"OFF"),false));
        panel.section("KEYS");
        panel.note("Play Emote and Pull Out Skateboard are rebindable in Controls.");
        panel.note("Nothing changes until you press a key: idle players stay exactly vanilla.");
        panel.note("Emotes need you to stand still. The camera returns on a click, damage or walking.");
        back(2);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==2){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.playerAnimation=!UtilitySettings.playerAnimation;
        if(b.id==1)UtilitySettings.emoteCamera=!UtilitySettings.emoteCamera;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int mx,int my,int state){super.mouseReleased(mx,my,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
