package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatAppearanceTest {
    @Test public void tabOpacityPreservesColorsAndScalesEveryTabState(){
        for(int color:new int[]{0xEE404040,0xCC202020,0xDD252525}){
            assertEquals(color,ChatAppearance.tabColor(color,100));
            assertEquals(color&0xFFFFFF,ChatAppearance.tabColor(color,0));
            assertEquals(Math.round((color>>>24)/2F),ChatAppearance.tabColor(color,50)>>>24);
            assertEquals(color,ChatAppearance.tabColor(color,200));
            assertEquals(0,ChatAppearance.tabColor(color,-1)>>>24);
        }
    }
}
