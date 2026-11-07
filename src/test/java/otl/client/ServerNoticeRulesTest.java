package otl.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerNoticeRulesTest {
    @Test public void everyModeExcludesPlayerMessagesIncludingObserversAndDecoratedNames(){
        for(ServerNoticeRules.Mode mode:ServerNoticeRules.Mode.values())for(String line:new String[]{
            "<Alex>: hello","\u00a77Alex\u00a7r: observer hello","(Observers) [VIP] *Alex: hello",
            "<[Admin] Alex>: hello","[Observer] Alex: hello","\u2605 Alex: hello",
            "(Blue Team) Alex: hello","\u00a7e(Party) Alex: hello","From Alex: hello","To Alex: hello",
            "(Observers) Alex\u00a0\u00bb hello","<Alex>: Bob joined the game"
        })assertFalse(mode+": "+line,ServerNoticeRules.accepts(line,mode));
    }
    @Test public void modesRouteOnlyTheirSelectedNotices(){
        for(String line:new String[]{"Alex joined the game","Sam left the game","Alex quietly joined the game.","Sam quietly left the game","<Alex> joined the server"}){
            assertTrue(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.ALL));
            assertTrue(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.JOINS_LEAVES));
            assertFalse(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.EXCEPT_JOINS_LEAVES));
        }
        for(String line:new String[]{"Match starts in 10 seconds","Created by MapMaker","\u26a0 No permission","Alex was slain by Bob's Wooden Sword"}){
            assertTrue(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.ALL));
            assertFalse(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.JOINS_LEAVES));
            assertTrue(line,ServerNoticeRules.accepts(line,ServerNoticeRules.Mode.EXCEPT_JOINS_LEAVES));
        }
    }
}
