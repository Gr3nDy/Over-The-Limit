package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.config.GuiSlider;

/**
 * Bedrock-style slider: thin track, white handle, snaps to preset stops.
 * The glyph on the right restores this slider's default without a second button.
 */
final class PresetSlider extends GuiSlider {
    static final int[] PERCENT={0,25,50,75,100};
    static final int[] SIZE={50,75,100,125,150};
    static final int[] HIT_SIZE={50,100,150,200};
    static final int[] SECONDS={1,5,10,15,20,30};
    static final int[] SHORT_SECONDS={1,2,4,6,8,10};
    static final int[] ROWS={1,2,3,4,5};

    private static final int RESET=14;
    private final int[] presets;
    private final int preset;

    PresetSlider(int id,int x,int y,int w,String prefix,String suffix,int[] presets,int value,int preset,ISlider callback){
        super(id,x,y,w,20,prefix,suffix,presets[0],presets[presets.length-1],value,false,true,callback);
        this.presets=presets;
        this.preset=preset;
    }
    private int track(){return width-RESET-8;}
    private int valueAt(int mx){
        double t=Math.max(0,Math.min(1,(mx-(xPosition+4))/(double)Math.max(1,track())));
        int raw=(int)Math.round(minValue+t*(maxValue-minValue)),best=presets[0];
        for(int p:presets)if(Math.abs(p-raw)<Math.abs(best-raw))best=p;
        return best;
    }
    private void snap(int value){setValue(value);updateSlider();}

    public boolean mousePressed(Minecraft mc,int mx,int my){
        if(!visible||!enabled||!BedrockUi.hit(mx,my,xPosition,yPosition,width,height))return false;
        if(mx>=xPosition+width-RESET-2){snap(preset);dragging=false;return true;}
        dragging=true;snap(valueAt(mx));return true;
    }
    public void drawButton(Minecraft mc,int mx,int my){
        if(!visible)return;
        hovered=BedrockUi.hit(mx,my,xPosition,yPosition,width,height);
        if(dragging)snap(valueAt(mx));
        int centre=yPosition+height-5,t=track();
        BedrockUi.inset(xPosition,yPosition,width,height);
        mc.fontRendererObj.drawStringWithShadow(displayString,xPosition+6,yPosition+2,enabled?BedrockUi.CAPTION:0xFF808080);
        drawRect(xPosition+4,centre-1,xPosition+4+t,centre+1,0xFF353535);
        int knob=xPosition+4+(int)(sliderValue*t);
        drawRect(knob-1,centre-1,knob+1,centre+1,0xFFFFFFFF);
        drawRect(knob-2,centre-5,knob+2,centre-1,0xFFFFFFFF);
        drawRect(knob-2,centre+1,knob+2,centre+5,0xFFFFFFFF);
        int gx=xPosition+width-RESET,gy=yPosition+3;
        int ink=hovered?0xFFE0E0E0:0xFFA0A0A0;
        drawRect(gx+1,gy,gx+7,gy+1,ink);drawRect(gx+1,gy+7,gx+7,gy+8,ink);
        drawRect(gx,gy+1,gx+1,gy+7,ink);drawRect(gx+7,gy+1,gx+8,gy+7,ink);
        drawRect(gx+5,gy-1,gx+7,gy+1,ink);
    }
}
