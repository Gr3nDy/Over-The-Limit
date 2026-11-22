package otl.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
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

    /** Board progress per tick; a pull-out or stow takes about seven ticks. */
    private static final float PULL_OUT=0.16f;
    private static final int OLLIE_TICKS=11;
    /** One kick of the back leg; after it the player just balances and the board rolls. */
    private static final int PUSH_TICKS=9;
    /** Ticks the emote pose takes to fade in. */
    private static final float EMOTE_FADE=4f;
    /**
     * The mesh is baked in real-world metres (0.815 x 0.217 x 0.131, wheels at Y=0). Real size is
     * tiny next to a Minecraft player, whose head alone is half a block wide, so the board is drawn
     * 1.6x: about 1.3 blocks long, the size a boat reads at. This is the one number to change.
     */
    private static final float BOARD_SCALE=1.7f;
    private static final class State {
        float board,prevBoard;
        boolean wantsBoard;
        int ollieAt=-1,trick;
        /** Tick the single kick started on; -1 when the player is just balancing. */
        int pushAt=-1;
        /** Whether movement was held last tick, so one kick starts per stop-to-move change. */
        boolean wasMoving;
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
    private static int savedView;

    private PlayerAnim(){}

    static boolean enabled(){
        UtilitySettings.load();
        return UtilitySettings.playerAnimation;
    }

    static boolean skating(){
        Minecraft mc=MinecraftAccess.get();
        return mc!=null&&mc.thePlayer!=null&&state(mc.thePlayer).wantsBoard;
    }

    /** Whether the local player has a board out, for the board sharing link. */
    static boolean localWantsBoard(){
        Minecraft mc=MinecraftAccess.get();
        return mc!=null&&mc.thePlayer!=null&&state(mc.thePlayer).wantsBoard;
    }

    /** True while this player has an emote running, so the rig is installed only for emotes. */
    static boolean emoting(EntityPlayer p){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null||p==null)return false;
        State s=STATES.get(p.getEntityId());
        return s!=null&&s.emote>=0;
    }

    /**
     * Draws the board under a skater after the vanilla model has rendered. This replaces the old
     * mainModel swap, which Lunar/OptiFine turned into a full-screen white flash.
     *
     * <p>{@code RenderPlayerEvent.Post} runs in camera space, after the renderer popped the entity
     * transform, so the player's own position has to be applied here before anything else.
     *
     * <p>Other players' boards come from {@link BoardSync}: the board itself is placed from their
     * real position and body yaw, so a remote rider needs nothing but "board is out" to look right.
     */
    static void renderBoard(EntityPlayer p,float partial,double x,double y,double z){
        Minecraft mc=MinecraftAccess.get();
        if(mc==null)return;
        State s=STATES.get(p.getEntityId());
        if(s==null||s.board<=0.001f)return;
        float board=s.prevBoard+(s.board-s.prevBoard)*partial;
        float boardDrop=(1f-board)*0.40f;
        float boardX=-0.9f*(1f-board),boardY=0f,boardZ=0f;
        if(s.ollieAt>=0){
            float phase=(p.ticksExisted+partial-s.ollieAt)/OLLIE_TICKS;
            if(phase>=0f&&phase<=1f){
                if(s.trick==0)boardX+=-0.85f*phase;
                else if(s.trick==1)boardZ=2f*(float)Math.PI*phase;
                else boardY=(float)Math.PI*phase;
            }
        }
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try{
            // The x/y/z given to the renderer are already camera-relative: RendererLivingEntity
            // translates by exactly these before drawing the model. They are the player's feet.
            net.minecraft.client.renderer.GlStateManager.translate(x,y,z);
            // Vanilla turns the model by 180 - bodyYaw; the board needs the same turn, or it keeps
            // pointing the same way no matter which direction the player rides.
            net.minecraft.client.renderer.GlStateManager.rotate(180f-bodyYaw(p,partial),0f,1f,0f);
            // The mesh already has the wheels at Y=0, so no lift is needed to rest on the ground.
            net.minecraft.client.renderer.GlStateManager.translate(0f,boardDrop,0f);
            net.minecraft.client.renderer.GlStateManager.rotate(boardX*57.29578f,1f,0f,0f);
            net.minecraft.client.renderer.GlStateManager.rotate(boardY*57.29578f,0f,1f,0f);
            net.minecraft.client.renderer.GlStateManager.rotate(boardZ*57.29578f,0f,0f,1f);
            // Scaled about the wheels, which sit at Y=0, so the board grows upward and stays grounded.
            net.minecraft.client.renderer.GlStateManager.scale(BOARD_SCALE,BOARD_SCALE,BOARD_SCALE);
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

    /** Pushes the emote pose for one render frame. Riding never touches the player's model. */
    static void apply(AnimatedModelPlayer model,EntityPlayer p,float swing,float amount,float age){
        Minecraft mc=MinecraftAccess.get();
        State s=STATES.get(p.getEntityId());
        // The skateboard is drawn separately in renderBoard; the player model stays vanilla.
        if(s!=null&&p==mc.thePlayer)emote(model,s,age);
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
            exitBoard(s);
            endCamera(mc,true);
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
        BoardSync.tick();
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
        if(p!=MinecraftAccess.get().thePlayer)remote(s,p);
        // Combat interrupts skating immediately. Do not leave a board/pose fade running after
        // damage: the player must return to the exact vanilla render on this tick.
        if(p==MinecraftAccess.get().thePlayer&&s.wantsBoard&&p.hurtTime>0){
            exitBoard(s);
            endCamera(MinecraftAccess.get(),true);
        }
        s.prevBoard=s.board;
        float target=s.wantsBoard?1f:0f;
        if(s.board<target)s.board=Math.min(target,s.board+PULL_OUT);
        else if(s.board>target)s.board=Math.max(target,s.board-PULL_OUT);
        if(s.ollieAt>=0&&p.ticksExisted-s.ollieAt>OLLIE_TICKS)s.ollieAt=-1;
        if(s.pushAt>=0&&p.ticksExisted-s.pushAt>PUSH_TICKS)s.pushAt=-1;
        // Crouch then jump pops the ollie; a plain jump is left alone as a normal jump.
        // Do not rely only on the server's onGround transition: in 1.8 that flag can remain true
        // for the first client tick of a jump. The jump key is the reliable local edge, while
        // lastGround prevents a held key from starting another trick in mid-air.
        boolean jumped=!p.onGround&&s.lastGround&&p.motionY>0.05;
        boolean jumpPressed=MinecraftAccess.get().gameSettings.keyBindJump.isKeyDown();
        if(p==MinecraftAccess.get().thePlayer&&(jumped||jumpPressed&&!p.onGround&&s.lastGround)&&p.isSneaking()
            &&s.wantsBoard&&s.board>=0.9f&&s.ollieAt<0){
            s.ollieAt=p.ticksExisted;
            s.trick=(int)(Math.random()*3);
            BoardSync.trick(s.trick);
        }
        s.lastGround=p.onGround;
        if(p==MinecraftAccess.get().thePlayer&&s.wantsBoard&&s.board>=0.9f){
            // One kick when movement starts; while it keeps moving the stance just balances.
            boolean walking=walking(p);
            if(walking&&!s.wasMoving&&s.pushAt<0&&s.ollieAt<0)s.pushAt=p.ticksExisted;
            s.wasMoving=walking;
        }else{
            s.wasMoving=false;
            s.pushAt=-1;
        }
    }

    private static void exitBoard(State s){
        s.wantsBoard=false;
        s.board=0f;
        s.prevBoard=0f;
        s.pushAt=-1;
        s.ollieAt=-1;
        s.wasMoving=false;
    }

    /**
     * A remote rider's board comes from their own client. The ollie is stamped with their tick
     * count as it is now, so the same render code animates it from the moment the message lands.
     */
    private static void remote(State s,EntityPlayer p){
        s.wantsBoard=BoardSync.riding(p.getName());
        int trick=BoardSync.takeTrick(p.getName());
        if(trick>=0){
            s.ollieAt=p.ticksExisted;
            s.trick=trick;
        }
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
            if(managing)endCamera(mc,true);
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
        mc.gameSettings.thirdPersonView=2;
        managing=true;
    }

    private static void beginSkateCamera(Minecraft mc){
        if(managing)return;
        savedView=mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView=1;
        managing=true;
    }

    private static void endCamera(Minecraft mc,boolean restore){
        if(!managing)return;
        managing=false;
        if(restore&&mc.gameSettings.thirdPersonView==2)mc.gameSettings.thirdPersonView=savedView;
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
        blendPart(m.bipedHead,p,AnimPose.HEAD,weight);
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
