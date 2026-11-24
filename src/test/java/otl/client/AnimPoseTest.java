package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pose curves are pure maths: they must stay finite, in range and on the intended parts. */
public class AnimPoseTest {
    @Test public void nothingIsAppliedWithoutAnEmoteOrTheBoard(){
        // The model must stay exactly vanilla until the player asks for something: a clip that
        // quietly runs all the time is what made the whole body spin.
        AnimPose p=new AnimPose();
        assertFalse(p.board);
        for(int i=0;i<AnimPose.PARTS;i++){
            assertFalse("part "+i+" replaced by default",p.replace[i]);
            assertEquals("part "+i+" not zero by default",0f,p.angle[i],1e-6f);
        }
        assertEquals(0f,p.elbowRight+p.elbowLeft+p.kneeRight+p.kneeLeft,1e-6f);
    }

    @Test public void everyEmoteReplacesTheWholeBody(){
        for(int id=0;id<AnimClips.EMOTES;id++)for(float t=0;t<200;t+=4.7f){
            AnimPose p=new AnimPose();
            AnimClips.emote(p,id,t);
            for(int i=0;i<AnimPose.PARTS;i++){
                assertTrue("emote "+id+" left part "+i+" additive",p.replace[i]);
                assertTrue("emote "+id+" part "+i+" = "+p.angle[i],
                    Float.isFinite(p.angle[i])&&Math.abs(p.angle[i])<3.3f);
            }
            assertJointsSane("emote "+id,p);
        }
    }

    /** Joints fold one way only, and never far enough to push a limb through the torso. */
    private static void assertJointsSane(String what,AnimPose p){
        for(float bend:new float[]{p.elbowRight,p.elbowLeft,p.kneeRight,p.kneeLeft})
            assertTrue(what+" joint out of range: "+bend,Float.isFinite(bend)&&bend>=-0.2f&&bend<=2.2f);
        for(float splay:new float[]{p.elbowRightZ,p.elbowLeftZ,p.kneeRightZ,p.kneeLeftZ})
            assertTrue(what+" splay out of range: "+splay,Float.isFinite(splay)&&Math.abs(splay)<=0.6f);
    }

    @Test public void everyPoseBendsItsJoints(){
        // The whole point of the split limbs: no clip may leave the elbows and knees locked straight.
        for(int id=0;id<AnimClips.EMOTES;id++){
            // Neither arm may be locked straight at any point, and each emote must drive one hard.
            float strongest=0f;
            for(float t=0f;t<40f;t+=1.7f){
                AnimPose p=new AnimPose();
                AnimClips.emote(p,id,t);
                assertTrue("emote "+id+" locked an arm straight at t="+t,p.elbowRight>0.1f&&p.elbowLeft>0.1f);
                strongest=Math.max(strongest,Math.max(p.elbowRight,p.elbowLeft));
            }
            assertTrue("emote "+id+" never bent an arm past a relaxed angle",strongest>0.4f);
        }
        AnimPose skate=new AnimPose();
        AnimClips.skate(skate,-1f);
        assertTrue("the skate stance did not bend its knees",skate.kneeLeft>0.2f&&skate.kneeRight>0.2f);
    }

    @Test public void skatePlantsTheLegsAndReplacesTheWalkingArms(){
        AnimPose p=new AnimPose();
        AnimClips.skate(p,-1f);
        assertTrue(p.replace[AnimPose.RIGHT_LEG]&&p.replace[AnimPose.LEFT_LEG]);
        assertTrue(p.replace[AnimPose.RIGHT_LEG+2]);
        assertTrue(p.replace[AnimPose.RIGHT_ARM]&&p.replace[AnimPose.LEFT_ARM]);
        // The head is left to the game so it still follows where the player looks.
        assertFalse(p.replace[AnimPose.HEAD]);
        assertJointsSane("skate",p);
    }

    @Test public void theSkateStanceTurnsTheFeetAcrossTheDeck(){
        // Riding side-on is the whole stance: both feet turn about the hip, the chest stays part
        // way back toward the direction of travel, and the head is left to the game.
        for(float push=-1f;push<1.01f;push+=0.11f){
            AnimPose p=new AnimPose();
            AnimClips.skate(p,push);
            assertTrue("the front foot did not turn across the deck",
                Math.abs(p.angle[AnimPose.LEFT_LEG+1])>0.9f);
            assertTrue("the back foot did not turn across the deck",
                Math.abs(p.angle[AnimPose.RIGHT_LEG+1])>0.9f);
            assertEquals("head turned",0f,p.angle[AnimPose.HEAD+1],1e-6f);
            assertEquals("head tilted sideways",0f,p.angle[AnimPose.HEAD+2],1e-6f);
            assertEquals("body tilted sideways",0f,p.angle[AnimPose.BODY+2],1e-6f);
            assertEquals("body yaw",0.42f,p.angle[AnimPose.BODY+1],1e-6f);
        }
    }

