package otl.client;

import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.config.GuiSlider;

final class KillConfirmSettings extends GuiScreen {
    private final GuiScreen parent;private int x,y,w;
    KillConfirmSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-228)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+42,w-28,20,"Kill Confirm: "+(UtilitySettings.killConfirm?"ON":"OFF"),false));
        buttonList.add(new GuiSlider(1,x+14,y+70,w-103,20,"Size: ","%",50,150,UtilitySettings.killConfirmSize,false,true,s->UtilitySettings.killConfirmSize=s.getValueInt()));
        buttonList.add(new NeutralButton(2,x+w-85,y+70,71,20,"Reset size",false));
        buttonList.add(new GuiSlider(3,x+14,y+98,w-28,20,"Background opacity: ","%",0,100,UtilitySettings.killConfirmOpacity,false,true,s->UtilitySettings.killConfirmOpacity=s.getValueInt()));
        buttonList.add(new NeutralButton(6,x+14,y+126,w-28,20,"Hide rewards in chat: "+(UtilitySettings.hideKillConfirmChat?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(7,x+14,y+154,w-28,20,"Kill/assist sounds: "+(UtilitySettings.killConfirmSounds?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(4,x+14,y+193,110,20,"Reset position",false));
        buttonList.add(new NeutralButton(5,x+w-94,y+193,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==1||b.id==3)return;
        if(b.id==5){mc.displayGuiScreen(parent);return;}
        if(b.id==0){UtilitySettings.killConfirm=!UtilitySettings.killConfirm;KillConfirm.state.clear();}
        if(b.id==2)UtilitySettings.killConfirmSize=75;
        if(b.id==4)UtilitySettings.hudPositions.remove(HudLayout.CONFIRM);
        if(b.id==6)UtilitySettings.hideKillConfirmChat=!UtilitySettings.hideKillConfirmChat;
        if(b.id==7)UtilitySettings.killConfirmSounds=!UtilitySettings.killConfirmSounds;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x,y,x+w,y+228,0xF0202020);
        drawString(fontRendererObj,"Kill Confirm",x+14,y+12,0xFFFFFFFF);
        drawString(fontRendererObj,"Kill and assist EXP stacks until death.",x+14,y+185,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
