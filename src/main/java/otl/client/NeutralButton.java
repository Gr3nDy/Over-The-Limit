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
            drawRect(xPosition,yPosition,xPosition+width,yPosition+height,!enabled?BedrockUi.INSET:hovered?0xFF606060:BedrockUi.SLOT);
            drawRect(xPosition,yPosition+height-1,xPosition+width,yPosition+height, BedrockUi.EDGE);
            int available=Math.max(0,width-42);
            label=mc.fontRendererObj.trimStringToWidth(label,available);
            mc.fontRendererObj.drawString(label,xPosition+6,yPosition+(height-8)/2,enabled?BedrockUi.TEXT:BedrockUi.DIM);
            BedrockUi.toggle(xPosition+width-32,yPosition+(height-12)/2,on,hovered&&enabled);
            return;
        }
        BedrockUi.button(mc.fontRendererObj,xPosition,yPosition,width,height,displayString,hovered,enabled,alpha);
        if(selected)drawRect(xPosition+2,yPosition+height-3,xPosition+width-2,yPosition+height-2,BedrockUi.ACCENT);
    }
}
