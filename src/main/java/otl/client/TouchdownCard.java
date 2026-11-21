package otl.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/** A short trading-card celebration, using only confirmed PGM touchdown events. */
final class TouchdownCard {
    static final TouchdownCard INSTANCE=new TouchdownCard();
    static final int WIDTH=146,HEIGHT=164;
    private static final long DURATION=5_000_000_000L, CATCH_WINDOW=2_000_000_000L, THROW_WINDOW=10_000_000_000L;
    private static final Pattern EVENT=Pattern.compile("(?i)([A-Za-z0-9_]{1,16}) (threw the Football!|caught the Football!|intercepted the Football!|takes it to the end zone!)");
    private static final Pattern SCORE=Pattern.compile("^(\\d{1,3})\\s*-\\s*(\\d{1,3})$");
    private static final ModelPlayer NORMAL_MODEL=new ModelPlayer(0F,false),SLIM_MODEL=new ModelPlayer(0F,true);
    private static final ModelBiped ARMOR_MODEL=new ModelBiped(1F);
    private final Deque<Card> queue=new ArrayDeque<>();
    private Card current;
    private Card recentCard,scoreTarget;
    private String pendingScore;
    private String lastConfirmedScore;
    private long matchEndedAt;
    private long recentAt,scoreAt,scoreHeaderAt;
    private long started,thrownAt,caughtAt;
    private String thrower,receiver;
    private double[] throwPos,catchPos;
    private ItemStack[] throwArmor;
    private final java.util.Map<String,ArmorSnapshot> armorCache=new java.util.HashMap<>();
    private long armorScanAt;
    private int lastPose=-1;
    private TouchdownCard(){}

    static final class ArmorSnapshot {
        final ItemStack[] items;final long at;
        ArmorSnapshot(ItemStack[] items,long at){this.items=items;this.at=at;}
    }
    static ItemStack[] recentArmor(ArmorSnapshot snapshot,long now){
        return snapshot!=null&&now>=snapshot.at&&now-snapshot.at<=THROW_WINDOW?snapshot.items:null;
    }

    static final class Card {
        final String scorer,passer,team;
        String score;
        final int distance,pose,colour;
        final ItemStack[] scorerArmor,passerArmor;
        Card(String scorer,String passer,String team,int distance,int pose,int colour){
            this(scorer,passer,team,distance,pose,colour,null,null);
        }
        Card(String scorer,String passer,String team,int distance,int pose,int colour,ItemStack[] scorerArmor,ItemStack[] passerArmor){
            this.scorer=scorer;this.passer=passer;this.team=team;this.distance=distance;this.pose=pose;this.colour=colour;
            this.scorerArmor=scorerArmor;this.passerArmor=passerArmor;
        }
    }

