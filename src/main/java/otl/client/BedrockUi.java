package otl.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Shared dark menu chrome. */
final class BedrockUi {
    private static final ResourceLocation MENU_UI=new ResourceLocation("otl","textures/menu/ui.png");
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
        menuSkin(0,x,y,w,h);
    }
    static void inset(int x,int y,int w,int h){
        menuSkin(2,x,y,w,h);
    }
    static void header(FontRenderer font,String title,int x,int y,int w){
        menuSkin(6,x+3,y+3,w-6,22);
        OtlModule module=null;
        for(OtlModule m:OtlModule.ALL)if(m.name.equalsIgnoreCase(title)){module=m;break;}
        if(module==null&&title.equals("Isometric Minimap"))module=OtlModule.byId("minimap");
        if(module==null&&title.equals("Preferred Hand"))module=OtlModule.byId("left_hand");
        if(module!=null)menuText(module.id,module.name.length(),x+16+Math.min(w-40,module.name.length()*7)/2,y+14,w-40);
        else if(title.equalsIgnoreCase("Settings"))menuText("nav_settings_active",8,x+16+28,y+14,w-40);
        else font.drawString(font.trimStringToWidth(title,Math.max(0,w-40)),x+14,y+10,TEXT);
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
    /** Shared 256x160 pixel-art atlas; skins stretch only their middle pixels. */
    static void menuIcon(int id,int x,int y,boolean hover){
        Minecraft.getMinecraft().getTextureManager().bindTexture(MENU_UI);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(x,y,id*16,hover?112:96,16,16,256,160);
    }
    static void menuLogo(int x,int y){
        Minecraft.getMinecraft().getTextureManager().bindTexture(MENU_UI);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,96,40,256,160);
    }
    /** Baked 7px-cell bitmap lettering for fixed UI labels, centered without blurring. */
    static void menuText(String file,int characters,int cx,int cy,int maxWidth){
        int sourceW=characters*7;
        if(sourceW<=0)return;
        float scale=Math.min(1f,(float)maxWidth/sourceW);
        int w=Math.max(1,Math.round(sourceW*scale)),h=Math.max(1,Math.round(13*scale));
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("otl","textures/menu/labels/"+file+".png"));
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(cx-w/2,cy-h/2,0,0,sourceW,13,w,h,sourceW,13);
    }
    static void menuSkin(int id,int x,int y,int w,int h){
        if(w<8||h<8)return;
        int sx=id%8*24,sy=40+id/8*24;
        Minecraft.getMinecraft().getTextureManager().bindTexture(MENU_UI);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            int dx=x+(col==0?0:col==1?4:w-4),dy=y+(row==0?0:row==1?4:h-4);
            int dw=col==1?w-8:4,dh=row==1?h-8:4;
            Gui.drawScaledCustomSizeModalRect(dx,dy,sx+(col==0?0:col==1?4:20),
                sy+(row==0?0:row==1?4:20),col==1?16:4,row==1?16:4,dw,dh,256,160);
        }
    }
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled){
        button(font,x,y,w,h,label,hover,enabled,255);
    }
    /** Alpha lets ghost chrome (e.g. the Mods button) keep the game visible behind it. */
    static void button(FontRenderer font,int x,int y,int w,int h,String label,boolean hover,boolean enabled,int alpha){
        if(alpha==255&&w>=8&&h>=8){
            menuSkin(enabled?(hover?7:6):10,x,y,w,h);
            String visible=font.trimStringToWidth(label,Math.max(0,w-8));
            plainCentered(font,visible,x+w/2,y+(h-font.FONT_HEIGHT)/2,enabled?BUTTON_TEXT:DISABLED_TEXT);
            return;
        }
        int face=!enabled?BUTTON_DISABLED:hover?BUTTON_HOVER:BUTTON_FACE;
        int a=Math.max(0,Math.min(255,alpha))<<24;
        Gui.drawRect(x,y,x+w,y+h,a|(face&0xFFFFFF));
        String visible=font.trimStringToWidth(label,Math.max(0,w-8));
        int tx=x+(w-font.getStringWidth(visible))/2;
        font.drawString(visible,tx,y+(h-font.FONT_HEIGHT)/2,a|(enabled?BUTTON_TEXT&0xFFFFFF:DISABLED_TEXT&0xFFFFFF));
    }
    /** Lunar switch: green pill when on, red pill when off, white knob. */
    static void toggle(int x,int y,boolean on,boolean hover){
        Minecraft.getMinecraft().getTextureManager().bindTexture(MENU_UI);
        GlStateManager.color(1,1,1,1);
        GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(x,y,(hover?64:0)+(on?0:32),128,26,12,256,160);
    }
    /** Lunar module state pill: ENABLED green, DISABLED red, LOCKED gray, white caps text. */
    static void statePill(FontRenderer font,int x,int y,int w,int h,String label,int colour){
        rounded(x,y,w,h,2,colour);
        plainCentered(font,label,x+w/2,y+(h-font.FONT_HEIGHT)/2,0xFFFFFFFF);
    }
    /** Lunar filter chip: blue when active, outlined card when idle. */
    static void chip(FontRenderer font,int x,int y,int w,int h,String label,boolean active,boolean hover){
        menuSkin(active?12:hover?7:6,x,y,w,h);
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
    static boolean hit(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    static void item(Item item,int x,int y){
        if(item==null)return;
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item),x,y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
