package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;

/** Profile transfer stays explicit: clipboard in, clipboard out. */
final class ProfileTransferScreen extends GuiScreen {
    private final GuiScreen parent;
    private final String name,json;
    private int x,y,w,h,scroll;
    private List<String> lines=Collections.emptyList();
    private String status="";
    ProfileTransferScreen(GuiScreen parent,String name){
        this.parent=parent;this.name=name;this.json=name==null?null:UtilitySettings.profileExport(name);
    }
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        w=Math.min(470,width-16);h=Math.min(272,height-16);x=(width-w)/2;y=(height-h)/2;buttonList.clear();
        buttonList.add(new NeutralButton(0,x+(w-140)/2,y+h-28,140,20,name==null?"Paste from Clipboard":"Copy to Clipboard",false));
        buttonList.add(new NeutralButton(1,x+w-92,y+h-28,80,20,"Back",false));
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==1){mc.displayGuiScreen(parent);return;}
        if(name==null)importJson(getClipboardString());
        else if(json==null)status="Could not read profile.";
        else{setClipboardString(json);status="Copied to clipboard.";}
    }
    private void importJson(String text){
        String imported=UtilitySettings.importProfile(text);
        if(imported==null)status="Invalid profile JSON (maximum 64 KiB).";
        else{mc.displayGuiScreen(parent);}
    }
    private int rows(){return Math.max(1,(h-120)/10);}
    private int maxScroll(){return Math.max(0,lines.size()-rows());}
    private int trackH(){return rows()*10-4;}
    private int thumbH(){return Math.max(14,trackH()*rows()/Math.max(1,lines.size()));}
    private int thumbY(){int max=maxScroll();return max<=0?y+38:y+40+(trackH()-thumbH())*scroll/max;}
    private boolean overTrack(int mx,int my){return mx>=x+w-20&&mx<x+w-15&&my>=y+38&&my<y+38+trackH();}
    public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();
        if(wheel!=0)scroll=Math.max(0,Math.min(maxScroll(),scroll+(wheel>0?-3:3)));
    }
    public void mouseClicked(int mx,int my,int btn)throws IOException{
        if(btn==0&&overTrack(mx,my)){scroll=(my-y-38-thumbH()/2)*maxScroll()/Math.max(1,trackH()-thumbH());scroll=Math.max(0,Math.min(maxScroll(),scroll));return;}
        super.mouseClicked(mx,my,btn);
    }
    public void mouseClickMove(int mx,int my,int btn,long time){
        if(btn==0&&overTrack(mx,my)){scroll=Math.max(0,Math.min(maxScroll(),(my-y-38-thumbH()/2)*maxScroll()/Math.max(1,trackH()-thumbH())));}
        else super.mouseClickMove(mx,my,btn,time);
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();BedrockUi.panel(x,y,w,h);
        BedrockUi.header(fontRendererObj,name==null?"Import Profile":"Export Profile: "+name,x,y,w);
        BedrockUi.inset(x+12,y+32,w-24,h-112);
        lines=new ArrayList<>();
        if(name==null)lines.addAll(fontRendererObj.listFormattedStringToWidth("Paste profile JSON from your clipboard. Import creates and activates a separate profile.",w-40));
        else if(json!=null)for(String line:json.split("\n"))lines.addAll(fontRendererObj.listFormattedStringToWidth(line,w-40));
        scroll=Math.min(scroll,maxScroll());
        for(int i=0;i<rows()&&i+scroll<lines.size();i++)fontRendererObj.drawString(lines.get(i+scroll),x+18,y+38+i*10,BedrockUi.MUTED);
        if(lines.size()>rows()){
            drawRect(x+w-21,y+36,x+w-15,y+36+trackH(),0xFF171819);
            drawRect(x+w-19,thumbY(),x+w-17,thumbY()+thumbH(),overTrack(mx,my)?0xFFFFFFFF:0xFFD0D1D4);
        }
        drawString(fontRendererObj,fontRendererObj.trimStringToWidth(status,w-24),x+12,y+h-72,BedrockUi.MUTED);
        super.drawScreen(mx,my,partial);
    }
}
