package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.FontRenderer;

/** Top-right undo-style button that restores the module's factory settings; tooltip on hover. */
final class ModuleReset extends GuiButton {
    private final String moduleId;
    ModuleReset(int id,int x,int y,String moduleId){super(id,x,y,11,12,"");this.moduleId=moduleId;}
    /** Resets the module and persists; screens refresh themselves afterwards. */
    boolean reset(){OtlModule module=OtlModule.byId(moduleId);if(module==null)return false;module.reset();UtilitySettings.save();return true;}
    public void drawButton(Minecraft mc,int mx,int my){
        if(!visible)return;
        hovered=BedrockUi.hit(mx,my,xPosition,yPosition,width,height);
        BedrockUi.iconBox(xPosition,yPosition,hovered,false);
        BedrockUi.resetArrow(xPosition,yPosition,hovered);
        if(hovered){
            FontRenderer font=mc.fontRendererObj;String tip="Reset Module";
            int tw=font.getStringWidth(tip)+10;
            int screen=mc.currentScreen!=null?mc.currentScreen.width:width;
            int tx=Math.min(Math.max(2,xPosition+width-tw),Math.max(2,screen-tw-2));
            int ty=yPosition+height+2;
            Gui.drawRect(tx,ty,tx+tw,ty+12,0xF0101010);
            Gui.drawRect(tx,ty,tx+tw,ty+1,0xFF454545);
            font.drawStringWithShadow(tip,tx+5,ty+2,0xFFE0E0E0);
        }
    }
}
