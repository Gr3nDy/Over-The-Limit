package otl.client;

import net.minecraft.client.gui.*;

public final class MinimapSettings extends GuiScreen {
    private final GuiScreen parent;private int x,y,w,page;
    public MinimapSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    private int panel(){return page==1?256:232;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);x=(width-w)/2;y=(height-panel())/2;buttonList.clear();
        buttonList.add(new ModuleReset(90,x+w-27,y+6,"minimap"));
        buttonList.add(new NeutralButton(0,x+14,y+42,w-28,20,"Minimap: "+(UtilitySettings.minimap?"ON":"OFF"),false));
        int tab=(w-32)/2;
        buttonList.add(new NeutralButton(20,x+14,y+66,tab,18,"General",page==0));
        buttonList.add(new NeutralButton(21,x+18+tab,y+66,tab,18,"Display",page==1));
        if(page==0){
            BedrockUi.section(fontRendererObj,"VIEWPORT",x+14,y+88,w-28);
            buttonList.add(new PresetSlider(1,x+14,y+102,w-28,"Viewport size: ","%",PresetSlider.SIZE,UtilitySettings.minimapSize,100,s->UtilitySettings.minimapSize=s.getValueInt()));
            buttonList.add(new PresetSlider(2,x+14,y+126,w-28,"Background: ","%",PresetSlider.PERCENT,UtilitySettings.minimapOpacity,25,s->UtilitySettings.minimapOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(5,x+14,y+150,w-28,20,"Mode: "+(UtilitySettings.minimapStatic?"Static":"Follow Player"),false));
            buttonList.add(new NeutralButton(7,x+14,y+174,w-28,20,"Rotate With Player: "+(UtilitySettings.minimapRotate?"ON":"OFF"),false));
        }else{
            BedrockUi.section(fontRendererObj,"MAP DISPLAY",x+14,y+88,w-28);
            buttonList.add(new NeutralButton(6,x+14,y+102,w-28,20,"Player Cutaway: "+(UtilitySettings.minimapCutaway?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(8,x+14,y+126,w-28,20,"Cardinal Directions: "+(UtilitySettings.minimapCardinals?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(9,x+14,y+150,w-28,20,"Show Coordinates: "+(UtilitySettings.minimapCoordinates?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(10,x+14,y+174,w-28,"Large map opacity: ","%",PresetSlider.PERCENT,UtilitySettings.minimapLargeOpacity,25,s->UtilitySettings.minimapLargeOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(11,x+14,y+198,w-28,20,"Border: "+(UtilitySettings.minimapBorder?"ON":"OFF"),false));
        }
        int footer=y+panel()-28;
        buttonList.add(new NeutralButton(3,x+14,footer,110,20,"Reset HUD position",false));
        buttonList.add(new NeutralButton(4,x+w-94,footer,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==90){((ModuleReset)button).reset();initGui();return;}
        if(button.id==0)UtilitySettings.minimap=!UtilitySettings.minimap;
        if(button.id==3)UtilitySettings.hudPositions.remove(HudLayout.MINIMAP);
        if(button.id==5)UtilitySettings.minimapStatic=!UtilitySettings.minimapStatic;
        if(button.id==6)UtilitySettings.minimapCutaway=!UtilitySettings.minimapCutaway;
        if(button.id==7)UtilitySettings.minimapRotate=!UtilitySettings.minimapRotate;
        if(button.id==8)UtilitySettings.minimapCardinals=!UtilitySettings.minimapCardinals;
        if(button.id==9)UtilitySettings.minimapCoordinates=!UtilitySettings.minimapCoordinates;
        if(button.id==11)UtilitySettings.minimapBorder=!UtilitySettings.minimapBorder;
        if(button.id==20||button.id==21){page=button.id-20;initGui();return;}
        if(button.id==4){mc.displayGuiScreen(parent);return;}
        if(button.id==1||button.id==2||button.id==10)return;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,panel());
        drawString(fontRendererObj,"Isometric Minimap",x+14,y+10,0xFFFFFFFF);
        drawString(fontRendererObj,"Square viewport. Static fits whole map; follow stays fixed.",x+14,y+27,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
