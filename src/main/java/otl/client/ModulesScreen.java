package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Module browser: searchable card grid plus a profile rail.
 * Profiles are separate files; switching one swaps HUD positions and module settings.
 */
final class ModulesScreen extends GuiScreen {
    private static final String[] TABS={"ALL","HUD","VISUAL","MECHANIC"};
    private static final int VISIBLE_ROWS=2,ROW_H=22,RAIL=132;
    private final GuiScreen parent;
    private int x,y,w,h,tab,columns,scrollRow,railScroll;
    private List<String> profiles=new ArrayList<>();
    private GuiTextField renameField,search;
    private String editing,status="",query="";
    private boolean scrollDrag,railDrag,enabledOnly;
    private String railDragName;

    ModulesScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}

    public void initGui(){
        UtilitySettings.load();
        // Match the compact Lunar modal proportions at GUI scale 2; do not grow to fill the screen.
        w=Math.min(520,width-16);h=Math.min(300,height-16);x=(width-w)/2;y=(height-h)/2;
        columns=cardSpace()<104?2:3;
        profiles=Profiles.profileNames();
        scrollRow=Math.max(0,Math.min(scrollRow,rows()-VISIBLE_ROWS));
        railScroll=Math.max(0,Math.min(railScroll,Math.max(0,profiles.size()-railRows())));
        status="";
        buttonList.clear();
        buttonList.add(new NeutralButton(3,x+w-76,y+h-24,64,18,"DONE",false));
        buttonList.add(new NeutralButton(4,railLeft(),y+h-26,railWidth(),18,"+ NEW PROFILE",false));
        buttonList.add(new NeutralButton(5,railLeft(),y+h-48,railWidth(),18,"IMPORT PROFILE",false));
        search=new GuiTextField(80,fontRendererObj,searchX()+13,y+9,searchW()-16,12);
        search.setMaxStringLength(40);
        search.setText(query);
        search.setEnableBackgroundDrawing(false);
        search.setFocused(!query.isEmpty());
        renameField=null;
        int row=editing==null?-1:profiles.indexOf(editing);
        if(row>=0&&row>=railScroll&&row<railScroll+railRows()){
            renameField=new GuiTextField(90,fontRendererObj,x+32,rowY(row)+3,actionX()-(x+32)-3,14);
            renameField.setMaxStringLength(24);
            renameField.setText(editing);
            renameField.setFocused(true);
            renameField.setCursorPositionEnd();
        }else if(editing!=null)editing=null;
    }

    private int searchW(){return Math.min(180,Math.max(60,w-RAIL-20-navWidth()-12));}
    private int navWidth(){return fontRendererObj.getStringWidth("MODS")+24+fontRendererObj.getStringWidth("SETTINGS");}
    private int searchX(){return x+w-10-searchW();}
    private int navModsX(){return contentX();}
    private int navSettingsX(){return contentX()+fontRendererObj.getStringWidth("MODS")+24;}
    /** Header section label; the active one is underlined rather than boxed. */
    private void drawNav(int nx,String label,boolean active,int mx,int my){
        int nw=fontRendererObj.getStringWidth(label);
        boolean hover=BedrockUi.hit(mx,my,nx-4,y+4,nw+8,20);
        drawString(fontRendererObj,label,nx,y+9,active?BedrockUi.TEXT:hover?BedrockUi.TEXT:BedrockUi.MUTED);
        if(active)drawRect(nx,y+19,nx+nw,y+20,BedrockUi.ACCENT);
    }
    private boolean overSearch(int mx,int my){return BedrockUi.hit(mx,my,searchX()-4,y+4,searchW()+8,20);}

    /** Dot and word for the OTL service link; hidden when the header has no room for it. */
    private List<String> hereNow=Collections.emptyList();
    /** "Online" plus how many other OTL users are in this world, e.g. "Online · 3". */
    private String serviceLabel(){
        int status=OtlService.status();
        String label=OtlService.statusLabel(status);
        if(status==OtlService.ONLINE&&!hereNow.isEmpty())label=label+" \u00b7 "+hereNow.size();
        return label;
    }
    private int lightX(){return searchX()-12-fontRendererObj.getStringWidth(serviceLabel())-9;}
    private void drawServiceLight(int mx,int my){
        hereNow=OtlPresence.here();
        int x=lightX();
        // A narrow window would push the light into the nav strip, so it is simply not drawn.
        if(x<navSettingsX()+fontRendererObj.getStringWidth("SETTINGS")+14)return;
        int status=OtlService.status();
        boolean hover=BedrockUi.hit(mx,my,x-3,y+4,fontRendererObj.getStringWidth(serviceLabel())+16,20);
        BedrockUi.rounded(x,y+10,6,6,2,OtlService.statusColour(status));
        drawString(fontRendererObj,serviceLabel(),x+10,y+9,status==OtlService.ONLINE?BedrockUi.TEXT:hover?BedrockUi.TEXT:BedrockUi.DIM);
    }
    /** Names of the other OTL users here; capped so a big server does not fill the screen. */
    private List<String> serviceTip(){
        List<String> tip=new ArrayList<>();
        int status=OtlService.status();
        tip.add("OTL service: "+OtlService.statusLabel(status));
        if(status!=OtlService.ONLINE){tip.add("Board sharing and other online features are idle");return tip;}
        if(hereNow.isEmpty()){tip.add("No other OTL users in this world");return tip;}
        tip.add(hereNow.size()==1?"1 other OTL user here:":hereNow.size()+" other OTL users here:");
        int shown=Math.min(hereNow.size(),8);
        for(int i=0;i<shown;i++)tip.add("  "+hereNow.get(i));
        if(hereNow.size()>shown)tip.add("  and "+(hereNow.size()-shown)+" more");
        return tip;
    }

    private List<OtlModule> visible(){
        String q=query.trim().toLowerCase(Locale.ROOT);
        List<OtlModule> list=new ArrayList<>();
        for(OtlModule m:OtlModule.ALL){
            boolean cat=q.isEmpty()?(tab==0||m.category.equals(TABS[tab])):true;
            if(cat&&matches(m,q)&&(!enabledOnly||m.enabled()))list.add(m);
        }
        Collections.sort(list,(a,b)->{
            int ca=categoryRank(a.category),cb=categoryRank(b.category);
            if(ca!=cb)return ca-cb;
            int oa=moduleRank(a.id),ob=moduleRank(b.id);
            return oa==ob?a.name.compareToIgnoreCase(b.name):oa-ob;
        });
        return list;
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
    private int rows(){return Math.max(1,(visible().size()+columns-1)/columns);}
    private int contentX(){return x+RAIL+8;}
    private int railLeft(){return x+8;}
    private int railWidth(){return RAIL-12;}
    private int divider(){return y+h-32;}
    private int cardW(){return Math.max(70,(x+w-14-contentX()-12)/columns);}
    /** Width a card would get at three columns; below ~104 the descriptions truncate, so drop to two. */
    private int cardSpace(){return (x+w-14-contentX()-12)/3;}
    private int cardH(){return Math.max(58,(divider()-10-(y+52))/VISIBLE_ROWS);}
    private int cardX(int column){return contentX()+column*(cardW()+4);}
    private int cardY(int row){return y+52+(row-scrollRow)*(cardH()+4);}
    // Keep the profile list compact; the rest of the rail belongs to the actions/footer.
    private int railRows(){return Math.max(1,(h-104)/ROW_H);}
    private int rowY(int index){return y+30+(index-railScroll)*ROW_H;}
    private int actionX(){return railLeft()+railWidth()-52;}
    /** Active profile names longer than the row slide back and forth; others stay trimmed. */
    private String marquee(String name,boolean active){
        int tw=fontRendererObj.getStringWidth(name);
        if(tw<=56||!active)return fontRendererObj.trimStringToWidth(name,56);
        int range=tw-56,span=range+30;
        long t=System.currentTimeMillis()/30L%(span*2L);
        int off=Math.max(0,Math.min(range,(int)(t<=span?t-15:span*2-t-15)));
        int start=0;
        while(start<name.length()&&fontRendererObj.getStringWidth(name.substring(0,start+1))<=off)start++;
        return fontRendererObj.trimStringToWidth(name.substring(start),56);
    }
    private boolean overRailTrack(int mx,int my){return profiles.size()>railRows()&&BedrockUi.hit(mx,my,railLeft()+railWidth()-6,y+30,5,railRows()*ROW_H);}
    private int railThumbHeight(){return Math.max(12,railRows()*ROW_H*railRows()/Math.max(1,profiles.size()));}
    private int railThumbTop(){int max=profiles.size()-railRows();return y+30+(max<=0?0:(railRows()*ROW_H-railThumbHeight())*railScroll/max);}
    private void scrollRailTo(int my){
        int max=Math.max(0,profiles.size()-railRows()),travel=railRows()*ROW_H-railThumbHeight();
        railScroll=Math.max(0,Math.min(max,(int)Math.round((my-y-30-railThumbHeight()/2D)*max/Math.max(1,travel))));
    }
    private int tabWidth(int i){return fontRendererObj.getStringWidth(TABS[i])+14;}
    private int tabX(int i){int tx=contentX();for(int j=0;j<i;j++)tx+=tabWidth(j)+4;return tx;}
    private int trackH(){return divider()-6-(y+52);}
    private int thumbH(){int total=rows();return Math.max(20,trackH()*VISIBLE_ROWS/Math.max(VISIBLE_ROWS,total));}
    private int thumbY(){int max=rows()-VISIBLE_ROWS;return max<=0?y+52:y+52+(trackH()-thumbH())*scrollRow/max;}
    private boolean overTrack(int mx,int my){return mx>=x+w-11&&mx<x+w-3&&my>=y+52&&my<divider()-6;}

    private void scrollTo(int my){
        int max=rows()-VISIBLE_ROWS;
        if(max<=0){scrollRow=0;return;}
        double t=(my-(y+52)-thumbH()/2D)/Math.max(1,trackH()-thumbH());
        scrollRow=Math.max(0,Math.min(max,(int)Math.round(t*max)));
    }
    private void close(){mc.displayGuiScreen(parent);}

    protected void actionPerformed(GuiButton b){
        if(!b.enabled)return;
        if(b.id==0){mc.displayGuiScreen(parent instanceof HudEditor?parent:new HudEditor());return;}
        if(b.id==3){close();return;}
        if(b.id==4){Profiles.newProfile();initGui();return;}
        if(b.id==5){importProfile();return;}
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
    private void copyProfile(String name){
        mc.displayGuiScreen(new ProfileTransferScreen(this,name));
    }
    private void importProfile(){
        mc.displayGuiScreen(new ProfileTransferScreen(this,null));
    }

    protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(editing!=null){
            if(keyCode==Keyboard.KEY_RETURN){commitRename();return;}
            if(keyCode==Keyboard.KEY_ESCAPE){editing=null;renameField=null;initGui();return;}
            if(renameField!=null)renameField.textboxKeyTyped(typedChar,keyCode);
            return;
        }
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

    /** One wheel notch moves the list under the cursor: the profile rail or the module grid. */
    void scrollWheel(int mx,int my,int wheel){
        if(mx>=railLeft()&&mx<railLeft()+railWidth()&&my>=y+30&&my<y+30+railRows()*ROW_H){
            railScroll=Math.max(0,Math.min(railScroll+(wheel>0?-1:1),Math.max(0,profiles.size()-railRows())));
        }else if(mx>=contentX()&&mx<x+w&&my>=y+52&&my<divider()){
            scrollRow=Math.max(0,Math.min(scrollRow+(wheel>0?-1:1),rows()-VISIBLE_ROWS));
        }
    }

    protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(editing!=null){
            if(renameField!=null&&BedrockUi.hit(mx,my,renameField.xPosition,renameField.yPosition,renameField.width,renameField.height)){
                renameField.mouseClicked(mx,my,button);return;
            }
            commitRename();
            return;
        }
        for(GuiButton b:buttonList)if(b.visible&&b.enabled&&mx>=b.xPosition&&mx<b.xPosition+b.width&&my>=b.yPosition&&my<b.yPosition+b.height){
            super.mouseClicked(mx,my,button);return;
        }
        if(button!=0){super.mouseClicked(mx,my,button);return;}
        if(search!=null&&overSearch(mx,my)){search.setFocused(true);return;}
        search.setFocused(false);
        if(my>=y+4&&my<y+24){
            if(mx>=navSettingsX()){mc.displayGuiScreen(new PgmSettingsScreen(this));return;}
            if(mx>=navModsX())return;
        }
        if(BedrockUi.hit(mx,my,contentX(),y+h-24,74,18)){
            enabledOnly=!enabledOnly;scrollRow=0;initGui();return;
        }
        if(BedrockUi.hit(mx,my,x+8,y+4,18,18)){mc.displayGuiScreen(parent instanceof HudEditor?parent:new HudEditor());return;}
        if(overRailTrack(mx,my)){railDrag=true;scrollRailTo(my);return;}
        if(mx>=railLeft()&&mx<railLeft()+railWidth()&&my>=y+30&&my<railRows()*ROW_H+y+30){
            int index=railScroll+(my-y-30)/ROW_H;
            if(index<profiles.size()){
                String name=profiles.get(index);
                railDragName=name;
                if(mx>=actionX()+32){
                    if(name.equals(UtilitySettings.profile)||name.equals("Default"))return;
                    mc.displayGuiScreen(new GuiYesNo((yes,id)->{
                        if(yes)Profiles.deleteProfile(name);
                        // displayGuiScreen re-inits this screen, which reloads the rail. Refreshing
                        // it in place left the dialog on screen with nothing behind it.
                        mc.displayGuiScreen(this);
                    },"Delete profile \""+name+"\"?","This cannot be undone.",0));
                    return;
                }
                if(mx>=actionX()+16){copyProfile(name);return;}
                if(mx>=actionX()){editing=name;initGui();return;}
                Profiles.switchProfile(name);initGui();return;
            }
            return;
        }
        if(overTrack(mx,my)){scrollDrag=true;scrollTo(my);return;}
        for(int i=0;i<TABS.length;i++){
            int tx=tabX(i),tw=tabWidth(i);
            if(my>=y+26&&my<y+46&&mx>=tx&&mx<tx+tw){
                tab=i;query="";if(search!=null){search.setText("");search.setFocused(false);}
                scrollRow=0;initGui();return;
            }
        }
        List<OtlModule> list=visible();
        for(int row=scrollRow;row<scrollRow+VISIBLE_ROWS;row++){
            for(int column=0;column<columns;column++){
                int index=row*columns+column;if(index>=list.size())return;
                OtlModule m=list.get(index);int cx=cardX(column),cy=cardY(row),cw=cardW(),ch=cardH();
                if(mx<cx||mx>=cx+cw||my<cy||my>=cy+ch)continue;
                if(!m.available())return;
                if(mx<cx+52&&my>=cy+ch-20){
                    m.setEnabled(!m.enabled());UtilitySettings.save();initGui();return;
                }
                GuiScreen s=m.settings(this);if(s!=null)mc.displayGuiScreen(s);return;
            }
        }
        super.mouseClicked(mx,my,button);
    }

    protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(scrollDrag)scrollTo(my);
        if(railDrag)scrollRailTo(my);
        if(railDragName!=null&&button==0&&mx>=railLeft()&&mx<railLeft()+railWidth()&&my>=y+30&&my<y+30+railRows()*ROW_H){
            int from=profiles.indexOf(railDragName),to=Math.max(0,Math.min(profiles.size()-1,railScroll+(my-y-30)/ROW_H));
            if(from>=0&&to!=from){profiles.add(to,profiles.remove(from));Profiles.saveProfileOrder(profiles);}
        }
        super.mouseClickMove(mx,my,button,elapsed);
    }
    protected void mouseReleased(int mx,int my,int state){
        scrollDrag=false;railDrag=false;railDragName=null;
        super.mouseReleased(mx,my,state);
    }

    public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,0x40101010);
        BedrockUi.panel(x,y,w,h);
        drawRect(x+RAIL+2,y+2,x+RAIL+3,y+h-2,BedrockUi.EDGE);
        drawRect(x+2,y+24,x+w-2,y+25,BedrockUi.EDGE);
        BedrockUi.plainCentered(fontRendererObj,"\u00a7l"+profiles.size()+" PROFILES",railLeft()+railWidth()/2,y+9,BedrockUi.MUTED);
        for(int i=0;i<railRows()&&railScroll+i<profiles.size();i++){
            String name=profiles.get(railScroll+i);int ry=rowY(railScroll+i);
            boolean active=name.equals(UtilitySettings.profile);
            boolean hover=BedrockUi.hit(mx,my,railLeft(),y+30,railWidth(),railRows()*ROW_H)&&my>=ry&&my<ry+ROW_H;
            drawRect(railLeft(),ry,railLeft()+railWidth(),ry+ROW_H-2,active?BedrockTokens.STATE_HOVER:hover?BedrockTokens.SURFACE_CARD:BedrockUi.INSET);
            if(active)drawRect(railLeft(),ry,railLeft()+2,ry+ROW_H,BedrockUi.ACCENT);
            if(name.equals(editing))continue;
            drawString(fontRendererObj,marquee(name,active),railLeft()+10,ry+6,active?0xFFFFFFFF:0xFFC8C8C8);
            int renameX=actionX(),exportX=renameX+16,deleteX=exportX+16;
            boolean renHover=hover&&mx>=renameX&&mx<renameX+12,copyHover=hover&&mx>=exportX&&mx<exportX+12;
            BedrockUi.pencil(renameX,ry+4,renHover);
            BedrockUi.duplicate(exportX,ry+4,copyHover);
            BedrockUi.icon(fontRendererObj,deleteX,ry+4,"x",mx>=deleteX&&mx<deleteX+12,!active&&!name.equals("Default"));
        }
        if(profiles.size()>railRows()){
            int sx=railLeft()+railWidth()-6,sy=y+30,sh=railRows()*ROW_H;
            drawRect(sx,sy,sx+5,sy+sh,BedrockUi.INSET);
            int top=railThumbTop();
            drawRect(sx+1,top+1,sx+4,top+railThumbHeight()-1,overRailTrack(mx,my)||railDrag?0xFFFFFFFF:0xFFD0D1D4);
        }
        // Top-level sections in the header, like the launcher's Mods / Settings strip.
        drawNav(navModsX(),"MODS",true,mx,my);
        drawNav(navSettingsX(),"SETTINGS",false,mx,my);
        drawServiceLight(mx,my);
        int on=0;for(OtlModule m:OtlModule.ALL)if(m.enabled())on++;
        String count=on+" / "+OtlModule.ALL.size()+" ON";
        BedrockUi.chip(fontRendererObj,contentX(),y+h-24,74,18,"Enabled only",enabledOnly,
            BedrockUi.hit(mx,my,contentX(),y+h-24,74,18));
        drawString(fontRendererObj,count,contentX()+82,y+h-19,BedrockUi.DIM);
        BedrockUi.backArrow(fontRendererObj,x+8,y+4,BedrockUi.hit(mx,my,x+8,y+4,18,18));
        // Search: inset field with a magnifier glyph; the placeholder shows only while empty.
        BedrockUi.inset(searchX()-4,y+4,searchW()+8,20);
        if(search.isFocused())BedrockUi.outline(searchX()-4,y+4,searchW()+8,20,BedrockTokens.STATE_SELECTED);
        drawSearchIcon(searchX()+1,y+9,BedrockUi.DIM);
        if(search.getText().isEmpty())drawString(fontRendererObj,"Search modules",searchX()+13,y+9,BedrockUi.DIM);
        search.drawTextBox();
        for(int i=0;i<TABS.length;i++){
            boolean onTab=tab==i&&query.isEmpty();int tx=tabX(i),tw=tabWidth(i);
            BedrockUi.chip(fontRendererObj,tx,y+28,tw,18,TABS[i],onTab,BedrockUi.hit(mx,my,tx,y+28,tw,18));
        }
        List<OtlModule> list=visible();
        if(list.isEmpty()){
            String empty=query.isEmpty()?"No enabled modules":"No matches";
            drawCenteredString(fontRendererObj,empty,(contentX()+x+w)/2,y+52+cardH()/2-4,BedrockUi.MUTED);
        }
        for(int row=scrollRow;row<scrollRow+VISIBLE_ROWS;row++){
            if(row*columns>=list.size())break;
            for(int column=0;column<columns;column++){
                int index=row*columns+column;if(index>=list.size())break;
                drawCard(list.get(index),cardX(column),cardY(row),mx,my);
            }
        }
        int sx=x+w-9;
        if(rows()>VISIBLE_ROWS){
            drawRect(sx,y+52,sx+4,divider()-6,BedrockUi.INSET);
            int ty=thumbY(),th=thumbH();
            drawRect(sx,ty,sx+4,ty+th,BedrockUi.hit(mx,my,sx-2,ty,8,th)||scrollDrag?0xFFB0B0B0:0xFF7A7A7A);
        }
        drawRect(contentX(),divider(),x+w-8,divider()+1,BedrockUi.EDGE);
        if(!status.isEmpty()){int left=contentX(),right=x+w-84;drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(status,Math.max(20,right-left)),(left+right)/2,y+h-19,0xFFB8B8B8);}
        if(renameField!=null){
            drawRect(renameField.xPosition-2,renameField.yPosition-2,renameField.xPosition+renameField.width+2,renameField.yPosition+renameField.height+2,0xFF111111);
            renameField.drawTextBox();
        }
        super.drawScreen(mx,my,partial);
        if(BedrockUi.hit(mx,my,railLeft(),y+30,railWidth(),railRows()*ROW_H))drawHoveringText(Arrays.asList("Click to select; pencil renames","Pages exports; X deletes"),mx,my);
        if(BedrockUi.hit(mx,my,x+8,y+4,18,18))drawHoveringText(Arrays.asList("Back to HUD editor"),mx,my);
        if(BedrockUi.hit(mx,my,navSettingsX()-4,y+4,fontRendererObj.getStringWidth("SETTINGS")+8,20))
            drawHoveringText(Arrays.asList("SETTINGS","PGM client and server options"),mx,my);
        if(overSearch(mx,my)&&!search.isFocused())drawHoveringText(Arrays.asList("Type to search, ESC clears"),mx,my);
        if(BedrockUi.hit(mx,my,lightX()-3,y+4,fontRendererObj.getStringWidth(serviceLabel())+16,20))
            drawHoveringText(serviceTip(),mx,my);
        for(int i=0;i<VISIBLE_ROWS*columns;i++){
            int index=scrollRow*columns+i;if(index>=list.size())break;
            int[] box=cardBox(i);
            if(BedrockUi.hit(mx,my,box[0],box[1],box[2],box[3]-20)){
                OtlModule m=list.get(index);
                List<String> tip=new ArrayList<>();tip.add(m.name);
                String desc=DESC.get(m.id);if(desc!=null)tip.addAll(fontRendererObj.listFormattedStringToWidth(desc,190));
                tip.add(m.available()?"Click to configure":"Unavailable on this server");
                drawHoveringText(tip,mx,my);break;
            }
        }
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
        drawRect(gx+3,gy+3,gx+4,gy+4,0xFF232832);
        drawRect(gx+5,gy+5,gx+7,gy+7,c);
    }

    /** Small Lunar-style switch: track plus travelling knob. */
    private void drawSwitch(int sx,int sy,int sw,int sh,boolean on){
        BedrockUi.rounded(sx,sy,sw,sh,sh/2,on?BedrockTokens.STATE_ENABLED:0xFF2A2E38);
        int knob=sh-2;
        BedrockUi.rounded(on?sx+sw-knob-1:sx+1,sy+1,knob,knob,knob/2,on?0xFFFFFFFF:0xFF9AA0AC);
    }

    /** One-line card subtitles, keyed by module id. Empty when a module has no copy yet. */
    private static final Map<String,String> DESC=new HashMap<>();
    static{
        DESC.put("reliable_chat","Organize team, global and admin chat");
        DESC.put("team_bar","See teams, scores and flags at a glance");
        DESC.put("kill_feed","Track recent kills without chat spam");
        DESC.put("kill_confirm","Show a clear confirmation after a kill");
        DESC.put("map_preview","Preview the next map before voting");
        DESC.put("face_rig","Add expressive face marks to your skin");
        DESC.put("player_animation","Emotes and a pull-out skateboard");
        DESC.put("minimap","Keep a compact map in a screen corner");
        DESC.put("left_hand","Choose what appears in your off-hand view");
        DESC.put("bow_optimizer","Improve bow timing and accuracy");
        DESC.put("auto_gg","Send a friendly GG when matches end");
        DESC.put("region_overlay","See region borders and buildable areas");
        DESC.put("touchdown","Show Touchdown player and ball details");
        DESC.put("custom_votebook","Turn PGM voting into a readable menu");
    }

    private void drawCard(OtlModule m,int cx,int cy,int mx,int my){
        int cw=cardW(),ch=cardH();
        boolean hover=BedrockUi.hit(mx,my,cx,cy,cw,ch),on=m.enabled(),usable=m.available();
        int tint=categoryColour(m);
        BedrockUi.rounded(cx,cy,cw,ch,hover?BedrockTokens.STATE_HOVER:BedrockUi.SLOT);
        drawOutline(cx,cy,cw,ch);
        int descY;
        if(ch>=100){
            int tileX=cx+cw/2-13;
            BedrockUi.rounded(tileX,cy+8,26,26,2,BedrockUi.INSET);
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(tileX+3,cy+11,0);
            net.minecraft.client.renderer.GlStateManager.scale(2,2,1);
            drawIcon(m.id,0,0,tint);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            BedrockUi.plainCentered(fontRendererObj,fontRendererObj.trimStringToWidth(m.name,cw-14),
                cx+cw/2,cy+40,usable?BedrockUi.TEXT:BedrockTokens.STATE_LOCKED);
            descY=cy+54;
        }else{
            BedrockUi.rounded(cx+6,cy+5,13,13,3,BedrockUi.INSET);
            drawIcon(m.id,cx+8,cy+7,tint);
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(m.name,cw-34),
                cx+23,cy+7,usable?BedrockUi.TEXT:BedrockTokens.STATE_LOCKED);
            descY=cy+22;
        }
        String desc=DESC.get(m.id);
        int bottom=cy+ch-17;
        if(desc!=null){
            for(String line:fontRendererObj.listFormattedStringToWidth(desc,cw-14)){
                if(descY+9>bottom-3)break;
                if(ch>=100)BedrockUi.plainCentered(fontRendererObj,line,cx+cw/2,descY,BedrockUi.MUTED);
                else drawString(fontRendererObj,line,cx+7,descY,BedrockUi.MUTED);
                descY+=10;
            }
        }
        drawRect(cx+6,bottom-4,cx+cw-6,bottom-3,BedrockUi.EDGE);
        boolean optionsHover=hover&&usable&&mx>=cx+cw-48&&my>=cy+ch-20;
        String options="EDIT  \u203a";
        int optionsX=cx+cw-7-fontRendererObj.getStringWidth(options);
        drawString(fontRendererObj,options,optionsX,bottom+3,
            !usable?BedrockTokens.STATE_LOCKED:optionsHover?0xFFFFFFFF:BedrockTokens.STATE_ACCENT);
        if(!usable){
            drawString(fontRendererObj,"LOCKED",cx+7,bottom+3,BedrockTokens.STATE_LOCKED);
        }else{
            drawSwitch(cx+7,bottom+4,18,9,on);
            String stateLabel=on?"ENABLED":"DISABLED";
            int stateWidth=fontRendererObj.getStringWidth(stateLabel);
            int stateX=Math.min(cx+29,optionsX-8-stateWidth);
            drawString(fontRendererObj,stateLabel,stateX,bottom+3,on?BedrockTokens.STATE_ENABLED:BedrockUi.MUTED);
        }
    }
    /** Lunar card category accent, used by the icon slot. */
    private int categoryColour(OtlModule m){
        if("HUD".equals(m.category))return BedrockTokens.STATE_ACCENT;
        if("VISUAL".equals(m.category))return 0xFF7C3AED;
        return BedrockTokens.STATE_ENABLED;
    }
    /** 1px Lunar card outline. */
    private void drawOutline(int x,int y,int w,int h){
        drawRect(x,y,x+w,y+1,BedrockTokens.SURFACE_EDGE);
        drawRect(x,y+h-1,x+w,y+h,BedrockTokens.SURFACE_EDGE);
        drawRect(x,y,x+1,y+h,BedrockTokens.SURFACE_EDGE);
        drawRect(x+w-1,y,x+w,y+h,BedrockTokens.SURFACE_EDGE);
    }

    // smoke helpers: card geometry plus the module grid's scrollbar
    int visibleCount(){return visible().size();}
    /** Position of a module in the grid as it is drawn; the grid is sorted, ALL is not. */
    int visibleIndex(OtlModule m){return visible().indexOf(m);}
    int[] cardBox(int index){
        int row=scrollRow+index/columns,col=index%columns;
        return new int[]{cardX(col),cardY(row),cardW(),cardH()};
    }
    int rowCount(){return rows();}
    int firstRow(){return scrollRow;}
    int trackLeft(){return x+w-7;}
    int trackTopY(){return y+52;}
    int trackBottomY(){return divider()-6;}
    int settingsNavX(){return navSettingsX();}
    int navTop(){return y+4;}
}