    @Test public void theSkateStanceIsStaticApartFromOnePush(){
        // Balancing, not jogging: with no push the stance must be identical frame after frame.
        AnimPose first=new AnimPose();
        AnimClips.skate(first,-1f);
        AnimPose again=new AnimPose();
        AnimClips.skate(again,-1f);
        assertArrayEquals(first.angle,again.angle,0f);
        assertEquals(first.kneeRight,again.kneeRight,0f);
        // A push starts and finishes, so the stance always returns to the same resting shape.
        AnimPose mid=new AnimPose();
        AnimClips.skate(mid,0.5f);
        assertTrue("the kick did not move the back leg",Math.abs(mid.angle[AnimPose.RIGHT_LEG]-first.angle[AnimPose.RIGHT_LEG])>0.3f);
        assertJointsSane("kick",mid);
    }

    @Test public void olliePopsTheDeckAndEachTrickTurnsIt(){
        for(int trick=0;trick<3;trick++){
            AnimPose p=new AnimPose();
            AnimClips.ollie(p,0f,trick);
            assertEquals(0f,p.boardX,1e-5f);
            assertEquals(0f,p.boardY,1e-5f);
            assertEquals(0f,p.boardZ,1e-5f);
        }
        AnimPose ollie=new AnimPose();
        AnimClips.ollie(ollie,0.5f,0);
        assertTrue(Math.abs(ollie.boardX)>0.5f);
        AnimPose flip=new AnimPose();
        AnimClips.ollie(flip,0.5f,1);
        assertTrue(Math.abs(flip.boardZ)>1f);
        AnimPose shove=new AnimPose();
        AnimClips.ollie(shove,0.5f,2);
        assertTrue(Math.abs(shove.boardY)>1f);
        // The body angle is replaced, so an ollie always unwinds back to the stance instead of
        // leaving the player leaning further over after every jump.
        AnimPose start=new AnimPose();
        AnimClips.ollie(start,0f,0);
        AnimPose end=new AnimPose();
        AnimClips.ollie(end,1f,0);
        assertEquals(start.angle[AnimPose.BODY],end.angle[AnimPose.BODY],1e-5f);
    }

    @Test public void smoothstepClamps(){
        assertEquals(0f,AnimClips.smooth(-1f),1e-6f);
        assertEquals(0f,AnimClips.smooth(0f),1e-6f);
        assertEquals(0.5f,AnimClips.smooth(0.5f),1e-6f);
        assertEquals(1f,AnimClips.smooth(1f),1e-6f);
        assertEquals(1f,AnimClips.smooth(2f),1e-6f);
    }

    @Test public void limbBendHasOneContinuousTransition(){
        assertEquals(0f,SkateLimb.hingeWeight(-2f),0f);
        assertEquals(0.5f,SkateLimb.hingeWeight(0f),0f);
        assertEquals(1f,SkateLimb.hingeWeight(2f),0f);
        assertEquals(SkateLimb.hingeWeight(-0.0001f),SkateLimb.hingeWeight(0.0001f),0.001f);
        assertEquals(1f,SkateLimb.bootInset(0f),0f);
        assertEquals(0.5f,SkateLimb.bootInset(2f),0f);
        assertEquals(0f,SkateLimb.bootInset(4f),0f);
    }

    @Test public void armorAndSkinUseTheSameInteriorBendRings(){
        for(int ring=1;ring<6;ring++)
            assertEquals(SkateLimb.ringY(0,12,0,12,ring),SkateLimb.ringY(-1,13,0,12,ring),0);
        assertEquals(-1,SkateLimb.ringY(-1,13,0,12,0),0);
        assertEquals(13,SkateLimb.ringY(-1,13,0,12,6),0);
    }

    @Test public void skateCrouchBendsBothKneesWithoutChangingTheStanceAtRest(){
        AnimPose pose=new AnimPose();
        AnimClips.skate(pose,-1f);
        float right=pose.kneeRight,left=pose.kneeLeft;
        AnimClips.skateCrouch(pose,0f);
        assertEquals(right,pose.kneeRight,0f);
        assertEquals(left,pose.kneeLeft,0f);
        AnimClips.skateCrouch(pose,1f);
        assertTrue(pose.kneeRight>right&&pose.kneeLeft>left);
    }

