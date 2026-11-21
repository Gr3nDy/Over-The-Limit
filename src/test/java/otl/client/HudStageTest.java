package otl.client;

import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import org.junit.Test;
import static org.junit.Assert.*;

public class HudStageTest {
    /** A mod cancelling the Chat event skips post(CHAT); the HUD must not go with it. */
    @Test public void theHudFallsBackToTheEndOfTheFrameWhenChatIsSkipped(){
        long t=1_000_000_000L;
        assertTrue(HudStage.due(ElementType.CHAT,t));
        assertFalse("a frame that drew on CHAT must not draw twice",HudStage.isFallback(ElementType.ALL,t+1));
        assertFalse("a frame or two late is not a skip",HudStage.isFallback(ElementType.ALL,t+50_000_000L));
        assertTrue("a skipped chat stage must not hide the HUD",HudStage.isFallback(ElementType.ALL,t+200_000_000L));
        assertFalse("only the end of the frame stands in",HudStage.isFallback(ElementType.HOTBAR,t+200_000_000L));
        assertTrue("CHAT stays the normal stage",HudStage.due(ElementType.CHAT,t+210_000_000L));
        assertFalse("a drawn CHAT stage resets the wait",HudStage.isFallback(ElementType.ALL,t+260_000_000L));
    }
}
