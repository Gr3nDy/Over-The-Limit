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
    static final int MAX_W=520,MAX_H=300,HEADER=26,FOOTER=28,PAD=14,SCROLL=24;
    /** Strip at the top of the body that holds the module reset, so it never covers a row. */
    static final int TOOL=14;
    private final Set<GuiButton> pinned=new HashSet<>();
    private int scroll,contentHeight;
    /** True while the scrollbar thumb is being dragged, so the strip is grabbable. */
    private boolean scrollDrag;
    ModulesScreen menu;
    protected SettingsPanel panel;

    protected abstract String title();
    protected String subtitle(){return null;}
    /** Declare rows with {@link #panel}; the frame is already sized and scrolled. */
    protected abstract void buildPanel();
    /** Draw custom widgets in panel space; labels and sections are drawn by the panel itself. */
    protected void drawContent(int mx,int my,float partial){}
    /** Draw above the scrolling band, outside its clip: category tabs and other fixed chrome. */
    protected void drawFixed(int mx,int my,float partial){}
    /** Draw on top of the buttons, e.g. swatch fills or text fields. */
    protected void drawOverlay(int mx,int my,float partial){}

    protected final int panelW(){return Math.min(MAX_W,width-16)-(menu==null?0:119);}
    protected final int panelH(){return Math.min(MAX_H,height-16);}
    protected final int panelX(){return (width-Math.min(MAX_W,width-16))/2+(menu==null?0:119);}
    protected final int panelY(){return (height-panelH())/2;}
    protected final int contentTop(){return panelY()+HEADER+(subtitle()==null?4:16)+TOOL+(menu==null?0:26);}
    /** Standalone screens keep the bottom footer; hosted ones return to the panel edge (Back is at the top). */
    protected final int contentBottom(){return panelY()+panelH()-(menu==null?FOOTER:0);}
    protected final int footerY(){return contentBottom()+4;}
    protected final int backX(){return panelX()+panelW()-PAD-80;}
    protected final int actionX(){return panelX()+PAD;}

    /** Header and footer buttons stay put while the content scrolls. */
    protected final <T extends GuiButton> T pin(T b){pinned.add(b);buttonList.add(b);return b;}
    protected final void back(int id){
        if(menu==null||this instanceof PgmSettingsScreen){pin(new NeutralButton(id,backX(),footerY(),80,BedrockTokens.CONTROL_H,"Back",false));return;}
        pin(new GuiButton(id,panelX()+9,panelY()+33,22,22,"Back"){
            @Override public void drawButton(net.minecraft.client.Minecraft mc,int mx,int my){
                if(!visible)return;
                hovered=BedrockUi.hit(mx,my,xPosition,yPosition,width,height);
                BedrockUi.menuSkin(hovered?7:6,xPosition,yPosition,width,height);
                BedrockUi.menuIcon(7,xPosition+3,yPosition+3,hovered);
            }
        });
    }
    protected final void resetButton(String moduleId){pin(new ModuleReset(90,panelX()+panelW()-PAD-16,menu==null?contentTop()-TOOL-3:panelY()+35,moduleId));}

    @Override public void initGui(){
        UtilitySettings.load();
        if(menu!=null){
            if(menu.width!=width||menu.height!=height)menu.setWorldAndResolution(mc,width,height);
            else menu.initGui();
        }
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
        if(wheel!=0&&menu!=null){
            int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight-1;
            if(mx<panelX()){menu.scrollWheel(mx,my,wheel);return;}
        }
        int max=Math.max(0,contentHeight-(contentBottom()-contentTop()));
        if(wheel!=0&&max>0){scroll=Math.max(0,Math.min(max,scroll+(wheel>0?-SCROLL:SCROLL)));initGui();}
    }
    /** Rows scrolled out of the band are clipped when drawn, so they must not take clicks either. */
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(menu!=null&&button==0){
            if(mx<panelX()){
                menu.clickSidebar(mx,my,this);
                if(mc.currentScreen==this)initGui();
                return;
            }
            if(my>=panelY()+8&&my<panelY()+28){
                if(mx>=panelX()&&mx<panelX()+50){mc.displayGuiScreen(menu);return;}
                if(mx>=panelX()+51&&mx<panelX()+114){
                    if(!(this instanceof PgmSettingsScreen)){
                        PgmSettingsScreen settings=new PgmSettingsScreen(menu);
                        settings.menu=menu;
                        mc.displayGuiScreen(settings);
                    }
                    return;
                }
            }
        }
        // The strip is outside the content band, so it has to be claimed before the hidden-row
        // pass below swallows the click.
        if(button==0&&scrollbarHit(mx,my)){scrollDrag=true;scrollTo(my);return;}
        if(BedrockUi.hit(mx,my,panelX()+2,contentTop(),panelW()-4,Math.max(0,contentBottom()-contentTop()))){super.mouseClicked(mx,my,button);return;}
        java.util.List<GuiButton> hidden=new java.util.ArrayList<>();
        for(GuiButton b:buttonList)if(!pinned.contains(b)&&b.visible){b.visible=false;hidden.add(b);}
        try{super.mouseClicked(mx,my,button);}finally{for(GuiButton b:hidden)b.visible=true;}
    }
    @Override public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,menu==null?0x40101010:0x10000000);
        if(menu==null){
            BedrockUi.panel(panelX(),panelY(),panelW(),panelH());
            BedrockUi.header(fontRendererObj,title(),panelX(),panelY(),panelW());
        }else{
            BedrockUi.menuSkin(0,panelX()-119,panelY(),panelW()+119,panelH());
            menu.drawSidebar(mx,my);
            BedrockUi.menuSkin(6,panelX()-3,panelY()+4,panelW()+5,28);
            boolean settings=this instanceof PgmSettingsScreen;
            BedrockUi.menuText("nav_modules"+(settings?"":"_active"),7,panelX()+27,panelY()+19,49);
            BedrockUi.menuText("nav_settings"+(settings?"_active":""),8,panelX()+97,panelY()+19,56);
            OtlModule module=null;
            for(OtlModule m:OtlModule.ALL)if(m.name.equalsIgnoreCase(title())){module=m;break;}
            if(module==null&&title().equals("Isometric Minimap"))module=OtlModule.byId("minimap");
            if(module==null&&title().equals("Preferred Hand"))module=OtlModule.byId("left_hand");
            if(module!=null)BedrockUi.menuText(module.id,module.name.length(),panelX()+39+Math.min(panelW()-90,module.name.length()*7)/2,panelY()+44,panelW()-90);
            else drawString(fontRendererObj,title(),panelX()+PAD,panelY()+39,BedrockUi.TEXT);
        }
        if(subtitle()!=null)drawString(fontRendererObj,fontRendererObj.trimStringToWidth(subtitle(),panelW()-PAD*2),panelX()+PAD,panelY()+(menu==null?29:57),BedrockUi.DIM);
        drawFixed(mx,my,partial);
        int top=contentTop(),bottom=contentBottom();
        scissor(panelX()+2,top,panelW()-4,Math.max(0,bottom-top));
        panel.draw(fontRendererObj);
        drawContent(mx,my,partial);
        for(GuiButton b:buttonList)if(!pinned.contains(b))b.drawButton(mc,mx,my);
        drawOverlay(mx,my,partial);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        BedrockUi.menuSkin(6,panelX()+2,bottom,panelW()-4,panelY()+panelH()-bottom-2);
        for(GuiButton b:buttonList)if(pinned.contains(b))b.drawButton(mc,mx,my);
        scrollbar(mx,my,top,bottom);
    }
    private void scissor(int x,int y,int w,int h){
        int sf=new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x*sf,mc.displayHeight-(y+h)*sf,w*sf,h*sf);
    }
    private void scrollbar(int mx,int my,int top,int bottom){
        int view=bottom-top,max=Math.max(0,contentHeight-view);
        if(view<=0||max<=0)return;
        int sx=panelX()+panelW()-8;
        drawRect(sx,top,sx+4,bottom,0xFF141414);
        int thumb=thumbHeight(view,max);
        int ty=top+(view-thumb)*scroll/max;
        boolean hot=scrollDrag||BedrockUi.hit(mx,my,sx-2,ty,8,thumb);
        drawRect(sx,ty,sx+4,ty+thumb,hot?0xFFFFFFFF:0xFF7A7A7A);
    }
    private int scrollMax(){int view=contentBottom()-contentTop();return view<=0?0:Math.max(0,contentHeight-view);}
    private static int thumbHeight(int view,int max){return Math.max(16,view*view/(view+max));}
    /** The 4px strip plus a couple of pixels of grab room on either side. */
    private boolean scrollbarHit(int mx,int my){
        return scrollMax()>0&&mx>=panelX()+panelW()-10&&mx<panelX()+panelW()-2
            &&my>=contentTop()&&my<contentBottom();
    }
    /** Centre the thumb on the cursor; the wheel and this share one clamped scroll value. */
    private void scrollTo(int my){
        int top=contentTop(),view=contentBottom()-top,max=scrollMax();
        if(max<=0)return;
        int travel=Math.max(1,view-thumbHeight(view,max));
        int next=Math.max(0,Math.min(max,(int)Math.round((my-top-thumbHeight(view,max)/2D)*max/travel)));
        if(next!=scroll){scroll=next;initGui();}
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(menu!=null&&mx<panelX()){menu.mouseClickMove(mx,my,button,elapsed);return;}
        if(scrollDrag){scrollTo(my);return;}
        super.mouseClickMove(mx,my,button,elapsed);
    }
    @Override protected void mouseReleased(int mx,int my,int state){
        scrollDrag=false;
        if(menu!=null)menu.mouseReleased(mx,my,state);
        super.mouseReleased(mx,my,state);
    }
    // smoke helpers
    int scrollValue(){return scroll;}
    int scrollMaxValue(){return scrollMax();}
}