    @Test public void aFallTipsTheDeckAndSpreadsTheArmsWithoutChangingIdle(){
        assertEquals(0f,AnimClips.fallStrength(1.25,0.52),0f);
        assertEquals(0f,AnimClips.fallStrength(2.5,-0.1),0f);
        assertTrue(AnimClips.fallStrength(2.1,0.55)>0.95f);
        AnimPose pose=new AnimPose();
        AnimClips.skate(pose,-1f);
        float arm=pose.angle[AnimPose.RIGHT_ARM+2];
        AnimClips.skateFall(pose,0f);
        assertEquals(arm,pose.angle[AnimPose.RIGHT_ARM+2],0f);
        assertEquals(0f,pose.boardX,0f);
        AnimClips.skateFall(pose,1f);
        assertTrue(pose.boardX< -0.2f&&pose.angle[AnimPose.RIGHT_ARM+2]>arm);
    }

    @Test public void grindingTurnsTheDeckSideways(){
        AnimPose pose=new AnimPose();
        AnimClips.skate(pose,-1f);
        AnimClips.skateGrind(pose,0f);
        assertEquals(0f,pose.boardY,0f);
        AnimClips.skateGrind(pose,1f);
        assertEquals((float)Math.PI/2f,pose.boardY,1e-6f);
        assertEquals(0f,pose.angle[AnimPose.LEFT_LEG+1],0f);
    }

    @Test public void aPushShiftsWeightBeforeDipping(){
        assertTrue(AnimClips.pushContact(0.15f)>0f);
        assertEquals(0f,AnimClips.pushDip(0.15f),0f);
        assertEquals(1f,AnimClips.pushDip(0.50f),0f);
        assertEquals(0f,AnimClips.pushDip(1f),0f);
    }

    @Test public void visibleSleevesAreBakedOntoTheBaseSkin(){
        java.awt.image.BufferedImage skin=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(40,20,0xFF0000FF);
        skin.setRGB(41,20,0xFF0000FF);
        skin.setRGB(42,20,0xFF0000FF);
        skin.setRGB(40,21,0xFF0000FF);
        skin.setRGB(40,36,0xFFFF0000);
        skin.setRGB(42,36,0x80FF0000);
        java.awt.image.BufferedImage baked=SkateSkin.flatten(skin,4);
        assertEquals(0xFFFF0000,baked.getRGB(40,20));
        assertEquals(0xFF0000FF,baked.getRGB(41,20));
        assertEquals(0xFF80007F,baked.getRGB(42,20));
        assertEquals(0xFF0000E8,baked.getRGB(40,21));
        assertEquals(0xFF0000FF,SkateSkin.flatten(skin,0).getRGB(40,20));
        assertEquals(0xFF0000FF,skin.getRGB(40,20));
    }

    @Test public void boardIsPlacedBeforeTheRiderMountsAndPushReturnsToIdle(){
        assertEquals(0f,AnimClips.mount(0.59f),0f);
        assertTrue(AnimClips.place(0.60f)>0.98f);
        assertEquals(1f,AnimClips.place(0.65f),0f);
        assertTrue("board was lowered into the standing rider",AnimClips.setDownZ(0.62f)<-0.7f);
        assertEquals(0f,AnimClips.setDownZ(1f),0f);
        assertEquals(1f,AnimClips.mount(1f),0f);
        AnimPose reach=new AnimPose(),rest=new AnimPose(),afterPush=new AnimPose();
        AnimClips.deploy(reach,0.22f);
        assertTrue(reach.angle[AnimPose.RIGHT_ARM]>1f && reach.elbowRight< -0.5f);
        AnimClips.skate(rest,-1f);
        AnimClips.skate(afterPush,1f);
        assertArrayEquals(rest.angle,afterPush.angle,0f);
        assertEquals(rest.kneeRight,afterPush.kneeRight,0f);
        for(int trick=0;trick<3;trick++){
            AnimPose landed=new AnimPose();
            AnimClips.skate(landed,-1f);
            AnimClips.ollie(landed,1f,trick);
            assertArrayEquals(rest.angle,landed.angle,0f);
            assertEquals(0f,landed.boardDrop,0f);
        }
    }

    @Test public void moduleIsRegisteredAndTogglesItsPreference(){
        boolean animation=UtilitySettings.playerAnimation,camera=UtilitySettings.emoteCamera;
        try{
            OtlModule module=OtlModule.ALL.stream().filter(m->m.id.equals("player_animation")).findFirst().get();
            assertEquals("Player Animation",module.name);
            assertTrue(module.settings(null) instanceof PlayerAnimSettings);
            module.setEnabled(false);assertFalse(module.enabled());
            module.setEnabled(true);assertTrue(module.enabled());
            module.reset();assertTrue(module.enabled());
        }finally{
            UtilitySettings.playerAnimation=animation;UtilitySettings.emoteCamera=camera;
        }
    }
}
