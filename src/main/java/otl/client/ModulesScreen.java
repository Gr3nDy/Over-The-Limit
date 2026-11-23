package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Module browser: one row per module, filtered by search and category, with the settings for a
 * module opening on click. Profiles live in a footer menu so the list gets the full panel width.
 *
 * <p>Rows are 22 tall and carry everything on one line - icon, name, what it does, and its switch -
 * so nine modules are visible at once and nothing is truncated the way the old card grid was.
 */
final class ModulesScreen extends GuiScreen {
    private static final String[] TABS={"ALL","HUD","VISUAL","MECHANIC"};
    private static final String[] TAB_LABELS={"All","Hud","Visual","Mechanic"};
    private static final int PAD=12,HEADER=30,SEARCH_Y=38,SEARCH_H=20,LIST_Y=64,ROW_H=22,FOOTER=28;
    private static final int POPUP_W=172,POPUP_ROW=18,POPUP_PAD=6;
    private final GuiScreen parent;
    private int x,y,w,h,tab,scrollRow,popupScroll;
    private List<String> profiles=new ArrayList<>();
    private GuiTextField renameField,search;
    private String editing,status="",query="";
    private boolean scrollDrag,enabledOnly,profileOpen,profileDrag;
    private String dragName;

    ModulesScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}

    public void initGui(){
        UtilitySettings.load();
        // Match the compact Lunar modal proportions at GUI scale 2; do not grow to fill the screen.
        w=Math.min(520,width-16);h=Math.min(300,height-16);x=(width-w)/2;y=(height-h)/2;
        profiles=Profiles.profileNames();
        scrollRow=Math.max(0,Math.min(scrollRow,list().size()-visibleRows()));
        popupScroll=Math.max(0,Math.min(popupScroll,Math.max(0,profiles.size()-popupRows())));
        status="";
        buttonList.clear();
        buttonList.add(new PillButton(3,x+w-PAD-54,y+h-FOOTER+5,54,18,"DONE"));
        search=new GuiTextField(80,fontRendererObj,listX()+18,SEARCH_Y+y+5,searchW()-24,12);
        search.setMaxStringLength(40);
        search.setText(query);
        search.setEnableBackgroundDrawing(false);
        search.setFocused(!query.isEmpty());
        renameField=null;
        if(editing==null)return;
        int row=profiles.indexOf(editing);
        if(row<0||row<popupScroll||row>=popupScroll+popupRows()){editing=null;return;}
        renameField=new GuiTextField(90,fontRendererObj,popupX()+6,popupRowY(row),POPUP_W-12,12);
        renameField.setMaxStringLength(24);
        renameField.setText(editing);
        renameField.setFocused(true);
        renameField.setCursorPositionEnd();
    }

    // ── geometry ────────────────────────────────────────────────────────────────────────────
    private int listX(){return x+PAD;}
    private int listW(){return w-2*PAD;}
    /** Rows that fit between the filter strip and the footer. */
    int visibleRows(){return Math.max(1,(h-FOOTER-4-LIST_Y)/ROW_H);}
    private int rowY(int index){return y+LIST_Y+(index-scrollRow)*ROW_H;}
    private int trackX(){return x+w-6;}
    private int trackTop(){return y+LIST_Y;}
    private int trackBottom(){return y+h-FOOTER-4;}
    private int trackH(){return trackBottom()-trackTop();}
    private int thumbH(){int total=list().size();return Math.max(16,trackH()*visibleRows()/Math.max(visibleRows(),total));}
    private int thumbY(){int max=list().size()-visibleRows();return max<=0?trackTop():trackTop()+(trackH()-thumbH())*scrollRow/max;}
    private boolean overTrack(int mx,int my){return mx>=trackX()&&mx<trackX()+6&&my>=trackTop()&&my<trackBottom()&&list().size()>visibleRows();}
    private int switchX(){return x+w-PAD-7-18;}
    private int chipY(){return y+SEARCH_Y+1;}
    private int chipH(){return 18;}
    /** The search box is the only thing that gives up width, so tabs and filter never overlap. */
    private int searchW(){
        int tabs=0;
        for(int i=0;i<TABS.length;i++)tabs+=tabWidth(i)+4;
        return Math.max(70,Math.min(188,listW()-tabs-filterW()-18));
    }
    private int tabX(int i){
        int tx=listX()+searchW()+8;
        for(int j=0;j<i;j++)tx+=fontRendererObj.getStringWidth(TAB_LABELS[j])+14+4;
        return tx;
    }
    private int tabWidth(int i){return fontRendererObj.getStringWidth(TAB_LABELS[i])+14;}
    private int filterX(){return listX()+listW()-filterW();}
    private int filterW(){return fontRendererObj.getStringWidth("On only")+14;}
    private int pillW(){return 7+6+5+fontRendererObj.getStringWidth(serviceLabel())+9;}
    private int pillX(){return x+w-PAD-pillW();}
    private int gearX(){return pillX()-8-18;}
    private int popupX(){return listX()-2;}
    private int popupRows(){return Math.max(1,(h-FOOTER-LIST_Y-POPUP_PAD*2-36)/POPUP_ROW);}
    private int popupRowY(int index){return popupTop()+POPUP_PAD+(index-popupScroll)*POPUP_ROW;}
    private int popupTop(){return y+h-FOOTER-4-popupH();}
    private int popupH(){return POPUP_PAD*2+Math.min(profiles.size(),popupRows())*POPUP_ROW+36;}
    private int popupActionY(int index){return popupTop()+POPUP_PAD+Math.min(profiles.size(),popupRows())*POPUP_ROW+4+index*POPUP_ROW;}

    // ── data ────────────────────────────────────────────────────────────────────────────────
    private List<OtlModule> list(){
        String q=query.trim().toLowerCase(Locale.ROOT);
        List<OtlModule> out=new ArrayList<>();
        for(OtlModule m:OtlModule.ALL){
            boolean cat=q.isEmpty()?(tab==0||m.category.equals(TABS[tab])):true;
            if(cat&&matches(m,q)&&(!enabledOnly||m.enabled()))out.add(m);
        }
        Collections.sort(out,(a,b)->{
            int ca=categoryRank(a.category),cb=categoryRank(b.category);
            if(ca!=cb)return ca-cb;
            int oa=moduleRank(a.id),ob=moduleRank(b.id);
            return oa==ob?a.name.compareToIgnoreCase(b.name):oa-ob;
        });
        return out;
    }
    private int categoryRank(String category){
        if("HUD".equals(category))return 0;
        if("VISUAL".equals(category))return 1;
        return 2;
    }
    /** Stable product order: high-frequency modules first, niche tools later. */
    private int moduleRank(String id){
        String order="reliable_chat team_bar kill_feed kill_confirm map_preview face_rig player_animation minimap left_hand bow_optimizer auto_gg region_overlay touchdown custom_votebook";
        int at=order.indexOf(id);
        return at<0?10000:at;
    }
    private boolean matches(OtlModule m,String q){
        if(q.isEmpty())return true;
        if(m.name.toLowerCase(Locale.ROOT).contains(q))return true;
        String desc=DESC.get(m.id);
        return (desc!=null&&desc.toLowerCase(Locale.ROOT).contains(q))||m.category.toLowerCase(Locale.ROOT).contains(q);
    }
    private void close(){mc.displayGuiScreen(parent);}

    // ── input ───────────────────────────────────────────────────────────────────────────────
    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==3)close();
    }

    private void commitRename(){
        String source=editing;editing=null;
        String text=renameField==null?null:renameField.getText();
        renameField=null;
        if(source==null)return;
        if(text!=null){
            String result=Profiles.renameProfile(source,text);
            initGui();
            status=result.equals(source)?"Name unchanged":"";
            return;
        }
        initGui();
    }
    private void copyProfile(String name){mc.displayGuiScreen(new ProfileTransferScreen(this,name));}
    private void importProfile(){mc.displayGuiScreen(new ProfileTransferScreen(this,null));}

    protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(editing!=null){
            if(keyCode==Keyboard.KEY_RETURN){commitRename();return;}
            if(keyCode==Keyboard.KEY_ESCAPE){editing=null;renameField=null;initGui();return;}
            if(renameField!=null)renameField.textboxKeyTyped(typedChar,keyCode);
            return;
        }
        if(profileOpen&&keyCode==Keyboard.KEY_ESCAPE){profileOpen=false;return;}
        if(search!=null&&search.isFocused()){
            if(keyCode==Keyboard.KEY_ESCAPE){search.setText("");query="";scrollRow=0;search.setFocused(false);initGui();return;}
            if(keyCode==Keyboard.KEY_RETURN){search.setFocused(false);return;}
            if(keyCode==Keyboard.KEY_F&&GuiScreen.isCtrlKeyDown()){search.setText("");query="";scrollRow=0;return;}
            search.textboxKeyTyped(typedChar,keyCode);
            if(!search.getText().equals(query)){query=search.getText();scrollRow=0;}
            return;
        }
        if(keyCode==Keyboard.KEY_ESCAPE){close();return;}
        if(keyCode==Keyboard.KEY_F&&GuiScreen.isCtrlKeyDown()){search.setFocused(true);return;}
        // Typing anywhere in the menu starts a search, like a launcher's mod list.
        if(typedChar>=32&&typedChar!=127&&search!=null){
            search.setFocused(true);
            search.textboxKeyTyped(typedChar,keyCode);
            if(!search.getText().equals(query)){query=search.getText();scrollRow=0;}
            return;
        }
        super.keyTyped(typedChar,keyCode);
    }

    public void handleMouseInput()throws IOException{
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0)scrollWheel(Mouse.getEventX()*width/mc.displayWidth,height-Mouse.getEventY()*height/mc.displayHeight-1,wheel);
        super.handleMouseInput();
    }

    /** One wheel notch: the open profile menu scrolls its own list, the wheel elsewhere scrolls modules. */
    void scrollWheel(int mx,int my,int wheel){
        if(profileOpen&&BedrockUi.hit(mx,my,popupX(),popupTop(),POPUP_W,popupH())){
            popupScroll=Math.max(0,Math.min(popupScroll+(wheel>0?-1:1),Math.max(0,profiles.size()-popupRows())));
            return;
        }
        scrollRow=Math.max(0,Math.min(scrollRow+(wheel>0?-1:1),Math.max(0,list().size()-visibleRows())));
    }

    protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(editing!=null){
            if(renameField!=null&&BedrockUi.hit(mx,my,renameField.xPosition,renameField.yPosition,renameField.width,renameField.height)){
                renameField.mouseClicked(mx,my,button);return;
            }
            commitRename();
            return;
        }
        if(button!=0){super.mouseClicked(mx,my,button);return;}
        if(profileOpen){
            // Any click outside the menu dismisses it, including one on the footer chip that opened it.
            if(!BedrockUi.hit(mx,my,popupX(),popupTop(),POPUP_W,popupH())){profileOpen=false;return;}
            if(clickProfileMenu(mx,my))return;
        }
        for(GuiButton b:buttonList)if(b.visible&&b.enabled&&mx>=b.xPosition&&mx<b.xPosition+b.width&&my>=b.yPosition&&my<b.yPosition+b.height){
            super.mouseClicked(mx,my,button);return;
        }
        if(BedrockUi.hit(mx,my,x+6,y+4,18,22)){close();return;}
        if(BedrockUi.hit(mx,my,gearX(),y+6,18,18)){mc.displayGuiScreen(new PgmSettingsScreen(this));return;}
        if(search!=null&&BedrockUi.hit(mx,my,listX(),chipY(),searchW(),SEARCH_H)){search.setFocused(true);return;}
        for(int i=0;i<TABS.length;i++){
            if(BedrockUi.hit(mx,my,tabX(i),chipY(),tabWidth(i),chipH())){
                tab=i;query="";if(search!=null){search.setText("");search.setFocused(false);}
                scrollRow=0;initGui();return;
            }
        }
        if(BedrockUi.hit(mx,my,filterX(),chipY(),filterW(),chipH())){
            enabledOnly=!enabledOnly;scrollRow=0;initGui();return;
        }
        if(BedrockUi.hit(mx,my,listX(),y+h-FOOTER+3,popupChipW(),20)){profileOpen=!profileOpen;initGui();return;}
        if(overTrack(mx,my)){scrollDrag=true;scrollTo(my);return;}
        for(int i=0;i<visibleRows();i++){
            int index=scrollRow+i;
            if(index>=list().size())return;
            int ry=rowY(index);
            if(my<ry||my>=ry+ROW_H)continue;
            OtlModule m=list().get(index);
            if(!m.available())return;
            if(mx>=switchX()-6){
                m.setEnabled(!m.enabled());UtilitySettings.save();initGui();return;
            }
            GuiScreen s=m.settings(this);if(s!=null)mc.displayGuiScreen(s);return;
        }
        super.mouseClicked(mx,my,button);
    }

    /** Profile menu clicks: pick, rename, copy, delete, or the two actions at the bottom. */
    private boolean clickProfileMenu(int mx,int my){
        int shown=Math.min(profiles.size(),popupRows());
        for(int i=0;i<shown;i++){
            int index=popupScroll+i;
            if(!BedrockUi.hit(mx,my,popupX(),popupRowY(index),POPUP_W,POPUP_ROW))continue;
            String name=profiles.get(index);
            int renameX=popupX()+POPUP_W-44,copyX=renameX+15,deleteX=copyX+15;
            if(mx>=deleteX){
                if(name.equals(UtilitySettings.profile)||name.equals("Default"))return true;
                mc.displayGuiScreen(new GuiYesNo((yes,id)->{
                    if(yes)Profiles.deleteProfile(name);
                    // displayGuiScreen re-inits this screen, which reloads the list behind the dialog.
                    mc.displayGuiScreen(this);
                },"Delete profile \""+name+"\"?","This cannot be undone.",0));
                return true;
            }
            if(mx>=copyX){copyProfile(name);return true;}
            if(mx>=renameX){editing=name;initGui();return true;}
            Profiles.switchProfile(name);initGui();return true;
        }
        if(BedrockUi.hit(mx,my,popupX(),popupActionY(0),POPUP_W,POPUP_ROW)){Profiles.newProfile();initGui();return true;}
        if(BedrockUi.hit(mx,my,popupX(),popupActionY(1),POPUP_W,POPUP_ROW)){importProfile();return true;}
        return true;
    }

    protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(scrollDrag)scrollTo(my);
        if(profileDrag&&dragName!=null&&button==0&&BedrockUi.hit(mx,my,popupX(),popupTop(),POPUP_W,popupH())){
            int from=profiles.indexOf(dragName),to=popupScroll+(my-popupTop()-POPUP_PAD)/POPUP_ROW;
            to=Math.max(0,Math.min(profiles.size()-1,to));
            if(from>=0&&to!=from){profiles.add(to,profiles.remove(from));Profiles.saveProfileOrder(profiles);}
        }
        super.mouseClickMove(mx,my,button,elapsed);
    }
    protected void mouseReleased(int mx,int my,int state){
        scrollDrag=false;profileDrag=false;dragName=null;
        super.mouseReleased(mx,my,state);
    }

    private void scrollTo(int my){
        int max=Math.max(0,list().size()-visibleRows());
        if(max==0){scrollRow=0;return;}
        double t=(my-trackTop()-thumbH()/2D)/Math.max(1,trackH()-thumbH());
        scrollRow=Math.max(0,Math.min(max,(int)Math.round(t*max)));
    }

    // ── drawing ─────────────────────────────────────────────────────────────────────────────
    public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,0x40101010);
        BedrockUi.panel(x,y,w,h);
        drawHeader(mx,my);
        drawFilterRow(mx,my);
        drawList(mx,my);
        drawFooter(mx,my);
        if(profileOpen)drawProfileMenu(mx,my);
        super.drawScreen(mx,my,partial);
        drawTips(mx,my);
    }

    private void drawHeader(int mx,int my){
        drawRect(x+2,y+HEADER-1,x+w-2,y+HEADER,BedrockUi.EDGE);
        BedrockUi.backArrow(fontRendererObj,x+4,y+5,BedrockUi.hit(mx,my,x+6,y+4,18,22));
        BedrockUi.title2x(fontRendererObj,"MODULES",x+30,y+7,BedrockUi.TEXT);
        BedrockUi.gear(gearX(),y+8,BedrockUi.hit(mx,my,gearX(),y+6,18,18));
        int status=OtlService.status();
        int px=pillX(),pw=pillW();
        BedrockUi.capsule(px,y+6,pw,18,BedrockUi.SLOT);
        BedrockUi.capsule(px+7,y+12,6,6,OtlService.statusColour(status));
        drawString(fontRendererObj,serviceLabel(),px+18,y+11,
            status==OtlService.ONLINE?BedrockUi.TEXT:BedrockUi.MUTED);
    }

    private void drawFilterRow(int mx,int my){
        BedrockUi.rounded(listX(),chipY(),searchW(),SEARCH_H,2,BedrockUi.SLOT);
        if(search.isFocused())BedrockUi.outline(listX(),chipY(),searchW(),SEARCH_H,BedrockTokens.STATE_SELECTED);
        drawSearchIcon(listX()+7,chipY()+7,BedrockUi.DIM);
        if(search.getText().isEmpty())
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth("Search modules",searchW()-26),
                listX()+18,chipY()+6,BedrockUi.DIM);
        search.drawTextBox();
        for(int i=0;i<TABS.length;i++){
            BedrockUi.chip(fontRendererObj,tabX(i),chipY(),tabWidth(i),chipH(),TAB_LABELS[i],
                tab==i&&query.isEmpty(),BedrockUi.hit(mx,my,tabX(i),chipY(),tabWidth(i),chipH()));
        }
        BedrockUi.chip(fontRendererObj,filterX(),chipY(),filterW(),chipH(),"On only",
            enabledOnly,BedrockUi.hit(mx,my,filterX(),chipY(),filterW(),chipH()));
    }

    private void drawList(int mx,int my){
        List<OtlModule> list=list();
        if(list.isEmpty()){
            String empty=query.isEmpty()?"No enabled modules":"No matches";
            drawCenteredString(fontRendererObj,empty,(listX()+x+w-PAD)/2,y+LIST_Y+rowHint()/2-4,BedrockUi.MUTED);
        }
        for(int i=0;i<visibleRows();i++){
            int index=scrollRow+i;
            if(index>=list.size())break;
            drawRow(list.get(index),index,rowY(index),mx,my);
        }
        if(list.size()>visibleRows()){
            BedrockUi.rounded(trackX(),trackTop(),3,trackH(),1,BedrockUi.INSET);
            boolean grab=scrollDrag||BedrockUi.hit(mx,my,trackX()-2,thumbY(),7,thumbH());
            BedrockUi.rounded(trackX(),thumbY(),3,thumbH(),1,grab?0xFFB0B0B0:0xFF6F747D);
        }
    }
    private int rowHint(){return Math.min(ROW_H*visibleRows(),40);}

    /** One module: tinted icon, name, what it does, and its switch, all on the same line. */
    private void drawRow(OtlModule m,int index,int ry,int mx,int my){
        boolean hover=BedrockUi.hit(mx,my,listX(),ry,listW(),ROW_H);
        boolean usable=m.available();
        if(hover&&usable)BedrockUi.rounded(listX(),ry,listW(),ROW_H,2,BedrockTokens.STATE_HOVER);
        int tint=categoryColour(m);
        int iconX=listX()+7,iconY=ry+5;
        BedrockUi.rounded(iconX,iconY,12,12,2,(0x2B<<24)|(tint&0xFFFFFF));
        drawIcon(m.id,iconX+1,iconY+1,tint);
        int nameX=listX()+27;
        String name=fontRendererObj.trimStringToWidth(m.name,110);
        drawString(fontRendererObj,name,nameX,ry+7,usable?BedrockUi.TEXT:BedrockTokens.STATE_LOCKED);
        if(usable){
            int descX=nameX+fontRendererObj.getStringWidth(name)+8;
            int descW=switchX()-8-descX;
            String desc=DESC.get(m.id);
            if(desc!=null&&descW>10)
                drawString(fontRendererObj,fontRendererObj.trimStringToWidth(desc,descW),descX,ry+7,BedrockUi.DIM);
            drawSwitch(switchX(),ry+6,18,10,m.enabled());
        }else{
            String label="LOCKED";
            drawString(fontRendererObj,label,switchX()+18-fontRendererObj.getStringWidth(label),ry+7,BedrockTokens.STATE_LOCKED);
        }
    }

    private void drawFooter(int mx,int my){
        int cy=y+h-FOOTER+5;
        int cw=popupChipW();
        BedrockUi.rounded(listX(),cy,cw,18,2,BedrockTokens.STATE_HOVER);
        drawString(fontRendererObj,"Profile",listX()+7,cy+6,BedrockUi.DIM);
        String name=fontRendererObj.trimStringToWidth(UtilitySettings.profile,cw-58);
        drawString(fontRendererObj,name,listX()+7+fontRendererObj.getStringWidth("Profile")+6,cy+6,BedrockUi.TEXT);
        drawString(fontRendererObj,"v",listX()+cw-9,cy+6,BedrockUi.DIM);
        int on=0;for(OtlModule m:OtlModule.ALL)if(m.enabled())on++;
        String count=on+" of "+OtlModule.ALL.size()+" on";
        drawString(fontRendererObj,count,listX()+cw+10,cy+6,BedrockUi.DIM);
        if(!status.isEmpty())
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(status,110),
                x+w-PAD-54-8-fontRendererObj.getStringWidth(fontRendererObj.trimStringToWidth(status,110)),cy+6,
                BedrockUi.MUTED);
    }
    private int popupChipW(){return 7+fontRendererObj.getStringWidth("Profile")+6
        +fontRendererObj.getStringWidth(fontRendererObj.trimStringToWidth(UtilitySettings.profile,90))+14;}

    /** Profile menu: pick, rename, copy or delete, plus the two actions the rail used to hold. */
    private void drawProfileMenu(int mx,int my){
        int px=popupX(),top=popupTop();
        BedrockUi.rounded(px,top,POPUP_W,popupH(),2,0xF212141B);
        BedrockUi.outline(px,top,POPUP_W,popupH(),BedrockUi.EDGE);
        int shown=Math.min(profiles.size(),popupRows());
        for(int i=0;i<shown;i++){
            int index=popupScroll+i;
            String name=profiles.get(index);
            int ry=popupRowY(index);
            boolean active=name.equals(UtilitySettings.profile),hover=BedrockUi.hit(mx,my,px,ry,POPUP_W,POPUP_ROW);
            if(active)BedrockUi.rounded(px+3,ry,POPUP_W-6,POPUP_ROW,2,BedrockTokens.STATE_HOVER);
            if(name.equals(editing))continue;
            int renameX=px+POPUP_W-44,copyX=renameX+15,deleteX=copyX+15;
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(name,POPUP_W-52),px+8,ry+5,
                active?0xFFFFFFFF:BedrockUi.TEXT);
            if(hover&&active)drawRect(px+3,ry,px+5,ry+POPUP_ROW,BedrockUi.ACCENT);
            if(hover){
                BedrockUi.pencil(renameX,ry+3,mx>=renameX&&mx<renameX+13);
                BedrockUi.duplicate(copyX,ry+3,mx>=copyX&&mx<copyX+13);
                BedrockUi.icon(fontRendererObj,deleteX,ry+3,"x",mx>=deleteX,
                    !active&&!name.equals("Default"));
            }
        }
        if(profiles.size()>shown){
            drawString(fontRendererObj,(profiles.size()-shown)+" more...",px+8,popupActionY(0)-10,BedrockUi.DIM);
        }
        BedrockUi.rounded(px+POPUP_PAD,popupActionY(0)-4,POPUP_W-POPUP_PAD*2,1,1,BedrockUi.EDGE);
        int y0=popupActionY(0);
        if(BedrockUi.hit(mx,my,px,y0,POPUP_W,POPUP_ROW))
            BedrockUi.rounded(px+3,y0,POPUP_W-6,POPUP_ROW,2,BedrockTokens.STATE_HOVER);
        drawString(fontRendererObj,"+ New profile",px+8,y0+5,BedrockUi.TEXT);
        int y1=popupActionY(1);
        if(BedrockUi.hit(mx,my,px,y1,POPUP_W,POPUP_ROW))
            BedrockUi.rounded(px+3,y1,POPUP_W-6,POPUP_ROW,2,BedrockTokens.STATE_HOVER);
        drawString(fontRendererObj,"Import profile",px+8,y1+5,BedrockUi.TEXT);
    }

    private void drawTips(int mx,int my){
        if(profileOpen){
            int shown=Math.min(profiles.size(),popupRows());
            for(int i=0;i<shown;i++){
                String name=profiles.get(popupScroll+i);
                if(BedrockUi.hit(mx,my,popupX(),popupRowY(popupScroll+i),POPUP_W,POPUP_ROW)&&name.equals(UtilitySettings.profile))
                    drawHoveringText(Arrays.asList("Click to select","Pencil renames, pages copy","X deletes"),mx,my);
            }
            return;
        }
        if(BedrockUi.hit(mx,my,x+6,y+4,18,22)){drawHoveringText(Arrays.asList("Back"),mx,my);return;}
        if(BedrockUi.hit(mx,my,gearX(),y+6,18,18)){
            drawHoveringText(Arrays.asList("PGM settings","Client and server options"),mx,my);return;
        }
        if(BedrockUi.hit(mx,my,listX(),chipY(),searchW(),SEARCH_H)&&!search.isFocused()){
            drawHoveringText(Arrays.asList("Type to search, ESC clears"),mx,my);return;
        }
        if(BedrockUi.hit(mx,my,pillX(),y+6,pillW(),18)){drawHoveringText(serviceTip(),mx,my);return;}
        List<OtlModule> list=list();
        for(int i=0;i<visibleRows();i++){
            int index=scrollRow+i;
            if(index>=list.size())break;
            int ry=rowY(index);
            if(!BedrockUi.hit(mx,my,listX(),ry,listW(),ROW_H))continue;
            OtlModule m=list.get(index);
            List<String> tip=new ArrayList<>();
            tip.add(m.name);
            String desc=DESC.get(m.id);
            if(desc!=null)tip.addAll(fontRendererObj.listFormattedStringToWidth(desc,190));
            tip.add(m.available()?"Click to configure":"Unavailable on this server");
            drawHoveringText(tip,mx,my);
            return;
        }
    }

    /** "Online" plus how many other OTL users are in this world, e.g. "Online · 3". */
    private String serviceLabel(){
        int status=OtlService.status();
        String label=OtlService.statusLabel(status);
        if(status==OtlService.ONLINE)label=label+" · "+OtlPresence.here().size();
        return label;
    }
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

    /** Original card glyphs, 10x10 in the module's category tint. */
    private void drawIcon(String id,int gx,int gy,int c){
        int bg=0xFF232832;
        switch(id){
            case "auto_gg": // speech bubble, three dots
                BedrockUi.outline(gx,gy,10,7,c);
                drawRect(gx+2,gy+7,gx+4,gy+9,c);
                for(int i=0;i<3;i++)drawRect(gx+2+i*3,gy+3,gx+3+i*3,gy+4,c);
                break;
            case "bow_optimizer": // bow with a nocked arrow
                drawRect(gx+1,gy,gx+2,gy+10,c);
                drawRect(gx+2,gy,gx+4,gy+1,c);drawRect(gx+4,gy+1,gx+5,gy+3,c);
                drawRect(gx+5,gy+3,gx+6,gy+7,c);drawRect(gx+4,gy+7,gx+5,gy+9,c);
                drawRect(gx+2,gy+9,gx+4,gy+10,c);
                drawRect(gx+3,gy+4,gx+9,gy+5,c);drawRect(gx+6,gy+2,gx+7,gy+8,c);
                break;
            case "face_rig": // pixel face
                BedrockUi.outline(gx+1,gy+1,8,8,c);
                drawRect(gx+3,gy+3,gx+4,gy+5,c);drawRect(gx+6,gy+3,gx+7,gy+5,c);
                drawRect(gx+3,gy+7,gx+7,gy+8,c);
                break;
            case "player_animation": // figure with a raised arm
                drawRect(gx+4,gy,gx+6,gy+2,c);drawRect(gx+4,gy+2,gx+6,gy+6,c);
                drawRect(gx+2,gy+2,gx+4,gy+3,c);drawRect(gx+6,gy+1,gx+9,gy+2,c);
                drawRect(gx+6,gy+2,gx+7,gy+3,c);
                drawRect(gx+3,gy+6,gx+4,gy+10,c);drawRect(gx+5,gy+6,gx+6,gy+10,c);
                break;
            case "kill_confirm": // crosshair
                drawRect(gx+4,gy+1,gx+6,gy+9,c);drawRect(gx+1,gy+4,gx+9,gy+6,c);
                drawRect(gx+4,gy+4,gx+6,gy+6,bg);
                break;
            case "kill_feed": // feed lines with author dots
                for(int i=0;i<3;i++){
                    drawRect(gx,gy+1+i*3,gx+2,gy+3+i*3,c);
                    drawRect(gx+3,gy+1+i*3,gx+10-(i==2?2:0),gy+3+i*3,c);
                }
                break;
            case "left_hand": // hand, fingers up
                drawRect(gx+2,gy+4,gx+8,gy+9,c);
                drawRect(gx+2,gy+1,gx+3,gy+4,c);drawRect(gx+4,gy,gx+5,gy+4,c);drawRect(gx+6,gy+1,gx+7,gy+4,c);
                drawRect(gx+1,gy+5,gx+2,gy+8,c);
                break;
            case "map_preview": // folded map sheet with a pin
                BedrockUi.outline(gx+1,gy+1,8,8,c);
                drawRect(gx+4,gy+1,gx+5,gy+9,c);
                drawRect(gx+7,gy+2,gx+8,gy+4,c);
                break;
            case "minimap": // framed corner map
                BedrockUi.outline(gx,gy,10,10,c);
                drawRect(gx+2,gy+2,gx+5,gy+5,c);
                drawRect(gx+6,gy+6,gx+8,gy+8,c);
                break;
            case "region_overlay": // corner brackets around a point
                drawRect(gx,gy,gx+3,gy+1,c);drawRect(gx,gy,gx+1,gy+3,c);
                drawRect(gx+7,gy,gx+10,gy+1,c);drawRect(gx+9,gy,gx+10,gy+3,c);
                drawRect(gx,gy+9,gx+3,gy+10,c);drawRect(gx,gy+7,gx+1,gy+10,c);
                drawRect(gx+7,gy+9,gx+10,gy+10,c);drawRect(gx+9,gy+7,gx+10,gy+10,c);
                drawRect(gx+4,gy+4,gx+6,gy+6,c);
                break;
            case "reliable_chat": // bubble with a check
                BedrockUi.outline(gx,gy,10,7,c);
                drawRect(gx+2,gy+7,gx+4,gy+9,c);
                drawRect(gx+3,gy+4,gx+4,gy+5,c);drawRect(gx+4,gy+3,gx+5,gy+4,c);
                drawRect(gx+5,gy+3,gx+7,gy+4,c);drawRect(gx+6,gy+4,gx+7,gy+5,c);
                break;
            case "team_bar": // scoreboard panel
                BedrockUi.outline(gx,gy,10,10,c);
                drawRect(gx+2,gy+2,gx+8,gy+3,c);
                drawRect(gx+2,gy+5,gx+7,gy+6,c);
                drawRect(gx+2,gy+8,gx+8,gy+9,c);
                break;
            case "touchdown": // ball with laces
                BedrockUi.rounded(gx,gy+2,10,6,2,c);
                drawRect(gx+4,gy+2,gx+5,gy+8,bg);
                drawRect(gx+3,gy+4,gx+6,gy+5,bg);
                break;
            case "custom_votebook": // check going into a ballot box
                BedrockUi.outline(gx+1,gy+4,8,6,c);
                drawRect(gx+3,gy+2,gx+4,gy+4,c);
                drawRect(gx+4,gy+1,gx+6,gy+2,c);
                drawRect(gx+6,gy+2,gx+7,gy+3,c);
                break;
            default:
                BedrockUi.rounded(gx+3,gy+3,4,4,1,c);
        }
    }
    private void drawSearchIcon(int gx,int gy,int c){
        BedrockUi.outline(gx,gy,6,6,c);
        drawRect(gx+3,gy+3,gx+4,gy+4,BedrockTokens.SURFACE_PANEL);
        drawRect(gx+5,gy+5,gx+7,gy+7,c);
    }

    /** Small switch: track plus travelling knob, green when on. */
    private void drawSwitch(int sx,int sy,int sw,int sh,boolean on){
        BedrockUi.capsule(sx,sy,sw,sh,on?BedrockTokens.STATE_ENABLED:BedrockUi.SLOT);
        int knob=sh-2;
        BedrockUi.capsule(on?sx+sw-knob-1:sx+1,sy+1,knob,knob,on?0xFFFFFFFF:0xFF5B6069);
    }

    /** Accent pill for the one primary action in the panel. */
    private static final class PillButton extends GuiButton {
        PillButton(int id,int x,int y,int w,int h,String label){super(id,x,y,w,h,label);}
        public void drawButton(Minecraft mc,int mx,int my){
            if(!visible)return;
            hovered=mx>=xPosition&&mx<xPosition+width&&my>=yPosition&&my<yPosition+height;
            BedrockUi.rounded(xPosition,yPosition,width,height,2,
                hovered?0xFF5A9BFF:BedrockTokens.STATE_SELECTED);
            drawCenteredString(mc.fontRendererObj,displayString,
                xPosition+width/2,yPosition+(height-8)/2,0xFFFFFFFF);
        }
    }

    /** One-line subtitles, keyed by module id. Empty when a module has no copy yet. */
    private static final Map<String,String> DESC=new HashMap<>();
    static{
        DESC.put("reliable_chat","Organize team, global and admin chat");
        DESC.put("team_bar","See teams, scores and flags");
        DESC.put("kill_feed","Recent kills without chat spam");
        DESC.put("kill_confirm","Clear confirmation after a kill");
        DESC.put("map_preview","Preview the next map before voting");
        DESC.put("face_rig","Expressive face marks on your skin");
        DESC.put("player_animation","Emotes and a pull-out skateboard");
        DESC.put("minimap","Compact map in a screen corner");
        DESC.put("left_hand","Choose what appears in your off-hand");
        DESC.put("bow_optimizer","Better bow timing and accuracy");
        DESC.put("auto_gg","Send a friendly GG when matches end");
        DESC.put("region_overlay","Region borders and buildable areas");
        DESC.put("touchdown","Player and ball details");
        DESC.put("custom_votebook","Readable PGM voting");
    }

    /** Category accent, used by the row icon. */
    private int categoryColour(OtlModule m){
        if("HUD".equals(m.category))return BedrockTokens.STATE_ACCENT;
        if("VISUAL".equals(m.category))return 0xFF7C3AED;
        return BedrockTokens.STATE_ENABLED;
    }

    // smoke helpers: row geometry plus the module list's scrollbar
    int visibleCount(){return list().size();}
    /** Position of a module in the drawn list; the list is sorted, OtlModule.ALL is not. */
    int visibleIndex(OtlModule m){return list().indexOf(m);}
    /** Every row the list holds, not just the ones on screen. */
    int rowCount(){return list().size();}
    int firstRow(){return scrollRow;}
    int[] rowBox(int index){
        int row=scrollRow+index;
        return new int[]{listX(),rowY(row),listW(),ROW_H};
    }
    int trackLeft(){return trackX();}
    int trackTopY(){return trackTop();}
    int trackBottomY(){return trackBottom();}
    int settingsGearX(){return gearX();}
    int headerY(){return y+4;}
    int popupTopY(){return popupTop();}
    boolean profileMenuOpen(){return profileOpen;}
    int[] onOnlyBox(){return new int[]{filterX(),chipY(),filterW(),chipH()};}
    int[] profileChipBox(){return new int[]{listX(),y+h-FOOTER+3,popupChipW(),20};}
}
