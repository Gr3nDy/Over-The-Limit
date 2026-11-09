package otl.client;

import net.minecraft.client.gui.ScaledResolution;

/** Screen-relative positions keep right/center alignment when GUI scale changes. */
final class HudLayout {
    static final String TABS="chat_tabs",KILLS="kill_feed",NOTICES="server_notices",CONFIRM="kill_confirm",HEIGHT="height_limit",BOW="bow_accuracy";
    static boolean valid(String id){return TABS.equals(id)||KILLS.equals(id)||NOTICES.equals(id)||CONFIRM.equals(id)||HEIGHT.equals(id)||BOW.equals(id);}
    static final class Position {
        final double x,y;
        Position(double x,double y){if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid HUD position");this.x=Math.max(0,Math.min(1,x));this.y=Math.max(0,Math.min(1,y));}
    }
    static final class Rect {
        final int x,y,w,h;
        Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        boolean contains(int mx,int my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    }
    static Position position(String id){
        Position p=UtilitySettings.hudPositions.get(id);if(p!=null)return p;
        if(CONFIRM.equals(id)){
            ScaledResolution sr=new ScaledResolution(MinecraftAccess.get());float scale=UtilitySettings.killConfirmSize/100F;
            return confirmPosition(sr.getScaledWidth(),sr.getScaledHeight(),(int)Math.ceil(KillConfirm.WIDTH*scale),(int)Math.ceil(KillConfirm.HEIGHT*scale));
        }
        if(TABS.equals(id)){int n=UtilitySettings.tabChatPosition;return new Position(n==1||n==3?1:0,n>=2?0:1);}
        if(HEIGHT.equals(id))return new Position(.5,1);
        if(BOW.equals(id))return new Position(1,.5);
        return new Position(KILLS.equals(id)?1:0,0);
    }
    static Rect rect(int sw,int sh,int w,int h,Position p){
        w=Math.max(1,Math.min(w,Math.max(1,sw-8)));h=Math.max(1,Math.min(h,Math.max(1,sh-8)));
        return new Rect(4+(int)Math.round(Math.max(0,sw-w-8)*p.x),4+(int)Math.round(Math.max(0,sh-h-8)*p.y),w,h);
    }
    static Rect bounds(String id,int w,int h){ScaledResolution sr=new ScaledResolution(MinecraftAccess.get());return rect(sr.getScaledWidth(),sr.getScaledHeight(),w,h,position(id));}
    static Position dragged(int sw,int sh,int w,int h,int x,int y){return new Position((x-4.0)/Math.max(1,sw-w-8),(y-4.0)/Math.max(1,sh-h-8));}
    /** Anchor is the gold plus sign itself: its centre sits exactly on the crosshair. */
    static Position confirmPosition(int sw,int sh,int w,int h){
        float scale=h/(float)KillConfirm.HEIGHT;
        float plusX=KillConfirm.EXP_X+KillConfirm.PLUS_CENTER_X*KillConfirm.EXP_TEXT_SCALE;
        float plusY=KillConfirm.EXP_Y+KillConfirm.GLYPH_CENTER_Y*KillConfirm.EXP_TEXT_SCALE;
        return dragged(sw,sh,w,h,sw/2-Math.round(plusX*scale),sh/2-Math.round(plusY*scale));
    }
    static final class Snap {
        final int x,y,guideX,guideY;
        Snap(int x,int y,int gx,int gy){this.x=x;this.y=y;guideX=gx;guideY=gy;}
    }
    static Snap snap(int sw,int sh,Rect moving,java.util.List<Rect> others,boolean bypass){
        int x=Math.max(4,Math.min(Math.max(4,sw-moving.w-4),moving.x)),y=Math.max(4,Math.min(Math.max(4,sh-moving.h-4),moving.y));
        if(bypass)return new Snap(x,y,-1,-1);
        int[] sx=snapAxis(x,moving.w,sw,others,true),sy=snapAxis(y,moving.h,sh,others,false);
        return new Snap(sx[0],sy[0],sx[1],sy[1]);
    }
    private static int[] snapAxis(int pos,int size,int screen,java.util.List<Rect> others,boolean horizontal){
        java.util.List<int[]> targets=new java.util.ArrayList<>();
        targets.add(new int[]{screen/2-size/2,screen/2});targets.add(new int[]{4,4});targets.add(new int[]{screen-size-4,screen-4});
        for(int line:new int[]{screen/4,screen/2,screen*3/4})for(int offset:new int[]{0,size/2,size})targets.add(new int[]{line-offset,line});
        for(Rect r:others){
            int start=horizontal?r.x:r.y,length=horizontal?r.w:r.h;
            for(int edge:new int[]{start,start+length/2,start+length})for(int offset:new int[]{0,size/2,size})targets.add(new int[]{edge-offset,edge});
            targets.add(new int[]{start+length+4,start+length+2});targets.add(new int[]{start-size-4,start-2});
        }
        int distance=7,best=pos,guide=-1;
        for(int[] target:targets){int d=Math.abs(pos-target[0]);if(target[0]>=4&&target[0]+size<=screen-4&&d<distance){distance=d;best=target[0];guide=target[1];}}
        return new int[]{best,guide};
    }
    static int resizedScale(int startScale,Rect start,int corner,int mx,int my){
        boolean left=corner%2==0,top=corner<2;double dx=(mx-(left?start.x+start.w:start.x))*(left?-1:1),dy=(my-(top?start.y+start.h:start.y))*(top?-1:1);
        double ratio=(dx*start.w+dy*start.h)/(start.w*(double)start.w+start.h*(double)start.h);
        return Math.max(50,Math.min(150,(int)Math.round(startScale*ratio)));
    }
}
