package otl.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Bedrock buttons; boolean state labels retain their full value for callers, but render as switch rows. */
final class NeutralButton extends GuiButton {
    private final boolean selected;
    private final int alpha;
    NeutralButton(int id,int x,int y,int w,int h,String text,boolean selected){this(id,x,y,w,h,text,selected,255);}
    NeutralButton(int id,int x,int y,int w,int h,String text,boolean selected,int alpha){super(id,x,y,w,h,text);this.selected=selected;this.alpha=alpha;}
    public void drawButton(Minecraft mc,int mx,int my){
        if(!visible)return;
        hovered=mx>=xPosition&&mx<xPosition+width&&my>=yPosition&&my<yPosition+height;
        if(displayString.endsWith(": ON")||displayString.endsWith(": OFF")){
            boolean on=displayString.endsWith(": ON");
            String label=displayString.substring(0,displayString.lastIndexOf(": "));
            BedrockUi.menuSkin(enabled?(hovered?7:6):10,xPosition,yPosition,width,height);
            int available=Math.max(0,width-42);
            label=mc.fontRendererObj.trimStringToWidth(label,available);
            mc.fontRendererObj.drawString(label,xPosition+6,yPosition+(height-8)/2,enabled?BedrockUi.TEXT:BedrockUi.DIM);
            BedrockUi.toggle(xPosition+width-32,yPosition+(height-12)/2,on,hovered&&enabled);
            return;
        }
        if(selected&&alpha==255){
            BedrockUi.menuSkin(12,xPosition,yPosition,width,height);
            BedrockUi.plainCentered(mc.fontRendererObj,mc.fontRendererObj.trimStringToWidth(displayString,width-8),
                xPosition+width/2,yPosition+(height-mc.fontRendererObj.FONT_HEIGHT)/2,0xFFFFFFFF);
        }else BedrockUi.button(mc.fontRendererObj,xPosition,yPosition,width,height,displayString,hovered,enabled,alpha);
    }
}
