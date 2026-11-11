package otl.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Flat Bedrock chrome: neutral mid-gray panels, light-gray beveled buttons, dark button text. */
final class BedrockUi {
    // Sidebar/panel grays (neutral, lighter than Java's near-black menus).
    static final int PANEL=0xFF3B3B3B,EDGE=0xFF4B4B4B,INSET=0xFF2D2D2D,SLOT=0xFF464646;
    static final int TEXT=0xFFFFFFFF,MUTED=0xFFE0E0E0,DIM=0xFFB0B0B0,ACCENT=0xFF6CC349,CAPTION=0xFFFFFFFF;
    private static final int BUTTON_FACE=0xFF8F8F8F,BUTTON_HOVER=0xFFA6A6A6,BUTTON_DISABLED=0xFF4A4A4A;
    private static final int BUTTON_TEXT=0xFF282828,DISABLED_TEXT=0xFF9B9B9B;
    private static final int BEVEL_LIGHT=0xFFC6C6C6,BEVEL_DARK=0xFF545454;

    static void panel(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,PANEL);
        Gui.drawRect(x,y,x+w,y+1,EDGE);
        Gui.drawRect(x,y,x+1,y+h,0xFF4E4E4E);
        Gui.drawRect(x,y+h-1,x+w,y+h,0xFF242424);
        Gui.drawRect(x+w-1,y,x+w,y+h,0xFF242424);
    }
    static void inset(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,INSET);
        Gui.drawRect(x,y,x+w,y+1,0xFF3A3A3A);
    }
    static void caption(FontRenderer font,String label,int x,int y){
        font.drawStringWithShadow(label,x,y,CAPTION);
    }
    /** Caption plus a divider, so option groups read as sections rather than one long list. */
    static void section(FontRenderer font,String label,int x,int y,int w){
        caption(font,label,x,y);
        Gui.drawRect(x,y+10,x+w,y+11,0xFF3A3A3A);
    }
    static void centered(FontRenderer font,String text,int cx,int y,int color){
        font.drawStringWithShadow(text,cx-font.getStringWidth(text)/2,y,color);
    }
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled){
        button(font,x,y,w,h,label,hover,enabled,255);
    }
    /** Alpha lets ghost chrome (e.g. the Mods button) keep the game visible behind it. */
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled,int alpha){
        int face=!enabled?BUTTON_DISABLED:hover?BUTTON_HOVER:BUTTON_FACE;
        int bevel=enabled?BEVEL_LIGHT:0xFF5A5A5A;
        int a=Math.max(0,Math.min(255,alpha))<<24;
        Gui.drawRect(x,y,x+w,y+h,a|(face&0xFFFFFF));
        Gui.drawRect(x,y,x+w,y+1,a|(bevel&0xFFFFFF));
        Gui.drawRect(x,y,x+1,y+h,a|(bevel&0xFFFFFF));
        Gui.drawRect(x,y+h-1,x+w,y+h,a|(BEVEL_DARK&0xFFFFFF));
        Gui.drawRect(x+w-1,y,x+w,y+h,a|(BEVEL_DARK&0xFFFFFF));
        int tx=x+(w-font.getStringWidth(label))/2;
        font.drawString(label,tx,y+(h-8)/2+1,a|(enabled?BUTTON_TEXT&0xFFFFFF:DISABLED_TEXT&0xFFFFFF));
    }
    /** Bedrock switch: gray track, white knob, piston green when on. */
    static void toggle(int x,int y,boolean on,boolean hover){
        Gui.drawRect(x,y,x+26,y+12,on?0xFF3F9142:hover?0xFF5A5A5A:0xFF4A4A4A);
        Gui.drawRect(x,y,x+26,y+1,on?ACCENT:0xFF5A5A5A);
        Gui.drawRect(x,y+11,x+26,y+12,0xFF2A2A2A);
        int knob=on?x+14:x;
        Gui.drawRect(knob+1,y+1,knob+12,y+11,0xFFFFFFFF);
        Gui.drawRect(knob+1,y+1,knob+12,y+2,0xFFE8E8E8);
    }
    /** Small square icon button used for row actions; glyph stays a string fallback. */
    static void icon(FontRenderer font,int x,int y,String glyph,boolean hover,boolean danger){
        iconBox(x,y,hover,danger);
        centered(font,glyph,x+6,y+2,hover?(danger?0xFFFF9090:TEXT):(danger?0xFFA86060:DIM));
    }
    /** Hover/pressed chip behind an icon glyph. */
    static void iconBox(int x,int y,boolean hover,boolean danger){
        Gui.drawRect(x,y,x+11,y+12,hover?0xFF4A4A4A:0xFF353535);
    }
    /** Pencil: rename. Vertical pencil, eraser up, lead down. */
    static void pencil(int x,int y,boolean hover){
        int eraser=hover?0xFFE79BA6:0xFFCE8B96,ferrule=hover?0xFFB0BEC5:0xFF8A98A0,body=hover?0xFFFFE082:0xFFE6C65A,lead=hover?0xFF404040:0xFF303030;
        Gui.drawRect(x+3,y+1,x+9,y+3,eraser);
        Gui.drawRect(x+3,y+3,x+9,y+4,ferrule);
        Gui.drawRect(x+3,y+4,x+9,y+9,body);
        Gui.drawRect(x+4,y+9,x+8,y+10,ferrule);
        Gui.drawRect(x+5,y+10,x+7,y+11,lead);
    }
    /** Two overlapping pages: copy. */
    static void duplicate(int x,int y,boolean hover){
        int ink=hover?0xFFFFFFFF:0xFFE0E0E0,face=hover?0xFF3C3C3C:0xFF2C2C2C;
        Gui.drawRect(x+1,y+1,x+9,y+8,face);
        Gui.drawRect(x+1,y+1,x+9,y+2,ink);Gui.drawRect(x+1,y+1,x+2,y+8,ink);
        Gui.drawRect(x+1,y+7,x+9,y+8,ink);Gui.drawRect(x+8,y+1,x+9,y+8,ink);
        Gui.drawRect(x+3,y+3,x+11,y+10,face);
        Gui.drawRect(x+3,y+3,x+11,y+4,ink);Gui.drawRect(x+3,y+3,x+4,y+10,ink);
        Gui.drawRect(x+3,y+9,x+11,y+10,ink);Gui.drawRect(x+10,y+3,x+11,y+10,ink);
    }
    static boolean hit(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    /** Counterclockwise circular arrow, chunky 2px pixels like Bedrock glyphs. */
    static void resetArrow(int x,int y,boolean hover){
        int ink=hover?0xFFFFFFFF:0xFFD8D8D8;
        float cx=x+6f,cy=y+6f,r=4.2f;
        for(int deg=225;deg<=525;deg+=9){
            double rad=Math.toRadians(deg);
            int px=Math.round(cx+r*(float)Math.cos(rad)),py=Math.round(cy+r*(float)Math.sin(rad));
            Gui.drawRect(px,py,px+2,py+2,ink);
        }
        int tipX=Math.round(cx+r*(float)Math.cos(Math.toRadians(225))),tipY=Math.round(cy+r*(float)Math.sin(Math.toRadians(225)));
        Gui.drawRect(tipX,tipY-2,tipX+2,tipY+2,ink);
        Gui.drawRect(tipX-2,tipY,tipX+4,tipY+1,ink);
    }
}