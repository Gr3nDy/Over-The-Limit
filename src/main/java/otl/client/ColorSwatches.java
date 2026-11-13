package otl.client;

import net.minecraft.client.gui.*;

final class ColorSwatches extends SettingsScreen {
    static final int[] COLORS={0x000000,0x555555,0xAAAAAA,0xFFFFFF,0xAA0000,0xFF5555,0xFFAA00,0xFFBD45,0xFFFF55,0x00AA00,0x55FF55,0x00AAAA,0x55FFFF,0x0000AA,0x5555FF,0xAA00AA,0xFF55FF,0xFF88AA};
    private static final int COLUMNS=6;
    private final GuiScreen parent;private final int kind;
    ColorSwatches(GuiScreen parent,int kind){this.parent=parent;this.kind=kind;}
    public boolean doesGuiPauseGame(){return false;}
    protected String title(){return "Border Color";}
    protected String subtitle(){return (kind==0?"Your kill border":kind==1?"Your death border":"Your assist border")+" - pick a swatch.";}
    protected void buildPanel(){
        int y=0;
        for(int i=0;i<COLORS.length;i++){
            if(i%COLUMNS==0)y=panel.row(26);
            buttonList.add(new NeutralButton(i,panel.columnX(COLUMNS,i%COLUMNS),y,panel.columnWidth(COLUMNS),26,"",false));
        }
        back(99);
    }
    protected void actionPerformed(GuiButton b){
        if(b.id<COLORS.length){
            int color=COLORS[b.id];
            if(parent instanceof KillFeedSettings)((KillFeedSettings)parent).applySwatch(kind,color);
            else if(kind==0)UtilitySettings.killBorderColor=color;
            else if(kind==1)UtilitySettings.deathBorderColor=color;
            else UtilitySettings.assistBorderColor=color;
            UtilitySettings.save();
        }
        mc.displayGuiScreen(parent);
    }
    protected void drawOverlay(int mx,int my,float partial){
        String hovered=null;
        for(GuiButton b:buttonList)if(b.id<COLORS.length){
            drawRect(b.xPosition+3,b.yPosition+3,b.xPosition+b.width-3,b.yPosition+b.height-3,0xFF000000|COLORS[b.id]);
            if(BedrockUi.hit(mx,my,b.xPosition,b.yPosition,b.width,b.height))hovered=String.format("#%06X",COLORS[b.id]);
        }
        if(hovered!=null)drawHoveringText(java.util.Collections.singletonList(hovered),mx,my);
    }
}
