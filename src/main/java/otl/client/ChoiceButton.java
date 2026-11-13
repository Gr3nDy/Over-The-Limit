package otl.client;

import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

/** A labeled row of explicit choices; selecting the current value is a no-op. */
final class ChoiceButton extends GuiButton {
    private final String title;
    private final String[] choices;
    private final int selected;
    private final IntConsumer change;
    private final GuiScreen screen;
    ChoiceButton(GuiScreen screen,int id,int x,int y,int w,String title,String[] choices,int selected,IntConsumer change){
        super(id,x,y,w,34,title+": "+choices[selected]);
        this.screen=screen;this.title=title;this.choices=choices;this.selected=selected;this.change=change;
    }
    public void drawButton(Minecraft mc,int mx,int my){
        if(!visible)return;
        mc.fontRendererObj.drawString(title,xPosition,yPosition,enabled?BedrockUi.TEXT:BedrockUi.DIM);
        for(int i=0;i<choices.length;i++){
            int left=xPosition+width*i/choices.length,right=xPosition+width*(i+1)/choices.length;
            boolean hover=BedrockUi.hit(mx,my,left,yPosition+12,right-left,20);
            String label=mc.fontRendererObj.trimStringToWidth(choices[i],Math.max(4,right-left-8));
            BedrockUi.button(mc.fontRendererObj,left,yPosition+12,right-left,20,i==selected?"":label,hover,enabled);
            if(i==selected){
                drawRect(left+2,yPosition+14,right-2,yPosition+29,enabled?0xFF3C8527:BedrockUi.INSET);
                BedrockUi.centered(mc.fontRendererObj,label,(left+right)/2,yPosition+18,BedrockUi.TEXT);
                drawRect(left+4,yPosition+29,right-4,yPosition+30,BedrockUi.ACCENT);
            }
        }
    }
    public boolean mousePressed(Minecraft mc,int mx,int my){
        if(visible&&enabled&&BedrockUi.hit(mx,my,xPosition,yPosition+12,width,20)){
            int index=Math.min(choices.length-1,(mx-xPosition)*choices.length/width);
            if(index!=selected){change.accept(index);UtilitySettings.save();screen.initGui();}
            playPressSound(mc.getSoundHandler());
        }
        return false;
    }
    public boolean keyTyped(char typed,int key){
        if(!visible||!enabled)return false;
        int next=selected;
        if(key==Keyboard.KEY_LEFT)next=Math.max(0,selected-1);
        if(key==Keyboard.KEY_RIGHT)next=Math.min(choices.length-1,selected+1);
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){change.accept(selected);UtilitySettings.save();screen.initGui();return true;}
        if(next!=selected){change.accept(next);UtilitySettings.save();screen.initGui();return true;}
        return false;
    }
}
