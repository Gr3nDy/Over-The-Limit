package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pose curves are pure maths: they must stay finite, in range and on the intended parts. */
public class AnimPoseTest {
    @Test public void idleStaysSubtleAndAdditive(){
        for(float t=0;t<400;t+=7.3f){
            AnimPose p=new AnimPose();
            AnimClips.idle(p,t);
            for(int i=0;i<AnimPose.PARTS;i++)assertFalse("part "+i+" replaced",p.replace[i]);
            for(float a:p.angle)assertTrue("angle "+a,Float.isFinite(a)&&Math.abs(a)<0.25f);
            assertFalse(p.board);
        }
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
        AnimClips.skate(skate,0f,0f,10f);
        assertTrue("the skate stance did not bend its knees",skate.kneeLeft>0.3f&&skate.kneeRight>0.2f);
        AnimPose idle=new AnimPose();
        AnimClips.idle(idle,10f);
        assertTrue("idle left the elbows locked",idle.elbowRight>0.05f&&idle.elbowLeft>0.05f);
    }

    @Test public void skatePlantsTheLegsAndKeepsTheArmsAdditive(){
        AnimPose p=new AnimPose();
        AnimClips.skate(p,3f,1f,20f);
        assertTrue(p.replace[AnimPose.RIGHT_LEG]&&p.replace[AnimPose.LEFT_LEG]);
        assertTrue(p.replace[AnimPose.RIGHT_LEG+2]);
        assertFalse(p.replace[AnimPose.RIGHT_ARM]||p.replace[AnimPose.LEFT_ARM]);
        assertFalse(p.replace[AnimPose.HEAD]);
        assertJointsSane("skate",p);
    }

    @Test public void theSkateStanceNeverTwistsTheTorsoOrHead(){
        // A body or head Y rotation is what made the whole model look like it was spinning.
        for(float t=0;t<200;t+=3.1f)for(float swing=0f;swing<6f;swing+=1.3f){
            AnimPose p=new AnimPose();
            AnimClips.skate(p,swing,1f,t);
            assertTrue("torso twisted",Math.abs(p.angle[AnimPose.BODY+1])<0.01f);
            assertTrue("head turned",Math.abs(p.angle[AnimPose.HEAD+1])<0.01f);
        }
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
