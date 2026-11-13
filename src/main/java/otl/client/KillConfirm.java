package otl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Session-only COD-style confirmation; original reward messages keep normal routing. */
public final class KillConfirm {
    /** Clear pending kill-confirm marks; part of the module's factory reset. */
    public static void clearState(){state.clear();}
    static final KillConfirm INSTANCE=new KillConfirm();
    static final int WIDTH=260,HEIGHT=58;
    /** Text origin and glyph metrics shared with the HUD anchor in HudLayout. */
    static final float EXP_X=4,EXP_Y=31,EXP_TEXT_SCALE=1.35F,PLUS_CENTER_X=2.5F,GLYPH_CENTER_Y=4;
    static final KillConfirmRules.State state=new KillConfirmRules.State();
    private boolean wasDead;private float lastHealth=Float.MAX_VALUE;
    private final KillConfirmRules.RespawnPosition respawnPosition=new KillConfirmRules.RespawnPosition();
    static boolean active(){UtilitySettings.load();return UtilitySettings.killConfirm&&ServerScope.active();}
    static HudLayout.Rect bounds(){float s=UtilitySettings.killConfirmSize/100F;return HudLayout.bounds(HudLayout.CONFIRM,(int)Math.ceil(WIDTH*s),(int)Math.ceil(HEIGHT*s));}
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void receive(ClientChatReceivedEvent event){
        Minecraft mc=MinecraftAccess.get();if(event.type!=2&&active()&&mc.thePlayer!=null&&mc.theWorld!=null){
            String text=event.message.getFormattedText();
            net.minecraft.util.AxisAlignedBB box=mc.thePlayer.getEntityBoundingBox();
            boolean airborne=!mc.thePlayer.onGround&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                new net.minecraft.util.AxisAlignedBB(box.minX,box.minY-2,box.minZ,box.maxX,box.minY,box.maxZ)).isEmpty();
            boolean waiting=state.awaitingRespawn;
            state.receive(text,mc.thePlayer.getName(),System.nanoTime(),mc.thePlayer.getHealth()+mc.thePlayer.getAbsorptionAmount(),airborne,!mc.thePlayer.onGround,mc.thePlayer.isSneaking());
            if(!waiting&&state.awaitingRespawn)respawnPosition.died(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
            if(!state.awaitingRespawn)respawnPosition.resumed();
            KillConfirmRules.Reward reward=KillConfirmRules.parse(text);
            if(reward!=null){
                if(UtilitySettings.killConfirmSounds)mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                    new net.minecraft.util.ResourceLocation(reward.assist?"random.pop":"random.orb"),reward.assist?1.1F:1F));
                if(UtilitySettings.hideKillConfirmChat)event.setCanceled(true);
            }
        }
    }
    @SubscribeEvent public void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(!active()||!(event.target instanceof net.minecraft.entity.player.EntityPlayer)||event.target==MinecraftAccess.get().thePlayer)return;
        net.minecraft.entity.player.EntityPlayer victim=(net.minecraft.entity.player.EntityPlayer)event.target;
        state.hit(victim.getName(),victim.getHealth(),System.nanoTime());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null){lastHealth=Float.MAX_VALUE;wasDead=false;respawnPosition.clear();return;}
        float health=mc.thePlayer.getHealth()+mc.thePlayer.getAbsorptionAmount();
        if(health<lastHealth)state.damaged();
        lastHealth=health;
        if(mc.theWorld!=null&&state.duelVictim!=null){
            for(net.minecraft.entity.player.EntityPlayer other:mc.theWorld.playerEntities)
                if(other.getName().equalsIgnoreCase(state.duelVictim)){state.confirmDuel(other.getHealth());break;}
        }
        boolean dead=mc.thePlayer.isDead||mc.thePlayer.getHealth()<=0;
        if(!active()){state.clear();wasDead=false;respawnPosition.clear();}
        else{
            if(dead&&!wasDead){state.died(state.lastAttacker);state.deathObserved(System.nanoTime());}
            if(state.awaitingRespawn)respawnPosition.died(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
            boolean teleported=respawnPosition.moved(mc.thePlayer.posX,mc.thePlayer.posY,mc.thePlayer.posZ);
            if(!dead&&!mc.thePlayer.isSpectator()&&(wasDead||teleported))state.respawn(System.nanoTime(),UtilitySettings.totalExpKills);
            if(!state.awaitingRespawn)respawnPosition.resumed();
            wasDead=dead;
        }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote){state.clear();wasDead=false;lastHealth=Float.MAX_VALUE;respawnPosition.clear();}}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        Minecraft mc=MinecraftAccess.get();
        if(event.type==RenderGameOverlayEvent.ElementType.CHAT&&active()&&mc.thePlayer!=null&&!(mc.currentScreen instanceof HudEditor)&&!mc.gameSettings.hideGUI)draw(false);
    }
    static void draw(boolean editing){
        Minecraft mc=MinecraftAccess.get();if(!editing&&state.reward==null&&state.recapAt==0)return;
        long now=System.nanoTime();
        boolean recap=!editing&&state.reward==null;
        KillConfirmRules.Reward reward=editing?new KillConfirmRules.Reward(25,false):state.reward;
        if(editing){reward.bow=true;reward.revenge=true;reward.survivor=true;}
        java.util.List<KillConfirmRules.Row> rows=editing?previewRows(now):state.rows();
        long age=editing?1150000000L:now-state.receivedAt;
        long fadeAge=editing?1150000000L:now-(rows.isEmpty()?now:rows.get(rows.size()-1).start);
        float alpha=editing?(UtilitySettings.killConfirm?1:.45F):KillConfirmRules.alpha(fadeAge);if(alpha<=0)return;
        HudLayout.Rect box=bounds();float scale=UtilitySettings.killConfirmSize/100F,pop=KillConfirmRules.pop(age);
        int white=((Math.max(4,(int)(255*alpha)))<<24)|0xFFFFFF,gold=(white&0xFF000000)|0xFFD36A;
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(box.x,box.y,0);GlStateManager.scale(scale,scale,1);
            int background=KillFeedRules.faded(ChatAppearance.tabColor(0xCC000000,UtilitySettings.killConfirmOpacity),alpha);
            if((background>>>24)>0)Gui.drawRect(0,0,WIDTH,HEIGHT,background);
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            String label=recap?"":"+"+(editing?75:state.streakExp)+" EXP";
            if(!recap){
                float textScale=EXP_TEXT_SCALE*pop;
                GlStateManager.pushMatrix();GlStateManager.translate(EXP_X+PLUS_CENTER_X*EXP_TEXT_SCALE,EXP_Y+GLYPH_CENTER_Y*EXP_TEXT_SCALE,0);GlStateManager.scale(textScale,textScale,1);
                mc.fontRendererObj.drawStringWithShadow(label,-PLUS_CENTER_X,-GLYPH_CENTER_Y,gold);GlStateManager.popMatrix();
            }
            int newest=KillConfirmRules.newestRow(rows,now);
            int right=recap?(int)EXP_X:12+(int)Math.ceil(mc.fontRendererObj.getStringWidth(label)*1.43F);
            for(int i=Math.max(0,newest-2);i<=newest;i++){
                long rowAge=now-rows.get(i).start;
                float visibility=KillConfirmRules.reveal(rowAge,0);if(visibility<=0)continue;
                float y=EXP_Y+KillConfirmRules.rowOffset(i,rows,now);
                int color=KillFeedRules.faded(white,visibility);
                if((color>>>24)<4)continue;
                int x=right-KillConfirmRules.slideOffset(KillConfirmRules.slideIn(rowAge,0));
                String text=rows.get(i).text;
                boolean kill=text.equals("KILL")||text.equals("BOW KILL");
                if(kill){
                    String victim=rows.get(i).victim;
                    if(!UtilitySettings.killConfirmPlayerHead)KillFeed.skull(x,(int)y-2,alpha*visibility);
                    else if(victim!=null)KillFeed.head(victim,x+2,(int)y-4,alpha*visibility);
                }
                mc.fontRendererObj.drawStringWithShadow(mc.fontRendererObj.trimStringToWidth(text,WIDTH-right-(recap?4:22)),x+(recap?0:18),y,color);
            }
        }finally{GlStateManager.popMatrix();if(!blend)GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);}
    }
    /** Editor preview uses the same rows, all already revealed. */
    private static java.util.List<KillConfirmRules.Row> previewRows(long now){
        KillConfirmRules.Reward reward=new KillConfirmRules.Reward(25,false);reward.bow=true;reward.revenge=true;reward.survivor=true;
        java.util.List<String> labels=reward.labels(3);
        java.util.List<KillConfirmRules.Row> rows=new java.util.ArrayList<>();
        for(int i=0;i<labels.size();i++)rows.add(new KillConfirmRules.Row(labels.get(i),now-1000000000L-(long)(labels.size()-1-i)*KillConfirmRules.ROW_NANOS,i==0?MinecraftAccess.get().thePlayer.getName():null));
        return rows;
    }
}
