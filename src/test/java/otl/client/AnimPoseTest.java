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
        assertTrue("the skate stance did not bend its knees",skate.kneeLeft>0.3f&&skate.kneeRight>0.2f);
    }

    @Test public void skatePlantsTheLegsAndKeepsTheArmsAdditive(){
        AnimPose p=new AnimPose();
        AnimClips.skate(p,-1f);
        assertTrue(p.replace[AnimPose.RIGHT_LEG]&&p.replace[AnimPose.LEFT_LEG]);
        assertTrue(p.replace[AnimPose.RIGHT_LEG+2]);
        assertFalse(p.replace[AnimPose.RIGHT_ARM]||p.replace[AnimPose.LEFT_ARM]);
        // The head is left to the game so it still follows where the player looks.
        assertFalse(p.replace[AnimPose.HEAD]);
        assertJointsSane("skate",p);
    }

    @Test public void theSkateStanceNeverTurnsTheBodyOrHead(){
        // Any Y rotation on the body or head is what made the model look like it was spinning.
        for(float push=-1f;push<1.01f;push+=0.11f){
            AnimPose p=new AnimPose();
            AnimClips.skate(p,push);
            for(int part:new int[]{AnimPose.HEAD,AnimPose.BODY}){
                assertEquals("part "+part+" turned",0f,p.angle[part+1],1e-6f);
                assertEquals("part "+part+" tilted sideways",0f,p.angle[part+2],1e-6f);
            }
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
