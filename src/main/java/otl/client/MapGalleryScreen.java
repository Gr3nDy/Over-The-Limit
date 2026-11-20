package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

/** Bundled community previews plus the cached catalogs of both repositories. */
final class MapGalleryScreen extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField search;
    private int page;
    private Grid grid;
    private List<String> filtered=Collections.emptyList(), catalogVersion;
    private String lastSearch;
    private boolean namesVersion,favoritesOnly,filteredFavoritesOnly;

    static final class Grid {
        final int columns,rows,cellWidth,cellHeight,image,left;
        final int top=48;
        Grid(int width,int height){
            columns=Math.max(1,Math.min(4,(width-16)/120));
            rows=Math.max(1,Math.min(3,(height-96)/104));
            cellWidth=Math.min(120,(width-16)/columns);
            cellHeight=Math.min(120,(height-96)/rows);
            image=Math.max(1,Math.min(80,Math.min(cellWidth-12,cellHeight-28)));
            left=(width-columns*cellWidth)/2;
        }
        int perPage(){return columns*rows;}
        int x(int i){return left+i%columns*cellWidth;}
        int y(int i){return top+i/columns*cellHeight;}
        int indexAt(int x,int y){
            x-=left;y-=top;
            return x<0||y<0||x>=columns*cellWidth||y>=rows*cellHeight?-1:y/cellHeight*columns+x/cellWidth;
        }
    }

    MapGalleryScreen(GuiScreen parent){this.parent=parent;}

    public boolean doesGuiPauseGame(){return false;}

    public void initGui(){
        UtilitySettings.load();
        MapRemote.requestCatalog();
        String query=search==null?"":search.getText();
        grid=new Grid(width,height);
        int searchWidth=Math.min(300,width-188);
        search=new GuiTextField(0,fontRendererObj,16,24,searchWidth,20);
        search.setMaxStringLength(80);
        search.setEnableBackgroundDrawing(false);
        search.setText(query);
        search.setFocused(true);
        buttonList.clear();
        buttonList.add(new NeutralButton(1,width/2-160,height-28,60,20,"Prev",false));
        buttonList.add(new NeutralButton(2,width/2-40,height-28,80,20,"Back",false));
        buttonList.add(new NeutralButton(3,width/2+100,height-28,60,20,"Next",false));
        buttonList.add(new NeutralButton(4,width-166,18,60,20,"All",!favoritesOnly));
        buttonList.add(new NeutralButton(5,width-102,18,90,20,"Favorites",favoritesOnly));
    }

    /** Return map paths matching the current search text. */
    List<String> paths(){
        String q=search==null?"":search.getText().toLowerCase(Locale.ROOT).trim();
        List<String> catalog=MapRemote.catalog;
        if(q.equals(lastSearch)&&catalogVersion==catalog&&namesVersion==MapPreview.namesReady&&filteredFavoritesOnly==favoritesOnly)return filtered;
        lastSearch=q;catalogVersion=catalog;namesVersion=MapPreview.namesReady;filteredFavoritesOnly=favoritesOnly;
        String normalized=MapInfo.norm(q);
        List<String> out=new ArrayList<>();
        Set<String> paths=new LinkedHashSet<>();
        for(String p:MapPreviewIndex.PATHS){
            if(p.endsWith("/map.png"))paths.add("CommunityMaps/master/"+p);
            else if(p.endsWith("/map.xml"))paths.add("CommunityMaps/master/"+p.substring(0,p.length()-8)+"/map.png");
        }
        for(String dir:catalog)paths.add(dir+"/map.png");
        for(String p:paths){
            if(favoritesOnly&&!UtilitySettings.mapFavorites.contains(UtilitySettings.favoriteKey(p)))continue;
            String dir=p.substring(0,p.length()-8);
            if(q.isEmpty()){
                out.add(p);
            } else {
                String name=mapName(dir).toLowerCase(Locale.ROOT);
                if(name.contains(q)||dir.toLowerCase(Locale.ROOT).contains(q)||(!normalized.isEmpty()&&MapInfo.norm(name).contains(normalized))){
                    out.add(p);
                }
            }
        }
        filtered=out;return filtered;
    }

    protected void actionPerformed(GuiButton b){
        List<String> list=paths();
        int maxPage=pages(list.size());
        if(b.id==1){ // Prev
            page=Math.max(0,page-1);
        } else if(b.id==2){ // Back
            mc.displayGuiScreen(parent);
        } else if(b.id==3){ // Next
            page=Math.min(maxPage-1,page+1);
        } else if(b.id==4||b.id==5){
            favoritesOnly=b.id==5;page=0;lastSearch=null;initGui();
        }
    }

    protected void keyTyped(char c,int key) throws IOException{
        if(key==1){
            mc.displayGuiScreen(parent);
            return;
        }
        String before=search.getText();
        search.textboxKeyTyped(c,key);
        if(!before.equals(search.getText()))page=0;
    }

    /** Mouse wheel scrolls pages. */
    public void handleMouseInput() throws IOException{
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0){
            List<String> list=paths();
            int maxPage=pages(list.size());
            if(wheel>0) page=Math.max(0,page-1);
            else page=Math.min(maxPage-1,page+1);
        }
    }

    /** Mouse click on a map cell opens the info card. */
    protected void mouseClicked(int mx,int my,int button) throws IOException{
        super.mouseClicked(mx,my,button);
        search.mouseClicked(mx,my,button);
        if(mc.currentScreen!=this)return;
        if(button!=0) return;
        List<String> list=paths();
        int i=grid.indexAt(mx,my),idx=page*grid.perPage()+i;
        if(i>=0&&idx<list.size()){
            String path=list.get(idx);
            int x=grid.x(i),y=grid.y(i);
            if(mx>=x+grid.cellWidth-19&&mx<x+grid.cellWidth-2&&my>=y+3&&my<y+21){
                String key=UtilitySettings.favoriteKey(path);
                if(key!=null){if(!UtilitySettings.mapFavorites.add(key))UtilitySettings.mapFavorites.remove(key);UtilitySettings.save();lastSearch=null;}
                return;
            }
            openMapCard(path);
        }
    }

    /** Open the map info card for the given path. */
    private void openMapCard(String path){
        mc.displayGuiScreen(new MapInfoScreen(path.substring(0,path.length()-8),this));
    }

    private String mapName(String dir){
        String name=MapPreview.displayName(dir);
        return name==null||name.isEmpty()?dir.substring(dir.lastIndexOf('/')+1).replace('_',' '):name;
    }

    private int pages(int size){return Math.max(1,(size+grid.perPage()-1)/grid.perPage());}

    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();
        drawRect(0,0,width,44,BedrockUi.PANEL);
        drawRect(0,43,width,44,BedrockUi.EDGE);
        drawString(fontRendererObj,"Map Gallery",12,7,BedrockUi.TEXT);
        drawString(fontRendererObj,fontRendererObj.trimStringToWidth(MapRemote.catalogStatus,Math.max(0,width-200)),
            118,7,BedrockUi.DIM);
        BedrockUi.inset(12,18,search.width+8,20);
        search.drawTextBox();

        List<String> list=paths();
        int maxPage=pages(list.size());
        page=Math.min(page,maxPage-1);
        int start=page*grid.perPage();

        // draw maps
        for(int i=0;i<grid.perPage();i++){
            int idx=start+i;
            if(idx>=list.size()) break;
            int x=grid.x(i),y=grid.y(i),cellW=grid.cellWidth,cellH=grid.cellHeight,side=grid.image;

            String path=list.get(idx);
            ResourceLocation tex=MapPreview.galleryTexture(path);
            boolean hover=grid.indexAt(mx,my)==i;
            int cx=x+2,cy=y+2,cw=cellW-4,ch=cellH-4;

            // Lunar card: dark fill, 1px outline, blue ring on hover.
            BedrockUi.rounded(cx,cy,cw,ch,4,hover?BedrockTokens.STATE_HOVER:BedrockUi.SLOT);
            BedrockUi.outline(cx,cy,cw,ch,BedrockUi.EDGE);
            if(hover)BedrockUi.outline(cx,cy,cw,ch,BedrockTokens.STATE_SELECTED);
            MapPreview.star(x+cellW-16,y+5,UtilitySettings.mapFavorites.contains(UtilitySettings.favoriteKey(path))?0xFFFFD34F:BedrockUi.DIM);

            // draw square thumbnail (1:1 ratio) in a recessed frame
            int ix=x+(cellW-side)/2,iy=y+7;
            BedrockUi.rounded(ix-2,iy-2,side+4,side+4,2,BedrockUi.INSET);
            if(tex!=null){
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.color(1,1,1,1);
                mc.getTextureManager().bindTexture(tex);
                Gui.drawModalRectWithCustomSizedTexture(ix,iy,0,0,side,side,side,side);
            } else {
                drawRect(ix,iy,ix+side,iy+side,BedrockUi.PANEL);
            }

            // draw name below image
            String dir=path.substring(0,path.length()-8);
            String name=mapName(dir);
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(name,cellW-12),x+6,y+side+11,0xFFFFFFFF);
        }

        // pagination info
        drawCenteredString(fontRendererObj,"Page "+(page+1)+" / "+maxPage+"  ("+list.size()+" maps)",width/2,height-41,BedrockUi.MUTED);
        for(GuiButton b:buttonList){if(b.id==1)b.enabled=page>0;else if(b.id==3)b.enabled=page<maxPage-1;}
        super.drawScreen(mx,my,partial);
    }
}
