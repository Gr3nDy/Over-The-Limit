package otl.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

/** Local canvas with correctly translated drawing and mouse input. Does not resize the game. */
abstract class CompactScreen extends GuiScreen {
    private int outerWidth,outerHeight,offsetX,offsetY;
    private boolean handlingMouse;
    @Override public void initGui(){
        UtilitySettings.load();outerWidth=width;outerHeight=height;
        if(!UtilitySettings.fullscreen){width=Math.min(720,Math.max(300,outerWidth-24));height=Math.min(430,Math.max(200,outerHeight-20));width=Math.min(width,outerWidth);height=Math.min(height,outerHeight);}
        offsetX=(outerWidth-width)/2;offsetY=(outerHeight-height)/2;initPanel();
    }
    protected abstract void initPanel();
    @Override public void drawScreen(int x,int y,float partial){
        GlStateManager.pushMatrix();GlStateManager.translate(offsetX,offsetY,0);
        try{drawPanel(x-offsetX,y-offsetY,partial);}finally{GlStateManager.popMatrix();}
    }
    protected void drawPanel(int x,int y,float partial){super.drawScreen(x,y,partial);}
    @Override public void handleMouseInput()throws IOException{
        handlingMouse=true;try{super.handleMouseInput();}finally{handlingMouse=false;}
    }
    private int localX(int x){return (handlingMouse?org.lwjgl.input.Mouse.getEventX()*outerWidth/mc.displayWidth:x)-offsetX;}
    private int localY(int y){return (handlingMouse?outerHeight-org.lwjgl.input.Mouse.getEventY()*outerHeight/mc.displayHeight-1:y)-offsetY;}
    @Override protected void mouseClicked(int x,int y,int b)throws IOException{panelMouseClicked(localX(x),localY(y),b);}
    protected void panelMouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);}
    @Override protected void mouseClickMove(int x,int y,int b,long elapsed){panelMouseMove(localX(x),localY(y),b,elapsed);}
    protected void panelMouseMove(int x,int y,int b,long elapsed){super.mouseClickMove(x,y,b,elapsed);}
    @Override protected void mouseReleased(int x,int y,int state){panelMouseReleased(localX(x),localY(y),state);}
    protected void panelMouseReleased(int x,int y,int state){super.mouseReleased(x,y,state);}
    protected void resizePanel(){width=outerWidth;height=outerHeight;initGui();}
}
