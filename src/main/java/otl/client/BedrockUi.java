package otl.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Shared dark menu chrome. */
final class BedrockUi {
    // Sidebar/panel grays (neutral, lighter than Java's near-black menus).
    static final int PANEL=BedrockTokens.SURFACE_PANEL,EDGE=BedrockTokens.SURFACE_EDGE,INSET=BedrockTokens.SURFACE_INSET,SLOT=BedrockTokens.SURFACE_CARD;
    static final int TEXT=BedrockTokens.TEXT_PRIMARY,MUTED=BedrockTokens.TEXT_MUTED,DIM=BedrockTokens.TEXT_DIM,ACCENT=BedrockTokens.STATE_ACCENT,CAPTION=BedrockTokens.TEXT_PRIMARY;
    private static final int BUTTON_FACE=0xFF2A2E38,BUTTON_HOVER=BedrockTokens.STATE_HOVER,BUTTON_DISABLED=0xFF191C22;
    private static final int BUTTON_TEXT=0xFFE8EAF0,DISABLED_TEXT=0xFF6B7280;

    static void rounded(int x,int y,int w,int h,int colour){rounded(x,y,w,h,1,colour);}
    /** Rounded rect; radius up to 4 px reads as Lunar's soft corners at GUI scale. */
    static void rounded(int x,int y,int w,int h,int r,int colour){
        if(w<=0||h<=0)return;
        if(r<=0||w<2*r+2||h<2*r+2){Gui.drawRect(x,y,x+w,y+h,colour);return;}
        for(int i=0;i<r;i++){
            int inset=r-i;
            Gui.drawRect(x+inset,y+i,x+w-inset,y+i+1,colour);
            Gui.drawRect(x+inset,y+h-1-i,x+w-inset,y+h-i,colour);
        }
        Gui.drawRect(x,y+r,x+w,y+h-r,colour);
    }

    /** Capsule: mirrored round caps and a straight middle, for switches and status pills. */
    static void capsule(int x,int y,int w,int h,int colour){
        int r=h/2;
        if(r<1||w<2*r){Gui.drawRect(x,y,x+w,y+h,colour);return;}
        for(int i=0;i<r;i++){
            double dy=r-(i+0.5);
            int inset=r-(int)Math.round(Math.sqrt(Math.max(0,r*r-dy*dy)));
            Gui.drawRect(x+inset,y+i,x+w-inset,y+i+1,colour);
            Gui.drawRect(x+inset,y+h-1-i,x+w-inset,y+h-i,colour);
        }
        Gui.drawRect(x,y+r,x+w,y+h-r,colour);
    }

