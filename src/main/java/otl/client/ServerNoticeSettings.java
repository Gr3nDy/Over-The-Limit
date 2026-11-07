package otl.client;

import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;

final class ServerNoticeSettings extends GuiScreen {
    private final GuiScreen parent;private int x,y,w;
    ServerNoticeSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-220)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+46,w-28,20,"Server Notices: "+(UtilitySettings.nonPlayerChat?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(1,x+14,y+72,w-28,20,"Mode: "+UtilitySettings.noticeMode.label,false));
        buttonList.add(new GuiSlider(2,x+14,y+98,w-28,20,"Panel/border opacity: ","%",0,100,UtilitySettings.noticeOpacity,false,true,s->UtilitySettings.noticeOpacity=s.getValueInt()));
        buttonList.add(new GuiSlider(5,x+14,y+124,w-103,20,"Size: ","%",50,150,UtilitySettings.noticeSize,false,true,s->UtilitySettings.noticeSize=s.getValueInt()));
        buttonList.add(new NeutralButton(6,x+w-85,y+124,71,20,"Reset size",false));
        buttonList.add(new NeutralButton(3,x+14,y+187,110,20,"Clear notice history",false));
        buttonList.add(new NeutralButton(4,x+w-94,y+187,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==2||b.id==5)return;
        if(b.id==4){mc.displayGuiScreen(parent);return;}
        if(b.id==0)UtilitySettings.nonPlayerChat=!UtilitySettings.nonPlayerChat;
        if(b.id==1)UtilitySettings.noticeMode=ServerNoticeRules.Mode.values()[(UtilitySettings.noticeMode.ordinal()+1)%ServerNoticeRules.Mode.values().length];
        if(b.id==6)UtilitySettings.noticeSize=100;
        if(b.id==3)ServerNotices.history.clear();UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x,y,x+w,y+220,0xF0202020);
        drawString(fontRendererObj,"Server Notices",x+14,y+12,0xFFFFFFFF);
        drawString(fontRendererObj,"Selected notices leave normal chat. Players stay.",x+14,y+29,0xFFAAAAAA);
        drawString(fontRendererObj,"10-second display; 100 session-only messages.",x+14,y+158,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
