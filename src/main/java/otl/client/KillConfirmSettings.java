package otl.client;

import net.minecraft.client.gui.*;

public final class KillConfirmSettings extends GuiScreen {
    private final GuiScreen parent;private int x,y,w;
    public KillConfirmSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-278)/2;buttonList.clear();
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"kill_confirm"));
        buttonList.add(new NeutralButton(0,x+14,y+42,w-28,20,"Kill Confirm: "+(UtilitySettings.killConfirm?"ON":"OFF"),false));
        BedrockUi.section(fontRendererObj,"APPEARANCE",x+14,y+72,w-28);
        buttonList.add(new PresetSlider(1,x+14,y+88,w-28,"Size: ","%",PresetSlider.SIZE,UtilitySettings.killConfirmSize,75,s->UtilitySettings.killConfirmSize=s.getValueInt()));
        buttonList.add(new PresetSlider(3,x+14,y+112,w-28,"Background opacity: ","%",PresetSlider.PERCENT,UtilitySettings.killConfirmOpacity,25,s->UtilitySettings.killConfirmOpacity=s.getValueInt()));
        BedrockUi.section(fontRendererObj,"CHAT & FEEDBACK",x+14,y+142,w-28);
        buttonList.add(new NeutralButton(6,x+14,y+158,w-28,20,"Hide rewards in chat: "+(UtilitySettings.hideKillConfirmChat?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(7,x+14,y+182,w-28,20,"Kill/assist sounds: "+(UtilitySettings.killConfirmSounds?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(8,x+14,y+206,w-28,20,"Total EXP, Kills & Assists: "+(UtilitySettings.totalExpKills?"ON":"OFF"),false));
        buttonList.add(new NeutralButton(9,x+14,y+230,w-28,20,"Kill icon: "+(UtilitySettings.killConfirmPlayerHead?"Player Head":"Skull"),false));
        buttonList.add(new NeutralButton(4,x+14,y+256,110,20,"Reset position",false));
        buttonList.add(new NeutralButton(5,x+w-94,y+256,80,20,"Back",false));
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
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,278);
        drawString(fontRendererObj,"Kill Confirm",x+14,y+12,0xFFFFFFFF);
        super.drawScreen(mx,my,partial);
    }
}
