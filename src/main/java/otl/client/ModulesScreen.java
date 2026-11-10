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
    private static final int COLUMNS=3,VISIBLE_ROWS=2,ROW_H=16,RAIL=100;
    private final GuiScreen parent;
    private int x,y,w,h,tab,scrollRow,railScroll;
    private List<String> profiles=new ArrayList<>();
    private GuiTextField renameField;
    private String editing,status="";
    private boolean scrollDrag;

    ModulesScreen(GuiScreen parent){this.parent=parent;}
    public boolean doesGuiPauseGame(){return false;}

    public void initGui(){
        UtilitySettings.load();
        w=Math.min(470,width-16);h=Math.min(272,height-16);x=(width-w)/2;y=(height-h)/2;
        profiles=UtilitySettings.profileNames();
        scrollRow=Math.max(0,Math.min(scrollRow,rows()-VISIBLE_ROWS));
        railScroll=Math.max(0,Math.min(railScroll,Math.max(0,profiles.size()-railRows())));
        status="";
        buttonList.clear();
        buttonList.add(new NeutralButton(0,contentX(),y+h-24,104,18,"EDIT HUD LAYOUT",false));
        buttonList.add(new NeutralButton(3,x+w-86,y+h-24,72,18,"DONE",false));
        buttonList.add(new NeutralButton(4,x+14,y+h-24,86,18,"+ NEW PROFILE",false));
        buttonList.add(new NeutralButton(5,x+8,divider()-22,RAIL-16,16,"IMPORT",false));
        renameField=null;
        int row=editing==null?-1:profiles.indexOf(editing);
        if(row>=0&&row>=railScroll&&row<railScroll+railRows()){
            renameField=new GuiTextField(90,fontRendererObj,x+12,rowY(row)+1,100,14);
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
    private int divider(){return y+h-32;}
    private int cardW(){return Math.max(70,(x+w-14-contentX()-12)/COLUMNS);}
    private int cardH(){return Math.max(64,(divider()-6-(y+52))/VISIBLE_ROWS);}
    private int cardX(int column){return contentX()+column*(cardW()+6);}
    /** Keeps a short last row centered instead of stuck against the left edge. */
    private int rowOffset(List<OtlModule> list,int row){int cols=Math.max(0,Math.min(COLUMNS,list.size()-row*COLUMNS));return (COLUMNS-cols)*(cardW()+6)/2;}
    private int cardY(int row){return y+52+(row-scrollRow)*(cardH()+6);}
    private int railRows(){return Math.max(1,(h-92)/ROW_H);}
    private int rowY(int index){return y+30+(index-railScroll)*ROW_H;}
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
        if(b.id==4){UtilitySettings.newProfile();initGui();return;}
        if(b.id==5){importProfile();return;}
    }

    private void commitRename(){
        String source=editing;editing=null;
        String text=renameField==null?null:renameField.getText();
        renameField=null;
        if(source==null)return;
        if(text!=null){
            String result=UtilitySettings.renameProfile(source,text);
            initGui();
            status=result.equals(source)?"Name unchanged":"Renamed to "+result;
            return;
        }
        initGui();
    }
    private void copyProfile(String name){
        String json=UtilitySettings.profileExport(name);
        initGui();
        if(json==null){status="Could not read "+name;return;}
        setClipboardString(json);
        status="Copied "+name+" to clipboard";
    }
    private void importProfile(){
        String imported=UtilitySettings.importProfile(getClipboardString());
        initGui();
        status=imported==null?"Clipboard holds no profile JSON":"Imported "+imported;
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
        if(wheel!=0){
            int mx=Mouse.getEventX()*width/mc.displayWidth,my=height-Mouse.getEventY()*height/mc.displayHeight;
            if(mx>=x+6&&mx<x+8+RAIL&&my>=y+30&&my<y+30+railRows()*ROW_H){
                railScroll=Math.max(0,Math.min(railScroll+(wheel>0?-1:1),Math.max(0,profiles.size()-railRows())));
            }else if(mx>=contentX()&&mx<x+w&&my>=y+52&&my<divider()){
                scrollRow=Math.max(0,Math.min(scrollRow+(wheel>0?-1:1),rows()-VISIBLE_ROWS));
            }
        }
        super.handleMouseInput();
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
        if(mx>=x+8&&mx<x+8+RAIL&&my>=y+30&&my<railRows()*ROW_H+y+30){
            int index=railScroll+(my-y-30)/ROW_H;
            if(index<profiles.size()){
                String name=profiles.get(index);
                if(mx>=x+93){if(!name.equals(UtilitySettings.profile)&&UtilitySettings.deleteProfile(name)){initGui();status="Deleted "+name;}return;}
                if(mx>=x+82){copyProfile(name);return;}
                if(mx>=x+71){editing=name;initGui();return;}
                UtilitySettings.switchProfile(name);initGui();return;
            }
            return;
        }
        if(overTrack(mx,my)){scrollDrag=true;scrollTo(my);return;}
        for(int i=0;i<TABS.length;i++){
            int tx=tabX(i),tw=tabWidth(i);
            if(my>=y+30&&my<y+46&&mx>=tx&&mx<tx+tw){tab=i;scrollRow=0;initGui();return;}
        }
        List<OtlModule> list=visible();
        for(int row=scrollRow;row<scrollRow+VISIBLE_ROWS;row++){
            int off=rowOffset(list,row);
            for(int column=0;column<COLUMNS;column++){
                int index=row*COLUMNS+column;if(index>=list.size())return;
                OtlModule m=list.get(index);int cx=cardX(column)+off,cy=cardY(row),cw=cardW(),ch=cardH();
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
        super.mouseClickMove(mx,my,button,elapsed);
    }
    protected void mouseReleased(int mx,int my,int state){
        scrollDrag=false;
        super.mouseReleased(mx,my,state);
    }

    public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,0xB0000000);
        BedrockUi.panel(x,y,w,h);
        BedrockUi.inset(x+8,y+6,RAIL,divider()-(y+6));
        BedrockUi.caption(fontRendererObj,"PROFILES",x+14,y+11);
        for(int i=0;i<railRows()&&railScroll+i<profiles.size();i++){
            String name=profiles.get(railScroll+i);int ry=rowY(railScroll+i);
            boolean active=name.equals(UtilitySettings.profile);
            boolean hover=BedrockUi.hit(mx,my,x+8,y+30,RAIL,railRows()*ROW_H)&&my>=ry&&my<ry+ROW_H;
            if(active)drawRect(x+8,ry,x+8+RAIL-4,ry+ROW_H,0xFF48494A);
            else if(hover)drawRect(x+8,ry,x+8+RAIL-4,ry+ROW_H,0xFF3E3E3E);
            if(active)drawRect(x+8,ry,x+10,ry+ROW_H,BedrockUi.ACCENT);
            if(name.equals(editing))continue;
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(name,54),x+14,ry+4,active?0xFFFFFFFF:0xFFC8C8C8);
            boolean renHover=hover&&mx>=x+71&&mx<x+82,copyHover=hover&&mx>=x+82&&mx<x+93;
            BedrockUi.iconBox(x+71,ry+2,renHover,false);BedrockUi.pencil(x+71,ry+2,renHover);
            BedrockUi.iconBox(x+82,ry+2,copyHover,false);BedrockUi.duplicate(x+82,ry+2,copyHover);
            if(!active)BedrockUi.icon(fontRendererObj,x+93,ry+2,"x",hover&&mx>=x+93&&mx<x+104,true);
        }
        BedrockUi.caption(fontRendererObj,"MODULES",contentX(),y+11);
        for(int i=0;i<TABS.length;i++){
            boolean on=tab==i;int tx=tabX(i),tw=tabWidth(i);
            drawString(fontRendererObj,TABS[i],tx+7,y+34,on?0xFFFFFFFF:0xFFBFBFBF);
            if(on)drawRect(tx+6,y+44,tx+tw-6,y+46,BedrockUi.ACCENT);
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
    }

    private void drawCard(OtlModule m,int cx,int cy,int mx,int my){
        int cw=cardW(),ch=cardH();
        boolean hover=BedrockUi.hit(mx,my,cx,cy,cw,ch),on=m.enabled(),usable=m.available();
        drawRect(cx,cy,cx+cw,cy+ch,hover?0xFF4B4B4B:BedrockUi.SLOT);
        drawRect(cx,cy,cx+cw,cy+1,0xFF3E3E3E);
        drawRect(cx,cy+ch-1,cx+cw,cy+ch,0xFF0E0E0E);
        if(usable&&on)drawRect(cx,cy,cx+2,cy+ch,BedrockUi.ACCENT);
        drawString(fontRendererObj,fontRendererObj.trimStringToWidth(m.name,cw-14),cx+8,cy+7,usable?0xFFFFFFFF:0xFF7A7A7A);
        if(ch>=76)drawString(fontRendererObj,m.category,cx+8,cy+19,usable?0xFFBFBFBF:0xFF5A5A5A);
        int options=cy+ch-38;boolean optionsHover=hover&&usable&&my>=options&&my<options+14;
        drawRect(cx+6,options,cx+cw-6,options+14,optionsHover?0xFF2A2A2A:0xFF1E1E1E);
        drawString(fontRendererObj,"OPTIONS",cx+10,options+3,usable?0xFFDEDEDE:0xFF5A5A5A);
        if(usable)drawString(fontRendererObj,">",cx+cw-16,options+3,0xFFBFBFBF);
        int state=cy+ch-18;boolean stateHover=hover&&usable&&my>=state&&my<state+14;
        drawRect(cx+6,state,cx+cw-6,state+14,stateHover?0xFF2A2A2A:0xFF1E1E1E);
        drawString(fontRendererObj,!usable?"LOCKED":on?"ON":"OFF",cx+10,state+3,!usable?0xFFCC7777:on?0xFF8FE0A8:0xFFB8B8B8);
        if(usable)BedrockUi.toggle(cx+cw-34,state+1,on,stateHover);
    }
}
