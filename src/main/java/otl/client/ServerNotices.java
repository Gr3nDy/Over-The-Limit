package otl.client;

import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Independent, exclusive server-message feed. The full components stay in memory. */
final class ServerNotices {
    static final ServerNotices INSTANCE=new ServerNotices();
    static final List<Line> history=new ArrayList<>();
    private static final List<Line> examples=Arrays.asList(new Line(new net.minecraft.util.ChatComponentText("\u00a7eMatch starts in 10 seconds"),0),new Line(new net.minecraft.util.ChatComponentText("\u00a77Alex joined the game"),0),new Line(new net.minecraft.util.ChatComponentText("\u00a77Sam left the game"),0));
    static final class Line {
        final IChatComponent component;final int id;final long time=System.nanoTime();
        Line(IChatComponent component,int id){this.component=component;this.id=id;}
    }
    static boolean active(){UtilitySettings.load();return UtilitySettings.nonPlayerChat&&ServerScope.active();}
    static boolean route(IChatComponent component,int id){
        if(!active()||!ServerNoticeRules.accepts(component.getFormattedText(),UtilitySettings.noticeMode))return false;
        if(id!=0)history.removeIf(line->line.id==id);
        history.add(0,new Line(component,id));if(history.size()>100)history.remove(100);return true;
    }
    private static float scale(){return UtilitySettings.noticeSize/100F;}
    private static int logicalWidth(){return Math.max(8,Math.min(320,(int)((new ScaledResolution(MinecraftAccess.get()).getScaledWidth()-8)/scale())));}
    private static int logicalHeight(){return Math.max(9,Math.min(100,new ScaledResolution(MinecraftAccess.get()).getScaledHeight()/3));}
    static HudLayout.Rect bounds(){return HudLayout.bounds(HudLayout.NOTICES,(int)Math.ceil(logicalWidth()*scale()),(int)Math.ceil(logicalHeight()*scale()));}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)history.clear();}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(event.type!=RenderGameOverlayEvent.ElementType.CHAT||!active())return;
        Minecraft mc=MinecraftAccess.get();if(mc.currentScreen instanceof HudEditor||mc.gameSettings.hideGUI||mc.gameSettings.chatVisibility==net.minecraft.entity.player.EntityPlayer.EnumChatVisibility.HIDDEN)return;
        draw(history,false);
    }
    static void drawPreview(){draw(history.stream().anyMatch(line->ServerNoticeRules.accepts(line.component.getFormattedText(),UtilitySettings.noticeMode))?history:examples,true);}
    private static void draw(List<Line> lines,boolean editing){
        Minecraft mc=MinecraftAccess.get();
        HudLayout.Rect box=bounds();int y=0;long now=System.nanoTime();
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        GlStateManager.pushMatrix();
        try{GlStateManager.translate(box.x,box.y,0);GlStateManager.scale(scale(),scale(),1);for(Line line:lines){
            if(!ServerNoticeRules.accepts(line.component.getFormattedText(),UtilitySettings.noticeMode))continue;
            float fade=editing?(UtilitySettings.nonPlayerChat?1:.45F):Math.max(0,Math.min(1,(10000000000L-(now-line.time))/2000000000F));if(fade<=0)break;
            for(IChatComponent part:GuiUtilRenderComponents.splitText(line.component,Math.max(4,logicalWidth()-4),mc.fontRendererObj,false,false)){
                if(y+9>logicalHeight())return;
                int alpha=(int)(255*fade*(mc.gameSettings.chatOpacity*.9F+.1F));
                int background=ChatAppearance.backgroundAlpha(alpha,UtilitySettings.noticeOpacity);
                if(background>0)Gui.drawRect(0,y,logicalWidth(),y+9,background<<24);
                GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
                mc.fontRendererObj.drawStringWithShadow(part.getFormattedText(),2,y,0xFFFFFF|(Math.max(4,alpha)<<24));y+=9;
            }
        }}finally{GlStateManager.popMatrix();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);}
    }
}
