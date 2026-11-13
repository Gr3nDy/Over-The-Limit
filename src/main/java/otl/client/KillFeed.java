package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Bounded, session-only PGM feed. Chat retains unrecognized messages. */
final class KillFeed {
    static final KillFeed INSTANCE=new KillFeed();
    static final KillFeedRules.History history=new KillFeedRules.History();
    private static final Map<String,ItemStack> weapons=new HashMap<>();
    private static final Map<String,int[]> heads=new HashMap<>();
    /** 16px keeps every face texel on exactly 2px; 12px forced an uneven 1px/2px mix. */
    private static final int HEAD=16,HEAD_ADVANCE=18;
    private static int scroll;
    private static boolean dragging;
    private static final Tooltip tooltip=new Tooltip();
    private static final Gui sprites=new Gui();
    private static List<KillFeedRules.Death> preview;
    private static String previewName="";
    static boolean active(){UtilitySettings.load();return UtilitySettings.killFeed&&ServerScope.active();}
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public void receive(ClientChatReceivedEvent event){
        if(!active()||MinecraftAccess.get().thePlayer==null)return;
        String message=event.message.getFormattedText();
        if(event.type==2){
            KillConfirmRules.Reward reward=KillConfirmRules.parse(message);
            if(reward!=null&&reward.assist)history.assist(MinecraftAccess.get().thePlayer.getName(),System.nanoTime(),teammatePredicate());
            return;
        }
        KillFeedRules.Death death=KillFeedRules.parse(message);
        // shortcut: no victim in assist rewards; skip reward-first or crowded death bursts rather than crediting the wrong player.
        if(death==null){KillConfirmRules.Reward reward=KillConfirmRules.parse(message);if(reward!=null&&reward.assist)history.assist(MinecraftAccess.get().thePlayer.getName(),System.nanoTime(),teammatePredicate());return;}
        history.add(death);if(scroll>0&&visible(death))scroll=Math.min(scroll+1,Math.max(0,visibleEntries().size()-rows()));
        if(UtilitySettings.hideKillChat)event.setCanceled(true);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world.isRemote){history.clear();heads.clear();rowCache.clear();scroll=0;dragging=false;}}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event){
        if(event.type==RenderGameOverlayEvent.ElementType.CHAT&&!(MinecraftAccess.get().currentScreen instanceof GuiChat)&&!(MinecraftAccess.get().currentScreen instanceof HudEditor))draw(-1,-1,false);
    }
    @SubscribeEvent public void screen(GuiScreenEvent.DrawScreenEvent.Post event){
        if(event.gui instanceof GuiChat)draw(event.mouseX,event.mouseY,true);
    }
    private static float scale(){return Math.max(.5F,UtilitySettings.killFeedSize/100F);}
    static int rows(){return Math.max(1,Math.min(UtilitySettings.killFeedRows,(int)((new ScaledResolution(MinecraftAccess.get()).getScaledHeight()-55)/(22*scale()))));}
    static HudLayout.Rect bounds(){return HudLayout.bounds(HudLayout.KILLS,(int)Math.ceil(width()*scale()),(int)Math.ceil(rows()*22*scale()));}
    private static float left(){return bounds().x;}
    private static float top(){return bounds().y;}
    private static int widthLimit(){return Math.min(420,(int)((new ScaledResolution(MinecraftAccess.get()).getScaledWidth()-12)/scale()));}
    private static int width(){
        Minecraft mc=MinecraftAccess.get();if(mc.thePlayer==null)return Math.min(180,widthLimit());
        boolean editing=mc.currentScreen instanceof HudEditor;
        List<KillFeedRules.Death> entries=editing||history.entries.isEmpty()?previewEntries():visibleEntries();
        int result=60,start=!editing&&mc.currentScreen instanceof GuiChat?scroll:0;
        for(int i=start;i<entries.size()&&i<start+rows();i++)result=Math.max(result,row(entries.get(i)).w+9);
        return Math.min(widthLimit(),result);
    }
    private static boolean visible(KillFeedRules.Death death){
        Minecraft mc=MinecraftAccess.get();String local=mc.thePlayer.getName();
        net.minecraft.scoreboard.ScorePlayerTeam team=mc.theWorld==null?null:mc.theWorld.getScoreboard().getPlayersTeam(local);
        return UtilitySettings.killFeedFilter.shows(death,local,name->team!=null&&team==mc.theWorld.getScoreboard().getPlayersTeam(name));
    }
    private static java.util.function.Predicate<String> teammatePredicate(){
        Minecraft mc=MinecraftAccess.get();
        net.minecraft.scoreboard.ScorePlayerTeam team=mc.theWorld==null||mc.thePlayer==null?null:mc.theWorld.getScoreboard().getPlayersTeam(mc.thePlayer.getName());
        // No team context means there is no teammate to filter; with one, assists only claim teammate deaths.
        return name->team==null||mc.theWorld==null||team==mc.theWorld.getScoreboard().getPlayersTeam(name);
    }
    private static List<KillFeedRules.Death> visibleEntries(){
        if(UtilitySettings.killFeedFilter==KillFeedRules.FilterMode.ALL)return history.entries;
        List<KillFeedRules.Death> result=new ArrayList<>();
        for(KillFeedRules.Death death:history.entries)if(visible(death))result.add(death);
        return result;
    }
    private static final Map<KillFeedRules.Death,Row> rowCache=new IdentityHashMap<>();
    private static Row row(KillFeedRules.Death death){
        Row cached=rowCache.get(death);
        if(cached!=null&&cached.valid(death))return cached;
        if(rowCache.size()>1200)rowCache.clear();
        Row made=new Row(death);rowCache.put(death,made);return made;
    }
    private static final class Row {
        final String actor,victim,detail,assist;final int actorWidth,extra,w;final boolean actorHead,victimHead,assistHead;
        final boolean assistFlag;final KillFeedRules.HeadMode heads;final String local;final int limit;final char color;
        Row(KillFeedRules.Death death){
            Minecraft mc=MinecraftAccess.get();boolean suicide=death.suicide();detail=death.distanceLabel();extra=death.extraContext()?16:0;
            assistFlag=death.assist;heads=UtilitySettings.killFeedHeads;local=mc.thePlayer.getName();limit=widthLimit();color=TeamBubbles.teamColor();
            actorHead=!suicide&&heads.shows(death.killer,local);
            victimHead=heads.shows(death.victim,local);
            assistHead=death.assist&&heads.shows(local,local);
            assist=death.assist?KillFeedRules.assistLabel(local,color):"";
            int heads=(actorHead?HEAD_ADVANCE:0)+(victimHead?HEAD_ADVANCE:0)+(assistHead?HEAD_ADVANCE:0),assistWidth=assist.isEmpty()?0:mc.fontRendererObj.getStringWidth(" + ")+mc.fontRendererObj.getStringWidth(assist);
            int budget=Math.max(10,limit-9-34-extra-heads-assistWidth-mc.fontRendererObj.getStringWidth(detail));
            actor=suicide?"":mc.fontRendererObj.trimStringToWidth(KillFeedRules.feedLabel(death.killerLabel,death,local),budget/2);
            victim=mc.fontRendererObj.trimStringToWidth(KillFeedRules.feedLabel(death.victimLabel,death,local),suicide?budget:budget/2);
            actorWidth=mc.fontRendererObj.getStringWidth(actor)+assistWidth+(assistHead?HEAD_ADVANCE:0);
            w=10+heads-(assistHead?HEAD_ADVANCE:0)+actorWidth+(suicide?0:4)+16+4+extra+mc.fontRendererObj.getStringWidth(victim)+mc.fontRendererObj.getStringWidth(detail);
        }
        boolean valid(KillFeedRules.Death death){
            return assistFlag==death.assist&&heads==UtilitySettings.killFeedHeads&&limit==widthLimit()&&color==TeamBubbles.teamColor()&&local.equals(MinecraftAccess.get().thePlayer.getName());
        }
    }
    private static boolean inside(int x,int y){return bounds().contains(x,y);}
    static void scrollBy(int delta){scroll=Math.max(0,Math.min(Math.max(0,visibleEntries().size()-rows()),scroll+delta));}
    @SubscribeEvent public void mouse(GuiScreenEvent.MouseInputEvent.Pre event){
        if(!(event.gui instanceof GuiChat)||!active()||history.entries.isEmpty())return;
        Minecraft mc=MinecraftAccess.get();ScaledResolution sr=new ScaledResolution(mc);
        int x=Mouse.getEventX()*sr.getScaledWidth()/mc.displayWidth;
        int y=sr.getScaledHeight()-Mouse.getEventY()*sr.getScaledHeight()/mc.displayHeight-1;
        if(!inside(x,y)&&!dragging)return;
        int wheel=Mouse.getEventDWheel();
        if(wheel!=0){scrollBy(wheel>0?3:-3);event.setCanceled(true);return;}
        if(Mouse.getEventButton()==0){
            if(!Mouse.getEventButtonState()){if(dragging)event.setCanceled(true);dragging=false;}
            else if(x>=left()+(width()-7)*scale()){dragging=true;drag(y);event.setCanceled(true);}
        }else if(dragging){event.setCanceled(true);}
    }
    private static void drag(int y){
        int max=Math.max(0,visibleEntries().size()-rows());
        scroll=(int)(max*Math.max(0,Math.min(1,((y-top())/scale()-4)/(rows()*22-8))));
    }
    private static void draw(int mx,int my,boolean open){
        draw(mx,my,open,visibleEntries(),false);
    }
    static void drawPreview(){
        Minecraft mc=MinecraftAccess.get();if(mc.thePlayer==null)return;
        draw(-1,-1,true,previewEntries(),true);
    }
    private static List<KillFeedRules.Death> previewEntries(){
        Minecraft mc=MinecraftAccess.get();
        if(preview==null||!previewName.equals(mc.thePlayer.getName())){
            previewName=mc.thePlayer.getName();String own="\u00a7c"+previewName+"\u00a7r";
            preview=Arrays.asList(KillFeedRules.parse("\u00a79Victim\u00a7r was slain by "+own+"'s Wooden Sword"),
                KillFeedRules.parse(own+" was shot by \u00a79Archer\u00a7r from 50 blocks"),
                KillFeedRules.parse("\u00a79Target\u00a7r was punched into lava by \u00a79Archer\u00a7r"),
                KillFeedRules.parse(own+" went up in flames"),KillFeedRules.parse(own+" fell out of the world"));
            preview.get(2).assist=true;
        }
        return preview;
    }
    private static void draw(int mx,int my,boolean open,List<KillFeedRules.Death> entries,boolean editing){
        Minecraft mc=MinecraftAccess.get();
        if((!editing&&!active())||mc.thePlayer==null||!editing&&(mc.gameSettings.hideGUI||mc.gameSettings.chatVisibility==net.minecraft.entity.player.EntityPlayer.EnumChatVisibility.HIDDEN))return;
        if(entries.isEmpty())return;
        if(!editing){
            if(!open){scroll=0;dragging=false;}else if(dragging){if(Mouse.isButtonDown(0))drag(my);else dragging=false;}
            scrollBy(0);
        }
        HudLayout.Rect box=bounds();
        boolean hover=open&&!editing&&box.contains(mx,my);int row=0;KillFeedRules.Death hit=null;
        float localX=(mx-box.x)/scale(),localY=(my-box.y)/scale();
        int panelWidth=width();
        float slide=KillFeedRules.slideOffset(System.nanoTime()-entries.get(0).receivedAt,UtilitySettings.killFeedSlide&&!open&&!editing);
        boolean scissor=slide<0&&GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);java.nio.IntBuffer oldScissor=slide<0?org.lwjgl.BufferUtils.createIntBuffer(16):null;
        if(slide<0){
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX,oldScissor);int sf=new ScaledResolution(mc).getScaleFactor();
            int x=box.x*sf,y=mc.displayHeight-(box.y+box.h)*sf,right=x+box.w*sf,top=y+box.h*sf;
            if(scissor){x=Math.max(x,oldScissor.get(0));y=Math.max(y,oldScissor.get(1));right=Math.min(right,oldScissor.get(0)+oldScissor.get(2));top=Math.min(top,oldScissor.get(1)+oldScissor.get(3));}
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x,y,Math.max(0,right-x),Math.max(0,top-y));
        }
        boolean light=GL11.glIsEnabled(GL11.GL_LIGHTING),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),rescale=GL11.glIsEnabled(GL12_RESCALE_NORMAL);
        GlStateManager.pushMatrix();
        try{
        GlStateManager.translate(box.x,box.y,0);GlStateManager.scale(scale(),scale(),1);
        GlStateManager.disableLighting();GlStateManager.disableDepth();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
        for(int i=open&&!editing?scroll:0;i<entries.size()&&row<rows();i++){
            KillFeedRules.Death death=entries.get(i);
            float fade=KillFeedRules.opacity(System.nanoTime()-death.receivedAt,UtilitySettings.killFeedSeconds,open)*(editing&&!UtilitySettings.killFeed?.45F:1);if(fade<=0)break;
            int textColor=0xFFFFFF|(Math.max(4,(int)(255*fade))<<24);
            int y=Math.round(row++*22+slide);boolean suicide=death.suicide();Row text=row(death);
            String detail=text.detail,actor=text.actor,victim=text.victim;
            int extraIcon=text.extra,actorWidth=text.actorWidth,w=text.w;
            int x=panelWidth-9-w;
            boolean rowHover=open&&!editing&&KillFeedRules.hitRow(localX,localY,x,y,w);
            int panel=KillFeedRules.panelColor(rowHover,UtilitySettings.killBackgroundOpacity,fade);
            if((panel>>>24)>0)Gui.drawRect(x,y,x+w,y+20,panel);
            int border=KillFeedRules.faded(death.border(mc.thePlayer.getName(),UtilitySettings.killBorderColor,UtilitySettings.deathBorderColor,UtilitySettings.assistBorderColor,UtilitySettings.killBorderOpacity),fade);
            if((border>>>24)>0){
                Gui.drawRect(x,y,x+w,y+1,border);Gui.drawRect(x,y+19,x+w,y+20,border);
                Gui.drawRect(x,y+1,x+1,y+19,border);Gui.drawRect(x+w-1,y+1,x+w,y+19,border);
            }
            if(text.actorHead)head(death.killer,x+5,y+2,fade);
            if(!suicide)mc.fontRendererObj.drawStringWithShadow(actor,x+5+(text.actorHead?HEAD_ADVANCE:0),y+6,textColor);
            if(death.assist){
                int meX=x+5+(text.actorHead?HEAD_ADVANCE:0)+mc.fontRendererObj.getStringWidth(actor);
                mc.fontRendererObj.drawStringWithShadow(" + ",meX,y+6,textColor);meX+=mc.fontRendererObj.getStringWidth(" + ");
                if(text.assistHead){head(mc.thePlayer.getName(),meX,y+2,fade);meX+=HEAD_ADVANCE;}
                mc.fontRendererObj.drawStringWithShadow(text.assist,meX,y+6,textColor);
            }
            int iconX=x+5+actorWidth+(text.actorHead?HEAD_ADVANCE:0)+(suicide?0:4);
            if(suicide)skull(iconX,y+2,fade);
            else if(death.cause==KillFeedRules.Cause.MELEE&&death.weapon==null){
                Gui.drawRect(iconX+3,y+5,iconX+13,y+15,KillFeedRules.faded(0xFFB98662,fade));
                Gui.drawRect(iconX+1,y+8,iconX+5,y+12,KillFeedRules.faded(0xFFDBAD87,fade));
                for(int finger=0;finger<4;finger++)Gui.drawRect(iconX+4+finger*2,y+4,iconX+5+finger*2,y+9,KillFeedRules.faded(0xFFE7BD94,fade));
            }else if(death.cause==KillFeedRules.Cause.FIRE||death.cause==KillFeedRules.Cause.FALL)contextIcon(death,iconX,y+2,fade);
            else drawItem(icon(death),iconX,y+2,fade);
            if(!suicide&&extraIcon>0)contextIcon(death,iconX+18,y+2,fade);
            int textX=iconX+20+(suicide?0:extraIcon);
            if(text.victimHead){head(death.victim,textX,y+2,fade);textX+=HEAD_ADVANCE;}
            mc.fontRendererObj.drawStringWithShadow(victim,textX,y+6,textColor);
            int afterName=textX+mc.fontRendererObj.getStringWidth(victim);
            if(suicide&&extraIcon>0)contextIcon(death,afterName+2,y+2,fade);
            mc.fontRendererObj.drawStringWithShadow(detail,afterName+(suicide?extraIcon:0),y+6,KillFeedRules.faded(0xFFBBC4D5,fade));
            if(rowHover)hit=death;
        }
        if(hover&&entries.size()>rows()){
            int x=panelWidth-5,h=rows()*22;
            Gui.drawRect(x,0,x+4,h,0xAA181818);
            int thumb=Math.max(8,h*rows()/entries.size());
            int top=(h-thumb)*scroll/Math.max(1,entries.size()-rows());
            Gui.drawRect(x,top,x+4,top+thumb,0xFFE0E0E0);
        }
        }finally{
            GlStateManager.popMatrix();
            if(slide<0){GL11.glScissor(oldScissor.get(0),oldScissor.get(1),oldScissor.get(2),oldScissor.get(3));if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);}
            if(light)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(rescale)GlStateManager.enableRescaleNormal();else GlStateManager.disableRescaleNormal();
            GlStateManager.color(1,1,1,1);
        }
        if(hit!=null)tooltip.show(hit.original,mx,my);
    }
    private static void contextIcon(KillFeedRules.Death death,int x,int y,float opacity){
        switch(death.context()){
            case VOID:fallIcon(x+3,y+2,true,opacity);break;
            case FALL:fallIcon(x+3,y+2,false,opacity);break;
            case LAVA:
                Gui.drawRect(x+1,y+6,x+15,y+13,KillFeedRules.faded(0xFFDA4D0E,opacity));
                Gui.drawRect(x+3,y+4,x+12,y+6,KillFeedRules.faded(0xFFFF901A,opacity));
                Gui.drawRect(x+2,y+8,x+7,y+10,KillFeedRules.faded(0xFFFFCF45,opacity));
                Gui.drawRect(x+9,y+10,x+14,y+12,KillFeedRules.faded(0xFFFFCF45,opacity));break;
            case FIRE:
                Gui.drawRect(x+3,y+5,x+13,y+12,KillFeedRules.faded(0xFFF56B13,opacity));
                Gui.drawRect(x+6,y+1,x+10,y+6,KillFeedRules.faded(0xFFFF8C19,opacity));
                Gui.drawRect(x+1,y+8,x+15,y+12,KillFeedRules.faded(0xFFF56B13,opacity));
                Gui.drawRect(x+4,y+12,x+12,y+15,KillFeedRules.faded(0xFFFFA320,opacity));
                Gui.drawRect(x+6,y+7,x+10,y+14,KillFeedRules.faded(0xFFFFE06A,opacity));break;
            default:drawItem(icon(death),x,y,opacity);
        }
    }
    private static void fallIcon(int x,int y,boolean voidFall,float opacity){
        if(voidFall){
            int rim=KillFeedRules.faded(0xFFAB80D6,opacity),hole=KillFeedRules.faded(0xFF160D26,opacity);
            Gui.drawRect(x,y+5,x+10,y+10,rim);Gui.drawRect(x-2,y+6,x+12,y+9,rim);
            Gui.drawRect(x,y+6,x+10,y+9,hole);Gui.drawRect(x+2,y+5,x+8,y+10,hole);
            Gui.drawRect(x+2,y+1,x+4,y+3,rim);Gui.drawRect(x+7,y+2,x+9,y+4,rim);
        }else{
            int ground=KillFeedRules.faded(0xFFA6A6A6,opacity),crack=KillFeedRules.faded(0xFF45454A,opacity),impact=KillFeedRules.faded(0xFFFF9360,opacity);
            Gui.drawRect(x-1,y+7,x+11,y+12,ground);Gui.drawRect(x+4,y+7,x+6,y+9,crack);
            Gui.drawRect(x+2,y+9,x+5,y+10,crack);Gui.drawRect(x+5,y+10,x+8,y+12,crack);
            Gui.drawRect(x+4,y+2,x+6,y+5,impact);Gui.drawRect(x,y+4,x+2,y+6,impact);Gui.drawRect(x+8,y+4,x+10,y+6,impact);
        }
    }
    static ItemStack icon(KillFeedRules.Death death){
        if(death.weapon!=null){
            if(weapons.isEmpty()){
                for(Object value:Item.itemRegistry){
                    Item item=(Item)value;ItemStack stack=new ItemStack(item);
                    weapons.put(stack.getDisplayName().toLowerCase(Locale.ROOT),stack);
                }
                weapons.put("wood sword",new ItemStack(Items.wooden_sword));
                weapons.put("wooden sword",new ItemStack(Items.wooden_sword));
            }
            ItemStack item=weapons.get(death.weapon.toLowerCase(Locale.ROOT));if(item!=null)return item;
        }
        switch(death.cause){
            case BOW:return new ItemStack(Items.bow);
            case EXPLOSION:return new ItemStack(Blocks.tnt);
            case FIRE:return new ItemStack(Items.lava_bucket);
            case MAGIC:return new ItemStack(Items.potionitem);
            case DROWN:return new ItemStack(Items.water_bucket);
            case CACTUS:return new ItemStack(Blocks.cactus);
            case ANVIL:return new ItemStack(Blocks.anvil);
            default:return new ItemStack(Items.bone);
        }
    }
    static void skull(int x,int y,float opacity){
        int bone=KillFeedRules.faded(0xFFE0E0D5,opacity),dark=KillFeedRules.faded(0xFF292932,opacity);
        Gui.drawRect(x+3,y+2,x+13,y+11,bone);Gui.drawRect(x+5,y+11,x+11,y+14,bone);
        Gui.drawRect(x+4,y+5,x+7,y+8,dark);Gui.drawRect(x+9,y+5,x+12,y+8,dark);Gui.drawRect(x+7,y+9,x+9,y+11,dark);
    }
    /**
     * Heads are drawn as skin texels rather than a bound texture: a skin that is still loading,
     * a released texture, or leftover GL state could otherwise leave the head invisible.
     */
    static void head(String name,int x,int y,float opacity){
        int[] pixels=face(name);
        if(pixels.length!=64){
            Gui.drawRect(x,y,x+HEAD,y+HEAD,KillFeedRules.faded(0xFF8A8A8A,opacity));
            return;
        }
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            int pixel=pixels[row*8+col];if((pixel>>>24)<16)continue;
            Gui.drawRect(x+col*2,y+row*2,x+col*2+2,y+row*2+2,KillFeedRules.faded(pixel,opacity));
        }
    }
    static int[] face(String name){
        Minecraft mc=MinecraftAccess.get();String key=name.toLowerCase(Locale.ROOT);
        NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(name);
        ResourceLocation skin=info==null?null:info.getLocationSkin();
        if(mc.thePlayer!=null&&name.equalsIgnoreCase(mc.thePlayer.getName()))skin=mc.thePlayer.getLocationSkin();
        int[] pixels=skinFace(skin);
        if(pixels.length==64){heads.put(key,pixels);return pixels;}
        // Keep the last real head while a skin is reloading or its texture is momentarily released.
        int[] remembered=heads.get(key);
        if(remembered!=null)return remembered;
        pixels=skinFace(DefaultPlayerSkin.getDefaultSkinLegacy());
        if(pixels.length==64)heads.put(key,pixels);
        return pixels;
    }
    private static int[] skinFace(ResourceLocation skin){
        return skin==null?new int[0]:SkinFace.composite(skin);
    }
    private static void drawItem(ItemStack stack,int x,int y,float opacity){
        Minecraft mc=MinecraftAccess.get();mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);
        GlStateManager.enableTexture2D();GlStateManager.disableAlpha();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,opacity);
        sprites.drawTexturedModalRect(x,y,mc.getRenderItem().getItemModelMesher().getItemModel(stack).getParticleTexture(),16,16);
        GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
    }
    private static final int GL12_RESCALE_NORMAL=32826;
    private static final class Tooltip extends GuiScreen {
        void show(String text,int x,int y){
            Minecraft mc=MinecraftAccess.get();ScaledResolution sr=new ScaledResolution(mc);
            if(this.mc==null||width!=sr.getScaledWidth()||height!=sr.getScaledHeight())setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
            drawHoveringText(fontRendererObj.listFormattedStringToWidth(text,Math.max(30,Math.min(360,width-24))),x,y);
        }
    }
}
