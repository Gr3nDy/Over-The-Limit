package otl.client;

import static org.junit.Assert.*;

import org.junit.Test;

public class EyeDetectorTest {
    private static final int SKIN=0xFFC8A078,WHITE=0xFFEDEDED,INK=0xFF3A3333;

    private static int[] plainSkin(){int[] f=new int[64];java.util.Arrays.fill(f,SKIN);return f;}

    /** Rect eyes in the lower face (rows 5-6): white beside one dark pupil column each. */
    private static int[] faceWithEyes(boolean brows){
        int[] f=plainSkin();
        for(int r=5;r<=6;r++){
            f[r*8+1]=WHITE;f[r*8+2]=INK;
            f[r*8+5]=INK;f[r*8+6]=WHITE;
        }
        if(brows){for(int c=1;c<=2;c++)f[3*8+c]=INK;for(int c=5;c<=6;c++)f[3*8+c]=INK;}
        return f;
    }

    /** The user's real skin: eyes at rows 5-6, sclera outside the pupil column. */
    private static int[] realSkin(){
        int[] f=new int[64];java.util.Arrays.fill(f,0xFFC2AEAE);
        for(int c=1;c<=6;c++){f[3*8+c]=0xFFFBD1BE;f[4*8+c]=0xFFFBD1BE;}
        int[] r5={0xFFC2AEAE,0xFFBEBEBE,0xFF796E6E,0xFFFCDBCC,0xFFFCDBCC,0xFF796E6E,0xFFBEBEBE,0xFFC2AEAE};
        int[] r6={0xFFC2AEAE,0xFFEEEEEE,0xFF857A7A,0xFFFEECE4,0xFFFEECE4,0xFF857A7A,0xFFEEEEEE,0xFFC2AEAE};
        int[] r7={0xFFC2AEAE,0xFFFCDBCC,0xFFFDE5DA,0xFFFEECE4,0xFFFEECE4,0xFFFDE5DA,0xFFFCDBCC,0xFFC2AEAE};
        System.arraycopy(r5,0,f,5*8,8);System.arraycopy(r6,0,f,6*8,8);System.arraycopy(r7,0,f,7*8,8);
        return f;
    }

    private static int count(int[] marks,int kind){int n=0;for(int m:marks)if(m==kind)n++;return n;}
    private static int at(int[] marks,int c,int r){return marks[r*8+c];}

    @Test public void plainSkinFindsNothing(){assertNull(EyeDetector.detect(plainSkin(),null));assertNull(EyeDetector.detect(null,null));assertNull(EyeDetector.detect(new int[10],null));}

    @Test public void findsRectEyePair(){
        int[] marks=EyeDetector.detect(faceWithEyes(false),null);
        assertNotNull(marks);
        assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));
        assertEquals(4,count(marks,EyeDetector.PUPIL));
        assertEquals(0,count(marks,EyeDetector.BROW));
        assertEquals(EyeDetector.EYE,at(marks,1,5));assertEquals(EyeDetector.PUPIL,at(marks,2,5));
        assertEquals(EyeDetector.PUPIL,at(marks,5,6));assertEquals(EyeDetector.EYE,at(marks,6,6));
    }

    @Test public void findsBrows(){int[] marks=EyeDetector.detect(faceWithEyes(true),null);assertNotNull(marks);assertEquals(4,count(marks,EyeDetector.BROW));assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));}

    @Test public void realSkinRectModel(){
        int[] marks=EyeDetector.detect(realSkin(),null);
        assertNotNull(marks);
        assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));
        assertEquals(4,count(marks,EyeDetector.PUPIL));
        assertEquals(EyeDetector.EYE,at(marks,1,6));assertEquals(EyeDetector.PUPIL,at(marks,2,5));
        assertEquals(EyeDetector.PUPIL,at(marks,5,5));assertEquals(EyeDetector.EYE,at(marks,6,6));
    }

    @Test public void opaqueHatDoesNotHideFaceEyes(){
        int[] hat=plainSkin();java.util.Arrays.fill(hat,0xFFC8A078);
        int[] marks=EyeDetector.detect(faceWithEyes(true),hat);
        assertNotNull(marks);assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));
    }

    @Test public void transparentHatIgnored(){
        int[] hat=new int[64];java.util.Arrays.fill(hat,0x00FF00FF);
        int[] marks=EyeDetector.detect(faceWithEyes(false),hat);
        assertNotNull(marks);assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));
    }

    @Test public void hatOnlyFaceFallsBackToDrawnSkin(){
        int[] face=plainSkin();
        int[] hat=new int[64];
        hat[5*8+2]=INK;hat[6*8+2]=INK;
        hat[5*8+5]=INK;hat[6*8+5]=INK;
        int[] marks=EyeDetector.detect(face,hat);
        assertNotNull(marks);assertEquals(4,count(marks,EyeDetector.PUPIL));
    }

    @Test public void singletonBlobStillPairs(){int[] f=plainSkin();f[2*8+2]=INK;f[2*8+5]=INK;int[] marks=EyeDetector.detect(f,null);assertNotNull(marks);assertTrue(count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL)==2);}

    @Test public void findsEyesOnDarkSkin(){
        int[] f=new int[64];java.util.Arrays.fill(f,0xFF5A3A24);
        for(int r=5;r<=6;r++){f[r*8+1]=0xFFEDEDED;f[r*8+2]=0xFF1A120C;f[r*8+5]=0xFF1A120C;f[r*8+6]=0xFFEDEDED;}
        int[] marks=EyeDetector.detect(f,null);
        assertNotNull(marks);
        assertEquals(8,count(marks,EyeDetector.EYE)+count(marks,EyeDetector.PUPIL));
        assertEquals(4,count(marks,EyeDetector.PUPIL));
    }

    @Test public void flatHairRowIsNotInk(){
        int[] f=plainSkin();
        for(int c=0;c<8;c++)f[2*8+c]=0xFF201810;
        assertNull(EyeDetector.detect(f,null));
    }
}