    /** 1px Lunar card outline. */
    static void outline(int x,int y,int w,int h,int colour){
        Gui.drawRect(x,y,x+w,y+1,colour);
        Gui.drawRect(x,y+h-1,x+w,y+h,colour);
        Gui.drawRect(x,y,x+1,y+h,colour);
        Gui.drawRect(x+w-1,y,x+w,y+h,colour);
    }
    static void panel(int x,int y,int w,int h){
        // Body first, border second: filling the whole rect opaque would hide the world behind it.
        Gui.drawRect(x+1,y+1,x+w-1,y+h-1,PANEL);
        outline(x,y,w,h,EDGE);
    }
    static void inset(int x,int y,int w,int h){
        Gui.drawRect(x,y,x+w,y+h,INSET);
    }
    static void header(FontRenderer font,String title,int x,int y,int w){
        Gui.drawRect(x+2,y+24,x+w-2,y+25,EDGE);
        Gui.drawRect(x+14,y+9,x+17,y+18,ACCENT);
        font.drawString(font.trimStringToWidth(title,Math.max(0,w-40)),x+23,y+10,TEXT);
    }
    static void headerCentered(FontRenderer font,String title,int x,int y,int w){
        Gui.drawRect(x+2,y+24,x+w-2,y+25,EDGE);
        plainCentered(font,title,x+w/2,y+9,TEXT);
    }
    static void caption(FontRenderer font,String label,int x,int y){
        font.drawStringWithShadow(label,x,y,CAPTION);
    }
    /** Caption plus a divider, so option groups read as sections rather than one long list. */
    static void section(FontRenderer font,String label,int x,int y,int w){
        caption(font,label,x,y);
        Gui.drawRect(x,y+10,x+w,y+11,EDGE);
    }
    static void centered(FontRenderer font,String text,int cx,int y,int color){
        font.drawStringWithShadow(text,cx-font.getStringWidth(text)/2,y,color);
    }
    static void plainCentered(FontRenderer font,String text,int cx,int y,int color){
        font.drawString(text,cx-font.getStringWidth(text)/2,y,color);
    }
    static void backArrow(FontRenderer font,int x,int y,boolean hover){
        plainCentered(font,"<",x+9,y+4,hover?0xFFFFFFFF:DIM);
    }
    /** Screen title at 2x: the only type size the game font offers above 8px. */
    static void title2x(FontRenderer font,String text,int x,int y,int colour){
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,0);
        GlStateManager.scale(2,2,1);
        font.drawString(text,0,0,colour);
        GlStateManager.popMatrix();
    }
    /** Gear: opens the shared settings page. */
    static void gear(int x,int y,boolean hover){
        int ink=hover?0xFFFFFFFF:DIM;
        // Hollow cog: an 8x8 ring, four teeth, nothing in the middle.
        Gui.drawRect(x+2,y+1,x+8,y+2,ink);
        Gui.drawRect(x+2,y+9,x+8,y+10,ink);
        Gui.drawRect(x+1,y+2,x+2,y+8,ink);
        Gui.drawRect(x+8,y+2,x+9,y+8,ink);
        Gui.drawRect(x+4,y,x+6,y+1,ink);
        Gui.drawRect(x+4,y+10,x+6,y+11,ink);
        Gui.drawRect(x,y+4,x+1,y+6,ink);
        Gui.drawRect(x+9,y+4,x+10,y+6,ink);
    }
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled){
        button(font,x,y,w,h,label,hover,enabled,255);
    }
    /** Alpha lets ghost chrome (e.g. the Mods button) keep the game visible behind it. */
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled,int alpha){
        int face=!enabled?BUTTON_DISABLED:hover?BUTTON_HOVER:BUTTON_FACE;
        int a=Math.max(0,Math.min(255,alpha))<<24;
        Gui.drawRect(x,y,x+w,y+h,a|(face&0xFFFFFF));
        String visible=font.trimStringToWidth(label,Math.max(0,w-8));
        int tx=x+(w-font.getStringWidth(visible))/2;
        font.drawString(visible,tx,y+(h-font.FONT_HEIGHT)/2,a|(enabled?BUTTON_TEXT&0xFFFFFF:DISABLED_TEXT&0xFFFFFF));
    }
    /** Lunar switch: green pill when on, red pill when off, white knob. */
    static void toggle(int x,int y,boolean on,boolean hover){
        rounded(x,y,26,12,3,on?BedrockTokens.STATE_ENABLED:BedrockTokens.STATE_DISABLED);
        if(hover)Gui.drawRect(x,y,x+26,y+12,0x22FFFFFF);
        rounded(on?x+15:x+2,y+2,9,8,2,0xFFFFFFFF);
    }
    /** Lunar module state pill: ENABLED green, DISABLED red, LOCKED gray, white caps text. */
    static void statePill(FontRenderer font,int x,int y,int w,int h,String label,int colour){
        rounded(x,y,w,h,2,colour);
        plainCentered(font,label,x+w/2,y+(h-font.FONT_HEIGHT)/2,0xFFFFFFFF);
    }
    /** Lunar filter chip: blue when active, outlined card when idle. */
    static void chip(FontRenderer font,int x,int y,int w,int h,String label,boolean active,boolean hover){
        if(active)rounded(x,y,w,h,2,BedrockTokens.STATE_SELECTED);
        else rounded(x,y,w,h,2,hover?BedrockTokens.STATE_HOVER:BedrockTokens.SURFACE_CARD);
        if(!active){
            Gui.drawRect(x,y,x+w,y+1,BedrockTokens.SURFACE_EDGE);
            Gui.drawRect(x,y+h-1,x+w,y+h,BedrockTokens.SURFACE_EDGE);
            Gui.drawRect(x,y,x+1,y+h,BedrockTokens.SURFACE_EDGE);
            Gui.drawRect(x+w-1,y,x+w,y+h,BedrockTokens.SURFACE_EDGE);
        }
        plainCentered(font,label,x+w/2,y+(h-font.FONT_HEIGHT)/2,active?0xFFFFFFFF:BedrockTokens.TEXT_MUTED);
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
