package otl.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.block.BlockFence;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client-side animation state: a skateboard and emotes for the local player. Nothing runs until
 * the player asks for it - no emote and no board means the model is left exactly vanilla.
 * Nothing is sent to the server. The board is shared with other OTL clients through
 * {@link BoardSync}; emotes stay local.
 *
 * <p>The emote owns the camera until the player left-clicks, takes damage, starts moving, opens a
 * screen, changes the perspective themselves, dies or leaves the world. Every exit restores the
 * view that was saved when the emote started, and the click that ends it is swallowed.
 */
final class PlayerAnim {
    static final PlayerAnim INSTANCE=new PlayerAnim();

    /** Reach, set down, then hop aboard in just under a second. */
    private static final float PULL_OUT=1f/23f;
    private static final int OLLIE_TICKS=11;
    /** One kick of the back leg; after it the player just balances and the board rolls. */
    private static final int PUSH_TICKS=12;
    private static final int LAND_TICKS=8;
    /** Ticks the emote pose takes to fade in. */
    private static final float EMOTE_FADE=4f;
    /**
     * The mesh is baked in real-world metres (0.815 x 0.217 x 0.131, wheels at Y=0). Real size is
     * tiny next to a Minecraft player, whose head alone is half a block wide, so the board is drawn
     * 1.6x: about 1.3 blocks long, the size a boat reads at. This is the one number to change.
     */
    private static final float BOARD_SCALE=1.7f;
    // The nose and tail rise above the grip tape where the rider actually stands.
    static final float DECK_TOP=0.09116f*BOARD_SCALE;
    private static final class State {
        float board,prevBoard,stow,prevStow;
        boolean wantsBoard;
        int ollieAt=-1,trick;
        /** Tick the single kick started on; -1 when the player is just balancing. */
        int pushAt=-1;
        /** Whether movement was held last tick, so one kick starts per stop-to-move change. */
        boolean wasMoving;
        float air,prevAir,crouch,prevCrouch,fall,prevFall,grind,prevGrind;
        int netPose,netTrick=-1,netLanding;
        boolean netPush;
        int landAt=-1;
        float landing;
        double airTop=Double.NaN;
        /** -1 while no emote runs; emoteEnd holds the tick the last one stopped on. */
        int emote=-1,emoteAt,emoteLast=-1,emoteEnd=-1;
        boolean baseline;
        float lastHealth;
        int lastHurt;
        boolean lastGround;
    }

    private static final Map<Integer,State> STATES=new HashMap<>();
    /** Two reusable poses: the base one, and the emote that blends on top of it. */
    private static final AnimPose SCRATCH=new AnimPose(),EMOTE=new AnimPose();
    private static boolean managing;
    private static int savedView,managedView;

    private PlayerAnim(){}

    static boolean enabled(){
        UtilitySettings.load();
        return UtilitySettings.playerAnimation;
    }

