package otl.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** A chat-opened team card, expanded past the HUD preview limit. */
final class TeamRosterScreen extends GuiScreen {
    /** Row height, head size at rest, head size while hovered, and where the name starts. */
    static final int ROW=24,HEAD=16,BIG_HEAD=24,TEXT_X=28;
    private final TeamBar.Side team;
    private final GuiScreen parent;
    private int page,rows,columns,cell,pages;
    private TeamBar.HeadMenu menu;

    TeamRosterScreen(TeamBar.Side team,GuiScreen parent){this.team=team;this.parent=parent;}

    public void initGui(){
        menu=null;
        cell=TEXT_X+8;
        for(String name:team.members)cell=Math.max(cell,TEXT_X+fontRendererObj.getStringWidth(name));
        rows=Math.max(1,(height-68)/ROW);
        columns=Math.max(1,Math.min((width-12)/cell,(team.members.size()+rows-1)/rows));
        pages=Math.max(1,(team.members.size()+rows*columns-1)/(rows*columns));
        page=Math.min(page,pages-1);
        buttonList.clear();
        if(pages>1){
            GuiButton prev=new GuiButton(1,width/2-70,height-24,28,BedrockTokens.CONTROL_H,"<");
            GuiButton next=new GuiButton(2,width/2+42,height-24,28,BedrockTokens.CONTROL_H,">");
            prev.enabled=page>0;next.enabled=page+1<pages;
            buttonList.add(prev);buttonList.add(next);
        }
        buttonList.add(new GuiButton(0,width/2-36,height-24,72,BedrockTokens.CONTROL_H,"Back"));
    }
    public void drawScreen(int mx,int my,float ticks){
        drawDefaultBackground();
        drawCenteredString(fontRendererObj,team.name+" ("+team.members.size()+")",width/2,10,0xFFFFFF);
        if(pages>1)drawCenteredString(fontRendererObj,(page+1)+" / "+pages,width/2,height-36,0xCCCCCC);
        int first=page*rows*columns;
        int visible=Math.min(rows*columns,team.members.size()-first);
        int left=(width-columns*cell)/2;
        Tessellator tess=Tessellator.getInstance();
        WorldRenderer world=tess.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableTexture2D();
        world.begin(7,DefaultVertexFormats.POSITION_COLOR);
        for(int i=0;i<visible;i++){
            String name=team.members.get(first+i);
            int x=left+i/rows*cell,y=26+i%rows*ROW;
            if(menu==null?BedrockUi.hit(mx,my,x,y,cell,ROW):name.equals(menu.player))
                TeamBar.quad(world,x,y,x+cell,y+ROW,BedrockTokens.STATE_HOVER);
            face(world,name,x,y+(ROW-HEAD)/2,HEAD);
        }
        // The hovered head is drawn last and larger, so it reads as the one you are on and never
        // disappears under its neighbours.
        if(menu!=null){
            int index=team.members.indexOf(menu.player);
            if(index>=first&&index<first+visible){
                int x=left+(index-first)/rows*cell,y=26+(index-first)%rows*ROW;
                face(world,menu.player,x,y+(ROW-BIG_HEAD)/2,BIG_HEAD);
            }
        }
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f,1f,1f,1f);
        for(int i=0;i<visible;i++){
            fontRendererObj.drawStringWithShadow(team.members.get(first+i),
                left+i/rows*cell+TEXT_X,26+i%rows*ROW+(ROW-8)/2,0xFFFFFF);
        }
        super.drawScreen(mx,my,ticks);
        if(menu!=null)TeamBar.drawHeadMenu(mc,menu,mx,my);
    }
    /** One player's face, drawn at any size so the hovered row can grow it. */
    private static void face(WorldRenderer world,String name,int x,int y,int size){
        int[] pixels=KillFeed.face(name);
        if(pixels.length!=64){TeamBar.quad(world,x,y,x+size,y+size,0xFF8A8A8A);return;}
        int step=size/8;
        for(int p=0;p<64;p++){
            if((pixels[p]>>>24)<16)continue;
            int px=x+(p%8)*step,py=y+(p/8)*step;
            TeamBar.quad(world,px,py,px+step,py+step,pixels[p]);
        }
    }
    protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button==0){
            if(menu!=null){
                TeamBar.HeadMenu open=menu;menu=null;
                int action=open.actionAt(mx,my);
                if(action>=0)TeamBar.playerAction(mc,open.player,action);
                return;
            }
            int left=(width-columns*cell)/2;
            if(BedrockUi.hit(mx,my,left,26,columns*cell,rows*ROW)){
                int col=(mx-left)/cell,row=(my-26)/ROW;
                int index=page*rows*columns+col*rows+row;
                if(index<team.members.size()){
                    String name=team.members.get(index);
                    menu=new TeamBar.HeadMenu(name,left+col*cell,26+(row+1)*ROW,cell,
                        TeamBar.HeadMenu.width(mc,name),width,height);
                }
                return;
            }
        }
        super.mouseClicked(mx,my,button);
    }
    protected void actionPerformed(GuiButton button){
        if(button.id==0){mc.displayGuiScreen(parent);return;}
        page+=button.id==1?-1:1;initGui();
    }
    protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE&&menu!=null)menu=null;
        else if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);
        else super.keyTyped(c,key);
    }
    public boolean doesGuiPauseGame(){return false;}
}
