package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

/** Searchable gallery of the bundled CommunityMaps previews. */
final class MapGalleryScreen extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField search;
    private int scroll;
    MapGalleryScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){search=new GuiTextField(0,fontRendererObj,width/2-150,18,300,20);search.setMaxStringLength(80);search.setFocused(true);buttonList.clear();buttonList.add(new GuiButton(1,width/2-40,height-28,80,20,"Back"));}
    private List<String> paths(){
        String q=search==null?"":search.getText().toLowerCase(Locale.ROOT).trim();List<String> out=new ArrayList<>();
        for(String p:MapPreviewIndex.PATHS)if(p.endsWith("/map.png")){
            String dir=p.substring(0,p.length()-8);
            if(q.isEmpty()||p.toLowerCase(Locale.ROOT).contains(q)){out.add(p);continue;}
            String name=MapPreview.displayName(dir).toLowerCase(Locale.ROOT);
            if(name.contains(q))out.add(p);
        }
        return out;
    }
    protected void actionPerformed(GuiButton b){if(b.id==1)mc.displayGuiScreen(parent);}
    protected void keyTyped(char c,int key)throws IOException{if(key==1){mc.displayGuiScreen(parent);return;}search.textboxKeyTyped(c,key);}
    public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getDWheel();if(wheel!=0)scroll=Math.max(0,scroll-(wheel>0?1:-1));}
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();drawString(fontRendererObj,"Map Gallery",12,6,0xFFFFFFFF);search.drawTextBox();
        List<String> list=paths();int cols=4,cellW=Math.max(110,(width-24)/cols),cellH=100,start=scroll*cols;
        for(int n=start;n<list.size();n++){int i=n-start,col=i%cols,row=i/cols,x=12+col*cellW,y=48+row*cellH;if(y+cellH>height-32)break;String path=list.get(n);ResourceLocation tex=MapPreview.galleryTexture(path);drawRect(x,y,x+cellW-10,y+78,0xAA101010);int imgW=cellW-20,imgH=52;if(tex!=null){int[] size=MapPreview.gallerySize(path);if(size!=null&&size[0]>0&&size[1]>0){float ar=(float)size[0]/size[1];if(ar>1){imgH=Math.max(20,(int)(imgW/ar));if(imgH>52){imgH=52;imgW=Math.max(24,(int)(52*ar));}}else{imgW=Math.max(24,(int)(52*ar));if(imgW>cellW-20){imgW=cellW-20;imgH=Math.max(20,(int)((cellW-20)/ar));}}}}int ix=x+(cellW-imgW)/2,iy=y+6;GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(tex);Gui.drawModalRectWithCustomSizedTexture(ix,iy,0,0,imgW,imgH,imgW,imgH);String dir=path.substring(0,path.length()-8);String name=MapPreview.displayName(dir);if(name==null||name.isEmpty())name=path.substring(path.lastIndexOf('/')+1,path.length()-8);drawString(fontRendererObj,fontRendererObj.trimStringToWidth(name,cellW-14),x+3,y+imgH+12,0xFFFFFFFF);}
        drawString(fontRendererObj,(start/cols+1)+" / "+Math.max(1,(list.size()+cols-1)/cols),12,height-20,0xFFAAAAAA);super.drawScreen(mx,my,partial);
    }
}
