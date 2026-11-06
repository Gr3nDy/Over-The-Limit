package otl.client;

import net.minecraft.client.gui.*;

/** Settings screen for the Map Preview module. */
final class MapPreviewSettings extends GuiScreen {
    private final GuiScreen parent;
    private int x,y,w,h;
    MapPreviewSettings(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();w=Math.min(350,width-24);h=Math.min(220,height-20);x=(width-w)/2;y=(height-h)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+14,y+64,w-28,19,"Map Preview: "+state(UtilitySettings.mapPreview),false));
        buttonList.add(new NeutralButton(3,x+14,y+86,w-28,19,"Preview on command: "+state(UtilitySettings.previewOnCommand),false));
        buttonList.add(new NeutralButton(4,x+14,y+108,w-28,19,"Preview size: "+(UtilitySettings.largeMapPreview?"Large":"Small"),false));
        buttonList.add(new NeutralButton(1,x+14,y+130,w-28,19,"Open Map Gallery",false));
        buttonList.add(new NeutralButton(2,x+w-94,y+h-27,80,19,"Back",false));
    }
    private String state(boolean on){return on?"ON":"OFF";}
    protected void actionPerformed(GuiButton b){
        if(b.id==0){UtilitySettings.mapPreview=!UtilitySettings.mapPreview;UtilitySettings.save();initGui();return;}
        if(b.id==3){UtilitySettings.previewOnCommand=!UtilitySettings.previewOnCommand;UtilitySettings.save();initGui();return;}
        if(b.id==4){UtilitySettings.largeMapPreview=!UtilitySettings.largeMapPreview;UtilitySettings.save();initGui();return;}
        if(b.id==1){mc.displayGuiScreen(new MapGalleryScreen(this));return;}
        if(b.id==2){mc.displayGuiScreen(parent);return;}
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x-1,y-1,x+w+1,y+h+1,0xFF555555);drawRect(x,y,x+w,y+h,0xF0181818);
        drawString(fontRendererObj,"Map Preview",x+14,y+12,0xFFEAEAEA);
        drawString(fontRendererObj,"Map images in vote books and gold chat links.",x+14,y+27,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
    }
}
