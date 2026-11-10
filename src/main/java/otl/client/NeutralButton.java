package otl.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Bedrock-style chrome: flat grey face, light bevel, green underline when selected. */
final class NeutralButton extends GuiButton {
    private final boolean selected;
    private final int alpha;
    NeutralButton(int id,int x,int y,int w,int h,String text,boolean selected){this(id,x,y,w,h,text,selected,255);}
    NeutralButton(int id,int x,int y,int w,int h,String text,boolean selected,int alpha){super(id,x,y,w,h,text);this.selected=selected;this.alpha=alpha;}
    public void drawButton(Minecraft mc,int mx,int my){
        if(!visible)return;
        hovered=mx>=xPosition&&mx<xPosition+width&&my>=yPosition&&my<yPosition+height;
        BedrockUi.button(mc.fontRendererObj,xPosition,yPosition,width,height,displayString,hovered,enabled,alpha);
        if(selected)drawRect(xPosition+2,yPosition+height-3,xPosition+width-2,yPosition+height-2,BedrockUi.ACCENT);
    }
}
