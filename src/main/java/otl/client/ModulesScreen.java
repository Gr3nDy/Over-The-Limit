package otl.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** OTL's compact module desk: translucent chrome, independent profile rail and 3 x 2 card viewport. */
final class ModulesScreen extends GuiScreen {
    private static final String[] CATS={"All","HUD","Visual","Mechanic"};
    private static final int SIDE=110,GAP=7,PROFILE_ROW=19;
    private final GuiScreen parent;
    private int x,y,w,h,tab,scrollRow,profileScroll;
    private boolean scrollDrag,profileDrag;
    private String query="";
    private GuiTextField search;
    private List<String> profiles=new ArrayList<>();

    ModulesScreen(GuiScreen parent){this.parent=parent;}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui(){
        UtilitySettings.load();
        w=Math.min(520,width-16);h=Math.min(300,height-16);x=(width-w)/2;y=(height-h)/2;
        profiles=Profiles.profileNames();
        scrollRow=Math.max(0,Math.min(scrollRow,rowCount()-visibleRows()));
        profileScroll=Math.max(0,Math.min(profileScroll,Math.max(0,profiles.size()-profileRows())));
        buttonList.clear();
        search=new GuiTextField(80,fontRendererObj,bodyX()+bodyW()-111,y+12,89,12);
        search.setMaxStringLength(40);
        search.setText(query);
        search.setEnableBackgroundDrawing(false);
    }

    private int bodyX(){return x+SIDE+9;}
    private int bodyW(){return w-SIDE-18;}
    private int gridY(){return y+58;}
    private int gridBottom(){return y+h-10;}
    private int cardW(){return Math.max(20,(bodyW()-14-5)/3);}
    private int cardH(){return Math.max(48,(gridBottom()-gridY()-GAP)/2);}
    private int rowStep(){return cardH()+GAP;}
    private int profileTop(){return y+60;}
    private int profileBottom(){return y+h-53;}
    private int profileRows(){return Math.max(1,(profileBottom()-profileTop())/PROFILE_ROW);}
    private int profileY(int index){return profileTop()+(index-profileScroll)*PROFILE_ROW;}
    private int tabX(int i){
        int xx=bodyX();
        for(int j=0;j<i;j++)xx+=fontRendererObj.getStringWidth(CATS[j])+14+3;
        return xx;
    }
    private int tabW(int i){return fontRendererObj.getStringWidth(CATS[i])+14;}
    private int trackX(){return bodyX()+bodyW()-3;}
    private int trackTop(){return gridY();}
    private int trackBottom(){return gridBottom();}
    private int thumbH(){return Math.max(15,(trackBottom()-trackTop())*visibleRows()/Math.max(visibleRows(),rowCount()));}
    private int thumbY(){int max=rowCount()-visibleRows();return max<=0?trackTop():trackTop()+(trackBottom()-trackTop()-thumbH())*scrollRow/max;}
    int visibleRows(){return 2;}
    int rowCount(){return (list().size()+2)/3;}
    int firstRow(){return scrollRow;}
    int visibleCount(){return list().size();}
    int visibleIndex(OtlModule m){return list().indexOf(m);}
    int[] rowBox(int index){int col=index%3,row=index/3;return new int[]{bodyX()+col*(cardW()+GAP),gridY()+row*rowStep(),cardW(),cardH()};}
    int trackLeft(){return trackX();}
    int trackTopY(){return trackTop();}
    int trackBottomY(){return trackBottom();}
    int settingsGearX(){return tabX(0);}
    int headerY(){return y+4;}
    int[] onOnlyBox(){return new int[]{tabX(0),y+37,tabW(0),16};}
    int[] profileChipBox(){return new int[]{x+6,y+40,SIDE-12,17};}
    int popupTopY(){return profileTop();}
    boolean profileMenuOpen(){return true;}