    static boolean skating(){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null||mc.thePlayer==null||!enabled())return false;
        State s=STATES.get(mc.thePlayer.getEntityId());
        return s!=null&&(s.wantsBoard||s.board>0f||s.prevBoard>0f);
    }

    /** Whether the local player has a board out, for the board sharing link. */
    static boolean localWantsBoard(){
        Minecraft mc=MinecraftAccess.get();
        return mc!=null&&mc.thePlayer!=null&&state(mc.thePlayer).wantsBoard;
    }

    static boolean posing(EntityPlayer p){
        State s=STATES.get(p.getEntityId());
        return s!=null&&(s.board>0f||s.prevBoard>0f||s.emote>=0||s.emoteLast>=0)&&enabled();
    }

    /**
     * Draws the board after the player, following the rig's hand until the deck is placed.
     *
     * <p>{@code RenderPlayerEvent.Post} runs in camera space, after the renderer popped the entity
     * transform, so the player's own position has to be applied here before anything else.
     *
     * <p>Other riders use the same clips, driven by the board and trick state from {@link BoardSync}.
     */
    static void renderBoard(EntityPlayer p,float partial,double x,double y,double z,ModelBase model){
        State s=STATES.get(p.getEntityId());
        if(s==null||!enabled()||p.isInvisible()||p.isSpectator())return;
        float board=progress(s,partial);
        float visible=AnimClips.smooth((board-0.05f)/0.15f);
        if(visible<=0f)return;
        float place=AnimClips.place(board),carry=1f-place;
        float boardX=1.38f*carry,boardY=-0.45f*carry,boardZ=0f;
        float trick=phase(p.ticksExisted+partial,s.ollieAt,OLLIE_TICKS);
        AnimPose deck=SCRATCH;
        deck.reset();
        if(trick>=0f)AnimClips.ollie(deck,trick,s.trick);
        else{
            AnimClips.skateFall(deck,fall(s,partial)*AnimClips.mount(board));
            AnimClips.skateGrind(deck,grind(s,partial)*AnimClips.mount(board));
        }
        Vec3 hand=carry>0f&&model instanceof AnimatedModelPlayer
            ?((AnimatedModelPlayer)model).skateHand():new Vec3(0.23,0.92,0.38);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try{
            float size=BOARD_SCALE*visible;
            // The x/y/z given to the renderer are already camera-relative: RendererLivingEntity
            // translates by exactly these before drawing the model. They are the player's feet.
            net.minecraft.client.renderer.GlStateManager.translate(x,y,z);
            // Vanilla turns the model by 180 - bodyYaw; the board needs the same turn, or it keeps
            // pointing the same way no matter which direction the player rides.
            net.minecraft.client.renderer.GlStateManager.rotate(180f-bodyYaw(p,partial),0f,1f,0f);
            // Convert the upgraded OBJ's local X heading to OTL's old board heading before applying
            // the legacy trick rotations. This preserves the original kick-flip axes and timing.
            net.minecraft.client.renderer.GlStateManager.rotate(90f,0f,1f,0f);
            net.minecraft.client.renderer.GlStateManager.translate(hand.xCoord*carry,
                hand.yCoord*carry+deck.boardDrop+0.087f*size,hand.zCoord*carry+AnimClips.setDownZ(board));
            // Rotate tricks around the deck, not around a point underneath its wheels.
            net.minecraft.client.renderer.GlStateManager.translate(0f,0.13f,0f);
            net.minecraft.client.renderer.GlStateManager.rotate((boardX+deck.boardX)*57.29578f,1f,0f,0f);
            net.minecraft.client.renderer.GlStateManager.rotate((boardY+deck.boardY)*57.29578f,0f,1f,0f);
            net.minecraft.client.renderer.GlStateManager.rotate((boardZ+deck.boardZ)*57.29578f,0f,0f,1f);
            net.minecraft.client.renderer.GlStateManager.translate(0f,-0.13f,0f);
            // Scaled about the wheels, which sit at Y=0, so the board grows upward and stays grounded.
            net.minecraft.client.renderer.GlStateManager.scale(size,size,size);
            ObjSkateboard.render();
        }finally{
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    /** The body yaw vanilla would use for this frame, wrapped the same way it wraps it. */
    private static float bodyYaw(EntityPlayer p,float partial){
        float d=p.renderYawOffset-p.prevRenderYawOffset;
        while(d<-180f)d+=360f;
        while(d>=180f)d-=360f;
        return p.prevRenderYawOffset+d*partial;
    }

    static void apply(AnimatedModelPlayer model,EntityPlayer p,float swing,float amount,float age){
        Minecraft mc=MinecraftAccess.get();
        State s=STATES.get(p.getEntityId());
        if(s==null)return;
        float board=progress(s,age-p.ticksExisted);
        if(board<=0f){
            if(p==mc.thePlayer)emote(model,s,age);
            return;
        }
        float partial=Math.max(0f,Math.min(1f,age-p.ticksExisted));
        float stow=s.prevStow+(s.stow-s.prevStow)*partial;
        float mount=AnimClips.mount(board);
        AnimPose pose=SCRATCH;
        pose.reset();
        AnimClips.deploy(pose,board);
        float deploy=AnimClips.smooth(board/0.16f);
        blendModel(model,pose,deploy);
        blendJoints(model,pose,deploy);
        pose.reset();
        float push=phase(age,s.pushAt,PUSH_TICKS);
        float trick=phase(age,s.ollieAt,OLLIE_TICKS);
        AnimClips.skate(pose,trick>=0f?-1f:push);
        float sway=(float)Math.sin(age*0.19f);
        float glide=AnimClips.smooth(amount);
        pose.add(AnimPose.BODY,0.025f*sway+0.09f*glide,0f,0.035f*sway);
        pose.add(AnimPose.RIGHT_ARM,0.045f*sway,0f,0f);
        pose.add(AnimPose.LEFT_ARM,-0.045f*sway,0f,0f);
        float air=s.prevAir+(s.air-s.prevAir)*partial;
        if(trick>=0f)AnimClips.ollie(pose,trick,s.trick);
        else{
            pose.add(AnimPose.RIGHT_LEG,-0.26f*air,0f,0f);
            pose.add(AnimPose.LEFT_LEG,-0.30f*air,0f,0f);
            pose.kneeRight+=0.34f*air;
            pose.kneeLeft+=0.42f*air;
            AnimClips.skateFall(pose,fall(s,partial));
            float impact=phase(age,s.landAt,LAND_TICKS);
            AnimClips.skateCrouch(pose,0.85f*s.landing*AnimClips.pulse(impact,0f,0.24f,1f));
            AnimClips.skateGrind(pose,grind(s,partial));
        }
        float crouch=AnimClips.smooth(s.prevCrouch+(s.crouch-s.prevCrouch)*partial);
        AnimClips.skateCrouch(pose,crouch*(1f-air)*(1f-stow));
        float step=stow*AnimClips.pulse(board,0.59f,0.81f,1f);
        pose.add(AnimPose.RIGHT_LEG,0.28f*step,0f,0.36f*step);
        pose.kneeRight+=0.25f*step;
        pose.add(AnimPose.BODY,0f,0f,0.11f*step);
        blendModel(model,pose,mount);
        blendJoints(model,pose,mount);
        float hop=0.22f*(1f-stow)*AnimClips.pulse(board,0.59f,0.78f,1f);
        float grounded=mount*(1f-air)*(1f-step);
        model.skateOffsets(mount,hop,0.19f*step,grounded,trick>=0f?-1f:push);
    }

    private static float progress(State s,float partial){
        return s.prevBoard+(s.board-s.prevBoard)*Math.max(0f,Math.min(1f,partial));
    }

    private static float fall(State s,float partial){
        return s.prevFall+(s.fall-s.prevFall)*partial;
    }

    private static float grind(State s,float partial){
        return AnimClips.smooth(s.prevGrind+(s.grind-s.prevGrind)*partial);
    }

    private static float phase(float age,int start,int duration){
        if(start<0)return -1f;
        float t=(age-start)/duration;
        return t>=0f&&t<=1f?t:-1f;
    }

    /**
     * The pose fades in when an emote starts and fades back out when it stops, so ending one never
     * snaps the limbs. The stopped pose keeps playing while it fades, which hides the transition.
     */
    private static void emote(AnimatedModelPlayer model,State s,float age){
        int id=s.emote>=0?s.emote:s.emoteLast;
        if(id<0)return;
        float weight;
        if(s.emote>=0){
            weight=AnimClips.smooth((age-s.emoteAt)/EMOTE_FADE);
        }else{
            weight=1f-AnimClips.smooth((age-s.emoteEnd)/EMOTE_FADE);
            if(weight<=0.001f){s.emoteEnd=-1;s.emoteLast=-1;return;}
        }
        if(weight<=0.001f)return;
        AnimPose pose=EMOTE;
        pose.reset();
        AnimClips.emote(pose,id,age-s.emoteAt);
        blendModel(model,pose,weight);
        blendJoints(model,pose,weight);
    }

    static void emoteKey(Minecraft mc){
        UtilitySettings.load();
        if(!UtilitySettings.playerAnimation||mc==null||mc.theWorld==null||mc.thePlayer==null||mc.currentScreen!=null)return;
        EntityPlayer p=mc.thePlayer;
        if(p.isRiding()||moving(p))return;
        State s=state(p);
        s.emote=(s.emote+1)%AnimClips.EMOTES;
        s.emoteAt=p.ticksExisted;
        s.emoteEnd=-1;
        s.wantsBoard=false;
        s.baseline=true;
        s.lastHealth=health(p);
        s.lastHurt=p.hurtTime;
        beginCamera(mc);
    }

    static void skateKey(Minecraft mc){
        UtilitySettings.load();
        if(!UtilitySettings.playerAnimation||mc==null||mc.thePlayer==null||mc.currentScreen!=null)return;
        EntityPlayer p=mc.thePlayer;
        if(p.isRiding())return;
        State s=state(p);
        if(s.emote>=0)stopEmote(mc,p,true);
        if(s.wantsBoard){
            s.wantsBoard=false;
        }else{
            s.wantsBoard=true;
            beginSkateCamera(mc);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=MinecraftAccess.get();
        if(mc.theWorld==null||mc.thePlayer==null){
            if(managing)endCamera(mc,true);
            return;
        }
        if(!enabled()){
            if(managing)endCamera(mc,true);
            STATES.clear();
            BoardSync.reset();
            return;
        }
        for(EntityPlayer p:new java.util.ArrayList<EntityPlayer>(mc.theWorld.playerEntities))step(p);
        checkEmote(mc);
        EntityPlayer rider=mc.thePlayer;
        State local=state(rider);
        BoardSync.tick(rider.getName(),local.wantsBoard,local.netPose,
            local.netTrick,local.netPush,local.netLanding);
        local.netTrick=-1;local.netPush=false;local.netLanding=0;
    }

    /** Attack or place exits an active animation before Minecraft handles the click. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void mouse(MouseEvent event){
        if((event.button!=0&&event.button!=1)||!event.buttonstate)return;
        Minecraft mc=MinecraftAccess.get();
        // A screen that takes clicks (chat) must keep them; the emote already ended on open.
        if(mc.thePlayer==null||mc.currentScreen!=null)return;
        State s=STATES.get(mc.thePlayer.getEntityId());
        boolean emoting=s!=null&&s.emote>=0;
        boolean skating=s!=null&&s.wantsBoard;
        if(!emoting&&!managing&&!skating)return;
        if(emoting)stopEmote(mc,mc.thePlayer,true);
        else if(managing)endCamera(mc,true);
        if(skating){
            exitBoard(s);
            endCamera(mc,true);
        }
        event.setCanceled(true);
    }

    static void clear(){
        Minecraft mc=MinecraftAccess.get();
        if(managing&&mc!=null)endCamera(mc,true);
        STATES.clear();
        BoardSync.reset();
    }

    private static void step(EntityPlayer p){
        State s=state(p);
        boolean local=p==MinecraftAccess.get().thePlayer;
        BoardSync.Ride peer=local?null:BoardSync.take(p.getName());
        if(!local){
            s.wantsBoard=peer!=null&&peer.board;
            if(s.wantsBoard){
                if(peer.trick>=0){s.ollieAt=p.ticksExisted;s.trick=peer.trick;}
                if(peer.push)s.pushAt=p.ticksExisted;
                if(peer.landing>0){s.landAt=p.ticksExisted;s.landing=BoardSync.amount(peer.landing);}
            }
        }
        // Combat interrupts skating immediately. Do not leave a board/pose fade running after
        // damage: the player must return to the exact vanilla render on this tick.
        if(local&&(s.wantsBoard||s.board>0f)&&(p.hurtTime>0||p.isDead||p.isRiding())){
            exitBoard(s);
            endCamera(MinecraftAccess.get(),true);
        }
        s.prevBoard=s.board;
        s.prevStow=s.stow;
        s.stow=s.wantsBoard?Math.max(0f,s.stow-0.18f):Math.min(1f,s.stow+0.18f);
        float target=s.wantsBoard?1f:0f;
        if(s.board<target)s.board=Math.min(target,s.board+PULL_OUT);
        else if(s.board>target)s.board=Math.max(target,s.board-PULL_OUT);
        int pose=peer==null?-1:peer.pose;
        boolean grounded=pose<0?p.onGround:BoardSync.grounded(pose);
        boolean sneaking=pose<0?p.isSneaking():BoardSync.crouching(pose);
        s.airTop=grounded||Double.isNaN(s.airTop)?p.posY:Math.max(s.airTop,p.posY);
        float targetFall=grounded?0f:pose>=0?BoardSync.falling(pose):
            AnimClips.fallStrength(s.airTop-p.posY,Math.max(-p.motionY,p.prevPosY-p.posY));
        s.prevAir=s.air;
        s.air=grounded?Math.max(0f,s.air-0.34f):Math.min(1f,s.air+0.25f);
        s.prevFall=s.fall;
        if(grounded){
            if(pose<0&&!s.lastGround&&s.wantsBoard&&s.fall>0.15f){
                s.landAt=p.ticksExisted;
                s.landing=s.fall;
                if(local)s.netLanding=BoardSync.level(s.fall);
            }
            s.fall=0f;
        }else if(s.wantsBoard){
            s.fall+=(targetFall-s.fall)*0.5f;
        }else s.fall=0f;
        s.prevCrouch=s.crouch;
        s.crouch=sneaking?Math.min(1f,s.crouch+0.25f):Math.max(0f,s.crouch-0.25f);
        if(s.ollieAt>=0&&p.ticksExisted-s.ollieAt>OLLIE_TICKS)s.ollieAt=-1;
        if(s.pushAt>=0&&p.ticksExisted-s.pushAt>PUSH_TICKS)s.pushAt=-1;
        boolean rail=s.wantsBoard&&s.board>=0.999f&&(pose<0?onRail(p):BoardSync.grinding(pose));
        if(local)s.netPose=BoardSync.pose(grounded,sneaking,rail,targetFall);
        s.prevGrind=s.grind;
        s.grind=rail?Math.min(1f,s.grind+0.25f):Math.max(0f,s.grind-0.20f);
        if(rail){
            s.pushAt=-1;
            if(s.grind>=0.5f&&(p.ticksExisted&1)==0)grindSparks(p);
        }
        // Crouch then jump pops the ollie; a plain jump is left alone as a normal jump.
        // Do not rely only on the server's onGround transition: in 1.8 that flag can remain true
        // for the first client tick of a jump. The jump key is the reliable local edge, while
        // lastGround prevents a held key from starting another trick in mid-air.
        boolean jumped=!p.onGround&&s.lastGround&&p.motionY>0.05;
        boolean jumpPressed=MinecraftAccess.get().gameSettings.keyBindJump.isKeyDown();
        if(local&&(jumped||jumpPressed&&!p.onGround&&s.lastGround)&&p.isSneaking()
            &&s.wantsBoard&&s.board>=0.999f&&s.ollieAt<0){
            s.ollieAt=p.ticksExisted;
            s.trick=(int)(Math.random()*3);
            s.netTrick=s.trick;
        }
        s.lastGround=grounded;
        if(local&&s.wantsBoard&&s.board>=0.999f){
            // One kick when movement starts; while it keeps moving the stance just balances.
            boolean walking=walking(p);
            if(walking&&!s.wasMoving&&p.onGround&&s.pushAt<0&&s.ollieAt<0&&s.grind==0f){
                s.pushAt=p.ticksExisted;s.netPush=true;
            }
            s.wasMoving=walking;
        }else{
            s.wasMoving=false;
        }
    }

    static boolean onRail(EntityPlayer p){
        double dx=p.posX-p.prevPosX,dz=p.posZ-p.prevPosZ;
        if(!p.onGround||dx*dx+dz*dz<=0.0016)return false;
        BlockPos below=new BlockPos(p.posX,p.posY-0.05,p.posZ);
        if(Math.abs(p.posY-below.getY()-1)<0.06&&p.worldObj.getBlockState(below).getBlock()==Blocks.iron_bars)return true;
        BlockPos fence=new BlockPos(p.posX,p.posY-0.55,p.posZ);
        return Math.abs(p.posY-fence.getY()-1.5)<0.06&&p.worldObj.getBlockState(fence).getBlock() instanceof BlockFence;
    }

    private static void grindSparks(EntityPlayer p){
        Minecraft mc=MinecraftAccess.get();
        if(mc.thePlayer==null||p.getDistanceSqToEntity(mc.thePlayer)>32*32)return;
        double dx=p.posX-p.prevPosX,dz=p.posZ-p.prevPosZ;
        double x=p.posX-dx*0.55,z=p.posZ-dz*0.55;
        p.worldObj.spawnParticle(EnumParticleTypes.CRIT,x,p.posY+0.035,z,-dx*1.4,0.085,-dz*1.4);
        p.worldObj.spawnParticle(EnumParticleTypes.FLAME,x,p.posY+0.035,z,-dx*0.8,0.025,-dz*0.8);
    }

    private static void exitBoard(State s){
        s.wantsBoard=false;
        s.board=0f;
        s.prevBoard=0f;
        s.pushAt=-1;
        s.ollieAt=-1;
        s.fall=s.prevFall=0f;
        s.grind=s.prevGrind=0f;
        s.landAt=-1;
        s.airTop=Double.NaN;
        s.wasMoving=false;
    }

    private static void checkEmote(Minecraft mc){
        EntityPlayer p=mc.thePlayer;
        State s=STATES.get(p.getEntityId());
        if(s==null){
            if(managing)endCamera(mc,true);
            return;
        }
        float hp=health(p);
        int hurt=p.hurtTime;
        boolean damaged=s.baseline&&(hp<s.lastHealth||hurt>s.lastHurt);
        s.baseline=true;
        s.lastHealth=hp;
        s.lastHurt=hurt;
        if(s.emote<0){
            if(managing&&(mc.gameSettings.thirdPersonView!=managedView||!skating()))endCamera(mc,true);
            return;
        }
        // F5 is the player's own choice: drop the emote, but leave the view they picked.
        boolean ownView=mc.gameSettings.thirdPersonView!=2;
        if(damaged||p.isDead||hp<=0f||mc.currentScreen!=null||moving(p)||ownView)
            stopEmote(mc,p,!ownView);
    }

    private static void stopEmote(Minecraft mc,EntityPlayer p,boolean restore){
        State s=STATES.get(p.getEntityId());
        if(s!=null&&s.emote>=0){
            // The pose keeps fading out from here, so nothing snaps back to the vanilla stance.
            s.emoteLast=s.emote;
            s.emote=-1;
            s.emoteEnd=p.ticksExisted;
        }
        if(managing)endCamera(mc,restore);
    }

    private static void beginCamera(Minecraft mc){
        if(managing||!UtilitySettings.emoteCamera)return;
        savedView=mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView=managedView=2;
        managing=true;
    }

    private static void beginSkateCamera(Minecraft mc){
        if(managing)return;
        savedView=mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView=managedView=1;
        managing=true;
    }

    private static void endCamera(Minecraft mc,boolean restore){
        if(!managing)return;
        managing=false;
        if(restore&&mc.gameSettings.thirdPersonView==managedView)mc.gameSettings.thirdPersonView=savedView;
    }

    private static boolean moving(EntityPlayer p){
        if(!(p instanceof net.minecraft.client.entity.EntityPlayerSP))return false;
        net.minecraft.util.MovementInput in=((net.minecraft.client.entity.EntityPlayerSP)p).movementInput;
        return in!=null&&(in.moveForward!=0f||in.moveStrafe!=0f||in.jump||in.sneak);
    }

    /** Direction keys only: crouching or jumping must not count as a kick. */
    private static boolean walking(EntityPlayer p){
        if(!(p instanceof net.minecraft.client.entity.EntityPlayerSP))return false;
        net.minecraft.util.MovementInput in=((net.minecraft.client.entity.EntityPlayerSP)p).movementInput;
        return in!=null&&(in.moveForward!=0f||in.moveStrafe!=0f);
    }

    private static float health(EntityPlayer p){
        return p.getHealth()+p.getAbsorptionAmount();
    }

    private static State state(EntityPlayer p){
        return STATES.computeIfAbsent(p.getEntityId(),id->new State());
    }

    /** A replaced part ignores the vanilla pose; every other part is nudged on top of it. */
    private static void poseModel(ModelPlayer m,AnimPose p){
        posePart(m.bipedHead,p,AnimPose.HEAD);
        posePart(m.bipedBody,p,AnimPose.BODY);
        posePart(m.bipedRightArm,p,AnimPose.RIGHT_ARM);
        posePart(m.bipedLeftArm,p,AnimPose.LEFT_ARM);
        posePart(m.bipedRightLeg,p,AnimPose.RIGHT_LEG);
        posePart(m.bipedLeftLeg,p,AnimPose.LEFT_LEG);
        copyWear(m);
    }

    private static void posePart(ModelRenderer r,AnimPose p,int i){
        if(p.replace[i]){
            r.rotateAngleX=p.angle[i];
            r.rotateAngleY=p.angle[i+1];
            r.rotateAngleZ=p.angle[i+2];
        }else{
            r.rotateAngleX+=p.angle[i];
            r.rotateAngleY+=p.angle[i+1];
            r.rotateAngleZ+=p.angle[i+2];
        }
    }

    private static void blendModel(ModelPlayer m,AnimPose p,float weight){
        if(p.replace[AnimPose.HEAD])blendPart(m.bipedHead,p,AnimPose.HEAD,weight);
        blendPart(m.bipedBody,p,AnimPose.BODY,weight);
        blendPart(m.bipedRightArm,p,AnimPose.RIGHT_ARM,weight);
        blendPart(m.bipedLeftArm,p,AnimPose.LEFT_ARM,weight);
        blendPart(m.bipedRightLeg,p,AnimPose.RIGHT_LEG,weight);
        blendPart(m.bipedLeftLeg,p,AnimPose.LEFT_LEG,weight);
        copyWear(m);
    }

    /**
     * The joints blend with the same weight as the limbs they belong to, so an emote fades its
     * elbows and knees in with the rest of the pose instead of snapping them.
     */
    private static void blendJoints(AnimatedModelPlayer m,AnimPose p,float w){
        m.elbowRight+= (p.elbowRight-m.elbowRight)*w;
        m.elbowLeft += (p.elbowLeft -m.elbowLeft )*w;
        m.kneeRight += (p.kneeRight -m.kneeRight )*w;
        m.kneeLeft  += (p.kneeLeft  -m.kneeLeft  )*w;
        m.elbowRightZ+=(p.elbowRightZ-m.elbowRightZ)*w;
        m.elbowLeftZ +=(p.elbowLeftZ -m.elbowLeftZ )*w;
        m.kneeRightZ +=(p.kneeRightZ -m.kneeRightZ )*w;
        m.kneeLeftZ  +=(p.kneeLeftZ  -m.kneeLeftZ  )*w;
    }

    private static void blendPart(ModelRenderer r,AnimPose p,int i,float w){
        r.rotateAngleX+=(p.angle[i]-r.rotateAngleX)*w;
        r.rotateAngleY+=(p.angle[i+1]-r.rotateAngleY)*w;
        r.rotateAngleZ+=(p.angle[i+2]-r.rotateAngleZ)*w;
    }

    /** The skin's outer layer is a second copy, so it has to follow the parts it covers. */
    private static void copyWear(ModelPlayer m){
        ModelBase.copyModelAngles(m.bipedHead,m.bipedHeadwear);
        ModelBase.copyModelAngles(m.bipedBody,m.bipedBodyWear);
        ModelBase.copyModelAngles(m.bipedRightArm,m.bipedRightArmwear);
        ModelBase.copyModelAngles(m.bipedLeftArm,m.bipedLeftArmwear);
        ModelBase.copyModelAngles(m.bipedRightLeg,m.bipedRightLegwear);
        ModelBase.copyModelAngles(m.bipedLeftLeg,m.bipedLeftLegwear);
    }
}
