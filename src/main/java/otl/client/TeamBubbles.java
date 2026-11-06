package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** Bubbles use only server-delivered team-chat lines, including the local player's echo. */
final class TeamBubbles {
    static final TeamBubbles INSTANCE=new TeamBubbles();
    private static Object world;
    private static String host="";
    private static final TeamBubbleRules.Cooldown cooldown=new TeamBubbleRules.Cooldown();
    static final Map<UUID,Bubble> bubbles=new HashMap<>();
    static final class Bubble {
        final String text;final long until;
        Bubble(String text){this.text=text;until=System.nanoTime()+UtilitySettings.bubbleSeconds*1000000000L;}
    }
    static void clearBubbles(){bubbles.clear();}
    private static void ensureWorld(){
        Minecraft mc=MinecraftAccess.get();String now=ServerScope.current();
        boolean active=ReliableChat.active()&&ReliableChat.fullChat();
        if(world!=mc.theWorld||!host.equals(now)){
            bubbles.clear();cooldown.clear();world=mc.theWorld;host=now;
        }
        if(!active)bubbles.clear();
    }
    static void receive(IChatComponent message){
        ensureWorld();
        if(!UtilitySettings.bubbles||!ReliableChat.active()||!ReliableChat.fullChat()||MinecraftAccess.get().theWorld==null)return;
        TeamBubbleRules.Chat chat=TeamBubbleRules.chat(message.getFormattedText());if(chat==null)return;
        EntityPlayer sender=null;
        for(EntityPlayer p:MinecraftAccess.get().theWorld.playerEntities)if(p.getName().equalsIgnoreCase(chat.name)){if(sender!=null)return;sender=p;}
        if(sender==null)return;
        String body=TeamBubbleRules.limit(chat.body,UtilitySettings.bubbleCharacters);
        if(!body.isEmpty()&&cooldown.accept(sender.getUniqueID(),System.nanoTime()))bubbles.put(sender.getUniqueID(),new Bubble(body));
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;ensureWorld();
        long now=System.nanoTime();cooldown.expire(now);
        if(!UtilitySettings.bubbles){bubbles.clear();return;}
        bubbles.values().removeIf(b->b.until<=now);
    }
    @SubscribeEvent public void render(RenderLivingEvent.Specials.Post<?> event){
        if(!UtilitySettings.bubbles||!ReliableChat.active()||FaceDoll.preview||!(event.entity instanceof EntityPlayer))return;
        Minecraft mc=MinecraftAccess.get();EntityPlayer player=(EntityPlayer)event.entity;
        Bubble bubble=bubbles.get(player.getUniqueID());
        if(bubble==null||bubble.until<=System.nanoTime()||!visible(player))return;
        FontRenderer font=mc.fontRendererObj;List<String> lines=new ArrayList<>(font.listFormattedStringToWidth(bubble.text,160));
        if(lines.size()>2){lines=new ArrayList<>(lines.subList(0,2));lines.set(1,font.trimStringToWidth(lines.get(1),font.getStringWidth(lines.get(1))-font.getStringWidth("\u2026"))+"\u2026");}
        if(lines.isEmpty())return;
        int width=0;for(String line:lines)width=Math.max(width,font.getStringWidth(line));
        boolean light=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int src=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        RenderManager manager=mc.getRenderManager();GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(event.x,event.y+player.height+1.05,event.z);GlStateManager.rotate(-manager.playerViewY,0,1,0);GlStateManager.rotate(manager.playerViewX,1,0,0);GlStateManager.scale(-.025F,-.025F,.025F);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            int top=-lines.size()*font.FONT_HEIGHT;
            Gui.drawRect(-width/2-5,top-4,width/2+5,4,0xD0202020);
            for(int i=0;i<lines.size();i++)font.drawString(lines.get(i),-font.getStringWidth(lines.get(i))/2,top+i*font.FONT_HEIGHT,0xFFF0F0F0);
        }finally{
            GlStateManager.popMatrix();GlStateManager.depthMask(mask);
            if(light)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.color(1,1,1,1);
        }
    }
    static boolean visible(EntityPlayer player){Minecraft mc=MinecraftAccess.get();return mc.thePlayer!=null&&!player.isInvisibleToPlayer(mc.thePlayer)&&!player.isSneaking()&&(player!=mc.thePlayer||mc.gameSettings.thirdPersonView!=0)&&mc.thePlayer.getDistanceSqToEntity(player)<=32*32&&mc.thePlayer.canEntityBeSeen(player);}
}
