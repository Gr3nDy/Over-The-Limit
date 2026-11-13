package otl.client;

import net.minecraft.client.gui.*;

/** Settings screen for the Map Preview module. */
public final class MapPreviewSettings extends SettingsScreen {
    private final GuiScreen parent;
    public MapPreviewSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Map Preview";}
    protected String subtitle(){return "Map images in vote books and gold chat links.";}
    protected void buildPanel(){
        resetButton("map_preview");
        buttonList.add(new NeutralButton(0,panel.left(),panel.row(),panel.width(),20,"Map Preview: "+state(UtilitySettings.mapPreview),false));
        buttonList.add(new NeutralButton(3,panel.left(),panel.row(),panel.width(),20,"Preview on command: "+state(UtilitySettings.previewOnCommand),false));
        buttonList.add(new ChoiceButton(this,4,panel.left(),panel.choice(),panel.width(),"Preview size",new String[]{"Small","Large"},UtilitySettings.largeMapPreview?1:0,v->UtilitySettings.largeMapPreview=v==1));
        buttonList.add(new NeutralButton(1,panel.left(),panel.row(),panel.width(),20,"Open Map Gallery",false));
        back(2);
    }
    private String state(boolean on){return on?"ON":"OFF";}
    protected void actionPerformed(GuiButton b){
        if(b.id==90){((ModuleReset)b).reset();initGui();return;}
        if(b.id==0)UtilitySettings.mapPreview=!UtilitySettings.mapPreview;
        if(b.id==3)UtilitySettings.previewOnCommand=!UtilitySettings.previewOnCommand;
        if(b.id==4)UtilitySettings.largeMapPreview=!UtilitySettings.largeMapPreview;
        if(b.id==1){mc.displayGuiScreen(new MapGalleryScreen(this));return;}
        if(b.id==2){mc.displayGuiScreen(parent);return;}
        UtilitySettings.save();initGui();
    }
}
