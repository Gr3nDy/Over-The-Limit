package otl.client;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Compact settings frame shared by every options page: a fixed 470x272 panel with the header and
 * footer pinned, and a content canvas that scrolls as one. Rows come from {@link SettingsPanel}.
 */
abstract class SettingsScreen extends GuiScreen {
    static final int MAX_W=470,MAX_H=272,HEADER=26,FOOTER=28,PAD=14,SCROLL=24;
    /** Strip at the top of the body that holds the module reset, so it never covers a row. */
    static final int TOOL=14;
    private final Set<GuiButton> pinned=new HashSet<>();
    private int scroll,contentHeight;
    protected SettingsPanel panel;

    protected abstract String title();
    protected String subtitle(){return null;}
    /** Declare rows with {@link #panel}; the frame is already sized and scrolled. */
    protected abstract void buildPanel();
    /** Draw custom widgets in panel space; labels and sections are drawn by the panel itself. */
    protected void drawContent(int mx,int my,float partial){}
    /** Draw on top of the buttons, e.g. swatch fills or text fields. */
    protected void drawOverlay(int mx,int my,float partial){}

    protected final int panelW(){return Math.min(MAX_W,width-16);}
    protected final int panelH(){return Math.min(MAX_H,height-16);}
    protected final int panelX(){return (width-panelW())/2;}
    protected final int panelY(){return (height-panelH())/2;}
    protected final int contentTop(){return panelY()+HEADER+(subtitle()==null?4:16)+TOOL;}
    protected final int contentBottom(){return panelY()+panelH()-FOOTER;}
    protected final int footerY(){return contentBottom()+4;}
    protected final int backX(){return panelX()+panelW()-PAD-80;}
    protected final int actionX(){return panelX()+PAD;}

    /** Header and footer buttons stay put while the content scrolls. */
    protected final <T extends GuiButton> T pin(T b){pinned.add(b);buttonList.add(b);return b;}
    protected final void back(int id){pin(new NeutralButton(id,backX(),footerY(),80,20,"Back",false));}
    protected final void resetButton(String moduleId){pin(new ModuleReset(90,panelX()+panelW()-PAD-11,contentTop()-TOOL+1,moduleId));}

    @Override public void initGui(){
        UtilitySettings.load();
        buttonList.clear();pinned.clear();
        layout();
        contentHeight=panel.height();
        int max=Math.max(0,contentHeight-(contentBottom()-contentTop()));
        if(scroll>max){scroll=max;layout();contentHeight=panel.height();}
    }
    private void layout(){
        panel=new SettingsPanel(panelX(),panelW(),contentTop()-scroll);
        buildPanel();
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();
        int max=Math.max(0,contentHeight-(contentBottom()-contentTop()));
        if(wheel!=0&&max>0){scroll=Math.max(0,Math.min(max,scroll+(wheel>0?-SCROLL:SCROLL)));initGui();}
    }
    /** Rows scrolled out of the band are clipped when drawn, so they must not take clicks either. */
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(BedrockUi.hit(mx,my,panelX()+2,contentTop(),panelW()-4,Math.max(0,contentBottom()-contentTop()))){super.mouseClicked(mx,my,button);return;}
        java.util.List<GuiButton> hidden=new java.util.ArrayList<>();
        for(GuiButton b:buttonList)if(!pinned.contains(b)&&b.visible){b.visible=false;hidden.add(b);}
        try{super.mouseClicked(mx,my,button);}finally{for(GuiButton b:hidden)b.visible=true;}
    }
    @Override public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();
        BedrockUi.panel(panelX(),panelY(),panelW(),panelH());
        BedrockUi.header(fontRendererObj,title(),panelX(),panelY(),panelW());
        if(subtitle()!=null)drawString(fontRendererObj,subtitle(),panelX()+PAD,panelY()+29,BedrockUi.DIM);
        int top=contentTop(),bottom=contentBottom();
        scissor(panelX()+2,top,panelW()-4,Math.max(0,bottom-top));
        panel.draw(fontRendererObj);
        drawContent(mx,my,partial);
        for(GuiButton b:buttonList)if(!pinned.contains(b))b.drawButton(mc,mx,my);
        drawOverlay(mx,my,partial);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawRect(panelX()+2,bottom,panelX()+panelW()-2,panelY()+panelH()-2,BedrockUi.PANEL);
        drawRect(panelX()+2,bottom,panelX()+panelW()-2,bottom+1,0xFF3A3A3A);
        for(GuiButton b:buttonList)if(pinned.contains(b))b.drawButton(mc,mx,my);
        scrollbar(top,bottom);
    }
    private void scissor(int x,int y,int w,int h){
        int sf=new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x*sf,mc.displayHeight-(y+h)*sf,w*sf,h*sf);
    }
    private void scrollbar(int top,int bottom){
        int view=bottom-top,max=Math.max(0,contentHeight-view);
        if(view<=0||max<=0)return;
        int sx=panelX()+panelW()-8;
        drawRect(sx,top,sx+4,bottom,0xFF141414);
        int thumb=Math.max(16,view*view/(view+max));
        int ty=top+(view-thumb)*scroll/max;
        drawRect(sx,ty,sx+4,ty+thumb,0xFF7A7A7A);
    }
}
