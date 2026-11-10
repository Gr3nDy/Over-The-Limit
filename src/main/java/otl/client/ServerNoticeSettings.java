package otl.client;

import net.minecraft.client.gui.*;

public final class ServerNoticeSettings extends GuiScreen {
    private final GuiScreen parent;private int x,y,w;
    public ServerNoticeSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-232)/2;buttonList.clear();
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"server_notices"));
        buttonList.add(new NeutralButton(0,x+14,y+46,w-28,20,"Server Notices: UNAVAILABLE",false));
        BedrockUi.section(fontRendererObj,"DISPLAY",x+14,y+72,w-28);
        buttonList.add(new NeutralButton(1,x+14,y+86,w-28,20,"Mode: "+UtilitySettings.noticeMode.label,false));
        buttonList.add(new PresetSlider(2,x+14,y+110,w-28,"Panel/border opacity: ","%",PresetSlider.PERCENT,UtilitySettings.noticeOpacity,25,s->UtilitySettings.noticeOpacity=s.getValueInt()));
        buttonList.add(new PresetSlider(5,x+14,y+134,w-28,"Size: ","%",PresetSlider.SIZE,UtilitySettings.noticeSize,100,s->UtilitySettings.noticeSize=s.getValueInt()));
        buttonList.add(new NeutralButton(3,x+14,y+194,110,20,"Clear notice history",false));
        buttonList.add(new NeutralButton(4,x+w-94,y+194,80,20,"Back",false));
        for(GuiButton button:buttonList)if(button.id!=4&&button.id!=3&&button.id!=90)button.enabled=false;
    }
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==3)ServerNotices.history.clear();UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,232);
        drawString(fontRendererObj,"Server Notices",x+14,y+12,0xFFFFFFFF);
        drawString(fontRendererObj,"This module is temporarily unavailable.",x+14,y+29,0xFFAAAAAA);
        drawString(fontRendererObj,"10-second display; 100 session-only messages.",x+14,y+166,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
