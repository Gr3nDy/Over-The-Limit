package otl.client;

import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;

final class HeightLimitSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w;
    HeightLimitSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-236)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+46,w-28,20,"Height Limit Overlay: DISABLED",false));
        buttonList.add(new NeutralButton(6,x+14,y+72,w-28,20,"Height Limit Indicator: "+(UtilitySettings.heightLimitIndicator?"ON":"OFF"),false));
        buttonList.add(new GuiSlider(1,x+14,y+98,w-28,20,"Darkness opacity: ","%",0,100,UtilitySettings.heightLimitOpacity,false,true,s->UtilitySettings.heightLimitOpacity=s.getValueInt()));
        buttonList.add(new GuiSlider(3,x+14,y+124,w-28,20,"Indicator fade: "," seconds",1,10,UtilitySettings.heightLimitIndicatorSeconds,false,true,s->UtilitySettings.heightLimitIndicatorSeconds=s.getValueInt()));
        buttonList.add(new NeutralButton(4,x+14,y+150,w-28,20,"Overlay faces: "+(UtilitySettings.heightLimitTopOnly?"Top only":"All"),false));
        buttonList.add(new GuiSlider(5,x+14,y+176,w-28,20,"Indicator size: ","%",50,150,UtilitySettings.heightLimitSize,false,true,s->UtilitySettings.heightLimitSize=s.getValueInt()));
        buttonList.add(new NeutralButton(2,x+w-94,y+204,80,20,"Back",false));
        for(GuiButton button:buttonList)if(button.id!=2)button.enabled=false;
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==1||b.id==3||b.id==5)return;
        if(b.id==2){mc.displayGuiScreen(parent);return;}
        if(b.id==6){UtilitySettings.heightLimitIndicator=!UtilitySettings.heightLimitIndicator;UtilitySettings.save();initGui();}
        if(b.id==4){UtilitySettings.heightLimitTopOnly=!UtilitySettings.heightLimitTopOnly;UtilitySettings.save();initGui();}
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x,y,x+w,y+236,0xF0202020);
        drawString(fontRendererObj,"Height Limit Overlay",x+14,y+12,0xFFFFFFFF);
        drawString(fontRendererObj,"This module is temporarily unavailable.",x+14,y+29,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
