package otl.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Flat Bedrock chrome: neutral mid-gray panels, light-gray beveled buttons, dark button text. */
final class BedrockUi {
    // Sidebar/panel grays (neutral, lighter than Java's near-black menus).
    static final int PANEL=BedrockTokens.SURFACE_PANEL,EDGE=BedrockTokens.SURFACE_EDGE,INSET=BedrockTokens.SURFACE_INSET,SLOT=BedrockTokens.SURFACE_CARD;
    static final int TEXT=BedrockTokens.TEXT_PRIMARY,MUTED=BedrockTokens.TEXT_MUTED,DIM=BedrockTokens.TEXT_DIM,ACCENT=BedrockTokens.STATE_ACCENT,CAPTION=BedrockTokens.TEXT_PRIMARY;
    private static final int BUTTON_FACE=0xFFD0D1D4,BUTTON_HOVER=BedrockTokens.STATE_HOVER,BUTTON_DISABLED=0xFF4A4A4A;
    private static final int BUTTON_TEXT=0xFF282828,DISABLED_TEXT=0xFF9B9B9B;
    private static final int BEVEL_LIGHT=0xFFC6C6C6,BEVEL_DARK=0xFF545454;

    static void panel(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,PANEL);
        Gui.drawRect(x,y,x+w,y+2,EDGE);
        Gui.drawRect(x,y,x+2,y+h,EDGE);
        Gui.drawRect(x,y+h-2,x+w,y+h,0xFF202122);
        Gui.drawRect(x+w-2,y,x+w,y+h,0xFF202122);
    }
    static void inset(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,INSET);
        Gui.drawRect(x,y,x+w,y+1,0xFF3A3A3A);
    }
    static void header(FontRenderer font,String title,int x,int y,int w){
        Gui.drawRect(x+2,y+2,x+w-2,y+24,0xFFE6E8EB);
        Gui.drawRect(x+2,y+24,x+w-2,y+26,0xFF202122);
        font.drawString(font.trimStringToWidth(title,Math.max(0,w-32)),x+14,y+10,0xFF202122);
    }
    static void headerCentered(FontRenderer font,String title,int x,int y,int w){
        Gui.drawRect(x+2,y+2,x+w-2,y+24,0xFFE6E8EB);
        Gui.drawRect(x+2,y+24,x+w-2,y+26,0xFF202122);
        plainCentered(font,title,x+w/2,y+9,0xFF202122);
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
    static void plainCentered(FontRenderer font,String text,int cx,int y,int color){
        font.drawString(text,cx-font.getStringWidth(text)/2,y,color);
    }
    static void backArrow(FontRenderer font,int x,int y,boolean hover){
        plainCentered(font,"<",x+9,y+4,hover?0xFFFFFFFF:0xFF303132);
    }
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled){
        button(font,x,y,w,h,label,hover,enabled,255);
    }
    /** Alpha lets ghost chrome (e.g. the Mods button) keep the game visible behind it. */
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled,int alpha){
        int face=!enabled?BUTTON_DISABLED:hover?BUTTON_HOVER:BUTTON_FACE;
        int bevel=enabled?BEVEL_LIGHT:0xFF5A5A5A;
        int a=Math.max(0,Math.min(255,alpha))<<24;
        Gui.drawRect(x,y,x+w,y+h,a|0x202122);
        Gui.drawRect(x+1,y+1,x+w-1,y+h-2,a|(face&0xFFFFFF));
        Gui.drawRect(x+1,y+1,x+w-1,y+2,a|(bevel&0xFFFFFF));
        Gui.drawRect(x+1,y+1,x+2,y+h-2,a|(bevel&0xFFFFFF));
        Gui.drawRect(x+1,y+h-3,x+w-1,y+h-1,a|(BEVEL_DARK&0xFFFFFF));
        int tx=x+(w-font.getStringWidth(label))/2;
        font.drawString(label,tx,y+(h-font.FONT_HEIGHT)/2,a|(enabled?BUTTON_TEXT&0xFFFFFF:DISABLED_TEXT&0xFFFFFF));
    }
    /** Bedrock switch: gray track, white knob, piston green when on. */
    static void toggle(int x,int y,boolean on,boolean hover){
        Gui.drawRect(x,y,x+26,y+12,0xFF171819);
        Gui.drawRect(x+1,y+1,x+25,y+11,on?0xFF3C8527:hover?0xFF777879:0xFF58595A);
        Gui.drawRect(x+1,y+1,x+25,y+2,on?ACCENT:0xFF999A9B);
        int knob=on?x+14:x;
        Gui.drawRect(knob,y-1,knob+12,y+13,0xFF171819);
        Gui.drawRect(knob+1,y,knob+11,y+12,0xFFE6E8EB);
        Gui.drawRect(knob+2,y+1,knob+10,y+2,0xFFFFFFFF);
        Gui.drawRect(knob+1,y+10,knob+11,y+12,0xFFB5B7BA);
    }
    /** Small square icon button used for row actions; glyph stays a string fallback. */
    static void icon(FontRenderer font,int x,int y,String glyph,boolean hover,boolean danger){
        iconBox(x,y,hover,danger);
        centered(font,glyph,x+6,y+2,hover?(danger?0xFFFF9090:TEXT):(danger?0xFFA86060:DIM));
    }
    /** Hover/pressed chip behind an icon glyph. */
    static void iconBox(int x,int y,boolean hover,boolean danger){
        if(hover)Gui.drawRect(x,y,x+11,y+12,0xFF4A4A4A);
    }
    /** Pencil: rename. Vertical pencil, eraser up, lead down. */
    static void pencil(int x,int y,boolean hover){
        int ink=hover?TEXT:0xFFB8B9BA;
        for(int i=0;i<6;i++)Gui.drawRect(x+3+i,y+8-i,x+5+i,y+10-i,ink);
        Gui.drawRect(x+2,y+10,x+5,y+11,ink);
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
    static void item(Item item,int x,int y){
        if(item==null)return;
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item),x,y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
    /** Counterclockwise circular arrow, chunky 2px pixels like Bedrock glyphs. */
    static void resetArrow(int x,int y,boolean hover){
        int ink=hover?0xFFFFFFFF:0xFFF0F1F2;
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
