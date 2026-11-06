package otl.client;

import net.minecraft.client.gui.*;

final class PreferedHandSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,h;
    PreferedHandSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);h=Math.min(220,height-20);x=(width-w)/2;y=(height-h)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+64,w-28,19,"Main Hand: "+(UtilitySettings.mainHandLeft?"Left":"Right"),false));
        buttonList.add(new NeutralButton(1,x+14,y+86,w-28,19,"Type: "+UtilitySettings.handType.label,false));
        buttonList.add(new NeutralButton(2,x+w-94,y+h-27,80,19,"Back",false));
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==2){mc.displayGuiScreen(parent);return;}
        if(button.id==0)UtilitySettings.mainHandLeft=!UtilitySettings.mainHandLeft;
        if(button.id==1)UtilitySettings.handType=UtilitySettings.handType.next();
        UtilitySettings.save();initGui();
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x-1,y-1,x+w+1,y+h+1,0xFF555555);drawRect(x,y,x+w,y+h,0xF0181818);
        drawString(fontRendererObj,"Prefered Hand",x+14,y+12,0xFFEAEAEA);
        drawString(fontRendererObj,"Choose the hand and which held items it applies to.",x+14,y+27,0xFFAAAAAA);
        drawString(fontRendererObj,"Other types use the opposite hand.",x+14,y+117,0xFFAAAAAA);
        drawString(fontRendererObj,"Two-handed maps stay readable.",x+14,y+130,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
