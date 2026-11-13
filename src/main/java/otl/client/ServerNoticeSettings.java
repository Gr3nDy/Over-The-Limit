package otl.client;

import net.minecraft.client.gui.*;

public final class ServerNoticeSettings extends SettingsScreen {
    private final GuiScreen parent;
    public ServerNoticeSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Server Notices";}
    protected String subtitle(){return "This module is temporarily unavailable.";}
    protected void buildPanel(){
        resetButton("server_notices");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Server Notices: LOCKED",false));
        buttonList.add(new ChoiceButton(this,1,panel.left(),panel.choice(),panel.width(),"Mode",java.util.Arrays.stream(ServerNoticeRules.Mode.values()).map(v->v.label).toArray(String[]::new),UtilitySettings.noticeMode.ordinal(),v->UtilitySettings.noticeMode=ServerNoticeRules.Mode.values()[v]));
        buttonList.add(new PresetSlider(2,panel.left(),panel.row(),panel.width(),"Panel/border opacity: ","%",PresetSlider.PERCENT,UtilitySettings.noticeOpacity,25,s->UtilitySettings.noticeOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(5,panel.left(),panel.row(),panel.width(),"Size: ","%",PresetSlider.SIZE,UtilitySettings.noticeSize,100,s->UtilitySettings.noticeSize=s.getValueInt()));
        panel.note("10-second display; 100 session-only messages.");
        pin(new NeutralButton(3,actionX(),footerY(),110,20,"Clear notice history",false));
        back(4);
        for(GuiButton button:buttonList)if(button.id!=4&&button.id!=3&&button.id!=90)button.enabled=false;
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==3){ServerNotices.history.clear();UtilitySettings.save();initGui();}
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
