package otl.client;

import net.minecraft.client.gui.*;

final class ColorSwatches extends GuiScreen {
    static final int[] COLORS={0x000000,0x555555,0xAAAAAA,0xFFFFFF,0xAA0000,0xFF5555,0xFFAA00,0xFFBD45,0xFFFF55,0x00AA00,0x55FF55,0x00AAAA,0x55FFFF,0x0000AA,0x5555FF,0xAA00AA,0xFF55FF,0xFF88AA};
    private final GuiScreen parent;private final int kind;private int x,y,w;
    ColorSwatches(GuiScreen parent,int kind){this.parent=parent;this.kind=kind;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        w=Math.min(300,width-20);x=(width-w)/2;y=(height-190)/2;buttonList.clear();
        for(int i=0;i<COLORS.length;i++)buttonList.add(new NeutralButton(i,x+15+i%6*(w-30)/6,y+44+i/6*31,(w-36)/6,26,"",false));
        buttonList.add(new NeutralButton(99,x+w-90,y+157,75,20,"Back",false));
    }
    protected void actionPerformed(GuiButton b){
        if(b.id<COLORS.length){if(kind==0)UtilitySettings.killBorderColor=COLORS[b.id];else if(kind==1)UtilitySettings.deathBorderColor=COLORS[b.id];else UtilitySettings.assistBorderColor=COLORS[b.id];UtilitySettings.save();}
        mc.displayGuiScreen(parent);
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawRect(x,y,x+w,y+190,0xF0202020);
        drawString(fontRendererObj,kind==0?"Your kill border color":kind==1?"Your death border color":"Your assist border color",x+15,y+14,0xFFFFFFFF);
        drawString(fontRendererObj,"Pick a swatch, or use hex in Colors.",x+15,y+29,0xFFAAAAAA);
        super.drawScreen(mx,my,partial);
        String hovered=null;
        for(GuiButton b:buttonList)if(b.id<COLORS.length){
            drawRect(b.xPosition+3,b.yPosition+3,b.xPosition+b.width-3,b.yPosition+b.height-3,0xFF000000|COLORS[b.id]);
            if(mx>=b.xPosition&&mx<b.xPosition+b.width&&my>=b.yPosition&&my<b.yPosition+b.height)hovered=String.format("#%06X",COLORS[b.id]);
        }
        if(hovered!=null)drawHoveringText(java.util.Collections.singletonList(hovered),mx,my);
    }
}