    static HudLayout.Rect bounds(){return HudLayout.bounds(HudLayout.CARD,WIDTH,HEIGHT);}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public void chat(ClientChatReceivedEvent event){
        UtilitySettings.load();
        if(event.type==2||!UtilitySettings.touchdown||!UtilitySettings.touchdownCard)return;
        String formatted=event.message.getFormattedText();
        if(ChatRules.playerMessage(formatted))return;
        for(String line:ChatRules.plain(event.message.getUnformattedText()).split("[\\r\\n]+")){
            String text=line.trim();
            if(text.startsWith("Playing ")||MatchEnd.kickoff(text)){reset();continue;}
            if(!Touchdown.active())continue;
            long now=System.nanoTime();
            if(MatchEnd.is(text)){if(matchEndedAt==0)matchEndedAt=now;continue;}
            if(text.contains("TOUCHDOWN!")){
                pendingScore=null;scoreHeaderAt=now;
                scoreTarget=recentCard!=null&&recentCard.score==null&&now-recentAt<3_000_000_000L?recentCard:null;
                continue;
            }
            Matcher score=SCORE.matcher(text);
            if(scoreHeaderAt!=0&&now-scoreHeaderAt<3_000_000_000L&&score.matches()){
                String value=score.group(1)+" - "+score.group(2);
                lastConfirmedScore=value;
                if(scoreTarget==null&&recentCard!=null&&recentAt>=scoreHeaderAt&&recentCard.score==null)scoreTarget=recentCard;
                if(scoreTarget!=null)scoreTarget.score=value;else{pendingScore=value;scoreAt=now;}
                scoreHeaderAt=0;scoreTarget=null;continue;
            }
            if(text.contains("The Flag was Reset!")||text.contains("The Flag carrier died!")||text.contains(" stripped the Flag!")||text.contains(" picked up the Flag!")){clearPass();continue;}
            Matcher m=EVENT.matcher(text);
            if(!m.find())continue;
            String name=m.group(1),action=m.group(2).toLowerCase(Locale.ROOT);
            Minecraft mc=MinecraftAccess.get();
            if(action.startsWith("threw")){
                thrower=name;thrownAt=now;throwPos=position(mc,name);throwArmor=knownArmor(mc,name,now);receiver=null;catchPos=null;
            }else if(action.startsWith("caught")){
                receiver=name;caughtAt=now;catchPos=position(mc,name);
            }else if(action.startsWith("intercepted")){
                clearPass();
            }else{
                boolean paired=validPass(thrower,receiver,name,thrownAt,caughtAt,now)&&sameTeam(mc,thrower,name);
                int distance=paired&&throwPos!=null&&catchPos!=null?(int)Math.round(Math.sqrt(Math.pow(throwPos[0]-catchPos[0],2)+Math.pow(throwPos[1]-catchPos[1],2)+Math.pow(throwPos[2]-catchPos[2],2))):-1;
                ScorePlayerTeam team=mc.theWorld==null?null:mc.theWorld.getScoreboard().getPlayersTeam(name);
                String label=team==null?null:ChatRules.plain(team.getTeamName());
                int colour=team==null?0xFFC5A352:TeamBar.teamColour(mc,java.util.Collections.singletonList(name));
                int pose=(lastPose+1+(int)(Math.random()*4))%5;lastPose=pose;
                // shortcut: keep at most eight pending cards; increase if real matches score faster than playback.
                if(queue.size()<8){
                    Card card=new Card(name,paired?thrower:null,label,distance,pose,colour,knownArmor(mc,name,now),paired?(throwArmor!=null?throwArmor:knownArmor(mc,thrower,now)):null);
                    if(pendingScore!=null&&now-scoreAt<3_000_000_000L){card.score=pendingScore;pendingScore=null;}
                    queue.addLast(card);recentCard=card;recentAt=now;
                }
                clearPass();
            }
        }
    }

    static boolean validPass(String passer,String catcher,String scorer,long thrown,long caught,long now){
        return passer!=null&&catcher!=null&&catcher.equalsIgnoreCase(scorer)&&!passer.equalsIgnoreCase(scorer)
            &&caught>=thrown&&caught-thrown<=THROW_WINDOW&&now>=caught&&now-caught<=CATCH_WINDOW;
    }

    /** PGM's symbolic sidebar: {team colour, scored points, total displayed slots}. */
    static int[] scoreRow(String formatted){
        char color='r',team='r',symbol=0;boolean arrow=false;int points=0,total=0;
        for(int i=0;i<formatted.length();i++){
            char c=formatted.charAt(i);
            if(c=='\u00a7'&&i+1<formatted.length()){
                char code=Character.toLowerCase(formatted.charAt(++i));
                if("0123456789abcdefr".indexOf(code)>=0)color=code;
            }else if(!arrow){
                if(c=='\u2794'){arrow=true;team=color;}
                else if(!Character.isWhitespace(c))return null;
            }else if(!Character.isWhitespace(c)){
                if("\u2b24\u2b1b\u2764|".indexOf(c)<0||(symbol!=0&&symbol!=c))return null;
                symbol=c;total++;
                if(color==team)points++;
                else if(color!='7')return null;
            }
        }
        return arrow&&team!='7'&&team!='8'&&team!='r'?new int[]{team,points,total}:null;
    }

    /** Returns the viewer's team first, just like the PGM score announcement. */
    static String sidebarScore(Minecraft mc){
        if(mc.theWorld==null||mc.thePlayer==null)return null;
        Scoreboard board=mc.theWorld.getScoreboard();ScoreObjective sidebar=board.getObjectiveInDisplaySlot(1);
        ScorePlayerTeam own=board.getPlayersTeam(mc.thePlayer.getName());
        if(sidebar==null||own==null)return null;
        String prefix=own.getColorPrefix();char ownColor='r';
        for(int i=0;prefix!=null&&i+1<prefix.length();i++)if(prefix.charAt(i)=='\u00a7'){
            char c=Character.toLowerCase(prefix.charAt(++i));if("0123456789abcdefr".indexOf(c)>=0)ownColor=c;
        }
        int[] first=null,second=null;
        for(Score score:board.getSortedScores(sidebar)){
            ScorePlayerTeam row=board.getPlayersTeam(score.getPlayerName());if(row==null)continue;
            int[] parsed=scoreRow(row.getColorPrefix()+row.getColorSuffix());if(parsed==null)continue;
            if(first==null)first=parsed;else if(second==null)second=parsed;else return null;
        }
        if(first==null||second==null||first[0]==second[0])return null;
        if(first[0]==ownColor)return first[1]+" - "+second[1];
        if(second[0]==ownColor)return second[1]+" - "+first[1];
        return null;
    }

