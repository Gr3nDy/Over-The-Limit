package otl.client;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Replaces the server's map book with a card built from map.xml, bundled or downloaded. */
final class MapInfoScreen extends GuiScreen {
    private final String query;
    private final GuiScreen parent;
    private volatile MapInfo info;
    private volatile boolean failed;
    private int x, y, w, h, banner, scroll;
    private MapInfo cachedInfo;
    private boolean cachedFailed;
    private int cachedWidth;
    private List<String> lines;

    MapInfoScreen(String query) {
        this(query,null);
    }

    MapInfoScreen(String query,GuiScreen parent) {
        this.query = query;
        this.parent = parent;
        // Both the bundled index scan and the repository lookup touch disk/network.
        MapRemote.lookup(query, found -> {
            if (found == null) failed = true; else info = found;
        });
    }

    MapInfoScreen(MapInfo info, GuiScreen parent) {
        this.query=info.name;this.info=info;this.parent=parent;
        if(info.incomplete)MapRemote.lookup(info.dir,found->{if(found!=null)this.info=found;});
    }

    static int imageSide(int width,int height){return Math.max(1,Math.min(220,Math.min((Math.min(620,width-20)-40)/2,height-88)));}
    static int bannerShade(int row){return Math.min(255,(int)(row*3.2f))<<24;}

    public boolean doesGuiPauseGame() {
        return false;
    }

    public void initGui() {
        w = Math.min(620, width - 20);
        banner = imageSide(width,height);
        h = Math.min(height-16, Math.max(180,banner+52));
        x = (width - w) / 2;
        y = (height - h) / 2;
        buttonList.clear();
        int row = y + h - 30;
        buttonList.add(new NeutralButton(1, x + w - 118, row, 104, 19, "See other maps", false));
        buttonList.add(new NeutralButton(2, x + w - 198, row, 72, 19, parent==null?"Close":"Back", false));
    }

    protected void actionPerformed(GuiButton b) {
        if (b.id == 1) {
            mc.displayGuiScreen(new MapGalleryScreen(this));
            return;
        }
        if (b.id == 2) mc.displayGuiScreen(parent);
    }

    protected void keyTyped(char typed, int key) throws IOException {
        if (key == 1) mc.displayGuiScreen(parent);
    }

    public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        drawRect(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
        BedrockUi.panel(x,y,w,h);
        drawBanner();
        drawBody();
        super.drawScreen(mx, my, partial);
    }

    private void drawBanner() {
        int bx = x + 8, by = y + 8, bw = banner;
        BedrockUi.rounded(bx,by,bw,banner,2,BedrockUi.INSET);
        ResourceLocation texture = info == null ? null : info.texture();
        if (texture != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(texture);
            drawModalRectWithCustomSizedTexture(bx, by, 0, 0, bw, banner, bw, banner);
            for (int i = 0; i < banner / 2; i++) {
                drawRect(bx, by + banner - banner / 2 + i, bx + bw, by + banner - banner / 2 + i + 1, bannerShade(i));
            }
        }
        String title = info == null ? query : info.name;
        String pill = fontRendererObj.trimStringToWidth(info == null ? "LOOKUP" : info.gamemode + (info.version.isEmpty() ? "" : "  " + info.version),bw-24);
        int pillWidth = fontRendererObj.getStringWidth(pill) + 12;
        BedrockUi.rounded(bx+bw-pillWidth-6,by+6,pillWidth,14,6,BedrockUi.PANEL);
        BedrockUi.outline(bx+bw-pillWidth-6,by+6,pillWidth,14,BedrockUi.EDGE);
        drawString(fontRendererObj, pill, bx + bw - pillWidth, by + 9, BedrockUi.ACCENT);
        GlStateManager.pushMatrix();
        GlStateManager.translate(bx + 8, by + banner - 24, 0);
        GlStateManager.scale(1.6f, 1.6f, 1f);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(title, (int) ((bw - 24) / 1.6f)), 0, 0, 0xFFFFFFFF, true);
        GlStateManager.popMatrix();
    }

    private void drawBody() {
        List<String> lines=detailLines();
        int left=x+banner+20,wid=w-banner-36;
        BedrockUi.inset(left-8,y+8,wid+16,h-16);
        BedrockUi.section(fontRendererObj,"MAP DETAILS",left,y+14,wid);
        int visible=Math.max(1,(h-70)/10);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-visible)));
        for(int i=0;i<visible&&i+scroll<lines.size();i++)drawString(fontRendererObj,lines.get(i+scroll),left,y+28+i*10,BedrockUi.TEXT);
        if(lines.size()>visible)drawString(fontRendererObj,fontRendererObj.trimStringToWidth("Mouse wheel: more details",wid),left,y+h-26,BedrockUi.DIM);
    }

    private List<String> detailLines(){
        if(lines!=null&&info==cachedInfo&&cachedWidth==Math.max(1,w-banner-36)&&failed==cachedFailed)return lines;
        List<String> result=new ArrayList<>();
        cachedInfo=info;cachedFailed=failed;cachedWidth=Math.max(1,w-banner-36);
        MapInfo map=info;
        if(map!=null){
            detail(result,"Objective",map.objective);
            detail(result,"Created",map.created);
            detail(result,"Players",map.players>0?String.valueOf(map.players):"Server-defined / not available");
            detail(result,"Author",map.authors);
            detail(result,"Credits",map.contributors.isEmpty()?"Not listed in map XML":map.contributors);
            detail(result,"Teams",map.teams.isEmpty()?"Server-defined / not applicable":map.teams);
            detail(result,"Variants",map.variants+" (preview: default)");
            detail(result,"Source",map.dir.startsWith("PublicMaps/")?"PublicMaps":"CommunityMaps");
            if(map.incomplete)detail(result,"Metadata","Shared rules unavailable or still loading; default variant shown.");
            detail(result,"XML",map.dir);
        }else if(failed){
            detail(result,"No match",query);
            detail(result,"Sources","Bundled maps, PublicMaps and CommunityMaps. Check the spelling or open the gallery. See the log if a repository is unavailable.");
        }else{
            detail(result,"Looking up",query);
            detail(result,"Sources","Bundled maps, then PublicMaps and CommunityMaps.");
        }
        return lines=result;
    }

    private void detail(List<String> lines,String label,String value){
        lines.addAll(fontRendererObj.listFormattedStringToWidth(label+": "+(value.isEmpty()?"-":value),Math.max(1,w-banner-36)));
        lines.add("");
    }

    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel=org.lwjgl.input.Mouse.getEventDWheel();
        if(wheel!=0)scroll=Math.max(0,scroll+(wheel>0?-3:3));
    }
}
