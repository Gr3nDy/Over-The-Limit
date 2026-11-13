package otl.client;

import net.minecraft.client.gui.*;

public final class KillConfirmSettings extends SettingsScreen {
    private final GuiScreen parent;
    public KillConfirmSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Kill Confirm";}
    protected void buildPanel(){
        resetButton("kill_confirm");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Kill Confirm: "+(UtilitySettings.killConfirm?"ON":"OFF"),false));
        panel.section("APPEARANCE");
        buttonList.add(new PresetSlider(1,panel.left(),panel.row(),panel.width(),"Size: ","%",PresetSlider.SIZE,UtilitySettings.killConfirmSize,75,s->UtilitySettings.killConfirmSize=s.getValueInt()));
        buttonList.add(new PresetSlider(3,panel.left(),panel.row(),panel.width(),"Background opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killConfirmOpacity,25,s->UtilitySettings.killConfirmOpacity=s.getValueInt()));
        buttonList.add(new ChoiceButton(this,9,panel.left(),panel.choice(),panel.width(),"Kill icon",new String[]{"Skull","Player Head"},UtilitySettings.killConfirmPlayerHead?1:0,v->UtilitySettings.killConfirmPlayerHead=v==1));
        panel.section("CHAT & FEEDBACK");
        buttonList.add(new NeutralButton(6,panel.left(),panel.row(),panel.width(),20,"Hide rewards in chat: "+(UtilitySettings.hideKillConfirmChat?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(7,panel.left(),panel.row(),panel.width(),20,"Kill/assist sounds: "+(UtilitySettings.killConfirmSounds?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(8,panel.left(),panel.row(),panel.width(),20,"Total EXP, Kills & Assists: "+(UtilitySettings.totalExpKills?"ON":"OFF"),false));
        pin(new NeutralButton(4,actionX(),footerY(),110,20,"Reset position",false));
        back(5);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==1||b.id==3)return;
        if(b.id==5){mc.displayGuiScreen(parent);return;}
        if(b.id==0){UtilitySettings.killConfirm=!UtilitySettings.killConfirm;KillConfirm.state.clear();}
        if(b.id==4)UtilitySettings.hudPositions.remove(HudLayout.CONFIRM);
        if(b.id==6)UtilitySettings.hideKillConfirmChat=!UtilitySettings.hideKillConfirmChat;
        if(b.id==7)UtilitySettings.killConfirmSounds=!UtilitySettings.killConfirmSounds;
        if(b.id==8)UtilitySettings.totalExpKills=!UtilitySettings.totalExpKills;
        if(b.id==9)UtilitySettings.killConfirmPlayerHead=!UtilitySettings.killConfirmPlayerHead;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
