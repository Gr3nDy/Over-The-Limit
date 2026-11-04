package otl.client;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChatWelcomeGateTest {
    @Test public void lobbyNeedsBothLinesAndCanArriveInEitherOrder(){
        ChatWelcomeGate g=new ChatWelcomeGate();assertTrue(g.enabled());assertFalse(g.accept("Overcast Community"));assertTrue(g.accept("view our /servers or click the beacon"));assertFalse(g.enabled());
        g.reset();g.accept("view our /servers or click the beacon");g.accept("Overcast Community");assertFalse(g.enabled());
    }
    @Test public void mapChangesKeepFullChatAndDoNotRepeatTeamCommand(){
        ChatWelcomeGate g=new ChatWelcomeGate();assertTrue(g.accept("Created by MapMaker"));g.worldChanged();assertTrue(g.enabled());assertFalse(g.accept("Created by AnotherMaker"));
        g.accept("Overcast Community\nview our /servers or click the beacon");assertFalse(g.enabled());assertTrue(g.accept("Welcome to Overcast Community!"));assertTrue(g.enabled());g.worldChanged();assertFalse(g.accept("Created by MapMaker"));
    }
    @Test public void directWelcomeAndPlayerQuotes(){ChatWelcomeGate g=new ChatWelcomeGate();assertFalse(g.accept("Alex: Created by MapMaker"));assertFalse(g.accept("From Alex: Overcast Community"));assertTrue(g.accept("\u00a7bWelcome to Overcast Community!"));assertFalse(g.accept("Welcome to Overcast Community!"));}
    @Test public void scopeAndOpacity(){assertTrue(FaceConfig.allows(0,true));assertFalse(FaceConfig.allows(0,false));assertTrue(FaceConfig.allows(1,false));assertFalse(FaceConfig.allows(2,true));assertEquals(0,ChatAppearance.backgroundAlpha(255,0));assertEquals(128,ChatAppearance.backgroundAlpha(255,50));}
}
