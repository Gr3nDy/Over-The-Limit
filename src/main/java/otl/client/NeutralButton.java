package otl.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Neutral, vanilla-adjacent chrome; no gold accent or colored hover text. */
final class NeutralButton extends GuiButton {
    private final boolean selected;
    NeutralButton(int id,int x,int y,int w,int h,String text,boolean selected){super(id,x,y,w,h,text);this.selected=selected;}
    public void drawButton(Minecraft mc,int mx,int my){
        super.drawButton(mc,mx,my);
        if(visible&&selected)drawRect(xPosition+2,yPosition+height-2,xPosition+width-2,yPosition+height-1,0xFFCCCCCC);
    }
}
