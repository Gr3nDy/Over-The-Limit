package otl.client;

import net.minecraft.client.gui.*;

public final class HeightLimitSettings extends SettingsScreen {
    private final GuiScreen parent;
    public HeightLimitSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Height Limit Overlay";}
    protected String subtitle(){return "This module is temporarily unavailable.";}
    protected void buildPanel(){
        resetButton("height_limit_overlay");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Height Limit Overlay: LOCKED",false));
        buttonList.add(new NeutralButton(6,panel.left(),panel.row(),panel.width(),20,"Height Limit Indicator: "+(UtilitySettings.heightLimitIndicator?"ON":"OFF"),false));
        panel.section("INDICATOR");
        buttonList.add(new PresetSlider(1,panel.left(),panel.row(),panel.width(),"Darkness opacity: ","%",PresetSlider.PERCENT,UtilitySettings.heightLimitOpacity,25,s->UtilitySettings.heightLimitOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(3,panel.left(),panel.row(),panel.width(),"Indicator fade: "," seconds",PresetSlider.SHORT_SECONDS,UtilitySettings.heightLimitIndicatorSeconds,4,s->UtilitySettings.heightLimitIndicatorSeconds=s.getValueInt()));
        buttonList.add(new ChoiceButton(this,4,panel.left(),panel.choice(),panel.width(),"Overlay faces",new String[]{"All","Top only"},UtilitySettings.heightLimitTopOnly?1:0,v->UtilitySettings.heightLimitTopOnly=v==1));
        buttonList.add(new PresetSlider(5,panel.left(),panel.row(),panel.width(),"Indicator size: ","%",PresetSlider.SIZE,UtilitySettings.heightLimitSize,75,s->UtilitySettings.heightLimitSize=s.getValueInt()));
        back(2);
        for(GuiButton button:buttonList)if(button.id!=2&&button.id!=90)button.enabled=false;
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==1||b.id==3||b.id==5)return;
        if(b.id==2){mc.displayGuiScreen(parent);return;}
        if(b.id==6)UtilitySettings.heightLimitIndicator=!UtilitySettings.heightLimitIndicator;
        if(b.id==4)UtilitySettings.heightLimitTopOnly=!UtilitySettings.heightLimitTopOnly;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