    private static double[] position(Minecraft mc,String name){
        EntityPlayer player=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(name);
        return player==null?null:new double[]{player.posX,player.posY,player.posZ};
    }
    static ItemStack[] armor(Minecraft mc,String name){
        EntityPlayer player=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(name);
        if(player==null)return null;
        return armor(player);
    }
    private static ItemStack[] armor(EntityPlayer player){
        ItemStack[] items=new ItemStack[2];
        for(int i=0;i<items.length;i++){
            ItemStack item=player.getCurrentArmor(i+2);
            if(item!=null)items[i]=item.copy();
        }
        return items;
    }
    private ItemStack[] knownArmor(Minecraft mc,String name,long now){
        ItemStack[] live=armor(mc,name);
        return live!=null?live:recentArmor(armorCache.get(name.toLowerCase(Locale.ROOT)),now);
    }
    private static boolean sameTeam(Minecraft mc,String first,String second){
        if(mc.theWorld==null)return false;
        ScorePlayerTeam a=mc.theWorld.getScoreboard().getPlayersTeam(first),b=mc.theWorld.getScoreboard().getPlayersTeam(second);
        return a!=null&&a==b;
    }
    private void clearPass(){thrower=receiver=null;throwPos=catchPos=null;throwArmor=null;thrownAt=caughtAt=0;}
    private void reset(){queue.clear();armorCache.clear();armorScanAt=0;current=null;recentCard=scoreTarget=null;pendingScore=lastConfirmedScore=null;scoreHeaderAt=scoreAt=recentAt=matchEndedAt=0;clearPass();}
    static Card current(){return INSTANCE.current;}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote)reset();}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(!UtilitySettings.touchdown||!UtilitySettings.touchdownCard){reset();return;}
        long now=System.nanoTime();
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld!=null&&Touchdown.active()&&now-armorScanAt>=250_000_000L){
            armorScanAt=now;
            armorCache.entrySet().removeIf(entry->recentArmor(entry.getValue(),now)==null);
            for(EntityPlayer player:mc.theWorld.playerEntities)
                armorCache.put(player.getName().toLowerCase(Locale.ROOT),new ArmorSnapshot(armor(player),now));
        }
        if(matchEndedAt!=0&&now-matchEndedAt>=500_000_000L&&recentCard!=null&&recentCard.score==null){
            String score=sidebarScore(MinecraftAccess.get());
            if(score!=null&&!score.equals(lastConfirmedScore))recentCard.score=score;
        }
        if(current!=null&&now-started>=DURATION)current=null;
        if(current==null&&!queue.isEmpty()){current=queue.removeFirst();started=now;}
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(!HudStage.due(event))return;
        Minecraft mc=MinecraftAccess.get();
        if(current==null||!UtilitySettings.touchdown||!UtilitySettings.touchdownCard||!Touchdown.active()||mc.thePlayer==null||mc.theWorld==null||mc.gameSettings.hideGUI||mc.currentScreen instanceof HudEditor)return;
        draw(current,(System.nanoTime()-started)/1_000_000_000F,false);
    }
    static void drawPreview(){
        Minecraft mc=MinecraftAccess.get();String name=mc.thePlayer==null?"Player343":mc.thePlayer.getName();
        Card card=new Card(name,"Teammate","BLUE TEAM",32,0,0xFF5599FF,armor(mc,name),null);
        card.score="2 - 1";INSTANCE.draw(card,2F,true);
    }

    private void draw(Card card,float elapsed,boolean preview){
        Minecraft mc=MinecraftAccess.get();HudLayout.Rect b=bounds();int x=b.x,y=b.y;
        float enter=preview?1F:ease(elapsed/.28F);
        float exit=preview?1F:ease((5F-elapsed)/.24F);
        int style=UtilitySettings.touchdownCardAnimation;
        float dx=offset(style,enter,WIDTH,true)+offset(style,exit,WIDTH,true);
        float dy=offset(style,enter,HEIGHT,false)+offset(style,exit,HEIGHT,false);
        float alpha=style==0?enter*exit:1F;
        if(alpha<=.02F)return;
        int a=(int)(alpha*255),accent=(a<<24)|(card.colour&0xFFFFFF);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.pushMatrix();GlStateManager.translate(dx,dy,0);
        GlStateManager.disableDepth();
        try{
        Gui.drawRect(x,y,x+WIDTH,y+HEIGHT,(a<<24)|0xE6E8E8);
        Gui.drawRect(x+2,y+2,x+WIDTH-2,y+HEIGHT-2,(a<<24)|0x18212A);
        Gui.drawRect(x+4,y+4,x+WIDTH-4,y+21,accent);
        centered(mc,card.passer==null?"TOUCHDOWN":"PASS TOUCHDOWN",x+WIDTH/2,y+8,(a<<24)|0xFFFFFF);
        Gui.drawRect(x+5,y+23,x+WIDTH-5,y+108,(a<<24)|0x25323B);
        if(card.passer==null)player(mc,card.scorer,x+WIDTH/2,y+145,59,card.pose,false,alpha,card.scorerArmor);
        else{
            player(mc,card.passer,x+44,y+142,55,card.pose,true,alpha,card.passerArmor);
            player(mc,card.scorer,x+103,y+145,59,card.pose,false,alpha,card.scorerArmor);
        }
        Gui.drawRect(x+5,y+108,x+WIDTH-5,y+110,accent);
        centered(mc,mc.fontRendererObj.trimStringToWidth(card.scorer,WIDTH-12),x+WIDTH/2,y+112,(a<<24)|0xFFFFFF);
        if(card.passer!=null)centered(mc,mc.fontRendererObj.trimStringToWidth("PASSER: "+card.passer,WIDTH-12),x+WIDTH/2,y+124,(a<<24)|0xC8D2DC);
        String detail=card.team==null?"":card.team;
        if(card.distance>=0)detail+=(detail.isEmpty()?"":"  ")+"~"+card.distance+" BLOCKS";
        centered(mc,mc.fontRendererObj.trimStringToWidth(detail,WIDTH-12),x+WIDTH/2,y+(card.passer==null?130:136),(a<<24)|0xD8BD78);
        centered(mc,scoreLabel(card.score),x+WIDTH/2,y+146,(a<<24)|0xFFFFFF);
        }finally{if(depth)GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();}
    }
    static String scoreLabel(String score){return score==null?"Score unavailable":"SCORE: "+score;}
    static float ease(float value){float t=Math.max(0F,Math.min(1F,value));return t*t*(3F-2F*t);}
    static float offset(int style,float progress,int distance,boolean horizontal){
        if(horizontal&&(style==1||style==2))return (style==1?-distance:distance)*(1F-progress);
        if(!horizontal&&(style==3||style==4))return (style==3?-distance:distance)*(1F-progress);
        return 0F;
    }
    private static void centered(Minecraft mc,String text,int x,int y,int colour){mc.fontRendererObj.drawStringWithShadow(text,x-mc.fontRendererObj.getStringWidth(text)/2,y,colour);}

    /** Render a static upper-body pose without modifying the player's real-world entity. */
    static void player(Minecraft mc,String name,int x,int y,int size,int pose,boolean passer,float alpha){
        player(mc,name,x,y,size,pose,passer,alpha,armor(mc,name));
    }
    private static void player(Minecraft mc,String name,int x,int y,int size,int pose,boolean passer,float alpha,ItemStack[] armor){
        NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(name);
        ResourceLocation skin=info==null?DefaultPlayerSkin.getDefaultSkinLegacy():info.getLocationSkin();
        ModelPlayer model=info!=null&&"slim".equals(info.getSkinType())?SLIM_MODEL:NORMAL_MODEL;
        model.bipedRightArm.rotateAngleX=model.bipedLeftArm.rotateAngleX=0F;
        model.bipedRightLeg.rotateAngleX=model.bipedLeftLeg.rotateAngleX=0F;
        model.bipedHead.rotateAngleY=passer?-.22F:.15F;
        // Scorer: Sprint, Victory Jump, Spike, Point, Champion. Passer: Pocket, Quick,
        // Follow-Through, Point & Celebrate, Handoff; matching indices form a duo.
        if(passer){
            model.bipedRightArm.rotateAngleX=pose==0?-1.9F:pose==1?-1.3F:pose==2?-2.2F:pose==3?-1.5F:-1.1F;
            model.bipedLeftArm.rotateAngleX=pose==4?-1.1F:-.25F;
        }else{
            model.bipedRightArm.rotateAngleX=pose==0?-.7F:pose==1?-2.7F:pose==2?-2F:pose==3?-1.4F:-1.8F;
            model.bipedLeftArm.rotateAngleX=pose==0?.5F:pose==1?-2.7F:pose==4?-.8F:0;
        }
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),alphaTest=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunc=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float alphaRef=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.enableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.depthMask(true);
            // Transparent skin-layer texels must not write depth over the underlying skin.
            GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
            GlStateManager.translate(x,y,100);GlStateManager.scale(-size,-size,-size);GlStateManager.rotate(180,0,0,1);
            GlStateManager.translate(0,-1.5,0);GlStateManager.disableLighting();
            mc.getTextureManager().bindTexture(skin);GlStateManager.color(1,1,1,alpha);
            model.bipedHead.render(.0625F);model.bipedHeadwear.rotateAngleY=model.bipedHead.rotateAngleY;model.bipedHeadwear.render(.0625F);
            model.bipedBody.render(.0625F);model.bipedRightArm.render(.0625F);model.bipedLeftArm.render(.0625F);
            net.minecraft.client.model.ModelBase.copyModelAngles(model.bipedBody,model.bipedBodyWear);
            net.minecraft.client.model.ModelBase.copyModelAngles(model.bipedRightArm,model.bipedRightArmwear);
            net.minecraft.client.model.ModelBase.copyModelAngles(model.bipedLeftArm,model.bipedLeftArmwear);
            net.minecraft.client.model.ModelBase.copyModelAngles(model.bipedRightLeg,model.bipedRightLegwear);
            net.minecraft.client.model.ModelBase.copyModelAngles(model.bipedLeftLeg,model.bipedLeftLegwear);
            model.bipedBodyWear.render(.0625F);model.bipedRightArmwear.render(.0625F);model.bipedLeftArmwear.render(.0625F);
            drawArmor(mc,model,armor,alpha);
        }finally{
            if(lighting)GlStateManager.enableLighting();
            GlStateManager.depthFunc(depthFunc);GlStateManager.depthMask(depthWrite);
            if(!depth)GlStateManager.disableDepth();if(!blend)GlStateManager.disableBlend();
            GlStateManager.alphaFunc(alphaFunc,alphaRef);if(!alphaTest)GlStateManager.disableAlpha();
            GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
        }
    }
    private static void drawArmor(Minecraft mc,ModelPlayer pose,ItemStack[] armor,float alpha){
        if(armor==null)return;
        net.minecraft.client.model.ModelBase.copyModelAngles(pose.bipedHead,ARMOR_MODEL.bipedHead);
        net.minecraft.client.model.ModelBase.copyModelAngles(pose.bipedBody,ARMOR_MODEL.bipedBody);
        net.minecraft.client.model.ModelBase.copyModelAngles(pose.bipedRightArm,ARMOR_MODEL.bipedRightArm);
        net.minecraft.client.model.ModelBase.copyModelAngles(pose.bipedLeftArm,ARMOR_MODEL.bipedLeftArm);
        for(int i=0;i<armor.length;i++){
            ItemStack stack=armor[i];if(stack==null||!(stack.getItem() instanceof ItemArmor))continue;
            ItemArmor item=(ItemArmor)stack.getItem();String material=item.getArmorMaterial().getName(),domain="minecraft";
            int colon=material.indexOf(':');if(colon>=0){domain=material.substring(0,colon);material=material.substring(colon+1);}
            String path=domain+":textures/models/armor/"+material+"_layer_1";
            int tint=item.getColor(stack);
            mc.getTextureManager().bindTexture(new ResourceLocation(path+".png"));
            GlStateManager.color(tint==-1?1F:(tint>>16&255)/255F,tint==-1?1F:(tint>>8&255)/255F,tint==-1?1F:(tint&255)/255F,alpha);
            armorParts(i);
            if(tint!=-1){mc.getTextureManager().bindTexture(new ResourceLocation(path+"_overlay.png"));GlStateManager.color(1,1,1,alpha);armorParts(i);}
        }
    }
    private static void armorParts(int slot){
        if(slot==1){ARMOR_MODEL.bipedHead.render(.0625F);ARMOR_MODEL.bipedHeadwear.rotateAngleY=ARMOR_MODEL.bipedHead.rotateAngleY;ARMOR_MODEL.bipedHeadwear.render(.0625F);}
        else{ARMOR_MODEL.bipedBody.render(.0625F);ARMOR_MODEL.bipedRightArm.render(.0625F);ARMOR_MODEL.bipedLeftArm.render(.0625F);}
    }
}
