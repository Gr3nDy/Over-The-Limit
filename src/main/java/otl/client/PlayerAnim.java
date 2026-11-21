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
 * Nothing is sent to the server, so other clients never see it.
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

    /** Pushes the pose for one render frame onto the model, before its limbs draw. */
    static void apply(AnimatedModelPlayer model,EntityPlayer p,float swing,float amount,float age){
        Minecraft mc=MinecraftAccess.get();
        float partial=Math.max(0f,Math.min(1f,age-p.ticksExisted));
        State s=STATES.get(p.getEntityId());
        // Nothing is touched until the player asks for it: vanilla behaviour stays exactly vanilla.
        if(s!=null&&s.board>0.001f&&p==mc.thePlayer){
            AnimPose pose=SCRATCH;
            pose.reset();
            float board=s.prevBoard+(s.board-s.prevBoard)*partial;
            float push=-1f;
            if(s.pushAt>=0){
                float phase=(age-s.pushAt)/PUSH_TICKS;
                if(phase>=0f&&phase<=1f)push=phase;
            }
            AnimClips.skate(pose,push);
            pose.board=true;
            // Blocks, not model units: this translate runs in the same space as a rotation point.
            pose.boardDrop=(1f-board)*0.40f;
            pose.boardX=-0.9f*(1f-board);
            if(s.ollieAt>=0){
                float phase=(age-s.ollieAt)/OLLIE_TICKS;
                if(phase>=0f&&phase<=1f)AnimClips.ollie(pose,phase,s.trick);
            }
            poseModel(model,pose);
            model.boardOut=true;
            model.boardX=pose.boardX;
            model.boardY=pose.boardY;
            model.boardZ=pose.boardZ;
            model.boardDrop=pose.boardDrop;
            model.elbowRight=pose.elbowRight;model.elbowLeft=pose.elbowLeft;
            model.kneeRight=pose.kneeRight;model.kneeLeft=pose.kneeLeft;
            model.elbowRightZ=pose.elbowRightZ;model.elbowLeftZ=pose.elbowLeftZ;
            model.kneeRightZ=pose.kneeRightZ;model.kneeLeftZ=pose.kneeLeftZ;
        }
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
        s.wantsBoard=!s.wantsBoard;
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
            return;
        }
        for(EntityPlayer p:new java.util.ArrayList<EntityPlayer>(mc.theWorld.playerEntities))step(p);
        checkEmote(mc);
    }

    /** The click that ends an emote must not also punch or place a block. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void mouse(MouseEvent event){
        if(event.button!=0||!event.buttonstate)return;
        Minecraft mc=MinecraftAccess.get();
        // A screen that takes clicks (chat) must keep them; the emote already ended on open.
        if(mc.thePlayer==null||mc.currentScreen!=null)return;
        State s=STATES.get(mc.thePlayer.getEntityId());
        boolean emoting=s!=null&&s.emote>=0;
        if(!emoting&&!managing)return;
        if(emoting)stopEmote(mc,mc.thePlayer,true);
        else endCamera(mc,true);
        event.setCanceled(true);
    }

    static void clear(){
        Minecraft mc=MinecraftAccess.get();
        if(managing&&mc!=null)endCamera(mc,true);
        STATES.clear();
    }

    private static void step(EntityPlayer p){
        State s=state(p);
        s.prevBoard=s.board;
        float target=s.wantsBoard?1f:0f;
        if(s.board<target)s.board=Math.min(target,s.board+PULL_OUT);
        else if(s.board>target)s.board=Math.max(target,s.board-PULL_OUT);
        if(s.ollieAt>=0&&p.ticksExisted-s.ollieAt>OLLIE_TICKS)s.ollieAt=-1;
        if(s.pushAt>=0&&p.ticksExisted-s.pushAt>PUSH_TICKS)s.pushAt=-1;
        // Crouch then jump pops the ollie; a plain jump is left alone as a normal jump.
        boolean jumped=!p.onGround&&s.lastGround&&p.motionY>0.12;
        if(p==MinecraftAccess.get().thePlayer&&jumped&&p.isSneaking()
            &&s.wantsBoard&&s.board>=0.9f){
            s.ollieAt=p.ticksExisted;
            s.trick=(int)(Math.random()*3);
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
