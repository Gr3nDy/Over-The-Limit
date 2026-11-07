package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** Bubbles use only server-delivered team-chat lines, including the local player's echo. */
final class TeamBubbles {
    static final TeamBubbles INSTANCE=new TeamBubbles();
    private static Object world;
    private static String host="";
    static final Map<UUID,TeamBubbleRules.Stack> bubbles=new HashMap<>();
    private static long renderedBubbles;
    static void clearBubbles(){bubbles.clear();}
    private static void ensureWorld(){
        Minecraft mc=MinecraftAccess.get();String now=ServerScope.current();
        boolean active=ReliableChat.active()&&ReliableChat.fullChat();
        if(world!=mc.theWorld||!host.equals(now)){
            bubbles.clear();world=mc.theWorld;host=now;
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
        if(MinecraftAccess.get().thePlayer==null||MinecraftAccess.get().thePlayer.getDistanceSqToEntity(sender)>32*32)return;
        String body=TeamBubbleRules.limit(chat.body,UtilitySettings.bubbleCharacters);
        if(!body.isEmpty())bubbles.computeIfAbsent(sender.getUniqueID(),id->new TeamBubbleRules.Stack()).add(body,System.nanoTime(),UtilitySettings.bubbleSeconds*1000000000L);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;ensureWorld();
        long now=System.nanoTime();
        if(!UtilitySettings.bubbles){bubbles.clear();return;}
        bubbles.values().forEach(stack->stack.expire(now));bubbles.values().removeIf(stack->stack.items.isEmpty());
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        if(!UtilitySettings.bubbles||!ReliableChat.active()||FaceDoll.preview)return;
        Minecraft mc=MinecraftAccess.get();if(mc.theWorld==null||mc.getRenderViewEntity()==null)return;
        net.minecraft.entity.Entity camera=mc.getRenderViewEntity();float partial=event.partialTicks;
        double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        for(EntityPlayer player:mc.theWorld.playerEntities){
            if(!bubbles.containsKey(player.getUniqueID())||!visible(player))continue;
            draw(player,player.lastTickPosX+(player.posX-player.lastTickPosX)*partial-cx,
                player.lastTickPosY+(player.posY-player.lastTickPosY)*partial-cy,
                player.lastTickPosZ+(player.posZ-player.lastTickPosZ)*partial-cz);
        }
    }
    private static void draw(EntityPlayer player,double x,double y,double z){
        Minecraft mc=MinecraftAccess.get();
        TeamBubbleRules.Stack stack=bubbles.get(player.getUniqueID());if(stack==null||!visible(player))return;
        for(TeamBubbleRules.Message bubble:stack.items)if(bubble.until>System.nanoTime())drawBubble(player,bubble,x,y,z);
    }
    private static void drawBubble(EntityPlayer player,TeamBubbleRules.Message bubble,double x,double y,double z){
        Minecraft mc=MinecraftAccess.get();
        FontRenderer font=mc.fontRendererObj;List<String> lines=new ArrayList<>(font.listFormattedStringToWidth(bubble.text,160));
        if(lines.size()>2){lines=new ArrayList<>(lines.subList(0,2));lines.set(1,font.trimStringToWidth(lines.get(1),font.getStringWidth(lines.get(1))-font.getStringWidth("\u2026"))+"\u2026");}
        if(lines.isEmpty())return;
        int width=0;for(String line:lines)width=Math.max(width,font.getStringWidth(line));
        boolean light=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int src=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        RenderManager manager=mc.getRenderManager();GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(x,y+player.height+.65+bubble.slot(System.nanoTime())*.75,z);GlStateManager.rotate(-manager.playerViewY,0,1,0);GlStateManager.rotate(manager.playerViewX*(mc.gameSettings.thirdPersonView==2?-1:1),1,0,0);GlStateManager.scale(-.025F,-.025F,.025F);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            int top=-lines.size()*font.FONT_HEIGHT;
            Gui.drawRect(-width/2-5,top-4,width/2+5,4,0xD0202020);
            for(int i=0;i<lines.size();i++)font.drawString(lines.get(i),-font.getStringWidth(lines.get(i))/2,top+i*font.FONT_HEIGHT,0xFFF0F0F0);
            renderedBubbles++;
        }finally{
            GlStateManager.popMatrix();GlStateManager.depthMask(mask);
            if(light)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.color(1,1,1,1);
        }
    }
    static boolean visible(EntityPlayer player){Minecraft mc=MinecraftAccess.get();return mc.thePlayer!=null&&!player.isInvisibleToPlayer(mc.thePlayer)&&!player.isSneaking()&&(player==mc.thePlayer?mc.gameSettings.thirdPersonView!=0:mc.thePlayer.getDistanceSqToEntity(player)<=32*32&&mc.thePlayer.canEntityBeSeen(player));}
}
