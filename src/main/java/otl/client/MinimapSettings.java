package otl.client;

import net.minecraft.client.gui.*;

public final class MinimapSettings extends SettingsScreen {
    private final GuiScreen parent;private int page;
    public MinimapSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Isometric Minimap";}
    protected String subtitle(){return page==0?"Square viewport. Static fits whole map; follow stays fixed.":"Map terrain and markers.";}
    protected void buildPanel(){
        resetButton("minimap");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Minimap: "+(UtilitySettings.minimap?"ON":"OFF"),false));
        int tabs=panel.row(18);
        buttonList.add(new NeutralButton(20,panel.columnX(2,0),tabs,panel.columnWidth(2),18,"General",page==0));
        buttonList.add(new NeutralButton(21,panel.columnX(2,1),tabs,panel.columnWidth(2),18,"Display",page==1));
        if(page==0){
            panel.section("VIEWPORT");
            buttonList.add(new PresetSlider(1,panel.left(),panel.row(),panel.width(),"Viewport size: ","%",PresetSlider.SIZE,UtilitySettings.minimapSize,100,s->UtilitySettings.minimapSize=s.getValueInt()));
            buttonList.add(new PresetSlider(2,panel.left(),panel.row(),panel.width(),"Background: ","%",PresetSlider.PERCENT,UtilitySettings.minimapOpacity,25,s->UtilitySettings.minimapOpacity=s.getValueInt()));
            buttonList.add(new PresetSlider(22,panel.left(),panel.row(),panel.width(),"Zoom level: ","",PresetSlider.ZOOM,UtilitySettings.minimapZoom,2,s->UtilitySettings.minimapZoom=s.getValueInt()));
            buttonList.add(new PresetSlider(23,panel.left(),panel.row(),panel.width(),"Alt Zoom level: ","",PresetSlider.ZOOM,UtilitySettings.minimapAltZoom,4,s->UtilitySettings.minimapAltZoom=s.getValueInt()));
            buttonList.add(new ChoiceButton(this,5,panel.left(),panel.choice(),panel.width(),"Mode",new String[]{"Follow Player","Static"},UtilitySettings.minimapStatic?1:0,v->UtilitySettings.minimapStatic=v==1));
            buttonList.add(new NeutralButton(7,panel.left(),panel.row(),panel.width(),20,"Rotate With Player: "+(UtilitySettings.minimapRotate?"ON":"OFF"),false));
        }else{
            panel.section("MAP DISPLAY");
            buttonList.add(new NeutralButton(6,panel.left(),panel.row(),panel.width(),20,"Player Cutaway: "+(UtilitySettings.minimapCutaway?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(8,panel.left(),panel.row(),panel.width(),20,"Cardinal Directions: "+(UtilitySettings.minimapCardinals?"ON":"OFF"),false));
            buttonList.add(new NeutralButton(9,panel.left(),panel.row(),panel.width(),20,"Show Coordinates: "+(UtilitySettings.minimapCoordinates?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(10,panel.left(),panel.row(),panel.width(),"Map terrain: ","%",PresetSlider.PERCENT,UtilitySettings.minimapLargeOpacity,25,s->UtilitySettings.minimapLargeOpacity=s.getValueInt()));
            buttonList.add(new NeutralButton(11,panel.left(),panel.row(),panel.width(),20,"Border: "+(UtilitySettings.minimapBorder?"ON":"OFF"),false));
            buttonList.add(new PresetSlider(12,panel.left(),panel.row(),panel.width(),"Head size: ","%",PresetSlider.SIZE,UtilitySettings.minimapHeadSize,125,s->UtilitySettings.minimapHeadSize=s.getValueInt()));
            buttonList.add(new PresetSlider(13,panel.left(),panel.row(),panel.width(),"Large background: ","%",PresetSlider.PERCENT,UtilitySettings.minimapLargeBackground,25,s->UtilitySettings.minimapLargeBackground=s.getValueInt()));
        }
        pin(new NeutralButton(3,actionX(),footerY(),110,20,"Reset HUD position",false));
        back(4);
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
        if(button.id==1||button.id==2||button.id==10||button.id==12||button.id==13||button.id==22||button.id==23)return;
        UtilitySettings.save();initGui();
    }
    protected void mouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);UtilitySettings.save();}
    public void onGuiClosed(){UtilitySettings.save();}
}
