package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatScrollTest {
    @Test public void noScrollbarUntilHistoryExceedsVisibleLines(){assertNull(ChatScroll.bar(400,300,250,1,20,20,0));}
    @Test public void dragTopIsOldestBottomIsNewestAndValuesClamp(){
        ChatScroll.Bar bar=ChatScroll.bar(400,300,250,1,20,100,0);
        assertEquals(80,bar.max);assertEquals(bar.trackTop+bar.trackHeight-bar.thumbHeight,bar.thumbTop);
        assertEquals(80,bar.offset(bar.trackTop,0));assertEquals(0,bar.offset(bar.trackTop+bar.trackHeight-bar.thumbHeight,0));
        assertEquals(80,bar.offset(-1000,0));assertEquals(0,bar.offset(10000,0));
        assertTrue(bar.contains(bar.x,bar.top));assertFalse(bar.contains(bar.x+9,bar.top));
    }
    @Test public void scrollbarStaysOnSmallScreensAtDifferentChatScales(){
        for(float scale:new float[]{.5F,1F,1.5F}){ChatScroll.Bar bar=ChatScroll.bar(200,180,320,scale,10,100,20);assertTrue(bar.x+9<=200);assertTrue(bar.top>=0);assertTrue(bar.thumbHeight<=bar.trackHeight);}
    }
}
