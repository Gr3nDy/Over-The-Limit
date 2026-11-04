package otl.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class FacePoseTest {
    @Test public void calmWhenNothingEnabled(){
        FacePose pose=FacePose.of(7,100,60,false,false,false,-1);
        assertEquals(0,pose.lid,0.0001f);assertEquals(0,pose.wink,0.0001f);assertEquals(0,pose.pupilX,0.0001f);assertEquals(0,pose.pupilY,0.0001f);assertEquals(0,pose.wide,0.0001f);
    }

    @Test public void deterministic(){for(float age=0;age<400;age+=7){assertEquals(FacePose.blink(3,age,48),FacePose.blink(3,age,48),0.0001f);}}

    @Test public void blinkClosesFully(){float max=0;for(float t=0;t<600;t+=0.25f)max=Math.max(max,FacePose.blink(5,t,24));assertEquals(1,max,0.0001f);}

    @Test public void eyesMostlyOpen(){double total=0;int n=0;for(float t=0;t<2000;t+=1){total+=FacePose.blink(11,t,48);n++;}assertTrue("eyes open "+(100-total*100/n)+"%",total/n<0.15f);}

    @Test public void blinkRespectsInterval(){float slow=0,fast=0;for(float t=0;t<600;t+=0.25f){slow=Math.max(slow,FacePose.blink(2,t,160));fast=Math.max(fast,FacePose.blink(2,t,24));}assertEquals(1,slow,0.0001f);assertEquals(1,fast,0.0001f);}

    @Test public void glancesMoveAndStayInBounds(){
        float seenMin=1,seenMax=-1;float previous=Float.NaN;int distinct=0;
        for(float age=0;age<400;age+=1){
            FacePose pose=FacePose.of(4,age,48,false,true,false,-1);
            assertTrue(pose.pupilX>=-1&&pose.pupilX<=1);assertTrue(pose.pupilY>=-1&&pose.pupilY<=1);
            seenMin=Math.min(seenMin,pose.pupilX);seenMax=Math.max(seenMax,pose.pupilX);
            if(!Float.isNaN(previous)&&Math.abs(previous-pose.pupilX)>0.01f)distinct++;
            previous=pose.pupilX;
        }
        assertTrue("glance never moved",distinct>0);assertTrue(seenMax-seenMin>0.1f);
    }

    @Test public void aimingFurrowsAndWidens(){
        FacePose aiming=FacePose.of(1,50,48,true,true,true,-1);
        assertTrue(aiming.brow<0);assertTrue(aiming.wide>0);
        assertEquals("aiming should wink the left eye",1,aiming.wink,0.0001f);
        FacePose fired=FacePose.of(1,50,48,true,true,true,0);
        assertEquals(1,fired.wide,0.0001f);
        FacePose settled=FacePose.of(1,50,48,true,true,true,20);
        assertEquals(aiming.wide,settled.wide,0.0001f);
        FacePose idle=FacePose.of(1,50,48,true,true,false,-1);
        assertEquals(0,idle.wink,0.0001f);
    }

    @Test public void blinkClosesWhenEnabled(){
        boolean closed=false;
        for(float t=0;t<600;t+=0.25f)if(FacePose.of(9,t,24,true,false,false,-1).lid>=0.999f){closed=true;break;}
        assertTrue("no full blink observed",closed);
    }
}
