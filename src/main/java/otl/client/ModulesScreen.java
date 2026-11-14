package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Bedrock-inspired module browser: scrollable card grid instead of pages, plus a profile rail.
 * Profiles are separate files; switching one swaps HUD positions and module settings.
 */
final class ModulesScreen extends GuiScreen {
    private static final String[] TABS={"ALL","HUD","VISUAL","MECHANIC"};
    private static final int COLUMNS=3,VISIBLE_ROWS=2,ROW_H=22,RAIL=132;    private final GuiScreen parent;
    private int x,y,w,h,tab,scrollRow,railScroll;
    private List<String> profiles=new ArrayList<>();
    private GuiTextField renameField;
    private String editing,status="";
    private boolean scrollDrag,railDrag;
    private String railDragName;

    ModulesScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}

    public void initGui(){
        UtilitySettings.load();
        w=Math.min(470,width-16);h=Math.min(272,height-16);x=(width-w)/2;y=(height-h)/2;
        profiles=Profiles.profileNames();
        scrollRow=Math.max(0,Math.min(scrollRow,rows()-VISIBLE_ROWS));
        railScroll=Math.max(0,Math.min(railScroll,Math.max(0,profiles.size()-railRows())));
        status="";
        buttonList.clear();
        buttonList.add(new NeutralButton(3,x+w-86,y+h-24,72,18,"DONE",false));
        buttonList.add(new NeutralButton(4,railLeft(),y+h-26,railWidth(),18,"+ NEW PROFILE",false));
        buttonList.add(new NeutralButton(5,railLeft(),y+h-48,railWidth(),18,"IMPORT PROFILE",false));
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

    private List<OtlModule> visible(){
        List<OtlModule> list=new ArrayList<>();
        for(OtlModule m:OtlModule.ALL)if(tab==0||m.category.equals(TABS[tab]))list.add(m);
        return list;
    }
    private int rows(){return Math.max(1,(visible().size()+COLUMNS-1)/COLUMNS);}
    private int contentX(){return x+RAIL+8;}
    private int railLeft(){return x+8;}
    private int railWidth(){return RAIL-12;}
    private int divider(){return y+h-32;}
    private int cardW(){return Math.max(70,(x+w-14-contentX()-12)/COLUMNS);}
    private int cardH(){return Math.max(64,(divider()-10-(y+52))/VISIBLE_ROWS);}
    private int cardX(int column){return contentX()+column*(cardW()+4);}
    private int cardY(int row){return y+52+(row-scrollRow)*(cardH()+4);}
    // Keep the profile list compact; the rest of the rail belongs to the actions/footer.
    private int railRows(){return Math.max(1,(h-82)/ROW_H);}
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
        if(keyCode==Keyboard.KEY_ESCAPE){close();return;}
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
                        if(yes&&Profiles.deleteProfile(name))initGui();
                        else mc.displayGuiScreen(this);
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
            if(my>=y+30&&my<y+48&&mx>=tx&&mx<tx+tw){tab=i;scrollRow=0;initGui();return;}
        }
        List<OtlModule> list=visible();
        for(int row=scrollRow;row<scrollRow+VISIBLE_ROWS;row++){
            for(int column=0;column<COLUMNS;column++){
                int index=row*COLUMNS+column;if(index>=list.size())return;
                OtlModule m=list.get(index);int cx=cardX(column),cy=cardY(row),cw=cardW(),ch=cardH();
                if(mx<cx||mx>=cx+cw||my<cy||my>=cy+ch)continue;
                if(m.available()){
                    if(my>=cy+ch-38&&my<cy+ch-24){GuiScreen s=m.settings(this);if(s!=null)mc.displayGuiScreen(s);return;}
                    if(my>=cy+ch-18&&my<cy+ch-4){m.setEnabled(!m.enabled());UtilitySettings.save();initGui();return;}
                }
                return;
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
        drawRect(0,0,width,height,0xB0000000);
        BedrockUi.panel(x,y,w,h);
        // The rail shares the main panel surface; rows use card grays like the module side.
        // Keep the rail/module gutter on the same border gray as the outer frame.
        drawRect(x+RAIL+2,y+2,x+RAIL+4,y+h-2,BedrockUi.EDGE);
        // Full-width header band; titles stay centered over their own column, divider kept visible.
        drawRect(x+2,y+2,x+w-2,y+24,0xFFE6E8EB);
        drawRect(x+2,y+24,x+w-2,y+26,0xFF202122);
        drawRect(x+RAIL+2,y+2,x+RAIL+4,y+26,BedrockUi.EDGE);
        BedrockUi.plainCentered(fontRendererObj,"PROFILES",railLeft()+railWidth()/2,y+9,0xFF202122);
        for(int i=0;i<railRows()&&railScroll+i<profiles.size();i++){
            String name=profiles.get(railScroll+i);int ry=rowY(railScroll+i);
            boolean active=name.equals(UtilitySettings.profile);
            boolean hover=BedrockUi.hit(mx,my,railLeft(),y+30,railWidth(),railRows()*ROW_H)&&my>=ry&&my<ry+ROW_H;
            drawRect(railLeft(),ry,railLeft()+railWidth(),ry+ROW_H-1,active?0xFF58595A:hover?0xFF505152:0xFF3F4041);
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
            drawRect(sx,sy,sx+5,sy+sh,0xFF171819);
            int top=railThumbTop();
            drawRect(sx+1,top+1,sx+4,top+railThumbHeight()-1,overRailTrack(mx,my)||railDrag?0xFFFFFFFF:0xFFD0D1D4);
        }
        BedrockUi.plainCentered(fontRendererObj,"MODULES",(contentX()+x+w-2)/2,y+9,0xFF202122);
        BedrockUi.backArrow(fontRendererObj,x+8,y+4,BedrockUi.hit(mx,my,x+8,y+4,18,18));
        for(int i=0;i<TABS.length;i++){
            boolean on=tab==i;int tx=tabX(i),tw=tabWidth(i);
            BedrockUi.button(fontRendererObj,tx,y+30,tw,18,TABS[i],BedrockUi.hit(mx,my,tx,y+30,tw,18),true);
            if(on)drawRect(tx+2,y+43,tx+tw-2,y+45,BedrockUi.ACCENT);
        }
        List<OtlModule> list=visible();
        for(int row=scrollRow;row<scrollRow+VISIBLE_ROWS;row++){
            if(row*COLUMNS>=list.size())break;
            for(int column=0;column<COLUMNS;column++){
                int index=row*COLUMNS+column;if(index>=list.size())break;
                drawCard(list.get(index),cardX(column),cardY(row),mx,my);
            }
        }
        int sx=x+w-9;
        if(rows()>VISIBLE_ROWS){
            drawRect(sx,y+52,sx+4,divider()-6,0xFF141414);
            int ty=thumbY(),th=thumbH();
            drawRect(sx,ty,sx+4,ty+th,BedrockUi.hit(mx,my,sx-2,ty,8,th)||scrollDrag?0xFFB0B0B0:0xFF7A7A7A);
        }
        drawRect(contentX(),divider(),x+w-8,divider()+1,0xFF2E2E2E);
        if(!status.isEmpty()){int left=contentX()+112,right=x+w-92;drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(status,Math.max(20,right-left)),(left+right)/2,y+h-19,0xFFB8B8B8);}
        if(renameField!=null){
            drawRect(renameField.xPosition-2,renameField.yPosition-2,renameField.xPosition+renameField.width+2,renameField.yPosition+renameField.height+2,0xFF111111);
            renameField.drawTextBox();
        }
        super.drawScreen(mx,my,partial);
        if(BedrockUi.hit(mx,my,railLeft(),y+30,railWidth(),railRows()*ROW_H))drawHoveringText(Arrays.asList("Click to select; pencil renames","Pages exports; X deletes"),mx,my);
        if(BedrockUi.hit(mx,my,x+8,y+4,18,18))drawHoveringText(Arrays.asList("Back to HUD editor"),mx,my);
    }

    private void drawCard(OtlModule m,int cx,int cy,int mx,int my){
        int cw=cardW(),ch=cardH();
        boolean hover=BedrockUi.hit(mx,my,cx,cy,cw,ch),on=m.enabled(),usable=m.available();
        BedrockUi.panel(cx,cy,cw,ch);
        drawRect(cx+2,cy+2,cx+cw-2,cy+ch-2,hover?0xFF58595A:BedrockUi.SLOT);
        if(usable&&on)drawRect(cx,cy,cx+2,cy+ch,BedrockUi.ACCENT);
        String title=m.name;
        while(fontRendererObj.getStringWidth(title)>cw-16&&title.length()>1)title=title.substring(0,title.length()-1);
        if(!title.equals(m.name))title=title.substring(0,Math.max(1,title.length()-2))+"...";
        BedrockUi.plainCentered(fontRendererObj,title,cx+cw/2,cy+7,usable?0xFFFFFFFF:0xFF7A7A7A);
        BedrockUi.plainCentered(fontRendererObj,m.category,cx+cw/2,cy+20,usable?0xFFBFBFBF:0xFF5A5A5A);
        int options=cy+ch-38;boolean optionsHover=hover&&usable&&my>=options&&my<options+14;
        BedrockUi.button(fontRendererObj,cx+6,options,cw-12,14,"OPTIONS",optionsHover,usable);
        int state=cy+ch-18;boolean stateHover=hover&&usable&&my>=state&&my<state+14;
        BedrockUi.inset(cx+6,state,cw-12,14);
        drawString(fontRendererObj,!usable?"LOCKED":on?"ON":"OFF",cx+10,state+3,!usable?0xFFCC7777:on?0xFF8FE0A8:0xFFB8B8B8);
        if(usable)BedrockUi.toggle(cx+cw-34,state+1,on,stateHover);
    }

    // smoke helpers: the module grid scrollbar's geometry and position
    int rowCount(){return rows();}
    int firstRow(){return scrollRow;}
    int trackLeft(){return x+w-7;}
    int trackTopY(){return y+52;}
    int trackBottomY(){return divider()-6;}
}
