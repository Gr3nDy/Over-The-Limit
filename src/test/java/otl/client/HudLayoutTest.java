package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class HudLayoutTest {
    @Test public void confirmDefaultsAnchoredAtCrosshairForEachSize(){
        for(int sw:new int[]{427,800})for(int w:new int[]{90,135,270}){
            HudLayout.Rect r=HudLayout.rect(sw,300,w,44,HudLayout.confirmPosition(sw,300,w,44));
            assertEquals(Math.min(sw/2-6,sw-w-4),r.x);assertEquals(122,r.y);
        }
    }
    @Test public void snapsToCenterEdgesAndReleasesOrBypasses(){
        java.util.List<HudLayout.Rect> none=java.util.Collections.emptyList();
        HudLayout.Snap s=HudLayout.snap(800,600,new HudLayout.Rect(304,253,200,100),none,false);
        assertEquals(300,s.x);assertEquals(250,s.y);assertEquals(400,s.guideX);assertEquals(300,s.guideY);
        s=HudLayout.snap(800,600,new HudLayout.Rect(9,493,200,100),none,false);assertEquals(4,s.x);assertEquals(496,s.y);
        s=HudLayout.snap(800,600,new HudLayout.Rect(307,260,200,100),none,false);assertEquals(307,s.x);assertEquals(-1,s.guideX);
        s=HudLayout.snap(800,600,new HudLayout.Rect(304,253,200,100),none,true);assertEquals(304,s.x);assertEquals(253,s.y);assertEquals(-1,s.guideX);
    }
    @Test public void snapsToOtherWidgetsAndAdjacentGap(){
        java.util.List<HudLayout.Rect> other=java.util.Collections.singletonList(new HudLayout.Rect(100,100,100,50));
        HudLayout.Snap s=HudLayout.snap(800,600,new HudLayout.Rect(102,153,80,30),other,false);
        assertEquals(100,s.x);assertEquals(154,s.y);
        s=HudLayout.snap(800,600,new HudLayout.Rect(206,101,80,30),other,false);assertEquals(204,s.x);assertEquals(100,s.y);
    }
    @Test public void snapsAllFourBoxSidesToQuarterAndCenterGuides(){
        java.util.List<HudLayout.Rect> none=java.util.Collections.emptyList();
        HudLayout.Snap s=HudLayout.snap(800,600,new HudLayout.Rect(204,154,100,50),none,false);
        assertEquals(200,s.x);assertEquals(150,s.y);assertEquals(200,s.guideX);assertEquals(150,s.guideY);
        s=HudLayout.snap(800,600,new HudLayout.Rect(496,397,100,50),none,false);
        assertEquals(500,s.x);assertEquals(400,s.y);assertEquals(600,s.guideX);assertEquals(450,s.guideY);
        s=HudLayout.snap(800,600,new HudLayout.Rect(294,273,100,50),none,false);
        assertEquals(300,s.x);assertEquals(275,s.y);assertEquals(400,s.guideX);assertEquals(300,s.guideY);
    }
    @Test public void cornerResizePreservesAspectAndClampsScale(){
        HudLayout.Rect r=new HudLayout.Rect(100,100,200,100);
        assertEquals(125,HudLayout.resizedScale(100,r,3,350,225));
        assertEquals(125,HudLayout.resizedScale(100,r,0,50,75));
        assertEquals(125,HudLayout.resizedScale(100,r,1,350,75));
        assertEquals(125,HudLayout.resizedScale(100,r,2,50,225));
        assertEquals(50,HudLayout.resizedScale(100,r,3,100,100));
        assertEquals(150,HudLayout.resizedScale(100,r,3,900,500));
    }
    @Test public void rightBottomAnchorsSurviveResolutionChanges(){
        HudLayout.Position p=new HudLayout.Position(1,1);
        HudLayout.Rect r=HudLayout.rect(800,600,200,100,p);assertEquals(596,r.x);assertEquals(496,r.y);
        r=HudLayout.rect(400,300,200,100,p);assertEquals(196,r.x);assertEquals(196,r.y);
    }
    @Test public void draggingRoundTripsAndClampsToVisibleScreen(){
        HudLayout.Position p=HudLayout.dragged(800,600,200,100,250,150);
        HudLayout.Rect r=HudLayout.rect(800,600,200,100,p);assertEquals(250,r.x);assertEquals(150,r.y);
        r=HudLayout.rect(100,60,500,500,new HudLayout.Position(-3,5));assertEquals(4,r.x);assertEquals(4,r.y);assertEquals(92,r.w);assertEquals(52,r.h);
        assertTrue(r.contains(4,4));assertFalse(r.contains(96,56));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonFiniteSavedPositions(){new HudLayout.Position(Double.NaN,0);}
}