    private List<OtlModule> list(){
        String q=query.trim().toLowerCase(Locale.ROOT);
        List<OtlModule> out=new ArrayList<>();
        for(OtlModule m:OtlModule.ALL)
            if((tab==0||m.category.equalsIgnoreCase(CATS[tab]))&&
               (q.isEmpty()||m.name.toLowerCase(Locale.ROOT).contains(q)||m.category.toLowerCase(Locale.ROOT).contains(q)))out.add(m);
        Collections.sort(out,Comparator.comparingInt((OtlModule m)->m.category.equals("HUD")?0:m.category.equals("VISUAL")?1:2)
            .thenComparing(m->m.name,String.CASE_INSENSITIVE_ORDER));
        return out;
    }
    private void leave(){mc.displayGuiScreen(parent);}
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(search.isFocused()){
            if(key==Keyboard.KEY_ESCAPE){query="";search.setText("");search.setFocused(false);scrollRow=0;return;}
            if(key==Keyboard.KEY_RETURN){search.setFocused(false);return;}
            search.textboxKeyTyped(c,key);
            if(!query.equals(search.getText())){query=search.getText();scrollRow=0;}
            return;
        }
        if(key==Keyboard.KEY_ESCAPE){leave();return;}
        if(key==Keyboard.KEY_F&&isCtrlKeyDown()){search.setFocused(true);return;}
        if(c>=32&&c!=127){search.setFocused(true);search.textboxKeyTyped(c,key);query=search.getText();scrollRow=0;return;}
        super.keyTyped(c,key);
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0)scrollWheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,wheel);
    }
    void scrollWheel(int mx,int my,int wheel){
        if(BedrockUi.hit(mx,my,x+2,profileTop(),SIDE-4,profileBottom()-profileTop())){
            profileScroll=Math.max(0,Math.min(Math.max(0,profiles.size()-profileRows()),profileScroll+(wheel>0?-1:1)));
        }else if(BedrockUi.hit(mx,my,bodyX(),gridY(),bodyW(),gridBottom()-gridY())){
            scrollRow=Math.max(0,Math.min(Math.max(0,rowCount()-visibleRows()),scrollRow+(wheel>0?-1:1)));
        }
    }
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button!=0){super.mouseClicked(mx,my,button);return;}
        if(BedrockUi.hit(mx,my,x+w-22,y+6,16,16)){leave();return;}
        if(BedrockUi.hit(mx,my,x+8,y+4,SIDE-16,40)){tab=0;query="";scrollRow=0;initGui();return;}
        if(BedrockUi.hit(mx,my,bodyX(),y+8,46,20))return;
        if(BedrockUi.hit(mx,my,bodyX()+51,y+8,62,20)){
            PgmSettingsScreen settings=new PgmSettingsScreen(this);
            settings.menu=this;
            mc.displayGuiScreen(settings);
            return;
        }
        if(BedrockUi.hit(mx,my,search.xPosition-17,y+7,bodyW()-(search.xPosition-bodyX())+17,24)){search.setFocused(true);return;}
        for(int i=0;i<CATS.length;i++)if(BedrockUi.hit(mx,my,tabX(i),y+37,tabW(i),17)){
            tab=i;scrollRow=0;initGui();return;
        }
        if(clickSidebar(mx,my,this))return;
        if(list().size()>6&&BedrockUi.hit(mx,my,trackX()-4,trackTop(),8,trackBottom()-trackTop())){scrollDrag=true;scrollTo(my);return;}
        if(BedrockUi.hit(mx,my,bodyX(),gridY(),bodyW()-8,gridBottom()-gridY())){
            int col=(mx-bodyX())/(cardW()+GAP),row=(my-gridY())/rowStep();
            if(col>=3||row>=2)return;
            int[] box=rowBox(row*3+col);
            if(!BedrockUi.hit(mx,my,box[0],box[1],box[2],box[3]))return;
            int index=(scrollRow+row)*3+col;
            List<OtlModule> shown=list();if(index>=shown.size())return;
            OtlModule m=shown.get(index);if(!m.available())return;
            if(my>=box[1]+box[3]-20){m.setEnabled(!m.enabled());UtilitySettings.save();return;}
            GuiScreen settings=m.settings(this);
            if(settings instanceof SettingsScreen)((SettingsScreen)settings).menu=this;
            if(settings!=null)mc.displayGuiScreen(settings);
        }
    }
    boolean clickSidebar(int mx,int my,GuiScreen back){
        if(BedrockUi.hit(mx,my,x+8,y+4,SIDE-16,40)){
            tab=0;query="";scrollRow=0;initGui();mc.displayGuiScreen(this);return true;
        }
        if(BedrockUi.hit(mx,my,x+5,y+h-48,SIDE-10,20)){Profiles.newProfile();initGui();return true;}
        if(BedrockUi.hit(mx,my,x+5,y+h-26,SIDE-10,20)){mc.displayGuiScreen(new ProfileTransferScreen(back,null));return true;}
        if(profiles.size()>profileRows()&&BedrockUi.hit(mx,my,x+SIDE-7,profileTop(),7,profileBottom()-profileTop())){
            profileDrag=true;scrollProfilesTo(my);return true;
        }
        if(BedrockUi.hit(mx,my,x+4,profileTop(),SIDE-8,profileBottom()-profileTop())){
            int at=(my-profileTop())/PROFILE_ROW+profileScroll;
            if(at>=profiles.size())return true;
            String name=profiles.get(at);
            if(mx>=x+SIDE-21){mc.displayGuiScreen(new ProfileTransferScreen(back,name));return true;}
            if(mx>=x+SIDE-37&&!name.equals("Default")){mc.displayGuiScreen(new ProfileEditScreen(back,name));return true;}
            Profiles.switchProfile(name);initGui();return true;
        }
        return false;
    }
    @Override protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(scrollDrag)scrollTo(my);
        if(profileDrag)scrollProfilesTo(my);
        super.mouseClickMove(mx,my,button,elapsed);
    }
    @Override protected void mouseReleased(int mx,int my,int button){scrollDrag=false;profileDrag=false;super.mouseReleased(mx,my,button);}
    private void scrollTo(int my){
        int max=Math.max(0,rowCount()-visibleRows());
        double t=(my-trackTop()-thumbH()/2.0)/Math.max(1,trackBottom()-trackTop()-thumbH());
        scrollRow=Math.max(0,Math.min(max,(int)Math.round(t*max)));
    }
    private void scrollProfilesTo(int my){
        int max=Math.max(0,profiles.size()-profileRows());
        int height=profileBottom()-profileTop(),thumb=Math.max(10,height*profileRows()/Math.max(1,profiles.size()));
        double t=(my-profileTop()-thumb/2.0)/Math.max(1,height-thumb);
        profileScroll=Math.max(0,Math.min(max,(int)Math.round(t*max)));
    }
    @Override public void drawScreen(int mx,int my,float partial){
        // 1.8.9 never calls mouseClickMove during drags, so the held position is polled here.
        if(scrollDrag)scrollTo(my);
        if(profileDrag)scrollProfilesTo(my);
        drawRect(0,0,width,height,0x10000000);
        BedrockUi.menuSkin(0,x,y,w,h);
        drawSidebar(mx,my);
        drawTop(mx,my);
        drawCards(mx,my);
        super.drawScreen(mx,my,partial);
    }
    void drawSidebar(int mx,int my){
        BedrockUi.menuSkin(1,x+1,y+1,SIDE-1,h-2);
        BedrockUi.menuLogo(x+7,y+4);
        drawString(fontRendererObj,"PROFILES",x+9,y+46,0xFFBFC6BE);
        BedrockUi.menuSkin(2,x+5,profileTop()-2,SIDE-10,profileBottom()-profileTop()+4);
        int end=Math.min(profiles.size(),profileScroll+profileRows());
        for(int i=profileScroll;i<end;i++){
            int yy=profileY(i);String name=profiles.get(i);
            boolean active=name.equals(UtilitySettings.profile),hover=BedrockUi.hit(mx,my,x+5,yy,SIDE-10,PROFILE_ROW);
            if(active)BedrockUi.menuSkin(11,x+6,yy,SIDE-11,PROFILE_ROW);
            int max=SIDE-(hover?54:20);
            String shown=name;
            if(fontRendererObj.getStringWidth(name)>max){
                if(active){
                    int over=Math.max(0,name.length()-Math.max(1,max/6));
                    int offset=(int)((System.currentTimeMillis()/430)%Math.max(1,2*over));
                    shown=name.substring(Math.max(0,Math.min(over,offset>over?2*over-offset:offset)));
                }
                shown=fontRendererObj.trimStringToWidth(shown,max);
            }
            drawString(fontRendererObj,shown,x+12,yy+6,active?0xFFF4F1E8:0xFFBFC6BE);
            if(hover){
                if(!name.equals("Default"))BedrockUi.menuIcon(2,x+SIDE-38,yy+1,mx>=x+SIDE-37&&mx<x+SIDE-21);
                BedrockUi.menuIcon(1,x+SIDE-22,yy+1,mx>=x+SIDE-21);
            }
        }
        if(profiles.size()>profileRows()){
            int bar=profileBottom()-profileTop(),thumb=Math.max(10,bar*profileRows()/profiles.size());
            int pos=profileTop()+(bar-thumb)*profileScroll/Math.max(1,profiles.size()-profileRows());
            BedrockUi.rounded(x+SIDE-5,pos,2,thumb,1,0xFF87928A);
        }
        drawAction(x+5,y+h-48,"profile_new",mx,my);
        drawAction(x+5,y+h-26,"profile_import",mx,my);
    }
    private void drawTop(int mx,int my){
        BedrockUi.menuSkin(6,bodyX()-3,y+4,bodyW()+5,28);
        BedrockUi.menuText("nav_modules_active",7,bodyX()+27,y+19,49);
        BedrockUi.menuText("nav_settings",8,bodyX()+97,y+19,56);
        if(BedrockUi.hit(mx,my,serviceX()-3,y+4,fontRendererObj.getStringWidth(serviceLabel())+16,20))drawHoveringText(serviceTip(),mx,my);
        drawService(mx,my);
        BedrockUi.menuSkin(6,search.xPosition-17,y+8,106,20);
        BedrockUi.menuIcon(4,search.xPosition-17,y+10,false);
        if(query.isEmpty())drawString(fontRendererObj,"Search",search.xPosition,y+14,0xFFBFC6BE);
        search.drawTextBox();
        BedrockUi.menuSkin(6,x+w-22,y+6,16,16);
        BedrockUi.menuIcon(3,x+w-22,y+6,BedrockUi.hit(mx,my,x+w-22,y+6,16,16));
        for(int i=0;i<CATS.length;i++){
            boolean hot=BedrockUi.hit(mx,my,tabX(i),y+37,tabW(i),17);
            BedrockUi.menuSkin(tab==i?12:hot?7:6,tabX(i),y+37,tabW(i),17);
            drawCenteredString(fontRendererObj,CATS[i],tabX(i)+tabW(i)/2,y+41,tab==i?0xFFFFFFFF:0xFFBFC6BE);
        }
        drawString(fontRendererObj,list().size()+"/"+OtlModule.ALL.size(),bodyX()+bodyW()-27,y+43,0xFFBFC6BE);
    }
    /** "Online" plus how many other OTL users are in this world, e.g. "Online \u00b7 3". */
    private String serviceLabel(){
        int status=OtlService.status();
        String label=OtlService.statusLabel(status);
        if(status==OtlService.ONLINE)label=label+" \u00b7 "+OtlPresence.here().size();
        return label;
    }
    /** The player list, as the tooltip behind the light: the other OTL users standing here. */
    private List<String> serviceTip(){
        List<String> tip=new ArrayList<>();
        int status=OtlService.status();
        List<String> here=OtlPresence.here();
        tip.add("OTL service: "+OtlService.statusLabel(status));
        if(status!=OtlService.ONLINE){tip.add("Board sharing and other online features are idle");return tip;}
        if(here.isEmpty()){tip.add("No other OTL users in this world");return tip;}
        tip.add(here.size()==1?"1 other OTL user here:":here.size()+" other OTL users here:");
        int shown=Math.min(here.size(),8);
        for(int i=0;i<shown;i++)tip.add("  "+here.get(i));
        if(here.size()>shown)tip.add("  and "+(here.size()-shown)+" more");
        return tip;
    }
    private int serviceX(){return search.xPosition-12-fontRendererObj.getStringWidth(serviceLabel())-9;}
    /** The service light: a dot plus the "Online \u00b7 N" word, just left of the search box. */
    private void drawService(int mx,int my){
        String label=serviceLabel();
        int x=serviceX();
        // A narrow window pushes the light into the nav strip, so it is simply not drawn.
        if(x<bodyX()+fontRendererObj.getStringWidth("Modules")+6)return;
        int status=OtlService.status();
        boolean hover=BedrockUi.hit(mx,my,x-3,y+4,fontRendererObj.getStringWidth(label)+16,20);
        BedrockUi.rounded(x,y+10,6,6,2,OtlService.statusColour(status));
        drawString(fontRendererObj,label,x+10,y+9,status==OtlService.ONLINE?BedrockUi.TEXT:hover?BedrockUi.TEXT:BedrockUi.DIM);
    }
    private void drawCards(int mx,int my){
        List<OtlModule> shown=list();
        if(shown.isEmpty())drawString(fontRendererObj,"No matching modules",bodyX()+8,gridY()+12,0xFFE2E7DF);
        for(int i=0;i<6&&scrollRow*3+i<shown.size();i++){
            int[] b=rowBox(i);int cx=b[0],cy=b[1],cw=b[2],ch=b[3];OtlModule m=shown.get(scrollRow*3+i);
            boolean hover=BedrockUi.hit(mx,my,cx,cy,cw,ch);
            BedrockUi.menuSkin(hover?4:3,cx,cy,cw,ch);
            int pictureY=cy+3;
            BedrockUi.menuSkin(5,cx+3,pictureY,cw-6,Math.max(12,ch-40));
            mc.getTextureManager().bindTexture(new ResourceLocation("otl","textures/modules/"+m.id+".png"));
            float scale=ch<88?.75F:1F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx+cw/2-32*scale,cy+Math.max(2,(ch-39-(int)(44*scale))/2),0);
            GlStateManager.scale(scale,scale,1);
            Gui.drawModalRectWithCustomSizedTexture(0,0,0,0,64,44,64,44);
            GlStateManager.popMatrix();
            BedrockUi.menuText(m.id,m.name.length(),cx+cw/2,cy+ch-30,cw-12);
            BedrockUi.menuIcon(0,cx+cw-19,cy+3,hover);
            BedrockUi.menuSkin(m.available()?(m.enabled()?8:9):10,cx+1,cy+ch-20,cw-2,19);
            String state=m.available()?(m.enabled()?"enabled":"disabled"):"locked";
            BedrockUi.menuText(state,state.length(),cx+cw/2,cy+ch-10,cw-10);
        }
        if(shown.size()>6){
            int height=trackBottom()-trackTop();
            BedrockUi.rounded(trackX(),trackTop(),3,height,1,0xA02B342E);
            BedrockUi.rounded(trackX(),thumbY(),3,thumbH(),1,0xFF9AA9A0);
        }
    }
    private void drawAction(int ax,int ay,String texture,int mx,int my){
        String suffix=BedrockUi.hit(mx,my,ax,ay,SIDE-10,20)?"_hover":"";
        mc.getTextureManager().bindTexture(new ResourceLocation("otl","textures/menu/"+texture+suffix+".png"));
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(ax,ay,0,0,100,20,100,20);
    }
}
