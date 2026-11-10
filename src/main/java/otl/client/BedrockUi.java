package otl.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/** Flat Bedrock chrome, anchored to Mojang's ui/_global_variables.json values. */
final class BedrockUi {
    // red_gray_panel_color / sidebar_default_option_color / sidebar_selected_bar_color
    static final int PANEL=0xF0262423,EDGE=0xFF45403E,INSET=0xFF141414,SLOT=0xFF393939;
    static final int TEXT=0xFFFFFFFF,MUTED=0xFFE0E0E0,DIM=0xFFB0B0B0,ACCENT=0xFF6CC349,CAPTION=0xFFFFFFFF;
    private static final int BUTTON_FACE=0xFF8F8F8F,BUTTON_HOVER=0xFFA6A6A6,BUTTON_DISABLED=0xFF4A4A4A;
    private static final int BEVEL_LIGHT=0xFFC6C6C6,BEVEL_DARK=0xFF545454,DISABLED_TEXT=0xFF9B9B9B;

    static void panel(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,PANEL);
        Gui.drawRect(x,y,x+w,y+1,EDGE);
        Gui.drawRect(x,y,x+1,y+h,0xFF454545);
        Gui.drawRect(x,y+h-1,x+w,y+h,0xFF0C0C0C);
        Gui.drawRect(x+w-1,y,x+w,y+h,0xFF0C0C0C);
    }
    static void inset(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,INSET);
        Gui.drawRect(x,y,x+w,y+1,0xFF262626);
    }
    static void caption(FontRenderer font,String label,int x,int y){
        font.drawStringWithShadow(label,x,y,CAPTION);
    }
    /** Caption plus a divider, so option groups read as sections rather than one long list. */
    static void section(FontRenderer font,String label,int x,int y,int w){
        caption(font,label,x,y);
        Gui.drawRect(x,y+10,x+w,y+11,0xFF2E2E2E);
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
        font.drawStringWithShadow(label,tx,y+(h-8)/2,a|(enabled?TEXT&0xFFFFFF:DISABLED_TEXT&0xFFFFFF));
    }
    /** Bedrock switch: dark track, white knob, piston green when on. */
    static void toggle(int x,int y,boolean on,boolean hover){
        Gui.drawRect(x,y,x+26,y+12,on?0xFF3F9142:hover?0xFF464646:0xFF2E2E2E);
        Gui.drawRect(x,y,x+26,y+1,on?ACCENT:0xFF464646);
        Gui.drawRect(x,y+11,x+26,y+12,0xFF101010);
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
        Gui.drawRect(x,y,x+11,y+12,hover?0xFF2E2E2E:0xFF1F1F1F);
    }
    /** Pencil: rename. */
    static void pencil(int x,int y,boolean hover){
        int skin=hover?0xFFFFFFFF:0xFFE8E8E8,wood=hover?0xFFFFE2B8:0xFFCB9A,lead=hover?0xFF404040:0xFF202020,band=hover?0xFFB0B0B0:0xFF8A8A8A;
        Gui.drawRect(x+3,y,x+8,y+2,skin);
        Gui.drawRect(x+3,y+2,x+8,y+3,band);
        Gui.drawRect(x+3,y+3,x+8,y+8,skin);
        Gui.drawRect(x+4,y+8,x+7,y+9,wood);
        Gui.drawRect(x+5,y+9,x+6,y+10,lead);
    }
    /** Two overlapping pages: copy. */
    static void duplicate(int x,int y,boolean hover){
        int ink=hover?0xFFFFFFFF:0xFFD8D8D8;
        Gui.drawRect(x+4,y,x+11,y+1,ink);
        Gui.drawRect(x+4,y+6,x+11,y+7,ink);
        Gui.drawRect(x+4,y,x+5,y+7,ink);
        Gui.drawRect(x+10,y,x+11,y+7,ink);
        Gui.drawRect(x,y+5,x+7,y+6,ink);
        Gui.drawRect(x,y+11,x+7,y+12,ink);
        Gui.drawRect(x,y+5,x+1,y+12,ink);
        Gui.drawRect(x+6,y+5,x+7,y+12,ink);
        Gui.drawRect(x+2,y+8,x+5,y+9,ink);
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
