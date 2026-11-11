package otl.client;

import net.minecraft.client.gui.*;

public final class HeightLimitSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w;
    public HeightLimitSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-248)/2;buttonList.clear();
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"height_limit_overlay"));
        buttonList.add(new NeutralButton(0,x+14,y+46,w-28,20,"Height Limit Overlay: LOCKED",false));
        buttonList.add(new NeutralButton(6,x+14,y+70,w-28,20,"Height Limit Indicator: "+(UtilitySettings.heightLimitIndicator?"ON":"OFF"),false));
        BedrockUi.section(fontRendererObj,"INDICATOR",x+14,y+96,w-28);
        buttonList.add(new PresetSlider(1,x+14,y+110,w-28,"Darkness opacity: ","%",PresetSlider.PERCENT,UtilitySettings.heightLimitOpacity,25,s->UtilitySettings.heightLimitOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(3,x+14,y+134,w-28,"Indicator fade: "," seconds",PresetSlider.SHORT_SECONDS,UtilitySettings.heightLimitIndicatorSeconds,4,s->UtilitySettings.heightLimitIndicatorSeconds=s.getValueInt()));
        buttonList.add(new NeutralButton(4,x+14,y+158,w-28,20,"Overlay faces: "+(UtilitySettings.heightLimitTopOnly?"Top only":"All"),false));
        buttonList.add(new PresetSlider(5,x+14,y+182,w-28,"Indicator size: ","%",PresetSlider.SIZE,UtilitySettings.heightLimitSize,75,s->UtilitySettings.heightLimitSize=s.getValueInt()));
        buttonList.add(new NeutralButton(2,x+w-94,y+220,80,20,"Back",false));
        for(GuiButton button:buttonList)if(button.id!=2&&button.id!=90)button.enabled=false;
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==1||b.id==3||b.id==5)return;
        if(b.id==2){mc.displayGuiScreen(parent);return;}
        if(b.id==6){UtilitySettings.heightLimitIndicator=!UtilitySettings.heightLimitIndicator;UtilitySettings.save();initGui();}
        if(b.id==4){UtilitySettings.heightLimitTopOnly=!UtilitySettings.heightLimitTopOnly;UtilitySettings.save();initGui();}
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,248);
        drawString(fontRendererObj,"Height Limit Overlay",x+14,y+12,0xFFFFFFFF);
        drawString(fontRendererObj,"This module is temporarily unavailable.",x+14,y+29,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
