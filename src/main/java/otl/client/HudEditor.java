package otl.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;

/** Gameplay HUD positions and editor hit boxes share bounds. */
final class HudEditor extends GuiScreen {
    private static final String[] IDS={HudLayout.NOTICES,HudLayout.TABS,HudLayout.CONFIRM,HudLayout.KILLS,HudLayout.BOW,HudLayout.MINIMAP};
    private String selected=HudLayout.KILLS,dragging;
    private int dx,dy,corner=-1,startScale,guideX=-1,guideY=-1;
    private HudLayout.Rect start;
    public boolean doesGuiPauseGame(){return false;}
    public void initGui(){
        UtilitySettings.load();buttonList.clear();dragging=null;corner=-1;guideX=guideY=-1;
        buttonList.add(new NeutralButton(0,width/2-45,height/2-11,90,22,"Mods",false,70));
    }
    static HudLayout.Rect bounds(String id){
        if(HudLayout.KILLS.equals(id))return KillFeed.bounds();
        if(HudLayout.TABS.equals(id))return ReliableChat.tabBounds();
        if(HudLayout.CONFIRM.equals(id))return KillConfirm.bounds();
        if(HudLayout.HEIGHT.equals(id))return HeightLimitOverlay.bounds();
        if(HudLayout.BOW.equals(id))return BowAccuracy.bounds();
        if(HudLayout.MINIMAP.equals(id))return Minimap.bounds();
        return ServerNotices.bounds();
    }
    private boolean enabled(String id){return HudLayout.MINIMAP.equals(id)?UtilitySettings.minimap:HudLayout.BOW.equals(id)?UtilitySettings.bowAccuracy:HudLayout.HEIGHT.equals(id)?UtilitySettings.heightLimitIndicator:HudLayout.CONFIRM.equals(id)?UtilitySettings.killConfirm:HudLayout.KILLS.equals(id)?UtilitySettings.killFeed:HudLayout.TABS.equals(id)?UtilitySettings.showTabChat:UtilitySettings.nonPlayerChat;}
    private static int size(String id){return HudLayout.MINIMAP.equals(id)?UtilitySettings.minimapSize:HudLayout.BOW.equals(id)?UtilitySettings.bowAccuracySize:HudLayout.HEIGHT.equals(id)?UtilitySettings.heightLimitSize:HudLayout.CONFIRM.equals(id)?UtilitySettings.killConfirmSize:HudLayout.KILLS.equals(id)?UtilitySettings.killFeedSize:HudLayout.NOTICES.equals(id)?UtilitySettings.noticeSize:UtilitySettings.tabHudSize;}
    private static void size(String id,int value){if(HudLayout.MINIMAP.equals(id))UtilitySettings.minimapSize=value;else if(HudLayout.BOW.equals(id))UtilitySettings.bowAccuracySize=value;else if(HudLayout.HEIGHT.equals(id))UtilitySettings.heightLimitSize=value;else if(HudLayout.CONFIRM.equals(id))UtilitySettings.killConfirmSize=value;else if(HudLayout.KILLS.equals(id))UtilitySettings.killFeedSize=value;else if(HudLayout.NOTICES.equals(id))UtilitySettings.noticeSize=value;else UtilitySettings.tabHudSize=value;}
    private List<String> order(){List<String> ids=new ArrayList<>(Arrays.asList(IDS));ids.remove(selected);ids.add(selected);return ids;}
    int toolsY(HudLayout.Rect r){return r.y+r.h+17<=height?r.y+r.h+2:Math.max(2,r.y-16);}
    HudLayout.Rect frame(HudLayout.Rect r){
        int w=Math.min(width-4,Math.max(76,r.w+4)),x=Math.max(2,Math.min(width-w-2,r.x-2));
        int top=Math.min(r.y-2,toolsY(r)-2),bottom=Math.max(r.y+r.h+2,toolsY(r)+14);
        return new HudLayout.Rect(x,top,w,bottom-top);
    }
    private static int handle(HudLayout.Rect r,int mx,int my){
        for(int i=0;i<4;i++){int x=i%2==0?r.x:r.x+r.w,y=i<2?r.y:r.y+r.h;if(Math.abs(mx-x)<=4&&Math.abs(my-y)<=4)return i;}return -1;
    }
    private void toggle(String id){
        if(HudLayout.MINIMAP.equals(id))UtilitySettings.minimap=!UtilitySettings.minimap;
        else if(HudLayout.BOW.equals(id)){UtilitySettings.bowAccuracy=!UtilitySettings.bowAccuracy;BowAccuracy.INSTANCE.reset();}
        else if(HudLayout.HEIGHT.equals(id))UtilitySettings.heightLimitIndicator=!UtilitySettings.heightLimitIndicator;
        else if(HudLayout.CONFIRM.equals(id)){UtilitySettings.killConfirm=!UtilitySettings.killConfirm;KillConfirm.state.clear();}
        else if(HudLayout.KILLS.equals(id))UtilitySettings.killFeed=!UtilitySettings.killFeed;
        else if(HudLayout.TABS.equals(id))UtilitySettings.showTabChat=!UtilitySettings.showTabChat;
        else UtilitySettings.nonPlayerChat=false;
        UtilitySettings.save();
    }
    protected void actionPerformed(GuiButton b){
        if(b.id==0)mc.displayGuiScreen(new ModulesScreen(this));
    }
    protected void mouseClicked(int mx,int my,int button)throws IOException{
        for(GuiButton b:buttonList)if(b.visible&&b.enabled&&mx>=b.xPosition&&mx<b.xPosition+b.width&&my>=b.yPosition&&my<b.yPosition+b.height){super.mouseClicked(mx,my,button);return;}
        List<String> ids=order();
        if(button==0)for(int i=ids.size()-1;i>=0;i--){String id=ids.get(i);HudLayout.Rect r=bounds(id),f=frame(r);int handle=handle(r,mx,my);if(!f.contains(mx,my)&&handle<0)continue;
            selected=id;
            if(my>=toolsY(r)&&my<toolsY(r)+12&&mx<f.x+17){mc.displayGuiScreen(HudLayout.MINIMAP.equals(id)?new MinimapSettings(this):HudLayout.BOW.equals(id)?new BowOptimizerSettings(this):HudLayout.HEIGHT.equals(id)?new HeightLimitSettings(this):HudLayout.CONFIRM.equals(id)?new KillConfirmSettings(this):HudLayout.KILLS.equals(id)?new KillFeedSettings(this):HudLayout.NOTICES.equals(id)?new ServerNoticeSettings(this):new UtilityScreen(true,this));return;}
            if(my>=toolsY(r)&&my<toolsY(r)+12&&mx>=f.x+f.w-17){toggle(id);return;}
            dragging=id;corner=handle;start=r;startScale=size(id);dx=mx-r.x;dy=my-r.y;return;
        }
        super.mouseClicked(mx,my,button);
    }
    protected void mouseClickMove(int mx,int my,int button,long elapsed){
        if(dragging==null)return;
        if(corner>=0){
            size(dragging,HudLayout.resizedScale(startScale,start,corner,mx,my));HudLayout.Rect r=bounds(dragging);
            int x=corner%2==0?start.x+start.w-r.w:start.x,y=corner<2?start.y+start.h-r.h:start.y;
            UtilitySettings.hudPositions.put(dragging,HudLayout.dragged(width,height,r.w,r.h,x,y));guideX=guideY=-1;
        }else{
            HudLayout.Rect r=bounds(dragging);List<HudLayout.Rect> others=new ArrayList<>();for(String id:IDS)if(!id.equals(dragging))others.add(bounds(id));
            HudLayout.Snap snap=HudLayout.snap(width,height,new HudLayout.Rect(mx-dx,my-dy,r.w,r.h),others,isShiftKeyDown());
            UtilitySettings.hudPositions.put(dragging,HudLayout.dragged(width,height,r.w,r.h,snap.x,snap.y));guideX=snap.guideX;guideY=snap.guideY;
        }
    }
    protected void mouseReleased(int mx,int my,int state){dragging=null;corner=-1;guideX=guideY=-1;UtilitySettings.save();super.mouseReleased(mx,my,state);}
    public void onGuiClosed(){dragging=null;corner=-1;guideX=guideY=-1;UtilitySettings.save();}
    public void drawScreen(int mx,int my,float partial){
        drawRect(0,0,width,height,0x50101010);
        int hx=width/2-91,hy=height-22;
        drawRect(hx,hy,hx+182,hy+22,0x14000000);
        drawRect(hx,hy,hx+182,hy+1,0x28FFFFFF);drawRect(hx,hy+21,hx+182,hy+22,0x28FFFFFF);
        for(int k=0;k<=9;k++)drawRect(hx+k*20,hy+1,hx+k*20+1,hy+21,0x28FFFFFF);
        if(dragging!=null&&corner<0){
            for(int x:new int[]{4,width/4,width/2,width*3/4,width-4})drawRect(x,0,x+1,height,0x407DDFFF);
            for(int y:new int[]{4,height/4,height/2,height*3/4,height-4})drawRect(0,y,width,y+1,0x407DDFFF);
        }
        String hover=null;
        for(String id:order()){
            HudLayout.Rect r=bounds(id),f=frame(r);boolean on=enabled(id);int color=id.equals(selected)?0xFF7DB8FF:0xFF888888;
            drawRect(f.x,f.y,f.x+f.w,f.y+f.h,0x20101010);
            drawRect(f.x,f.y,f.x+f.w,f.y+1,color);drawRect(f.x,f.y+f.h-1,f.x+f.w,f.y+f.h,color);
            drawRect(f.x,f.y,f.x+1,f.y+f.h,color);drawRect(f.x+f.w-1,f.y,f.x+f.w,f.y+f.h,color);
            if(HudLayout.MINIMAP.equals(id))Minimap.drawPreview();else if(HudLayout.BOW.equals(id))BowAccuracy.drawPreview();else if(HudLayout.HEIGHT.equals(id))HeightLimitOverlay.drawPreview();else if(HudLayout.CONFIRM.equals(id))KillConfirm.draw(true);else if(HudLayout.KILLS.equals(id))KillFeed.drawPreview();else if(HudLayout.NOTICES.equals(id))ServerNotices.drawPreview();else ReliableChat.drawTabsPreview();
            int gy=toolsY(r);drawRect(f.x+2,gy,f.x+f.w-2,gy+12,0xDD202020);
            gear(f.x+4,gy+2);drawString(fontRendererObj,on?"x":"+",f.x+f.w-12,gy+2,0xFFFFFFFF);
            String label=HudLayout.MINIMAP.equals(id)?"Minimap":HudLayout.BOW.equals(id)?"Bow Accuracy":HudLayout.HEIGHT.equals(id)?"Height Limit":HudLayout.CONFIRM.equals(id)?"Kill Confirm":HudLayout.KILLS.equals(id)?"Kill Feed":HudLayout.NOTICES.equals(id)?"Server Notices":"Chat tabs";
            drawString(fontRendererObj,fontRendererObj.trimStringToWidth(label+(on?"":" (disabled)"),f.w-36),f.x+19,gy+2,on?0xFFFFFFFF:0xFFAAAAAA);
            for(int i=0;i<4;i++){int x=i%2==0?r.x:r.x+r.w,y=i<2?r.y:r.y+r.h;drawRect(x-2,y-2,x+3,y+3,color);}
            if(handle(r,mx,my)>=0)hover="Drag corner to resize";
            else if(f.contains(mx,my)&&my>=gy&&my<gy+12){if(mx<f.x+17)hover="Settings";else if(mx>=f.x+f.w-17)hover=on?"Disable":"Enable";}
        }
        if(guideX>=0)drawRect(guideX,0,guideX+1,height,0xAA7DDFFF);
        if(guideY>=0)drawRect(0,guideY,width,guideY+1,0xAA7DDFFF);
        drawCenteredString(fontRendererObj,"HUD editor",width/2,height/2-27,0xFFFFFFFF);
        drawCenteredString(fontRendererObj,"Drag / resize corners | Shift bypasses snap",width/2,height/2+17,0xFFCCCCCC);
        super.drawScreen(mx,my,partial);
        if(hover!=null)drawHoveringText(Collections.singletonList(hover),mx,my);
    }
    private void gear(int x,int y){
        drawRect(x+1,y+1,x+7,y+7,0xFFDDDDDD);drawRect(x+3,y,x+5,y+8,0xFFDDDDDD);drawRect(x,y+3,x+8,y+5,0xFFDDDDDD);drawRect(x+3,y+3,x+5,y+5,0xFF202020);
    }
}
