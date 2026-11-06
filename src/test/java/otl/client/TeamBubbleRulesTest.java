package otl.client;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class TeamBubbleRulesTest {
    @Test public void extractsTeamChatWithoutJoinOrColorInformation(){
        TeamBubbleRules.Chat team=TeamBubbleRules.chat("(Orange) [Member] Alex: defend the wool");
        assertNotNull(team);assertEquals("Alex",team.name);assertEquals("defend the wool",team.body);
        assertEquals("hello",TeamBubbleRules.chat("\u00a7b(Blue Team) \u00a7cAlex\u00a7r: hello").body);
        assertEquals("Alex",TeamBubbleRules.chat("(Unknown Team) <Alex>: hello").name);
        assertEquals("observer hello",TeamBubbleRules.chat("(Observers) Alex: observer hello").body);
    }
    @Test public void ignoresGlobalPrivatePartyAndSystemMessages(){
        assertNull(TeamBubbleRules.chat("\u00a7b<Alex>: hello observers"));
        assertNull(TeamBubbleRules.chat("From Alex: secret"));
        assertNull(TeamBubbleRules.chat("\u00a7e(Party) Alex: secret"));
        assertNull(TeamBubbleRules.chat("Alex joined the game"));assertNull(TeamBubbleRules.chat("Objective: Capture wool"));
        assertNull(TeamBubbleRules.chat("You joined Blue"));
        assertNull(TeamBubbleRules.chat("<Alex>: (Blue) Bob: quoted text"));
    }
    @Test public void cooldownIsPerSenderAndSpamDoesNotExtendIt(){
        TeamBubbleRules.Cooldown cooldown=new TeamBubbleRules.Cooldown();UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertTrue(cooldown.accept(a,0));assertFalse(cooldown.accept(a,500000000L));assertTrue(cooldown.accept(b,500000000L));
        assertFalse(cooldown.accept(a,1999999999L));assertTrue(cooldown.accept(a,2000000000L));
        cooldown.expire(3000000000L);assertTrue(cooldown.accept(b,3000000000L));assertFalse(cooldown.accept(a,3000000000L));
        cooldown.clear();assertTrue(cooldown.accept(a,3000000000L));
    }
}
